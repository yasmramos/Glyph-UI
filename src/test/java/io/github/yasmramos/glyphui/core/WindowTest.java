package io.github.yasmramos.glyphui.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless unit tests for {@link Window}: the fluent creation-time setters
 * (which replaced the former {@code WindowConfig} bag) and the runtime
 * setters that must be safe with no live native window handle.
 *
 * <p>No native window is created: every setter guards on the live window
 * handle, so these exercise the caching/forwarding logic without a display
 * server.</p>
 */
public class WindowTest {

    @Test
    public void testFluentSettersReturnSameInstance() {
        Window window = new Window("Glyph", 800, 600);

        Window returned = window
                .title("Glyph")
                .size(1280, 720)
                .decorated(false)
                .resizable(false)
                .visible(true)
                .floating(true)
                .maximized(false)
                .fullscreen(false)
                .centered(false);

        assertSame(window, returned, "fluent API must return the same instance");
    }

    @Test
    public void testTitleAndSizeSettersUpdateCachedState() {
        Window window = new Window("Before", 800, 600);

        window.title("After").size(1024, 768);

        assertEquals("After", window.getTitle());
        assertEquals(1024, window.getWidth());
        assertEquals(768, window.getHeight());
    }

    @Test
    public void testSetTitleWithoutHandleOnlyCaches() {
        Window window = new Window("Before", 800, 600);

        window.setTitle("After");

        assertEquals("After", window.getTitle());
        // No live handle -> nothing native was touched; create() would use it
        // later. The raw handle is not exposed on Window (internal API); the
        // headless backend keeps reporting 0 through WindowBackend.
    }

    @Test
    public void testRuntimeAttributeSettersAreHeadlessSafe() {
        Window window = new Window("Attrs", 800, 600);

        // These must simply not throw without a live window handle.
        window.setDecorated(false);
        window.setResizable(false);
        window.setFloating(true);
        window.setOpacity(0.5f);
        window.setPosition(10, 20);
        window.show();
        window.hide();
        window.setMaximized(true);
        window.setMaximized(false);
        window.setFullscreen(true);
        window.setFullscreen(false);
        window.centered(false);
    }

    @Test
    public void testSetSizeWithoutHandleUpdatesDimensionsViaScale() {
        Window window = new Window("Resize", 800, 600);
        window.setContentScale(2.0f, 2.0f);

        // No live handle -> setSize falls back to updateDimensions, which
        // derives the physical framebuffer size from the cached DPI scale.
        window.setSize(1024, 768);

        assertEquals(1024, window.getWidth());
        assertEquals(768, window.getHeight());
        assertEquals(2048, window.getFramebufferWidth());
        assertEquals(1536, window.getFramebufferHeight());
    }

    @Test
    public void testMaximizedFlagTrackedByWindowSetter() {
        Window window = new Window("Max", 800, 600);

        assertFalse(window.isMaximized());
        window.setMaximized(true);
        assertTrue(window.isMaximized());
        window.setMaximized(false);
        assertFalse(window.isMaximized());
    }
}
