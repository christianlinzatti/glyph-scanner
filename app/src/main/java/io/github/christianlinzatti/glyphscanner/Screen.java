package io.github.christianlinzatti.glyphscanner;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

final class Screen {
    private Screen() {
    }

    /** Full physical screen size in the current orientation (including system bars). */
    @SuppressWarnings("deprecation")
    static Point realSize(Context ctx) {
        WindowManager wm = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            Rect b = wm.getMaximumWindowMetrics().getBounds();
            return new Point(b.width(), b.height());
        }
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new Point(dm.widthPixels, dm.heightPixels);
    }

    static int statusBarHeight(Context ctx) {
        int id = ctx.getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? ctx.getResources().getDimensionPixelSize(id) : (int) (24 * ctx.getResources().getDisplayMetrics().density);
    }
}
