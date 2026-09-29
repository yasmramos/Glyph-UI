package com.glyphui.integration;

import com.glyphui.core.Application;
import com.glyphui.graphics.Canvas;
import com.glyphui.ui.Panel;
import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.ImageInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for HiDPI rendering: with a simulated content scale of
 * 2.0, {@code Application.render()} must apply {@code Canvas.scale(2,2)} to
 * the native canvas and paint the (logical) UI into the (physical) raster
 * surface at double resolution — verified by reading back pixels.
 *
 * <p>Requires GLFW (skipped in headless environments).</p>
 */
public class HiDpiRenderIT {

    private Application app;

    @BeforeEach
    public void setUp() {
        app = new Application();
        boolean initialized = false;
        try {
            initialized = app.init("HiDPI IT", 400, 300, true);
        } catch (Throwable t) {
            // GLFW/native libraries unavailable in this environment
            Assumptions.abort("GLFW not available (headless environment); skipping test: " + t);
        }
        Assumptions.assumeTrue(initialized, "GLFW not available (headless environment); skipping test");
    }

    @AfterEach
    public void tearDown() {
        if (app != null && app.getWindow() != null) {
            app.destroy();
        }
    }

    /** Reads one premultiplied RGBA pixel from the app's raster surface. */
    private int readPixel(Canvas wrapper, int physX, int physY) {
        io.github.humbleui.skija.Surface skijaSurface = wrapper.getSurface();
        int w = skijaSurface.getWidth();
        int h = skijaSurface.getHeight();
        Bitmap full = new Bitmap();
        try {
            assertTrue(full.allocN32Pixels(w, h), "allocN32Pixels failed");
            assertTrue(skijaSurface.readPixels(full, 0, 0), "readPixels failed");
            ByteBuffer buf = full.peekPixels();
            buf.rewind();
            byte[] px = new byte[buf.remaining()];
            buf.get(px);
            int off = (physY * w + physX) * 4;
            // N32 premultiplied on little-endian JVMs: bytes are [B, G, R, A].
            // Read as a little-endian ARGB int so 0xFFFF0000 == opaque red.
            int argb = (px[off] & 0xFF) | ((px[off + 1] & 0xFF) << 8)
                     | ((px[off + 2] & 0xFF) << 16) | ((px[off + 3] & 0xFF) << 24);
            return argb;
        } finally {
            full.close();
        }
    }

    @Test
    public void testContentScaleTwoIsAppliedDuringRender() {
        Panel root = app.getRootPanel();
        // The root panel paints its background rect in logical coordinates;
        // with content scale 2.0 it must cover physical pixels up to 2x.
        // NOTE: the panel is sized to the LOGICAL window size (400x300), so
        // the red rect spans logical [0..400)x[0..300) = physical [0..800)x
        // [0..600). A pixel at physical x>=800 would be outside it, but the
        // surface is exactly 800 wide, so scaling is proven by sampling deep
        // inside the physical buffer (see below).
        root.setBackgroundColor(0xFFFF0000); // opaque red, logical 400x300

        // Simulate a 2.0x HiDPI display on the raster path: physical
        // framebuffer = logical * 2. Grow the root panel to the full logical
        // window size so its background covers every logical pixel.
        app.getWindow().setContentScale(2.0f, 2.0f);
        app.getWindow().setSizes(app.getWindow().getWidth(), app.getWindow().getHeight(),
                app.getWindow().getWidth() * 2, app.getWindow().getHeight() * 2);
        root.setWidth(app.getWindow().getWidth());
        root.setHeight(app.getWindow().getHeight());
        app.recreateRasterSurfaceForTesting();

        Canvas wrapper = app.getCanvas();
        assertEquals(400, wrapper.getWidth(), "canvas wrapper stays logical");
        assertEquals(800, wrapper.getSurface().getWidth(), "surface is physical (2x)");

        app.renderFrame();

        // Inside the scaled region: logical (10,10) -> physical (20,20) is red.
        // The bitmap stores premultiplied RGBA; extract RGB from bytes 1..3.
        int inside = readPixel(wrapper, 20, 20);
        assertEquals(0xFF0000, inside & 0xFFFFFF,
                "Canvas.scale(2,2) must map logical drawing to physical pixels");

        // Physical pixel (799, 599) corresponds to logical (~399.5, ~299.5):
        // only painted red if the UI was scaled into the full 2x buffer. If
        // no scale were applied, this area would show the dark clear color.
        int farCorner = readPixel(wrapper, 799, 599);
        assertEquals(0xFF0000, farCorner & 0xFFFFFF,
                "physical pixel 799 corresponds to logical ~400 and must be painted");

        // With the background cleared (transparent), the render() clear color
        // (dark gray RGB 30,30,30) must show through instead of red.
        root.setBackgroundColor(io.github.humbleui.skija.Color.makeARGB(0, 0, 0, 0));
        app.renderFrame();
        int cleared = readPixel(wrapper, 20, 20);
        assertNotEquals(0xFF0000, cleared & 0xFFFFFF,
                "unpainted pixels must fall back to the clear color");
    }

    @Test
    public void testMatrixIdentityOutsideSaveRestoreScope() {
        // render() wraps the content scale in save/restore; after a frame
        // the persistent CTM must be identity again.
        app.renderFrame();
        float[] m = app.getCanvas().getMatrixArray();
        assertEquals(1.0f, m[0], 1e-4f, "CTM must be restored after render");
        assertEquals(1.0f, m[4], 1e-4f);
        assertEquals(0.0f, m[6], 1e-4f);
    }
}
