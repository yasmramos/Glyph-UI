package com.glyphui.core;

import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Mutable, fluent configuration applied as GLFW window hints <em>before</em>
 * {@link Window#create()} calls {@code glfwCreateWindow}.
 *
 * <p>Defaults reproduce the historical behaviour of {@link Window}: a
 * visible, resizable, decorated window with an OpenGL 3.2 core /
 * forward-compatible context, no MSAA (the Skija render target is created
 * with samples=0, so a multisampled default framebuffer would never be
 * resolved), centered on the primary monitor and shown immediately.</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * Window w = new Window("My App", 1280, 720,
 *         WindowConfig.create()
 *                 .transparentFramebuffer(true)
 *                 .samples(4)               // only if you also enable MSAA in Skija
 *                 .visible(false));         // show it yourself later
 * }</pre>
 *
 * <p>Note: not every hint is honoured on every platform (e.g. Wayland
 * ignores positioning and transparency on some compositors). These are
 * limitations of the backend, not of this class.</p>
 */
public final class WindowConfig {
    /** Logical window width/height requested at creation. */
    public int width = 800;
    /** Logical window height requested at creation. */
    public int height = 600;
    /** Initial window title. */
    public String title = "Glyph-UI";

    // --- GLFW window hints -------------------------------------------------
    /** {@code GLFW_VISIBLE}: false to create the window hidden. */
    public boolean visible = true;
    /** {@code GLFW_RESIZABLE}. */
    public boolean resizable = true;
    /** {@code GLFW_DECORATED}. */
    public boolean decorated = true;
    /** {@code GLFW_MAXIMIZED}. */
    public boolean maximized = false;
    /** {@code GLFW_FLOATING} (stays above other windows). */
    public boolean floating = false;
    /** {@code GLFW_FOCUSED} at creation. */
    public boolean focused = true;
    /** {@code GLFW_TRANSPARENT_FRAMEBUFFER} (X11/Wayland/Windows). */
    public boolean transparentFramebuffer = false;
    /** {@code GLFW_SAMPLES}: MSAA samples for the default framebuffer. */
    public int samples = 0;
    /** {@code GLFW_CONTEXT_VERSION_MAJOR}. */
    public int glMajor = 3;
    /** {@code GLFW_CONTEXT_VERSION_MINOR}. */
    public int glMinor = 2;
    /** {@code GLFW_OPENGL_PROFILE}: core, compatibility or any. */
    public int glProfile = GLFW_OPENGL_CORE_PROFILE;
    /** {@code GLFW_OPENGL_FORWARD_COMPAT}. */
    public boolean glForwardCompat = true;

    // --- Window-level behaviour -------------------------------------------
    /** Swap interval (vsync): 1 = vsync on, 0 = off, N = every Nth refresh. */
    public int swapInterval = 1;
    /** Center the window on the primary monitor after creation. */
    public boolean center = true;
    /** Create the window directly in fullscreen on the primary monitor. */
    public boolean fullscreen = false;

    /** Fluent factory with defaults. */
    public static WindowConfig create() {
        return new WindowConfig();
    }

    // --- Fluent setters ------------------------------------------------------
    public WindowConfig size(int width, int height) {
        this.width = width;
        this.height = height;
        return this;
    }

    public WindowConfig title(String title) {
        this.title = title;
        return this;
    }

    public WindowConfig visible(boolean visible) {
        this.visible = visible;
        return this;
    }

    public WindowConfig resizable(boolean resizable) {
        this.resizable = resizable;
        return this;
    }

    public WindowConfig decorated(boolean decorated) {
        this.decorated = decorated;
        return this;
    }

    public WindowConfig maximized(boolean maximized) {
        this.maximized = maximized;
        return this;
    }

    public WindowConfig floating(boolean floating) {
        this.floating = floating;
        return this;
    }

    public WindowConfig focused(boolean focused) {
        this.focused = focused;
        return this;
    }

    public WindowConfig transparentFramebuffer(boolean transparent) {
        this.transparentFramebuffer = transparent;
        return this;
    }

    /**
     * Requests {@code samples} MSAA buffers on the default framebuffer.
     * Beware: under a core profile many drivers silently ignore this, and
     * Glyph-UI's Skija render target currently uses samples=0 — real MSAA
     * requires matching {@code BackendRenderTarget} configuration (or a
     * custom multisampled FBO + blit).
     */
    public WindowConfig samples(int samples) {
        this.samples = samples;
        return this;
    }

    public WindowConfig glVersion(int major, int minor) {
        this.glMajor = major;
        this.glMinor = minor;
        return this;
    }

    /** One of {@code GLFW_OPENGL_CORE_PROFILE}, {@code _COMPAT_PROFILE} or {@code _ANY_PROFILE}. */
    public WindowConfig glProfile(int profile) {
        this.glProfile = profile;
        return this;
    }

    public WindowConfig glForwardCompat(boolean forwardCompat) {
        this.glForwardCompat = forwardCompat;
        return this;
    }

    public WindowConfig swapInterval(int swapInterval) {
        this.swapInterval = swapInterval;
        return this;
    }

    public WindowConfig center(boolean center) {
        this.center = center;
        return this;
    }

    public WindowConfig fullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        return this;
    }

    /**
     * Applies this configuration as GLFW window hints. Always starts from
     * {@code glfwDefaultWindowHints()} so hints leaked from a previous
     * window/configuration cannot bleed through.
     */
    void apply() {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, visible ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_DECORATED, decorated ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_MAXIMIZED, maximized ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_FLOATING, floating ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_FOCUSED, focused ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER,
                transparentFramebuffer ? GLFW_TRUE : GLFW_FALSE);
        glfwWindowHint(GLFW_SAMPLES, samples);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, glMajor);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, glMinor);
        glfwWindowHint(GLFW_OPENGL_PROFILE, glProfile);
        if (glForwardCompat) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        }
    }

    /**
     * Returns the video mode to use for a fullscreen window, or {@code null}
     * when headless (no primary monitor attached) — in which case
     * {@link Window#create()} falls back to windowed mode instead of passing
     * NULL to {@code glfwGetVideoMode}.
     */
    GLFWVidMode fullscreenVideoMode() {
        long monitor = glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) {
            return null;
        }
        return glfwGetVideoMode(monitor);
    }
}
