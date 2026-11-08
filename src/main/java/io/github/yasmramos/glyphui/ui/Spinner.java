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
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A numeric stepper: a value between two bounds, edited with a {@code -} and a
 * {@code +} button or the keyboard.
 *
 * <p>The value is clamped to {@code [min, max]} and snapped to the nearest
 * multiple of {@link #getStep()}. The arrow keys nudge by one step, Home/End
 * jump to the bounds and Page Up/Down move by ten steps. Clicking the left or
 * right half steps down or up.</p>
 *
 * <p>Like every other widget the native paints are allocated lazily on the
 * first render, so construction and the whole interaction state machine are
 * safe in headless unit tests.</p>
 */
public class Spinner extends Component {

    /** Default height of the spinner, logical units. */
    public static final float DEFAULT_SPINNER_HEIGHT = 24.0f;

    /** Default width of the spinner, logical units. */
    public static final float DEFAULT_SPINNER_WIDTH = 96.0f;

    /** Number of steps a Page Up/Down press moves. */
    private static final float PAGE_STEPS = 10.0f;

    private float min;
    private float max = 100.0f;
    private float value;
    private float step = 1.0f;
    private boolean minusPressed;
    private boolean plusPressed;
    private boolean minusHovered;
    private boolean plusHovered;

    private Consumer<Float> onChange;
    private Property<Float> valueProperty;

    private Paint backgroundPaint;
    private Paint borderPaint;
    private Paint buttonPaint;
    private Paint textPaint;

    /**
     * Creates a spinner over {@code [0, 100]} with a step of one.
     */
    public Spinner() {
        super();
        initDefaults();
    }

    /**
     * Creates a spinner over {@code [0, 100]} with a step of one.
     *
     * @param width  the width of the spinner
     * @param height the height of the spinner
     */
    public Spinner(float width, float height) {
        super(width, height);
        initDefaults();
    }

    /**
     * Creates a spinner over {@code [0, 100]} with a step of one.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the spinner
     * @param height the height of the spinner
     */
    public Spinner(float x, float y, float width, float height) {
        super(x, y, width, height);
        initDefaults();
    }

    private void initDefaults() {
        // Focusable: a spinner participates in Tab traversal.
        setFocusable(true);
    }

    @Override
    protected String defaultStyleTag() {
        return "spinner";
    }

    @Override
    protected GlyphCursor defaultCursor() {
        return GlyphCursor.POINTING_HAND;
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
     * @return the step applied to every change
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
     * Sets the step. A step of zero (or less) is treated as one, so the
     * spinner always has a usable increment.
     *
     * @param step the step size
     */
    public void setStep(float step) {
        this.step = Float.isNaN(step) || step <= 0.0f ? 1.0f : step;
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

    /**
     * Steps the value by one step in the given direction.
     *
     * @param direction {@code +1} to increment, {@code -1} to decrement
     */
    public void step(int direction) {
        setValue(value + Math.signum(direction) * step);
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

    /** The value as text: whole numbers without a decimal point, else trimmed. */
    private String formatValue() {
        if (Math.abs(value - Math.rint(value)) < 1e-6f) {
            return Long.toString(Math.round(value));
        }
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        if (text.endsWith(".")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /** Width of each stepper button, capped so a short spinner stays usable. */
    private float buttonWidth() {
        return Math.min(Math.max(0.0f, height), Math.max(0.0f, width / 2.0f));
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SPINNER;
    }

    @Override
    public String getAccessibleName() {
        String override = accessibleNameOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return "Spinner";
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return DEFAULT_SPINNER_WIDTH;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return DEFAULT_SPINNER_HEIGHT;
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
        if (backgroundPaint == null) {
            backgroundPaint = new Paint();
            backgroundPaint.setAntiAlias(true);
        }
        if (borderPaint == null) {
            borderPaint = new Paint();
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            borderPaint.setAntiAlias(true);
        }
        if (buttonPaint == null) {
            buttonPaint = new Paint();
            buttonPaint.setAntiAlias(true);
        }
        if (textPaint == null) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();

        Theme theme = getTheme();
        float radius = resolveFloatStyle(StyleProperty.BORDER_RADIUS, null,
                theme.getControlCornerRadius());
        float buttonWidth = buttonWidth();

        backgroundPaint.setColor(enabled
                ? resolveIntStyle(StyleProperty.BACKGROUND, null,
                        theme.getInputBackgroundColor())
                : theme.getControlDisabledColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, backgroundPaint);

        // Stepper buttons: tinted when hovered/pressed, dimmed when disabled.
        if (enabled) {
            if (minusPressed) {
                buttonPaint.setColor(theme.getControlPressedColor());
            } else if (state == ComponentState.HOVER && minusHovered) {
                buttonPaint.setColor(theme.getControlHoverColor());
            } else {
                buttonPaint.setColor(theme.getControlNormalColor());
            }
            canvas.drawRRect(0, 0, buttonWidth, height, radius, radius, buttonPaint);

            if (plusPressed) {
                buttonPaint.setColor(theme.getControlPressedColor());
            } else if (state == ComponentState.HOVER && plusHovered) {
                buttonPaint.setColor(theme.getControlHoverColor());
            } else {
                buttonPaint.setColor(theme.getControlNormalColor());
            }
            canvas.drawRRect(width - buttonWidth, 0, buttonWidth, height,
                    radius, radius, buttonPaint);
        }

        int focusBorder = isFocused() ? theme.getAccentColor() : theme.getBorderColor();
        borderPaint.setColor(resolveIntStyle(StyleProperty.BORDER_COLOR, null, focusBorder));
        canvas.drawRRect(0.5f, 0.5f, Math.max(0.0f, width - 1.0f),
                Math.max(0.0f, height - 1.0f), radius, radius, borderPaint);

        Font font = resolveFont(Theme.FontRole.BUTTON);
        FontMetrics metrics = font.getMetrics();
        float lineHeight = metrics.getDescent() - metrics.getAscent();
        float baseline = (height - lineHeight) / 2.0f - metrics.getAscent();

        int glyphColor = enabled
                ? resolveIntStyle(StyleProperty.COLOR, null, theme.getControlTextColor())
                : theme.getControlTextDisabledColor();
        textPaint.setColor(glyphColor);
        canvas.drawString("-", (buttonWidth - canvas.measureText("-", font)) / 2.0f,
                baseline, textPaint, font);
        canvas.drawString("+", width - buttonWidth
                        + (buttonWidth - canvas.measureText("+", font)) / 2.0f,
                baseline, textPaint, font);

        String text = formatValue();
        float textX = buttonWidth
                + (Math.max(0.0f, width - 2.0f * buttonWidth)
                        - canvas.measureText(text, font)) / 2.0f;
        textPaint.setColor(enabled
                ? resolveIntStyle(StyleProperty.COLOR, null, theme.getForegroundColor())
                : theme.getControlTextDisabledColor());
        canvas.drawString(text, textX, baseline, textPaint, font);
        // The focus ring is painted by Component.renderInternal() when
        // isFocusVisible() is true — widgets must not draw it themselves.
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }

        boolean inside = contains(event.getX(), event.getY());
        float localX = event.getX() - x;
        float buttonWidth = buttonWidth();
        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE:
                minusHovered = inside && localX < buttonWidth;
                plusHovered = inside && localX >= width - buttonWidth;
                setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                break;

            case PRESS:
                if (inside && event.getButton() == MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                    requestFocusFromPointer();
                    if (localX < buttonWidth) {
                        minusPressed = true;
                        step(-1);
                    } else if (localX >= width - buttonWidth) {
                        plusPressed = true;
                        step(1);
                    }
                }
                break;

            case RELEASE:
                minusPressed = false;
                plusPressed = false;
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
        float delta;
        switch (event.getKeyCode()) {
            case GlyphKeys.RIGHT:
            case GlyphKeys.UP:
                delta = step;
                break;
            case GlyphKeys.LEFT:
            case GlyphKeys.DOWN:
                delta = -step;
                break;
            case GlyphKeys.PAGE_UP:
                delta = step * PAGE_STEPS;
                break;
            case GlyphKeys.PAGE_DOWN:
                delta = -step * PAGE_STEPS;
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
        close(backgroundPaint);
        backgroundPaint = null;
        close(borderPaint);
        borderPaint = null;
        close(buttonPaint);
        buttonPaint = null;
        close(textPaint);
        textPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
