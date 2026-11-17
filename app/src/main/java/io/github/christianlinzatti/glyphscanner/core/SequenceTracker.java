package io.github.christianlinzatti.glyphscanner.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects the glyphs shown during the "incoming glyph sequence" phase and keeps
 * them for the input phase. Each slot votes over the stable edge sets it saw, so
 * fade-in / fade-out frames do not win.
 */
public final class SequenceTracker {

    /** No glyph screen for this long while showing -> next display starts a new sequence. */
    private static final long NEW_SEQUENCE_GAP_MS = 2500;

    private int count;
    private Map<Long, Integer>[] votes;
    private long[] best = new long[0];

    private long lastDisplayMs = -1;
    private boolean sawInputSinceDisplay;
    private int pendingCount = -1;

    private long prevEdges;
    private int prevIndex = -1;

    private int displayIndex = -1;
    private int inputIndex = -1;
    private boolean onGlyphScreen;
    private boolean inputPhase;

    public synchronized void reset() {
        count = 0;
        votes = null;
        best = new long[0];
        lastDisplayMs = -1;
        sawInputSinceDisplay = false;
        pendingCount = -1;
        prevEdges = 0;
        prevIndex = -1;
        displayIndex = -1;
        inputIndex = -1;
    }

    @SuppressWarnings("unchecked")
    private void start(int n) {
        count = n;
        votes = new Map[n];
        for (int i = 0; i < n; i++) votes[i] = new HashMap<>();
        best = new long[n];
        prevEdges = 0;
        prevIndex = -1;
        sawInputSinceDisplay = false;
        inputIndex = -1;
    }

    /** @return true if anything visible in the overlay changed. */
    public synchronized boolean update(Analysis a, long nowMs) {
        String before = signature();
        onGlyphScreen = a.glyphScreen;

        if (a.glyphScreen && !a.inputPhase && a.count > 0) {
            inputPhase = false;
            inputIndex = -1;
            boolean gap = lastDisplayMs >= 0 && nowMs - lastDisplayMs > NEW_SEQUENCE_GAP_MS;
            boolean newSeq = count == 0 || sawInputSinceDisplay || gap;
            if (!newSeq && a.count != count) {
                // only trust a changed length if it is seen twice in a row
                if (pendingCount == a.count) newSeq = true;
                pendingCount = a.count;
            } else {
                pendingCount = -1;
            }
            if (newSeq) start(a.count);
            lastDisplayMs = nowMs;

            if (a.count == count && a.current >= 0 && a.current < count) {
                displayIndex = a.current;
                if (a.edges != 0 && a.edges == prevEdges && a.current == prevIndex) {
                    Map<Long, Integer> v = votes[a.current];
                    Integer old = v.get(a.edges);
                    v.put(a.edges, old == null ? 1 : old + 1);
                    best[a.current] = pick(v);
                }
                prevEdges = a.edges;
                prevIndex = a.current;
            }
        } else if (a.glyphScreen && a.inputPhase) {
            inputPhase = true;
            sawInputSinceDisplay = true;
            displayIndex = -1;
            inputIndex = (a.count == count) ? a.current : -1;
        } else {
            displayIndex = -1;
            inputIndex = -1;
            inputPhase = false;
        }
        return !before.equals(signature());
    }

    private static long pick(Map<Long, Integer> v) {
        long bestMask = 0;
        int bestVotes = -1;
        for (Map.Entry<Long, Integer> e : v.entrySet()) {
            int votes = e.getValue();
            long m = e.getKey();
            if (votes > bestVotes || (votes == bestVotes && Long.bitCount(m) > Long.bitCount(bestMask))) {
                bestVotes = votes;
                bestMask = m;
            }
        }
        return bestMask;
    }

    private String signature() {
        StringBuilder sb = new StringBuilder();
        sb.append(count).append('|').append(displayIndex).append('|').append(inputIndex).append('|')
                .append(onGlyphScreen).append(inputPhase);
        for (long b : best) sb.append(',').append(b);
        return sb.toString();
    }

    /** Immutable view for drawing. */
    public synchronized State snapshot() {
        return new State(count, best.clone(), displayIndex, inputIndex, onGlyphScreen, inputPhase);
    }

    public static final class State {
        public final int count;
        public final long[] glyphs;
        public final int displayIndex;
        public final int inputIndex;
        public final boolean onGlyphScreen;
        public final boolean inputPhase;

        State(int count, long[] glyphs, int displayIndex, int inputIndex, boolean onGlyphScreen, boolean inputPhase) {
            this.count = count;
            this.glyphs = glyphs;
            this.displayIndex = displayIndex;
            this.inputIndex = inputIndex;
            this.onGlyphScreen = onGlyphScreen;
            this.inputPhase = inputPhase;
        }

        public static State empty() {
            return new State(0, new long[0], -1, -1, false, false);
        }
    }
}
