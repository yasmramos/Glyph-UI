package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.core.RepaintRequester;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.reactive.Disposable;
import io.github.yasmramos.glyphui.reactive.DisposableScope;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleNode;
import io.github.yasmramos.glyphui.style.StyleProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Abstract base class for all UI components.
 *
 * <p>
 * Components implement {@link StyleNode} so the style subsystem
 * ({@code StyleSheet}/{@code StyleEngine}) can match CSS selectors against the
 * live widget tree.</p>
 *
 * <p>
 * Components that own native resources (paints, fonts) should release them in
 * {@link #dispose()}. {@code Component} implements {@link AutoCloseable} so
 * widgets can also be used with try-with-resources; {@link #close()} simply
 * delegates to {@link #dispose()}.</p>
 *
 * <h2>Threading</h2>
 * <p>
 * The geometric/visibility state is exposed as observable {@link Property}
 * objects ({@link #xProperty()}, {@link #yProperty()},
 * {@link #widthProperty()}, {@link #heightProperty()},
 * {@link #visibleProperty()}, {@link #enabledProperty()}). Calling
 * {@code Property.set(...)} from a background thread marshals the change onto
 * the UI thread automatically; the classic setters ({@link #setX(float)}, etc.)
 * delegate to those properties and are therefore equally safe.</p>
 */
public abstract class Component implements StyleNode, Disposable {

    /**
     * Monotonic counter used to generate unique component IDs.
     */
    private static final AtomicLong ID_COUNTER = new AtomicLong();

    /**
     * Resources tied to this component's lifetime; created lazily.
     */
    private DisposableScope resources;
    /**
     * Set by the first {@link #dispose()} call.
     */
    private boolean disposed;

    /**
     * Notifies the global repaint requester that this component needs
     * repainting. Subclasses and containers call this after any mutation
     * that affects how the component is drawn.
     *
     * <p>This hook used to be a <em>second</em>, per-class static requester
     * ({@code Component.setRepaintRequester}) maintained beside the global
     * one. Two independent process-wide hooks meant {@code Application.init()}
     * had to install (and {@code close()} release) both, with twin ownership
     * flags — and every widget copied the confusion into the next 45 widgets.
     * There is now exactly one hook: {@link #setGlobalRepaintRequester}.
     * When no application owns it (standalone components, unit tests) the
     * reference is null and requesting a repaint is a silent no-op.</p>
     */
    protected void requestRepaint() {
        invalidate();
    }

    /**
     * Process-wide "style dirty" latch consumed by {@code Application}. The
     * cascade re-run (stylesheet matching for the whole tree) is far more
     * expensive than painting, so it must not happen on every frame — only
     * when something that affects selector matching actually changed: a
     * pseudo-class state (:hover/:focus/:disabled/:active), visibility,
     * enabled state, style classes, id, style tag, inline style, or the
     * stylesheet/theme itself.
     *
     * <p>The latch is deliberately global and sticky-true: once any component
     * invalidates its style, the next cascade pass clears it (see
     * {@code Application.frameStep()}). A missed reset can only cause one
     * redundant cascade; a spurious set merely re-cascades, which is always
     * safe.</p>
     */
    private static final java.util.concurrent.atomic.AtomicBoolean globalStyleDirty =
            new java.util.concurrent.atomic.AtomicBoolean(true);

    /**
     * Marks the global style cache as dirty so the next frame re-runs the CSS
     * cascade. Called automatically by every style-affecting mutator
     * ({@link #setState}, {@link #setVisible}, {@link #setEnabled},
     * {@link #addStyleClass}, {@link #removeStyleClass}, {@link #setId},
     * {@link #setStyleTag}, {@link #setInlineStyle}); application code rarely
     * needs to call it directly.
     */
    public static void invalidateStyle() { // NOPMD - static latch kept for API compatibility; see invalidateStyleAndRelayout()
        globalStyleDirty.set(true);
    }

    /**
     * Atomically tests and clears the global style-dirty latch. Internal
     * framework hook used by {@code Application} to decide whether the frame
     * needs a cascade pass.
     *
     * @return true when a cascade pass is required (and consumes the latch)
     */
    public static boolean consumeStyleDirty() {
        return globalStyleDirty.getAndSet(false);
    }

    /**
     * Clears the global style-dirty latch. Test-support hook (package-private
     * in {@code io.github.yasmramos.glyphui.ui}): unit tests in other packages
     * drain it via reflection so each test starts from a known-clean cascade
     * state regardless of what other tests or setup code invalidated. Never
     * part of the frozen public API surface.
     */
    static void resetStyleDirtyForTests() {
        globalStyleDirty.set(false);
    }

    /**
     * Default width used when no explicit bounds were provided and layout is
     * free to decide.
     */
    public static final float DEFAULT_WIDTH = 150f;
    /**
     * Default height used when no explicit bounds were provided and layout is
     * free to decide.
     */
    public static final float DEFAULT_HEIGHT = 40f;

    protected float x;
    protected float y;
    protected float width;
    protected float height;
    protected boolean visible;
    protected boolean enabled;
    protected Panel parent;
    protected String id;
    protected ComponentState state;
    /**
     * Tracks whether the bounds (width/height) were set explicitly by the user
     * through a bounds-carrying constructor or
     * {@link #setWidth}/{@link #setHeight}. When false, layout managers are
     * free to resize the component to its preferred size; when true, the
     * user-provided size must be respected.
     */
    protected boolean sizeExplicitlySet;

    /**
     * Whether <em>this component instance</em> was constructed with explicit
     * bounds. Unlike {@link #sizeExplicitlySet} (which flips to true whenever
     * {@code setWidth}/{@code setHeight} runs), this flag is final-by-
     * construction and lets subclasses distinguish "the user pinned my size"
     * from "my container's layout manager assigned me these bounds". Panels
     * use it in {@code measure()} to decide whether content-based sizing via
     * the layout manager still applies.
     */
    private boolean ctorSizeExplicit;

    // --- Observable properties (lazily created, marshalled via Application.invokeLater) ---
    private Property<Float> xProperty;
    private Property<Float> yProperty;
    private Property<Float> widthProperty;
    private Property<Float> heightProperty;
    private Property<Boolean> visibleProperty;
    private Property<Boolean> enabledProperty;

    /**
     * Property hook: applies a user-set width, marks bounds as explicit and
     * requests a relayout. Package-private so {@code Panel} can install the
     * same property without re-marking the bounds flag (it only needs to add
     * the layout-dirty step on top).
     */
    void applyWidthExplicit(float newWidth) {
        this.width = newWidth;
        this.sizeExplicitlySet = true;
        applyExplicitSize();
        invalidateLayout();
    }

    /**
     * Property hook: applies a user-set height; same single-repaint path as
     * {@link #applyWidthExplicit(float)}.
     */
    void applyHeightExplicit(float newHeight) {
        this.height = newHeight;
        this.sizeExplicitlySet = true;
        applyExplicitSize();
        invalidateLayout();
    }

    /**
     * Returns the observable x-coordinate property. Setting it from a
     * background thread marshals the change onto the UI thread.
     *
     * @return the x property (never null after first access)
     */
    public Property<Float> xProperty() {
        if (xProperty == null) {
            xProperty = new Property<>(null, x, v -> { this.x = v; invalidate(); });
        }
        return xProperty;
    }

    /**
     * Returns the observable y-coordinate property.
     *
     * @return the y property (never null after first access)
     */
    public Property<Float> yProperty() {
        if (yProperty == null) {
            yProperty = new Property<>(null, y, v -> { this.y = v; invalidate(); });
        }
        return yProperty;
    }

    /**
     * Returns the observable width property. The property is the single
     * source of truth for size changes: assigning to it (directly or via
     * {@link #setWidth(float)}) marks the bounds as explicitly user-set —
     * layout managers must then respect the size until {@link #setSize} or a
     * layout-neutral path resets the flag.
     *
     * @return the width property (never null after first access)
     */
    public Property<Float> widthProperty() {
        if (widthProperty == null) {
            widthProperty = new Property<>(null, width, this::applyWidthExplicit);
        }
        return widthProperty;
    }

    /**
     * Returns the observable height property. Like {@link #widthProperty()},
     * assigning to it marks the bounds as explicitly user-set (see
     * {@link #isSizeExplicitlySet()}).
     *
     * @return the height property (never null after first access)
     */
    public Property<Float> heightProperty() {
        if (heightProperty == null) {
            heightProperty = new Property<>(null, height, this::applyHeightExplicit);
        }
        return heightProperty;
    }

    /**
     * Returns the observable visibility property.
     *
     * @return the visible property (never null after first access)
     */
    public Property<Boolean> visibleProperty() {
        if (visibleProperty == null) {
            visibleProperty = new Property<>(null, visible, v -> {
                if (this.visible != v) {
                    this.visible = v;
                    // A hidden widget cannot keep the keyboard focus.
                    FocusManager fm = FocusManager.getGlobalFocusManager();
                    if (fm != null) {
                        fm.componentVisibilityChanged(this);
                    }
                    // Focus loss can change :focus pseudo-class matching.
                    invalidateStyleAndRelayout();
                }
            });
        }
        return visibleProperty;
    }

    /**
     * Returns the observable enabled property.
     *
     * @return the enabled property (never null after first access)
     */
    public Property<Boolean> enabledProperty() {
        if (enabledProperty == null) {
            enabledProperty = new Property<>(null, enabled, v -> {
                if (this.enabled != v) {
                    this.enabled = v;
                    if (!v) {
                        // A disabled widget cannot keep the keyboard focus,
                        // and it must not linger in :hover/:active — those
                        // pseudo-classes could otherwise match together with
                        // :disabled until the next mouse event arrives.
                        this.state = ComponentState.IDLE;
                        io.github.yasmramos.glyphui.core.FocusManager fm =
                                io.github.yasmramos.glyphui.core.FocusManager.getGlobalFocusManager();
                        if (fm != null) {
                            fm.componentDisabled(this);
                        }
                    }
                    // :disabled pseudo-class rules depend on this value.
                    invalidateStyleAndRelayout();
                }
            });
        }
        return enabledProperty;
    }

    /**
     * Whether this component participates in keyboard focus traversal.
     */
    private boolean focusable = true;

    /**
     * Whether this component currently holds the keyboard focus.
     */
    private boolean focused;

    /**
     * Optional explicit accessibility name override (see
     * {@link #getAccessibleName()}). Null means "derive a default".
     */
    private String accessibleName;

    /**
     * Optional explicit accessibility description override.
     */
    private String accessibleDescription;

    /**
     * Global repaint hook. When set (typically by {@code Application}), any
     * component mutation that calls {@link #invalidate()} propagates up the
     * parent chain until it reaches this requester, marking the application
     * paint-dirty. Kept as a static so plain components work even when no
     * Application is running (e.g. unit tests).
     */
    private static RepaintRequester globalRepaintRequester;

    /**
     * Number of times invalidate() has been called on this component.
     */
    private int invalidateCount;

    /**
     * Set by {@link #invalidateLayout()} / {@link #invalidateStyle()}: the
     * nearest ancestor panel must re-run its layout manager before the next
     * paint, because something that affects geometry changed (a preferred
     * size, the layout configuration, or a cascaded length property).
     */
    private boolean layoutDirty;

    /**
     * Set by {@link #invalidateStyle()}: the CSS cascade must re-run for this
     * subtree before it is painted again (in addition to the global latch
     * consumed by {@code Application}).
     */
    private boolean styleDirty;

    // ------------------------------------------------------------------
    // Style subsystem hooks (CSS subset)
    // ------------------------------------------------------------------
    /**
     * Style class names carried by this component (like HTML {@code class}).
     */
    private final List<String> styleClasses = new ArrayList<>();

    /**
     * Inline style declarations (like HTML {@code style="..."}). Highest
     * cascade priority.
     */
    private Style inlineStyle = Style.EMPTY;

    /**
     * The last computed style produced by the {@code StyleEngine} for this
     * component.
     */
    private Style computedStyle = Style.EMPTY;

    /**
     * Cache of the merged (computed + inline) style returned by
     * {@link #getComputedStyle()}. Rebuilt only when {@code computedStyle} or
     * {@code inlineStyle} actually change identity — every render/measure pass
     * used to allocate a fresh merged map and a fresh Builder per call.
     */
    private Style effectiveStyleCache;
    /** Inputs the cache was built from ({@code ==}, not {@code equals}). */
    private Style cachedEffectiveComputed;
    private Style cachedEffectiveInline;

    /** Explicit HTML-equivalent tag override; null means use {@link #defaultStyleTag()}. */
    private String styleTagOverride;

    /**
     * Installs the <b>single</b> global repaint requester used to propagate
     * invalidations that reach the root of the component tree. Called by
     * {@code Application.init()}; {@code Application.close()} releases it
     * again (only while the instance still owns it).
     *
     * @param requester the repaint requester (may be null to clear)
     */
    public static void setGlobalRepaintRequester(RepaintRequester requester) {
        globalRepaintRequester = requester;
    }

    /**
     * Gets the currently installed global repaint requester. Intended for
     * tests and diagnostics; application code should never depend on it —
     * install requesters only through {@code Application}.
     *
     * @return the requester or null
     */
    static io.github.yasmramos.glyphui.core.RepaintRequester getGlobalRepaintRequester() {
        return globalRepaintRequester;
    }

    /**
     * Creates a zero-sized component to be positioned/sized by a layout manager
     * or by {@link #setSize}. Subclasses such as {@code TextField} and
     * {@code ImageView} use this when their size is derived from content via
     * {@link #measure}.
     */
    protected Component() {
        this(0, 0, 0, 0);
        this.sizeExplicitlySet = false;
        this.ctorSizeExplicit = false;
        // The delegating constructor above sets the flags to true; a no-bounds
        // construction is by definition content-sized.
    }

    public Component(float width, float height) {
        this(0, 0, width, height);
        this.sizeExplicitlySet = false;
        this.ctorSizeExplicit = false;
    }

    /**
     * Creates a new Component.
     *
     * @param x the x-coordinate of the component
     * @param y the y-coordinate of the component
     * @param width the width of the component
     * @param height the height of the component
     */
    public Component(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.visible = true;
        this.enabled = true;
        this.parent = null;
        this.id = "component_" + ID_COUNTER.incrementAndGet();
        this.state = ComponentState.IDLE;
        this.sizeExplicitlySet = true;
        this.ctorSizeExplicit = true;
    }

    /**
     * Marks this component as needing a repaint and propagates the invalidation
     * up the parent chain. When the top of the tree is reached, the global
     * {@link io.github.yasmramos.glyphui.core.RepaintRequester} (installed by
     * {@code Application}) is invoked, which sets the application's
     * {@code paintDirty} flag so the next frame is rendered.
     *
     * <p>
     * All visual mutators ({@code setText}, {@code setWidth},
     * {@code setVisible}, ...) call this automatically.</p>
     *
     * <p><strong>Invalidation kinds.</strong> This method is the full-power
     * invalidation: it schedules a repaint and flushes any pending layout /
     * style requests accumulated along the propagation path. Mutators that
     * know <em>what</em> they changed should express that intent through the
     * narrower entry points instead:</p>
     * <ul>
     * <li>{@link #invalidatePaint()} — only pixels changed (same geometry,
     * same styles); cheapest request.</li>
     * <li>{@link #invalidateLayout()} — the preferred size changed; the
     * nearest ancestor panels re-run their layout managers before painting.</li>
     * <li>{@link #invalidateStyle()} — selector-relevant state changed
     * (pseudo-classes, classes, id, tag, inline style); forces a cascade pass
     * plus a relayout, because cascaded length properties can move pixels.</li>
     * </ul>
     * <p>All three currently converge on this method for the actual repaint
     * bookkeeping; keeping them as distinct entry points preserves the
     * caller's intent so finer-grained dirty tracking can be added later
     * without touching widget code again.</p>
     */
    public void invalidate() {
        invalidateCount++;
        // Flush per-component requests at every node of the propagation path,
        // including the component itself when it is a Panel: an attached child
        // must be able to dirty its direct parent's layout flag, not only the
        // grandparent chain. Doing this before walking up keeps the flags set
        // even when no repaint requester is installed (headless tests).
        flushInvalidationRequests();
        Panel p = parent;
        if (p != null) {
            // Propagate upward through the tree until the root is reached
            p.invalidate();
        } else {
            // Ask the application to repaint
            RepaintRequester requester = globalRepaintRequester;
            if (requester != null) {
                requester.requestRepaint();
            }
        }
    }

    /**
     * Drains this component's pending layout/style invalidation flags into the
     * appropriate sinks: the style request goes to the global cascade latch,
     * and a layout request marks the nearest ancestor panel (or this component
     * itself when it is a panel) as needing a relayout. Called from
     * {@link #invalidate()} at each node along the propagation path.
     */
    private void flushInvalidationRequests() {
        if (styleDirty) {
            styleDirty = false;
            Component.invalidateStyle();
        }
        if (layoutDirty && !(this instanceof Panel)) {
            // Children propagate their relayout request to the direct parent.
            // A Panel's own flag is consumed by doLayout(), so flushing it
            // here would clear a freshly-set structural request before the
            // paint pass ever sees it; panels keep and drain the flag locally.
            // A detached component has no panel to dirty: the flag stays set
            // until the next propagation after it is added to a tree.
            Panel p = parent;
            if (p != null) {
                p.markLayoutDirty();
            }
        }
    }

    /**
     * Requests a repaint only. Use for mutations that change how the component
     * looks without affecting its size or the CSS cascade result (e.g. a
     * layout-assigned bounds change, an internal animation frame).
     */
    protected void invalidatePaint() {
        invalidate();
    }

    /**
     * Requests a relayout of the containing panels plus a repaint. Use for
     * mutations that change the component's preferred size (text content,
     * image source) so ancestors get a chance to resize siblings around it.
     */
    protected void invalidateLayout() {
        // Set the flag first: invalidate() flushes pending requests at every
        // node of the propagation path (including this component). Flushing
        // before setting would drain the fresh flag on a leaf component that
        // has no parent to receive it, losing the relayout request entirely.
        layoutDirty = true;
        invalidate();
    }

    /**
     * Requests a CSS re-cascade plus a relayout and repaint. Use for
     * mutations that change selector-relevant state (pseudo-class state,
     * style classes, id, style tag, inline style): cascaded values may
     * include lengths, so a style change must also re-measure the subtree.
     *
     * <p>Instance variant of the static {@link #invalidateStyle()} latch:
     * besides setting the global "cascade needed" flag, it marks the ancestor
     * chain layout-dirty so geometry reacts to the new computed values.</p>
     */
    protected void invalidateStyleAndRelayout() {
        // Same flag-before-flush ordering as invalidateLayout(): the style
        // latch and the layout request must survive the drain performed by
        // invalidate() at this node.
        styleDirty = true;
        layoutDirty = true;
        invalidate();
    }

    /**
     * Whether a relayout request is pending on this node (its own flag or,
     * for containers, the aggregated flag of the subtree rooted here).
     * Protected so {@code Panel} can expose the aggregated state while the
     * raw per-node flag stays private to {@code Component}.
     *
     * @return true when a relayout has been requested but not yet consumed
     */
    protected boolean isLayoutDirtyFlag() {
        return layoutDirty;
    }

    /**
     * Records a pending relayout request on this node's own flag without
     * running the upward invalidation chain. Package-private counterpart of
     * the private {@code flushInvalidationRequests} sink for callers that
     * must keep the request deferred — notably {@link Panel}, whose panel-
     * level drain ({@code doLayout}) and component-level flush otherwise
     * track different flags. Overridden by {@code Panel} to keep both views
     * in sync.
     */
    void markLayoutDirtyRequested() {
        layoutDirty = true;
    }

    /**
     * Clears this node's own pending relayout request without touching the
     * upward invalidation chain. Package-private drain counterpart of
     * {@link #markLayoutDirtyRequested()} used by {@code Panel.doLayout()}
     * once a relayout pass has actually run.
     */
    void clearLayoutRequest() {
        layoutDirty = false;
    }

    /**
     * Gets how many times this component has been invalidated. Internal
     * instrumentation hook — NOT public API since the 0.1 surface freeze;
     * tests reach it via {@code io.github.yasmramos.glyphui.core.internal.TestHooks}
     * (reflectively, since this method is package-private). See
     * {@code docs/architecture/api-stability.md}.
     *
     * @return the invalidate count
     */
    int getInvalidateCount() {
        return invalidateCount;
    }

    /**
     * Resets the invalidate counter back to zero. Internal instrumentation
     * hook — see {@link #getInvalidateCount()} for the boundary policy; use
     * {@link io.github.yasmramos.glyphui.core.internal.TestHooks} from tests.
     */
    void resetInvalidateCount() {
        invalidateCount = 0;
    }

    // ------------------------------------------------------------------
    // Style API (CSS subset integration)
    // ------------------------------------------------------------------
    /**
     * The default HTML-equivalent tag name for this component class, used by
     * CSS type selectors. Subclasses override it (Button → "button", Label →
     * "label", TextField → "input", ...). Panels return "div".
     *
     * @return a lower-case tag name
     */
    protected String defaultStyleTag() {
        return "div";
    }

    /**
     * Gets the effective style tag: the per-instance override when set,
     * otherwise {@link #defaultStyleTag()}.
     *
     * @return the tag name matched by type selectors
     */
    public String getStyleTag() {
        return styleTagOverride != null ? styleTagOverride : defaultStyleTag();
    }

    /**
     * Overrides the HTML-equivalent tag for this instance (used by the markup
     * loader for unknown tags and by tests).
     *
     * @param styleTag the tag name (null restores the class default)
     */
    public void setStyleTag(String styleTag) {
        this.styleTagOverride = styleTag == null
                ? null : styleTag.toLowerCase(Locale.ROOT);
        // Type selectors match on the style tag: re-cascade on the next frame.
        invalidateStyleAndRelayout();
    }

    /**
     * Adds a style class to this component (ignored when already present).
     * Invalidates the component so the repaint re-runs style resolution.
     *
     * @param className the class name without the leading dot
     */
    public void addStyleClass(String className) {
        if (className != null && !className.isBlank()
                && !styleClasses.contains(className)) {
            styleClasses.add(className);
            // Class selectors depend on this set: re-cascade on the next frame.
            invalidateStyleAndRelayout();
        }
    }

    /**
     * Removes a style class from this component.
     *
     * @param className the class name without the leading dot
     */
    public void removeStyleClass(String className) {
        if (styleClasses.remove(className)) {
            // Class selectors depend on this set: re-cascade on the next frame.
            invalidateStyleAndRelayout();
        }
    }

    /**
     * Whether this component carries the given style class.
     *
     * @param className the class name
     * @return true when present
     */
    public boolean hasStyleClass(String className) {
        return styleClasses.contains(className);
    }

    /**
     * The style classes of this component, in insertion order.
     *
     * @return an unmodifiable list
     */
    public List<String> getStyleClasses() {
        return Collections.unmodifiableList(styleClasses);
    }

    /**
     * Sets the inline style (equivalent of {@code style="..."} in HTML). Inline
     * declarations have the highest cascade priority.
     *
     * @param style the inline style (null clears it)
     */
    public void setInlineStyle(Style style) {
        this.inlineStyle = style == null ? Style.EMPTY : style;
        // Keep the explicit-size fields in sync with the inline declarations:
        // applyExplicitSize() is the only writer of WIDTH/HEIGHT in the inline
        // style, so an externally supplied inline style that declares them
        // must update the fields too. Without this, getWidth() and
        // getComputedStyle().getFloat(WIDTH) could disagree after a programmatic
        // setWidth() followed by setInlineStyle().
        if (sizeExplicitlySet) {
            float inlineW = inlineStyle.getFloat(
                    io.github.yasmramos.glyphui.style.StyleProperty.WIDTH, Float.NaN);
            float inlineH = inlineStyle.getFloat(
                    io.github.yasmramos.glyphui.style.StyleProperty.HEIGHT, Float.NaN);
            if (!Float.isNaN(inlineW)) {
                width = inlineW;
            }
            if (!Float.isNaN(inlineH)) {
                height = inlineH;
            }
        }
        // Single-writer rule for WIDTH/HEIGHT in the inline layer (Block 4
        // item 4.10): after adopting any inline size declarations above, fold
        // the authoritative explicit size back on top so the fields and the
        // computed inline WIDTH/HEIGHT never disagree (a replacement inline
        // style without a width used to leave getComputedStyle(WIDTH) at NaN
        // while getWidth() still reported the programmatic value).
        applyExplicitSize();
        // Inline declarations sit at the top of the cascade for this node.
        invalidateStyleAndRelayout();
    }

    /**
     * Gets the inline style of this component.
     *
     * @return the inline style, never null
     */
    public Style getInlineStyle() {
        return inlineStyle;
    }

    /**
     * Gets the computed style produced by the last {@code StyleEngine} pass,
     * with the inline style merged on top. Widgets consult this in
     * {@code render} before falling back to {@link Theme} defaults.
     *
     * @return the computed style, never null (empty before any engine pass)
     */
    public Style getComputedStyle() {
        // Identity check: the merge result is referentially stable whenever
        // both inputs are the same objects as last time, so re-cascading an
        // unchanged subtree never rebuilds the merged map.
        if (effectiveStyleCache != null
                && cachedEffectiveComputed == computedStyle
                && cachedEffectiveInline == inlineStyle) {
            return effectiveStyleCache;
        }
        // Inline style has the highest cascade priority. overrideWith() also
        // strips the synthetic RAW_VALUES var() carrier from the computed
        // style before returning it to callers.
        effectiveStyleCache = inlineStyle.isEmpty()
                ? computedStyle.withoutRawValues()
                : computedStyle.overrideWith(inlineStyle);
        cachedEffectiveComputed = computedStyle;
        cachedEffectiveInline = inlineStyle;
        return effectiveStyleCache;
    }

    /**
     * Stores the computed style. Called by the style engine; not part of the
     * public widget API. Kept public so {@code StyleEngine} (a different
     * package) can reach it and subclasses can observe/intercept cascade
     * passes; see {@link #getComputedStyle()} for the read path.
     *
     * @param computed the computed (cascaded + inherited) style
     */
    public void setComputedStyle(Style computed) {
        this.computedStyle = computed == null ? Style.EMPTY : computed;
    }

    // ------------------------------------------------- Style resolution
    // Protected helpers widgets consult in render()/measure(): a value from
    // the computed style wins over the explicit override, which in turn wins
    // over the theme default. Order matches CSS intuition: stylesheet rule →
    // programmatic setter → inline style is highest via getComputedStyle().
    /**
     * Resolves an integer (color/weight) style property for the current
     * component state. Pseudo-class rules already re-cascaded by the
     * {@code StyleEngine} are reflected here automatically.
     *
     * <p>The effective style is cached (see {@link #getComputedStyle()}) and
     * map lookups are O(1), so no further memoization is needed: two widgets
     * resolving different properties in the same frame must not interfere.</p>
     *
     * @param property       the style property
     * @param explicitOverride the per-instance setter value (may be null)
     * @param themeDefault fallback taken from the active theme
     * @return the winning value
     */
    protected int resolveIntStyle(io.github.yasmramos.glyphui.style.StyleProperty property,
            Integer explicitOverride, int themeDefault) {
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        if (s.has(property)) {
            return s.getInt(property, themeDefault);
        }
        return explicitOverride != null ? explicitOverride : themeDefault;
    }

    /**
     * Float variant of {@link #resolveIntStyle}.
     *
     * @param property the style property
     * @param explicitOverride per-instance setter value (may be null)
     * @param themeDefault fallback from the theme
     * @return the winning value
     */
    protected float resolveFloatStyle(io.github.yasmramos.glyphui.style.StyleProperty property,
            Float explicitOverride, float themeDefault) {
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        if (s.has(property)) {
            return s.getFloat(property, themeDefault);
        }
        return explicitOverride != null ? explicitOverride : themeDefault;
    }

    /**
     * Whether the computed style carries an explicit declaration for the
     * given property. Used by state-dependent widgets (e.g. a disabled
     * control) to distinguish "the author declared this value" from "the
     * theme default is in effect": a CSS declaration must win over any
     * per-instance override or themed fallback, while the fallback chain
     * only applies when no rule matched.
     *
     * @param property the style property to probe
     * @return true when the computed style contains the property
     */
    protected boolean hasStyleValue(io.github.yasmramos.glyphui.style.StyleProperty property) {
        return getComputedStyle().has(property);
    }

    /**
     * Resolves the Skia font for this component: when the computed style
     * declares {@code font-family} and/or {@code font-size}, a font is built
     * through {@link io.github.yasmramos.glyphui.graphics.FontManager}'s
     * typeface resolution (cached per family/size); otherwise the theme font
     * for the given role is returned.
     *
     * <p>
     * The returned font is owned by the caches below or by {@code FontManager}
     * — callers must not close it.</p>
     *
     * @param role the theme font role to fall back to
     * @return the resolved font, never null
     */
    protected io.github.humbleui.skija.Font resolveFont(Theme.FontRole role) {
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        boolean hasFamily = s.has(io.github.yasmramos.glyphui.style.StyleProperty.FONT_FAMILY);
        boolean hasSize = s.has(io.github.yasmramos.glyphui.style.StyleProperty.FONT_SIZE);
        if (!hasFamily && !hasSize) {
            Theme theme = getTheme();
            return theme != null ? theme.getFont(role) : io.github.yasmramos.glyphui.graphics.FontManager.getFont(role);
        }
        String family = hasFamily
                ? s.getString(io.github.yasmramos.glyphui.style.StyleProperty.FONT_FAMILY, null)
                : null;
        float size = hasSize
                ? s.getFloat(io.github.yasmramos.glyphui.style.StyleProperty.FONT_SIZE, 0f)
                : io.github.yasmramos.glyphui.graphics.FontManager.getDefaultFontSize(role);
        if (size <= 0f || Float.isNaN(size)) {
            size = io.github.yasmramos.glyphui.graphics.FontManager.getDefaultFontSize(role);
        }
        int weight = s.getInt(io.github.yasmramos.glyphui.style.StyleProperty.FONT_WEIGHT, 400);
        io.github.humbleui.skija.FontStyle style = weight >= 600
                ? io.github.humbleui.skija.FontStyle.BOLD
                : io.github.humbleui.skija.FontStyle.NORMAL;
        String key = (family == null ? "*" : family.toLowerCase(java.util.Locale.ROOT))
                + '|' + size + '|' + style.hashCode();
        io.github.humbleui.skija.Font cached = STYLE_FONTS.get(key);
        if (cached != null) {
            return cached;
        }
        io.github.humbleui.skija.Typeface face = family != null
                ? io.github.yasmramos.glyphui.graphics.FontManager.resolveTypeface(family, style)
                : io.github.yasmramos.glyphui.graphics.FontManager.resolveTypeface(null, style);
        if (face == null) {
            // Unknown/unsupported family: fall back to the default typeface.
            face = io.github.yasmramos.glyphui.graphics.FontManager.resolveTypeface(null,
                    io.github.humbleui.skija.FontStyle.NORMAL);
        }
        if (face == null) {
            // No usable typeface at all (e.g. empty font catalog in a
            // headless test): return the theme/default font instead of
            // letting the Font constructor throw an opaque NPE.
            Theme theme = getTheme();
            return theme != null ? theme.getFont(role)
                    : io.github.yasmramos.glyphui.graphics.FontManager.getFont(role);
        }
        io.github.humbleui.skija.Font font = new io.github.humbleui.skija.Font(face, size);
        STYLE_FONTS.put(key, font);
        return font;
    }

    /**
     * Cache of fonts created from CSS font-* declarations (keyed
     * family|size|style).
     */
    private static final java.util.Map<String, io.github.humbleui.skija.Font> STYLE_FONTS
            = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Releases every cached style-derived font. This cache is <b>global and
     * process-wide</b> (static): in a multi-application process, closing one
     * application invalidates fonts still referenced by another — those uses
     * simply rebuild the font lazily on next resolution, so the worst case is
     * a transient re-allocation, never a crash. Callers should treat this as
     * JVM-shutdown cleanup only. Theme/registered typefaces are owned by
     * {@code FontManager} and closed there. Not thread-safe against concurrent
     * font resolution; call from the UI/shutdown path.
     */
    static void disposeStyleFonts() {
        for (io.github.humbleui.skija.Font f : STYLE_FONTS.values()) {
            try {
                f.close();
            } catch (Exception ignored) {
                // already closed
            }
        }
        STYLE_FONTS.clear();
    }

    // ------------------------------------------------ StyleNode adapters
    @Override
    public String styleTag() {
        return getStyleTag();
    }

    @Override
    public String id() {
        return getId();
    }

    @Override
    public boolean hasClass(String className) {
        return hasStyleClass(className);
    }

    @Override
    public boolean matchesPseudo(io.github.yasmramos.glyphui.style.Selector.PseudoClass pseudo) {
        return switch (pseudo) {
            case HOVER ->
                state == ComponentState.HOVER;
            case FOCUS ->
                isFocused();
            case DISABLED ->
                !isEnabled();
            case ACTIVE ->
                state == ComponentState.PRESSED;
        };
    }

    @Override
    public StyleNode parent() {
        return parent;
    }

    /**
     * Gets the x-coordinate of the component.
     *
     * @return the x-coordinate
     */
    public float getX() {
        return x;
    }

    /**
     * Sets the x-coordinate of the component.
     *
     * @param x the new x-coordinate
     */
    public void setX(float x) {
        xProperty().set(x);
    }

    /**
     * Gets the y-coordinate of the component.
     *
     * @return the y-coordinate
     */
    public float getY() {
        return y;
    }

    /**
     * Sets the y-coordinate of the component.
     *
     * @param y the new y-coordinate
     */
    public void setY(float y) {
        yProperty().set(y);
    }

    /**
     * Gets the width of the component.
     *
     * @return the width
     */
    public float getWidth() {
        return width;
    }

    /**
     * Sets the width of the component.
     *
     * @param width the new width
     */
    public void setWidth(float width) {
        // Single source of truth: the observable property applies the value,
        // marks the bounds as explicit, syncs inline width and invalidates.
        widthProperty().set(width);
    }

    /**
     * Gets the height of the component.
     *
     * @return the height
     */
    public float getHeight() {
        return height;
    }

    /**
     * Sets the height of the component.
     *
     * @param height the new height
     */
    public void setHeight(float height) {
        // Single source of truth: the observable property applies the value,
        // marks the bounds as explicit, syncs inline height and invalidates.
        heightProperty().set(height);
    }

    /**
     * Applies a size computed by a layout manager without marking the bounds as
     * explicitly user-set. This keeps {@link #isSizeExplicitlySet()} false so a
     * subsequent layout pass can still resize the component to its preferred
     * size.
     *
     * @param width the layout-assigned width
     * @param height the layout-assigned height
     */
    public void applyLayoutSize(float width, float height) {
        boolean changed = false;
        if (this.width != width) {
            this.width = width;
            changed = true;
        }
        if (this.height != height) {
            this.height = height;
            changed = true;
        }
        if (changed) {
            // Layout-assigned bounds: repaint only. Requesting a relayout
            // here would make the layout pass dirty its own input and loop.
            invalidatePaint();
        }
    }

    /**
     * Checks whether the component's size was provided explicitly by the user
     * (via a bounds-carrying constructor or
     * {@link #setWidth}/{@link #setHeight}) rather than left for layout
     * managers to decide.
     *
     * @return true if the size must be respected by layout managers
     */
    public boolean isSizeExplicitlySet() {
        return sizeExplicitlySet;
    }

    /**
     * Whether this component instance was <em>constructed</em> with explicit
     * bounds (as opposed to having its size assigned later by a layout
     * manager or by {@code setWidth}/{@code setHeight}). Package-private
     * instrumentation used by {@code Panel.measure} to keep content-based
     * sizing working for panels that an outer layout resizes.
     *
     * @return true when the bounds-carrying constructor pinned this size
     */
    boolean isCtorSizeExplicit() {
        return ctorSizeExplicit;
    }

    /**
     * Keeps the {@code width}/{@code height} CSS properties in sync with
     * programmatic size setters: an explicit {@code setWidth}/{@code
     * setHeight} call is recorded as a highest-priority inline style value,
     * mirroring how inline styles override stylesheet rules. Called from
     * {@link #setWidth(float)} and {@link #setHeight(float)}; no-op when no
     * inline style exists yet (the plain fields remain authoritative).
     */
    private void applyExplicitSize() {
        if (!sizeExplicitlySet) {
            return;
        }
        if (inlineStyle == null || inlineStyle.isEmpty()) {
            // First explicit size with no inline declarations: create the
            // inline style so programmatic sizes win over stylesheet rules,
            // as documented above.
            inlineStyle = io.github.yasmramos.glyphui.style.Style.builder()
                    .length(io.github.yasmramos.glyphui.style.StyleProperty.WIDTH, width)
                    .length(io.github.yasmramos.glyphui.style.StyleProperty.HEIGHT, height)
                    .build();
            return;
        }
        float curW = inlineStyle.getFloat(io.github.yasmramos.glyphui.style.StyleProperty.WIDTH, Float.NaN);
        float curH = inlineStyle.getFloat(io.github.yasmramos.glyphui.style.StyleProperty.HEIGHT, Float.NaN);
        if (curW == width && curH == height) {
            // Nothing changed: skip the full Builder rebuild (and the repaint
            // storm a fresh inline-style object would trigger downstream).
            return;
        }
        io.github.yasmramos.glyphui.style.Style.Builder b = io.github.yasmramos.glyphui.style.Style.builder()
                .putAll(inlineStyle);
        b.length(io.github.yasmramos.glyphui.style.StyleProperty.WIDTH, width);
        b.length(io.github.yasmramos.glyphui.style.StyleProperty.HEIGHT, height);
        inlineStyle = b.build();
    }

    /**
     * Checks if the component is visible.
     *
     * @return true if visible
     */
    public boolean isVisible() {
        return visible;
    }

    /**
     * Sets the visibility of the component. Hiding a focused component clears
     * its focus (see {@link io.github.yasmramos.glyphui.core.FocusManager}).
     *
     * @param visible true to make visible, false to hide
     */
    public void setVisible(boolean visible) {
        visibleProperty().set(visible);
    }

    /**
     * Checks if the component is enabled.
     *
     * @return true if enabled
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the component is enabled. Disabling a focused component
     * clears its focus (see
     * {@link io.github.yasmramos.glyphui.core.FocusManager}).
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        enabledProperty().set(enabled);
    }

    /**
     * Gets the parent panel of this component.
     *
     * @return the parent panel
     */
    public Panel getParent() {
        return parent;
    }

    /**
     * Sets the parent panel of this component.
     *
     * <p>
     * This only rewires the back-pointer; it does not add or remove the
     * component in any child list and does not trigger a relayout/repaint.
     * Containers own tree membership — prefer {@link Panel#add(Component)} and
     * {@link Panel#remove(Component)}, which call this method as part of a
     * fully consistent reparenting.</p>
     *
     * @param parent the parent panel
     */
    public void setParent(Panel parent) {
        this.parent = parent;
    }

    /**
     * Gets the unique identifier of this component.
     *
     * @return the component ID
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the unique identifier of this component.
     *
     * @param id the new ID
     */
    public void setId(String id) {
        this.id = id;
        // ID selectors match on this value: re-cascade on the next frame.
        invalidateStyleAndRelayout();
    }

    /**
     * Gets the current state of the component.
     *
     * @return the component state
     */
    public ComponentState getState() {
        return state;
    }

    /**
     * Sets the current state of the component.
     *
     * @param state the new state
     */
    public void setState(ComponentState state) {
        if (this.state != state) {
            this.state = state;
            // Pseudo-class rules (:hover, :focus, :disabled, :active) depend
            // on this value: force a cascade pass plus relayout on the next
            // frame, since :hover/:active rules may carry lengths.
            invalidateStyleAndRelayout();
        }
    }

    /**
     * Checks if a point is within the bounds of this component.
     *
     * <p>
     * Bounds are half-open: the left/top edge is inclusive, the right/bottom
     * edge exclusive. Adjacent components therefore do not both claim a point
     * that lies exactly on their shared border.</p>
     *
     * @param mouseX the x-coordinate to check
     * @param mouseY the y-coordinate to check
     * @return true if the point is inside the component
     */
    public boolean contains(int mouseX, int mouseY) {
        return contains((float) mouseX, (float) mouseY);
    }

    /**
     * Float-precision hit test; see {@link #contains(int, int)} for the
     * half-open bounds semantics. Preferred when coordinates come from a HiDPI
     * conversion or fractional layout.
     *
     * @param px the x-coordinate to check
     * @param py the y-coordinate to check
     * @return true if the point is inside the component
     */
    public boolean contains(float px, float py) {
        return px >= x && px < x + width && py >= y && py < y + height;
    }

    /**
     * Returns the deepest visible component covering the given point, or
     * {@code null} when the point is outside this component.
     *
     * <p>The point is in this component's parent coordinate space (the same
     * space {@link #contains} uses). Containers such as {@link Panel} override
     * this to descend into their children, so the result is the leaf widget
     * under the cursor. Used by the application to track hover: when the
     * pointer moves onto a different component, the previous one must have its
     * hover state cleared even though it will not receive the move event.</p>
     *
     * @param px the x-coordinate to check
     * @param py the y-coordinate to check
     * @return the deepest component at the point, or null if none
     */
    public Component getComponentAt(float px, float py) {
        return (visible && contains(px, py)) ? this : null;
    }

    /**
     * Renders the component on the canvas. Called by the framework (see
     * {@link Panel#render(Canvas)}); widget authors implement this, but
     * application code must not invoke it directly — invalidate instead and
     * let the next frame repaint.
     *
     * @param canvas the canvas to draw on
     */
    protected abstract void render(Canvas canvas);

    /**
     * Framework entry point for painting this component. Delegates to the
     * subclass {@link #render(Canvas)} implementation; call sites (root panel
     * painting, container child loops) use this instead so widget authors
     * never see a public render on their API surface.
     *
     * @param canvas the canvas to draw on
     */
    public final void renderInternal(Canvas canvas) {
        render(canvas);
    }

    /**
     * Measures the intrinsic size of this component under the given
     * constraints, in logical units.
     *
     * <p>
     * The default implementation returns the preferred size (see
     * {@link #getPreferredWidth()} / {@link #getPreferredHeight()}) clamped to
     * the supplied maximums. Text widgets such as {@code Button} and
     * {@code Label} override this to measure their text with the theme font
     * plus padding.</p>
     *
     * @param maxWidth the maximum available width (use
     * {@link Float#POSITIVE_INFINITY} for unbounded)
     * @param maxHeight the maximum available height (use
     * {@link Float#POSITIVE_INFINITY} for unbounded)
     * @return the measured dimension, never negative
     */
    public Dimension measure(float maxWidth, float maxHeight) {
        return measureBase(maxWidth, maxHeight);
    }

    /**
     * Base measurement logic shared by every component: explicit CSS bounds
     * (with percentage resolution against the parent content box) win over
     * the intrinsic preferred size, which is clamped to the constraints.
     * Subclasses that customize measurement override {@link #measure} and can
     * call this through {@code super.measure(...)}; layout managers reach it
     * via {@link #measureContent}.
     *
     * @param maxWidth the maximum available width
     * @param maxHeight the maximum available height
     * @return the measured dimension
     */
    final Dimension measureBase(float maxWidth, float maxHeight) {
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        if (s != null) {
            // Explicit CSS width/height win over the intrinsic preferred size.
            // Percentage declarations arrive as negative fractions (see
            // CssValues.parseLength) and are resolved against the parent's
            // content box; px values pass through unchanged.
            float explicitW = resolveCssLength(s,
                    io.github.yasmramos.glyphui.style.StyleProperty.WIDTH,
                    parentAvailableWidth());
            float explicitH = resolveCssLength(s,
                    io.github.yasmramos.glyphui.style.StyleProperty.HEIGHT,
                    parentAvailableHeight());
            if (explicitW >= 0f || explicitH >= 0f) {
                float w = explicitW >= 0f ? explicitW : getPreferredWidth();
                float h = explicitH >= 0f ? explicitH : getPreferredHeight();
                return new Dimension(Math.min(w, sanitize(maxWidth)),
                        Math.min(h, sanitize(maxHeight)));
            }
        }
        float w = Math.min(getPreferredWidth(), sanitize(maxWidth));
        float h = Math.min(getPreferredHeight(), sanitize(maxHeight));
        return new Dimension(w, h);
    }

    /**
     * Reads a length style property and resolves percentage-encoded values
     * (negative fractions produced by
     * {@link io.github.yasmramos.glyphui.style.CssValues#parseLength(String)})
     * against the given reference size. Plain pixel values pass through
     * unchanged; an absent property yields {@code -1f} ("not set"), matching
     * the sentinel used by {@link #measure(float, float)}.
     *
     * @param s         the computed style to read from
     * @param property  the length property (WIDTH or HEIGHT)
     * @param available the reference size for percentages (parent content box)
     * @return resolved pixels, or -1f when the property is not declared
     */
    private static float resolveCssLength(io.github.yasmramos.glyphui.style.Style s,
            io.github.yasmramos.glyphui.style.StyleProperty property, float available) {
        if (!s.has(property)) {
            return -1f;
        }
        // CssValues is package-private; percentages use the negative-sentinel
        // encoding documented in CssValues.parseLength, so resolve inline.
        float parsed = s.getFloat(property, -1f);
        if (parsed < 0f) {
            // Percentage sentinel: magnitude is the fraction of `available`.
            return Math.max(0f, -parsed * Math.max(0f, available));
        }
        return parsed;
    }

    /**
     * Plain component measurement path, bypassing any layout manager. Used by
     * {@link io.github.yasmramos.glyphui.layout.LayoutManager#measure} default
     * implementations so a panel can be sized exactly like a leaf component
     * (explicit CSS bounds or the preferred-size defaults) even when
     * {@code Panel.measure} delegates to its layout manager.
     *
     * @param maxWidth  maximum available width
     * @param maxHeight maximum available height
     * @return the measured dimension
     */
    public final Dimension measureContent(float maxWidth, float maxHeight) {
        // Virtual dispatch: when this component is a Panel whose measure()
        // delegates to its layout manager, children must see the content
        // size (not the raw 150x40 defaults). When invoked on a plain
        // Component (or a panel with an explicit/CSS size, where
        // Panel.measure already takes the super path) this is identical to
        // the base measurement.
        return measure(maxWidth, maxHeight);
    }

    /**
     * Bridge to the base {@link Component#measure(float, float)} for subclasses
     * that override {@code measure} and still need the plain behavior (e.g.
     * {@code Panel} delegating to its layout manager). Package-private on
     * purpose: application code should call {@code measure}.
     *
     * @param maxWidth  maximum available width
     * @param maxHeight maximum available height
     * @return the measured dimension
     */
    Dimension superMeasure(float maxWidth, float maxHeight) {
        return measureBase(maxWidth, maxHeight);
    }

    /**
     * Reference width for resolving percentage lengths: the parent's border-
     * box width minus its declared horizontal padding, or unbounded when the
     * component is detached. Mirrors the CSS containing-block rule where a
     * percentage resolves against the parent content box.
     *
     * @return available content width in logical pixels, never negative
     */
    private float parentAvailableWidth() {
        return parentContentSize(true);
    }

    /**
     * Height counterpart of {@link #parentAvailableWidth()}.
     *
     * @return available content height in logical pixels, never negative
     */
    private float parentAvailableHeight() {
        return parentContentSize(false);
    }

    /**
     * Shared implementation for the percentage reference size helpers:
     * subtracts twice the parent's computed {@code padding} from the requested
     * dimension. Returns {@link Float#POSITIVE_INFINITY} when there is no
     * parent (a detached component has no containing block).
     *
     * @param widthAxis true for the horizontal axis, false for vertical
     * @return the parent content size on that axis, never negative
     */
    private float parentContentSize(boolean widthAxis) {
        Panel p = getParent();
        if (p == null) {
            return Float.POSITIVE_INFINITY;
        }
        io.github.yasmramos.glyphui.style.Style ps = p.getComputedStyle();
        float pad = ps != null
                ? ps.getFloat(io.github.yasmramos.glyphui.style.StyleProperty.PADDING, 0f)
                : 0f;
        float size = widthAxis ? p.getWidth() : p.getHeight();
        return Math.max(0f, size - 2f * pad);
    }

    /**
     * Treats NaN/infinite constraints as "unbounded" so the clamp above still
     * yields the preferred size.
     *
     * @param value the raw constraint
     * @return a finite upper bound
     */
    private static float sanitize(float value) {
        if (Float.isNaN(value) || value == Float.POSITIVE_INFINITY) {
            return Float.MAX_VALUE;
        }
        return Math.max(0.0f, value);
    }

    // ------------------------------------------------------------------
    // Focus support
    // ------------------------------------------------------------------
    /**
     * Checks whether this component participates in keyboard focus traversal.
     *
     * @return true if focusable
     */
    public boolean isFocusable() {
        return focusable;
    }

    /**
     * Sets whether this component participates in keyboard focus traversal.
     * Making a focused component unfocusable clears its focus.
     *
     * @param focusable true to allow focus
     */
    public void setFocusable(boolean focusable) {
        if (this.focusable == focusable) {
            // No-op: avoid the double invalidation that would otherwise run
            // through clearFocus() plus the trailing invalidate().
            return;
        }
        this.focusable = focusable;
        if (!focusable && focused) {
            io.github.yasmramos.glyphui.core.FocusManager fm = io.github.yasmramos.glyphui.core.FocusManager.getGlobalFocusManager();
            if (fm != null) {
                // clearFocus already invalidates the component.
                fm.clearFocus(this);
                return;
            }
            setFocusedInternal(false); // also invalidates
            return;
        }
        invalidate();
    }

    /**
     * Checks whether this component currently holds the keyboard focus.
     *
     * @return true if focused
     */
    public boolean isFocused() {
        return focused;
    }

    /**
     * Requests the keyboard focus for this component through the global
     * {@link io.github.yasmramos.glyphui.core.FocusManager}. No-op when the
     * component is not focusable or not visible/enabled.
     */
    public void requestFocus() {
        io.github.yasmramos.glyphui.core.FocusManager fm = io.github.yasmramos.glyphui.core.FocusManager.getGlobalFocusManager();
        if (fm != null) {
            fm.requestFocus(this);
        }
    }

    /**
     * Internal focus flag setter used by
     * {@link io.github.yasmramos.glyphui.core.FocusManager} and unit tests.
     * Application code should use {@link #requestFocus()} or the focus manager
     * instead.
     *
     * <p>Package-private by design: {@code FocusManager} reaches it through
     * the {@link #applyFocusChange(boolean)} bridge declared in this class.</p>
     *
     * @param focused the new focus state
     */
    void setFocusedInternal(boolean focused) {
        if (this.focused == focused) {
            return;
        }
        this.focused = focused;
        if (focused) {
            onFocusGained();
        } else {
            onFocusLost();
        }
        invalidate();
    }

    /**
     * Internal focus-flag entry point for {@link io.github.yasmramos.glyphui.core.FocusManager}
     * and test harnesses. This is the <em>only</em> non-public-package way to
     * drive the focus flag; it is intentionally {@code public} because
     * {@code FocusManager} lives in a different package, but it is NOT part of
     * the frozen application-facing API (see
     * {@code docs/architecture/api-stability.md}). Application code should use
     * {@link #requestFocus()} instead.
     *
     * @param focused the new internal focus state
     */
    public final void applyFocusChange(boolean focused) {
        setFocusedInternal(focused);
    }

    /**
     * Called when this component gains the keyboard focus. The default
     * implementation does nothing; subclasses may override.
     */
    protected void onFocusGained() {
    }

    /**
     * Called when this component loses the keyboard focus. The default
     * implementation does nothing; subclasses may override.
     */
    protected void onFocusLost() {
    }

    // ------------------------------------------------------------------
    // Accessibility stubs (v0.1 API only — no platform bridge yet)
    // ------------------------------------------------------------------
    /**
     * Gets the accessibility role of this component. Subclasses override this
     * to return a more specific role (e.g. {@link AccessibleRole#BUTTON}).
     *
     * @return the accessible role (defaults to {@link AccessibleRole#GENERIC})
     */
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.GENERIC;
    }

    /**
     * Gets the accessibility name of this component. When not explicitly set
     * via {@link #setAccessibleName(String)}, the default is a human-readable
     * label derived from the widget itself (text widgets override this); the
     * base fallback here is the style tag (e.g. {@code "button"},
     * {@code "label"}) rather than the auto-generated id, which was opaque
     * noise for screen readers.
     *
     * @return the accessible name, never null
     */
    public String getAccessibleName() {
        return accessibleName != null ? accessibleName : getStyleTag();
    }

    /**
     * Overrides the accessibility name.
     *
     * @param accessibleName the new name (null restores the default)
     */
    public void setAccessibleName(String accessibleName) {
        this.accessibleName = accessibleName;
    }

    /**
     * The explicit accessibility-name override, or null when the widget should
     * derive its own readable default (caption, placeholder, style tag).
     * Lets subclasses distinguish "user set a name" from "base fallback in
     * effect" without string-comparing against the auto-generated id.
     *
     * @return the override or null
     */
    protected final String accessibleNameOverride() {
        return accessibleName;
    }

    /**
     * Gets the accessibility description of this component. Defaults to the
     * role name unless overridden via
     * {@link #setAccessibleDescription(String)}.
     *
     * @return the accessible description
     */
    public String getAccessibleDescription() {
        return accessibleDescription != null ? accessibleDescription : getAccessibleRole().name();
    }

    /**
     * Overrides the accessibility description.
     *
     * @param accessibleDescription the new description (null restores the
     * default)
     */
    public void setAccessibleDescription(String accessibleDescription) {
        this.accessibleDescription = accessibleDescription;
    }

    // ------------------------------------------------------------------
    // Theme access
    // ------------------------------------------------------------------
    /**
     * Gets the theme used by this component. Components read colors, fonts,
     * radii and paddings from the current theme at render/measure time.
     *
     * @return the current theme (never null)
     */
    protected Theme getTheme() {
        return Theme.current();
    }

    /**
     * Draws the standard focus ring for this component using the theme's accent
     * color: a dashed rounded rectangle inset by one logical pixel. Widgets
     * that accept focus ({@code Button}, {@code TextField}, ...) call this from
     * {@code render} when {@link #isFocused()} is true.
     *
     * @param canvas the canvas to draw on
     */
    protected void renderFocusRing(Canvas canvas) {
        io.github.humbleui.skija.Paint paint = new io.github.humbleui.skija.Paint();
        try {
            paint.setColor(getTheme().getAccentColor());
            paint.setStroke(true);
            paint.setStrokeWidth(1.5f);
            paint.setAntiAlias(true);
            // The dash effect is an immutable native object shared by every
            // focus ring in the process; it must never be closed here (or by
            // Paint.close(), which does not own its PathEffect).
            paint.setPathEffect(FOCUS_DASH_EFFECT);
            canvas.drawRRect(x + 1.0f, y + 1.0f, Math.max(0.0f, width - 2.0f),
                    Math.max(0.0f, height - 2.0f),
                    getTheme().getFocusRingRadius(), getTheme().getFocusRingRadius(), paint);
        } finally {
            paint.close();
        }
    }

    /**
     * Shared dashed path effect for focus rings (created once, never closed
     * per-frame).
     */
    private static final io.github.humbleui.skija.PathEffect FOCUS_DASH_EFFECT
            = io.github.humbleui.skija.PathEffect.makeDash(new float[]{4.0f, 3.0f}, 0.0f);

    /**
     * Handles mouse events.
     *
     * @param event the mouse event
     * @return {@code true} if the event was consumed and propagation to other
     * components should stop; {@code false} otherwise
     */
    public abstract boolean onMouseEvent(MouseEvent event);

    /**
     * Handles key events.
     *
     * @param event the key event
     */
    public abstract void onKeyEvent(KeyEvent event);

    /**
     * Registers a resource whose lifetime is tied to this component, typically
     * a {@code Binding}, a {@code Computed} or a subscription. Tracked
     * resources are closed by {@link #dispose()}, in registration order, after
     * the children have been disposed. If the component is already disposed the
     * resource is closed immediately, so late registration cannot leak.
     *
     * @param resource the resource to close on dispose
     * @param <C> the resource type
     * @return {@code resource}, for chaining
     */
    public <C extends AutoCloseable> C track(C resource) {
        if (resources == null) {
            resources = new DisposableScope();
        }
        return resources.add(resource);
    }

    /**
     * Releases everything this component owns. Idempotent: only the first call
     * does anything.
     *
     * <p>
     * The order is fixed: first {@link #disposeChildren()} (children are
     * disposed before their parent releases anything), then the resources
     * registered with {@link #track(AutoCloseable)} in registration order, and
     * finally {@link #onDispose()} for native resources. A failure in one step
     * does not skip the others; the first failure is rethrown at the end with
     * the rest attached as suppressed exceptions.</p>
     *
     * <p>
     * Subclasses do not override this method: they release native resources in
     * {@link #onDispose()} and containers dispose their children in
     * {@link #disposeChildren()}.</p>
     */
    @Override
    public final void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        RuntimeException failure = null;
        try {
            disposeChildren();
        } catch (RuntimeException e) {
            failure = e;
        }
        if (resources == null) {
            resources = new DisposableScope();
        }
        try {
            resources.dispose();
        } catch (RuntimeException e) {
            failure = accumulate(failure, e);
        }
        try {
            onDispose();
        } catch (RuntimeException e) {
            failure = accumulate(failure, e);
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Tells whether {@link #dispose()} has been called.
     *
     * @return true once disposed
     */
    @Override
    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Hook for containers: dispose the children here. Called first by
     * {@link #dispose()}. The default implementation does nothing.
     */
    protected void disposeChildren() {
        // Leaf components have no children
    }

    /**
     * Hook for subclasses that own native resources (paints, ...): release them
     * here. Called last by {@link #dispose()}, at most once. The default
     * implementation does nothing.
     */
    protected void onDispose() {
        // Default implementation does nothing
    }

    private static RuntimeException accumulate(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    /**
     * Releases resources held by this component by delegating to
     * {@link #dispose()}. Provided so components can be used with
     * try-with-resources statements.
     */
    @Override
    public void close() {
        dispose();
    }

    /**
     * Gets the preferred width of this component. The base implementation
     * returns the user-provided width when bounds were set explicitly,
     * otherwise a sensible default so layout managers can place the component
     * without measuring content.
     *
     * @return the preferred width
     */
    public float getPreferredWidth() {
        // Block 5 item 4.3 follow-up: an inline `width` declaration (fed by a
        // CSS rule or by setWidth through applyExplicitSize) counts as an
        // explicit size too, so getPreferredWidth and
        // getComputedStyle().getFloat(WIDTH) never disagree.
        Style s = getComputedStyle();
        if (s != null && s.has(StyleProperty.WIDTH)) {
            return s.getFloat(StyleProperty.WIDTH, width);
        }
        return sizeExplicitlySet ? width : DEFAULT_WIDTH;
    }

    /**
     * Gets the preferred height of this component. Subclasses can override this
     * to provide content-based sizing.
     *
     * @return the preferred height
     */
    public float getPreferredHeight() {
        // Block 5 item 4.3 follow-up: mirrors getPreferredWidth for the
        // inline `height` declaration.
        Style s = getComputedStyle();
        if (s != null && s.has(StyleProperty.HEIGHT)) {
            return s.getFloat(StyleProperty.HEIGHT, height);
        }
        return sizeExplicitlySet ? height : DEFAULT_HEIGHT;
    }
}
