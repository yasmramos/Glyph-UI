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
 * {@code App.start(...)} must run on the main thread and dispatches every
 * window event from it. This backend therefore reports
 * {@link #isAppOwnedLoop()} true, and {@code Application.run()} enters
 * {@link #enterEventLoop(Runnable)}: the native loop calls back into the
 * toolkit once per dispatched event batch instead of the toolkit polling
 * from its own thread.</p>
 *
 * <p><strong>Rendering:</strong> raster-first. The application paints into
 * a CPU Skija surface; on every frame the backend blits it onto JWM's
 * native raster layer (created reflectively against
 * {@code io.github.humbleui.jwm.skija.LayerRasterSkija}, so this class
 * compiles without skija bindings on the compile classpath). GL-capable
 * builds can opt in with {@code -Dglyphui.jwm.gl=true}, which installs the
 * Skija GL layer instead (fbId 0 = default framebuffer bound by JWM via
 * {@code window.makeCurrent()}).</p>
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
    /** Set when create() succeeded or was skipped because JWM is absent. */
    private final AtomicBoolean initAttempted = new AtomicBoolean(false);

    /** Reflective LayerRasterSkija instance (null until create()). */
    private Object rasterLayer;
    /** io.github.humbleui.skija.Surface of the raster layer (reflective). */
    private Object layerSurface;
    /** Cached makeARGB ColorInfo for the layer surface (reflective). */
    private Object colorInfo;
    private boolean useGl;

    /** Cursor position in logical coords, tracked from mouse events. */
    private double cursorX, cursorY;

    public JwmWindowBackend(String title, int width, int height, WindowConfig config) {
        super(title, width, height, config);
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Override
    public boolean create() {
        if (!initAttempted.compareAndSet(false, true)) {
            return jwmWindow != null;
        }
        useGl = Boolean.getBoolean(USE_GL_PROPERTY);
        try {
            // App.start returns immediately on Windows/X11 (it only blocks
            // on macOS where it takes over the main thread). It MUST be
            // called from the main thread.
            App.start(() -> { /* no-op bootstrap; frames flow through the window listener */ });
            Window w = App.makeWindow();
            w.setEventListener(this::onJwmEvent);
            w.setTitle(title);
            w.setContentSize(windowWidth, windowHeight);
            if (!config.visible) {
                w.setVisible(false);
            }
            if (config.maximized) {
                w.maximize();
            }
            if (config.floating) {
                w.setZOrder(io.github.humbleui.jwm.ZOrder.FLOATING);
            }
            attachLayer(w);
            installTextInputClient(w);
            this.jwmWindow = w;
            // Seed geometry/scale from the first resize/screen events that
            // JWM dispatches during the next loop iteration; meanwhile use
            // the primary screen scale as a sane initial value.
            applyScreenScale(App.getPrimaryScreen());
            return true;
        } catch (Throwable t) {
            System.err.println("Glyph UI: JWM initialization failed (" + t.getMessage()
                + "). Running headless; window operations are no-ops.");
            t.printStackTrace();
            return false;
        }
    }

    /** Creates and attaches the presentation layer (raster or GL), reflectively. */
    private void attachLayer(Window w) {
        try {
            if (useGl) {
                Class<?> gl = Class.forName("io.github.humbleui.jwm.skija.LayerGLSkija");
                Object layer = gl.getDeclaredConstructor().newInstance();
                w.setLayer((io.github.humbleui.jwm.Layer) layer);
                return;
            }
            Class<?> rasterCls = Class.forName("io.github.humbleui.jwm.skija.LayerRasterSkija");
            Object layer = rasterCls.getDeclaredConstructor().newInstance();
            // ColorInfo colorInfo = ColorInfo.makeARGB(w,h,ColorType.RGBA_8888,AlphaType.UNPREMUL)
            Class<?> ciCls = Class.forName("io.github.humbleui.skija.ColorInfo");
            Class<?> ctCls = Class.forName("io.github.humbleui.skija.ColorType");
            Class<?> atCls = Class.forName("io.github.humbleui.skija.AlphaType");
            Object rgba8888 = ctCls.getField("RGBA_8888").get(null);
            Object unpremul = atCls.getField("UNPREMUL").get(null);
            colorInfo = ciCls.getMethod("makeARGB", int.class, int.class, ctCls, atCls)
                .invoke(null, framebufferWidth, framebufferHeight, rgba8888, unpremul);
            rasterCls.getMethod("setColorInfo", ciCls).invoke(layer, colorInfo);
            w.setLayer((io.github.humbleui.jwm.Layer) layer);
            this.rasterLayer = layer;
            this.layerSurface = rasterCls.getMethod("getSurface").invoke(layer);
        } catch (ClassNotFoundException e) {
            // No skija bindings on the classpath: fall back to plain JWM
            // raster layer (pixels exposed via getPixelsPtr for blitting).
            System.err.println("Glyph UI: skija jwm bindings missing; using raw raster layer.");
            w.setLayer(new io.github.humbleui.jwm.LayerRaster());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot create JWM presentation layer", e);
        }
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
        return hasLiveWindow();
    }

    @Override
    public void enterEventLoop(Runnable onFrame) {
        if (!hasLiveWindow()) {
            // Headless fallback: drive the frame callback inline.
            while (!shouldClose()) {
                onFrame.run();
            }
            return;
        }
        loopRunning.set(true);
        // Wake-up posted events execute the frame body on the UI thread.
        Runnable pump = () -> {
            if (shouldClose()) {
                loopRunning.set(false);
                App.runOnUIThread(() -> { /* exit marker */ });
                return;
            }
            onFrame.run();
        };
        while (loopRunning.get() && !shouldClose()) {
            App.runOnUIThread(pump);
            try {
                Thread.sleep(8); // ~120 Hz wake-up cadence; frames stay dirty-gated
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        loopRunning.set(false);
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
        return useGl && hasLiveWindow();
    }

    @Override
    public void present(SurfaceResult result) {
        Window w = jwmWindow;
        if (w == null) {
            return;
        }
        if (useGl) {
            // Skija GL draws straight into the default framebuffer (fbId 0)
            // bound by JWM's makeCurrent(); just ask for a new frame.
            w.requestFrame();
            return;
        }
        // Raster path: copy the app surface pixels into the layer surface.
        if (result == null || rasterLayer == null || layerSurface == null) {
            w.requestFrame();
            return;
        }
        try {
            io.github.humbleui.skija.Surface src =
                (io.github.humbleui.skija.Surface) result.surface();
            io.github.humbleui.skija.Surface dst =
                (io.github.humbleui.skija.Surface) layerSurface;
            int dw = dst.getWidth();
            int dh = dst.getHeight();
            io.github.humbleui.skija.Image img = src.makeImageSnapshot(
                io.github.humbleui.types.IRect.makeXYWH(0, 0,
                    Math.min(dw, src.getWidth()), Math.min(dh, src.getHeight())));
            dst.getCanvas().clear(0x00000000);
            // Plain drawImage blits 1:1 without resampling (nearest by construction).
            dst.getCanvas().drawImage(img, 0, 0);
            img.close();
            dst.flush();
        } catch (Throwable t) {
            // Surface recreated concurrently with resize: skip this frame.
        }
        w.requestFrame();
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

    private void onJwmEvent(io.github.humbleui.jwm.Event event) {
        Window w = jwmWindow;
        if (event instanceof EventWindowResize) {
            EventWindowResize e = (EventWindowResize) event;
            // Logical size = content area (window rect includes decorations)
            notifyWindowSize(e.getContentWidth(), e.getContentHeight());
            Screen s = w != null ? w.getScreen() : null;
            float scale = s != null && s._scale > 0f ? s._scale : 1f;
            notifyFramebufferSize(Math.round(e.getContentWidth() * scale),
                                  Math.round(e.getContentHeight() * scale));
            resizeLayer();
        } else if (event instanceof EventWindowScreenChange) {
            applyScreenScale(w != null ? w.getScreen() : null);
        } else if (event instanceof EventWindowMove) {
            EventWindowMove e = (EventWindowMove) event;
            posX = e.getWindowLeft();
            posY = e.getWindowTop();
        } else if (event instanceof EventWindowCloseRequest) {
            setShouldClose(true);
        } else if (event instanceof EventWindowClose) {
            shouldClose = true;
            loopRunning.set(false);
        } else if (event instanceof EventKey) {
            EventKey e = (EventKey) event;
            Integer code = KEY_MAP.get(e._key);
            if (code != null) {
                notifyKey(code, e._isPressed, mapNativeModifiers(e._modifiers));
            }
        } else if (event instanceof EventTextInput) {
            EventTextInput e = (EventTextInput) event;
            notifyTextInput(e._text, e._replacementStart, e._replacementEnd);
        } else if (event instanceof EventTextInputMarked) {
            // Pre-edit (composing) text: not surfaced through the neutral
            // API yet; ignore so the committed EventTextInput drives input.
        } else if (event instanceof EventMouseButton) {
            EventMouseButton e = (EventMouseButton) event;
            cursorX = e._x;
            cursorY = e._y;
            notifyMouseButton(buttonToToolkit(e._button), e._isPressed,
                e._x, e._y, mapNativeModifiers(e._modifiers));
        } else if (event instanceof EventMouseMove) {
            EventMouseMove e = (EventMouseMove) event;
            cursorX = e._x;
            cursorY = e._y;
            notifyCursorPos(e._x, e._y);
        } else if (event instanceof EventMouseScroll) {
            EventMouseScroll e = (EventMouseScroll) event;
            // Map wheel to toolkit buttons 3 (up) / 4 (down), like GLFW.
            int btn = e._deltaY >= 0 ? 3 : 4;
            notifyMouseButton(btn, true, e._x, e._y, mapNativeModifiers(e._modifiers));
            notifyMouseButton(btn, false, e._x, e._y, mapNativeModifiers(e._modifiers));
        }
    }

    /** Re-sizes the raster/GL layer to the current physical size. */
    private void resizeLayer() {
        Window w = jwmWindow;
        if (w == null) {
            return;
        }
        try {
            io.github.humbleui.jwm.Layer layer = w.getLayer();
            if (layer != null) {
                layer.resize(framebufferWidth, framebufferHeight);
                if (rasterLayer != null) {
                    Class<?> ciCls = Class.forName("io.github.humbleui.skija.ColorInfo");
                    colorInfo = ciCls.getMethod("makeARGB", int.class, int.class,
                            Class.forName("io.github.humbleui.skija.ColorType"),
                            Class.forName("io.github.humbleui.skija.AlphaType"))
                        .invoke(null, framebufferWidth, framebufferHeight,
                            Class.forName("io.github.humbleui.skija.ColorType")
                                .getField("RGBA_8888").get(null),
                            Class.forName("io.github.humbleui.skija.AlphaType")
                                .getField("UNPREMUL").get(null));
                    rasterLayer.getClass().getMethod("setColorInfo", ciCls)
                        .invoke(rasterLayer, colorInfo);
                    layerSurface = rasterLayer.getClass().getMethod("getSurface").invoke(rasterLayer);
                }
            }
        } catch (Throwable ignored) {
            // layer may be mid-recreation; next frame retries
        }
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

    /** Maps JWM modifier bits (KeyModifier._mask) onto GlyphMods bits. */
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
        switch (b) {
            case PRIMARY:   return 0;
            case SECONDARY: return 1;
            case MIDDLE:    return 2;
            case BACK:      return 3;
            case FORWARD:   return 4;
            default:        return 0;
        }
    }
}
