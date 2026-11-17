package io.github.christianlinzatti.glyphscanner.core;

/** Minimal read-only image access so the detector can run on Android and in plain Java tests. */
public interface Frame {
    int width();

    int height();

    /** Pixel as 0xRRGGBB. Coordinates are clamped by the caller. */
    int rgb(int x, int y);
}
