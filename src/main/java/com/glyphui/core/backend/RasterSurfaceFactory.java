package com.glyphui.core.backend;

import com.glyphui.core.Window;
import io.github.humbleui.skija.Surface;

/**
 * CPU {@link SurfaceFactory}: allocates premultiplied N32 raster surfaces
 * with {@code Surface.makeRasterN32Premul}. No {@code DirectContext} is
 * involved (the result carries a null context), so there are no GL handles
 * to release on failure paths — allocation failures simply throw.
 *
 * <p>Used both when the application explicitly requests the raster backend
 * and as the automatic fallback when GPU initialization fails (headless CI,
 * missing drivers).</p>
 */
public class RasterSurfaceFactory implements SurfaceFactory {

    @Override
    public SurfaceResult create(Window window) {
        int fbWidth = window.getFramebufferWidth();
        int fbHeight = window.getFramebufferHeight();
        int logicalWidth = window.getWidth();
        int logicalHeight = window.getHeight();

        // Create raster surface at physical resolution (no OpenGL context needed).
        // Skija raster surfaces always store rows top-to-bottom, so pixel
        // sampling matches the UI coordinate system without any flip.
        Surface surface = Surface.makeRasterN32Premul(fbWidth, fbHeight);

        if (surface == null) {
            throw new RuntimeException("Failed to create raster surface");
        }

        return new SurfaceResult(surface, null,
            logicalWidth, logicalHeight, fbWidth, fbHeight);
    }

    @Override
    public SurfaceResult recreate(Window window, int fbWidth, int fbHeight) {
        // Logical size derived from the physical framebuffer via the DPI
        // factor. A null window (headless/test recreation) means no DPI
        // scaling is known, so logical == physical.
        float contentScaleX = window != null
            ? Math.max(window.getContentScaleX(), 1.0f) : 1.0f;
        float contentScaleY = window != null
            ? Math.max(window.getContentScaleY(), 1.0f) : 1.0f;
        int logicalWidth = Math.max(1, Math.round(fbWidth / contentScaleX));
        int logicalHeight = Math.max(1, Math.round(fbHeight / contentScaleY));

        // Same factory as create(): keep both allocation paths consistent
        // (makeRasterN32Premul is the idiomatic premultiplied N32 variant of
        // makeRaster(ImageInfo)).
        Surface surface = Surface.makeRasterN32Premul(fbWidth, fbHeight);

        if (surface == null) {
            throw new RuntimeException("Failed to recreate raster surface");
        }

        return new SurfaceResult(surface, null,
            logicalWidth, logicalHeight, fbWidth, fbHeight);
    }

    @Override
    public boolean isGpu() {
        return false;
    }

    @Override
    public void close() {
        // Nothing to release: raster surfaces carry no context and the
        // application owns the surface itself.
    }
}
