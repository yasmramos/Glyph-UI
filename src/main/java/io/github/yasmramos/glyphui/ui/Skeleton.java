package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Paint;

/**
 * A placeholder shown while content loads.
 *
 * <p>A skeleton paints a muted, rounded block in the shape of the content that
 * is coming, so a layout does not jump when the real data arrives. Three shapes
 * cover the common cases: {@link Shape#TEXT} (a text line), {@link Shape#CIRCLE}
 * (an avatar or icon) and {@link Shape#RECT} (a card, image or chart).</p>
 *
 * <p>The widget is decoration: it never takes focus and never consumes pointer
 * or key events. The pulse animation is driven from outside (see
 * {@link #setPhase(float)}) so the widget stays deterministic in tests.</p>
 */
public class Skeleton extends Component {

    /** The silhouette a skeleton paints. */
    public enum Shape {
        /** A text line: a short, rounded bar. */
        TEXT,
        /** A circle, for avatars and icons. */
        CIRCLE,
        /** A rounded rectangle, for cards, images and charts. */
        RECT
    }

    /** Default height of a text line, logical units. */
    public static final float TEXT_HEIGHT = 12.0f;

    /** Default size of a circle, logical units. */
    public static final float CIRCLE_SIZE = 40.0f;

    private Shape shape = Shape.TEXT;

    /** Animation phase in {@code [0, 1]}: the pulse highlight position. */
    private float phase;

    private boolean animated = true;
    private Integer baseColorOverride;
    private Paint basePaint;
    private Paint pulsePaint;

    /**
     * Creates a text-shaped skeleton with no explicit bounds.
     */
    public Skeleton() {
        this(Shape.TEXT);
    }

    /**
     * Creates a skeleton with the supplied shape.
     *
     * @param shape the silhouette to paint, never null
     */
    public Skeleton(Shape shape) {
        super();
        this.shape = shape == null ? Shape.TEXT : shape;
        // Placeholder decoration: never focusable, never interactive.
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "skeleton";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SKELETON;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null
                ? accessibleNameOverride()
                : "Loading";
    }

    /**
     * @return the painted silhouette
     */
    public Shape getShape() {
        return shape;
    }

    /**
     * Sets the silhouette.
     *
     * @param shape the new shape, never null
     */
    public void setShape(Shape shape) {
        Shape next = shape == null ? Shape.TEXT : shape;
        if (this.shape == next) {
            return;
        }
        this.shape = next;
        invalidateLayout();
    }

    /**
     * @return true when the pulse highlight is painted
     */
    public boolean isAnimated() {
        return animated;
    }

    /**
     * Turns the pulse highlight on or off. A static skeleton paints a flat
     * block, which is useful for screenshots and tests.
     *
     * @param animated the new policy
     */
    public void setAnimated(boolean animated) {
        if (this.animated == animated) {
            return;
        }
        this.animated = animated;
        invalidatePaint();
    }

    /**
     * @return the current animation phase in {@code [0, 1]}
     */
    public float getPhase() {
        return phase;
    }

    /**
     * Sets the animation phase. The widget owns no clock: an animation callback
     * feeds the elapsed fraction each frame, the same pattern as
     * {@link ProgressBar#advanceAnimation(float)}.
     *
     * @param phase the phase, wrapped into {@code [0, 1]}
     */
    public void setPhase(float phase) {
        float wrapped = phase - (float) Math.floor(phase);
        if (wrapped == this.phase) {
            return;
        }
        this.phase = wrapped;
        invalidatePaint();
    }

    /**
     * Gets the block color (CSS {@code background}, then the override, then a
     * muted blend of the control surface).
     *
     * @return the base color (ARGB)
     */
    public int getBaseColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, baseColorOverride,
                blend(getTheme().getControlNormalColor(),
                        getTheme().getBackgroundColor(), 0.5f));
    }

    /**
     * Overrides the block color. Pass null to follow the muted theme blend.
     *
     * @param baseColor the new color (ARGB) or null
     */
    public void setBaseColor(Integer baseColor) {
        if (java.util.Objects.equals(this.baseColorOverride, baseColor)) {
            return;
        }
        this.baseColorOverride = baseColor;
        invalidatePaint();
    }

    /** The brighter color the pulse highlight sweeps with. */
    private int highlightColor() {
        return blend(getBaseColor(), getTheme().getForegroundColor(), 0.18f);
    }

    private static int blend(int from, int to, float ratio) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - ratio) + ((to >> 16) & 0xFF) * ratio);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - ratio) + ((to >> 8) & 0xFF) * ratio);
        int b = Math.round((from & 0xFF) * (1 - ratio) + (to & 0xFF) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return shape == Shape.CIRCLE ? CIRCLE_SIZE : 120.0f;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return switch (shape) {
            case CIRCLE -> CIRCLE_SIZE;
            case TEXT -> TEXT_HEIGHT;
            case RECT -> 80.0f;
        };
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSized = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            return super.measure(maxWidth, maxHeight);
        }
        float w = getPreferredWidth();
        float h = getPreferredHeight();
        if (Float.isNaN(maxWidth) || maxWidth == Float.POSITIVE_INFINITY) {
            maxWidth = Float.MAX_VALUE;
        }
        if (Float.isNaN(maxHeight) || maxHeight == Float.POSITIVE_INFINITY) {
            maxHeight = Float.MAX_VALUE;
        }
        return new Dimension(Math.min(w, Math.max(0.0f, maxWidth)),
                Math.min(h, Math.max(0.0f, maxHeight)));
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();
        basePaint.setColor(getBaseColor());

        if (shape == Shape.CIRCLE) {
            float diameter = Math.min(width, height);
            float cx = width / 2.0f;
            float cy = height / 2.0f;
            canvas.drawCircle(cx, cy, diameter / 2.0f, basePaint);
            if (animated) {
                pulsePaint.setColor(highlightColor());
                canvas.drawCircle(cx, cy, diameter / 2.0f * 0.55f, pulsePaint);
            }
            return;
        }

        float radius = shape == Shape.TEXT ? height / 2.0f
                : getTheme().getControlCornerRadius();
        canvas.drawRRect(0, 0, width, height, radius, radius, basePaint);
        if (animated) {
            // Sweep a soft highlight band across the block.
            float bandWidth = Math.max(24.0f, width * 0.3f);
            float bandX = (width + bandWidth) * phase - bandWidth;
            int saveCount = canvas.save();
            canvas.clipRect(0, 0, width, height);
            pulsePaint.setColor(highlightColor());
            canvas.drawRRect(bandX, 0, bandWidth, height, radius, radius, pulsePaint);
            canvas.restoreToCount(saveCount);
        }
    }

    private void ensurePaints() {
        if (basePaint == null || basePaint.isClosed()) {
            basePaint = new Paint();
            basePaint.setAntiAlias(true);
        }
        if (pulsePaint == null || pulsePaint.isClosed()) {
            pulsePaint = new Paint();
            pulsePaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
        // Decorative: never consumes pointer events.
        return false;
    }

    @Override
    public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
        // Decorative: skeletons never take focus or handle keys.
    }

    @Override
    protected void onDispose() {
        if (basePaint != null) {
            basePaint.close();
            basePaint = null;
        }
        if (pulsePaint != null) {
            pulsePaint.close();
            pulsePaint = null;
        }
    }
}
