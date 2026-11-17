package io.github.christianlinzatti.glyphscanner;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import io.github.christianlinzatti.glyphscanner.core.Analysis;
import io.github.christianlinzatti.glyphscanner.core.ScreenAnalyzer;
import io.github.christianlinzatti.glyphscanner.core.SequenceTracker;

/**
 * Foreground service: mirrors the screen into an ImageReader (half resolution),
 * analyses ~10 frames per second and drives the overlay.
 */
public class CaptureService extends Service implements OverlayController.Callbacks {

    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";
    public static final String EXTRA_RESULT_CODE = "resultCode";
    public static final String EXTRA_RESULT_DATA = "resultData";
    static final String PREFS = "glyphscanner";

    private static final String TAG = "GlyphScanner";
    private static final String CHANNEL = "capture";
    private static final int NOTIFICATION_ID = 1;
    private static final long FRAME_INTERVAL_MS = 100;

    static volatile boolean running;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ScreenAnalyzer analyzer = new ScreenAnalyzer();
    private final SequenceTracker tracker = new SequenceTracker();

    private SharedPreferences prefs;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader reader;
    private HandlerThread worker;
    private Handler bg;
    private OverlayController overlay;

    private long lastFrameMs;
    private boolean scheduled;
    private boolean wasCalibrated;
    private volatile boolean debug;
    private String lastStatus = "";
    private long lastHackScreenMs = -1;
    private boolean expanded;
    static final String KEY_ICON_SCALE = "icon_scale";
    static final int DEFAULT_ICON_SCALE = 180; // percent

    private final Runnable processRunnable = this::processLatest;

    /** After a hack: this long without the glyph screen -> clear and wait for the next one. */
    private static final long AUTO_RESET_MS = 8000;
    private final Runnable autoResetTick = new Runnable() {
        @Override
        public void run() {
            checkAutoReset(SystemClock.uptimeMillis());
            if (bg != null) bg.postDelayed(this, 1000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (projection != null) return START_NOT_STICKY; // already running

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        startInForeground();

        int code = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        Intent data;
        if (Build.VERSION.SDK_INT >= 33) {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent.class);
        } else {
            data = intent.getParcelableExtra(EXTRA_RESULT_DATA);
        }
        if (data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        try {
            MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
        } catch (RuntimeException e) {
            Log.e(TAG, "getMediaProjection failed", e);
        }
        if (projection == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        projection.registerCallback(new MediaProjection.Callback() {
            @Override
            public void onStop() {
                main.post(CaptureService.this::stopSelf);
            }
        }, main);

        restoreCalibration();

        worker = new HandlerThread("glyph-capture");
        worker.start();
        bg = new Handler(worker.getLooper());

        bg.postDelayed(autoResetTick, 1000);
        try {
            startCapture();
        } catch (RuntimeException e) {
            Log.e(TAG, "createVirtualDisplay failed", e);
            stopSelf();
            return START_NOT_STICKY;
        }

        overlay = new OverlayController(this, prefs, this);
        try {
            overlay.show();
        } catch (RuntimeException e) {
            Log.e(TAG, "overlay failed (permission?)", e);
            stopSelf();
            return START_NOT_STICKY;
        }
        overlay.update(SequenceTracker.State.empty(), "Bereit", false);
        running = true;
        return START_NOT_STICKY;
    }

    private void startInForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "Glyph-Erkennung", NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);

        Intent stop = new Intent(this, CaptureService.class).setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stop, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent openPi = PendingIntent.getActivity(this, 2, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_glyph)
                .setContentTitle("Glyph Scanner aktiv")
                .setContentText("Erkennt Glyphen im Ingress-Hack")
                .setContentIntent(openPi)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(null, "Beenden", stopPi).build())
                .build();
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    private void startCapture() {
        Point size = Screen.realSize(this);
        int w = Math.min(size.x, size.y), h = Math.max(size.x, size.y);
        // half resolution is plenty for the thick glyph strokes and saves battery
        int cw = (w / 2) & ~1, ch = (h / 2) & ~1;
        int dpi = Math.max(80, getResources().getDisplayMetrics().densityDpi / 2);
        reader = ImageReader.newInstance(cw, ch, PixelFormat.RGBA_8888, 2);
        reader.setOnImageAvailableListener(r -> {
            if (scheduled) return;
            scheduled = true;
            long delay = Math.max(0, lastFrameMs + FRAME_INTERVAL_MS - SystemClock.uptimeMillis());
            bg.postDelayed(processRunnable, delay);
        }, bg);
        virtualDisplay = projection.createVirtualDisplay("GlyphScanner", cw, ch, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.getSurface(), null, bg);
    }

    /** Runs on the worker thread. */
    private void processLatest() {
        scheduled = false;
        if (reader == null) return;
        Image img = null;
        try {
            img = reader.acquireLatestImage();
            if (img == null) return;
            lastFrameMs = SystemClock.uptimeMillis();
            Image.Plane p = img.getPlanes()[0];
            ImageFrame frame = new ImageFrame(p.getBuffer(), img.getWidth(), img.getHeight(),
                    p.getPixelStride(), p.getRowStride());
            Analysis a = analyzer.analyze(frame);
            if (analyzer.isCalibrated() && !wasCalibrated) saveCalibration();
            wasCalibrated = analyzer.isCalibrated();

            boolean changed = tracker.update(a, lastFrameMs);
            SequenceTracker.State s = tracker.snapshot();

            if (a.glyphScreen || a.gridVisible) lastHackScreenMs = lastFrameMs;
            boolean wantExpanded = s.locked && s.count > 0
                    && lastHackScreenMs >= 0 && lastFrameMs - lastHackScreenMs < 2500;
            if (wantExpanded != expanded) {
                expanded = wantExpanded;
                final boolean ex = wantExpanded;
                final float scale = prefs.getInt(KEY_ICON_SCALE, DEFAULT_ICON_SCALE) / 100f;
                main.post(() -> {
                    if (overlay != null) overlay.setExpanded(ex, scale);
                });
            }
            if (checkAutoReset(lastFrameMs)) return;
            String status = debug ? a.debug : statusText(s, expanded);
            if (changed || !status.equals(lastStatus)) {
                lastStatus = status;
                final boolean dbg = debug;
                main.post(() -> {
                    if (overlay != null) overlay.update(s, status, dbg);
                });
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "frame failed", e);
        } finally {
            if (img != null) img.close();
        }
    }

    /** Runs on the worker thread. @return true if the sequence was cleared. */
    private boolean checkAutoReset(long now) {
        if (lastHackScreenMs < 0 || now - lastHackScreenMs < AUTO_RESET_MS) return false;
        if (tracker.snapshot().count == 0) return false;
        tracker.reset();
        lastHackScreenMs = -1;
        expanded = false;
        lastStatus = "Bereit";
        final boolean dbg = debug;
        final SequenceTracker.State empty = tracker.snapshot();
        main.post(() -> {
            if (overlay != null) {
                overlay.setExpanded(false, 1f);
                overlay.update(empty, "Bereit", dbg);
            }
        });
        return true;
    }

    private static String statusText(SequenceTracker.State s, boolean expanded) {
        if (expanded && !s.onGlyphScreen) return "Eingabe";
        if (!s.onGlyphScreen) return s.count > 0 ? "Fertig" : "Bereit";
        if (s.inputPhase) return s.inputIndex >= 0 ? "Zeichne " + (s.inputIndex + 1) + "/" + s.count : "Eingabe";
        if (s.displayIndex >= 0) return "Lese " + (s.displayIndex + 1) + "/" + s.count;
        return "Glyph-Hack";
    }

    // ------------------------------------------------------------------ calibration storage

    private void restoreCalibration() {
        int w = prefs.getInt("cal_w", -1), h = prefs.getInt("cal_h", -1);
        if (w > 0 && h > 0) {
            analyzer.setCalibration(w, h, prefs.getFloat("cal_cy", 0), prefs.getFloat("cal_r", 0));
            wasCalibrated = true;
        }
    }

    private void saveCalibration() {
        prefs.edit()
                .putInt("cal_w", analyzer.calibrationWidth())
                .putInt("cal_h", analyzer.calibrationHeight())
                .putFloat("cal_cy", analyzer.centerY())
                .putFloat("cal_r", analyzer.radius())
                .apply();
    }

    static void clearCalibration(SharedPreferences prefs) {
        prefs.edit().remove("cal_w").remove("cal_h").remove("cal_cy").remove("cal_r").apply();
    }

    // ------------------------------------------------------------------ overlay callbacks (main thread)

    @Override
    public void onCloseRequested() {
        stopSelf();
    }

    @Override
    public void onResetRequested() {
        tracker.reset();
        if (overlay != null) overlay.setExpanded(false, 1f);
        if (bg != null) bg.post(() -> expanded = false);
        lastStatus = "";
        if (overlay != null) overlay.update(tracker.snapshot(), "Zurückgesetzt", debug);
    }

    @Override
    public void onDebugToggled() {
        debug = !debug;
        lastStatus = "";
        if (overlay != null) overlay.update(tracker.snapshot(), debug ? "Diagnose an" : "Diagnose aus", debug);
    }

    // ------------------------------------------------------------------ teardown

    @Override
    public void onDestroy() {
        running = false;
        if (overlay != null) {
            overlay.hide();
            overlay = null;
        }
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (bg != null) bg.removeCallbacksAndMessages(null);
        final ImageReader r = reader;
        reader = null;
        if (worker != null) {
            // close the reader on its own thread so no frame is processed concurrently
            Handler h = bg;
            h.post(() -> {
                if (r != null) r.close();
            });
            worker.quitSafely();
            worker = null;
        } else if (r != null) {
            r.close();
        }
        if (projection != null) {
            MediaProjection p = projection;
            projection = null;
            try {
                p.stop();
            } catch (RuntimeException ignored) {
            }
        }
        super.onDestroy();
    }
}
