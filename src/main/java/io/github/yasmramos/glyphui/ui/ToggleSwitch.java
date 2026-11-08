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

import java.util.function.Consumer;

/**
 * A labelled on/off switch with a sliding knob.
 *
 * <p>Functionally a two-state control like {@link CheckBox}, but rendered as a
 * pill with a knob so it reads as an immediate toggle (settings, feature
 * flags). Clicking or pressing Space toggles {@link #isOn()} and notifies the
 * optional change listener.</p>
 *
 * <p>Like every other widget the native paints are allocated lazily on the
 * first render, so constructing a switch and driving its state machine is safe
 * in headless unit tests.</p>
 */
public class ToggleSwitch extends Component implements Textual {

    /** Width of the pill track, logical units. */
    public static final float TRACK_WIDTH = 36.0f;

    /** Height of the pill track, logical units. */
    public static final float TRACK_HEIGHT = 20.0f;

    /** Horizontal gap between the track and the caption. */
    public static final float LABEL_GAP = 8.0f;

    private String text;
    private boolean on;
    private Consumer<Boolean> onChange;
    private Property<String> textProperty;

    private Paint trackPaint;
    private Paint knobPaint;
    private Paint textPaint;

    /**
     * Creates an off switch with no caption and no explicit bounds.
     */
    public ToggleSwitch() {
        super();
    }

    /**
     * Creates a switch with a caption.
     *
     * @param text the caption
     */
    public ToggleSwitch(String text) {
        super();
        this.text = text;
    }

    /**
     * Creates a switch with explicit bounds.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the switch
     * @param height the height of the switch
     */
    public ToggleSwitch(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    /**
     * Creates a switch with explicit bounds and a caption.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the switch
     * @param height the height of the switch
     * @param text   the caption
     */
    public ToggleSwitch(float x, float y, float width, float height, String text) {
        super(x, y, width, height);
        this.text = text;
    }

    @Override
    protected String defaultStyleTag() {
        return "switch";
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
     * @return true when the switch is on
     */
    public boolean isOn() {
        return on;
    }

    /**
     * Sets the on/off state, notifying the change listener only when the value
     * actually changes.
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
     * Flips the on/off state.
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

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SWITCH;
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
        return "Switch";
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        float caption = text == null ? 0.0f : font.measureTextWidth(text);
        float padding = getTheme().getPadding();
        return padding + TRACK_WIDTH + (caption > 0.0f ? LABEL_GAP + caption : 0.0f) + padding;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        FontMetrics metrics = font.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return Math.max(TRACK_HEIGHT, line) + 2.0f * getTheme().getPadding();
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        if (sizeExplicitlySet) {
            return super.measure(maxWidth, maxHeight);
        }
        float w = getPreferredWidth();
        float h = getPreferredHeight();
        return new Dimension(
                Math.min(w, sanitize(maxWidth)),
                Math.min(h, sanitize(maxHeight)));
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
        if (knobPaint == null) {
            knobPaint = new Paint();
            knobPaint.setAntiAlias(true);
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

        float trackH = Math.max(0.0f, Math.min(TRACK_HEIGHT, height));
        float trackW = Math.max(0.0f, Math.min(TRACK_WIDTH, width));
        float trackY = (height - trackH) / 2.0f;
        float radius = trackH / 2.0f;

        int trackColor;
        if (!enabled) {
            trackColor = getTheme().getControlDisabledColor();
        } else if (on) {
            trackColor = getTheme().getAccentColor();
        } else if (state == ComponentState.HOVER) {
            trackColor = getTheme().getControlHoverColor();
        } else {
            trackColor = getTheme().getControlDisabledColor();
        }
        trackPaint.setColor(trackColor);
        canvas.drawRRect(0, trackY, trackW, trackH, radius, radius, trackPaint);

        float inset = 2.0f;
        float knobD = Math.max(0.0f, trackH - 2.0f * inset);
        float knobR = knobD / 2.0f;
        float knobCx = on
                ? trackW - inset - knobR
                : inset + knobR;
        knobPaint.setColor(getTheme().getControlTextColor());
        canvas.drawCircle(knobCx, trackY + trackH / 2.0f, knobR, knobPaint);

        String caption = text == null ? "" : text;
        if (!caption.isEmpty()) {
            Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
            FontMetrics metrics = font.getMetrics();
            float line = metrics.getDescent() - metrics.getAscent();
            float baseline = (height - line) / 2.0f - metrics.getAscent();
            textPaint.setColor(enabled
                    ? getTheme().getControlTextColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(caption, trackW + LABEL_GAP, baseline, textPaint, font);
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
        close(trackPaint);
        trackPaint = null;
        close(knobPaint);
        knobPaint = null;
        close(textPaint);
        textPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
