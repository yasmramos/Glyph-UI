package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphCursor;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.layout.LayoutManager;
import io.github.humbleui.skija.Paint;

import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Two panes separated by a draggable divider.
 *
 * <p>The first child fills the leading pane and the second the trailing one;
 * the split fraction is draggable with the pointer and adjustable with the
 * arrow keys while the divider has focus. Dragging never collapses a pane
 * below {@link #getMinSize()} pixels, and the fraction is clamped to
 * {@code [0, 1]}.</p>
 *
 * <p>The split fraction is observable through {@link #ratioProperty()}. Setting
 * it from a background thread marshals the change onto the UI thread.</p>
 */
public class SplitPane extends Panel {

    /** Logger for resize-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(SplitPane.class.getName());

    /** Which axis the divider splits. */
    public enum Orientation {
        /** Left and right panes, with a vertical divider. */
        HORIZONTAL,
        /** Top and bottom panes, with a horizontal divider. */
        VERTICAL
    }

    /** Thickness of the divider strip, logical units. */
    public static final float DIVIDER_SIZE = 6.0f;

    /** Smallest pane size the divider can produce, logical units. */
    public static final float MIN_PANE = 24.0f;

    /** Step the arrow keys move the divider by, logical units. */
    public static final float KEY_STEP = 16.0f;

    private Orientation orientation = Orientation.HORIZONTAL;

    /** Fraction of the pane area given to the first child, in {@code [0, 1]}. */
    private float ratio = 0.5f;

    private float minSize = MIN_PANE;
    private boolean dragging;
    private Consumer<Float> onResize;
    private Property<Float> ratioProperty;

    private Paint dividerPaint;
    private Paint handlePaint;

    /**
     * Creates an empty horizontal split with no explicit bounds.
     */
    public SplitPane() {
        super();
        setLayoutManager(new SplitLayout());
        setFocusable(true);
    }

    /**
     * Creates a split with the supplied panes.
     *
     * @param orientation the split axis, never null
     * @param first       the leading pane
     * @param second      the trailing pane
     */
    public SplitPane(Orientation orientation, Component first, Component second) {
        this();
        if (orientation != null) {
            this.orientation = orientation;
        }
        if (first != null) {
            add(first);
        }
        if (second != null) {
            add(second);
        }
    }

    /**
     * Positions the two panes around the divider. Idempotent: both panes are
     * re-derived from the current bounds on every run.
     */
    private final class SplitLayout extends LayoutManager {

        @Override
        public void layout(Panel panel) {
            if (panel.getChildren().size() < 2) {
                return;
            }
            Component first = panel.getChildren().get(0);
            Component second = panel.getChildren().get(1);
            float total = horizontal() ? panel.getWidth() : panel.getHeight();
            float available = Math.max(0.0f, total - DIVIDER_SIZE);
            float firstSize = clampFirst(available, available * ratio);

            if (horizontal()) {
                first.applyLayoutSize(firstSize, panel.getHeight());
                first.setX(0.0f);
                first.setY(0.0f);
                second.applyLayoutSize(Math.max(0.0f, available - firstSize), panel.getHeight());
                second.setX(firstSize + DIVIDER_SIZE);
                second.setY(0.0f);
            } else {
                first.applyLayoutSize(panel.getWidth(), firstSize);
                first.setX(0.0f);
                first.setY(0.0f);
                second.applyLayoutSize(panel.getWidth(), Math.max(0.0f, available - firstSize));
                second.setX(0.0f);
                second.setY(firstSize + DIVIDER_SIZE);
            }
        }

        @Override
        public Dimension measure(Panel panel, float maxWidth, float maxHeight) {
            if (panel.getChildren().size() < 2) {
                return panel.measureContent(maxWidth, maxHeight);
            }
            Dimension a = panel.getChildren().get(0).measure(maxWidth, maxHeight);
            Dimension b = panel.getChildren().get(1).measure(maxWidth, maxHeight);
            if (horizontal()) {
                return new Dimension(a.getWidth() + b.getWidth() + DIVIDER_SIZE,
                        Math.max(a.getHeight(), b.getHeight()));
            }
            return new Dimension(Math.max(a.getWidth(), b.getWidth()),
                    a.getHeight() + b.getHeight() + DIVIDER_SIZE);
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "splitpane";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SPLIT_PANE;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null
                ? accessibleNameOverride()
                : "Split pane";
    }

    private boolean horizontal() {
        return orientation == Orientation.HORIZONTAL;
    }

    /**
     * @return the split axis
     */
    public Orientation getOrientation() {
        return orientation;
    }

    /**
     * Sets the split axis.
     *
     * @param orientation the new axis, never null
     * @throws IllegalArgumentException when {@code orientation} is null
     */
    public void setOrientation(Orientation orientation) {
        if (orientation == null) {
            throw new IllegalArgumentException("SplitPane orientation must not be null");
        }
        if (this.orientation == orientation) {
            return;
        }
        this.orientation = orientation;
        invalidateLayout();
    }

    /**
     * @return the fraction of the pane area given to the first child
     */
    public float getRatio() {
        return ratio;
    }

    /**
     * Returns the observable ratio property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the ratio property (never null after first access)
     */
    public Property<Float> ratioProperty() {
        if (ratioProperty == null) {
            ratioProperty = new Property<>(propertyOwner(), ratio, newValue -> {
                this.ratio = clampRatio(newValue == null ? 0.5f : newValue);
                invalidateLayout();
            });
        }
        return ratioProperty;
    }

    /**
     * Sets the split fraction. This does not run the resize handler; the
     * handler only fires on an actual drag or key press.
     *
     * @param ratio the new fraction, clamped into {@code [0, 1]}
     */
    public void setRatio(float ratio) {
        ratioProperty().set(ratio);
    }

    /**
     * @return the smallest pane size the divider can produce
     */
    public float getMinSize() {
        return minSize;
    }

    /**
     * Sets the smallest pane size.
     *
     * @param minSize the new minimum, never negative
     * @throws IllegalArgumentException when {@code minSize} is negative
     */
    public void setMinSize(float minSize) {
        if (minSize < 0.0f) {
            throw new IllegalArgumentException("SplitPane minSize must not be negative");
        }
        if (this.minSize == minSize) {
            return;
        }
        this.minSize = minSize;
        invalidateLayout();
    }

    /**
     * Registers the resize handler, run while the user drags the divider.
     *
     * @param onResize the handler receiving the new fraction, or null
     */
    public void setOnResize(Consumer<Float> onResize) {
        this.onResize = onResize;
    }

    /** Clamps a raw fraction into the range the minimum pane size allows. */
    private float clampRatio(float raw) {
        return Math.max(0.0f, Math.min(raw, 1.0f));
    }

    /**
     * Clamps a desired leading-pane size so neither pane falls below
     * {@link #getMinSize()}. When the panes cannot both hold the minimum, the
     * leading pane takes the whole space minus one minimum.
     */
    private float clampFirst(float available, float desired) {
        float upper = Math.max(0.0f, available - minSize);
        float lower = Math.min(minSize, upper);
        return Math.max(lower, Math.min(desired, upper));
    }

    /** The leading pane's size implied by the current bounds and fraction. */
    private float firstSize() {
        float total = horizontal() ? width : height;
        float available = Math.max(0.0f, total - DIVIDER_SIZE);
        return clampFirst(available, available * ratio);
    }

    /** The divider's leading edge along the split axis. */
    private float dividerStart() {
        return firstSize();
    }

    /**
     * Whether a local point lands on the divider strip.
     *
     * @param localX the x-coordinate in the pane's local space
     * @param localY the y-coordinate in the pane's local space
     * @return true when the point is on the divider
     */
    private boolean isOnDivider(float localX, float localY) {
        float start = dividerStart();
        if (horizontal()) {
            return localX >= start && localX <= start + DIVIDER_SIZE;
        }
        return localY >= start && localY <= start + DIVIDER_SIZE;
    }

    @Override
    protected GlyphCursor cursorAt(float localX, float localY) {
        // Over the divider the pointer advertises the resize axis, matching the
        // web's col-resize / row-resize; over either pane it stays the arrow so
        // the panes' own widgets keep their affordances.
        if (isOnDivider(localX, localY)) {
            return horizontal() ? GlyphCursor.RESIZE_EW : GlyphCursor.RESIZE_NS;
        }
        return super.cursorAt(localX, localY);
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        doLayout();
        ensurePaints();
        float start = dividerStart();
        dividerPaint.setColor(getTheme().getBackgroundColor());
        handlePaint.setColor(getTheme().getBorderColor());

        if (horizontal()) {
            canvas.drawRect(start, 0.0f, DIVIDER_SIZE, height, dividerPaint);
            float gripX = start + DIVIDER_SIZE / 2.0f - 0.5f;
            canvas.drawRect(gripX, Math.max(0.0f, height / 2.0f - 12.0f),
                    1.0f, Math.min(24.0f, height), handlePaint);
        } else {
            canvas.drawRect(0.0f, start, width, DIVIDER_SIZE, dividerPaint);
            float gripY = start + DIVIDER_SIZE / 2.0f - 0.5f;
            canvas.drawRect(Math.max(0.0f, width / 2.0f - 12.0f), gripY,
                    Math.min(24.0f, width), 1.0f, handlePaint);
        }

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            for (Component child : getChildren()) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    private void ensurePaints() {
        if (dividerPaint == null || dividerPaint.isClosed()) {
            dividerPaint = new Paint();
            dividerPaint.setAntiAlias(true);
        }
        if (handlePaint == null || handlePaint.isClosed()) {
            handlePaint = new Paint();
            handlePaint.setAntiAlias(true);
        }
    }

    /** Maps a pointer position on the split axis to a new fraction. */
    private void dragTo(float localX, float localY) {
        float total = horizontal() ? width : height;
        float available = Math.max(0.0f, total - DIVIDER_SIZE);
        if (available <= 0.0f) {
            return;
        }
        float position = horizontal() ? localX : localY;
        applyRatio(clampFirst(available, position) / available, true);
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        boolean isInside = contains(event.getX(), event.getY());
        float localX = event.getX() - x;
        float localY = event.getY() - y;

        switch (event.getType()) {
            case PRESS -> {
                if (isInside && event.getButton() == MouseButton.LEFT
                        && isOnDivider(localX, localY)) {
                    dragging = true;
                    requestFocusFromPointer();
                    setState(ComponentState.PRESSED);
                    return true;
                }
            }
            case DRAG -> {
                if (dragging && event.getButton() == MouseButton.LEFT) {
                    dragTo(localX, localY);
                    return true;
                }
            }
            case RELEASE -> {
                if (dragging) {
                    dragging = false;
                    setState(ComponentState.IDLE);
                    return true;
                }
            }
            default -> {
            }
        }

        // Anything off the divider goes to the panes, so nested controls keep
        // receiving their own events.
        boolean consumed = super.onMouseEvent(event);
        if (isInside && state == ComponentState.PRESSED && !dragging) {
            setState(ComponentState.IDLE);
        }
        return consumed || isInside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            super.onKeyEvent(event);
            return;
        }
        float total = horizontal() ? width : height;
        float available = Math.max(0.0f, total - DIVIDER_SIZE);
        if (available <= 0.0f) {
            super.onKeyEvent(event);
            return;
        }
        float step = KEY_STEP / available;
        switch (event.getKeyCode()) {
            case GlyphKeys.LEFT -> applyRatio(ratio - step, true);
            case GlyphKeys.UP -> applyRatio(ratio - step, true);
            case GlyphKeys.RIGHT -> applyRatio(ratio + step, true);
            case GlyphKeys.DOWN -> applyRatio(ratio + step, true);
            case GlyphKeys.HOME -> applyRatio(0.0f, true);
            case GlyphKeys.END -> applyRatio(1.0f, true);
            default -> super.onKeyEvent(event);
        }
    }

    /** Applies a user-driven fraction and reports the change. */
    private void applyRatio(float requested, boolean notify) {
        float next = clampRatio(requested);
        if (next == ratio) {
            return;
        }
        ratio = next;
        if (ratioProperty != null) {
            ratioProperty.set(ratio);
        }
        invalidateLayout();
        if (notify && onResize != null) {
            try {
                onResize.accept(ratio);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "SplitPane handler threw", e);
            }
        }
    }

    @Override
    protected void onDispose() {
        if (dividerPaint != null) {
            dividerPaint.close();
            dividerPaint = null;
        }
        if (handlePaint != null) {
            handlePaint.close();
            handlePaint = null;
        }
    }
}
