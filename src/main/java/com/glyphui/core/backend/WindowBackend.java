package com.glyphui.core.backend;

import com.glyphui.core.WindowConfig;

/**
 * Backend-agnostic window interface.
 *
 * <p>{@code WindowBackend} abstracts everything the toolkit needs from a
 * native window: creation/destruction, event pumping, geometry (logical and
 * physical/framebuffer sizes), HiDPI content scale, runtime window
 * attributes, clipboard access and input-event dispatch. Concrete backends
 * adapt a specific windowing library to this API:</p>
 *
 * <ul>
 *   <li>{@link JwmWindowBackend} — the default backend, built on
 *       <em>io.github.humbleui:jwm</em> (with IME support through
 *       {@code TextInputClient}).</li>
 *   <li>{@link GlfwWindowBackend} — optional legacy backend built on
 *       LWJGL/GLFW.</li>
 * </ul>
 *
 * <p>The interface deliberately avoids any backend-specific types in its
 * signatures: listeners are plain functional interfaces declared below (the
 * "window handle" parameter of the historical GLFW callbacks is simply
 * dropped). Key codes arriving at
 * {@link KeyListener} and mouse button ids arriving at
 * {@link MouseListener} use the toolkit's own numeric vocabulary
 * ({@link com.glyphui.events.GlyphKeys} / 0=left, 1=right, 2=middle); each
 * backend translates its native identifiers before dispatching. Modifier
 * bitmasks follow {@link com.glyphui.events.GlyphMods}.</p>
 *
 * <p>The public API of {@code com.glyphui.core.Window} mirrors this
 * interface one-to-one and delegates to the active backend; see that class
 * for user-facing documentation.</p>
 *
 * <p><strong>Headless contract:</strong> every method must be safe to call
 * before {@link #create()} succeeds (no live native window). Setters may
 * cache their values; size/scale setters ({@link #setSizes},
 * {@link #setContentScale}) update cached state and fire the corresponding
 * listeners without touching native code. This keeps unit tests runnable
 * without a display server.</p>
 */
public interface WindowBackend extends AutoCloseable {

    // ------------------------------------------------------------------
    // Listener functional interfaces (backend-agnostic replacements for
    // the LWJGL GLFWXxxCallbackI types used by the original API)
    // ------------------------------------------------------------------

    /** Receives logical window-size changes, in logical units. */
    @FunctionalInterface
    interface WindowSizeListener {
        void onWindowSizeChanged(int width, int height);
    }

    /** Receives physical framebuffer-size changes, in pixels. */
    @FunctionalInterface
    interface FramebufferSizeListener {
        void onFramebufferSizeChanged(int width, int height);
    }

    /** Receives content-scale (DPI) changes. */
    @FunctionalInterface
    interface ContentScaleListener {
        void onContentScaleChanged(float scaleX, float scaleY);
    }

    /** Receives printable-character (Unicode codepoint) events. */
    @FunctionalInterface
    interface CharListener {
        void onChar(int codepoint);
    }

    /**
     * Receives committed / composed text from the platform input method
     * (IME). Backends without a native IME bridge (GLFW) never fire this
     * listener; JWM dispatches its {@code EventTextInput} here. The default
     * implementation forwards to the {@link CharListener} one codepoint at a
     * time so applications written against the char callback keep working.
     *
     * @param text             the committed text (never null, may be empty)
     * @param replacementStart start of the range to replace in the current
     *                         marked/composing text (0 when no composition)
     * @param replacementEnd   end of the range to replace (== start when none)
     */
    @FunctionalInterface
    interface TextInputListener {
        void onTextInput(String text, int replacementStart, int replacementEnd);
    }

    /**
     * Provides the caret/marked-text geometry the native input method needs
     * to position its candidate window. Implemented by the application layer
     * (which knows the focused widget); the backend queries it from the UI
     * thread whenever the OS asks.
     */
    interface ImeClient {
        /**
         * @param insertionStart caret/composition start offset in the client's
         *                       text buffer
         * @param insertionEnd   caret/composition end offset
         * @return the caret rectangle in <b>physical screen coordinates</b>,
         *         or null when there is no editable focus
         */
        int[] getCursorRect(int insertionStart, int insertionEnd);

        /** @return the currently edited text, or null when not editing */
        String getText();

        /**
         * Current selection as {@code {lo, hi}} offsets into
         * {@link #getText()}, or null when there is no selection. Used by
         * IME-capable backends to report the selected range to the OS.
         */
        default int[] getSelectionRange() {
            return null;
        }
    }

    /**
     * Receives raw key events.
     *
     * @param keyCode   toolkit key code (see
     *                  {@link com.glyphui.events.GlyphKeys}); backends
     *                  translate their native identifiers before dispatching
     * @param action    true for press/repeat, false for release
     * @param modifiers modifier bit flags (see
     *                  {@link com.glyphui.events.GlyphMods})
     */
    @FunctionalInterface
    interface KeyListener {
        void onKey(int keyCode, boolean action, int modifiers);
    }

    /**
     * Receives mouse button events.
     *
     * @param button    toolkit button id: 0 = left, 1 = right, 2 = middle
     * @param action    true for press, false for release
     * @param x         logical X coordinate
     * @param y         logical Y coordinate
     * @param modifiers modifier bit flags (see
     *                  {@link com.glyphui.events.GlyphMods})
     */
    @FunctionalInterface
    interface MouseListener {
        void onMouseButton(int button, boolean action, double x, double y, int modifiers);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Creates the native window and makes its GL context current (when the
     * backend uses GL).
     *
     * @return true if initialization succeeded
     */
    boolean create();

    /**
     * Destroys the window and releases all backend resources. Idempotent.
     */
    void destroy();

    // ------------------------------------------------------------------
    // Event loop plumbing
    // ------------------------------------------------------------------

    /** Presents the frame (swaps front/back buffers). */
    void swapBuffers();

    /** @return true when a close has been requested */
    boolean shouldClose();

    /** Requests (or cancels) window close. Safe before {@link #create()}. */
    void setShouldClose(boolean shouldClose);

    /** Processes all pending events without blocking. */
    void pollEvents();

    /** Blocks until at least one event arrives, then processes them. */
    void waitEvents();

    /** Waits for events with an upper time bound (seconds). */
    void waitEventsTimeout(double seconds);

    /** Wakes up a thread blocked in {@link #waitEvents()}. */
    void postEmptyEvent();

    // ------------------------------------------------------------------
    // Geometry & metadata
    // ------------------------------------------------------------------

    /** @return the native window handle, or 0 when no live window exists */
    long getWindowHandle();

    /** @return the logical window width */
    int getWidth();

    /** @return the logical window height */
    int getHeight();

    /** @return the physical framebuffer width in pixels */
    int getFramebufferWidth();

    /** @return the physical framebuffer height in pixels */
    int getFramebufferHeight();

    /** @return the content scale (DPI) factor along X */
    float getContentScaleX();

    /** @return the content scale (DPI) factor along Y */
    float getContentScaleY();

    /** @return the current window title */
    String getTitle();

    /** Updates the title immediately (live window) or caches it. */
    void setTitle(String title);

    /** @return the active window configuration (never null) */
    WindowConfig getConfig();

    /**
     * Updates the cached <b>logical</b> dimensions, deriving the physical
     * framebuffer size from the content scale when no live window exists.
     */
    void updateDimensions(int width, int height);

    /**
     * Sets both logical and physical dimensions explicitly (headless/test
     * mode). Does not touch the native window.
     */
    void setSizes(int windowWidth, int windowHeight,
                  int framebufferWidth, int framebufferHeight);

    /**
     * Sets the cached content scale factors and fires the content-scale
     * listener (headless/test simulation of a DPI change).
     */
    void setContentScale(float scaleX, float scaleY);

    // ------------------------------------------------------------------
    // Runtime window attributes
    // ------------------------------------------------------------------

    void show();

    void hide();

    void setDecorated(boolean decorated);

    void setResizable(boolean resizable);

    void setFloating(boolean floating);

    void setOpacity(float opacity);

    void setPosition(int x, int y);

    void setSize(int width, int height);

    void setMaximized(boolean maximized);

    void setFullscreen(boolean fullscreen);

    /**
     * @return the window position in screen coordinates as {x, y}, or
     *         {0, 0} when unknown/headless. Used by the IME bridge to map
     *         caret rects into screen space.
     */
    int[] getPosition();

    // ------------------------------------------------------------------
    // Clipboard
    // ------------------------------------------------------------------

    /** Places a UTF-8 string on the system clipboard. */
    void setClipboardString(String text);

    /** @return the current clipboard text, or null when unavailable */
    String getClipboardString();

    // ------------------------------------------------------------------
    // Listeners
    // ------------------------------------------------------------------

    /** Registers the logical window-size listener (null removes). */
    void setWindowSizeListener(WindowSizeListener listener);

    /** Registers the physical framebuffer-size listener (null removes). */
    void setFramebufferSizeListener(FramebufferSizeListener listener);

    /** Registers the content-scale (DPI) listener (null removes). */
    void setContentScaleListener(ContentScaleListener listener);

    /** Registers the printable-character listener (null removes). */
    void setCharListener(CharListener listener);

    /** Registers the raw key listener (null removes). */
    void setKeyListener(KeyListener listener);

    /** Registers the IME text-input listener (null restores the default). */
    void setTextInputListener(TextInputListener listener);

    /**
     * Installs the IME caret-info client consulted by the native input
     * method. Backends without IME support may ignore it. Null removes.
     */
    void setImeClient(ImeClient client);

    /**
     * Enables/disables the native text-input (IME) connection for this
     * window. Backends that always accept text input may treat this as a
     * hint; safe to call before {@link #create()}.
     */
    void setTextInputEnabled(boolean enabled);

    /** Registers the mouse-button listener (null removes). */
    void setMouseListener(MouseListener listener);

    /**
     * Receives cursor-position updates in logical window coordinates.
     * Backends deliver both axes in a single call (GLFW's separate x/y
     * doubles are coalesced by the backend before dispatching).
     */
    @FunctionalInterface
    interface CursorPosListener {
        void onCursorPos(double x, double y);
    }

    /** Registers the cursor-position listener (null removes). */
    void setCursorPosListener(CursorPosListener listener);

    // ------------------------------------------------------------------
    // Backend capabilities / rendering hooks
    // ------------------------------------------------------------------

    /**
     * Returns true when this backend renders through OpenGL and the toolkit
     * should build a GPU (Skija GL) surface. Raster-only backends (e.g. a
     * headless JWM window without GL support) return false, in which case
     * {@link #present(SurfaceResult)} is called instead of
     * {@link #swapBuffers()}.
     *
     * @return true for GL-capable backends
     */
    default boolean isGlCapable() {
        return true;
    }

    /**
     * Presents the freshly rendered frame after {@code render()} completed a
     * paint cycle. GL backends swap buffers here ({@link #swapBuffers()} —
     * Skija draws directly into the window framebuffer); raster-backed
     * windows blit the CPU surface onto the native window layer. The default
     * implementation delegates to {@link #swapBuffers()}.
     *
     * @param result the surface result currently bound to the application
     *               canvas (may be null on early frames)
     */
    default void present(SurfaceResult result) {
        swapBuffers();
    }

    // ------------------------------------------------------------------
    // Event-loop ownership hooks
    // ------------------------------------------------------------------

    /**
     * Returns true when the backend owns the process-wide UI thread and its
     * event loop cannot be pumped from the caller thread (JWM: the native
     * library must be started from the main thread via {@code App.start} and
     * dispatches all window events from it). When this returns false (GLFW,
     * headless tests) {@code Application.run()} keeps its classical
     * poll/wait loop driven by the calling thread.
     *
     * @return true for app-owned-loop backends
     */
    default boolean isAppOwnedLoop() {
        return false;
    }

    /**
     * Called once by {@code Application.run()} immediately before entering
     * the frame loop, only when {@link #isAppOwnedLoop()} is true. The
     * backend uses this to hand control to its native event loop, invoking
     * {@code onFrame} once per dispatched event batch (the loop body), and
     * returning when the application signals close. The default implementation
     * simply runs the callback inline (polling loop fallback).
     *
     * @param onFrame one full iteration of the toolkit's frame loop
     */
    default void enterEventLoop(Runnable onFrame) {
        while (!shouldClose()) {
            onFrame.run();
        }
    }

    /**
     * Requests that the native event loop wake up and run another frame as
     * soon as possible (raster path has no vsync to pace frames). Default:
     * no-op — GLFW/polling backends are paced by swap intervals and real
     * events.
     */
    default void requestNewFrame() {
    }

    /**
     * Releases native resources. Equivalent to {@link #destroy()}.
     */
    @Override
    default void close() {
        destroy();
    }
}
