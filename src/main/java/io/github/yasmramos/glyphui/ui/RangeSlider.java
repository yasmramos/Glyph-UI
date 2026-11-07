package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.function.BiConsumer;

/**
 * A slider with two thumbs that selects an inclusive range, the classic
 * "from / to" control.
 *
 * <p>It is the two-ended sibling of {@link Slider}: the track between the
 * thumbs is filled, either thumb can be dragged, and the two values can never
 * cross — pushing one past the other carries it along instead of inverting
 * the range. Applications observe the pair through
 * {@link #setOnChange(BiConsumer)} or the individual
 * {@link #lowerValueProperty()} / {@link #upperValueProperty()}:</p>
 *
 * <pre>{@code
 * RangeSlider price = new RangeSlider(0, 1000);
 * price.setValues(200, 800);
 * price.setOnChange((lo, hi) -> filter(lo, hi));
 * }</pre>
 *
 * <p>Keyboard support matches {@link Slider}: arrows nudge the active thumb,
 * Page Up/Down move it in larger steps, Home/End push it to the bounds, and
 * Tab switches which thumb the arrows drive.</p>
 */
public class RangeSlider extends Component {

    /** Default height, logical units. */
    public static final float DEFAULT_SLIDER_HEIGHT = 24.0f;

    /** Diameter of each thumb, logical units. */
    public static final float THUMB_SIZE = 14.0f;

    /** Thickness of the track, logical units. */
    public static final float TRACK_THICKNESS = 4.0f;

    /** Multiplier applied by Page Up / Page Down, in steps. */
    private static final float PAGE_STEPS = 10.0f;

    /** Which thumb the keyboard and the drag operate on. */
    private enum Thumb { LOWER, UPPER }

    private float min;
    private float max = 1.0f;
    private float step;

    private float lowerValue;
    private float upperValue;

    private boolean dragging;
    private Thumb activeThumb = Thumb.LOWER;
    private Thumb hoveredThumb;

    private BiConsumer<Float, Float> onChange;
    private Property<Float> lowerValueProperty;
    private Property<Float> upperValueProperty;

    private Paint trackPaint;
    private Paint fillPaint;
    private Paint thumbPaint;
    private Paint borderPaint;

    /**
     * Creates a range slider over {@code [0, 1]} with both thumbs at zero.
     */
    public RangeSlider() {
        this(0.0f, 1.0f);
    }

    /**
     * Creates a range slider over the supplied span.
     *
     * @param min the lower bound
     * @param max the upper bound (must be greater than {@code min})
     */
    public RangeSlider(float min, float max) {
        super();
        setRange(min, max);
    }

    @Override
    protected String defaultStyleTag() {
        return "rangeslider";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.RANGE_SLIDER;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return format(lowerValue) + " – " + format(upperValue);
    }

    // ---- Range and values --------------------------------------------------

    /**
     * @return the lower bound of the slider
     */
    public float getMin() {
        return min;
    }

    /**
     * @return the upper bound of the slider
     */
    public float getMax() {
        return max;
    }

    /**
     * Sets the slider's bounds. The current values are clamped into the new
     * span, and a degenerate span (equal bounds) is widened so the thumbs
     * never divide by zero.
     *
     * @param min the lower bound
     * @param max the upper bound
     */
    public void setRange(float min, float max) {
        if (max < min) {
            float tmp = min;
            min = max;
            max = tmp;
        }
        this.min = min;
        this.max = max > min ? max : min + 1.0f;
        this.lowerValue = clampAndSnap(lowerValue);
        this.upperValue = clampAndSnap(upperValue);
        if (lowerValue > upperValue) {
            upperValue = lowerValue;
        }
        invalidatePaint();
    }

    /**
     * @return the step used to snap values, or {@code 0} for continuous
     */
    public float getStep() {
        return step;
    }

    /**
     * Sets the snapping step. A non-positive step keeps the slider continuous.
     *
     * @param step the new step
     */
    public void setStep(float step) {
        this.step = Math.max(0.0f, step);
        this.lowerValue = clampAndSnap(lowerValue);
        this.upperValue = clampAndSnap(upperValue);
        invalidatePaint();
    }

    /**
     * @return the lower thumb value
     */
    public float getLowerValue() {
        return lowerValue;
    }

    /**
     * @return the upper thumb value
     */
    public float getUpperValue() {
        return upperValue;
    }

    /**
     * Returns the observable lower-value property.
     *
     * @return the property (never null after first access)
     */
    public Property<Float> lowerValueProperty() {
        if (lowerValueProperty == null) {
            lowerValueProperty = new Property<>(propertyOwner(), lowerValue,
                    v -> setValuesInternal(v, upperValue, true));
        }
        return lowerValueProperty;
    }

    /**
     * Returns the observable upper-value property.
     *
     * @return the property (never null after first access)
     */
    public Property<Float> upperValueProperty() {
        if (upperValueProperty == null) {
            upperValueProperty = new Property<>(propertyOwner(), upperValue,
                    v -> setValuesInternal(lowerValue, v, true));
        }
        return upperValueProperty;
    }

    /**
     * Sets both thumb values. They are clamped into the range and snapped to
     * the step, and the pair is reordered so the lower thumb never exceeds
     * the upper one.
     *
     * @param lower the new lower value
     * @param upper the new upper value
     */
    public void setValues(float lower, float upper) {
        setValuesInternal(lower, upper, true);
    }

    private void setValuesInternal(float lower, float upper, boolean notify) {
        float lo = clampAndSnap(lower);
        float hi = clampAndSnap(upper);
        if (lo > hi) {
            float tmp = lo;
            lo = hi;
            hi = tmp;
        }
        if (lo == lowerValue && hi == upperValue) {
            return;
        }
        lowerValue = lo;
        upperValue = hi;
        invalidatePaint();
        if (notify && onChange != null) {
            onChange.accept(lowerValue, upperValue);
        }
    }

    /**
     * Registers the range-change handler.
     *
     * @param onChange the handler receiving (lower, upper), or null to clear
     */
    public void setOnChange(BiConsumer<Float, Float> onChange) {
        this.onChange = onChange;
    }

    /**
     * @return the range-change handler, or null when none is set
     */
    public BiConsumer<Float, Float> getOnChange() {
        return onChange;
    }

    private float clampAndSnap(float v) {
        float clamped = Math.max(min, Math.min(max, v));
        if (step > 0.0f) {
            clamped = min + Math.round((clamped - min) / step) * step;
            clamped = Math.max(min, Math.min(max, clamped));
        }
        return clamped;
    }

    private float fraction(float value) {
        float span = max - min;
        return span <= 0.0f ? 0.0f : (value - min) / span;
    }

    private float valueAtX(float localX) {
        float usable = Math.max(0.0f, width - THUMB_SIZE);
        float fraction = usable <= 0.0f ? 0.0f : (localX - THUMB_SIZE / 2.0f) / usable;
        fraction = Math.max(0.0f, Math.min(1.0f, fraction));
        return min + fraction * (max - min);
    }

    // ---- Geometry ----------------------------------------------------------

    @Override
    public float getPreferredHeight() {
        return sizeExplicitlySet ? height : DEFAULT_SLIDER_HEIGHT;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        return Dimension.of(
                Math.min(getPreferredWidth(), maxWidth),
                Math.min(getPreferredHeight(), maxHeight));
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();

        float cy = height / 2.0f;
        float trackY = cy - TRACK_THICKNESS / 2.0f;
        float radius = TRACK_THICKNESS / 2.0f;
        float usable = Math.max(0.0f, width - THUMB_SIZE);
        float lowerCx = THUMB_SIZE / 2.0f + fraction(lowerValue) * usable;
        float upperCx = THUMB_SIZE / 2.0f + fraction(upperValue) * usable;

        trackPaint.setColor(getTheme().getControlDisabledColor());
        canvas.drawRRect(0, trackY, width, TRACK_THICKNESS, radius, radius, trackPaint);

        fillPaint.setColor(enabled
                ? getTheme().getAccentColor()
                : getTheme().getControlDisabledColor());
        if (upperCx > lowerCx) {
            canvas.drawRRect(lowerCx, trackY, upperCx - lowerCx, TRACK_THICKNESS,
                    radius, radius, fillPaint);
        }

        float thumbRadius = Math.min(THUMB_SIZE, height) / 2.0f;
        borderPaint.setColor(getTheme().getBorderColor());
        drawThumb(canvas, lowerCx, cy, thumbRadius);
        drawThumb(canvas, upperCx, cy, thumbRadius);
    }

    private void drawThumb(Canvas canvas, float cx, float cy, float radius) {
        int color;
        if (!enabled) {
            color = getTheme().getControlDisabledColor();
        } else if (dragging && activeThumb != null && isActiveThumb(cx)) {
            color = getTheme().getControlPressedColor();
        } else if (state == ComponentState.HOVER) {
            color = getTheme().getControlHoverColor();
        } else {
            color = getTheme().getControlNormalColor();
        }
        thumbPaint.setColor(color);
        canvas.drawCircle(cx, cy, radius, thumbPaint);
        canvas.drawCircle(cx, cy, radius - 0.5f, borderPaint);
    }

    private boolean isActiveThumb(float cx) {
        float usable = Math.max(0.0f, width - THUMB_SIZE);
        float activeCx = THUMB_SIZE / 2.0f
                + fraction(activeThumb == Thumb.UPPER ? upperValue : lowerValue) * usable;
        return Math.abs(activeCx - cx) < 0.01f;
    }

    private void ensurePaints() {
        if (trackPaint == null || trackPaint.isClosed()) {
            trackPaint = new Paint();
            trackPaint.setAntiAlias(true);
        }
        if (fillPaint == null || fillPaint.isClosed()) {
            fillPaint = new Paint();
            fillPaint.setAntiAlias(true);
        }
        if (thumbPaint == null || thumbPaint.isClosed()) {
            thumbPaint = new Paint();
            thumbPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            borderPaint.setAntiAlias(true);
        }
    }

    // ---- Input -------------------------------------------------------------

    /**
     * Picks the thumb nearest to a local x coordinate, so a press grabs the
     * closer handle (and a press in an empty stretch moves the nearer one).
     */
    private Thumb thumbAt(float localX) {
        float usable = Math.max(0.0f, width - THUMB_SIZE);
        float lowerCx = THUMB_SIZE / 2.0f + fraction(lowerValue) * usable;
        float upperCx = THUMB_SIZE / 2.0f + fraction(upperValue) * usable;
        return Math.abs(localX - lowerCx) <= Math.abs(localX - upperCx)
                ? Thumb.LOWER : Thumb.UPPER;
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
                    activeThumb = thumbAt(event.getX() - x);
                    moveActiveThumb(valueAtX(event.getX() - x));
                }
                break;

            case DRAG:
                if (dragging && event.getButton() == MouseButton.LEFT) {
                    moveActiveThumb(valueAtX(event.getX() - x));
                    return true;
                }
                return false;

            case RELEASE:
                dragging = false;
                setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                return inside;

            default:
                break;
        }

        if (state != oldState) {
            requestRepaint();
        }
        return inside;
    }

    /**
     * Moves the active thumb to the requested value, carrying the other thumb
     * along when the two would cross. Keeping the pair ordered here means the
     * public {@code lower <= upper} invariant holds for every drag frame.
     */
    private void moveActiveThumb(float requested) {
        if (activeThumb == Thumb.LOWER) {
            float lo = requested;
            float hi = Math.max(upperValue, lo);
            setValuesInternal(lo, hi, true);
        } else {
            float hi = requested;
            float lo = Math.min(lowerValue, hi);
            setValuesInternal(lo, hi, true);
        }
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!enabled || !isFocused() || event.getType() != KeyEventType.PRESS) {
            return;
        }
        float span = max - min;
        float unit = step > 0.0f ? step : span / 100.0f;

        if (event.getKeyCode() == GlyphKeys.TAB) {
            activeThumb = activeThumb == Thumb.LOWER ? Thumb.UPPER : Thumb.LOWER;
            requestRepaint();
            return;
        }

        float current = activeThumb == Thumb.LOWER ? lowerValue : upperValue;
        float target;
        switch (event.getKeyCode()) {
            case GlyphKeys.RIGHT:
            case GlyphKeys.UP:
                target = current + unit;
                break;
            case GlyphKeys.LEFT:
            case GlyphKeys.DOWN:
                target = current - unit;
                break;
            case GlyphKeys.PAGE_UP:
                target = current + unit * PAGE_STEPS;
                break;
            case GlyphKeys.PAGE_DOWN:
                target = current - unit * PAGE_STEPS;
                break;
            case GlyphKeys.HOME:
                target = min;
                break;
            case GlyphKeys.END:
                target = max;
                break;
            default:
                return;
        }
        moveActiveThumb(target);
    }

    private String format(float value) {
        if (step >= 1.0f) {
            return String.valueOf(Math.round(value));
        }
        return String.valueOf(value);
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
        super.onDispose();
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) {
            paint.close();
        }
    }
}
