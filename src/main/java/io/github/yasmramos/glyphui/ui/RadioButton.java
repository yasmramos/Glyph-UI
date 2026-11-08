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
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.function.Consumer;

/**
 * A labelled radio button, optionally part of a {@link RadioGroup}.
 *
 * <p>Radio buttons are meant to be mutually exclusive, so a button added to a
 * {@link RadioGroup} has that exclusivity enforced: selecting one member
 * deselects the rest. A button outside a group keeps its own state and can be
 * selected or cleared freely.</p>
 *
 * <p>Clicking a button (or pressing Space while it is focused) selects it. The
 * optional change listener fires when the selection state changes.</p>
 *
 * <p>Like every other widget the native paints are allocated lazily on the
 * first render, so construction and the state machine are safe in headless
 * unit tests.</p>
 */
public class RadioButton extends Component implements Textual {

    /** Diameter of the circular indicator, logical units. */
    public static final float INDICATOR_SIZE = 18.0f;

    /** Horizontal gap between the indicator and the caption. */
    public static final float LABEL_GAP = 8.0f;

    private String text;
    private boolean selected;
    private RadioGroup group;
    private Consumer<Boolean> onChange;
    private Property<String> textProperty;

    private Paint circlePaint;
    private Paint borderPaint;
    private Paint dotPaint;
    private Paint textPaint;

    /**
     * Creates an unselected radio button with no caption.
     */
    public RadioButton() {
        super();
    }

    /**
     * Creates a radio button with a caption.
     *
     * @param text the caption
     */
    public RadioButton(String text) {
        super();
        this.text = text;
    }

    /**
     * Creates a radio button with explicit bounds.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the button
     * @param height the height of the button
     */
    public RadioButton(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    /**
     * Creates a radio button with explicit bounds and a caption.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the button
     * @param height the height of the button
     * @param text   the caption
     */
    public RadioButton(float x, float y, float width, float height, String text) {
        super(x, y, width, height);
        this.text = text;
    }

    @Override
    protected String defaultStyleTag() {
        return "radio";
    }

    @Override
    protected GlyphCursor defaultCursor() {
        return GlyphCursor.POINTING_HAND;
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
     * @return true when this button is the selected one
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * @return the group this button belongs to, or null when independent
     */
    public RadioGroup getGroup() {
        return group;
    }

    /**
     * Selects or clears this button.
     *
     * <p>Selecting a member of a {@link RadioGroup} delegates to the group so
     * the other members are deselected atomically; clearing a grouped button
     * directly is a no-op because a group always keeps exactly one selection
     * once one has been made.</p>
     *
     * @param selected the new selection state
     */
    public void setSelected(boolean selected) {
        if (group != null) {
            if (selected) {
                group.select(this);
            }
            // Deselecting through the button is intentionally ignored: the
            // group owns the invariant that at most one member is selected.
            return;
        }
        setSelectedInternal(selected);
    }

    /**
     * Applies the selection state and fires the change listener. Package
     * private: only the owning {@link RadioGroup} may call it, so the group
     * stays the single writer of member state.
     *
     * @param selected the new selection state
     */
    void setSelectedInternal(boolean selected) {
        if (this.selected == selected) {
            return;
        }
        this.selected = selected;
        invalidatePaint();
        Consumer<Boolean> listener = onChange;
        if (listener != null) {
            listener.accept(selected);
        }
    }

    /**
     * Assigns the owning group. Package private: use {@link RadioGroup#add}.
     *
     * @param group the new owning group, or null to detach
     */
    void setGroup(RadioGroup group) {
        this.group = group;
    }

    /**
     * Registers a listener invoked after the selection state changes.
     *
     * @param onChange the listener, or null to clear it
     */
    public void setOnChange(Consumer<Boolean> onChange) {
        this.onChange = onChange;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.RADIO_BUTTON;
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
        return "Radio button";
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        float caption = text == null ? 0.0f : font.measureTextWidth(text);
        float padding = getTheme().getPadding();
        return padding + INDICATOR_SIZE + (caption > 0.0f ? LABEL_GAP + caption : 0.0f) + padding;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        FontMetrics metrics = font.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return Math.max(INDICATOR_SIZE, line) + 2.0f * getTheme().getPadding();
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
        if (circlePaint == null) {
            circlePaint = new Paint();
            circlePaint.setAntiAlias(true);
        }
        if (borderPaint == null) {
            borderPaint = new Paint();
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            borderPaint.setAntiAlias(true);
        }
        if (dotPaint == null) {
            dotPaint = new Paint();
            dotPaint.setAntiAlias(true);
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

        float d = Math.max(0.0f, Math.min(INDICATOR_SIZE, height));
        float r = d / 2.0f;
        float cx = r;
        float cy = height / 2.0f;

        int fill = selected
                ? getTheme().getAccentColor()
                : (state == ComponentState.HOVER
                        ? getTheme().getControlHoverColor()
                        : getTheme().getInputBackgroundColor());
        circlePaint.setColor(fill);
        canvas.drawCircle(cx, cy, r, circlePaint);

        borderPaint.setColor(getTheme().getBorderColor());
        canvas.drawCircle(cx, cy, r - 0.5f, borderPaint);

        if (selected) {
            dotPaint.setColor(getTheme().getControlTextColor());
            canvas.drawCircle(cx, cy, Math.max(0.0f, r * 0.4f), dotPaint);
        }

        String caption = text == null ? "" : text;
        if (!caption.isEmpty()) {
            Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
            FontMetrics metrics = font.getMetrics();
            float line = metrics.getDescent() - metrics.getAscent();
            float baseline = (height - line) / 2.0f - metrics.getAscent();
            textPaint.setColor(enabled
                    ? getTheme().getControlTextColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(caption, d + LABEL_GAP, baseline, textPaint, font);
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
                    setSelected(true);
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
            setSelected(true);
        }
    }

    @Override
    protected void onDispose() {
        close(circlePaint);
        circlePaint = null;
        close(borderPaint);
        borderPaint = null;
        close(dotPaint);
        dotPaint = null;
        close(textPaint);
        textPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
