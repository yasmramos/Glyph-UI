package io.github.yasmramos.glyphui.core.backend;

import io.github.yasmramos.glyphui.core.WindowConfig;

/**
 * In-memory {@link WindowBackend} used by unit and characterization tests.
 *
 * <p>The backend contract is explicitly headless-safe (every method must be
 * callable before {@code create()}), so this implementation records listener
 * registrations, caches geometry state and exposes test drivers that fire the
 * registered callbacks — letting tests exercise the toolkit's input routing,
 * focus-loss handling, HiDPI validation and framebuffer-resize hardening
 * without any native windowing library.</p>
 *
 * <p>Not thread-safe by design: tests drive it from a single thread.</p>
 */
public class HeadlessTestBackend implements WindowBackend {

    // --- Cached state -----------------------------------------------------
    private final WindowConfig config;
    private int width;
    private int height;
    private int fbWidth;
    private int fbHeight;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private String title;
    private int posX;
    private int posY;
    private boolean shouldClose;
    private boolean created;
    private boolean destroyed;
    private String clipboard = "";

    // --- Registered listeners --------------------------------------------
    public FramebufferSizeListener framebufferSizeListener;
    public ContentScaleListener contentScaleListener;
    public MouseListener mouseListener;
    public CursorPosListener cursorPosListener;
    public KeyListener keyListener;
    public CharListener charListener;
    public TextInputListener textInputListener;
    public WindowFocusListener windowFocusListener;
    public ImeClient imeClient;
    public boolean textInputEnabled;

    public HeadlessTestBackend(String title, int width, int height, WindowConfig config) {
        this.title = title;
        this.width = width;
        this.height = height;
        this.fbWidth = width;
        this.fbHeight = height;
        this.config = config != null ? config : new WindowConfig();
    }

    // --- Lifecycle ---------------------------------------------------------
    @Override
    public boolean create() {
        created = true;
        return true;
    }

    @Override
    public void destroy() {
        destroyed = true;
    }

    public boolean isCreated() {
        return created;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    // --- Event pumping (no-ops for the fake) -------------------------------
    @Override
    public boolean pollEvents() {
        return !shouldClose;
    }

    @Override
    public void waitEvents() {
    }

    @Override
    public void waitEventsTimeout(double seconds) {
    }

    @Override
    public void postEmptyEvent() {
    }

    @Override
    public boolean shouldClose() {
        return shouldClose;
    }

    @Override
    public void setShouldClose(boolean shouldClose) {
        this.shouldClose = shouldClose;
    }

    // --- Geometry -----------------------------------------------------------
    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getFramebufferWidth() {
        return fbWidth;
    }

    @Override
    public int getFramebufferHeight() {
        return fbHeight;
    }

    @Override
    public float getContentScaleX() {
        return scaleX;
    }

    @Override
    public float getContentScaleY() {
        return scaleY;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public WindowConfig getConfig() {
        return config;
    }

    @Override
    public void updateDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void setSizes(int windowWidth, int windowHeight,
                         int framebufferWidth, int framebufferHeight) {
        this.width = windowWidth;
        this.height = windowHeight;
        this.fbWidth = framebufferWidth;
        this.fbHeight = framebufferHeight;
    }

    @Override
    public void setContentScale(float scaleX, float scaleY) {
        this.scaleX = scaleX;
        this.scaleY = scaleY;
    }

    // --- Window attributes (recorded no-ops) --------------------------------
    @Override
    public void show() {
    }

    @Override
    public void hide() {
    }

    @Override
    public void setDecorated(boolean decorated) {
    }

    @Override
    public void setResizable(boolean resizable) {
    }

    @Override
    public void setFloating(boolean floating) {
    }

    @Override
    public void setOpacity(float opacity) {
    }

    @Override
    public void setPosition(int x, int y) {
        this.posX = x;
        this.posY = y;
    }

    @Override
    public int[] getPosition() {
        return new int[]{posX, posY};
    }

    @Override
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    @Override
    public void setMaximized(boolean maximized) {
    }

    @Override
    public void minimize() {
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
    }

    // --- Clipboard -----------------------------------------------------------
    @Override
    public void setClipboardString(String text) {
        this.clipboard = text;
    }

    @Override
    public String getClipboardString() {
        return clipboard;
    }

    // --- GL / presentation ----------------------------------------------------
    @Override
    public boolean isGlCapable() {
        return false;
    }

    @Override
    public void swapBuffers() {
    }

    // --- Listener registration -------------------------------------------------
    @Override
    public void setWindowSizeListener(WindowSizeListener listener) {
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
    public void setTextInputListener(TextInputListener listener) {
        this.textInputListener = listener;
    }

    @Override
    public void setImeClient(ImeClient client) {
        this.imeClient = client;
    }

    @Override
    public void setTextInputEnabled(boolean enabled) {
        this.textInputEnabled = enabled;
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
    public void setWindowFocusListener(WindowFocusListener listener) {
        this.windowFocusListener = listener;
    }
}
