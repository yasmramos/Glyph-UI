package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.core.backend.JwmWindowBackend;
import io.github.yasmramos.glyphui.core.backend.WindowBackend;

/**
 * Manages the application window, delegating all native operations to a
 * backend-agnostic {@link WindowBackend}. The toolkit's single production
 * backend is JWM ({@code io.github.humbleui:jwm}); the GLFW/LWJGL backend
 * was removed, so {@link io.github.yasmramos.glyphui.core.backend.JwmWindowBackend} is
 * instantiated directly here.
 *
 * <p>This class exposes the toolkit's public window API: creation/destruction,
 * event pumping, geometry (logical + physical/framebuffer sizes), HiDPI
 * content scale, runtime window attributes, clipboard access and input-event
 * dispatch. None of its signatures leak native-windowing types; listeners use
 * the plain functional interfaces declared in {@link WindowBackend}.</p>
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
 *
 * <p><strong>Threading:</strong> all window operations are UI-thread-only.
 * Every method ultimately reaches native windowing calls that are not thread
 * safe; when the window is driven by an {@link Application} instance, each
 * entry point performs a fail-fast thread check (the same mechanism used by
 * {@code Canvas.setThreadCheckOwner}) so misuse off the UI thread throws
 * instead of corrupting native state. Standalone windows created outside an
 * application loop (typical in headless unit tests) have no registered owner
 * and skip the check.</p>
 */
public class Window implements AutoCloseable {

    /** The active native-window backend; never null. */
    private final WindowBackend backend;

    /**
     * Fail-fast UI-thread check installed by {@link Application} before the
     * event loop starts (see the class-level Threading note). Null while no
     * application owns this window — standalone/headless windows skip it.
     */
    private Runnable threadCheck;

    /**
     * Installs (or clears, with {@code null}) the UI-thread guard consulted
     * by every public method of this window. Package-private: called by
     * {@link Application} when it takes ownership of the window.
     *
     * @param threadCheck a runnable that throws when invoked off the UI
     *                    thread, or null to disable the check
     */
    void setThreadCheck(Runnable threadCheck) {
        this.threadCheck = threadCheck;
    }

    private void checkThread() {
        Runnable check = threadCheck;
        if (check != null) {
            check.run();
        }
    }

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
        this.backend = new JwmWindowBackend(
            title, width, height, config != null ? config : new WindowConfig());
    }

    /**
     * Gets the active window configuration. Mutating the returned object
     * affects the next {@link #create()} (and, for runtime-safe fields such
     * as fullscreen mode, the live window through the backend).
     *
     * <p><strong>Contract after {@link #create()}:</strong> the same instance
     * originally supplied to the constructor is returned; it reflects the
     * creation-time settings, not necessarily the live window state (for
     * example, a deferred native-window backend may still report pre-create
     * values until the loop starts).</p>
     *
     * @return the configuration (never null)
     */
    public WindowConfig getConfig() {
        checkThread();
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
        checkThread();
        return backend.create();
    }

    /**
     * Destroys the window and releases all backend resources. Idempotent.
     */
    public void destroy() {
        checkThread();
        backend.destroy();
    }

    @Override
    public void close() {
        checkThread();
        destroy();
    }

    // ------------------------------------------------------------------
    // Event loop plumbing (package-private: driven by Application only)
    // ------------------------------------------------------------------

    /** Presents the frame (swaps front/back buffers). */
    void swapBuffers() {
        checkThread();
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
    boolean present(io.github.yasmramos.glyphui.core.backend.SurfaceResult result) {
        checkThread();
        return backend.present(result);
    }

    /** @return true when a close has been requested */
    boolean shouldClose() {
        checkThread();
        return backend.shouldClose();
    }

    /** Requests (or cancels) window close. Safe before {@link #create()}. */
    void setShouldClose(boolean shouldClose) {
        checkThread();
        backend.setShouldClose(shouldClose);
    }

    /** Processes all pending events without blocking. */
    void pollEvents() {
        checkThread();
        backend.pollEvents();
    }

    /** Blocks until at least one event arrives, then processes them. */
    void waitEvents() {
        checkThread();
        backend.waitEvents();
    }

    /** Waits for events with an upper time bound (seconds). */
    void waitEventsTimeout(double seconds) {
        checkThread();
        backend.waitEventsTimeout(seconds);
    }

    /** Wakes up a thread blocked in {@link #waitEvents()}. */
    void postEmptyEvent() {
        // Runs on the posting thread by design: this is the cross-thread
        // wake-up primitive used by invokeLater, so no UI-thread check here.
        backend.postEmptyEvent();
    }

    /**
     * Whether the active backend owns the application event loop (JWM returns
     * true: its native message loop drives the frame callback instead of the
     * toolkit's polling loop). Promoted from {@code getBackend().…} so callers
     * never need the raw backend handle.
     *
     * @return true when the backend must be entered via
     *         {@code enterEventLoop(Runnable)} instead of the polling loop
     */
    public boolean isAppOwnedLoop() {
        checkThread();
        return backend.isAppOwnedLoop();
    }

    /**
     * Whether the active backend can host a GPU (OpenGL) surface. The single
     * production backend (JWM) reports false until a live GL-capable window
     * exists, so {@link Application} routes frames through
     * {@code present(SurfaceResult)} instead of {@code swapBuffers()}.
     * Promoted from {@code getBackend().…} so callers never need the raw
     * backend handle.
     *
     * @return true when GL surfaces/buffer swaps are available
     */
    public boolean isGlCapable() {
        checkThread();
        return backend.isGlCapable();
    }

    /**
     * Hands control to the backend's native event loop when
     * {@link #isAppOwnedLoop()} is true. Package-private: driven by
     * {@link Application} only; replaces the former
     * {@code getBackend().enterEventLoop(...)} path so the raw backend handle
     * never leaves this package.
     *
     * @param onFrame callback invoked once per dispatched event batch
     */
    void enterBackendEventLoop(Runnable onFrame) {
        checkThread();
        backend.enterEventLoop(onFrame);
    }

    // ------------------------------------------------------------------
    // Geometry & metadata
    // ------------------------------------------------------------------

    /** @return the logical window width */
    public int getWidth() {
        checkThread();
        return backend.getWidth();
    }

    /** @return the logical window height */
    public int getHeight() {
        checkThread();
        return backend.getHeight();
    }

    /** @return the physical framebuffer width in pixels */
    public int getFramebufferWidth() {
        checkThread();
        return backend.getFramebufferWidth();
    }

    /** @return the physical framebuffer height in pixels */
    public int getFramebufferHeight() {
        checkThread();
        return backend.getFramebufferHeight();
    }

    /** @return the content scale (DPI) factor along X */
    public float getContentScaleX() {
        checkThread();
        return backend.getContentScaleX();
    }

    /** @return the content scale (DPI) factor along Y */
    public float getContentScaleY() {
        checkThread();
        return backend.getContentScaleY();
    }

    /** @return the current window title */
    public String getTitle() {
        checkThread();
        return backend.getTitle();
    }

    /** Updates the title immediately (live window) or caches it. */
    public void setTitle(String title) {
        checkThread();
        backend.setTitle(title);
    }

    /**
     * Updates the cached <b>logical</b> dimensions, deriving the physical
     * framebuffer size from the content scale when no live window exists.
     */
    public void updateDimensions(int width, int height) {
        checkThread();
        backend.updateDimensions(width, height);
    }

    /**
     * Sets both logical and physical dimensions explicitly (headless/test
     * mode). Does not touch the native window.
     */
    public void setSizes(int windowWidth, int windowHeight,
                         int framebufferWidth, int framebufferHeight) {
        checkThread();
        backend.setSizes(windowWidth, windowHeight, framebufferWidth, framebufferHeight);
    }

    /**
     * Sets the cached content scale factors and fires the content-scale
     * listener (headless/test simulation of a DPI change).
     */
    public void setContentScale(float scaleX, float scaleY) {
        checkThread();
        backend.setContentScale(scaleX, scaleY);
    }

    /** @return the window X position in screen coordinates (0 when unknown/headless) */
    public int getPositionX() {
        checkThread();
        int[] pos = backend.getPosition();
        return pos != null ? pos[0] : 0;
    }

    /** @return the window Y position in screen coordinates (0 when unknown/headless) */
    public int getPositionY() {
        checkThread();
        int[] pos = backend.getPosition();
        return pos != null ? pos[1] : 0;
    }

    // ------------------------------------------------------------------
    // Runtime window attributes
    // ------------------------------------------------------------------

    /** Shows a hidden window. */
    public void show() {
        checkThread();
        backend.show();
    }

    /** Hides the window without destroying it. */
    public void hide() {
        checkThread();
        backend.hide();
    }

    /** Toggles window decorations (title bar / borders). */
    public void setDecorated(boolean decorated) {
        checkThread();
        backend.setDecorated(decorated);
    }

    /** Toggles user resizing. */
    public void setResizable(boolean resizable) {
        checkThread();
        backend.setResizable(resizable);
    }

    /** Toggles always-on-top floating state. */
    public void setFloating(boolean floating) {
        checkThread();
        backend.setFloating(floating);
    }

    /** Sets window opacity (0.0 fully transparent .. 1.0 opaque). */
    public void setOpacity(float opacity) {
        checkThread();
        backend.setOpacity(opacity);
    }

    /** Moves the window to screen coordinates (x, y). */
    public void setPosition(int x, int y) {
        checkThread();
        backend.setPosition(x, y);
    }

    /** Resizes the window (logical units). */
    public void setSize(int width, int height) {
        checkThread();
        backend.setSize(width, height);
    }

    /** Maximizes or restores the window. */
    public void setMaximized(boolean maximized) {
        checkThread();
        backend.setMaximized(maximized);
    }

    /** Enters/exits fullscreen mode on the primary monitor. */
    public void setFullscreen(boolean fullscreen) {
        checkThread();
        backend.setFullscreen(fullscreen);
    }

    // ------------------------------------------------------------------
    // Clipboard
    // ------------------------------------------------------------------

    /** Places a UTF-8 string on the system clipboard. */
    public void setClipboardString(String text) {
        checkThread();
        backend.setClipboardString(text);
    }

    /** @return the current clipboard text, or null when unavailable */
    public String getClipboardString() {
        checkThread();
        return backend.getClipboardString();
    }

    // ------------------------------------------------------------------
    // Listeners (backend-agnostic functional interfaces)
    // ------------------------------------------------------------------

    /** Registers the logical window-size listener (null removes). */
    public void setWindowSizeListener(WindowBackend.WindowSizeListener listener) {
        checkThread();
        backend.setWindowSizeListener(listener);
    }

    /** Registers the physical framebuffer-size listener (null removes). */
    public void setFramebufferSizeListener(WindowBackend.FramebufferSizeListener listener) {
        checkThread();
        backend.setFramebufferSizeListener(listener);
    }

    /** Registers the content-scale (DPI) listener (null removes). */
    public void setContentScaleListener(WindowBackend.ContentScaleListener listener) {
        checkThread();
        backend.setContentScaleListener(listener);
    }

    /** Registers the printable-character listener (null removes). */
    public void setCharListener(WindowBackend.CharListener listener) {
        checkThread();
        backend.setCharListener(listener);
    }

    /** Registers the raw key listener (null removes). */
    public void setKeyListener(WindowBackend.KeyListener listener) {
        checkThread();
        backend.setKeyListener(listener);
    }

    /** Registers the IME text-input listener (null restores the default). */
    public void setTextInputListener(WindowBackend.TextInputListener listener) {
        checkThread();
        backend.setTextInputListener(listener);
    }

    /** Installs the IME caret-info client consulted by the native input method. */
    public void setImeClient(WindowBackend.ImeClient client) {
        checkThread();
        backend.setImeClient(client);
    }

    /** Enables/disables the native text-input (IME) connection. */
    public void setTextInputEnabled(boolean enabled) {
        checkThread();
        backend.setTextInputEnabled(enabled);
    }

    /** Registers the mouse-button listener (null removes). */
    public void setMouseListener(WindowBackend.MouseListener listener) {
        checkThread();
        backend.setMouseListener(listener);
    }

    /** Registers the cursor-position listener (null removes). */
    public void setCursorPosListener(WindowBackend.CursorPosListener listener) {
        checkThread();
        backend.setCursorPosListener(listener);
    }
}
