package com.glyphui.core;

import com.glyphui.core.backend.WindowBackend;

/**
 * Manages the application window, delegating all native operations to a
 * backend-agnostic {@link WindowBackend} (GLFW/LWJGL by default, JWM when the
 * {@code io.github.humbleui:jwm} dependency is present — see
 * {@link BackendFactory}).
 *
 * <p>This class exposes the toolkit's public window API: creation/destruction,
 * event pumping, geometry (logical + physical/framebuffer sizes), HiDPI
 * content scale, runtime window attributes, clipboard access and input-event
 * dispatch. None of its signatures leak LWJGL/GLFW types; listeners use the
 * plain functional interfaces declared in {@link WindowBackend}.</p>
 *
 * <p>{@code Window} owns the native window, so it implements
 * {@link AutoCloseable}. {@link #close()} destroys the window and releases
 * backend resources; it is safe to call multiple times.</p>
 *
 * <p><strong>Headless contract:</strong> every method is safe to call before
 * {@link #create()} succeeds (no live native window). Size/scale setters
 * ({@link #setSizes}, {@link #setContentScale}) update cached state and fire
 * the corresponding listeners without touching native code, which keeps unit
 * tests runnable without a display server.</p>
 */
public class Window implements AutoCloseable {

    /** The active native-window backend; never null. */
    private final WindowBackend backend;

    /**
     * Creates a new Window with the default {@link WindowConfig}
     * (visible, resizable, decorated, OpenGL 3.2 core, centered).
     *
     * @param title  the window title
     * @param width  the window width (logical)
     * @param height the window height (logical)
     */
    public Window(String title, int width, int height) {
        this(title, width, height, new WindowConfig());
    }

    /**
     * Creates a new Window with an explicit configuration.
     *
     * @param title  the window title
     * @param width  the window width (logical)
     * @param height the window height (logical)
     * @param config the window configuration (a null value falls back to
     *               defaults)
     */
    public Window(String title, int width, int height, WindowConfig config) {
        this.backend = BackendFactory.create(
            title, width, height, config != null ? config : new WindowConfig());
    }

    /**
     * Gets the underlying backend. Intended for advanced integrations and
     * tests; ordinary application code should not need it.
     *
     * @return the active window backend (never null)
     */
    public WindowBackend getBackend() {
        return backend;
    }

    /**
     * Gets the active window configuration. Mutating the returned object
     * affects the next {@link #create()} (and, for runtime-safe fields such
     * as fullscreen mode, the live window through the backend).
     *
     * @return the configuration (never null)
     */
    public WindowConfig getConfig() {
        return backend.getConfig();
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Initializes the backend and creates the native window.
     *
     * @return true if initialization succeeded
     */
    public boolean create() {
        return backend.create();
    }

    /**
     * Destroys the window and releases all backend resources. Idempotent.
     */
    public void destroy() {
        backend.destroy();
    }

    @Override
    public void close() {
        destroy();
    }

    // ------------------------------------------------------------------
    // Event loop plumbing
    // ------------------------------------------------------------------

    /** Presents the frame (swaps front/back buffers). */
    public void swapBuffers() {
        backend.swapBuffers();
    }

    /**
     * Presents the given frame through the active backend: GL backends swap
     * buffers, raster-only backends (JWM) blit the CPU surface onto the
     * native window layer.
     *
     * @param result the surface result currently bound to the app canvas
     * @return false when the backend could not display the frame yet and the
     *         caller should request another repaint
     */
    public boolean present(com.glyphui.core.backend.SurfaceResult result) {
        return backend.present(result);
    }

    /** @return true when a close has been requested */
    public boolean shouldClose() {
        return backend.shouldClose();
    }

    /** Requests (or cancels) window close. Safe before {@link #create()}. */
    public void setShouldClose(boolean shouldClose) {
        backend.setShouldClose(shouldClose);
    }

    /** Processes all pending events without blocking. */
    public void pollEvents() {
        backend.pollEvents();
    }

    /** Blocks until at least one event arrives, then processes them. */
    public void waitEvents() {
        backend.waitEvents();
    }

    /** Waits for events with an upper time bound (seconds). */
    public void waitEventsTimeout(double seconds) {
        backend.waitEventsTimeout(seconds);
    }

    /** Wakes up a thread blocked in {@link #waitEvents()}. */
    public void postEmptyEvent() {
        backend.postEmptyEvent();
    }

    // ------------------------------------------------------------------
    // Geometry & metadata
    // ------------------------------------------------------------------

    /** @return the native window handle, or 0 when no live window exists */
    public long getWindowHandle() {
        return backend.getWindowHandle();
    }

    /** @return the logical window width */
    public int getWidth() {
        return backend.getWidth();
    }

    /** @return the logical window height */
    public int getHeight() {
        return backend.getHeight();
    }

    /** @return the physical framebuffer width in pixels */
    public int getFramebufferWidth() {
        return backend.getFramebufferWidth();
    }

    /** @return the physical framebuffer height in pixels */
    public int getFramebufferHeight() {
        return backend.getFramebufferHeight();
    }

    /** @return the content scale (DPI) factor along X */
    public float getContentScaleX() {
        return backend.getContentScaleX();
    }

    /** @return the content scale (DPI) factor along Y */
    public float getContentScaleY() {
        return backend.getContentScaleY();
    }

    /**
     * Convenience accessor returning the uniform content scale (the X
     * factor; most platforms report equal X/Y factors).
     *
     * @return the content scale factor
     */
    public float getContentScale() {
        return backend.getContentScaleX();
    }

    /** @return the current window title */
    public String getTitle() {
        return backend.getTitle();
    }

    /** Updates the title immediately (live window) or caches it. */
    public void setTitle(String title) {
        backend.setTitle(title);
    }

    /**
     * Updates the cached <b>logical</b> dimensions, deriving the physical
     * framebuffer size from the content scale when no live window exists.
     */
    public void updateDimensions(int width, int height) {
        backend.updateDimensions(width, height);
    }

    /**
     * Sets both logical and physical dimensions explicitly (headless/test
     * mode). Does not touch the native window.
     */
    public void setSizes(int windowWidth, int windowHeight,
                         int framebufferWidth, int framebufferHeight) {
        backend.setSizes(windowWidth, windowHeight, framebufferWidth, framebufferHeight);
    }

    /**
     * Sets the cached content scale factors and fires the content-scale
     * listener (headless/test simulation of a DPI change).
     */
    public void setContentScale(float scaleX, float scaleY) {
        backend.setContentScale(scaleX, scaleY);
    }

    /** @return the logical window width (alias of {@link #getWidth()}) */
    public int getWindowWidth() {
        return backend.getWidth();
    }

    /** @return the logical window height (alias of {@link #getHeight()}) */
    public int getWindowHeight() {
        return backend.getHeight();
    }

    /**
     * @return the window position in screen coordinates as {x, y}, or
     *         {0, 0} when unknown/headless
     */
    public int[] getPosition() {
        return backend.getPosition();
    }

    // ------------------------------------------------------------------
    // Runtime window attributes
    // ------------------------------------------------------------------

    /** Shows a hidden window. */
    public void show() {
        backend.show();
    }

    /** Hides the window without destroying it. */
    public void hide() {
        backend.hide();
    }

    /** Toggles window decorations (title bar / borders). */
    public void setDecorated(boolean decorated) {
        backend.setDecorated(decorated);
    }

    /** Toggles user resizing. */
    public void setResizable(boolean resizable) {
        backend.setResizable(resizable);
    }

    /** Toggles always-on-top floating state. */
    public void setFloating(boolean floating) {
        backend.setFloating(floating);
    }

    /** Sets window opacity (0.0 fully transparent .. 1.0 opaque). */
    public void setOpacity(float opacity) {
        backend.setOpacity(opacity);
    }

    /** Moves the window to screen coordinates (x, y). */
    public void setPosition(int x, int y) {
        backend.setPosition(x, y);
    }

    /** Resizes the window (logical units). */
    public void setSize(int width, int height) {
        backend.setSize(width, height);
    }

    /** Maximizes or restores the window. */
    public void setMaximized(boolean maximized) {
        backend.setMaximized(maximized);
    }

    /** Enters/exits fullscreen mode on the primary monitor. */
    public void setFullscreen(boolean fullscreen) {
        backend.setFullscreen(fullscreen);
    }

    // ------------------------------------------------------------------
    // Clipboard
    // ------------------------------------------------------------------

    /** Places a UTF-8 string on the system clipboard. */
    public void setClipboardString(String text) {
        backend.setClipboardString(text);
    }

    /** @return the current clipboard text, or null when unavailable */
    public String getClipboardString() {
        return backend.getClipboardString();
    }

    // ------------------------------------------------------------------
    // Listeners (backend-agnostic functional interfaces)
    // ------------------------------------------------------------------

    /** Registers the logical window-size listener (null removes). */
    public void setWindowSizeListener(WindowBackend.WindowSizeListener listener) {
        backend.setWindowSizeListener(listener);
    }

    /** Registers the physical framebuffer-size listener (null removes). */
    public void setFramebufferSizeListener(WindowBackend.FramebufferSizeListener listener) {
        backend.setFramebufferSizeListener(listener);
    }

    /** Registers the content-scale (DPI) listener (null removes). */
    public void setContentScaleListener(WindowBackend.ContentScaleListener listener) {
        backend.setContentScaleListener(listener);
    }

    /** Registers the printable-character listener (null removes). */
    public void setCharListener(WindowBackend.CharListener listener) {
        backend.setCharListener(listener);
    }

    /** Registers the raw key listener (null removes). */
    public void setKeyListener(WindowBackend.KeyListener listener) {
        backend.setKeyListener(listener);
    }

    /** Registers the IME text-input listener (null restores the default). */
    public void setTextInputListener(WindowBackend.TextInputListener listener) {
        backend.setTextInputListener(listener);
    }

    /** Installs the IME caret-info client consulted by the native input method. */
    public void setImeClient(WindowBackend.ImeClient client) {
        backend.setImeClient(client);
    }

    /** Enables/disables the native text-input (IME) connection. */
    public void setTextInputEnabled(boolean enabled) {
        backend.setTextInputEnabled(enabled);
    }

    /** Registers the mouse-button listener (null removes). */
    public void setMouseListener(WindowBackend.MouseListener listener) {
        backend.setMouseListener(listener);
    }

    /** Registers the cursor-position listener (null removes). */
    public void setCursorPosListener(WindowBackend.CursorPosListener listener) {
        backend.setCursorPosListener(listener);
    }
}
