package io.github.christianlinzatti.glyphscanner;

import java.nio.ByteBuffer;

import io.github.christianlinzatti.glyphscanner.core.Frame;

/** Wraps an RGBA_8888 image plane without copying it. */
final class ImageFrame implements Frame {
    private final ByteBuffer buf;
    private final int w, h, pixelStride, rowStride;

    ImageFrame(ByteBuffer buf, int w, int h, int pixelStride, int rowStride) {
        this.buf = buf;
        this.w = w;
        this.h = h;
        this.pixelStride = pixelStride;
        this.rowStride = rowStride;
    }

    @Override
    public int width() {
        return w;
    }

    @Override
    public int height() {
        return h;
    }

    @Override
    public int rgb(int x, int y) {
        int i = y * rowStride + x * pixelStride;
        return ((buf.get(i) & 0xff) << 16) | ((buf.get(i + 1) & 0xff) << 8) | (buf.get(i + 2) & 0xff);
    }
}
