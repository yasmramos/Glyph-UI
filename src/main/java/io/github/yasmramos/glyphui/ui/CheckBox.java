package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.function.Consumer;

/**
 * A labelled check box with a boolean state.
 *
 * <p>Like every other widget the native paints are allocated lazily on the
 * first render, so constructing a check box and driving its state machine is
 * safe in headless unit tests. Clicking or pressing Space toggles
 * {@link #isChecked()} and notifies the optional change listener.</p>
 */
public class CheckBox extends Component {

    /** Edge length of the square indicator. */
    public static final float BOX_SIZE = 18.0f;
    /** Horizontal gap between the indicator and the caption. */
    public static final float LABEL_GAP = 8.0f;

    private String text;
    private boolean checked;
    private Consumer<Boolean> onChange;
    private Property<String> textProperty;

    private Paint boxPaint;
    private Paint borderPaint;
    private Paint markPaint;
    private Paint textPaint;

    /**
     * Creates an unchecked check box with an empty caption and no explicit
     * bounds.
     */
    public CheckBox() {
        super();
    }

    /**
     * Creates a check box with a caption.
     *
     * @param text the caption
     */
    public CheckBox(String text) {
        super();
        this.text = text;
    }

    @Override
    protected String defaultStyleTag() {
        return "checkbox";
    }

    /**
     * @return the caption
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the observable caption property.
     *
     * @return the text property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            textProperty = new Property<>(propertyOwner(), text, newText -> {
                this.text = newText;
                invalidateLayout();
            });
        }
        return textProperty;
    }

    /**
     * Sets the caption.
     *
     * @param text the new caption
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * @return true when the box is checked
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Sets the checked state, notifying the change listener only when the
     * value actually changes.
     *
     * @param checked the new state
     */
    public void setChecked(boolean checked) {
        if (this.checked == checked) {
            return;
        }
        this.checked = checked;
        invalidatePaint();
        Consumer<Boolean> listener = onChange;
        if (listener != null) {
            listener.accept(checked);
        }
    }

    /**
     * Flips the checked state.
     */
    public void toggle() {
        setChecked(!checked);
    }

    /**
     * Registers a listener invoked after the checked state changes.
     *
     * @param onChange the listener, or null to clear it
     */
    public void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.CHECKBOX;
    }

    @Override
    public String getAccessibleName() {
        String override = accessibleNameOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        if (text != null && !text.isEmpty()) {
            return text;
        }
        return "Checkbox";
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        float caption = text == null ? 0.0f : font.measureTextWidth(text);
        float padding = getTheme().getPadding();
        return padding + BOX_SIZE + (caption > 0.0f ? LABEL_GAP + caption : 0.0f) + padding;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        FontMetrics metrics = font.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return Math.max(BOX_SIZE, line) + 2.0f * getTheme().getPadding();
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        if (sizeExplicitlySet) {
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
        return new Dimension(
                Math.min(w, Math.max(0.0f, maxWidth)),
                Math.min(h, Math.max(0.0f, maxHeight)));
    }

    private void ensurePaints() {
        if (boxPaint == null) {
            boxPaint = new Paint();
            boxPaint.setAntiAlias(true);
        }
        if (borderPaint == null) {
            borderPaint = new Paint();
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            borderPaint.setAntiAlias(true);
        }
        if (markPaint == null) {
            markPaint = new Paint();
            markPaint.setMode(PaintMode.STROKE);
            markPaint.setStrokeWidth(2.0f);
            markPaint.setAntiAlias(true);
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

        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        float box = Math.max(0.0f, Math.min(BOX_SIZE, height));
        float boxY = (height - box) / 2.0f;

        int fill = checked
                ? getTheme().getAccentColor()
                : (state == ComponentState.HOVER
                        ? getTheme().getControlHoverColor()
                        : getTheme().getInputBackgroundColor());
        boxPaint.setColor(fill);
        canvas.drawRect(0, boxY, box, box, boxPaint);

        borderPaint.setColor(getTheme().getBorderColor());
        canvas.drawRect(0.5f, boxY + 0.5f, box - 1.0f, box - 1.0f, borderPaint);

        if (checked) {
            markPaint.setColor(0xFFFFFFFF);
            canvas.drawLine(box * 0.24f, boxY + box * 0.52f,
                    box * 0.44f, boxY + box * 0.72f, markPaint);
            canvas.drawLine(box * 0.44f, boxY + box * 0.72f,
                    box * 0.78f, boxY + box * 0.30f, markPaint);
        }

        String caption = text == null ? "" : text;
        if (!caption.isEmpty()) {
            FontMetrics metrics = font.getMetrics();
            float line = metrics.getDescent() - metrics.getAscent();
            float baseline = (height - line) / 2.0f - metrics.getAscent();
            textPaint.setColor(enabled
                    ? getTheme().getControlTextColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(caption, box + LABEL_GAP, baseline, textPaint, font);
        }
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
        close(boxPaint);
        boxPaint = null;
        close(borderPaint);
        borderPaint = null;
        close(markPaint);
        markPaint = null;
        close(textPaint);
        textPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
