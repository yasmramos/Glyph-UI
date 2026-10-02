package com.glyphui.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Headless unit tests for {@link WindowConfig} and the config-driven
 * additions to {@link Window} (custom constructor, runtime setters that
 * must be safe with no live GLFW window handle).
 *
 * <p>No native window is created: every setter guards on
 * {@code windowHandle != NULL}, so these exercise the caching/forwarding
 * logic without a display server.</p>
 */
public class WindowConfigTest {

    @Test
    public void testDefaultsMatchHistoricalBehaviour() {
        WindowConfig config = new WindowConfig();

        assertTrue(config.visible);
        assertTrue(config.resizable);
        assertTrue(config.decorated);
        assertTrue(config.focused);
        assertFalse(config.maximized);
        assertFalse(config.floating);
        assertFalse(config.transparentFramebuffer);
        assertEquals(0, config.samples);           // no MSAA by default
        assertEquals(3, config.glMajor);
        assertEquals(2, config.glMinor);
        assertEquals(GLFW_OPENGL_CORE_PROFILE, config.glProfile);
        assertTrue(config.glForwardCompat);
        assertEquals(1, config.swapInterval);      // vsync on
        assertTrue(config.center);
        assertFalse(config.fullscreen);
    }

    @Test
    public void testFluentSettersReturnSameInstanceAndMutate() {
        WindowConfig config = WindowConfig.create();

        WindowConfig returned = config
                .size(1280, 720)
                .title("Glyph")
                .visible(false)
                .decorated(false)
                .floating(true)
                .transparentFramebuffer(true)
                .samples(4)
                .glVersion(4, 5)
                .glProfile(GLFW_OPENGL_ANY_PROFILE)
                .glForwardCompat(false)
                .swapInterval(0)
                .center(false)
                .fullscreen(true);

        assertSame(config, returned, "fluent API must return the same instance");
        assertEquals(1280, config.width);
        assertEquals(720, config.height);
        assertEquals("Glyph", config.title);
        assertFalse(config.visible);
        assertFalse(config.decorated);
        assertTrue(config.floating);
        assertTrue(config.transparentFramebuffer);
        assertEquals(4, config.samples);
        assertEquals(4, config.glMajor);
        assertEquals(5, config.glMinor);
        assertEquals(GLFW_OPENGL_ANY_PROFILE, config.glProfile);
        assertFalse(config.glForwardCompat);
        assertEquals(0, config.swapInterval);
        assertFalse(config.center);
        assertTrue(config.fullscreen);
    }

    @Test
    public void testWindowKeepsCustomConfigAndOverridesGeometry() {
        WindowConfig config = WindowConfig.create().samples(4).visible(false);
        Window window = new Window("Custom", 640, 480, config);

        // The window adopts the very same config instance...
        assertSame(config, window.getConfig());
        // ...but constructor args are the source of truth for geometry/title.
        assertSame(window.getConfig(), window.getConfig());
        assertEquals("Custom", window.getTitle());
        assertEquals(640, window.getWidth());
        assertEquals(480, window.getHeight());
        assertEquals(640, window.getConfig().width);
        assertEquals(480, window.getConfig().height);
        assertEquals("Custom", window.getConfig().title);
        // Non-geometry settings from the user's config survive.
        assertEquals(4, window.getConfig().samples);
        assertFalse(window.getConfig().visible);
    }

    @Test
    public void testNullConfigFallsBackToDefaults() {
        Window window = new Window("Default", 800, 600, null);

        assertNotNull(window.getConfig());
        assertTrue(window.getConfig().resizable);
        assertEquals(GLFW_OPENGL_CORE_PROFILE, window.getConfig().glProfile);
        // Legacy 3-arg constructor behaves identically
        Window legacy = new Window("Default", 800, 600);
        assertNotNull(legacy.getConfig());
        assertEquals(800, legacy.getConfig().width);
    }

    @Test
    public void testSetTitleWithoutHandleOnlyCaches() {
        Window window = new Window("Before", 800, 600);

        window.setTitle("After");

        assertEquals("After", window.getTitle());
        // No live handle -> nothing native was touched; create() would use it later.
        assertEquals(0L, window.getWindowHandle());
    }

    @Test
    public void testRuntimeAttributeSettersAreHeadlessSafeAndSyncConfig() {
        Window window = new Window("Attrs", 800, 600);

        window.setDecorated(false);
        window.setResizable(false);
        window.setFloating(true);

        assertFalse(window.getConfig().decorated);
        assertFalse(window.getConfig().resizable);
        assertTrue(window.getConfig().floating);

        // These must simply not throw without a live window handle.
        window.setOpacity(0.5f);
        window.setPosition(10, 20);
        window.show();
        window.hide();
        window.setMaximized(true);
        window.setMaximized(false);
        window.setFullscreen(true);   // headless: no-op, must not crash
        window.setFullscreen(false);  // no handle: no-op
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
    public void testFullscreenFlagToggledByWindowSetter() {
        Window window = new Window("FS", 800, 600);

        // Headless: setFullscreen(true) is a guarded no-op and must NOT flip
        // the config flag (nothing was actually applied).
        window.setFullscreen(true);
        assertFalse(window.getConfig().fullscreen);
    }
}
