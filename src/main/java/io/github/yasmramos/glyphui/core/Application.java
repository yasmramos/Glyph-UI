package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.GlyphMods;
import io.github.yasmramos.glyphui.core.backend.GlSurfaceFactory;
import io.github.yasmramos.glyphui.core.backend.RasterSurfaceFactory;
import io.github.yasmramos.glyphui.core.backend.SurfaceFactory;
import io.github.yasmramos.glyphui.core.backend.SurfaceResult;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ComponentState;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.PopupLayer;
import io.github.yasmramos.glyphui.ui.TextField;
import io.github.humbleui.skija.*;
import io.github.yasmramos.glyphui.graphics.Fonts;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.style.StyleEngine;
import io.github.yasmramos.glyphui.style.StyleSheet;
import java.lang.System.Logger;

import java.util.EnumSet;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Main application class that manages the event loop, rendering, and window
 * lifecycle.
 *
 * <p>{@code Application} owns every native resource created during
 * {@link #init()} (Skija surface, optional GPU context and JWM window), so
 * it implements {@link AutoCloseable} and is intended to be used with
 * try-with-resources:</p>
 *
 * <pre>{@code
 * try (Application app = new Application()) {
 *     if (app.init("Glyph UI", 800, 600)) {
 *         app.getRootPanel().add(new Button("Click here!"));
 *         app.run();
 *     }
 * }
 * }</pre>
 *
 * <p>{@link #close()} releases resources in dependency order: the component
 * tree first (widgets close their paints/fonts), then the shared font cache,
 * then the Skija surface, then the DirectContext that created it, and finally
 * the JWM window.</p>
 */
public class Application implements AutoCloseable {

    private static final Logger log = System.getLogger(Application.class.getName());

    private Window window;
    /**
     * When true, releasing the ESCAPE key closes the window. Off by default:
     * a toolkit-level "ESC quits the app" behaviour would be copied into
     * every widget built on top of Application and would hijack the key from
     * focused text fields. Opt in via {@link #setCloseOnEscape(boolean)}.
     */
    private boolean closeOnEscape;
    private Surface surface;
    /**
     * Latest {@link SurfaceResult} produced by {@link #surfaceFactory}. Kept so
     * {@link #render()} can hand it to {@link Window#present(SurfaceResult)} on
     * raster-only backends (JWM), which blit the CPU surface onto their native
     * layer instead of swapping GL buffers.
     */
    private SurfaceResult surfaceResult;
    private Canvas canvas;
    private final Panel rootPanel;

    /** Top-most layer hosting popups (menus); rendered and hit last. */
    private final PopupLayer popupLayer;
    /**
     * The GPU context of the active backend, or null for raster. Owned by
     * {@link #surfaceFactory} once created (the factory hands over closing
     * responsibility to {@link #close()} through this field). Kept here only as
     * a cached handle for per-frame flushes.
     */
    private DirectContext directContext;
    /**
     * Backend strategy that creates/recreates the Skija surface. Selected in
     * {@link #init(String, int, int, boolean)} from the raster flag and swapped
     * to {@link RasterSurfaceFactory} on GPU-init failure. All GL /
     * render-target ownership logic lives in the factory implementations.
     */
    private SurfaceFactory surfaceFactory;
    private boolean running;
    private double lastFrameTime;
    private boolean useRasterSurface;

    /**
     * When true, the GPU backend is skipped and a raster surface is used
     * directly.
     */
    private boolean forceRasterSurface;

    /**
     * Optional CSS style engine; when set, it re-cascades only on frames
     * where something affecting selector matching changed (see the
     * style-dirty latch in {@code Component}), not on every painted frame.
     */
    private StyleEngine styleEngine;

    /**
     * Per-application "needs cascade" flag. Seeded from the global
     * style-dirty latch in {@code frameStep()}; also forced true while an
     * animation is running so state changes that bypass the framework
     * setters (direct field writes in custom widgets) still converge.
     */
    private boolean styleDirty = true;

    /**
     * Test hook: consumes the process-wide style-dirty latch and returns
     * whether a cascade pass was pending. Used by tests to verify that
     * style-affecting mutators mark the latch without running a full frame.
     *
     * @return true when something invalidated selector matching since the
     *         last consumption (the latch is cleared by this call)
     */
    public static boolean consumePendingStyleInvalidationForTests() {
        return Component.consumeStyleDirty();
    }

    /**
     * On-demand rendering flag: true when something changed (events, component
     * mutations, resize) and the next loop iteration should repaint.
     */
    private volatile boolean paintDirty;

    /**
     * Optional animation callback. While it returns true, the event loop keeps
     * repainting every frame (continuous animations). May be null.
     */
    private Runnable animationCallback;

    /**
     * Sleep interval for the raster backend idle wait (no hardware vsync
     * there).
     */
    private static final long RASTER_IDLE_SLEEP_MS = 16;

    /**
     * The UI thread: the thread that called {@link #run()}. All widget state
     * changes, layout and rendering happen on this thread. Null before the loop
     * starts.
     */
    private volatile Thread uiThread;

    /**
     * Queue of tasks posted from other threads via
     * {@link #invokeLater(Runnable)}. Consumed by the UI thread on every
     * event-loop iteration.
     */
    private final ConcurrentLinkedQueue<Runnable> uiTaskQueue = new ConcurrentLinkedQueue<>();

    // Mouse state — the last cursor position reported by the backend. Only
    // used as a fallback for listeners that do not carry coordinates (the
    // mouse-button callback always uses its own x/y parameters; see
    // setupCallbacks()).
    private double mouseX;
    private double mouseY;
    private final boolean[] mouseButtons = new boolean[10];

    /**
     * The component currently under the pointer (deepest hit). Tracking it lets
     * the application clear the hover state of the previous component when the
     * pointer moves onto a different one: routing only delivers a move to the
     * component under the cursor, so a widget that is left behind would
     * otherwise stay stuck in {@link ComponentState#HOVER}.
     */
    private Component hovered;

    /**
     * Owns Tab / Shift+Tab focus traversal and the currently focused widget.
     */
    private FocusManager focusManager;

    /**
     * Cached HiDPI content scale factors (logical -> physical pixels).
     */
    private float contentScaleX = 1.0f;
    private float contentScaleY = 1.0f;

    /**
     * True while this application owns the single static repaint hook
     * ({@code Component.setGlobalRepaintRequester}, installed by
     * {@link #init()}). Tracked explicitly because method references have
     * unstable identity and cannot be compared with {@code ==}. Cleared by
     * {@link #close()} so a closed instance never lingers through globals,
     * and never cleared over a foreign requester installed after our init
     * (e.g. by a unit test) — the old second hook
     * ({@code Component.setRepaintRequester}) had no such flag at all and
     * was nulled out unconditionally by every close(); it has been removed
     * so one flag now protects the only remaining hook.
     */
    private boolean globalHookOwnedByThis;

    /**
     * The most recently started application instance, used by widgets to obtain
     * the UI-thread marshalling target lazily (properties are created on first
     * access, which may happen before {@code init()} completes). Cleared when
     * the application closes. package-private for tests.
     */
    static volatile Application current;

    /**
     * Returns the currently running application instance, or {@code null} in
     * headless/test contexts where no application has been started. Widget
     * properties use this to marshal cross-thread sets automatically.
     *
     * <p><b>Design note (Block 4 item 4.1):</b> "most recently started" means
     * this static pointer models one app per process; it is a fallback for
     * code without an explicit context reference. Multi-app processes and
     * embeddings should pass their {@code Application} instance down the tree
     * (an AppContext propagated by ownership) instead of relying on this
     * getter, which always points at the last-started app.</p>
     *
     * @return the current application, or null
     */
    public static Application getCurrent() {
        return current;
    }

    /**
     * Creates a new Application.
     */
    public Application() {
        this.running = false;
        this.forceRasterSurface = false;
        this.paintDirty = true; // first frame must always be painted
        this.rootPanel = new Panel(0, 0, 800, 600);
        this.popupLayer = new PopupLayer();
        // NOTE: the repaint hook (Component.setGlobalRepaintRequester) is
        // deliberately NOT installed here. A constructor that grabs
        // process-wide static state would leak the hook when the instance is
        // never initialized (e.g. dropped in tests). init() installs it
        // instead, and close() clears it again.
    }

    /**
     * Enables or disables closing the window when the ESCAPE key is
     * released. <b>Disabled by default</b>: ESC is a regular application
     * keystroke (dismissing dialogs, cancelling text composition) and an
     * implicit "ESC quits" wired into the core event loop would be inherited
     * by every widget built on top of {@code Application}. Call
     * {@code setCloseOnEscape(true)} before {@link #init()} to opt in to the
     * legacy behaviour.
     *
     * @param closeOnEscape true to close the window on ESCAPE release
     */
    public void setCloseOnEscape(boolean closeOnEscape) {
        this.closeOnEscape = closeOnEscape;
    }

    /**
     * @return true when releasing ESCAPE closes the window (default false)
     */
    public boolean isCloseOnEscape() {
        return closeOnEscape;
    }

    /**
     * Marks the display dirty so the next loop iteration renders a new frame.
     * Safe to call from other threads (e.g. from {@code invokeLater} tasks).
     */
    public void requestRepaint() {
        synchronized (this) {
            paintDirty = true;
            // Wake up the loop if it is blocked waiting for events
            if (window != null && running) {
                window.postEmptyEvent();
            }
        }
    }

    /**
     * Returns the current paint-dirty state (mainly for tests).
     *
     * @return true when a repaint has been requested but not yet performed
     */
    boolean isPaintDirty() {
        return paintDirty;
    }

    /**
     * Installs a repaint requester as the single global hook so that any
     * component {@code invalidate()} reaching the root of the tree marks
     * this application paint-dirty. {@link Component#invalidate()}
     * propagates up the parent chain until it reaches the top-level panel,
     * which then invokes this requester (see {@link #requestRepaint()}).
     * Widget mutators call {@code Component.requestRepaint()}, which is now
     * an alias for {@code invalidate()} — there is exactly one repaint hook,
     * so re-pointing it here rewires every path at once.
     *
     * <p>{@link #init()} installs this application as the global requester;
     * this setter allows re-pointing the hook (e.g. when nesting applications
     * in tests or restoring a previous requester). Re-pointing through this
     * method transfers ownership of the global hook: {@link #close()} will
     * only clear it while this instance still owns it, so a requester
     * installed by someone else is never nulled out behind their back.</p>
     *
     * @param requester the repaint requester (may be null to clear)
     */
    public void setRepaintRequester(RepaintRequester requester) {
        Component.setGlobalRepaintRequester(requester);
        // Routing through the setter means this instance's owner claims (or,
        // for null, releases) the static hook — keep the ownership flag in
        // sync so close() can restore it symmetrically.
        globalHookOwnedByThis = (requester != null);
    }

    /**
     * Initializes the application with default window settings.
     *
     * @return true if initialization was successful
     */
    public boolean init() {
        return init("Glyph UI", 800, 600);
    }

    /**
     * Initializes the application.
     *
     * @param title the window title
     * @param width the window width
     * @param height the window height
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height) {
        return init(title, width, height, null, false);
    }

    /**
     * Initializes the application with an explicit window configuration — for
     * example a {@code decorated(false)} window that supplies its own custom
     * title bar instead of the native one.
     *
     * @param title the window title
     * @param width the window width
     * @param height the window height
     * @param config the window configuration (null uses defaults)
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height, WindowConfig config) {
        return init(title, width, height, config, false);
    }

    /**
     * Initializes the application.
     *
     * <p><strong>Behaviour notes (read before wiring an app):</strong></p>
     * <ul>
     *   <li><b>ESCAPE does not close the window.</b> Closing on ESC is an
     *       explicit opt-in via {@link #setCloseOnEscape(boolean)} (default
     *       false), so focused widgets keep the key for their own use.</li>
     *   <li>{@code init()} installs this instance as the owner of both static
     *       repaint hooks in {@code Component}; ownership is tracked with the
     *       twin flags and released symmetrically by {@link #close()}.
     *       Re-point them through {@link #setRepaintRequester} only.</li>
     *   <li>A successful {@code init()} publishes this instance as
     *       {@link #getCurrent()} immediately, so lazily created widget
     *       properties can marshal to it even before {@link #run()} starts;
     *       a failed init leaves no trace (see the cleanup below).</li>
     * </ul>
     *
     * @param title  the window title
     * @param width  the window width
     * @param height the window height
     * @param useRasterSurface if true, forces the raster backend and skips the
     * GPU initialization attempt entirely (for testing)
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height, boolean useRasterSurface) {
        return init(title, width, height, null, useRasterSurface);
    }

    /**
     * Full initialization entry point: explicit window configuration plus
     * surface selection.
     *
     * @param title the window title
     * @param width the window width
     * @param height the window height
     * @param config the window configuration (null uses defaults)
     * @param useRasterSurface if true, forces the raster backend and skips the
     * GPU initialization attempt entirely (for testing)
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height, WindowConfig config,
            boolean useRasterSurface) {
        // Idempotency guard: a second init() would leak the previous window,
        // surface and DirectContext (two GL contexts, orphaned native handles).
        if (running || window != null) {
            throw new IllegalStateException("Application already initialized; "
                    + "call close() before re-initializing");
        }
        try {
            this.forceRasterSurface = useRasterSurface;

            // Create window
            window = new Window(title, width, height, config);
            if (!window.create()) {
                // Do not leave a half-initialized application behind: release
                // the (failed) window so a later close()/re-init is consistent.
                window.destroy();
                window = null;
                return false;
            }

            // Initialize Skija surface: try GPU first, automatically degrade to
            // raster when the GPU backend is unavailable (headless / no driver).
            // All native-resource creation and ownership now lives in the
            // SurfaceFactory implementations; Application only adopts the
            // returned handles.
            //
            // Backends that report no GL support skip the attempt entirely:
            // under JWM the native window does not exist yet (its creation is
            // deferred to the event loop), so no GL context is current and
            // DirectContext.makeGL() would fail against a null context.
            final boolean tryGpu = !useRasterSurface
                && window.isGlCapable();
            if (!tryGpu) {
                this.surfaceFactory = new RasterSurfaceFactory();
                initSurfaceFromFactory();
            } else {
                try {
                    this.surfaceFactory = new GlSurfaceFactory();
                    initSurfaceFromFactory();
                } catch (Throwable gpuFailure) {
                    log.log(Logger.Level.ERROR, () -> "GPU backend unavailable ("
                            + gpuFailure.getMessage() + "). Falling back to raster surface.");
                    // The failed GL factory already released its DirectContext
                    // on every internal failure path; belt and braces here so
                    // the raster fallback never leaves an orphaned context.
                    closeDirectContextAndNull();
                    this.forceRasterSurface = true;
                    this.surfaceFactory = new RasterSurfaceFactory();
                    initSurfaceFromFactory();
                }
            }

            // Route repaint requests from components to this application.
            // There is a single static hook in Component (invalidate() at the
            // tree root invokes it); point it at this application — close()
            // clears it again, but only if this instance still owns it
            // (method references have unstable identity, hence the flag).
            Component.setGlobalRepaintRequester(this::requestRepaint);
            globalHookOwnedByThis = true;

            // Setup callbacks
            setupCallbacks();

            // Set initial root panel size to the LOGICAL window size. The
            // default layout is FlowLayout, which only positions children at
            // their preferred sizes; stretch the panel itself so CSS width /
            // height:100% and flex containers have a real viewport to fill.
            rootPanel.setLayoutManager(new FlexLayout(FlexLayout.Direction.ROW));
            rootPanel.setWidth(window.getWidth());
            rootPanel.setHeight(window.getHeight());
            // The popup layer spans the window so popups can be placed at
            // any window coordinate and hit-tested against the full surface.
            popupLayer.setWidth(window.getWidth());
            popupLayer.setHeight(window.getHeight());

            lastFrameTime = nowSeconds();
            running = true;
            paintDirty = true; // always paint the first frame

            // Cache the HiDPI content scale factors; render() applies them
            // per frame and the callback below keeps them up to date.
            this.contentScaleX = window.getContentScaleX();
            this.contentScaleY = window.getContentScaleY();

            // Publish the marshalling target at the END of a SUCCESSFUL
            // init(), not in run(): widgets created between init() and
            // run() (the normal bootstrap order) capture Application.current
            // lazily through their properties, and until now they saw null,
            // silently skipping cross-thread marshalling. close() clears it
            // again; the failure path below must not leave a stale pointer.
            current = this;

            return true;
        } catch (Exception e) {
            log.log(Logger.Level.ERROR, "Failed to initialize application: " + e.getMessage(), e);
            // A failed init() must leave no global trace behind: drop the
            // current-instance pointer (in case a re-init overwrote another
            // app's) and release both repaint hooks if we already claimed
            // them before throwing. close() performs the full native cleanup.
            if (current == this) {
                current = null;
            }
            releaseRepaintHooks();
            return false;
        }
    }

    /**
     * Creates the initial Skija surface through the active
     * {@link SurfaceFactory} (GPU or raster) and wires the canvas wrapper to
     * it. The factory owns all backend-specific native handles; this method
     * only adopts the returned {@link SurfaceResult}.
     *
     * <p>
     * The render target/surface is created with the <b>physical</b>
     * framebuffer dimensions; the {@link Canvas} wrapper (and therefore the
     * component tree) uses the <b>logical</b> window dimensions.</p>
     *
     * @throws RuntimeException if the factory fails; by contract the factory
     * has already released any partially created native resources, so
     * {@link #init()} can safely fall back to raster
     */
    private void initSurfaceFromFactory() {
        SurfaceResult result = surfaceFactory.create(window);

        this.surface = result.surface();
        this.directContext = result.directContext();
        this.surfaceResult = result;

        // Create canvas wrapper in LOGICAL coordinates; the content-scale
        // transform is applied per-frame in render()
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas = new Canvas(skijaCanvas, surface,
                result.logicalWidth(), result.logicalHeight(), this);
    }

    /**
     * Closes and clears {@link #directContext} if set. Used by the GPU-init
     * failure fallback in {@link #init()} as a belt-and-braces release for
     * contexts that predate the current factory split (or were installed via
     * reflection in tests); the active {@link SurfaceFactory} performs the
     * authoritative release on its own failure paths. Safe to call multiple
     * times.
     */
    private void closeDirectContextAndNull() {
        if (directContext != null) {
            try {
                directContext.close();
            } catch (RuntimeException ignored) {
                // Best-effort release during an already-failing init path.
            }
            directContext = null;
        }
    }

    /**
     * Initializes the Skija surface with raster backend (for testing).
     * Delegates to {@link RasterSurfaceFactory} and rebinds the canvas wrapper,
     * exactly like the GPU init path.
     */
    private void initRasterSurface() {
        this.surfaceFactory = new RasterSurfaceFactory();
        initSurfaceFromFactory();
    }

    /**
     * Releases the static repaint hook in {@code Component} if and only if
     * this instance still owns it (ownership flag — method references have
     * unstable identity, so the installed requester cannot be compared with
     * {@code ==}). Shared by {@link #close()} and the failure path of
     * {@link #init(String, int, int, boolean)} so a half-initialized
     * application never lingers through process-wide globals.
     */
    private void releaseRepaintHooks() {
        if (globalHookOwnedByThis) {
            Component.setGlobalRepaintRequester(null);
            globalHookOwnedByThis = false;
        }
    }

    /**
     * Recreates a CPU-backed raster surface at the given size and rebinds it
     * to the existing canvas wrapper. Public shim kept for tests that
     * exercise the raster resize path without a GL context (and without a
     * fully initialized application).
     *
     * <p>The <b>active</b> {@link #surfaceFactory} performs the allocation
     * when one exists: creating a throw-away {@link RasterSurfaceFactory}
     * instead would bypass the GPU factory's ownership rules and could hand
     * back a raster surface while a live {@code DirectContext} still owned
     * the previous render target. Only uninitialized instances (unit-test
     * shims) fall back to a fresh raster factory.</p>
     *
     * @param width the new width
     * @param height the new height
     */
    public void recreateRasterSurface(int width, int height) {
        checkThread();
        if (canvas == null) {
            throw new IllegalStateException("Canvas must be initialized before recreating a raster surface");
        }

        if (surface != null) {
            surface.close();
            surface = null;
        }

        // Use the ACTIVE factory whenever one is installed — same code path
        // as the framebuffer-resize listener and initRasterSurface(), so no
        // allocation route diverges from the backend's ownership rules. The
        // window may be absent in unit tests (logical size equals the
        // physical one there), hence the conditional factory fallback.
        SurfaceResult result;
        if (surfaceFactory != null) {
            result = surfaceFactory.recreate(window, width, height);
        } else {
            result = new RasterSurfaceFactory().recreate(null, width, height);
        }
        surface = result.surface();
        this.directContext = result.directContext();
        this.surfaceResult = result;

        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        // Always rebind canvas AND surface together: flush() goes through the
        // wrapper's surface reference, so a stale surface would flush into a
        // closed native object.
        canvas.bindNativeSurface(skijaCanvas, surface);
        canvas.resize(width, height);

        requestRepaint();
    }

    /**
     * Recreates the CPU-backed raster surface using the window's current
     * framebuffer size. Used by tests to simulate HiDPI framebuffer changes
     * without a GL context.
     */
    void recreateRasterSurfaceForTesting() {
        checkThread();
        if (window == null) {
            throw new IllegalStateException("Window must be initialized first");
        }
        recreateRasterSurface(window.getFramebufferWidth(), window.getFramebufferHeight());
    }

    /**
     * Sets up the backend event listeners for input and HiDPI changes.
     *
     * <p>Coordinate policy: mouse/cursor coordinates reported by the backend
     * are in the logical window space and are propagated unchanged to the
     * component tree (which is also in logical space thanks to the
     * content-scale canvas transform). No manual fb/window ratio conversion
     * is needed.</p>
     */
    private void setupCallbacks() {
        // The backend owns the native event plumbing; every subscription here
        // goes through Window's neutral listener API (see WindowBackend).
        final Application app = this;
        // Framebuffer resize: the active backend caches the physical size and
        // keeps the logical dimensions in sync before firing this listener,
        // so by the time it runs Window's getters already report the new
        // values. Subscribe through the neutral listener API (never a raw
        // native callback) so the toolkit works with any backend.
        window.setFramebufferSizeListener((fbWidth, fbHeight) -> {
            recreateSurface(fbWidth, fbHeight);
            requestRepaint();
        });

        // Content scale (DPI) change — e.g. window moved across monitors:
        // only the per-frame scale factor changes; render() reads it live,
        // so we just need a repaint.
        window.setContentScaleListener((xscale, yscale) -> {
            this.contentScaleX = xscale;
            this.contentScaleY = yscale;
            requestRepaint();
        });

        // Mouse button listener — coordinates arrive in LOGICAL space
        // (the backend translates its native identifiers onto the toolkit
        // vocabulary: 0 = left, 1 = right, 2 = middle).
        window.setMouseListener((button, pressed, x, y, mods) -> {
            MouseButton glyphButton = convertMouseButton(button);
            MouseEventType type = pressed ? MouseEventType.PRESS : MouseEventType.RELEASE;

            // Track the held-button state so the cursor-move listener below can
            // tell a plain hover (MOVE) from a drag (DRAG).
            if (button >= 0 && button < mouseButtons.length) {
                mouseButtons[button] = pressed;
            }

            // Dispatch at the coordinates reported BY THIS EVENT. Using the
            // cached mouseX/mouseY here was a real bug: backends deliver the
            // button event with its own position, and the cache may still
            // hold the previous cursor-move location (or (0,0) when no move
            // arrived yet), so clicks landed on the wrong widget.
            MouseEvent event = new MouseEvent(type, (int) x, (int) y, glyphButton, 1);
            // Popups (menus) sit above everything: they see the event first
            // and swallow it when it lands on popup chrome. What they do not
            // consume falls through to the page; a press outside every popup
            // is what dismisses an open menu (see PopupLayer).
            if (!popupLayer.onMouseEvent(event)) {
                rootPanel.onMouseEvent(event);
            }
            requestRepaint();
        });

        // Cursor position listener — backends report LOGICAL coordinates,
        // which match the logical-space component tree directly (HiDPI-safe).
        // The event type distinguishes a plain hover (MOVE) from a drag (DRAG,
        // any button held) so widgets can extend a selection while the button
        // stays down; this is what makes mouse selection in a TextField work.
        window.setCursorPosListener((xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;

            boolean anyButtonHeld = false;
            for (boolean down : mouseButtons) {
                if (down) {
                    anyButtonHeld = true;
                    break;
                }
            }
            MouseEventType type = anyButtonHeld ? MouseEventType.DRAG : MouseEventType.MOVE;
            MouseEvent event = new MouseEvent(type, (int) mouseX, (int) mouseY, MouseButton.LEFT, 0);
            if (!popupLayer.onMouseEvent(event)) {
                rootPanel.onMouseEvent(event);
            }
            if (type == MouseEventType.MOVE) {
                updateHover();
            }
            requestRepaint();
        });

        // Focus manager: owns Tab / Shift+Tab traversal and the focused widget
        focusManager = new FocusManager(this::requestRepaint);
        focusManager.setRoot(rootPanel);
        FocusManager.setGlobalFocusManager(focusManager);

        // Clipboard bridge for TextField (copy/paste through the backend-
        // agnostic clipboard API of Window).
        TextField.setClipboardHooks(
                () -> {
                    Component focused = focusManager.getFocused();
                    if (focused instanceof TextField textField) {
                        String sel = textField.getSelectedTextOrNull();
                        if (sel != null) {
                            window.setClipboardString(sel);
                        }
                    }
                },
                () -> {
                    String clip = window.getClipboardString();
                    if (clip != null && !clip.isEmpty()) {
                        Component focused = focusManager.getFocused();
                        if (focused instanceof TextField textField) {
                            textField.insertText(clip);
                        }
                    }
                });

        // Key listener -- Tab / Shift+Tab are intercepted by the FocusManager;
        // every other key is dispatched down the tree (widgets only react
        // when they hold the keyboard focus). Key codes use the toolkit's own
        // GlyphKeys vocabulary mapped by each backend.
        window.setKeyListener((key, pressed, mods) -> {
            // ESC dismisses an open popup menu first; only when no popup is
            // open does the close-window opt-in below get a chance.
            if (key == GlyphKeys.ESCAPE && pressed && popupLayer.closeOnEscape()) {
                requestRepaint();
                return;
            }

            // ESC closes the window only as an explicit opt-in (see
            // setCloseOnEscape): by default it stays a normal keystroke for
            // focused widgets.
            if (closeOnEscape && !pressed && key == GlyphKeys.ESCAPE) {
                window.setShouldClose(true);
                return;
            }

            if (key == GlyphKeys.TAB && pressed) {
                boolean shift = (mods & GlyphMods.SHIFT) != 0;
                if (shift) {
                    focusManager.focusPrevious();
                } else {
                    focusManager.focusNext();
                }
                return; // Tab never reaches individual widgets
            }

            KeyEventType type = pressed ? KeyEventType.PRESS : KeyEventType.RELEASE;
            EnumSet<KeyModifier> modifiers = getModifiers(mods);

            char keyChar = pressed ? mapKeyToChar(key) : 0;
            KeyEvent event = new KeyEvent(type, key, keyChar, modifiers);
            rootPanel.onKeyEvent(event);
            requestRepaint();
        });

        // Char listener -- printable text input goes straight to the focused
        // TextField. On the JWM backend the TextInputListener below takes
        // precedence for composed input and the default forwarding (see
        // AbstractWindowBackend#notifyTextInput) keeps plain characters
        // flowing here as well.
        window.setCharListener(codepoint -> {
            Component focused = focusManager.getFocused();
            if (focused instanceof TextField textField) {
                textField.onCharTyped((char) (int) codepoint);
            }
        });

        // IME text-input listener -- committed/composed strings from the
        // platform input method (JWM's EventTextInput). The replacement
        // range marks the currently composing region inside the focused
        // TextField, so candidate-window commits replace the marked text.
        window.setTextInputListener((text, replacementStart, replacementEnd) -> {
            Component focused = focusManager.getFocused();
            if (focused instanceof TextField tf) {
                // applyImeText reads the (-1,-1) Windows sentinel as "at the
                // caret" and honours explicit ranges (clamped to the buffer),
                // so committed text lands after the previous input instead
                // of at position 0.
                tf.applyImeText(text, replacementStart, replacementEnd);
            }
            requestRepaint();
        });

        // IME caret geometry: the backend asks this client where the caret
        // is (in physical screen pixels) so the OS positions its candidate
        // window correctly. Backends without IME support ignore it.
        window.setImeClient(new ImeBridge(app, window));
        window.setTextInputEnabled(true);
    }

    /**
     * Maps the most common special keys to control characters so widgets can
     * recognize them through {@link KeyEvent#getKeyChar()} without depending
     * on native key constants. Printable characters arrive through the char
     * callback instead; unmapped keys produce NUL.
     *
     * @param key the toolkit key code (see GlyphKeys)
     * @return the mapped character, or 0 when there is no mapping
     */
    /**
     * Backend-neutral monotonic clock in seconds.
     *
     * @return elapsed seconds from an arbitrary fixed origin
     */
    private static double nowSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }

    private static char mapKeyToChar(int key) {
        return switch (key) {
            case GlyphKeys.ENTER ->
                '\r';
            case GlyphKeys.TAB ->
                '\t';
            case GlyphKeys.BACKSPACE ->
                8;
            case GlyphKeys.ESCAPE ->
                27;
            default ->
                0;
        };
    }

    /**
     * Recreates the Skija surface after window resize. Delegates entirely to
     * the active {@link SurfaceFactory}, which encapsulates the backend's
     * native-resource ownership (raster reallocates a CPU buffer; GPU wraps the
     * window framebuffer in a fresh render target — see
     * {@link GlSurfaceFactory} for the issue #24 ownership rule). This method
     * only rebinds the canvas wrapper and updates the logical sizes.
     *
     * @param fbWidth the new physical framebuffer width
     * @param fbHeight the new physical framebuffer height
     */
    private void recreateSurface(int fbWidth, int fbHeight) {
        // A minimized window reports a 0x0 framebuffer (JWM does).
        // Skija cannot build an empty raster surface — Surface.makeRaster(...)
        // throws IllegalArgumentException — so keep the previous surface and
        // skip this resize: render() is suppressed while the framebuffer is
        // degenerate (see frameStep()) and the next real resize recreates it.
        // Closing first (the old order) would also leave `surface` null when
        // the allocation threw, turning a later paint into an NPE.
        if (fbWidth <= 0 || fbHeight <= 0) {
            return;
        }

        if (surface != null) {
            surface.close();
            surface = null;
        }

        // Ensure a factory exists even on partially initialized instances
        // (e.g. resize callbacks racing a failed init); the backend choice
        // mirrors isGpuBackend()/the raster fallback flag.
        if (surfaceFactory == null) {
            surfaceFactory = isGpuBackend()
                    ? new GlSurfaceFactory() : new RasterSurfaceFactory();
        }

        SurfaceResult result = surfaceFactory.recreate(window, fbWidth, fbHeight);
        this.surface = result.surface();
        this.directContext = result.directContext();
        this.surfaceResult = result;

        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        // Rebind canvas AND surface together — Canvas.flush() delegates to
        // surface.flushAndSubmit(), so a stale surface reference would
        // flush into the closed (previous) surface.
        canvas.bindNativeSurface(skijaCanvas, surface);
        canvas.resize(result.logicalWidth(), result.logicalHeight());

        // Update root panel size (logical coordinates)
        rootPanel.setWidth(result.logicalWidth());
        rootPanel.setHeight(result.logicalHeight());
        popupLayer.setWidth(result.logicalWidth());
        popupLayer.setHeight(result.logicalHeight());

        requestRepaint();
    }

    /**
     * Recomputes the hover target after a plain pointer move (no button held).
     *
     * <p>The move event has already been routed to whichever component is now
     * under the cursor, which sets that component's hover state. This method
     * clears the hover of the component that was hovered before, if the pointer
     * has moved onto a different one. Routing only ever delivers a move to the
     * component under the cursor, so without this the left-behind component
     * would stay stuck in {@link ComponentState#HOVER} until something else
     * happened to repaint it.</p>
     */
    private void updateHover() {
        // Popups overlay the page, so they win the hover race; hitTest()
        // reports null when the pointer is not over one, letting the page
        // underneath keep its own hover updates.
        Component target = popupLayer.hitTest((float) mouseX, (float) mouseY);
        if (target == null) {
            target = rootPanel.getComponentAt((float) mouseX, (float) mouseY);
        }
        if (target == hovered) {
            return;
        }
        if (hovered != null && hovered.getState() == ComponentState.HOVER) {
            hovered.setState(ComponentState.IDLE);
        }
        hovered = target;
    }

    /**
     * Converts a toolkit mouse button id to a Glyph MouseButton enum value.
     *
     * @param button the toolkit button id (0 = left, 1 = right, 2 = middle)
     * @return the corresponding MouseButton
     */
    private MouseButton convertMouseButton(int button) {
        return switch (button) {
            case 1 ->
                MouseButton.RIGHT;
            case 2 ->
                MouseButton.MIDDLE;
            default ->
                MouseButton.LEFT;
        }; // toolkit button id: right
        // toolkit button id: middle
    }

    /**
     * Gets modifier keys from the toolkit modifier bitmask.
     *
     * @param mods the modifier flags (see GlyphMods)
     * @return the set of KeyModifiers
     */
    private EnumSet<KeyModifier> getModifiers(int mods) {
        EnumSet<KeyModifier> modifiers = EnumSet.noneOf(KeyModifier.class);

        if ((mods & GlyphMods.SHIFT) != 0) {
            modifiers.add(KeyModifier.SHIFT);
        }
        if ((mods & GlyphMods.CTRL) != 0) {
            modifiers.add(KeyModifier.CTRL);
        }
        if ((mods & GlyphMods.ALT) != 0) {
            modifiers.add(KeyModifier.ALT);
        }

        return modifiers;
    }

    /**
     * Gets the root panel of the application.
     *
     * @return the root panel
     */
    public Panel getRootPanel() {
        return rootPanel;
    }

    /**
     * Gets the top-most popup layer of the application. Popups added here
     * (typically {@link io.github.yasmramos.glyphui.ui.Menu}s) paint above the
     * whole scene, receive the pointer before the root panel, and are
     * dismissed by a press outside them. {@code Menu.open()} attaches itself
     * automatically, so applications rarely need this accessor directly.
     *
     * @return the popup layer (never null, valid before {@link #init()})
     */
    public PopupLayer getPopupLayer() {
        return popupLayer;
    }

    /**
     * Gets the native window wrapper created during {@link #init()}.
     *
     * <p><strong>Not part of the frozen public API:</strong> reserved for the
     * toolkit's own render loop and integration tests. Application code should
     * configure windows through {@code Application} / {@code WindowConfig}
     * instead (see {@code docs/architecture/api-stability.md}).</p>
     *
     * @return the window, or null before init
     */
    public Window getWindow() {
        return window;
    }

    /**
     * Gets the keyboard focus manager created during {@link #init()}.
     *
     * @return the focus manager, or null before init
     */
    public FocusManager getFocusManager() {
        return focusManager;
    }

    /**
     * Installs a CSS style engine for this application. When set, the engine
     * re-cascades its stylesheet against the component tree at the start of
     * every frame (so {@code :hover}/{@code :focus}/{@code
     * :disabled}/{@code :active} rules track live component state). Pass null
     * to disable styling.
     *
     * <p>
     * Typical usage with a loaded markup document:</p>
     * <pre>{@code
     * StyleSheet sheet = StyleSheet.parse(cssText);
     * StyleEngine engine = new StyleEngine();
     * engine.setStyleSheet(sheet);
     * app.setStyleEngine(engine);
     * }</pre>
     *
     * @param styleEngine the engine to install, or null
     */
    public void setStyleEngine(StyleEngine styleEngine) {
        this.styleEngine = styleEngine;
        requestRepaint();
    }

    /**
     * Convenience: builds a
     * {@link io.github.yasmramos.glyphui.style.StyleEngine} from the given
     * stylesheet and installs it (see {@link #setStyleEngine}).
     *
     * @param styleSheet the stylesheet to apply each frame
     * @return the installed engine
     */
    public StyleEngine applyStyleSheet(StyleSheet styleSheet) {
        StyleEngine engine = new StyleEngine(styleSheet);
        setStyleEngine(engine);
        return engine;
    }

    /**
     * Gets the canvas for rendering operations.
     *
     * @return the canvas
     */
    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * Renders a single frame. Exposed for testing purposes. Must run on the
     * UI thread (Skija native state is not thread-safe); before {@link
     * #run()} starts any thread qualifies, which keeps headless tests legal.
     */
    void renderFrame() {
        checkThread();
        // Test/debug entry point: always cascade so a manually rendered frame
        // reflects the latest tree state regardless of the dirty latch.
        Component.consumeStyleDirty();
        render(true);
    }

    /**
     * Captures the current frame to a PNG file.
     *
     * <p>No vertical flip is applied: the GPU surface wraps the default
     * framebuffer with {@code SurfaceOrigin.BOTTOM_LEFT}, but Skija's
     * {@code makeImageSnapshot()} already normalises snapshots to top-left
     * row order, so the encoded PNG matches the on-screen orientation for
     * both backends.</p>
     *
     * <p>
     * <strong>Threading:</strong> this method touches Skija native state and
     * the component tree without synchronization; it must be called on the UI
     * thread (or before {@link #run()} starts). From other threads, marshal it:
     * {@code app.invokeLater(() -> app.captureToPng(file))}.</p>
     *
     * @param file the output file path
     * @throws RuntimeException if capture fails
     */
    public void captureToPng(java.io.File file) {
        checkThread();

        // Render the current frame first
        renderFrame();

        // Flush GPU context if using GPU backend
        if (directContext != null) {
            directContext.flush();
        }

        // Create directory if it doesn't exist
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        // Take a snapshot of the surface
        Image image = surface.makeImageSnapshot();
        if (image == null) {
            throw new RuntimeException("Failed to create image snapshot");
        }

        try {
            // Encode to PNG format
            Data data = image.encodeToData(EncodedImageFormat.PNG);

            // If encoding failed, try alternative approach
            if (data == null) {
                data = image.encodeToData();
            }

            if (data == null) {
                throw new RuntimeException("Failed to encode image to PNG");
            }

            try {
                // Write bytes to file
                byte[] bytes = data.getBytes();
                try {
                    java.nio.file.Files.write(file.toPath(), bytes);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Failed to write PNG file: " + e.getMessage(), e);
                }
            } finally {
                data.close();
            }
        } finally {
            image.close();
        }
    }

    /**
     * Returns the thread that started {@link #run()} (the UI thread), or
     * {@code null} if the event loop has not started yet.
     *
     * @return the UI thread, or null
     */
    public Thread getUiThread() {
        return uiThread;
    }

    /**
     * Returns true when the calling thread is the UI thread. Before
     * {@link #run()} starts there is no UI thread yet, so widget mutation
     * (building the initial tree) is also considered "on the UI thread".
     *
     * @return true if it is safe to mutate widgets directly from this thread
     */
    public boolean isUiThread() {
        Thread thread = uiThread;
        return thread == null || thread == Thread.currentThread();
    }

    /**
     * Throws {@link IllegalStateException} when called from a thread other than
     * the UI thread. Used by native-resource-owning APIs (fonts, paints) to
     * fail fast instead of corrupting Skija state.
     */
    public void checkThread() {
        if (!isUiThread()) {
            throw new IllegalStateException(
                    "Operation must run on the Glyph UI thread; use Application.invokeLater(...) "
                    + "or Property.set(...) which marshals automatically.");
        }
    }

    /**
     * Schedules a task to run on the UI thread during the next event-loop
     * iteration. If called from the UI thread the task runs immediately.
     *
     * <p>
     * This is the escape hatch for composite operations that cannot be
     * expressed as a single property change. Most callers do not need it:
     * {@link io.github.yasmramos.glyphui.graphics.Property#set(Object)}
     * marshals through this method automatically.</p>
     *
     * <p>The posted task marks the frame dirty and wakes up a UI thread
     * blocked waiting for events via {@code postEmptyEvent()}, so it
     * executes promptly even when the window is idle.</p>
     *
     * @param task the runnable to execute on the UI thread
     */
    public void invokeLater(Runnable task) {
        if (task == null) {
            return;
        }
        if (isUiThread()) {
            task.run();
            return;
        }
        uiTaskQueue.add(task);
        requestRepaint();
        // Wake the UI thread if it is blocked waiting for events. Only valid
        // once the window backend is initialized; before that the queue is
        // drained on the first run() iteration anyway.
        if (window != null) {
            window.postEmptyEvent();
        }
    }

    /**
     * Drains the UI task queue on the UI thread. Exceptions from user tasks are
     * reported but do not stop the loop or the remaining tasks.
     */
    /**
     * Package-private test hook: drains the posted-task queue on the calling
     * thread and returns how many tasks ran. Used by unit tests that simulate
     * the UI loop without native/Skija initialization.
     *
     * @return number of tasks executed
     */
    int drainUiTasksForTests() {
        int count = 0;
        Runnable task;
        while ((task = uiTaskQueue.poll()) != null) {
            count++;
            try {
                task.run();
            } catch (RuntimeException e) {
                log.log(Logger.Level.ERROR, "Exception in posted UI task: " + e.getMessage(), e);
            }
        }
        return count;
    }

    private void drainUiTasks() {
        Runnable task;
        while ((task = uiTaskQueue.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.log(Logger.Level.ERROR, "Exception in UI task: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Queues a task on the current application's UI-thread queue and wakes up
     * its event loop. Used by
     * {@link io.github.yasmramos.glyphui.graphics.Property} when the widget
     * captured no application instance at creation time. A no-op when no
     * application is running (headless/test contexts).
     *
     * @param task the runnable to execute on the UI thread
     */
    public static void invokeOnCurrent(Runnable task) {
        if (task == null) {
            return;
        }
        Application app = current;
        if (app != null) {
            app.invokeLater(task);
        }
    }

    /**
     * Sets an animation callback that keeps the loop repainting every frame.
     * Pass null to disable animations and return to pure on-demand rendering.
     *
     * @param animationCallback callback advanced once per loop iteration while
     * set
     */
    public void setAnimationCallback(Runnable animationCallback) {
        this.animationCallback = animationCallback;
        if (animationCallback != null) {
            requestRepaint();
        }
    }

    /**
     * Returns true when the GPU (OpenGL) backend is active, false for raster.
     * The raster backend never creates a DirectContext, so a null context
     * identifies it reliably; an installed {@link GlSurfaceFactory} counts as
     * GPU even before its first successful create().
     *
     * @return true if GPU backend is active
     */
    public boolean isGpuBackend() {
        return directContext != null
                || (surfaceFactory != null && surfaceFactory.isGpu());
    }

    /**
     * Runs the application main loop with on-demand rendering:
     * frames are only produced when something changed (input events, component
     * mutations, resize) or an animation is active. The JWM backend drives
     * its own native message loop (see {@code enterEventLoop}); the polling
     * fallback blocks on waitEvents (GPU) or sleeps briefly while idle
     * (raster) to avoid busy-waiting.
     */
    public void run() {
        if (!running) {
            return;
        }
        uiThread = Thread.currentThread();
        // Publish this instance so lazily created widget properties can find
        // the marshalling target (see Application.getCurrent()).
        current = this;
        // Let Canvas wrappers created from now on (e.g. per-widget offscreen
        // surfaces) inherit this application's UI-thread affinity, so a draw
        // from a foreign thread fails fast instead of corrupting Skija.
        Canvas.setThreadCheckOwner(() -> uiThread == null ? null : this);
        // Same fail-fast guard for every Window entry point (item 21): the
        // native windowing calls behind it are UI-thread-only.
        window.setThreadCheck(this::checkThread);

        if (window.isAppOwnedLoop()) {
            // The backend owns the UI thread and its native message loop
            // (JWM): hand it the frame body, which it invokes on this thread
            // once per dispatched event batch. This call blocks until the
            // application shuts down, so there is nothing left to do here.
            window.enterBackendEventLoop(this::frameStep);
            return;
        }

        while (!window.shouldClose()) {
            final boolean painted = frameStep();
            if (!painted) {
                if (!isGpuBackend()) {
                    // Raster backend has no hardware vsync: sleep a short interval
                    // so the loop does not consume CPU while idle.
                    try {
                        Thread.sleep(RASTER_IDLE_SLEEP_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    // GPU backend with a clean frame: block in waitEvents()
                    // instead of busy-polling. The loop is woken up by real input
                    // events or by postEmptyEvent() from invokeLater(...).
                    window.waitEvents();
                }
            }
        }
    }

    /**
     * One iteration of the frame loop: pump native events, drain tasks posted
     * from other threads, advance the animation callback and paint when
     * something changed. Called by the polling loop in {@link #run()} or by an
     * app-owned-loop backend (JWM), always on the UI thread.
     *
     * @return true when this iteration rendered a frame
     */
    private boolean frameStep() {
        // Poll events (callbacks may mark paintDirty)
        window.pollEvents();

        // Run tasks posted from other threads via invokeLater(...).
        // Properties marshalled through Application.invokeOnCurrent()
        // end up in the same queue (it delegates to current.invokeLater),
        // so draining this instance's queue is sufficient — no separate
        // "drainCurrent()" pass is needed.
        drainUiTasks();

        // Advance animation while one is registered (continuous repainting)
        boolean animating = (animationCallback != null);
        if (animating) {
            animationCallback.run();
        }

        // Consume the global style-dirty latch once per frame. The cascade is
        // much more expensive than painting, so it only re-runs when a
        // style-affecting mutator fired (setState/setVisible/setEnabled/
        // addStyleClass/... or a stylesheet/theme swap). While an animation is
        // running we keep re-cascading as a safety net for custom widgets that
        // mutate state without going through the framework setters.
        if (Component.consumeStyleDirty() || animating) {
            styleDirty = true;
        }

        // Atomically test-and-consume the dirty flag so that a concurrent
        // requestRepaint() landing between the check and render() cannot
        // be lost (volatile read + write alone has a race window).
        final boolean needPaint;
        final boolean needCascade;
        synchronized (this) {
            needPaint = (paintDirty || animating) && !framebufferDegenerate();
            needCascade = styleDirty && needPaint;
            if (needPaint) {
                paintDirty = false;
            }
            if (needCascade) {
                styleDirty = false;
            }
        }

        if (needPaint) {
            lastFrameTime = nowSeconds();
            render(needCascade);
            return true;
        }
        return false;
    }

    /**
     * True while the window has no drawable area — i.e. it is minimized,
     * because JWM reports a 0x0 framebuffer then. Painting is
     * suppressed in that state: the frame would have nowhere to go, and on
     * JWM the present path would ask the native layer (also resized to 0x0)
     * for a frame, whose empty {@code Surface.wrapPixels(...)} throws from
     * inside the native event dispatch and kills the loop. The dirty flag is
     * preserved meanwhile, so restoring the window repaints immediately.
     *
     * @return true when rendering must be skipped
     */
    private boolean framebufferDegenerate() {
        return window != null
                && (window.getFramebufferWidth() <= 0 || window.getFramebufferHeight() <= 0);
    }

    /**
     * Renders the current frame. The paint-dirty flag is consumed by the event
     * loop under the instance monitor (see {@link #run()}); this method also
     * clears it defensively so direct calls from tests ({@link #renderFrame()})
     * and {@link #captureToPng(java.io.File)} behave consistently.
     *
     * @param cascade true when the CSS cascade must re-run before painting
     *                (style-dirty frame); false skips the expensive pass and
     *                paints with the last computed styles
     */
    private void render(boolean cascade) {
        synchronized (this) {
            paintDirty = false;
        }

        // Re-cascade the stylesheet against the current tree state so that
        // pseudo-classes (:hover, :focus, :disabled, :active) reflect live
        // component state before painting. Only done on style-dirty frames —
        // see the styleDirty flag in frameStep().
        if (styleEngine != null && cascade) {
            styleEngine.apply(rootPanel);
            styleEngine.apply(popupLayer);
        }

        io.github.humbleui.skija.Canvas nativeCanvas = canvas.nativeCanvasForInternalUse();
        int saveCount = nativeCanvas.save();
        try {
            // Clear once, on the identity CTM, so the whole PHYSICAL surface
            // is covered. (A second clear through the wrapper was redundant
            // and polluted the save/restore stack.)
            nativeCanvas.clear(Color.makeARGB(255, 30, 30, 30));

            // HiDPI: map logical UI coordinates to physical device pixels
            nativeCanvas.scale(contentScaleX, contentScaleY);

            // Render root panel and all children (in logical coordinates)
            rootPanel.renderInternal(canvas);

            // The popup layer paints last so menus overlay page content —
            // this is the toolkit's substitute for a z-index.
            popupLayer.renderInternal(canvas);
        } finally {
            nativeCanvas.restoreToCount(saveCount);
        }

        // Flush responsibilities:
        //   - Canvas.flush() -> Surface.flushAndSubmit(): submits Skia draw
        //     ops to the backend (required for both raster and GPU paths).
        //   - DirectContext.flush(): additionally pushes GL work into the
        //     driver queue; only meaningful with a GPU context.
        canvas.flush();

        if (isGpuBackend()) {
            directContext.flush();
            window.swapBuffers();
        } else if (window != null && !window.isGlCapable()) {
            // Raster-only backend (JWM): the frame lives in a CPU surface,
            // so push it onto the native window layer. When the layer is not
            // ready yet (JWM allocates it on the first native frame), ask for
            // another paint instead of leaving the window stale.
            if (!window.present(surfaceResult)) {
                requestRepaint();
            }
        }
        // Raster backend behind a GL-capable window: the surface is only
        // consumed off-screen (tests / captureToPng), no presentation needed.
    }

    /**
     * Stops the application.
     */
    public void stop() {
        running = false;
    }

    /**
     * Cleans up resources and destroys the application.
     *
     * <p>Release order follows native-resource ownership: the component tree
     * first (widgets close their paints/fonts), then the shared font cache,
     * then the Skija surface, then the {@code DirectContext} that created it,
     * and finally the JWM window. This method is idempotent.</p>
     */
    @Override
    public void close() {
        running = false;
        if (current == this) {
            current = null;
        }
        // Drop the Canvas thread-check hook once this instance stops being
        // the marshalling target, so wrappers created later (e.g. by other
        // applications or tests) do not inherit a closed instance's affinity.
        if (uiThread != null) {
            Canvas.setThreadCheckOwner(null);
            uiThread = null;
        }

        // Clear every process-wide static this application installed, so a
        // closed instance is never retained through global hooks (important
        // for tests that create/destroy many applications). Method references
        // have unstable identity (== between this::requestRepaint instances
        // is not guaranteed), so ownership is tracked with an explicit flag
        // set by init().
        releaseRepaintHooks();

        if (focusManager != null) {
            if (FocusManager.getGlobalFocusManager() == focusManager) {
                FocusManager.setGlobalFocusManager(null);
            }
            focusManager = null;
        }
        TextField.setClipboardHooks(null, null);

        // Dispose all components in the root panel (cascades to children)
        if (rootPanel != null) {
            rootPanel.dispose();
        }

        // Same for any popup still attached to the layer (menus).
        if (popupLayer != null) {
            popupLayer.dispose();
        }

        // Release the shared typeface cache after every widget font is closed
        Fonts.close();

        // Close the surface before the context that owns it
        if (surface != null) {
            surface.close();
            surface = null;
        }

        if (directContext != null) {
            directContext.close();
            directContext = null;
        }

        // Release whatever the active factory still owns (e.g. a GL context
        // created during a failed init that was never adopted above). The
        // application already closed the adopted context, and factory close()
        // is idempotent, so this is a no-op on every successful path.
        if (surfaceFactory != null) {
            surfaceFactory.close();
            surfaceFactory = null;
        }

        // Destroy window (JWM) last
        if (window != null) {
            window.destroy();
        }
    }

    /**
     * Legacy alias for {@link #close()}, kept for backward compatibility. New
     * code should prefer try-with-resources on {@code Application}.
     */
    public void destroy() {
        close();
    }
}
