package io.github.christianlinzatti.glyphscanner.core;

/**
 * Ingress glyph grid: 11 nodes, numbered like gm9/ingress-glyph-tools (MIT):
 *
 *            0
 *      5           1
 *         9     6
 *            10
 *         8     7
 *      4           2
 *            3
 *
 * A glyph is stored as a bitmask over the "base edges" (pairs of nodes whose
 * connecting segment does not pass through another node).
 */
public final class GlyphGeometry {

    public static final int NODES = 11;
    private static final double RT3 = Math.sqrt(3.0);

    /** Ideal node positions, radius 1, y pointing down. */
    public static final double[][] POS = {
            {0, -1},
            {RT3 / 2, -0.5},
            {RT3 / 2, 0.5},
            {0, 1},
            {-RT3 / 2, 0.5},
            {-RT3 / 2, -0.5},
            {RT3 / 4, -0.25},
            {RT3 / 4, 0.25},
            {-RT3 / 4, 0.25},
            {-RT3 / 4, -0.25},
            {0, 0}
    };

    public static final int EDGE_COUNT;
    public static final int[] EDGE_A;
    public static final int[] EDGE_B;
    private static final int[][] EDGE_INDEX = new int[NODES][NODES];

    static {
        int[] a = new int[NODES * NODES];
        int[] b = new int[NODES * NODES];
        int n = 0;
        for (int i = 0; i < NODES; i++) {
            for (int j = 0; j < NODES; j++) EDGE_INDEX[i][j] = -1;
        }
        for (int i = 0; i < NODES; i++) {
            for (int j = i + 1; j < NODES; j++) {
                boolean blocked = false;
                for (int k = 0; k < NODES && !blocked; k++) {
                    if (k != i && k != j && between(i, j, k)) blocked = true;
                }
                if (!blocked) {
                    a[n] = i;
                    b[n] = j;
                    EDGE_INDEX[i][j] = n;
                    EDGE_INDEX[j][i] = n;
                    n++;
                }
            }
        }
        EDGE_COUNT = n;
        EDGE_A = new int[n];
        EDGE_B = new int[n];
        System.arraycopy(a, 0, EDGE_A, 0, n);
        System.arraycopy(b, 0, EDGE_B, 0, n);
    }

    private GlyphGeometry() {
    }

    /** True if node k lies strictly between nodes i and j. */
    private static boolean between(int i, int j, int k) {
        double ax = POS[i][0], ay = POS[i][1];
        double bx = POS[j][0], by = POS[j][1];
        double px = POS[k][0], py = POS[k][1];
        double dx = bx - ax, dy = by - ay;
        double cross = dx * (py - ay) - dy * (px - ax);
        if (Math.abs(cross) > 1e-6) return false;
        double dot = (px - ax) * dx + (py - ay) * dy;
        double len2 = dx * dx + dy * dy;
        return dot > 1e-6 && dot < len2 - 1e-6;
    }

    public static int edgeIndex(int a, int b) {
        return EDGE_INDEX[a][b];
    }

    /** Bitmask for a straight stroke from a to b (split at intermediate nodes). */
    public static long stroke(int a, int b) {
        if (a == b) return 0L;
        int idx = EDGE_INDEX[a][b];
        if (idx >= 0) return 1L << idx;
        // find nodes on the segment, sorted by distance from a
        int[] nodes = new int[NODES];
        double[] t = new double[NODES];
        int c = 0;
        double dx = POS[b][0] - POS[a][0], dy = POS[b][1] - POS[a][1];
        for (int k = 0; k < NODES; k++) {
            if (k != a && k != b && between(a, b, k)) {
                nodes[c] = k;
                t[c] = (POS[k][0] - POS[a][0]) * dx + (POS[k][1] - POS[a][1]) * dy;
                c++;
            }
        }
        for (int i = 0; i < c; i++) {
            for (int j = i + 1; j < c; j++) {
                if (t[j] < t[i]) {
                    double tt = t[i]; t[i] = t[j]; t[j] = tt;
                    int nn = nodes[i]; nodes[i] = nodes[j]; nodes[j] = nn;
                }
            }
        }
        long mask = 0;
        int prev = a;
        for (int i = 0; i < c; i++) {
            mask |= 1L << EDGE_INDEX[prev][nodes[i]];
            prev = nodes[i];
        }
        mask |= 1L << EDGE_INDEX[prev][b];
        return mask;
    }

    /** Parses a gm9 style code: pairs of base-11 digits, e.g. "0a3a" or "78". */
    public static long parseCode(String code) {
        long mask = 0;
        for (int i = 0; i + 1 < code.length(); i += 2) {
            int a = Character.digit(code.charAt(i), 11);
            int b = Character.digit(code.charAt(i + 1), 11);
            if (a < 0 || b < 0) throw new IllegalArgumentException("bad glyph code " + code);
            mask |= stroke(a, b);
        }
        return mask;
    }

    public static String toCode(long mask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < EDGE_COUNT; i++) {
            if ((mask & (1L << i)) != 0) {
                sb.append(Character.forDigit(EDGE_A[i], 11)).append(Character.forDigit(EDGE_B[i], 11));
            }
        }
        return sb.toString();
    }
}
