package com.glyphui.events;

/**
 * Represents a mouse event with position, button, and type information.
 */
public class MouseEvent {
    private final MouseEventType type;
    private final int x;
    private final int y;
    private final MouseButton button;
    private final int clickCount;

    /**
     * Creates a new MouseEvent.
     *
     * @param type       the type of mouse event
     * @param x          the x-coordinate of the mouse
     * @param y          the y-coordinate of the mouse
     * @param button     the mouse button involved
     * @param clickCount the number of clicks
     */
    public MouseEvent(MouseEventType type, int x, int y, MouseButton button, int clickCount) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = clickCount;
    }

    /**
     * Gets the type of mouse event.
     *
     * @return the mouse event type
     */
    public MouseEventType getType() {
        return type;
    }

    /**
     * Gets the x-coordinate of the mouse.
     *
     * @return the x-coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the y-coordinate of the mouse.
     *
     * @return the y-coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Gets the mouse button involved in the event.
     *
     * @return the mouse button
     */
    public MouseButton getButton() {
        return button;
    }

    /**
     * Gets the click count.
     *
     * @return the number of clicks
     */
    public int getClickCount() {
        return clickCount;
    }
}
