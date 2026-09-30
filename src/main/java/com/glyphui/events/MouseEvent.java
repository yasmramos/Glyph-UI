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
    private final double deltaX;
    private final double deltaY;

    /**
     * Creates a new MouseEvent with no scroll deltas.
     *
     * @param type       the type of mouse event
     * @param x          the x-coordinate of the mouse (logical window coordinates)
     * @param y          the y-coordinate of the mouse (logical window coordinates)
     * @param button     the mouse button involved
     * @param clickCount the number of clicks
     */
    public MouseEvent(MouseEventType type, int x, int y, MouseButton button, int clickCount) {
        this(type, x, y, button, clickCount, 0.0, 0.0);
    }

    /**
     * Creates a new MouseEvent, optionally carrying scroll deltas.
     *
     * @param type       the type of mouse event
     * @param x          the x-coordinate of the mouse (logical window coordinates)
     * @param y          the y-coordinate of the mouse (logical window coordinates)
     * @param button     the mouse button involved
     * @param clickCount the number of clicks
     * @param deltaX     the horizontal scroll offset (only meaningful for SCROLL events)
     * @param deltaY     the vertical scroll offset (only meaningful for SCROLL events)
     */
    public MouseEvent(MouseEventType type, int x, int y, MouseButton button, int clickCount,
                      double deltaX, double deltaY) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.button = button;
        this.clickCount = clickCount;
        this.deltaX = deltaX;
        this.deltaY = deltaY;
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

    /**
     * Gets the horizontal scroll offset. Only meaningful for SCROLL events.
     *
     * @return the horizontal scroll delta
     */
    public double getDeltaX() {
        return deltaX;
    }

    /**
     * Gets the vertical scroll offset. Only meaningful for SCROLL events.
     *
     * @return the vertical scroll delta
     */
    public double getDeltaY() {
        return deltaY;
    }
}
