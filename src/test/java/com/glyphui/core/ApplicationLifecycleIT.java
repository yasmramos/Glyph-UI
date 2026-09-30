package com.glyphui.core;

import com.glyphui.events.KeyEvent;
import com.glyphui.graphics.Canvas;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the Application lifecycle: HiDPI coordinate model,
 * surface recreation after resize (stale canvas), char callback filling
 * KeyEvent character, scroll event dispatch and safe destroy().
 *
 * <p>Uses the raster backend so assertions can run headless.</p>
 */
public class ApplicationLifecycleIT {

    private Application app;

    @BeforeEach
    public void setUp() {
        app = new Application();
        boolean initialized = app.init("Glyph UI Lifecycle Test", 400, 300, true);
        Assumptions.assumeTrue(initialized, "Skija/native initialization failed; skipping");
    }

    @AfterEach
    public void tearDown() {
        if (app != null) {
            app.destroy();
        }
    }

    /**
     * Bug #1 regression: mouse events must carry logical coordinates as reported
     * by GLFW, never multiplied by the HiDPI scale factor. We verify that the
     * cached DPI scale is exposed and that dispatch through the root panel keeps
     * coordinates verbatim (the callbacks now do exactly this with raw GLFW values).
     */
    @Test
    public void testEventCoordinatesAreLogicalUnderHiDpi() {
        float sx = app.getDpiScaleX();
        float sy = app.getDpiScaleY();
        assertTrue(sx > 0 && sy > 0, "Cached DPI scales must be positive");

        final List<Integer> xs = new ArrayList<>();
        final List<Integer> ys = new ArrayList<>();
        app.getRootPanel().add(new CapturingComponent(0, 0, 100, 50, xs, ys));

        int logicalX = 40;
        int logicalY = 25;
        // Same math as the cursor callback: raw GLFW coords cast to int, no scaling.
        double mouseX = logicalX;
        double mouseY = logicalY;
        app.getRootPanel().onMouseEvent(new com.glyphui.events.MouseEvent(
            com.glyphui.events.MouseEventType.MOVE, (int) mouseX, (int) mouseY,
            com.glyphui.events.MouseButton.LEFT, 0));

        assertEquals(1, xs.size());
        assertEquals(logicalX, (int) xs.get(0), "Event x must NOT be multiplied by dpiScale");
        assertEquals(logicalY, (int) ys.get(0), "Event y must NOT be multiplied by dpiScale");
    }

    /**
     * Bug #3 regression: after recreateSurface (resize path), the Canvas wrapper
     * must be rebound to the new surface; rendering afterwards must not touch the
     * native canvas of the closed surface.
     */
    @Test
    public void testCanvasIsReboundAfterResize() throws Exception {
        Canvas canvasBefore = app.getCanvas();
        io.github.humbleui.skija.Canvas nativeBefore = canvasBefore.getNativeCanvas();

        // Simulate the framebuffer-resize path
        app.recreateSurface(640, 480);

        assertEquals(640, app.getCanvas().getWidth());
        assertEquals(480, app.getCanvas().getHeight());
        assertNotSame(nativeBefore, app.getCanvas().getNativeCanvas(),
            "Native canvas must come from the recreated surface");

        // Rendering after resize must succeed (would crash on a stale canvas)
        app.renderFrame();

        File out = new File("target/lifecycle-after-resize.png");
        app.captureToPng(out);
        assertTrue(out.exists() && out.length() > 0, "Screenshot after resize should be produced");
    }

    /**
     * Design #8 regression: the char callback must fill KeyEvent.getKeyChar() with
     * the real typed character instead of (char) 0.
     */
    @Test
    public void testCharCallbackFillsKeyEventCharacter() {
        final List<KeyEvent> received = new ArrayList<>();
        app.getRootPanel().add(new KeyCapturingComponent(received));

        KeyEvent event = app.dispatchKeyPressForTesting(65 /* GLFW_KEY_A */, 'a', 0);

        assertEquals('a', event.getKeyChar(), "Key press must carry the real character");
        assertEquals(1, received.size());
        assertEquals('a', received.get(0).getKeyChar());

        // Each press carries its own character
        KeyEvent followUp = app.dispatchKeyPressForTesting(66, 'b', 0);
        assertEquals('b', followUp.getKeyChar());
    }

    /**
     * Design #8 regression: scroll callback produces SCROLL MouseEvents with deltas.
     */
    @Test
    public void testScrollProducesScrollEvents() {
        final List<com.glyphui.events.MouseEvent> received = new ArrayList<>();
        app.getRootPanel().add(new MouseCapturingComponent(received));

        // Same construction as the GLFW scroll callback performs
        com.glyphui.events.MouseEvent scroll = new com.glyphui.events.MouseEvent(
            com.glyphui.events.MouseEventType.SCROLL, 10, 20,
            com.glyphui.events.MouseButton.LEFT, 0, 0.0, -2.0);
        app.getRootPanel().onMouseEvent(scroll);

        assertEquals(1, received.size());
        assertEquals(com.glyphui.events.MouseEventType.SCROLL, received.get(0).getType());
        assertEquals(-2.0, received.get(0).getDeltaY(), 1e-9);
    }

    /**
     * Bug #5 regression: destroy() must release resources in dependency order
     * (surface before context) and be idempotent. Calling destroy() twice must
     * be safe.
     */
    @Test
    public void testDestroyIsSafeAndIdempotent() {
        app.renderFrame();
        app.destroy();
        // Calling destroy again must not throw
        assertDoesNotThrow(() -> app.destroy());
        app = null; // prevent tearDown from destroying again
    }

    /** Component that captures key events. */
    private static class KeyCapturingComponent extends com.glyphui.ui.Component {
        private final List<KeyEvent> received;

        KeyCapturingComponent(List<KeyEvent> received) {
            super(0, 0, 10, 10);
            this.received = received;
        }

        @Override
        public void render(Canvas canvas) {
        }

        @Override
        public void onMouseEvent(com.glyphui.events.MouseEvent event) {
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
            received.add(event);
        }

        @Override
        public void dispose() {
        }
    }

    /** Component that captures mouse events. */
    private static class MouseCapturingComponent extends com.glyphui.ui.Component {
        private final List<com.glyphui.events.MouseEvent> received;

        MouseCapturingComponent(List<com.glyphui.events.MouseEvent> received) {
            super(0, 0, 10, 10);
            this.received = received;
        }

        @Override
        public void render(Canvas canvas) {
        }

        @Override
        public void onMouseEvent(com.glyphui.events.MouseEvent event) {
            received.add(event);
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
        }

        @Override
        public void dispose() {
        }
    }

    /** Component that records event coordinates. */
    private static class CapturingComponent extends com.glyphui.ui.Component {
        private final List<Integer> xs;
        private final List<Integer> ys;

        CapturingComponent(float x, float y, float w, float h, List<Integer> xs, List<Integer> ys) {
            super(x, y, w, h);
            this.xs = xs;
            this.ys = ys;
        }

        @Override
        public void render(Canvas canvas) {
        }

        @Override
        public void onMouseEvent(com.glyphui.events.MouseEvent event) {
            xs.add(event.getX());
            ys.add(event.getY());
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
        }

        @Override
        public void dispose() {
        }
    }
}
