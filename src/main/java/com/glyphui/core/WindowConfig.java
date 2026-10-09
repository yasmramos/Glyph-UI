package com.glyphui.core;

/**
 * Mutable, fluent configuration applied as window hints <em>before</em>
 * {@link Window#create()} creates the native window.
 *
 * <p>The field vocabulary is backend-neutral: no LWJGL/GLFW types appear in
 * any public signature. The legacy GLFW backend translates these values onto
 * {@code glfwWindowHint} calls inside
 * {@link com.glyphui.core.backend.GlfwWindowBackend}; other backends (JWM)
 * honour whatever subset they support and ignore the rest.</p>
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

    // --- GL profile constants (toolkit-owned; wire-compatible with the
    //     historical GLFW values so existing code keeps working) ----------
    // These MUST stay equal to GLFW's real hint values: the legacy backend
    // passes cfg.glProfile straight to glfwWindowHint(GLFW_OPENGL_PROFILE, …)
    // and GLFW only recognises ANY / CORE / COMPAT (values verified against
    // org.lwjgl.glfw.GLFW: CORE=204801, COMPAT=204802, ANY=0).
    /** Any OpenGL profile — driver chooses (GLFW value: 0). */
    public static final int GL_PROFILE_ANY = 0;
    /** No OpenGL context at all (alias of {@link #GL_PROFILE_ANY}). */
    public static final int GL_PROFILE_NONE = GL_PROFILE_ANY;
    /** OpenGL core profile (GLFW value: 204801). */
    public static final int GL_PROFILE_CORE = 204801;
    /** OpenGL compatibility profile (GLFW value: 204802). */
    public static final int GL_PROFILE_COMPAT = 204802;

    /** Logical window width/height requested at creation. */
    public int width = 800;
    /** Logical window height requested at creation. */
    public int height = 600;
    /** Initial window title. */
    public String title = "Glyph-UI";

    // --- Window hints ------------------------------------------------------
    /** Visibility at creation (legacy {@code GLFW_VISIBLE}). */
    public boolean visible = true;
    /** User resizing allowed (legacy {@code GLFW_RESIZABLE}). */
    public boolean resizable = true;
    /** Title bar / borders (legacy {@code GLFW_DECORATED}). */
    public boolean decorated = true;
    /** Start maximized (legacy {@code GLFW_MAXIMIZED}). */
    public boolean maximized = false;
    /** Stays above other windows (legacy {@code GLFW_FLOATING}). */
    public boolean floating = false;
    /** Grab keyboard focus at creation (legacy {@code GLFW_FOCUSED}). */
    public boolean focused = true;
    /** Per-pixel alpha framebuffer (legacy {@code GLFW_TRANSPARENT_FRAMEBUFFER}). */
    public boolean transparentFramebuffer = false;
    /** MSAA samples for the default framebuffer (legacy {@code GLFW_SAMPLES}). */
    public int samples = 0;
    /** Requested GL context major version (legacy {@code GLFW_CONTEXT_VERSION_MAJOR}). */
    public int glMajor = 3;
    /** Requested GL context minor version (legacy {@code GLFW_CONTEXT_VERSION_MINOR}). */
    public int glMinor = 2;
    /** GL profile: one of {@link #GL_PROFILE_CORE}, {@link #GL_PROFILE_COMPAT}, {@link #GL_PROFILE_ANY}. */
    public int glProfile = GL_PROFILE_CORE;
    /** Forward-compatible context (legacy {@code GLFW_OPENGL_FORWARD_COMPAT}). */
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

    /** One of {@link #GL_PROFILE_CORE}, {@link #GL_PROFILE_COMPAT} or {@link #GL_PROFILE_ANY}. */
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
}
