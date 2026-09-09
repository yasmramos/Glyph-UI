package com.glyphui.ui;

import com.glyphui.graphics.Canvas;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;

/**
 * Abstract base class for all UI components.
 */
public abstract class Component {
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
        this.x = x;
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
        this.y = y;
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
        this.width = width;
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
        this.height = height;
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
        this.visible = visible;
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
        this.enabled = enabled;
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
        this.state = state;
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
}
