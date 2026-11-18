package io.github.christianlinzatti.glyphscanner;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/**
 * Invisible activity for quick start (tile, launcher shortcut, start button):
 * asks for screen capture, starts the service and opens Ingress.
 */
public class StartActivity extends Activity {

    private static final int REQ_CAPTURE = 1;
    static final String INGRESS_PACKAGE = "com.nianticproject.ingress";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) return; // capture dialog already open
        if (CaptureService.running) {
            openIngress();
            finish();
            return;
        }
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Bitte zuerst die Overlay-Berechtigung erteilen", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent svc = new Intent(this, CaptureService.class)
                        .setAction(CaptureService.ACTION_START)
                        .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                        .putExtra(CaptureService.EXTRA_RESULT_DATA, data);
                startForegroundService(svc);
                SharedPreferences prefs = getSharedPreferences(CaptureService.PREFS, MODE_PRIVATE);
                if (prefs.getBoolean("launch_ingress", true)) openIngress();
            } else {
                Toast.makeText(this, "Bildschirmaufnahme wurde nicht erlaubt", Toast.LENGTH_SHORT).show();
            }
        }
        finish();
    }

    private void openIngress() {
        Intent ingress = getPackageManager().getLaunchIntentForPackage(INGRESS_PACKAGE);
        if (ingress != null) startActivity(ingress);
    }
}
