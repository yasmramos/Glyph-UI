package com.glyphui.core;

import com.glyphui.ui.TestComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the HiDPI logical/physical separation in {@link Window}.
 *
 * <p>No GLFW window is created here: the tests exercise the cached state
 * (logical {@code glfwGetWindowSize} values vs. physical
 * {@code glfwGetFramebufferSize} values) and the content-scale bookkeeping,
 * including a simulated 2.0x DPI display.</p>
 */
public class WindowHiDpiTest {

    @AfterEach
    public void tearDown() {
        // Keep the global repaint hook clean across tests
        com.glyphui.ui.Component.setGlobalRepaintRequester(null);
    }

    @Test
    public void testGetWidthGetHeightReturnLogicalSize() {
        Window window = new Window("HiDPI", 800, 600);

        // getWidth()/getHeight() must expose the LOGICAL size
        assertEquals(800, window.getWidth());
        assertEquals(600, window.getHeight());
        assertEquals(800, window.getWindowWidth());
        assertEquals(600, window.getWindowHeight());
    }

    @Test
    public void testFramebufferSizeIsPhysicalAndSeparateFromLogical() {
        Window window = new Window("HiDPI", 800, 600);

        // Before any real GLFW query, framebuffer is assumed 1:1
        assertEquals(window.getWidth(), window.getFramebufferWidth());
        assertEquals(window.getHeight(), window.getFramebufferHeight());

        // Simulate a 2.0x HiDPI display: logical 800x600 -> physical 1600x1200
        window.setContentScale(2.0f, 2.0f);
        window.setSizes(800, 600, 1600, 1200);

        // Logical accessors keep returning logical size...
        assertEquals(800, window.getWidth());
        assertEquals(600, window.getHeight());
        // ...while framebuffer accessors return the physical render-target size
        assertEquals(1600, window.getFramebufferWidth());
        assertEquals(1200, window.getFramebufferHeight());
        assertNotEquals(window.getWidth(), window.getFramebufferWidth(),
                "logical and physical widths must be independent");
    }

    @Test
    public void testContentScaleDefaultsToOne() {
        Window window = new Window("HiDPI", 800, 600);
        assertEquals(1.0f, window.getContentScale());
        assertEquals(1.0f, window.getContentScaleX());
        assertEquals(1.0f, window.getContentScaleY());
    }

    @Test
    public void testSimulatedContentScaleTwoUpdatesCachedValues() {
        Window window = new Window("HiDPI", 1024, 768);
        window.setContentScale(2.0f, 2.0f);

        assertEquals(2.0f, window.getContentScale());
        assertEquals(2.0f, window.getContentScaleX());
        assertEquals(2.0f, window.getContentScaleY());
        // getContentScale() assumes uniform scaling (x == y)
        assertEquals(window.getContentScaleX(), window.getContentScale(), 0.0f);
    }

    @Test
    public void testUpdateDimensionsWithoutGlfwDerivesPhysicalFromScale() {
        Window window = new Window("HiDPI", 800, 600);
        window.setContentScale(2.0f, 2.0f);

        // No live GLFW handle: updateDimensions() must derive the physical
        // framebuffer size from logical size * content scale
        window.updateDimensions(500, 400);

        assertEquals(500, window.getWidth(), "getWidth() stays logical");
        assertEquals(400, window.getHeight());
        assertEquals(1000, window.getFramebufferWidth());
        assertEquals(800, window.getFramebufferHeight());
    }

    @Test
    public void testContentScaleListenerFiresOnScaleChange() {
        Window window = new Window("HiDPI", 800, 600);

        AtomicReference<float[]> received = new AtomicReference<>();
        window.setContentScaleListener((xs, ys) -> received.set(new float[]{xs, ys}));

        window.setContentScale(1.5f, 1.5f);

        assertNotNull(received.get(), "content-scale callback must fire (DPI change)");
        assertEquals(1.5f, received.get()[0], 0.0f);
        assertEquals(1.5f, received.get()[1], 0.0f);
    }

    @Test
    public void testMouseCoordinatesStayInLogicalSpace() {
        // HiDPI contract: GLFW reports mouse positions in logical window
        // coordinates, and the UI tree lives in logical coordinates too.
        // The component hit-testing therefore needs no physical conversion.
        Window window = new Window("HiDPI", 800, 600);
        window.setContentScale(2.0f, 2.0f);
        window.setSizes(800, 600, 1600, 1200);

        TestComponent button = new TestComponent(100, 100, 200, 50);

        // A logical cursor position at (150, 120) — which maps to physical
        // pixel (300, 240) on the 2x framebuffer — must hit the component
        // using its logical bounds unchanged.
        double logicalX = 150; // GLFW cursor x (logical space)
        double logicalY = 120;
        assertTrue(button.contains((int) logicalX, (int) logicalY),
                "logical mouse coords must match the logical component tree");

        // And a point outside the logical bounds does not hit, even though
        // its physical equivalent would be inside the physical surface.
        assertFalse(button.contains((int) (logicalX + 300), (int) logicalY));
    }
}
