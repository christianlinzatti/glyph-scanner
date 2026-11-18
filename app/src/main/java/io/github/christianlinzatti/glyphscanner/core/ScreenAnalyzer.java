package io.github.christianlinzatti.glyphscanner.core;

/**
 * Finds the Ingress glyph hack screen in a frame and reads
 *  - the hexagon row (sequence length + highlighted slot),
 *  - whether the countdown timer is visible (input phase),
 *  - which edges of the 11-node grid are lit.
 *
 * All sizes are relative to the frame, so it works on any resolution and on
 * down-scaled captures.
 */
public final class ScreenAnalyzer {

    // Defaults measured on a 1008x2244 portrait screenshot of the glyph screen.
    private static final float DEF_CY = 1270f / 2244f; // grid centre, fraction of height
    private static final float DEF_R = 453f / 1008f;   // centre->top node, fraction of width
    /** In-game grid is slightly narrower than a regular hexagon. */
    private static final float KX = 0.961f;

    private int frameW = -1, frameH = -1;
    private boolean calibrated;
    private float cx, cy, r;
    private int hexY = -1;

    // ---------------------------------------------------------------- calibration state

    public boolean isCalibrated() {
        return calibrated;
    }

    public int calibrationWidth() {
        return frameW;
    }

    public int calibrationHeight() {
        return frameH;
    }

    public float centerY() {
        return cy;
    }

    public float radius() {
        return r;
    }

    /** Restores a calibration saved earlier for the given frame size. */
    public void setCalibration(int w, int h, float centerY, float radius) {
        frameW = w;
        frameH = h;
        cx = w / 2f;
        cy = centerY;
        r = radius;
        calibrated = true;
        hexY = -1;
    }

    public void resetCalibration() {
        frameW = -1;
        frameH = -1;
        calibrated = false;
        hexY = -1;
    }

    public float nodeX(int k) {
        return cx + (float) GlyphGeometry.POS[k][0] * r * KX;
    }

    public float nodeY(int k) {
        return cy + (float) GlyphGeometry.POS[k][1] * r;
    }

    // ---------------------------------------------------------------- main entry

    public Analysis analyze(Frame f) {
        int w = f.width(), h = f.height();
        if (w < 100 || h < 100 || w > h) return Analysis.none("kein Hochformat");
        if (w != frameW || h != frameH) {
            frameW = w;
            frameH = h;
            calibrated = false;
            hexY = -1;
            cx = w / 2f;
            cy = DEF_CY * h;
            r = DEF_R * w;
        }

        int[] row = findHexRow(f);
        if (row == null) {
            int nodes = countNodes(f);
            if (nodes >= 9) {
                long edges = detectEdges(f);
                return new Analysis(false, timerVisible(f), 0, -1, edges, true,
                        "Gitter " + nodes + "/11, Reihe verdeckt | " + Long.bitCount(edges) + " Linien");
            }
            return Analysis.none("keine Glyph-Reihe");
        }
        int count = row[0], current = row[1];

        if (!calibrated) tryCalibrate(f);
        int present = countNodes(f);
        if (present < 9 && calibrated) {
            // calibration may be stale (different app layout) -> try again
            if (tryCalibrate(f)) present = countNodes(f);
        }
        if (present < 9) {
            return Analysis.none("Reihe " + count + ", Gitter " + present + "/11");
        }

        boolean input = timerVisible(f);
        long edges = detectEdges(f);
        String dbg = (input ? "Eingabe" : "Anzeige") + " " + (current + 1) + "/" + count
                + " | Gitter " + present + "/11" + (calibrated ? "" : "*")
                + " | " + Long.bitCount(edges) + " Linien";
        return new Analysis(true, input, count, current, edges, dbg);
    }

    // ---------------------------------------------------------------- colours

    private static boolean isOrange(int c) {
        int R = (c >> 16) & 0xff, G = (c >> 8) & 0xff, B = c & 0xff;
        return R >= 170 && G >= 90 && G <= 215 && B <= 140 && R - B >= 90;
    }

    private static boolean isLavender(int c) {
        int R = (c >> 16) & 0xff, G = (c >> 8) & 0xff, B = c & 0xff;
        return R >= 150 && B >= 190 && B - G >= 25 && B - R >= 10;
    }

    private static boolean isPink(int c) {
        int R = (c >> 16) & 0xff, G = (c >> 8) & 0xff, B = c & 0xff;
        return R >= 200 && G <= 130 && B >= 70 && B <= 200 && R - G >= 100;
    }

    /** Lit glyph stroke: white core / light purple body. */
    private static boolean isLine(int c) {
        int R = (c >> 16) & 0xff, G = (c >> 8) & 0xff, B = c & 0xff;
        return R >= 150 && G >= 90 && B >= 200;
    }

    private static boolean isBright(int c) {
        int R = (c >> 16) & 0xff, G = (c >> 8) & 0xff, B = c & 0xff;
        return R + G + B >= 540;
    }

    private static int px(Frame f, int x, int y) {
        if (x < 0) x = 0;
        if (y < 0) y = 0;
        if (x >= f.width()) x = f.width() - 1;
        if (y >= f.height()) y = f.height() - 1;
        return f.rgb(x, y);
    }

    // ---------------------------------------------------------------- hexagon row

    /** @return {count, currentIndex} or null. */
    private int[] findHexRow(Frame f) {
        if (hexY >= 0) {
            int[] res = rowAt(f, hexY);
            if (res != null) return res;
        }
        int h = f.height();
        int y0 = (int) (0.105f * h), y1 = (int) (0.215f * h);
        int step = Math.max(1, h / 900);
        int bestStart = -1, bestEnd = -1;
        int runStart = -1, runCount = -1, lastY = -1;
        for (int y = y0; y <= y1; y += step) {
            int[] res = rowAt(f, y);
            if (res != null && runStart >= 0 && res[0] == runCount) {
                lastY = y;
            } else if (res != null) {
                runStart = y;
                runCount = res[0];
                lastY = y;
            } else {
                runStart = -1;
                runCount = -1;
            }
            if (runStart >= 0 && lastY - runStart > bestEnd - bestStart) {
                bestStart = runStart;
                bestEnd = lastY;
            }
        }
        if (bestStart < 0) return null;
        // need a reasonably tall plateau (hexagon side), otherwise it's noise
        if (bestEnd - bestStart < 0.012f * h) return null;
        hexY = (bestStart + bestEnd) / 2;
        return rowAt(f, hexY);
    }

    private int[] rowAt(Frame f, int y) {
        int w = f.width();
        int mergeGap = Math.max(2, (int) (0.015f * w));
        int maxRuns = 16;
        int[] rs = new int[maxRuns], re = new int[maxRuns], ro = new int[maxRuns];
        int n = 0;
        int lastX = -1000;
        for (int x = 0; x < w; x++) {
            int c = f.rgb(x, y);
            boolean o = isOrange(c);
            if (!o && !isLavender(c)) continue;
            if (n > 0 && x - lastX <= mergeGap) {
                re[n - 1] = x;
            } else {
                if (n == maxRuns) return null;
                rs[n] = x;
                re[n] = x;
                ro[n] = 0;
                n++;
            }
            if (o) ro[n - 1]++;
            lastX = x;
        }
        if (n < 2 || (n & 1) != 0 || n > 12) return null;
        float[] c = new float[n];
        for (int i = 0; i < n; i++) {
            if (re[i] - rs[i] > 0.05f * w) return null;
            c[i] = (rs[i] + re[i]) / 2f;
        }
        int count = n / 2;
        float minD = Float.MAX_VALUE, maxD = 0;
        for (int k = 0; k < count; k++) {
            float d = c[2 * k + 1] - c[2 * k];
            minD = Math.min(minD, d);
            maxD = Math.max(maxD, d);
        }
        if (minD < 0.07f * w || maxD > 0.2f * w || maxD > minD * 1.3f) return null;
        for (int k = 0; k + 1 < count; k++) {
            float gap = c[2 * k + 2] - c[2 * k + 1];
            if (gap >= minD) return null;
        }
        float mid = (c[0] + c[n - 1]) / 2f;
        if (Math.abs(mid - w / 2f) > 0.06f * w) return null;
        int current = -1, best = 0;
        for (int k = 0; k < count; k++) {
            int o = ro[2 * k] + ro[2 * k + 1];
            if (o > best) {
                best = o;
                current = k;
            }
        }
        return new int[]{count, current};
    }

    // ---------------------------------------------------------------- timer

    private boolean timerVisible(Frame f) {
        int w = f.width(), h = f.height();
        int y0 = (int) (0.17f * h), y1 = (int) (0.26f * h);
        int step = Math.max(1, w / 400);
        int cnt = 0;
        for (int y = y0; y <= y1; y += step) {
            for (int x = 0; x < w; x += step) {
                if (isPink(f.rgb(x, y)) && ++cnt >= 15) return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- grid

    private int countNodes(Frame f) {
        int w = f.width();
        int rad = Math.max(2, (int) (0.03f * w));
        int step = Math.max(1, (int) (0.004f * w));
        int n = 0;
        for (int k = 0; k < GlyphGeometry.NODES; k++) {
            int x0 = Math.round(nodeX(k)), y0 = Math.round(nodeY(k));
            boolean found = false;
            for (int dy = -rad; dy <= rad && !found; dy += step) {
                for (int dx = -rad; dx <= rad; dx += step) {
                    if (isBright(px(f, x0 + dx, y0 + dy))) {
                        found = true;
                        break;
                    }
                }
            }
            if (found) n++;
        }
        return n;
    }

    /**
     * Finds the grid from the three nodes on the vertical centre line (top, centre, bottom).
     */
    private boolean tryCalibrate(Frame f) {
        int w = f.width(), h = f.height();
        int x0 = w / 2;
        int ys = (int) (0.25f * h), ye = (int) (0.88f * h);
        int mergeGap = Math.max(2, (int) (0.045f * w));
        int max = 32;
        int[] bs = new int[max], be = new int[max];
        int n = 0, last = -10000;
        for (int y = ys; y <= ye; y++) {
            boolean b = false;
            for (int dx = -2; dx <= 2 && !b; dx++) b = isBright(px(f, x0 + dx, y));
            if (!b) continue;
            if (n > 0 && y - last <= mergeGap) {
                be[n - 1] = y;
            } else {
                if (n == max) break;
                bs[n] = y;
                be[n] = y;
                n++;
            }
            last = y;
        }
        float saveCy = cy, saveR = r;
        boolean wasCal = calibrated;
        float bestCy = 0, bestR = 0;
        int bestScore = -1;
        for (int i = 0; i < n; i++) {
            if (be[i] - bs[i] > 0.1f * w) continue;
            for (int k = i + 2; k < n; k++) {
                if (be[k] - bs[k] > 0.1f * w) continue;
                float ci = (bs[i] + be[i]) / 2f, ck = (bs[k] + be[k]) / 2f;
                float rr = (ck - ci) / 2f;
                if (rr < 0.36f * w || rr > 0.54f * w) continue;
                for (int j = i + 1; j < k; j++) {
                    float cj = (bs[j] + be[j]) / 2f;
                    if (Math.abs((cj - ci) - (ck - cj)) > 0.025f * w) continue;
                    cy = cj;
                    r = rr;
                    int score = countNodes(f);
                    if (score > bestScore) {
                        bestScore = score;
                        bestCy = cj;
                        bestR = rr;
                    }
                }
            }
        }
        if (bestScore >= 10) {
            cx = w / 2f;
            cy = bestCy;
            r = bestR;
            calibrated = true;
            hexY = -1;
            return true;
        }
        cy = saveCy;
        r = saveR;
        calibrated = wasCal;
        return false;
    }

    // ---------------------------------------------------------------- edges

    private long detectEdges(Frame f) {
        int w = f.width();
        int win = Math.max(1, Math.round(0.007f * w));
        final int samples = 7;
        long mask = 0;
        for (int e = 0; e < GlyphGeometry.EDGE_COUNT; e++) {
            int a = GlyphGeometry.EDGE_A[e], b = GlyphGeometry.EDGE_B[e];
            float ax = nodeX(a), ay = nodeY(a), bx = nodeX(b), by = nodeY(b);
            int lit = 0;
            for (int s = 0; s < samples; s++) {
                float t = 0.25f + 0.5f * s / (samples - 1);
                int x = Math.round(ax + (bx - ax) * t);
                int y = Math.round(ay + (by - ay) * t);
                boolean on = false;
                for (int dy = -win; dy <= win && !on; dy += win) {
                    for (int dx = -win; dx <= win; dx += win) {
                        if (isLine(px(f, x + dx, y + dy))) {
                            on = true;
                            break;
                        }
                    }
                }
                if (on) lit++;
            }
            if (lit >= samples - 1) mask |= 1L << e;
        }
        return mask;
    }
}
