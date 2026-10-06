package com.glyphui.core.backend;

import com.glyphui.core.WindowConfig;
import com.glyphui.events.GlyphMods;

/**
 * Base implementation of {@link WindowBackend} holding all backend-neutral
 * cached state (geometry, content scale, title, close flag and the listener
 * registry) plus the headless bookkeeping rules required by the interface's
 * headless contract. Concrete backends only translate native events into
 * {@code notifyXxx(...)} calls and implement the live-window operations.
 */
public abstract class AbstractWindowBackend implements WindowBackend {

    // --- Cached geometry / metadata (logical + physical) -----------------
    protected int windowWidth;
    protected int windowHeight;
    protected int framebufferWidth;
    protected int framebufferHeight;
    protected float contentScaleX = 1.0f;
    protected float contentScaleY = 1.0f;
    protected String title;
    protected boolean shouldClose;

    /** Active configuration (hints + behaviour); never null. */
    protected final WindowConfig config;

    // --- Listener registry ------------------------------------------------
    protected WindowSizeListener windowSizeListener;
    protected FramebufferSizeListener framebufferSizeListener;
    protected ContentScaleListener contentScaleListener;
    protected CharListener charListener;
    protected KeyListener keyListener;
    protected MouseListener mouseListener;
    protected CursorPosListener cursorPosListener;
    protected TextInputListener textInputListener;
    protected ImeClient imeClient;

    /** Last known window position in screen coordinates (x, y); {0,0} until known. */
    protected int posX;
    protected int posY;

    protected AbstractWindowBackend(String title, int width, int height, WindowConfig config) {
        this.title = title;
        this.windowWidth = width;
        this.windowHeight = height;
        // Until the real framebuffer size is queried, assume 1:1 scaling
        this.framebufferWidth = width;
        this.framebufferHeight = height;
        this.shouldClose = false;
        this.config = config != null ? config : new WindowConfig();
        // The constructor arguments are the source of truth for geometry/title
        this.config.title = title;
        this.config.width = width;
        this.config.height = height;
    }

    // ------------------------------------------------------------------
    // Headless-safe state updates (interface contract)
    // ------------------------------------------------------------------

    @Override
    public void updateDimensions(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
        if (hasLiveWindow()) {
            int[] fb = queryNativeFramebufferSize();
            if (fb != null) {
                this.framebufferWidth = fb[0];
                this.framebufferHeight = fb[1];
                return;
            }
        }
        // No live native window (unit tests): derive physical from scale
        this.framebufferWidth = Math.round(width * getContentScaleX());
        this.framebufferHeight = Math.round(height * getContentScaleY());
    }

    @Override
    public void setSizes(int windowWidth, int windowHeight,
                         int framebufferWidth, int framebufferHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
    }

    @Override
    public void setContentScale(float scaleX, float scaleY) {
        this.contentScaleX = scaleX;
        this.contentScaleY = scaleY;
        notifyContentScale(scaleX, scaleY);
    }

    /**
     * Default: no live window (headless), so the position is unknown and
     * reported as the origin. Backends with a native window override.
     */
    @Override
    public int[] getPosition() {
        return new int[]{posX, posY};
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
        applyNativeTitle(title);
    }

    @Override
    public void setShouldClose(boolean shouldClose) {
        this.shouldClose = shouldClose;
        applyNativeShouldClose(shouldClose);
    }

    @Override
    public boolean shouldClose() {
        if (hasLiveWindow()) {
            return queryNativeShouldClose();
        }
        return shouldClose;
    }

    // ------------------------------------------------------------------
    // Geometry getters (cached values; no native round-trip)
    // ------------------------------------------------------------------

    @Override
    public int getWidth() {
        return windowWidth;
    }

    @Override
    public int getHeight() {
        return windowHeight;
    }

    @Override
    public int getFramebufferWidth() {
        return framebufferWidth;
    }

    @Override
    public int getFramebufferHeight() {
        return framebufferHeight;
    }

    @Override
    public float getContentScaleX() {
        return contentScaleX;
    }

    @Override
    public float getContentScaleY() {
        return contentScaleY;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public WindowConfig getConfig() {
        return config;
    }

    // ------------------------------------------------------------------
    // Listener registration
    // ------------------------------------------------------------------

    @Override
    public void setWindowSizeListener(WindowSizeListener listener) {
        this.windowSizeListener = listener;
    }

    @Override
    public void setFramebufferSizeListener(FramebufferSizeListener listener) {
        this.framebufferSizeListener = listener;
    }

    @Override
    public void setContentScaleListener(ContentScaleListener listener) {
        this.contentScaleListener = listener;
    }

    @Override
    public void setCharListener(CharListener listener) {
        this.charListener = listener;
    }

    @Override
    public void setKeyListener(KeyListener listener) {
        this.keyListener = listener;
    }

    @Override
    public void setMouseListener(MouseListener listener) {
        this.mouseListener = listener;
    }

    @Override
    public void setCursorPosListener(CursorPosListener listener) {
        this.cursorPosListener = listener;
    }

    @Override
    public void setTextInputListener(TextInputListener listener) {
        this.textInputListener = listener;
    }

    @Override
    public void setImeClient(ImeClient client) {
        this.imeClient = client;
    }

    /**
     * Default: no native IME bridge (GLFW); backends with IME support
     * override and consult {@link #imeClient}.
     */
    @Override
    public void setTextInputEnabled(boolean enabled) {
    }

    // ------------------------------------------------------------------
    // Notification helpers used by concrete backends when native events
    // arrive. Each helper refreshes the cached state FIRST so that
    // listeners observing getWidth()/getFramebufferWidth()/... already
    // see the new values (the historical GLFW callback ordering contract).
    // ------------------------------------------------------------------

    /** Fires the logical-size listener after caching the new size. */
    protected void notifyWindowSize(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
        if (windowSizeListener != null) {
            windowSizeListener.onWindowSizeChanged(width, height);
        }
    }

    /** Fires the framebuffer-size listener after caching the new size. */
    protected void notifyFramebufferSize(int width, int height) {
        this.framebufferWidth = width;
        this.framebufferHeight = height;
        if (framebufferSizeListener != null) {
            framebufferSizeListener.onFramebufferSizeChanged(width, height);
        }
    }

    /** Fires the content-scale listener after caching the new factors. */
    protected void notifyContentScale(float scaleX, float scaleY) {
        this.contentScaleX = scaleX;
        this.contentScaleY = scaleY;
        if (contentScaleListener != null) {
            contentScaleListener.onContentScaleChanged(scaleX, scaleY);
        }
    }

    /** Dispatches a printable-character event to the registered listener. */
    protected void notifyChar(int codepoint) {
        if (charListener != null) {
            charListener.onChar(codepoint);
        }
    }

    /** Dispatches a raw key event (toolkit codes, see GlyphKeys/GlyphMods). */
    protected void notifyKey(int keyCode, boolean pressed, int modifiers) {
        if (keyListener != null) {
            keyListener.onKey(keyCode, pressed, modifiers);
        }
    }

    /** Dispatches a mouse-button event (toolkit button ids, logical coords). */
    protected void notifyMouseButton(int button, boolean pressed, double x, double y, int modifiers) {
        if (mouseListener != null) {
            mouseListener.onMouseButton(button, pressed, x, y, modifiers);
        }
    }

    /** Dispatches a cursor-position event (logical coordinates). */
    protected void notifyCursorPos(double x, double y) {
        if (cursorPosListener != null) {
            cursorPosListener.onCursorPos(x, y);
        }
    }

    /**
     * Dispatches an IME text-input event. The default implementation (used
     * by backends without a dedicated listener registered) forwards one
     * char event per codepoint, surrogate pairs included, so applications
     * written against the plain char callback keep working.
     */
    protected void notifyTextInput(String text, int replacementStart, int replacementEnd) {
        if (textInputListener != null) {
            textInputListener.onTextInput(text, replacementStart, replacementEnd);
        } else if (text != null) {
            text.codePoints().forEach(this::notifyChar);
        }
    }

    // ------------------------------------------------------------------
    // Backend hooks with safe default (no-op / cached-value) behaviour,
    // so headless paths never touch native code.
    // ------------------------------------------------------------------

    /** @return true when a live native window exists */
    protected abstract boolean hasLiveWindow();

    /** Applies the title to the live native window; no-op by default. */
    protected void applyNativeTitle(String title) {
    }

    /** Propagates the close request to the live native window; no-op by default. */
    protected void applyNativeShouldClose(boolean shouldClose) {
    }

    /** Queries the native close flag; defaults to the cached value. */
    protected boolean queryNativeShouldClose() {
        return shouldClose;
    }

    /** Queries the native framebuffer size; {@code null} = unavailable. */
    protected int[] queryNativeFramebufferSize() {
        return null;
    }

    /**
     * Native modifier remap hook. The default passes the bits through
     * unchanged — valid for GLFW, whose {@code GLFW_MOD_*} layout is
     * identical to {@link GlyphMods}. JWM overrides it.
     */
    protected int mapNativeModifiers(int nativeMods) {
        return nativeMods;
    }
}
