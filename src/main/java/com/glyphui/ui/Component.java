package com.glyphui.ui;

import com.glyphui.graphics.Canvas;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;

/**
 * Abstract base class for all UI components.
 */
public abstract class Component {

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

    /**
     * Creates a new Component with default (unset) bounds.
     * The resulting component has zero geometry until a layout manager assigns
     * a preferred size or the user sets bounds explicitly.
     */
    public Component() {
        this(0f, 0f, 0f, 0f);
        this.sizeExplicitlySet = false;
    }

    /**
     * Creates a new Component.
     *
     * @param x      the x-coordinate of the component
     * @param y      the y-coordinate of the component
     * @param width  the width of the component
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
        this.id = "component_" + System.nanoTime();
        this.state = ComponentState.IDLE;
        this.sizeExplicitlySet = true;
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
        if (this.x != x) {
            this.x = x;
            requestRepaint();
        }
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
        if (this.y != y) {
            this.y = y;
            requestRepaint();
        }
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
        if (this.width != width) {
            this.width = width;
            this.sizeExplicitlySet = true;
            requestRepaint();
        }
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
        if (this.height != height) {
            this.height = height;
            this.sizeExplicitlySet = true;
            requestRepaint();
        }
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
     * Sets the visibility of the component.
     *
     * @param visible true to make visible, false to hide
     */
    public void setVisible(boolean visible) {
        if (this.visible != visible) {
            this.visible = visible;
            requestRepaint();
        }
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
     * Sets whether the component is enabled.
     *
     * @param enabled true to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        if (this.enabled != enabled) {
            this.enabled = enabled;
            requestRepaint();
        }
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
     * Handles mouse events.
     *
     * @param event the mouse event
     */
    public abstract void onMouseEvent(MouseEvent event);

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
