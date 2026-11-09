package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.events.ScrollEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;

/**
 * A minimal vertical scroll container with an overlay scrollbar.
 *
 * <p>Children keep their own layout coordinates; the pane paints them shifted
 * up by {@link #getScrollY()} and clips to its bounds. The pane scrolls through
 * the mouse wheel (the application routes a {@link ScrollEvent} to it),
 * {@link #setScrollY(float)} / {@link #scrollBy(float)} or the arrow and page
 * keys while the pane is focused.</p>
 *
 * <p>When the children are taller than the viewport the pane draws a thin
 * overlay scrollbar on its trailing edge, the way a modern web page does. The
 * bar is painted on top of the content, so it never takes layout space away
 * from the children. The thumb can be dragged, and pressing the track pages
 * towards the click; the scroll offset stays the single source of truth, so the
 * thumb always reflects the same {@link #getScrollY()} the keyboard and the
 * wheel move.</p>
 */
public class ScrollPane extends Panel {

    /** Keyboard scroll step for the arrow keys. */
    public static final float LINE_STEP = 24.0f;

    /** Width of the overlay scrollbar, logical units. */
    public static final float SCROLLBAR_WIDTH = OverlayScrollbar.WIDTH;

    private float scrollY;

    private final OverlayScrollbar scrollbar = new OverlayScrollbar();

    /**
     * Creates an empty scroll pane with no explicit bounds.
     */
    public ScrollPane() {
        super();
    }

    @Override
    protected String defaultStyleTag() {
        return "scroll";
    }

    /**
     * @return the current vertical scroll offset in pixels (never negative)
     */
    public float getScrollY() {
        return scrollY;
    }

    /**
     * Sets the vertical scroll offset, clamped to the scrollable content
     * range.
     *
     * @param scrollY the requested offset
     */
    public void setScrollY(float scrollY) {
        float clamped = Math.max(0.0f, Math.min(scrollY, getMaxScrollY()));
        if (clamped != this.scrollY) {
            this.scrollY = clamped;
            invalidatePaint();
        }
    }

    /**
     * Scrolls by a delta; negative values scroll up.
     *
     * @param delta the offset delta in pixels
     */
    public void scrollBy(float delta) {
        setScrollY(scrollY + delta);
    }

    /**
     * @return the largest offset that still shows content, or zero when the
     *         children fit inside the viewport
     */
    public float getMaxScrollY() {
        float contentBottom = 0.0f;
        for (Component child : children) {
            if (child.isVisible()) {
                contentBottom = Math.max(contentBottom, child.getY() + child.getHeight());
            }
        }
        return Math.max(0.0f, contentBottom - height);
    }

    /**
     * @return true when the content is taller than the viewport and the pane
     *         therefore shows the overlay scrollbar
     */
    public boolean isScrollbarVisible() {
        return scrollbar.isVisible(height, contentHeight());
    }

    /** The total height the children occupy, used to size the scrollbar. */
    private float contentHeight() {
        return height + getMaxScrollY();
    }

    @Override
    protected void onDispose() {
        super.onDispose();
        scrollbar.dispose();
    }

    /**
     * The pane itself needs focus so it can receive arrow keys when no child
     * is focused.
     */
    @Override
    public boolean isFocusable() {
        return true;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SCROLL_PANE;
    }

    @Override
    protected void render(Canvas canvas) {
        // Component.renderInternal() already translated the origin to the
        // pane's top-left corner; only the vertical scroll offset is added
        // here. The clip rectangle is expressed in that translated space,
        // so it stays anchored to the viewport.
        doLayout();

        int saveCount = canvas.save();
        try {
            canvas.translate(0, -scrollY);
            canvas.clipRect(0, scrollY, width, height);
            for (Component child : children) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }

        renderScrollbar(canvas);
    }

    /**
     * Paints the overlay scrollbar on top of the (already clipped) content.
     * Drawn last so the thumb is never hidden by a child, and outside the
     * content clip so it does not scroll with the children.
     */
    private void renderScrollbar(Canvas canvas) {
        scrollbar.render(canvas, width, height, 0.0f, contentHeight(), scrollY,
                getMaxScrollY(), getTheme());
    }

    /**
     * The overlay scrollbar sits above the content, so a point on the bar
     * resolves to the pane itself rather than to the child painted under it.
     * This keeps hover and cursor resolution consistent with
     * {@link #onMouseEvent}, which gives the bar the pointer first.
     */
    @Override
    public Component getComponentAt(float px, float py) {
        if (!visible || !contains(px, py)) {
            return null;
        }
        if (scrollbar.isOverTrack(px - x, py - y, width, height, 0.0f, contentHeight())) {
            return this;
        }
        return super.getComponentAt(px, py);
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        boolean inside = contains(event.getX(), event.getY());
        float localX = event.getX() - x;
        float localY = event.getY() - y;

        // The overlay bar sits on top of the content, so it gets the pointer
        // before the children: a press on the thumb starts a drag, a press on
        // the track pages towards the click, and a drag keeps mapping the
        // pointer to an offset even after it leaves the bar.
        switch (event.getType()) {
            case PRESS -> {
                if (inside && event.getButton() == MouseButton.LEFT) {
                    OverlayScrollbar.Press where = scrollbar.press(
                            localX, localY, width, height, 0.0f, contentHeight(),
                            scrollY, getMaxScrollY());
                    if (where != OverlayScrollbar.Press.NONE) {
                        requestFocusFromPointer();
                        if (where != OverlayScrollbar.Press.THUMB) {
                            // Page towards the click: a track press above the
                            // thumb scrolls up by a viewport, below it scrolls
                            // down.
                            float page = Math.max(height, LINE_STEP);
                            scrollBy(where == OverlayScrollbar.Press.TRACK_BEFORE
                                    ? -page : page);
                        }
                        invalidatePaint();
                        return true;
                    }
                }
            }
            case DRAG -> {
                if (scrollbar.isDragging() && event.getButton() == MouseButton.LEFT) {
                    float target = scrollbar.drag(localY, height, 0.0f, contentHeight(),
                            getMaxScrollY());
                    if (!Float.isNaN(target)) {
                        setScrollY(target);
                        return true;
                    }
                }
            }
            case RELEASE -> {
                if (scrollbar.release()) {
                    invalidatePaint();
                    return true;
                }
            }
            case MOVE -> {
                if (scrollbar.setHover(scrollbar.isOverTrack(
                        localX, localY, width, height, 0.0f, contentHeight()))) {
                    invalidatePaint();
                }
            }
            default -> {
            }
        }

        if (inside && event.getType() == MouseEventType.PRESS) {
            requestFocusFromPointer();
        }
        // Panel translates by the pane's origin; add the scroll offset so
        // children are hit-tested in their unscrolled layout coordinates.
        MouseEvent shifted = new MouseEvent(event.getType(),
                event.getX(), event.getY() + (int) scrollY,
                event.getButton(), event.getClickCount());
        return super.onMouseEvent(shifted) || inside;
    }

    @Override
    public boolean onScroll(ScrollEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        if (!contains(event.getX(), event.getY())) {
            return false;
        }
        // A child that scrolls itself (a nested ScrollPane, a list) gets the
        // gesture first; only when no child consumes it does this pane move,
        // so a wheel over an inner scroller does not scroll both. Children are
        // hit-tested in their unscrolled layout space, exactly as in
        // onMouseEvent, so the scroll offset is added here.
        int localX = event.getX() - (int) x;
        int localY = event.getY() - (int) y;
        ScrollEvent childEvent = new ScrollEvent(
                event.getDeltaX(), event.getDeltaY(), localX, localY + (int) scrollY);
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (!child.isVisible() || !child.isEnabled()) {
                continue;
            }
            if (child.contains(childEvent.getX(), childEvent.getY())
                    && child.onScroll(childEvent)) {
                return true;
            }
        }
        if (event.getDeltaY() == 0f) {
            return false;
        }
        // Only consume the gesture when the pane actually moved: at the top or
        // bottom (or with nothing to scroll) the leftover delta bubbles up to
        // an enclosing scroller.
        float before = scrollY;
        scrollBy(event.getDeltaY() * LINE_STEP);
        return scrollY != before;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            super.onKeyEvent(event);
            return;
        }
        float page = Math.max(height, LINE_STEP);
        switch (event.getKeyCode()) {
            case GlyphKeys.UP -> scrollBy(-LINE_STEP);
            case GlyphKeys.DOWN -> scrollBy(LINE_STEP);
            case GlyphKeys.PAGE_UP -> scrollBy(-page);
            case GlyphKeys.PAGE_DOWN -> scrollBy(page);
            default -> super.onKeyEvent(event);
        }
    }
}
