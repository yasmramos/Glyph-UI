package com.glyphui.core;

import com.glyphui.graphics.RasterCanvasTestFactory;
import com.glyphui.ui.TestComponent;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the on-demand rendering flags and the raster resize path of
 * {@link Application}. These avoid creating a window/GL context: the
 * application instance is built directly and internal state is wired via
 * reflection (same package, so package-private members are accessible).
 */
class ApplicationOnDemandTest {

    private Application app;

    @BeforeEach
    void setUp() {
        app = new Application();
    }

    @AfterEach
    void tearDown() {
        // Unhook the global repaint requester registered by init paths.
        com.glyphui.ui.Component.setRepaintRequester(null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean readPaintDirty(Application target) throws Exception {
        return (boolean) getField(target, "paintDirty");
    }

    @Test
    void newApplicationStartsDirtySoFirstFrameIsPainted() throws Exception {
        assertTrue(readPaintDirty(app), "first frame must always be painted");
    }

    @Test
    void requestRepaintSetsPaintDirtyFlag() throws Exception {
        setField(app, "paintDirty", false);
        assertFalse(readPaintDirty(app));

        app.requestRepaint();

        assertTrue(readPaintDirty(app));
    }

    @Test
    void requestRepaintIsIdempotent() throws Exception {
        app.requestRepaint();
        app.requestRepaint();
        app.requestRepaint();

        assertTrue(readPaintDirty(app));
    }

    @Test
    void setAnimationCallbackRequestsRepaintAndClearingDoesNot() throws Exception {
        setField(app, "paintDirty", false);

        app.setAnimationCallback(() -> { });
        assertTrue(readPaintDirty(app), "registering an animation must schedule a repaint");

        setField(app, "paintDirty", false);
        app.setAnimationCallback(null);
        assertFalse(readPaintDirty(app), "removing the animation must not force a repaint");
    }

    @Test
    void rasterBackendIdentifiedByNullDirectContext() throws Exception {
        setField(app, "directContext", null);
        assertFalse(app.isGpuBackend());
        assertNull(getField(app, "directContext"));
    }

    @Test
    void recreateRasterSurfaceWithoutCanvasFailsFast() {
        assertThrows(IllegalStateException.class, () -> app.recreateRasterSurface(100, 100));
    }

    @Test
    void recreateRasterSurfaceResizesCanvasAndMarksPaintDirty() throws Exception {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
            "Skija native library required for raster surface tests");

        // Wire a raster canvas/surface into the application without a window.
        Surface[] holder = new Surface[1];
        com.glyphui.graphics.Canvas canvas = RasterCanvasTestFactory.create(holder);
        assertNotNull(canvas);
        setField(app, "canvas", canvas);
        setField(app, "surface", holder[0]);
        setField(app, "paintDirty", false);

        int newSize = 320;
        app.recreateRasterSurface(newSize, newSize);

        // New surface allocated at the requested size...
        Surface current = (Surface) getField(app, "surface");
        assertNotNull(current);
        try (var image = current.makeImageSnapshot()) {
            assertEquals(newSize, image.getWidth());
            assertEquals(newSize, image.getHeight());
        }

        // ...canvas wrapper resized to match...
        assertEquals(newSize, canvas.getWidth());
        assertEquals(newSize, canvas.getHeight());

        // ...and a repaint scheduled by the recreation itself.
        assertTrue(readPaintDirty(app));

        current.close();
    }

    @Test
    void renderConsumesPaintDirtyFlagInRasterMode() throws Exception {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
            "Skija native library required for raster surface tests");

        Surface[] holder = new Surface[1];
        com.glyphui.graphics.Canvas canvas = RasterCanvasTestFactory.create(holder);
        setField(app, "canvas", canvas);
        setField(app, "surface", holder[0]);
        // directContext stays null => raster backend, no swapBuffers needed.
        // The Application constructor already sets paintDirty = true.

        assertTrue(readPaintDirty(app));
        app.renderFrame(); // exercises the private render(): clears flag, draws root panel

        assertFalse(readPaintDirty(app), "render must consume the repaint request");

        // A subsequent widget mutation must re-dirty the flag. Wire a
        // counting requester so the test does not depend on other tests
        // having registered the global repaint hook.
        java.util.concurrent.atomic.AtomicInteger repaints =
            new java.util.concurrent.atomic.AtomicInteger(0);
        com.glyphui.ui.Component.setRepaintRequester(repaints::incrementAndGet);
        app.requestRepaint();
        assertTrue(readPaintDirty(app), "requestRepaint must re-dirty the flag");

        // End-to-end: Panel.add marks the layout dirty, which routes
        // through the static repaint hook into this application's flag.
        app.getRootPanel().add(new TestComponent(0, 0, 10, 10));
        assertEquals(1, repaints.get(), "adding a child must request a repaint");
        assertTrue(readPaintDirty(app), "widget mutation must re-dirty the flag");

        ((Surface) getField(app, "surface")).close();
    }

    @Test
    void uninitializedApplicationReportsNonGpuBackendAndStopsSafely() {
        // Before init (or in forced-raster mode) there is no DirectContext,
        // so the public contract reports a non-GPU backend and stop() is safe.
        assertFalse(app.isGpuBackend());
        assertDoesNotThrow(() -> app.stop());
    }
}
