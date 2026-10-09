package com.glyphui.core.backend;

import com.glyphui.core.WindowConfig;
import com.glyphui.events.GlyphKeys;
import com.glyphui.events.GlyphMods;

import io.github.humbleui.jwm.App;
import io.github.humbleui.jwm.Clipboard;
import io.github.humbleui.jwm.ClipboardEntry;
import io.github.humbleui.jwm.ClipboardFormat;
import io.github.humbleui.jwm.EventKey;
import io.github.humbleui.jwm.EventMouseButton;
import io.github.humbleui.jwm.EventMouseMove;
import io.github.humbleui.jwm.EventMouseScroll;
import io.github.humbleui.jwm.EventTextInput;
import io.github.humbleui.jwm.EventTextInputMarked;
import io.github.humbleui.jwm.EventWindowClose;
import io.github.humbleui.jwm.EventWindowCloseRequest;
import io.github.humbleui.jwm.EventWindowMove;
import io.github.humbleui.jwm.EventWindowResize;
import io.github.humbleui.jwm.EventWindowScreenChange;
import io.github.humbleui.jwm.Key;
import io.github.humbleui.jwm.KeyModifier;
import io.github.humbleui.jwm.MouseCursor;
import io.github.humbleui.jwm.Screen;
import io.github.humbleui.jwm.TextInputClient;
import io.github.humbleui.jwm.Window;
import io.github.humbleui.types.IRange;
import io.github.humbleui.types.IRect;
import java.lang.reflect.InvocationTargetException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * JWM {@link WindowBackend}: native windows driven by
 * <em>io.github.humbleui:jwm</em>, the toolkit's primary backend. It
 * provides what GLFW (via LWJGL) could not: a real native IME bridge
 * ({@code TextInputClient} / {@code EventTextInput}), cross-platform
 * clipboard and per-monitor DPI scale through {@code Screen._scale}.
 *
 * <p><strong>Event-loop model:</strong> JWM owns the process UI thread —
 * {@code App.start(...)} must run on the main thread, dispatches every
 * window event from it and <b>blocks inside the native message loop until
 * {@code App.terminate()}</b> (true on Windows/X11 as well as macOS). It
 * therefore cannot run inside {@link #create()}, which must return so the
 * application can build its widget tree; the whole native bootstrap
 * (library load, window creation, message loop) is <b>deferred to
 * {@link #enterEventLoop(Runnable)}</b>, called by {@code Application.run()}.
 * Until then the backend is fully headless but already answers geometry
 * queries from the cached configuration (see the headless contract).</p>
 *
 * <p>Because the native loop only pumps messages on the JWM UI thread, the
 * toolkit's frame body is driven from a small daemon thread that posts it
 * via {@code App.runOnUIThread(...)} — so {@code Application.run()}'s loop,
 * all widget state and every Skija call stay on the UI thread. When the
 * application signals close, the driver posts {@code App.terminate()} and
 * the native loop unwinds, returning control to {@code enterEventLoop()}.</p>
 *
 * <p><strong>Rendering:</strong> raster-first. The application paints into
 * a CPU Skija surface; on every frame the backend blits it onto JWM's
 * native raster layer (created reflectively against
 * {@code io.github.humbleui.jwm.skija.LayerRasterSkija}, so this class
 * compiles without skija bindings on the compile classpath). The
 * experimental GL layer ({@code -Dglyphui.jwm.gl=true}) is not wired end to
 * end yet and is refused with a warning, falling back to raster.</p>
 *
 * <p><strong>Headless contract:</strong> nothing native is touched until
 * {@link #create()} runs, so cached-state setters and listener wiring work
 * display-free (unit tests).</p>
 */
public class JwmWindowBackend extends AbstractWindowBackend {

    /** System property to force the (experimental) GL layer over raster. */
    private static final String USE_GL_PROPERTY = "glyphui.jwm.gl";

    // --- Native state (only valid after create()) -------------------------
    private volatile Window jwmWindow;
    /** True while the app-owned native loop is running (see enterEventLoop). */
    private final AtomicBoolean loopRunning = new AtomicBoolean(false);
    /** Set when create() ran; the native bootstrap itself happens later. */
    private final AtomicBoolean initAttempted = new AtomicBoolean(false);
    /**
     * True when {@link #create()} succeeded and the native bootstrap
     * (library load + window creation + message loop) is still owed to
     * {@link #enterEventLoop(Runnable)}.
     */
    private volatile boolean pendingBootstrap;
    /** True when the deferred native bootstrap failed; the loop must not start. */
    private volatile boolean bootstrapFailed;

    /** Frame driver thread posting the toolkit loop onto the JWM UI thread. */
    private volatile Thread frameDriver;

    /** True while JWM's native message loop is pumping (enterEventLoop). */
    private volatile boolean nativeLoopActive;

    /** Desired IME state, applied at window creation (may arrive before it). */
    private volatile boolean textInputEnabled;

    /** JWM Skija raster layer hosting the pixels presented to the window. */
    private volatile io.github.humbleui.jwm.skija.LayerRasterSkija rasterLayer;
    /** Skija surface wrapping the layer pixels; null until JWM's first frame. */
    private volatile io.github.humbleui.skija.Surface layerSurface;
    /** True while a presentation layer is attached to the JWM window. */
    private volatile boolean layerAttached;
    private boolean useGl;

    /** Cursor position in logical coords, tracked from mouse events. */
    private double cursorX, cursorY;

    public JwmWindowBackend(String title, int width, int height, WindowConfig config) {
        super(title, width, height, config);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Deferred by design: {@code App.start(...)} blocks inside JWM's native
     * message loop until {@code App.terminate()} (see the class doc), so
     * creating the window here would deadlock {@code Application.init()} and
     * the application would never show. This method only records the
     * request; the native bootstrap runs later, from
     * {@link #enterEventLoop(Runnable)}, on the JWM UI thread. Until then
     * the backend answers every query from the cached configuration
     * (headless contract), so building the widget tree works unchanged.
     *
     * @return true when the bootstrap has been scheduled (or already ran)
     */
    @Override
    public boolean create() {
        if (!initAttempted.compareAndSet(false, true)) {
            return pendingBootstrap || jwmWindow != null;
        }
        useGl = Boolean.getBoolean(USE_GL_PROPERTY);
        if (useGl) {
            // The GL layer needs a live GL context, but the Skija surface is
            // built during Application.init(), before this deferred window
            // exists (see isGlCapable()). Until both ends can be wired
            // together, honour the flag as "not yet supported" instead of
            // presenting nothing: the raster layer below is always used.
            System.err.println("Glyph UI: -Dglyphui.jwm.gl=true is not supported yet; "
                + "using the JWM raster layer.");
            useGl = false;
        }
        pendingBootstrap = true;
        return true;
    }

    /**
     * Creates the native window and its presentation layer. Runs on the JWM
     * UI thread inside the {@code App.start(...)} launcher callback — the
     * only context where the JWM API is legal — and finishes by making the
     * window visible, since JWM creates windows hidden.
     *
     * @return true when the window exists and is ready to draw
     */
    private boolean createNativeWindow() {
        try {
            Window w = App.makeWindow();
            w.setEventListener(this::onJwmEvent);
            w.setTitle(title);
            w.setContentSize(windowWidth, windowHeight);
            // Publish before attaching the layer: setLayer() fires screen and
            // resize events synchronously and their handlers query the live
            // window for scale and geometry.
            this.jwmWindow = w;
            attachLayer(w);
            installTextInputClient(w);
            // Seed geometry/scale from the screen events JWM dispatches during
            // the next loop iteration; meanwhile use the primary screen scale.
            applyScreenScale(App.getPrimaryScreen());
            applyPendingWindowState(w);
            return true;
        } catch (Throwable t) {
            System.err.println("Glyph UI: JWM initialization failed (" + t.getMessage()
                + "). Running headless; window operations are no-ops.");
            t.printStackTrace();
            this.jwmWindow = null;
            return false;
        }
    }

    /**
     * Applies the {@link WindowConfig} state that could not be set before
     * the native window existed (maximize / float / fullscreen / centering /
     * IME), then shows the window when {@code config.visible} is set —
     * the step missing from the original bootstrap, and the direct reason
     * nothing ever appeared on screen.
     */
    private void applyPendingWindowState(Window w) {
        // JWM creates the HWND with a style read from a handle that does not
        // exist yet (WindowWin32::_createInternal calls _getWindowStyle()
        // before CreateWindowExW), so the window only ends up with WS_CAPTION
        // and is missing WS_SYSMENU / WS_MINIMIZEBOX / WS_MAXIMIZEBOX /
        // WS_THICKFRAME — i.e. no close/minimize/maximize buttons and no
        // resize border. setTitlebarVisible() ORs that exact style set in
        // (and reapplies the frame), which is precisely config.decorated;
        // false strips the whole titlebar, as the option promises.
        w.setTitlebarVisible(config.decorated);

        if (config.maximized) {
            w.maximize();
        }
        if (config.floating) {
            w.setZOrder(io.github.humbleui.jwm.ZOrder.FLOATING);
        }
        if (config.fullscreen) {
            w.setFullScreen(true);
        }
        if (config.center) {
            centerOnPrimaryScreen(w);
        }
        if (textInputEnabled) {
            w.setTextInputEnabled(true);
        }
        if (config.visible) {
            w.setVisible(true);
        }
    }

    /**
     * Centers the window on the primary monitor's work area. JWM places new
     * windows at the OS default position (CW_USEDEFAULT), so without this
     * {@code WindowConfig.center} would be ignored on the JWM backend.
     *
     * @param w the freshly created window (UI thread)
     */
    private static void centerOnPrimaryScreen(Window w) {
        Screen screen = App.getPrimaryScreen();
        if (screen == null || screen._bounds == null) {
            return;
        }
        IRect content = w.getContentRect();
        int x = screen._bounds._left
            + Math.max(0, (screen._bounds.getWidth() - content.getWidth()) / 2);
        int y = screen._bounds._top
            + Math.max(0, (screen._bounds.getHeight() - content.getHeight()) / 2);
        w.setWindowPosition(x, y);
    }

    /**
     * Creates and attaches the presentation layer: JWM's Skija raster layer,
     * whose native pixel buffer is where {@link #present(SurfaceResult)}
     * blits the application frame.
     *
     * <p>Typed against skija/JWM directly: the historical reflective probing
     * targeted APIs that do not exist in the pinned versions
     * ({@code skija.AlphaType} is {@code ColorAlphaType}, and
     * {@code ColorInfo} has no {@code makeARGB(...)} factory), so it always
     * fell through to a raw layer with no drawing path — a visible but blank
     * window. The layer keeps JWM's default {@code ColorInfo} (N32/premul),
     * which matches the N32 premul surface the raster factory produces.</p>
     *
     * @param w the freshly created window (UI thread)
     */
    private void attachLayer(Window w) {
        if (useGl) {
            // Refused in create() today (no GL context exists before the
            // deferred window); kept so the flag has a defined meaning once
            // the GL path is wired end to end.
            layerAttached = true;
            w.setLayer(new io.github.humbleui.jwm.skija.LayerGLSkija());
            return;
        }
        try {
            io.github.humbleui.jwm.skija.LayerRasterSkija layer =
                new io.github.humbleui.jwm.skija.LayerRasterSkija();
            // Publish BEFORE setLayer(): attaching fires EventWindowScreenChange
            // (which JWM turns into a resize), and that resize runs our own
            // listener — it must see the layer as already attached, otherwise
            // ensureLayerAttached() would attach it again, recursively.
            this.rasterLayer = layer;
            this.layerSurface = null;
            this.layerAttached = true;
            w.setLayer(layer);
            // Null until JWM runs the layer's first frame (see resolveLayerSurface).
            this.layerSurface = layer.getSurface();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot create JWM presentation layer", e);
        }
    }

    /**
     * Detaches the presentation layer while the window has no drawable area
     * (minimized: JWM reports 0x0 and its layer ends up empty).
     *
     * <p>This is what actually stops the resize crash. On a 0x0 resize JWM
     * runs our listener and <b>then</b> feeds an {@code EventFrame} to
     * itself ({@code Window.accept}), whose {@code LayerRasterSkija.frame()}
     * calls {@code Surface.wrapPixels(...)} on the empty buffer and throws
     * from inside the native dispatch — aborting the {@code WM_PAINT}
     * handler, which Windows immediately re-sends, in a tight loop. With no
     * layer attached JWM skips all layer work ({@code if (_layer != null)}),
     * and {@code requestFrame()} becomes a no-op too (it is gated on JWM's
     * "has attached layer" flag).</p>
     */
    private void detachLayer() {
        Window w = jwmWindow;
        if (w == null || !layerAttached) {
            return;
        }
        layerAttached = false;
        rasterLayer = null;
        layerSurface = null;
        try {
            w.setLayer(null); // closes and frees the old layer
        } catch (Throwable t) {
            System.err.println("Glyph UI: could not detach the JWM layer ("
                + t.getMessage() + ").");
        }
    }

    /**
     * Re-attaches the layer after the window becomes drawable again (restore
     * from minimize). The layer is sized by the resize event that JWM
     * generates right after {@code setLayer}, so nothing else is needed.
     *
     * @param w the live window (UI thread)
     */
    private void ensureLayerAttached(Window w) {
        if (layerAttached) {
            return;
        }
        attachLayer(w);
    }

    /** Wires the IME bridge: caret rect + selection come from ImeClient. */
    private void installTextInputClient(Window w) {
        w.setTextInputClient(new TextInputClient() {
            @Override
            public IRect getRectForMarkedRange(int insertionStart, int insertionEnd) {
                if (imeClient == null) {
                    return IRect.makeXYWH(posX, posY, 1, 1);
                }
                int[] r = imeClient.getCursorRect(insertionStart, insertionEnd);
                if (r == null || r.length < 4) {
                    return IRect.makeXYWH(posX, posY, 1, 1);
                }
                return IRect.makeLTRB(r[0], r[1], r[2], r[3]);
            }

            @Override
            public IRange getSelectedRange() {
                if (imeClient == null) {
                    return new IRange(0, 0);
                }
                int[] sel = imeClient.getSelectionRange();
                if (sel == null || sel.length < 2) {
                    return new IRange(0, 0);
                }
                return new IRange(sel[0], sel[1]);
            }

            @Override
            public IRange getMarkedRange() {
                return new IRange(0, 0);
            }

            @Override
            public String getSubstring(int start, int end) {
                if (imeClient == null) {
                    return "";
                }
                String text = imeClient.getText();
                if (text == null || text.isEmpty()) {
                    return "";
                }
                int lo = Math.max(0, Math.min(start, text.length()));
                int hi = Math.max(lo, Math.min(end, text.length()));
                return text.substring(lo, hi);
            }
        });
    }

    @Override
    public void destroy() {
        stopFrameDriver();
        pendingBootstrap = false;
        Window w = jwmWindow;
        jwmWindow = null;
        if (w != null) {
            try {
                w.close();
            } catch (Throwable ignored) {
                // already closed / wrong thread
            }
        }
        shouldClose = true;
    }

    // ------------------------------------------------------------------
    // Event loop (app-owned)
    // ------------------------------------------------------------------

    @Override
    public boolean isAppOwnedLoop() {
        // Backend property, not a window property: the native loop must be
        // entered even though the window does not exist yet (create() defers
        // the bootstrap — see the class doc). Headless paths without a
        // pending bootstrap are handled inside enterEventLoop(Runnable).
        return true;
    }

    /**
     * Bootstraps JWM and runs its native message loop. {@code App.start(...)}
     * executes the launcher (window creation + frame driver) and then
     * <b>blocks pumping messages on the calling thread</b> — which must be
     * the UI thread — until {@code App.terminate()} posts WM_CLOSE. Control
     * returns to {@code Application.run()} only once the app has shut down.
     *
     * @param onFrame one full iteration of the toolkit's frame loop
     */
    @Override
    public void enterEventLoop(Runnable onFrame) {
        if (!pendingBootstrap) {
            // Headless/test path: no native window owed, drive frames inline.
            while (!shouldClose()) {
                onFrame.run();
            }
            return;
        }
        pendingBootstrap = false;
        try {
            App.start(() -> {
                if (createNativeWindow()) {
                    nativeLoopActive = true;
                    startFrameDriver(onFrame);
                } else {
                    bootstrapFailed = true;
                    shouldClose = true;
                    // We are on the UI thread: post WM_CLOSE so the message
                    // loop (started right after this launcher returns) exits
                    // instead of blocking forever.
                    App.terminate();
                }
            });
        } catch (Throwable t) {
            bootstrapFailed = true;
            shouldClose = true;
            System.err.println("Glyph UI: JWM event loop failed (" + t.getMessage() + ").");
            t.printStackTrace();
        } finally {
            nativeLoopActive = false;
            stopFrameDriver();
        }
    }

    /**
     * Starts the daemon that drives the toolkit loop while JWM pumps
     * messages. Every frame body is posted onto the JWM UI thread through
     * {@code App.runOnUIThread(...)}, so widget state and Skija stay
     * single-threaded; when the application asks to close, the driver posts
     * {@code App.terminate()} to unwind the native loop.
     *
     * @param onFrame one full iteration of the toolkit's frame loop
     */
    private void startFrameDriver(Runnable onFrame) {
        loopRunning.set(true);
        Thread driver = new Thread(() -> {
            while (loopRunning.get() && !shouldClose()) {
                App.runOnUIThread(() -> {
                    if (shouldClose()) {
                        return;
                    }
                    try {
                        onFrame.run();
                    } catch (Throwable t) {
                        // Throwing into the native dispatcher would abort the
                        // message loop: report and shut down cleanly instead.
                        System.err.println("Glyph UI: frame step failed ("
                            + t.getMessage() + ").");
                        t.printStackTrace();
                        setShouldClose(true);
                    }
                });
                try {
                    Thread.sleep(8); // ~120 Hz cadence; frames stay dirty-gated
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            if (nativeLoopActive) {
                try {
                    App.runOnUIThread(App::terminate);
                } catch (Throwable ignored) {
                    // Native loop already gone.
                }
            }
        }, "glyphui-jwm-driver");
        driver.setDaemon(true);
        this.frameDriver = driver;
        driver.start();
    }

    /** Stops the frame driver (idempotent). */
    private void stopFrameDriver() {
        loopRunning.set(false);
        Thread driver = this.frameDriver;
        this.frameDriver = null;
        if (driver != null) {
            driver.interrupt();
            try {
                driver.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void requestNewFrame() {
        Window w = jwmWindow;
        if (w != null) {
            try {
                w.requestFrame();
            } catch (Throwable ignored) {
                // window closing
            }
        }
    }

    @Override
    public void pollEvents() {
        // JWM dispatches events from its own loop; nothing to poll here.
    }

    @Override
    public void waitEvents() {
        Window w = jwmWindow;
        if (w == null) {
            sleepQuietly(16);
            return;
        }
        // Block until an event arrives: park briefly, JWM keeps dispatching
        // into onJwmEvent from the UI thread. requestFrame guarantees the
        // loop wakes periodically even with zero input (raster pacing).
        long deadline = System.nanoTime() + 16_000_000L;
        while (System.nanoTime() < deadline && !shouldClose()) {
            sleepQuietly(2);
        }
        w.requestFrame();
    }

    @Override
    public void waitEventsTimeout(double seconds) {
        long ms = Math.max(1, (long) (seconds * 1000));
        sleepQuietly(ms);
        Window w = jwmWindow;
        if (w != null) {
            try {
                w.requestFrame();
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void swapBuffers() {
        // Raster presentation happens in present(); GL builds would swap the
        // window's own buffers here — JWM owns the swap, so just ask for a frame.
        requestNewFrame();
    }

    @Override
    public void postEmptyEvent() {
        Window w = jwmWindow;
        if (w != null) {
            try {
                w.requestFrame();
            } catch (Throwable ignored) {
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------
    // Presentation
    // ------------------------------------------------------------------

    @Override
    public boolean isGlCapable() {
        // Raster by construction: the Skija surface is created during
        // Application.init(), i.e. BEFORE the deferred native window (and
        // therefore before any GL context exists), so a GPU surface cannot
        // be built on this backend. Application then routes every frame
        // through present(). See create() for the flag handling.
        return useGl && hasLiveWindow();
    }

    /**
     * Blits the application's CPU surface onto JWM's raster layer and asks
     * the native loop for a frame (that frame is what pushes the layer
     * pixels to the window through {@code LayerRasterSkija.swapBuffers()}).
     *
     * <p>The layer surface is resolved lazily because JWM allocates it on
     * the <b>first native frame</b>; before that this method reports the
     * frame as not presented so {@code Application} repaints once the layer
     * is ready.</p>
     *
     * @param result the surface result currently bound to the app canvas
     * @return true when the frame reached the native layer
     */
    @Override
    public boolean present(SurfaceResult result) {
        Window w = jwmWindow;
        if (w == null) {
            return true; // headless: there is no window to present to
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            // Minimized: JWM resized its layer to 0x0, and requestFrame()
            // would make LayerRasterSkija.frame() wrap empty pixels — an
            // IllegalArgumentException thrown from inside the native
            // dispatch. Nothing to show anyway, so just skip the frame.
            return false;
        }
        if (result == null || rasterLayer == null) {
            w.requestFrame();
            return true;
        }
        io.github.humbleui.skija.Surface dst = resolveLayerSurface();
        if (dst == null) {
            // Layer pixels not allocated yet: request a native frame (that is
            // what allocates them) and ask the caller for another paint.
            w.requestFrame();
            return false;
        }
        try {
            io.github.humbleui.skija.Surface src = result.surface();
            int dw = dst.getWidth();
            int dh = dst.getHeight();
            try (io.github.humbleui.skija.Image img = src.makeImageSnapshot(
                    io.github.humbleui.types.IRect.makeXYWH(0, 0,
                            Math.min(dw, src.getWidth()), Math.min(dh, src.getHeight())))) {
                dst.getCanvas().clear(0x00000000);
                // Plain drawImage blits 1:1 without resampling (nearest by construction).
                dst.getCanvas().drawImage(img, 0, 0);
            }
            dst.flush();
            return true;
        } catch (Throwable t) {
            // Surface recreated concurrently with resize: skip this frame.
            return false;
        } finally {
            w.requestFrame();
        }
    }

    /**
     * Returns the layer's Skija surface, refreshing the cache when JWM has
     * (re)allocated it. {@code LayerRasterSkija.getSurface()} returns null
     * until its first {@code frame()}, and a resize nulls the cache, so a
     * null result is retried on the next paint.
     *
     * @return the layer surface, or null while it is not allocated yet
     */
    private io.github.humbleui.skija.Surface resolveLayerSurface() {
        io.github.humbleui.skija.Surface surface = layerSurface;
        if (surface == null && rasterLayer != null) {
            surface = rasterLayer.getSurface();
            layerSurface = surface;
        }
        return surface;
    }

    // ------------------------------------------------------------------
    // Native attribute operations
    // ------------------------------------------------------------------

    @Override
    protected boolean hasLiveWindow() {
        return jwmWindow != null;
    }

    @Override
    protected void applyNativeTitle(String t) {
        Window w = jwmWindow;
        if (w != null) {
            w.setTitle(t);
        }
    }

    @Override
    public void show() {
        Window w = jwmWindow;
        if (w != null) {
            w.setVisible(true);
        }
    }

    @Override
    public void hide() {
        Window w = jwmWindow;
        if (w != null) {
            w.setVisible(false);
        }
    }

    @Override
    public void setDecorated(boolean decorated) {
        // Always remember the intent in config (headless contract: setters
        // sync the config even without a live window).
        config.decorated = decorated;
        Window w = jwmWindow;
        if (w != null) {
            w.setTitlebarVisible(decorated);
        }
    }

    @Override
    public void setResizable(boolean resizable) {
        // JWM does not expose a runtime resizability toggle; remembered in
        // the config for recreation-based backends.
        config.resizable = resizable;
    }

    @Override
    public void setFloating(boolean floating) {
        // Remember the intent in config even when headless (see setDecorated).
        config.floating = floating;
        Window w = jwmWindow;
        if (w != null) {
            w.setZOrder(floating
                ? io.github.humbleui.jwm.ZOrder.FLOATING
                : io.github.humbleui.jwm.ZOrder.NORMAL);
        }
    }

    @Override
    public void setOpacity(float opacity) {
        Window w = jwmWindow;
        if (w != null) {
            w.setOpacity(Math.max(0f, Math.min(1f, opacity)));
        }
    }

    @Override
    public void setPosition(int x, int y) {
        posX = x;
        posY = y;
        Window w = jwmWindow;
        if (w != null) {
            w.setWindowPosition(x, y);
        }
    }

    @Override
    public void setSize(int width, int height) {
        Window w = jwmWindow;
        if (w != null) {
            w.setContentSize(width, height);
            // The authoritative update arrives via EventWindowResize; keep
            // the cache optimistic so same-frame reads see the new size.
            notifyWindowSize(width, height);
            notifyFramebufferSize(Math.round(width * contentScaleX),
                                  Math.round(height * contentScaleY));
        } else {
            updateDimensions(width, height);
        }
    }

    @Override
    public void setMaximized(boolean maximized) {
        Window w = jwmWindow;
        if (w != null) {
            if (maximized) {
                w.maximize();
            } else {
                w.restore();
            }
        }
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        Window w = jwmWindow;
        // Headless contract: without a live window this is a guarded no-op
        // and must not flip the config flag (nothing was actually applied).
        if (w == null) {
            return;
        }
        w.setFullScreen(fullscreen);
        config.fullscreen = fullscreen;
    }

    @Override
    public long getWindowHandle() {
        return 0L; // JWM exposes no numeric handle
    }

    // ------------------------------------------------------------------
    // Clipboard
    // ------------------------------------------------------------------

    @Override
    public void setClipboardString(String text) {
        if (!hasLiveWindow()) {
            return;
        }
        Clipboard.set(ClipboardEntry.makePlainText(text == null ? "" : text));
    }

    @Override
    public String getClipboardString() {
        if (!hasLiveWindow()) {
            return "";
        }
        ClipboardEntry entry = Clipboard.get(ClipboardFormat.TEXT);
        if (entry == null || entry._data == null) {
            return "";
        }
        return new String(entry._data, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // IME enable/disable
    // ------------------------------------------------------------------

    @Override
    public void setTextInputEnabled(boolean enabled) {
        // Remember the request: Application.init() enables the IME before the
        // deferred native window exists (see create()/enterEventLoop()).
        this.textInputEnabled = enabled;
        Window w = jwmWindow;
        if (w != null) {
            w.setTextInputEnabled(enabled);
            if (!enabled) {
                w.unmarkText();
            }
        }
    }

    // ------------------------------------------------------------------
    // Event dispatch (called by JWM from the UI thread)
    // ------------------------------------------------------------------

    /**
     * Listener entry point registered with {@code Window.setEventListener}.
     * JWM invokes it from inside its native event dispatch (a JNI upcall), so
     * <b>no exception may escape</b>: a pending Java exception there surfaces
     * in the middle of the message loop, spraying errors and aborting the
     * whole run. Failures are reported and the loop keeps going.
     *
     * @param event the JWM event to process
     */
    private void onJwmEvent(io.github.humbleui.jwm.Event event) {
        try {
            handleJwmEvent(event);
        } catch (Throwable t) {
            System.err.println("Glyph UI: JWM event handler failed ("
                + t.getMessage() + ").");
            t.printStackTrace();
        }
    }

    /** Body of {@link #onJwmEvent}; runs on the JWM UI thread. */
    private void handleJwmEvent(io.github.humbleui.jwm.Event event) {
        Window w = jwmWindow;
        if (event instanceof EventWindowResize e) {
            // Logical size = content area (window rect includes decorations)
            notifyWindowSize(e.getContentWidth(), e.getContentHeight());
            Screen s = w != null ? w.getScreen() : null;
            float scale = s != null && s._scale > 0f ? s._scale : 1f;
            notifyFramebufferSize(Math.round(e.getContentWidth() * scale),
                                  Math.round(e.getContentHeight() * scale));
            if (w != null) {
                if (e.getContentWidth() <= 0 || e.getContentHeight() <= 0) {
                    // Minimized: drop the layer *before* JWM re-feeds the
                    // EventFrame it dispatches after this listener, so that
                    // frame finds no layer and cannot throw (see detachLayer).
                    detachLayer();
                } else {
                    // First real size, or back from minimize.
                    ensureLayerAttached(w);
                }
                invalidateLayerSurface();
            }
        } else if (event instanceof EventWindowScreenChange) {
            applyScreenScale(w != null ? w.getScreen() : null);
        } else if (event instanceof EventWindowMove e) {
            posX = e.getWindowLeft();
            posY = e.getWindowTop();
        } else if (event instanceof EventWindowCloseRequest) {
            setShouldClose(true);
        } else if (event instanceof EventWindowClose) {
            shouldClose = true;
            loopRunning.set(false);
        } else if (event instanceof EventKey e) {
            Integer code = KEY_MAP.get(e._key);
            if (code != null) {
                notifyKey(code, e._isPressed, mapNativeModifiers(e._modifiers));
            }
        } else if (event instanceof EventTextInput e) {
            notifyTextInput(e._text, e._replacementStart, e._replacementEnd);
        } else if (event instanceof EventTextInputMarked) {
            // Pre-edit (composing) text: not surfaced through the neutral
            // API yet; ignore so the committed EventTextInput drives input.
        } else if (event instanceof EventMouseButton e) {
            cursorX = e._x;
            cursorY = e._y;
            notifyMouseButton(buttonToToolkit(e._button), e._isPressed,
                e._x, e._y, mapNativeModifiers(e._modifiers));
        } else if (event instanceof EventMouseMove e) {
            cursorX = e._x;
            cursorY = e._y;
            notifyCursorPos(e._x, e._y);
        } else if (event instanceof EventMouseScroll e) {
            // Map wheel to toolkit buttons 3 (up) / 4 (down), like GLFW.
            int btn = e._deltaY >= 0 ? 3 : 4;
            notifyMouseButton(btn, true, e._x, e._y, mapNativeModifiers(e._modifiers));
            notifyMouseButton(btn, false, e._x, e._y, mapNativeModifiers(e._modifiers));
        }
    }

    /**
     * Drops the cached layer-surface reference after a window resize.
     *
     * <p>JWM resizes its own layer ({@code Window.accept(EventWindowResize)}
     * runs {@code Layer.resize(...)} <b>before</b> this backend's listener),
     * and {@code LayerRasterSkija} closes and nulls its Skija surface on
     * that resize. Only the cache has to be invalidated here: re-wrapping
     * with the wrong size (the old code resized the layer again with the
     * toolkit's framebuffer value) would fight JWM's own bookkeeping, and
     * the surface is rebuilt lazily on the next frame anyway.</p>
     */
    private void invalidateLayerSurface() {
        layerSurface = null;
    }

    /** Applies the monitor scale as content scale (uniform on all platforms). */
    private void applyScreenScale(Screen screen) {
        float scale = screen != null && screen._scale > 0f ? screen._scale : 1f;
        setContentScale(scale, scale);
        notifyFramebufferSize(Math.round(windowWidth * scale),
                              Math.round(windowHeight * scale));
    }

    // ------------------------------------------------------------------
    // Key/modifier/button mapping tables
    // ------------------------------------------------------------------

    private static final Map<Key, Integer> KEY_MAP = new HashMap<>();

    static {
        // Letters/digits: GlyphKeys mirrors the ASCII wire codes for these.
        KEY_MAP.put(Key.A, GlyphKeys.A); KEY_MAP.put(Key.B, GlyphKeys.B);
        KEY_MAP.put(Key.C, GlyphKeys.C); KEY_MAP.put(Key.D, GlyphKeys.D);
        KEY_MAP.put(Key.E, GlyphKeys.E); KEY_MAP.put(Key.F, GlyphKeys.F);
        KEY_MAP.put(Key.G, GlyphKeys.G); KEY_MAP.put(Key.H, GlyphKeys.H);
        KEY_MAP.put(Key.I, GlyphKeys.I); KEY_MAP.put(Key.J, GlyphKeys.J);
        KEY_MAP.put(Key.K, GlyphKeys.K); KEY_MAP.put(Key.L, GlyphKeys.L);
        KEY_MAP.put(Key.M, GlyphKeys.M); KEY_MAP.put(Key.N, GlyphKeys.N);
        KEY_MAP.put(Key.O, GlyphKeys.O); KEY_MAP.put(Key.P, GlyphKeys.P);
        KEY_MAP.put(Key.Q, GlyphKeys.Q); KEY_MAP.put(Key.R, GlyphKeys.R);
        KEY_MAP.put(Key.S, GlyphKeys.S); KEY_MAP.put(Key.T, GlyphKeys.T);
        KEY_MAP.put(Key.U, GlyphKeys.U); KEY_MAP.put(Key.V, GlyphKeys.V);
        KEY_MAP.put(Key.W, GlyphKeys.W); KEY_MAP.put(Key.X, GlyphKeys.X);
        KEY_MAP.put(Key.Y, GlyphKeys.Y); KEY_MAP.put(Key.Z, GlyphKeys.Z);
        KEY_MAP.put(Key.DIGIT0, GlyphKeys.D0); KEY_MAP.put(Key.DIGIT1, GlyphKeys.D1);
        KEY_MAP.put(Key.DIGIT2, GlyphKeys.D2); KEY_MAP.put(Key.DIGIT3, GlyphKeys.D3);
        KEY_MAP.put(Key.DIGIT4, GlyphKeys.D4); KEY_MAP.put(Key.DIGIT5, GlyphKeys.D5);
        KEY_MAP.put(Key.DIGIT6, GlyphKeys.D6); KEY_MAP.put(Key.DIGIT7, GlyphKeys.D7);
        KEY_MAP.put(Key.DIGIT8, GlyphKeys.D8); KEY_MAP.put(Key.DIGIT9, GlyphKeys.D9);
        KEY_MAP.put(Key.ENTER, GlyphKeys.ENTER);
        KEY_MAP.put(Key.BACKSPACE, GlyphKeys.BACKSPACE);
        KEY_MAP.put(Key.TAB, GlyphKeys.TAB);
        KEY_MAP.put(Key.ESCAPE, GlyphKeys.ESCAPE);
        KEY_MAP.put(Key.SPACE, GlyphKeys.SPACE);
        KEY_MAP.put(Key.LEFT, GlyphKeys.LEFT);
        KEY_MAP.put(Key.RIGHT, GlyphKeys.RIGHT);
        KEY_MAP.put(Key.UP, GlyphKeys.UP);
        KEY_MAP.put(Key.DOWN, GlyphKeys.DOWN);
        KEY_MAP.put(Key.HOME, GlyphKeys.HOME);
        KEY_MAP.put(Key.END, GlyphKeys.END);
        KEY_MAP.put(Key.PAGE_UP, GlyphKeys.PAGE_UP);
        KEY_MAP.put(Key.PAGE_DOWN, GlyphKeys.PAGE_DOWN);
        KEY_MAP.put(Key.DELETE, GlyphKeys.DELETE);
        KEY_MAP.put(Key.INSERT, GlyphKeys.UP); // no INSERT in toolkit set; UP-compatible 265
        KEY_MAP.put(Key.MINUS, GlyphKeys.MINUS);
        KEY_MAP.put(Key.EQUALS, GlyphKeys.EQUAL);
        KEY_MAP.put(Key.SLASH, GlyphKeys.SLASH);
        KEY_MAP.put(Key.BACK_SLASH, GlyphKeys.BACKSLASH);
        KEY_MAP.put(Key.SEMICOLON, GlyphKeys.SEMICOLON);
        KEY_MAP.put(Key.QUOTE, GlyphKeys.APOSTROPHE);
        KEY_MAP.put(Key.COMMA, GlyphKeys.COMMA);
        KEY_MAP.put(Key.PERIOD, GlyphKeys.PERIOD);
        KEY_MAP.put(Key.BACK_QUOTE, GlyphKeys.GRAVE);
        KEY_MAP.put(Key.OPEN_BRACKET, GlyphKeys.LEFT_BRACKET);
        KEY_MAP.put(Key.CLOSE_BRACKET, GlyphKeys.RIGHT_BRACKET);
        KEY_MAP.put(Key.SHIFT, GlyphKeys.LEFT_SHIFT);
        KEY_MAP.put(Key.CONTROL, GlyphKeys.LEFT_CONTROL);
        KEY_MAP.put(Key.ALT, GlyphKeys.LEFT_ALT);
        KEY_MAP.put(Key.WIN_LOGO, GlyphKeys.LEFT_SUPER);
        KEY_MAP.put(Key.CAPS_LOCK, GlyphKeys.CAPS_LOCK);
        KEY_MAP.put(Key.F1, GlyphKeys.F1); KEY_MAP.put(Key.F2, GlyphKeys.F2);
        KEY_MAP.put(Key.F3, GlyphKeys.F3); KEY_MAP.put(Key.F4, GlyphKeys.F4);
        KEY_MAP.put(Key.F5, GlyphKeys.F5); KEY_MAP.put(Key.F6, GlyphKeys.F6);
        KEY_MAP.put(Key.F7, GlyphKeys.F7); KEY_MAP.put(Key.F8, GlyphKeys.F8);
        KEY_MAP.put(Key.F9, GlyphKeys.F9); KEY_MAP.put(Key.F10, GlyphKeys.F10);
        KEY_MAP.put(Key.F11, GlyphKeys.F11); KEY_MAP.put(Key.F12, GlyphKeys.F12);
    }

    /** Maps JWM modifier bits (KeyModifier._mask) onto GlyphMods bits.
     * @param nativeMods
     * @return  */
    @Override
    protected int mapNativeModifiers(int nativeMods) {
        int out = 0;
        if ((nativeMods & KeyModifier.SHIFT._mask) != 0)   out |= GlyphMods.SHIFT;
        if ((nativeMods & KeyModifier.CONTROL._mask) != 0) out |= GlyphMods.CTRL;
        if ((nativeMods & KeyModifier.ALT._mask) != 0)     out |= GlyphMods.ALT;
        if ((nativeMods & (KeyModifier.WIN_LOGO._mask
                | KeyModifier.MAC_COMMAND._mask
                | KeyModifier.LINUX_SUPER._mask)) != 0)    out |= GlyphMods.SUPER;
        if ((nativeMods & KeyModifier.CAPS_LOCK._mask) != 0) out |= GlyphMods.CAPS_LOCK;
        return out;
    }

    /** JWM MouseButton → toolkit ids (0=left, 1=right, 2=middle). */
    private static int buttonToToolkit(io.github.humbleui.jwm.MouseButton b) {
        return switch (b) {
            case PRIMARY -> 0;
            case SECONDARY -> 1;
            case MIDDLE -> 2;
            case BACK -> 3;
            case FORWARD -> 4;
            default -> 0;
        };
    }
}
