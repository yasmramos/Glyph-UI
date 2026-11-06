package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.function.Consumer;

/**
 * A horizontal slider that selects a value from a continuous range.
 *
 * <p>The value is clamped to {@code [min, max]} and snapped to the nearest
 * multiple of {@link #getStep()} when a step greater than zero is set. The
 * slider is keyboard accessible: the arrow keys nudge the value by one step
 * (or by {@code (max - min) / 100} when the step is zero), Home/End jump to
 * the bounds, and Page Up/Down move by ten steps.</p>
 *
 * <p>Dragging is handled through the {@code DRAG} event the application
 * dispatches while a button is held, so pressing anywhere on the track moves
 * the thumb to that position and keeps following the pointer.</p>
 *
 * <p>Like every other widget the native paints are allocated lazily on the
 * first render, so construction and the whole interaction state machine are
 * safe in headless unit tests.</p>
 */
public class Slider extends Component {

    /** Default height of the slider, logical units. */
    public static final float DEFAULT_SLIDER_HEIGHT = 24.0f;

    /** Diameter of the thumb, logical units. */
    public static final float THUMB_SIZE = 14.0f;

    /** Thickness of the track line, logical units. */
    public static final float TRACK_THICKNESS = 4.0f;

    /** Number of steps a Page Up/Down press moves when a step is configured. */
    private static final float PAGE_STEPS = 10.0f;

    private float min;
    private float max = 1.0f;
    private float value;
    private float step;
    private boolean dragging;

    private Consumer<Float> onChange;
    private Property<Float> valueProperty;

    private Paint trackPaint;
    private Paint fillPaint;
    private Paint thumbPaint;
    private Paint borderPaint;

    /**
     * Creates a slider over {@code [0, 1]} with no explicit bounds.
     */
    public Slider() {
        super();
        initDefaults();
    }

    /**
     * Creates a slider over {@code [0, 1]} with explicit bounds.
     *
     * @param width  the width of the slider
     * @param height the height of the slider
     */
    public Slider(float width, float height) {
        super(width, height);
        initDefaults();
    }

    /**
     * Creates a slider over {@code [0, 1]} with explicit bounds.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the slider
     * @param height the height of the slider
     */
    public Slider(float x, float y, float width, float height) {
        super(x, y, width, height);
        initDefaults();
    }

    private void initDefaults() {
        // Focusable: a slider participates in Tab traversal.
        setFocusable(true);
    }

    @Override
    protected String defaultStyleTag() {
        return "slider";
    }

    /**
     * @return the lower bound of the range
     */
    public float getMin() {
        return min;
    }

    /**
     * @return the upper bound of the range
     */
    public float getMax() {
        return max;
    }

    /**
     * @return the current value
     */
    public float getValue() {
        return value;
    }

    /**
     * @return the snap step, or zero when the range is continuous
     */
    public float getStep() {
        return step;
    }

    /**
     * Sets the range. The bounds are normalised so {@code min <= max} and the
     * current value is re-clamped into the new range.
     *
     * @param min the lower bound
     * @param max the upper bound
     */
    public void setRange(float min, float max) {
        float lo = Float.isNaN(min) ? 0.0f : min;
        float hi = Float.isNaN(max) ? lo : max;
        if (lo > hi) {
            float swap = lo;
            lo = hi;
            hi = swap;
        }
        this.min = lo;
        this.max = hi;
        setValue(this.value);
        invalidatePaint();
    }

    /**
     * Sets the snap step. A step of zero (or less) makes the slider
     * continuous.
     *
     * @param step the step size, clamped to a non-negative value
     */
    public void setStep(float step) {
        this.step = Float.isNaN(step) ? 0.0f : Math.max(0.0f, step);
        setValue(this.value);
        invalidatePaint();
    }

    /**
     * Sets the current value, clamped to the range and snapped to the step.
     * The change listener fires only when the value actually changes.
     *
     * @param value the new value
     */
    public void setValue(float value) {
        valueProperty().set(clampAndSnap(value));
    }

    /**
     * Returns the observable value property.
     *
     * @return the value property (never null after first access)
     */
    public Property<Float> valueProperty() {
        if (valueProperty == null) {
            valueProperty = new Property<>(propertyOwner(), value, newValue -> {
                this.value = clampAndSnap(newValue);
                invalidatePaint();
                Consumer<Float> listener = onChange;
                if (listener != null) {
                    listener.accept(this.value);
                }
            });
        }
        return valueProperty;
    }

    /**
     * Registers a listener invoked after the value changes.
     *
     * @param onChange the listener, or null to clear it
     */
    public void setOnChange(Consumer<Float> onChange) {
        this.onChange = onChange;
    }

    private float clampAndSnap(float v) {
        if (Float.isNaN(v)) {
            v = min;
        }
        float clamped = Math.max(min, Math.min(max, v));
        if (step > 0.0f) {
            float snapped = min + Math.round((clamped - min) / step) * step;
            clamped = Math.max(min, Math.min(max, snapped));
        }
        return clamped;
    }

    /** Fraction of the track the thumb sits at, in {@code [0, 1]}. */
    private float fraction() {
        float span = max - min;
        return span <= 0.0f ? 0.0f : (value - min) / span;
    }

    /** Maps a local x coordinate onto a value. */
    private float valueAtX(float localX) {
        float usable = Math.max(0.0f, width - THUMB_SIZE);
        float t = usable <= 0.0f ? 0.0f : (localX - THUMB_SIZE / 2.0f) / usable;
        t = Math.max(0.0f, Math.min(1.0f, t));
        return min + t * (max - min);
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SLIDER;
    }

    @Override
    public String getAccessibleName() {
        String override = accessibleNameOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return "Slider";
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return DEFAULT_WIDTH;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return DEFAULT_SLIDER_HEIGHT;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        if (sizeExplicitlySet) {
            return super.measure(maxWidth, maxHeight);
        }
        return new Dimension(
                Math.min(getPreferredWidth(), sanitize(maxWidth)),
                Math.min(getPreferredHeight(), sanitize(maxHeight)));
    }

    private static float sanitize(float constraint) {
        if (Float.isNaN(constraint) || constraint == Float.POSITIVE_INFINITY) {
            return Float.MAX_VALUE;
        }
        return Math.max(0.0f, constraint);
    }

    private void ensurePaints() {
        if (trackPaint == null) {
            trackPaint = new Paint();
            trackPaint.setAntiAlias(true);
        }
        if (fillPaint == null) {
            fillPaint = new Paint();
            fillPaint.setAntiAlias(true);
        }
        if (thumbPaint == null) {
            thumbPaint = new Paint();
            thumbPaint.setAntiAlias(true);
        }
        if (borderPaint == null) {
            borderPaint = new Paint();
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            borderPaint.setAntiAlias(true);
        }
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();

        float cy = height / 2.0f;
        float trackY = cy - TRACK_THICKNESS / 2.0f;
        float radius = TRACK_THICKNESS / 2.0f;
        float usable = Math.max(0.0f, width - THUMB_SIZE);
        float thumbCx = THUMB_SIZE / 2.0f + fraction() * usable;

        trackPaint.setColor(getTheme().getControlDisabledColor());
        canvas.drawRRect(0, trackY, width, TRACK_THICKNESS, radius, radius, trackPaint);

        fillPaint.setColor(enabled
                ? getTheme().getAccentColor()
                : getTheme().getControlDisabledColor());
        if (thumbCx > 0.0f) {
            canvas.drawRRect(0, trackY, thumbCx, TRACK_THICKNESS, radius, radius, fillPaint);
        }

        float thumbRadius = Math.min(THUMB_SIZE, height) / 2.0f;
        int thumbColor;
        if (!enabled) {
            thumbColor = getTheme().getControlDisabledColor();
        } else if (state == ComponentState.PRESSED || dragging) {
            thumbColor = getTheme().getControlPressedColor();
        } else if (state == ComponentState.HOVER) {
            thumbColor = getTheme().getControlHoverColor();
        } else {
            thumbColor = getTheme().getControlNormalColor();
        }
        thumbPaint.setColor(thumbColor);
        canvas.drawCircle(thumbCx, cy, thumbRadius, thumbPaint);

        borderPaint.setColor(getTheme().getBorderColor());
        canvas.drawCircle(thumbCx, cy, thumbRadius - 0.5f, borderPaint);
        // The focus ring is painted by Component.renderInternal() when
        // isFocusVisible() is true — widgets must not draw it themselves.
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }

        boolean inside = contains(event.getX(), event.getY());
        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE:
                setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                break;

            case PRESS:
                if (inside && event.getButton() == MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                    requestFocusFromPointer();
                    dragging = true;
                    setValue(valueAtX(event.getX() - x));
                }
                break;

            case DRAG:
                if (dragging && event.getButton() == MouseButton.LEFT) {
                    setValue(valueAtX(event.getX() - x));
                    return true;
                }
                // No drag session: do not claim the event, so a drag that
                // started on another widget is not swallowed here.
                return false;

            case RELEASE:
                if (dragging) {
                    dragging = false;
                    setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                    return true;
                }
                setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                break;

            default:
                break;
        }

        if (state != oldState) {
            requestRepaint();
        }
        return inside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!enabled || !isFocused() || event.getType() != KeyEventType.PRESS) {
            return;
        }
        float span = max - min;
        float unit = step > 0.0f ? step : span / 100.0f;
        float delta;
        switch (event.getKeyCode()) {
            case GlyphKeys.RIGHT:
            case GlyphKeys.UP:
                delta = unit;
                break;
            case GlyphKeys.LEFT:
            case GlyphKeys.DOWN:
                delta = -unit;
                break;
            case GlyphKeys.PAGE_UP:
                delta = unit * PAGE_STEPS;
                break;
            case GlyphKeys.PAGE_DOWN:
                delta = -unit * PAGE_STEPS;
                break;
            case GlyphKeys.HOME:
                setValue(min);
                return;
            case GlyphKeys.END:
                setValue(max);
                return;
            default:
                return;
        }
        setValue(value + delta);
    }

    @Override
    protected void onDispose() {
        close(trackPaint);
        trackPaint = null;
        close(fillPaint);
        fillPaint = null;
        close(thumbPaint);
        thumbPaint = null;
        close(borderPaint);
        borderPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
