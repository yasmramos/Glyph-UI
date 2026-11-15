package io.github.yasmramos.glyphui.events;

/**
 * A mouse-wheel (or trackpad) scroll gesture.
 *
 * <p>The wheel is a gesture, not a button: it carries a delta per axis and no
 * button state. Modelling it separately keeps it out of the
 * {@link MouseButton} vocabulary, so a wheel flick can never be mistaken for a
 * click — historically the backend surfaced the wheel as press/release pairs on
 * virtual buttons, which made scrolling over a control press and click it.</p>
 *
 * <p>The deltas are in <em>notches</em>: a positive value scrolls right
 * ({@link #getDeltaX()}) or down ({@link #getDeltaY()}), matching the direction
 * the content moves. The backend reports the integer notch count the native
 * event carries, so a fast flick scrolls proportionally instead of being
 * flattened to a single step. The position is in window space, so a widget can
 * decide whether the gesture applies to it.</p>
 */
public class ScrollEvent {

    private final float deltaX;
    private final float deltaY;
    private final int x;
    private final int y;

    /**
     * Creates a scroll event.
     *
     * @param deltaX the horizontal delta in notches (positive scrolls right)
     * @param deltaY the vertical delta in notches (positive scrolls down)
     * @param x      the x-coordinate in window space
     * @param y      the y-coordinate in window space
     */
    public ScrollEvent(float deltaX, float deltaY, int x, int y) {
        this.deltaX = deltaX;
        this.deltaY = deltaY;
        this.x = x;
        this.y = y;
    }

    /**
     * @return the horizontal delta in notches (positive scrolls right)
     */
    public float getDeltaX() {
        return deltaX;
    }

    /**
     * @return the vertical delta in notches (positive scrolls down)
     */
    public float getDeltaY() {
        return deltaY;
    }

    /**
     * @return the x-coordinate in window space
     */
    public int getX() {
        return x;
    }

    /**
     * @return the y-coordinate in window space
     */
    public int getY() {
        return y;
    }
}
