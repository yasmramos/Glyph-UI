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
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

import java.util.function.Consumer;

/**
 * A button that latches on and off.
 *
 * <p>Visually a {@link Button}, behaviourally a two-state control: clicking or
 * pressing Space flips {@link #isOn()} and paints the pressed-in look while it
 * is on. It is the classic toolbar toggle — bold/italic toggles, view mode
 * switches — where a {@link ToggleSwitch} would be too large.</p>
 *
 * <p>Native paints are allocated lazily on the first render, so constructing a
 * toggle button and driving its state machine stays safe in headless unit
 * tests.</p>
 */
public class ToggleButton extends Component implements Textual {

    /** Horizontal padding added on each side of the caption when measuring. */
    public static final float HORIZONTAL_PADDING = 12.0f;

    /** Vertical padding added above and below the caption when measuring. */
    public static final float VERTICAL_PADDING = 6.0f;

    /** Corner radius of the rounded rectangle, logical units. */
    public static final float CORNER_RADIUS = 6.0f;

    private String text;
    private boolean on;
    private Consumer<Boolean> onChange;
    private Property<String> textProperty;

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint textPaint;

    /**
     * Creates an off toggle button with no caption and no explicit bounds.
     */
    public ToggleButton() {
        this("");
    }

    /**
     * Creates a toggle button with a caption.
     *
     * @param text the caption
     */
    public ToggleButton(String text) {
        super();
        this.text = text == null ? "" : text;
    }

    /**
     * Creates a toggle button with explicit bounds and a caption.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the button
     * @param height the height of the button
     * @param text   the caption
     */
    public ToggleButton(float x, float y, float width, float height, String text) {
        super(x, y, width, height);
        this.text = text == null ? "" : text;
    }

    @Override
    protected String defaultStyleTag() {
        return "togglebutton";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TOGGLE_BUTTON;
    }

    @Override
    public String getAccessibleName() {
        String override = accessibleNameOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return text.isEmpty() ? "Toggle button" : text;
    }

    // ---- Content -----------------------------------------------------------

    /**
     * @return the caption, never null
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the observable caption property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the text property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            textProperty = new Property<>(propertyOwner(), text, newText -> {
                this.text = newText == null ? "" : newText;
                invalidateLayout();
                invalidatePaint();
            });
        }
        return textProperty;
    }

    /**
     * Sets the caption.
     *
     * @param text the new caption (null is treated as empty)
     */
    public void setText(String text) {
        textProperty().set(text == null ? "" : text);
    }

    // ---- State -------------------------------------------------------------

    /**
     * @return true when the button is latched on
     */
    public boolean isOn() {
        return on;
    }

    /**
     * Sets the latched state, notifying the change listener only when the
     * value actually changes.
     *
     * @param on the new state
     */
    public void setOn(boolean on) {
        if (this.on == on) {
            return;
        }
        this.on = on;
        invalidatePaint();
        Consumer<Boolean> listener = onChange;
        if (listener != null) {
            listener.accept(on);
        }
    }

    /**
     * Flips the latched state.
     */
    public void toggle() {
        setOn(!on);
    }

    /**
     * Registers a listener invoked after the state changes.
     *
     * @param onChange the listener, or null to clear it
     */
    public void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    // ---- Colors ------------------------------------------------------------

    /**
     * Gets the fill. Resolution order: a CSS {@code background} declaration,
     * then the theme's pressed control color while on, its hover color while
     * hovered, and its normal control color otherwise.
     *
     * @return the fill color (ARGB)
     */
    public int getBackgroundColor() {
        Theme theme = getTheme();
        if (hasStyleValue(StyleProperty.BACKGROUND)) {
            return resolveIntStyle(StyleProperty.BACKGROUND, null,
                    theme.getControlNormalColor());
        }
        if (!enabled) {
            return theme.getControlDisabledColor();
        }
        if (on) {
            return theme.getControlPressedColor();
        }
        return state == ComponentState.HOVER
                ? theme.getControlHoverColor()
                : theme.getControlNormalColor();
    }

    /**
     * Gets the caption color. Resolution order: a CSS {@code color}
     * declaration, then the theme's text color.
     *
     * @return the caption color (ARGB)
     */
    public int getTextColor() {
        Theme theme = getTheme();
        return resolveIntStyle(StyleProperty.COLOR, null,
                enabled ? theme.getControlTextColor() : theme.getControlTextDisabledColor());
    }

    /**
     * Gets the border color. Resolution order: a CSS {@code border-color}
     * declaration, then the theme's accent color while on, then its border
     * color.
     *
     * @return the border color (ARGB)
     */
    public int getBorderColor() {
        Theme theme = getTheme();
        return resolveIntStyle(StyleProperty.BORDER_COLOR, null,
                on ? theme.getAccentColor() : theme.getBorderColor());
    }

    // ---- Geometry ----------------------------------------------------------

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        float caption = text.isEmpty() ? 0.0f : font().measureTextWidth(text);
        return caption + 2.0f * HORIZONTAL_PADDING;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        FontMetrics metrics = font().getMetrics();
        return (metrics.getDescent() - metrics.getAscent()) + 2.0f * VERTICAL_PADDING;
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
        Font f = font();
        float radius = CORNER_RADIUS;

        bgPaint.setColor(getBackgroundColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, bgPaint);

        borderPaint.setColor(getBorderColor());
        canvas.drawRRect(0.5f, 0.5f, width - 1.0f, height - 1.0f, radius, radius, borderPaint);

        FontMetrics m = f.getMetrics();
        float textWidth = f.measureTextWidth(text);
        float baseline = (height - (m.getDescent() - m.getAscent())) / 2.0f - m.getAscent();
        textPaint.setColor(getTextColor());
        canvas.drawString(text, (width - textWidth) / 2.0f, baseline, textPaint, f);
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(io.github.humbleui.skija.PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    // ---- Input -------------------------------------------------------------

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
                }
                break;
            case RELEASE:
                boolean activate = state == ComponentState.PRESSED && inside;
                setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                if (activate) {
                    toggle();
                }
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
        if (!enabled || !isFocused() || event.getKeyCode() != GlyphKeys.SPACE) {
            return;
        }
        if (event.getType() == KeyEventType.PRESS) {
            setState(ComponentState.PRESSED);
        } else if (event.getType() == KeyEventType.RELEASE
                && state == ComponentState.PRESSED) {
            setState(ComponentState.IDLE);
            toggle();
        }
    }

    @Override
    protected void onDispose() {
        close(bgPaint);
        bgPaint = null;
        close(borderPaint);
        borderPaint = null;
        close(textPaint);
        textPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
