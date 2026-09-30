package com.glyphui.ui;

import com.glyphui.core.Application;
import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.Property;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;

/**
 * Abstract base class for all UI components.
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
public abstract class Component implements AutoCloseable {

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

    /** Applies a user-set width: marks bounds as explicit and repaints. */
    private void applyWidthExplicit(float newWidth) {
        this.width = newWidth;
        this.sizeExplicitlySet = true;
        requestRepaint();
    }

    /** Applies a user-set height: marks bounds as explicit and repaints. */
    private void applyHeightExplicit(float newHeight) {
        this.height = newHeight;
        this.sizeExplicitlySet = true;
        requestRepaint();
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
        // notifies listeners and requests a repaint via its change hook.
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
        // notifies listeners and requests a repaint via its change hook.
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
