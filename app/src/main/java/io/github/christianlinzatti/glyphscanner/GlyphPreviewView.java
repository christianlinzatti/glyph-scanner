package io.github.christianlinzatti.glyphscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import io.github.christianlinzatti.glyphscanner.core.GlyphGeometry;

/** Draws one glyph large (used on the naming screen). */
final class GlyphPreviewView extends View {
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final long glyph;

    GlyphPreviewView(Context ctx, long glyph) {
        super(ctx);
        this.glyph = glyph;
        float dp = ctx.getResources().getDisplayMetrics().density;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeWidth(5 * dp);
        line.setColor(0xFFC9B3FF);
        label.setColor(0xFF8FA3AD);
        label.setTextSize(12 * dp);
        label.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    protected void onDraw(Canvas c) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float s = Math.min(getWidth(), getHeight()) * 0.42f;
        for (int e = 0; e < GlyphGeometry.EDGE_COUNT; e++) {
            if ((glyph & (1L << e)) == 0) continue;
            int a = GlyphGeometry.EDGE_A[e], b = GlyphGeometry.EDGE_B[e];
            c.drawLine(cx + (float) GlyphGeometry.POS[a][0] * s, cy + (float) GlyphGeometry.POS[a][1] * s,
                    cx + (float) GlyphGeometry.POS[b][0] * s, cy + (float) GlyphGeometry.POS[b][1] * s, line);
        }
        for (int k = 0; k < GlyphGeometry.NODES; k++) {
            float x = cx + (float) GlyphGeometry.POS[k][0] * s, y = cy + (float) GlyphGeometry.POS[k][1] * s;
            dot.setColor(0xFFFFFFFF);
            c.drawCircle(x, y, s * 0.045f, dot);
        }
    }
}
