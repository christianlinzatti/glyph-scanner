package io.github.christianlinzatti.glyphscanner.core;

import java.util.HashMap;
import java.util.Map;

/**
 * Collects the glyphs shown during the "incoming glyph sequence" phase and keeps
 * them for the input phase. Each slot votes over the stable edge sets it saw, so
 * fade-in / fade-out frames do not win.
 */
public final class SequenceTracker {

    /** No glyph screen for this long -> the next display phase is a new hack. */
    private static final long NEW_SEQUENCE_GAP_MS = 3000;

    private int count;
    private Map<Long, Integer>[] votes;
    private long[] best = new long[0];

    /**
     * Set as soon as the display phase is over (timer seen, or the highlight jumped
     * back to an earlier slot). While locked, nothing on screen can change the glyphs,
     * so the player's own drawing never overwrites what was read.
     */
    private boolean locked;
    private int maxShownIndex = -1;
    private long lastGlyphScreenMs = -1;
    private int pendingCount = -1;
    private int jumpBackFrames;

    // stroke counting while the hexagon row is hidden (done slots turn yellow / our strip covers it)
    private int cursor = -1;
    private boolean strokeArmed;
    private int drawFrames, clearFrames;

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
        locked = false;
        maxShownIndex = -1;
        lastGlyphScreenMs = -1;
        pendingCount = -1;
        prevEdges = 0;
        prevIndex = -1;
        displayIndex = -1;
        inputIndex = -1;
        cursor = -1;
        strokeArmed = false;
    }

    @SuppressWarnings("unchecked")
    private void start(int n) {
        count = n;
        votes = new Map[n];
        for (int i = 0; i < n; i++) votes[i] = new HashMap<>();
        best = new long[n];
        locked = false;
        maxShownIndex = -1;
        pendingCount = -1;
        jumpBackFrames = 0;
        prevEdges = 0;
        prevIndex = -1;
        inputIndex = -1;
        cursor = -1;
        strokeArmed = false;
        drawFrames = 0;
        clearFrames = 0;
    }

    /** @return true if anything visible in the overlay changed. */
    public synchronized boolean update(Analysis a, long nowMs) {
        String before = signature();
        onGlyphScreen = a.glyphScreen;
        boolean gap = lastGlyphScreenMs < 0 || nowMs - lastGlyphScreenMs > NEW_SEQUENCE_GAP_MS;
        if (a.glyphScreen || a.gridVisible) lastGlyphScreenMs = nowMs;

        if (!a.glyphScreen || a.count <= 0) {
            displayIndex = -1;
            inputPhase = false;
            if (locked && count > 0) {
                if (cursor < 0) cursor = 0;
                countStrokes(a);
                inputIndex = cursor < count ? cursor : -1;
            } else {
                inputIndex = -1;
            }
            return !before.equals(signature());
        }

        boolean display = !a.inputPhase;
        if (display) {
            if (count == 0) {
                start(a.count);
            } else if (locked) {
                // a new hack only after a real pause, starting with the first glyph
                if (gap && a.current == 0) start(a.count);
                else display = false;
            } else if (gap && a.current == 0) {
                start(a.count);
            } else if (a.count != count) {
                // only trust a changed length if it is seen twice in a row
                if (pendingCount == a.count) start(a.count);
                else {
                    pendingCount = a.count;
                    display = false;
                }
            } else if (a.current >= 0 && a.current < maxShownIndex) {
                // highlight jumped back -> display phase is over, player is drawing
                // (confirmed on two frames so a single misread cannot end the display)
                if (++jumpBackFrames >= 2) locked = true;
                display = false;
            } else {
                jumpBackFrames = 0;
            }
        } else {
            if (count > 0) locked = true;
        }

        if (display && a.count == count && a.current >= 0 && a.current < count) {
            inputPhase = false;
            inputIndex = -1;
            displayIndex = a.current;
            maxShownIndex = Math.max(maxShownIndex, a.current);
            if (a.edges != 0 && a.edges == prevEdges && a.current == prevIndex) {
                Map<Long, Integer> v = votes[a.current];
                Integer old = v.get(a.edges);
                v.put(a.edges, old == null ? 1 : old + 1);
                best[a.current] = pick(v);
            }
            prevEdges = a.edges;
            prevIndex = a.current;
        } else if (!display) {
            inputPhase = locked;
            displayIndex = -1;
            inputIndex = (locked && a.count == count) ? a.current : -1;
            if (inputIndex >= 0) {
                // the hexagon row is the truth whenever we can see it
                cursor = inputIndex;
                strokeArmed = false;
                drawFrames = 0;
                clearFrames = 0;
            }
        }
        return !before.equals(signature());
    }

    /**
     * One drawn glyph = lines visible for a few frames, then the grid is empty (or gone)
     * for a few frames. Each such episode moves the cursor to the next slot.
     */
    private void countStrokes(Analysis a) {
        boolean drawing = a.gridVisible && a.edges != 0;
        if (drawing) {
            clearFrames = 0;
            if (++drawFrames >= 2) strokeArmed = true;
        } else {
            drawFrames = 0;
            if (strokeArmed && ++clearFrames >= 2) {
                cursor++;
                strokeArmed = false;
                clearFrames = 0;
            }
        }
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
                .append(onGlyphScreen).append(inputPhase).append(locked);
        for (long b : best) sb.append(',').append(b);
        return sb.toString();
    }

    /** Immutable view for drawing. */
    public synchronized State snapshot() {
        return new State(count, best.clone(), displayIndex, inputIndex, onGlyphScreen, inputPhase, locked);
    }

    public static final class State {
        public final int count;
        public final long[] glyphs;
        public final int displayIndex;
        public final int inputIndex;
        public final boolean onGlyphScreen;
        public final boolean inputPhase;
        /** Display phase is over; the glyphs are final. */
        public final boolean locked;

        State(int count, long[] glyphs, int displayIndex, int inputIndex, boolean onGlyphScreen,
              boolean inputPhase, boolean locked) {
            this.count = count;
            this.glyphs = glyphs;
            this.displayIndex = displayIndex;
            this.inputIndex = inputIndex;
            this.onGlyphScreen = onGlyphScreen;
            this.inputPhase = inputPhase;
            this.locked = locked;
        }

        public static State empty() {
            return new State(0, new long[0], -1, -1, false, false, false);
        }
    }
}
