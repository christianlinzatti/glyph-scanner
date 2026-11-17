package io.github.christianlinzatti.glyphscanner.core;

/** Result of analysing one screen frame. */
public final class Analysis {
    /** The Ingress glyph hack screen (hexagon row + node grid) is visible. */
    public final boolean glyphScreen;
    /** Timer visible: the player is drawing (input phase). Otherwise glyphs are being shown. */
    public final boolean inputPhase;
    /** Number of glyphs in the sequence (hexagons in the top row). */
    public final int count;
    /** Index of the highlighted (orange) hexagon, -1 if none. */
    public final int current;
    /** Detected edges of the glyph currently on screen (bitmask over base edges). */
    public final long edges;
    /** Short human readable diagnostics. */
    public final String debug;

    public Analysis(boolean glyphScreen, boolean inputPhase, int count, int current, long edges, String debug) {
        this.glyphScreen = glyphScreen;
        this.inputPhase = inputPhase;
        this.count = count;
        this.current = current;
        this.edges = edges;
        this.debug = debug;
    }

    public static Analysis none(String debug) {
        return new Analysis(false, false, 0, -1, 0L, debug);
    }
}
