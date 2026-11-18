package io.github.christianlinzatti.glyphscanner;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/** Quick settings tile: start (incl. Ingress) or stop the scanner. */
public class GlyphTileService extends TileService {

    static void refresh(Context ctx) {
        try {
            TileService.requestListeningState(ctx, new ComponentName(ctx, GlyphTileService.class));
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setState(CaptureService.running ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        if (Build.VERSION.SDK_INT >= 29) t.setSubtitle(CaptureService.running ? "läuft" : "aus");
        t.updateTile();
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @SuppressWarnings("deprecation")
    @Override
    public void onClick() {
        if (CaptureService.running) {
            startService(new Intent(this, CaptureService.class).setAction(CaptureService.ACTION_STOP));
            Tile t = getQsTile();
            if (t != null) {
                t.setState(Tile.STATE_INACTIVE);
                t.updateTile();
            }
            return;
        }
        Intent i = new Intent(this, StartActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
            startActivityAndCollapse(pi);
        } else {
            startActivityAndCollapse(i);
        }
    }
}
