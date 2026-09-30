package com.glyphui.ui;

import com.glyphui.core.Application;
import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.Property;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;
import com.glyphui.style.Style;
import com.glyphui.style.StyleNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Abstract base class for all UI components.
 *
 * <p>Components implement {@link StyleNode} so the style subsystem
 * ({@code StyleSheet}/{@code StyleEngine}) can match CSS selectors against
 * the live widget tree.</p>
 *
 * <p>Components that own native resources (paints, fonts) should release them
 * in {@link #dispose()}. {@code Component} implements {@link AutoCloseable} so
 * widgets can also be used with try-with-resources; {@link #close()} simply
 * delegates to {@link #dispose()}.</p>
 *
 * <h2>Threading</h2>
 * <p>The geometric/visibility state is exposed as observable
 * {@link Property} objects ({@link #xProperty()}, {@link #yProperty()},
 * {@link #widthProperty()}, {@link #heightProperty()},
 * {@link #visibleProperty()}, {@link #enabledProperty()}). Calling
 * {@code Property.set(...)} from a background thread marshals the change onto
 * the UI thread automatically; the classic setters ({@link #setX(float)}, etc.)
 * delegate to those properties and are therefore equally safe.</p>
 */
public abstract class Component implements StyleNode, AutoCloseable {

    /** Monotonic counter used to generate unique component IDs. */
    private static final java.util.concurrent.atomic.AtomicLong ID_COUNTER =
            new java.util.concurrent.atomic.AtomicLong();


    /**
     * Callback used to notify the application that a component's visual state
     * changed and a repaint should be scheduled (on-demand rendering).
     * The default implementation is a no-op so components work standalone
     * (e.g. in unit tests) without an application attached.
     */
    public interface RepaintRequester {
        void requestRepaint();
    }

    private static volatile RepaintRequester repaintRequester = () -> { };

    /**
     * Registers the global repaint requester, typically called by the
     * {@code Application} during initialization.
     *
     * @param requester the callback to invoke on visual mutations, or null to reset to a no-op
     */
    public static void setRepaintRequester(RepaintRequester requester) {
        repaintRequester = (requester != null) ? requester : () -> { };
    }

    /**
     * Notifies the registered repaint requester that this component needs repainting.
     * Subclasses and containers should call this after any mutation that affects
     * how the component is drawn.
     */
    protected void requestRepaint() {
        repaintRequester.requestRepaint();
    }

    /** Default width used when no explicit bounds were provided and layout is free to decide. */
    public static final float DEFAULT_WIDTH = 150f;
    /** Default height used when no explicit bounds were provided and layout is free to decide. */
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
     * through a bounds-carrying constructor or {@link #setWidth}/{@link #setHeight}.
     * When false, layout managers are free to resize the component to its
     * preferred size; when true, the user-provided size must be respected.
     */
    protected boolean sizeExplicitlySet;

    // --- Observable properties (lazily created, marshalled via Application.invokeLater) ---

    private Property<Float> xProperty;
    private Property<Float> yProperty;
    private Property<Float> widthProperty;
    private Property<Float> heightProperty;
    private Property<Boolean> visibleProperty;
    private Property<Boolean> enabledProperty;

    /** Applies a user-set width: marks bounds as explicit, repaints and invalidates. */
    private void applyWidthExplicit(float newWidth) {
        this.width = newWidth;
        this.sizeExplicitlySet = true;
        requestRepaint();
        applyExplicitSize();
        invalidate();
    }

    /** Applies a user-set height: marks bounds as explicit, repaints and invalidates. */
    private void applyHeightExplicit(float newHeight) {
        this.height = newHeight;
        this.sizeExplicitlySet = true;
        requestRepaint();
        applyExplicitSize();
        invalidate();
    }

    /**
     * Returns the observable x-coordinate property. Setting it from a
     * background thread marshals the change onto the UI thread.
     *
     * @return the x property (never null after first access)
     */
    public Property<Float> xProperty() {
        if (xProperty == null) {
            xProperty = new Property<>(Application.getCurrent(), x, v -> { this.x = v; requestRepaint(); });
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
            yProperty = new Property<>(Application.getCurrent(), y, v -> { this.y = v; requestRepaint(); });
        }
        return yProperty;
    }

    /**
     * Returns the observable width property. Changes applied through the
     * property do not mark the bounds as explicitly user-set; use
     * {@link #setWidth(float)} for that.
     *
     * @return the width property (never null after first access)
     */
    public Property<Float> widthProperty() {
        if (widthProperty == null) {
            widthProperty = new Property<>(Application.getCurrent(), width, this::applyWidthExplicit);
        }
        return widthProperty;
    }

    /**
     * Returns the observable height property. Changes applied through the
     * property do not mark the bounds as explicitly user-set; use
     * {@link #setHeight(float)} for that.
     *
     * @return the height property (never null after first access)
     */
    public Property<Float> heightProperty() {
        if (heightProperty == null) {
            heightProperty = new Property<>(Application.getCurrent(), height, this::applyHeightExplicit);
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
            visibleProperty = new Property<>(Application.getCurrent(), visible, v -> {
                if (this.visible != v) {
                    this.visible = v;
                    requestRepaint();
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
            enabledProperty = new Property<>(Application.getCurrent(), enabled, v -> {
                if (this.enabled != v) {
                    this.enabled = v;
                    requestRepaint();
                }
            });
        }
        return enabledProperty;
    }

    /**
     * Creates a new Component with default (unset) bounds.
     * The resulting component has zero geometry until a layout manager assigns
     * a preferred size or the user sets bounds explicitly.
     */
    public Component() {
        this(0f, 0f, 0f, 0f);
        this.sizeExplicitlySet = false;
    }

    /** Whether this component participates in keyboard focus traversal. */
    private boolean focusable = true;

    /** Whether this component currently holds the keyboard focus. */
    private boolean focused;

    /**
     * Optional explicit accessibility name override (see
     * {@link #getAccessibleName()}). Null means "derive a default".
     */
    private String accessibleName;

    /** Optional explicit accessibility description override. */
    private String accessibleDescription;

    /**
     * Global repaint hook. When set (typically by {@code Application}), any
     * component mutation that calls {@link #invalidate()} propagates up the
     * parent chain until it reaches this requester, marking the application
     * paint-dirty. Kept as a static so plain components work even when no
     * Application is running (e.g. unit tests).
     */
    private static com.glyphui.core.RepaintRequester globalRepaintRequester;

    /** Number of times invalidate() has been called on this component. */
    private int invalidateCount;

    // ------------------------------------------------------------------
    // Style subsystem hooks (CSS subset)
    // ------------------------------------------------------------------

    /** Style class names carried by this component (like HTML {@code class}). */
    private final List<String> styleClasses = new ArrayList<>();

    /** Inline style declarations (like HTML {@code style="..."}). Highest cascade priority. */
    private Style inlineStyle = Style.EMPTY;

    /** The last computed style produced by the {@code StyleEngine} for this component. */
    private Style computedStyle = Style.EMPTY;

    /** Explicit HTML-equivalent tag override; null means use {@link #defaultStyleTag()}. */
    private String styleTagOverride;

    /**
     * Installs the global repaint requester used to propagate invalidations
     * that reach the root of the component tree.
     *
     * @param requester the repaint requester (may be null to clear)
     */
    public static void setGlobalRepaintRequester(com.glyphui.core.RepaintRequester requester) {
        globalRepaintRequester = requester;
    }

    /**
     * Gets the currently installed global repaint requester.
     *
     * @return the requester or null
     */
    public static com.glyphui.core.RepaintRequester getGlobalRepaintRequester() {
        return globalRepaintRequester;
    }

    /**
     * Creates a new Component.
     *
     * @param x      the x-coordinate of the component
     * @param y      the y-coordinate of the component
     * @param width  the width of the component
     * @param height the height of the component
     */
    /**
     * Creates a zero-sized component to be positioned/sized by a layout
     * manager or by {@link #setSize}. Subclasses such as {@code TextField}
     * and {@code ImageView} use this when their size is derived from
     * content via {@link #measure}.
     */
    protected Component() {
        this(0, 0, 0, 0);
    }

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
    }

    /**
     * Marks this component as needing a repaint and propagates the
     * invalidation up the parent chain. When the top of the tree is reached,
     * the global {@link com.glyphui.core.RepaintRequester} (installed by
     * {@code Application}) is invoked, which sets the application's
     * {@code paintDirty} flag so the next frame is rendered.
     *
     * <p>All visual mutators ({@code setText}, {@code setWidth},
     * {@code setVisible}, ...) call this automatically.</p>
     */
    public void invalidate() {
        invalidateCount++;
        Panel p = parent;
        if (p != null) {
            // Propagate upward through the tree until the root is reached
            p.invalidate();
        } else {
            // Root of the tree: ask the application to repaint
            com.glyphui.core.RepaintRequester requester = globalRepaintRequester;
            if (requester != null) {
                requester.requestRepaint();
            }
        }
    }

    /**
     * Gets how many times this component has been invalidated. Useful for
     * tests and diagnostics.
     *
     * @return the invalidate count
     */
    public int getInvalidateCount() {
        return invalidateCount;
    }

    /**
     * Resets the invalidate counter back to zero. Intended for tests and
     * diagnostics.
     */
    public void resetInvalidateCount() {
        invalidateCount = 0;
    }

    // ------------------------------------------------------------------
    // Style API (CSS subset integration)
    // ------------------------------------------------------------------

    /**
     * The default HTML-equivalent tag name for this component class, used by
     * CSS type selectors. Subclasses override it (Button → "button",
     * Label → "label", TextField → "input", ...). Panels return "div".
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
     * Overrides the HTML-equivalent tag for this instance (used by the
     * markup loader for unknown tags and by tests).
     *
     * @param styleTag the tag name (null restores the class default)
     */
    public void setStyleTag(String styleTag) {
        this.styleTagOverride = styleTag == null
                ? null : styleTag.toLowerCase(Locale.ROOT);
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
            invalidate();
        }
    }

    /**
     * Removes a style class from this component.
     *
     * @param className the class name without the leading dot
     */
    public void removeStyleClass(String className) {
        if (styleClasses.remove(className)) {
            invalidate();
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
     * Sets the inline style (equivalent of {@code style="..."} in HTML).
     * Inline declarations have the highest cascade priority.
     *
     * @param style the inline style (null clears it)
     */
    public void setInlineStyle(Style style) {
        this.inlineStyle = style == null ? Style.EMPTY : style;
        invalidate();
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
        // Inline style has the highest cascade priority. overrideWith() also
        // strips the synthetic RAW_VALUES var() carrier from the computed
        // style before returning it to callers.
        return inlineStyle.isEmpty()
                ? computedStyle.withoutRawValues()
                : computedStyle.overrideWith(inlineStyle);
    }

    /**
     * Stores the computed style. Called by the style engine; not part of the
     * public widget API.
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
     * @param property       the style property
     * @param explicitOverride the per-instance setter value (may be null)
     * @param themeDefault   fallback taken from the active theme
     * @return the winning value
     */
    protected int resolveIntStyle(com.glyphui.style.StyleProperty property,
                                  Integer explicitOverride, int themeDefault) {
        com.glyphui.style.Style s = getComputedStyle();
        if (s.has(property)) {
            return s.getInt(property, themeDefault);
        }
        return explicitOverride != null ? explicitOverride : themeDefault;
    }

    /**
     * Float variant of {@link #resolveIntStyle}.
     *
     * @param property         the style property
     * @param explicitOverride per-instance setter value (may be null)
     * @param themeDefault     fallback from the theme
     * @return the winning value
     */
    protected float resolveFloatStyle(com.glyphui.style.StyleProperty property,
                                      Float explicitOverride, float themeDefault) {
        com.glyphui.style.Style s = getComputedStyle();
        if (s.has(property)) {
            return s.getFloat(property, themeDefault);
        }
        return explicitOverride != null ? explicitOverride : themeDefault;
    }

    /**
     * Resolves the Skia font for this component: when the computed style
     * declares {@code font-family} and/or {@code font-size}, a font is built
     * through {@link com.glyphui.graphics.FontManager}'s typeface resolution
     * (cached per family/size); otherwise the theme font for the given role
     * is returned.
     *
     * <p>The returned font is owned by the caches below or by
     * {@code FontManager} — callers must not close it.</p>
     *
     * @param role the theme font role to fall back to
     * @return the resolved font, never null
     */
    protected io.github.humbleui.skija.Font resolveFont(Theme.FontRole role) {
        com.glyphui.style.Style s = getComputedStyle();
        boolean hasFamily = s.has(com.glyphui.style.StyleProperty.FONT_FAMILY);
        boolean hasSize = s.has(com.glyphui.style.StyleProperty.FONT_SIZE);
        if (!hasFamily && !hasSize) {
            Theme theme = getTheme();
            return theme != null ? theme.getFont(role) : com.glyphui.graphics.FontManager.getFont(role);
        }
        String family = hasFamily
                ? s.getString(com.glyphui.style.StyleProperty.FONT_FAMILY, null)
                : null;
        float size = hasSize
                ? s.getFloat(com.glyphui.style.StyleProperty.FONT_SIZE, 0f)
                : com.glyphui.graphics.FontManager.getDefaultFontSize(role);
        if (size <= 0f || Float.isNaN(size)) {
            size = com.glyphui.graphics.FontManager.getDefaultFontSize(role);
        }
        int weight = s.getInt(com.glyphui.style.StyleProperty.FONT_WEIGHT, 400);
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
                ? com.glyphui.graphics.FontManager.resolveTypeface(family, style)
                : com.glyphui.graphics.FontManager.resolveTypeface(null, style);
        io.github.humbleui.skija.Font font = new io.github.humbleui.skija.Font(face, size);
        STYLE_FONTS.put(key, font);
        return font;
    }

    /** Cache of fonts created from CSS font-* declarations (keyed family|size|style). */
    private static final java.util.Map<String, io.github.humbleui.skija.Font> STYLE_FONTS =
            new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Releases every cached style-derived font. Called from
     * {@code Application.close()}; theme/registered typefaces are owned by
     * {@code FontManager} and closed there.
     */
    public static void disposeStyleFonts() {
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
    public boolean matchesPseudo(com.glyphui.style.Selector.PseudoClass pseudo) {
        return switch (pseudo) {
            case HOVER -> state == ComponentState.HOVER;
            case FOCUS -> isFocused();
            case DISABLED -> !isEnabled();
            case ACTIVE -> state == ComponentState.PRESSED;
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
     * subsequent layout pass can still resize the component to its preferred size.
     *
     * @param width  the layout-assigned width
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
            requestRepaint();
        }
    }

    /**
     * Checks whether the component's size was provided explicitly by the user
     * (via a bounds-carrying constructor or {@link #setWidth}/{@link #setHeight})
     * rather than left for layout managers to decide.
     *
     * @return true if the size must be respected by layout managers
     */
    public boolean isSizeExplicitlySet() {
        return sizeExplicitlySet;
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
        if (inlineStyle != null && !inlineStyle.isEmpty()) {
            com.glyphui.style.Style.Builder b = com.glyphui.style.Style.builder()
                    .putAll(inlineStyle);
            if (inlineStyle.has(com.glyphui.style.StyleProperty.WIDTH)) {
                b.length(com.glyphui.style.StyleProperty.WIDTH, width);
            }
            if (inlineStyle.has(com.glyphui.style.StyleProperty.HEIGHT)) {
                b.length(com.glyphui.style.StyleProperty.HEIGHT, height);
            }
            inlineStyle = b.build();
        }
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
     * Sets the visibility of the component. Hiding a focused component
     * clears its focus (see {@link com.glyphui.core.FocusManager}).
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
     * clears its focus (see {@link com.glyphui.core.FocusManager}).
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
            requestRepaint();
        }
    }

    /**
     * Checks if a point is within the bounds of this component.
     *
     * @param mouseX the x-coordinate to check
     * @param mouseY the y-coordinate to check
     * @return true if the point is inside the component
     */
    public boolean contains(int mouseX, int mouseY) {
        return mouseX >= x && mouseX <= x + width &&
               mouseY >= y && mouseY <= y + height;
    }

    /**
     * Renders the component on the canvas.
     *
     * @param canvas the canvas to draw on
     */
    public abstract void render(Canvas canvas);

    /**
     * Measures the intrinsic size of this component under the given
     * constraints, in logical units.
     *
     * <p>The default implementation returns the preferred size (see
     * {@link #getPreferredWidth()} / {@link #getPreferredHeight()}) clamped
     * to the supplied maximums. Text widgets such as {@code Button} and
     * {@code Label} override this to measure their text with the theme font
     * plus padding.</p>
     *
     * @param maxWidth  the maximum available width (use
     *                  {@link Float#POSITIVE_INFINITY} for unbounded)
     * @param maxHeight the maximum available height (use
     *                  {@link Float#POSITIVE_INFINITY} for unbounded)
     * @return the measured dimension, never negative
     */
    public Dimension measure(float maxWidth, float maxHeight) {
        com.glyphui.style.Style s = getComputedStyle();
        if (s != null) {
            // Explicit CSS width/height win over the intrinsic preferred size.
            float explicitW = s.getFloat(com.glyphui.style.StyleProperty.WIDTH, -1f);
            float explicitH = s.getFloat(com.glyphui.style.StyleProperty.HEIGHT, -1f);
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
     * Treats NaN/infinite constraints as "unbounded" so the clamp above
     * still yields the preferred size.
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

    /**
     * Gets the preferred width of this component. Kept as a helper consumed
     * by the default {@link #measure(float, float)} implementation; layout
     * managers should call {@code measure} instead.
     *
     * @return the preferred width
     */
    public float getPreferredWidth() {
        return width;
    }

    /**
     * Gets the preferred height of this component. Kept as a helper consumed
     * by the default {@link #measure(float, float)} implementation; layout
     * managers should call {@code measure} instead.
     *
     * @return the preferred height
     */
    public float getPreferredHeight() {
        return height;
    }

    // ------------------------------------------------------------------
    // Focus support
    // ------------------------------------------------------------------

    /**
     * Checks whether this component participates in keyboard focus
     * traversal.
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
        this.focusable = focusable;
        if (!focusable && focused) {
            com.glyphui.core.FocusManager fm = com.glyphui.core.FocusManager.getGlobalFocusManager();
            if (fm != null) {
                fm.clearFocus(this);
            } else {
                setFocusedInternal(false);
            }
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
     * {@link com.glyphui.core.FocusManager}. No-op when the component is
     * not focusable or not visible/enabled.
     */
    public void requestFocus() {
        com.glyphui.core.FocusManager fm = com.glyphui.core.FocusManager.getGlobalFocusManager();
        if (fm != null) {
            fm.requestFocus(this);
        }
    }

    /**
     * Internal focus flag setter used by {@link com.glyphui.core.FocusManager}
     * and unit tests. Application code should use {@link #requestFocus()} or
     * the focus manager instead.
     *
     * @param focused the new focus state
     */
    public void setFocusedInternal(boolean focused) {
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
     * Gets the accessibility role of this component. Subclasses override
     * this to return a more specific role (e.g. {@link AccessibleRole#BUTTON}).
     *
     * @return the accessible role (defaults to {@link AccessibleRole#GENERIC})
     */
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.GENERIC;
    }

    /**
     * Gets the accessibility name of this component. Defaults to the
     * component id unless overridden via {@link #setAccessibleName(String)}.
     *
     * @return the accessible name
     */
    public String getAccessibleName() {
        return accessibleName != null ? accessibleName : id;
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
     * Gets the accessibility description of this component. Defaults to the
     * role name unless overridden via {@link #setAccessibleDescription(String)}.
     *
     * @return the accessible description
     */
    public String getAccessibleDescription() {
        return accessibleDescription != null ? accessibleDescription : getAccessibleRole().name();
    }

    /**
     * Overrides the accessibility description.
     *
     * @param accessibleDescription the new description (null restores the default)
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
     * Draws the standard focus ring for this component using the theme's
     * accent color: a dashed rounded rectangle inset by one logical pixel.
     * Widgets that accept focus ({@code Button}, {@code TextField}, ...)
     * call this from {@code render} when {@link #isFocused()} is true.
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
            paint.setPathEffect(io.github.humbleui.skija.PathEffect.makeDash(new float[]{4.0f, 3.0f}, 0.0f));
            canvas.drawRRect(x + 1.0f, y + 1.0f, Math.max(0.0f, width - 2.0f),
                    Math.max(0.0f, height - 2.0f),
                    getTheme().getFocusRingRadius(), getTheme().getFocusRingRadius(), paint);
        } finally {
            paint.close();
        }
    }

    /**
     * Handles mouse events.
     *
     * @param event the mouse event
     * @return {@code true} if the event was consumed and propagation to other
     *         components should stop; {@code false} otherwise
     */
    public abstract boolean onMouseEvent(MouseEvent event);

    /**
     * Handles key events.
     *
     * @param event the key event
     */
    public abstract void onKeyEvent(KeyEvent event);
    
    /**
     * Releases resources held by this component.
     * Subclasses should override this method to clean up resources.
     * The default implementation does nothing.
     */
    public void dispose() {
        // Default implementation does nothing
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
     * Gets the preferred width of this component.
     * The base implementation returns the user-provided width when bounds were
     * set explicitly, otherwise a sensible default so layout managers can place
     * the component without measuring content.
     *
     * @return the preferred width
     */
    public float getPreferredWidth() {
        return sizeExplicitlySet ? width : DEFAULT_WIDTH;
    }

    /**
     * Gets the preferred height of this component.
     * Subclasses can override this to provide content-based sizing.
     *
     * @return the preferred height
     */
    public float getPreferredHeight() {
        return sizeExplicitlySet ? height : DEFAULT_HEIGHT;
    }
}
