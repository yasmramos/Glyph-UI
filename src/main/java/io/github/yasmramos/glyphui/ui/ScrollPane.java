package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;

/**
 * A minimal vertical scroll container.
 *
 * <p>Children keep their own layout coordinates; the pane paints them shifted
 * up by {@link #getScrollY()} and clips to its bounds. There is no scrollbar
 * (the toolkit has no wheel event yet): callers scroll through
 * {@link #setScrollY(float)} / {@link #scrollBy(float)} or the arrow and page
 * keys while the pane is focused.</p>
 */
public class ScrollPane extends Panel {

    /** Keyboard scroll step for the arrow keys. */
    public static final float LINE_STEP = 24.0f;

    private float scrollY;

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
        if (!visible) {
            return;
        }
        doLayout();

        int saveCount = canvas.save();
        try {
            // Translate to the pane's local origin, then up by the scroll
            // offset. The clip rectangle is expressed in that translated
            // space, so it stays anchored to the viewport.
            canvas.translate(x, y - scrollY);
            canvas.clipRect(0, scrollY, width, height);
            for (Component child : children) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        boolean inside = contains(event.getX(), event.getY());
        if (inside && event.getType() == io.github.yasmramos.glyphui.events.MouseEventType.PRESS) {
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
