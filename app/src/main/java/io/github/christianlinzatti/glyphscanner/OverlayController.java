package io.github.christianlinzatti.glyphscanner;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.view.Gravity;
import android.view.WindowManager;

import io.github.christianlinzatti.glyphscanner.core.SequenceTracker;

/** Adds the glyph strip as a system overlay window and handles dragging. */
final class OverlayController implements GlyphStripView.Listener {

    interface Callbacks {
        void onCloseRequested();

        void onResetRequested();

        void onDebugToggled();
    }

    private static final String KEY_Y = "overlay_y";
    static final String KEY_SHOW_NAMES = "show_names";

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Callbacks callbacks;
    private final WindowManager wm;
    private GlyphStripView view;
    private WindowManager.LayoutParams lp;
    private int dragStartY;
    private int screenH;
    private int compactHeight;
    private boolean expanded;

    OverlayController(Context ctx, SharedPreferences prefs, Callbacks callbacks) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.callbacks = callbacks;
        this.wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
    }

    void show() {
        if (view != null) return;
        Point size = Screen.realSize(ctx);
        int screenW = Math.min(size.x, size.y);
        screenH = Math.max(size.x, size.y);
        int statusBar = Screen.statusBarHeight(ctx);
        float density = ctx.getResources().getDisplayMetrics().density;

        // Keep the strip above Ingress' hexagon row (starts at ~11.5 % of the screen height),
        // otherwise our own overlay would hide what we are trying to read.
        int maxH = (int) (0.112f * screenH) - statusBar;
        float slotW = screenW / 6.4f;
        int wanted = (int) (slotW * 1.05f + 20 * density);
        int height = Math.max((int) (48 * density), Math.min(wanted, maxH));
        compactHeight = height;
        expanded = false;

        view = new GlyphStripView(ctx, this);
        int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                height,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                flags,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        if (Build.VERSION.SDK_INT >= 28) {
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        if (Build.VERSION.SDK_INT >= 30) {
            lp.setFitInsetsTypes(0); // y is measured from the very top of the screen
        }
        lp.y = prefs.getInt(KEY_Y, statusBar);
        wm.addView(view, lp);
    }

    void hide() {
        if (view == null) return;
        try {
            wm.removeView(view);
        } catch (RuntimeException ignored) {
        }
        view = null;
    }

    /**
     * Large mode for the drawing phase. The glyphs are already locked then, so the strip may
     * cover Ingress' hexagon row; it stops above the countdown (~18.5 % of the height).
     */
    void setExpanded(boolean expand, float scale) {
        if (view == null) return;
        int target = compactHeight;
        if (expand && scale > 1.01f) {
            int limit = lp.y < 0.185f * screenH
                    ? (int) (0.185f * screenH) - lp.y
                    : screenH - lp.y;
            target = Math.max(compactHeight, Math.min(Math.round(compactHeight * scale), limit));
        }
        if (expanded == expand && lp.height == target) return;
        expanded = expand;
        lp.height = target;
        try {
            wm.updateViewLayout(view, lp);
        } catch (RuntimeException ignored) {
        }
    }

    void update(SequenceTracker.State state, String status, boolean debug) {
        if (view != null) view.setData(state, status, debug, prefs.getBoolean(KEY_SHOW_NAMES, true));
    }

    @Override
    public void onCloseClicked() {
        callbacks.onCloseRequested();
    }

    @Override
    public void onResetClicked() {
        callbacks.onResetRequested();
    }

    @Override
    public void onDebugToggled() {
        callbacks.onDebugToggled();
    }

    @Override
    public void onDragStart() {
        if (lp != null) dragStartY = lp.y;
    }

    @Override
    public void onDrag(float dy) {
        if (view == null) return;
        int y = Math.round(dragStartY + dy);
        y = Math.max(0, Math.min(y, screenH - lp.height));
        lp.y = y;
        try {
            wm.updateViewLayout(view, lp);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onDragEnd() {
        if (lp != null) prefs.edit().putInt(KEY_Y, lp.y).apply();
    }

    static void resetPosition(SharedPreferences prefs) {
        prefs.edit().remove(KEY_Y).apply();
    }
}
