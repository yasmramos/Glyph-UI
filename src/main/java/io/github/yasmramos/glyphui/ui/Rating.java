package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;

import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A star rating control.
 *
 * <p>The widget paints {@link #getMax()} stars, {@link #getValue()} of them
 * filled. Clicking the {@code n}-th star sets the value to {@code n}; clicking
 * the star that already holds the value clears it back to zero, so a rating can
 * be undone without a separate reset control. Half values are honoured when
 * {@link #setAllowHalf(boolean)} is enabled, in which case a click on the left
 * half of a star produces {@code n - 0.5}.</p>
 *
 * <p>Keyboard access mirrors the mouse: the arrow keys move the value by one
 * step (or half a step with half values enabled) and Home/End jump to the
 * bounds. The value can be driven from a background thread through
 * {@link #valueProperty()}.</p>
 */
public class Rating extends Component {

    /** Logger for change-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Rating.class.getName());

    /** Default number of stars. */
    public static final int DEFAULT_MAX = 5;

    /** Size of one star, logical units. */
    public static final float STAR_SIZE = 20.0f;

    /** Gap between two stars, logical units. */
    public static final float STAR_GAP = 4.0f;

    private int max = DEFAULT_MAX;
    private float value;
    private boolean allowHalf;
    private boolean readOnly;
    private IntConsumer onChange;
    private Property<Float> valueProperty;

    private Integer starColorOverride;
    private Integer emptyColorOverride;

    private Paint fillPaint;
    private Paint emptyPaint;
    private Path starPath;

    /**
     * Creates an empty, unrated control.
     */
    public Rating() {
        super();
        // A rating is a control, so it is keyboard reachable.
        setFocusable(true);
    }

    /**
     * Creates a control with the supplied number of stars.
     *
     * @param max the number of stars (at least one)
     */
    public Rating(int max) {
        this();
        setMax(max);
    }

    @Override
    protected String defaultStyleTag() {
        return "rating";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.RATING;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return "Rating " + trimmed(value) + " of " + max;
    }

    /**
     * @return the number of stars painted
     */
    public int getMax() {
        return max;
    }

    /**
     * Sets the number of stars. The value is re-clamped into the new range.
     *
     * @param max the number of stars, clamped to at least one
     */
    public void setMax(int max) {
        int clamped = Math.max(1, max);
        if (this.max == clamped) {
            return;
        }
        this.max = clamped;
        setValue(this.value);
        invalidateLayout();
    }

    /**
     * @return the current rating, {@code 0..max}
     */
    public float getValue() {
        return value;
    }

    /**
     * Returns the observable value property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the value property (never null after first access)
     */
    public Property<Float> valueProperty() {
        if (valueProperty == null) {
            valueProperty = new Property<>(propertyOwner(), value, newValue -> {
                this.value = clamp(newValue == null ? 0.0f : newValue);
                invalidatePaint();
            });
        }
        return valueProperty;
    }

    /**
     * Sets the rating. This does not run the change handler; the handler only
     * fires on an actual click or key press.
     *
     * @param value the new rating, clamped into {@code [0, max]}
     */
    public void setValue(float value) {
        valueProperty().set(value);
    }

    /**
     * @return true when half stars are accepted
     */
    public boolean isAllowHalf() {
        return allowHalf;
    }

    /**
     * Enables half-star steps.
     *
     * @param allowHalf the new policy
     */
    public void setAllowHalf(boolean allowHalf) {
        if (this.allowHalf == allowHalf) {
            return;
        }
        this.allowHalf = allowHalf;
        setValue(value);
    }

    /**
     * @return true when the rating cannot be edited by the user
     */
    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Makes the control display-only. A read-only rating still reports its
     * value but ignores pointer and key input.
     *
     * @param readOnly the new policy
     */
    public void setReadOnly(boolean readOnly) {
        if (this.readOnly == readOnly) {
            return;
        }
        this.readOnly = readOnly;
        invalidatePaint();
    }

    /**
     * Registers the change handler, run when the user changes the rating.
     *
     * @param onChange the handler, or null to clear it
     */
    public void setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
    }

    /**
     * Gets the fill color of a set star (CSS {@code accent-color}, then the
     * override, then the theme accent).
     *
     * @return the star color (ARGB)
     */
    public int getStarColor() {
        return resolveIntStyle(StyleProperty.ACCENT_COLOR, starColorOverride,
                getTheme().getAccentColor());
    }

    /**
     * Overrides the star fill. Pass null to follow the theme accent.
     *
     * @param starColor the new color (ARGB) or null
     */
    public void setStarColor(Integer starColor) {
        if (java.util.Objects.equals(this.starColorOverride, starColor)) {
            return;
        }
        this.starColorOverride = starColor;
        invalidatePaint();
    }

    /**
     * Gets the color of an unset star.
     *
     * @return the empty-star color (ARGB)
     */
    public int getEmptyColor() {
        return resolveIntStyle(StyleProperty.COLOR, emptyColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the empty-star color. Pass null to follow the theme border.
     *
     * @param emptyColor the new color (ARGB) or null
     */
    public void setEmptyColor(Integer emptyColor) {
        if (java.util.Objects.equals(this.emptyColorOverride, emptyColor)) {
            return;
        }
        this.emptyColorOverride = emptyColor;
        invalidatePaint();
    }

    private float step() {
        return allowHalf ? 0.5f : 1.0f;
    }

    private float clamp(float raw) {
        float bounded = Math.max(0.0f, Math.min(raw, max));
        // Snap to the accepted granularity so a programmatic set cannot leave
        // a value the pointer could never produce.
        float snapped = Math.round(bounded / step()) * step();
        return Math.max(0.0f, Math.min(snapped, max));
    }

    private static String trimmed(float value) {
        return value == Math.rint(value)
                ? String.valueOf((int) value)
                : String.valueOf(value);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return max * STAR_SIZE + (max - 1) * STAR_GAP;
    }

    @Override
    public float getPreferredHeight() {
        return sizeExplicitlySet ? height : STAR_SIZE;
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
        float size = Math.min(STAR_SIZE, Math.min(width, height));
        float offsetY = (height - size) / 2.0f;
        fillPaint.setColor(enabled
                ? getStarColor()
                : getTheme().getControlTextDisabledColor());
        emptyPaint.setColor(enabled
                ? getEmptyColor()
                : getTheme().getControlDisabledColor());
        emptyPaint.setMode(io.github.humbleui.skija.PaintMode.STROKE);
        emptyPaint.setStrokeWidth(1.0f);

        for (int i = 0; i < max; i++) {
            float x = i * (STAR_SIZE + STAR_GAP);
            float filled = Math.max(0.0f, Math.min(value - i, 1.0f));
            buildStar(x, offsetY, size, starPath);
            canvas.nativeCanvasForInternalUse().drawPath(starPath, emptyPaint);
            if (filled > 0.0f) {
                // Clip the filled pass to the covered fraction so a half star
                // reads as a half star rather than a whole one.
                int saveCount = canvas.save();
                canvas.clipRect(x, offsetY, size * filled, size);
                canvas.nativeCanvasForInternalUse().drawPath(starPath, fillPaint);
                canvas.restoreToCount(saveCount);
            }
        }
    }

    /** Builds a five-pointed star inscribed in the given box. */
    private static void buildStar(float x, float y, float size, Path path) {
        path.reset();
        float cx = x + size / 2.0f;
        float cy = y + size / 2.0f;
        float outer = size / 2.0f;
        float inner = outer * 0.382f;
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2.0 + i * Math.PI / 5.0;
            float radius = (i % 2 == 0) ? outer : inner;
            float px = cx + (float) (Math.cos(angle) * radius);
            float py = cy + (float) (Math.sin(angle) * radius);
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.closePath();
    }

    private void ensurePaints() {
        if (fillPaint == null || fillPaint.isClosed()) {
            fillPaint = new Paint();
            fillPaint.setAntiAlias(true);
        }
        if (emptyPaint == null || emptyPaint.isClosed()) {
            emptyPaint = new Paint();
            emptyPaint.setAntiAlias(true);
        }
        if (starPath == null) {
            starPath = new Path();
        }
    }

    /** Maps a local x-coordinate to the rating the click should produce. */
    private float valueAtX(float localX) {
        float slot = STAR_SIZE + STAR_GAP;
        int index = (int) Math.floor(localX / slot);
        if (index < 0) {
            return 0.0f;
        }
        if (index >= max) {
            return max;
        }
        float within = localX - index * slot;
        float candidate = index + 1;
        if (allowHalf && within < STAR_SIZE / 2.0f) {
            candidate = index + 0.5f;
        }
        return candidate;
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled || readOnly) {
            return false;
        }
        boolean isInside = contains(event.getX(), event.getY());
        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE -> setState(isInside ? ComponentState.HOVER : ComponentState.IDLE);
            case PRESS -> {
                if (isInside && event.getButton() == MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                    requestFocusFromPointer();
                }
            }
            case RELEASE -> {
                if (isInside && state == ComponentState.PRESSED) {
                    apply(valueAtX(event.getX() - x), true);
                }
                setState(isInside ? ComponentState.HOVER : ComponentState.IDLE);
            }
            default -> {
            }
        }

        if (state != oldState) {
            requestRepaint();
        }
        return isInside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || readOnly || event.getType() != KeyEventType.PRESS) {
            return;
        }
        switch (event.getKeyCode()) {
            case GlyphKeys.LEFT -> apply(value - step(), true);
            case GlyphKeys.DOWN -> apply(value - step(), true);
            case GlyphKeys.RIGHT -> apply(value + step(), true);
            case GlyphKeys.UP -> apply(value + step(), true);
            case GlyphKeys.HOME -> apply(0.0f, true);
            case GlyphKeys.END -> apply(max, true);
            default -> {
            }
        }
    }

    /**
     * Applies a user-driven value. Re-clicking the star that already holds the
     * value clears the rating, the conventional toggle-off gesture.
     */
    private void apply(float requested, boolean notify) {
        float next = clamp(requested);
        if (notify && next == value && next > 0.0f) {
            next = 0.0f;
        }
        boolean changed = next != value;
        value = next;
        if (valueProperty != null) {
            valueProperty.set(value);
        }
        invalidatePaint();
        if (changed && notify && onChange != null) {
            try {
                onChange.accept((int) Math.round(value));
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Rating handler threw", e);
            }
        }
    }

    @Override
    protected void onDispose() {
        if (fillPaint != null) {
            fillPaint.close();
            fillPaint = null;
        }
        if (emptyPaint != null) {
            emptyPaint.close();
            emptyPaint = null;
        }
        if (starPath != null) {
            starPath.close();
            starPath = null;
        }
    }
}
