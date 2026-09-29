package com.glyphui.graphics;

import io.github.humbleui.skija.Surface;

/**
 * Test helper that creates a {@link Canvas} backed by an in-memory Skia raster surface.
 *
 * <p>This avoids any GLFW/OpenGL windowing while still exercising the real Skija
 * native code paths (rasterization happens on the CPU). If the Skija native library
 * cannot be loaded (e.g. missing libEGL on the host), {@link #isAvailable()} returns
 * false and tests using this factory should be skipped via
 * {@code Assumptions.assumeTrue(...)}.</p>
 */
public final class RasterCanvasTestFactory {

    /** Fixed size used for all raster test surfaces. */
    public static final int SIZE = 256;

    private RasterCanvasTestFactory() {
    }

    /**
     * @return true if the Skija native library can be loaded on this machine
     */
    public static boolean isAvailable() {
        try {
            try (Surface s = Surface.makeRasterN32Premul(1, 1)) {
                return s != null;
            }
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Creates a fresh raster-backed {@link Canvas}. The caller is responsible for
     * closing the returned surface (e.g. with try-with-resources) when done.
     *
     * @param surfaceHolder single-element array that receives the created
     *                      {@link Surface} so the caller can close it
     * @return a Canvas wrapper around the raster surface
     */
    public static Canvas create(Surface[] surfaceHolder) {
        Surface surface = Surface.makeRasterN32Premul(SIZE, SIZE);
        surfaceHolder[0] = surface;
        return new Canvas(surface.getCanvas(), surface, SIZE, SIZE);
    }
}
