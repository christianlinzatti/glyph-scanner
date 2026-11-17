package io.github.christianlinzatti.glyphscanner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import io.github.christianlinzatti.glyphscanner.core.GlyphDictionary;
import io.github.christianlinzatti.glyphscanner.core.GlyphGeometry;
import io.github.christianlinzatti.glyphscanner.core.SequenceTracker;

/** The strip at the top of the screen: [X][reset]  glyph1 glyph2 ... with names. */
final class GlyphStripView extends View {

    interface Listener {
        void onCloseClicked();

        void onResetClicked();

        void onDebugToggled();

        void onDragStart();

        void onDrag(float dy);

        void onDragEnd();
    }

    private static final int MAX_SLOTS = 5;
    private static final float SQRT3 = (float) Math.sqrt(3);

    private final Listener listener;
    private final float dp;
    private final int touchSlop;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hexFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hexStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint label = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint small = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint symbol = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    private SequenceTracker.State state = SequenceTracker.State.empty();
    private String status = "";
    private boolean debug;
    private boolean showNames = true;
    private final float baseLabelSize;

    // button geometry, updated in onDraw
    private float closeCx, resetCx, btnCy, btnR;

    private float downRawY;
    private boolean dragging, longPressFired;
    private final Runnable longPress = new Runnable() {
        @Override
        public void run() {
            longPressFired = true;
            listener.onDebugToggled();
        }
    };

    GlyphStripView(Context ctx, Listener listener) {
        super(ctx);
        this.listener = listener;
        dp = ctx.getResources().getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(ctx).getScaledTouchSlop();

        bg.setColor(0xD90A0D12);
        hexFill.setStyle(Paint.Style.FILL);
        hexStroke.setStyle(Paint.Style.STROKE);
        hexStroke.setStrokeJoin(Paint.Join.ROUND);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setColor(0xFFF2FFFB);
        dot.setStyle(Paint.Style.FILL);
        label.setColor(Color.WHITE);
        label.setTextAlign(Paint.Align.LEFT);
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        label.setFakeBoldText(true);
        baseLabelSize = label.getTextSize();
        small.setColor(0xFFB8C4CC);
        small.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 9, getResources().getDisplayMetrics()));
        symbol.setColor(Color.WHITE);
        symbol.setStyle(Paint.Style.STROKE);
        symbol.setStrokeCap(Paint.Cap.ROUND);
    }

    void setData(SequenceTracker.State state, String status, boolean debug, boolean showNames) {
        this.showNames = showNames;
        this.state = state;
        this.status = status == null ? "" : status;
        this.debug = debug;
        invalidate();
    }

    // ------------------------------------------------------------------ drawing

    private void hexPath(float cx, float cy, float r) {
        path.reset();
        for (int i = 0; i < 6; i++) {
            double a = Math.toRadians(-90 + 60 * i);
            float x = cx + (float) Math.cos(a) * r, y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float pad = 4 * dp;
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, 10 * dp, 10 * dp, bg);

        // label grows with the strip (large mode while drawing)
        label.setTextSize(Math.max(baseLabelSize, Math.min(h * 0.11f, baseLabelSize * 1.8f)));
        float labelH = showNames ? label.getTextSize() * 1.35f : 0f;

        // buttons: two units of 0.7, glyph slots share the rest
        float unit = (w - 2 * pad - 6 * dp) / (MAX_SLOTS + 1.4f);
        float slotsLeft = pad + 1.4f * unit + 6 * dp;
        float slotsWidth = w - pad - slotsLeft;
        int n = Math.min(state.count, MAX_SLOTS);
        float slotW = slotsWidth / Math.max(n, 3);
        float hexR = Math.min(slotW / SQRT3 * 0.96f, (h - labelH - 2 * pad) / 2f);
        hexR = Math.max(hexR, 8 * dp);
        float hexCy = pad + hexR + Math.max(0f, (h - 2 * pad - labelH - 2 * hexR) / 2f);

        // ---- buttons
        btnR = Math.min(0.7f * unit / SQRT3 * 0.92f, hexR * 0.8f);
        btnCy = hexCy;
        closeCx = pad + 0.35f * unit;
        resetCx = pad + 1.05f * unit;
        drawButton(c, closeCx, btnCy, btnR, true);
        drawButton(c, resetCx, btnCy, btnR, false);
        // status under the buttons
        float statusW = debug ? w - 2 * pad : 1.4f * unit;
        String st = TextUtils.ellipsize(status, small, statusW, TextUtils.TruncateAt.END).toString();
        small.setColor(debug ? 0xFFFFE08A : 0xFFB8C4CC);
        c.drawText(st, pad, h - pad - small.descent(), small);

        // ---- glyph slots
        if (n == 0) {
            String hint = state.onGlyphScreen ? "Lese Glyph-Sequenz…" : "Warte auf Glyph-Hack…";
            label.setColor(0xFF8FA3AD);
            label.setTextSize(baseLabelSize);
            c.drawText(hint, slotsLeft + 4 * dp, h / 2f + label.getTextSize() / 3, label);
            label.setColor(Color.WHITE);
            return;
        }
        float start = slotsLeft + (slotsWidth - n * slotW) / 2f;
        for (int i = 0; i < n; i++) {
            float cx = start + slotW * (i + 0.5f);
            long g = state.glyphs[i];
            String name = g == 0 ? null : GlyphDictionary.shortName(g);
            boolean inputNow = state.inputIndex == i;
            boolean showingNow = state.displayIndex == i;

            hexPath(cx, hexCy, hexR);
            if (g == 0) hexFill.setColor(0x33223344);
            else if (name != null) hexFill.setColor(0xE01B5E52);
            else hexFill.setColor(0xE04A2F78);
            c.drawPath(path, hexFill);

            if (inputNow) {
                hexStroke.setColor(0xFFFFB43C);
                hexStroke.setStrokeWidth(3 * dp);
            } else if (showingNow) {
                hexStroke.setColor(0xFFFFFFFF);
                hexStroke.setStrokeWidth(2 * dp);
            } else {
                hexStroke.setColor(g == 0 ? 0x806F7F8F : 0xFF6FE3C6);
                hexStroke.setStrokeWidth(1.5f * dp);
            }
            c.drawPath(path, hexStroke);

            drawGlyph(c, cx, hexCy, hexR * 0.74f, g);

            String text;
            if (g == 0) text = showingNow ? "…" : "";
            else text = name != null ? name : "?";
            if (showNames && !text.isEmpty()) {
                label.setColor(inputNow ? 0xFFFFC65C : Color.WHITE);
                CharSequence t = TextUtils.ellipsize(text, label, slotW - 2 * dp, TextUtils.TruncateAt.END);
                float tw = label.measureText(t, 0, t.length());
                c.drawText(t, 0, t.length(), cx - tw / 2f, hexCy + hexR + labelH * 0.8f, label);
            }
        }
        label.setColor(Color.WHITE);
    }

    private void drawGlyph(Canvas c, float cx, float cy, float s, long g) {
        dot.setColor(0x55FFFFFF);
        float dr = Math.max(1.2f * dp, s * 0.045f);
        for (int k = 0; k < GlyphGeometry.NODES; k++) {
            c.drawCircle(cx + (float) GlyphGeometry.POS[k][0] * s, cy + (float) GlyphGeometry.POS[k][1] * s, dr, dot);
        }
        if (g == 0) return;
        line.setStrokeWidth(Math.max(1.8f * dp, s * 0.07f));
        boolean[] used = new boolean[GlyphGeometry.NODES];
        for (int e = 0; e < GlyphGeometry.EDGE_COUNT; e++) {
            if ((g & (1L << e)) == 0) continue;
            int a = GlyphGeometry.EDGE_A[e], b = GlyphGeometry.EDGE_B[e];
            used[a] = used[b] = true;
            c.drawLine(cx + (float) GlyphGeometry.POS[a][0] * s, cy + (float) GlyphGeometry.POS[a][1] * s,
                    cx + (float) GlyphGeometry.POS[b][0] * s, cy + (float) GlyphGeometry.POS[b][1] * s, line);
        }
        dot.setColor(0xFFFFFFFF);
        for (int k = 0; k < GlyphGeometry.NODES; k++) {
            if (used[k]) {
                c.drawCircle(cx + (float) GlyphGeometry.POS[k][0] * s, cy + (float) GlyphGeometry.POS[k][1] * s, dr * 1.6f, dot);
            }
        }
    }

    private void drawButton(Canvas c, float cx, float cy, float r, boolean close) {
        hexPath(cx, cy, r);
        hexFill.setColor(0xE07A5C1E);
        c.drawPath(path, hexFill);
        hexStroke.setColor(0xFFC9A44A);
        hexStroke.setStrokeWidth(1.5f * dp);
        c.drawPath(path, hexStroke);
        symbol.setStrokeWidth(2.5f * dp);
        float s = r * 0.38f;
        if (close) {
            c.drawLine(cx - s, cy - s, cx + s, cy + s, symbol);
            c.drawLine(cx - s, cy + s, cx + s, cy - s, symbol);
        } else {
            rect.set(cx - s, cy - s, cx + s, cy + s);
            c.drawArc(rect, -60, 300, false, symbol);
            // arrow head at the start of the arc (angle -60deg)
            double a = Math.toRadians(-60);
            float ex = cx + (float) Math.cos(a) * s, ey = cy + (float) Math.sin(a) * s;
            c.drawLine(ex, ey, ex - s * 0.55f, ey - s * 0.05f, symbol);
            c.drawLine(ex, ey, ex + s * 0.05f, ey + s * 0.55f, symbol);
        }
    }

    // ------------------------------------------------------------------ touch

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawY = ev.getRawY();
                dragging = false;
                longPressFired = false;
                if (hit(ev, resetCx)) postDelayed(longPress, 600);
                listener.onDragStart();
                return true;
            case MotionEvent.ACTION_MOVE: {
                float dy = ev.getRawY() - downRawY;
                if (!dragging && Math.abs(dy) > touchSlop) {
                    dragging = true;
                    removeCallbacks(longPress);
                }
                if (dragging) listener.onDrag(dy);
                return true;
            }
            case MotionEvent.ACTION_UP:
                removeCallbacks(longPress);
                if (dragging) {
                    listener.onDragEnd();
                } else if (!longPressFired) {
                    if (hit(ev, closeCx)) listener.onCloseClicked();
                    else if (hit(ev, resetCx)) listener.onResetClicked();
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPress);
                if (dragging) listener.onDragEnd();
                return true;
            default:
                return true;
        }
    }

    private boolean hit(MotionEvent ev, float cx) {
        float dx = ev.getX() - cx, dy = ev.getY() - btnCy;
        float rr = btnR * 1.25f;
        return dx * dx + dy * dy <= rr * rr;
    }
}
