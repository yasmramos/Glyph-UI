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
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A compact, interactive tag for filters, choices and tokens.
 *
 * <p>A chip shows a short caption on a pill-shaped background. Unlike
 * {@link Badge}, which is decoration, a chip is a control: clicking it (or
 * pressing Enter/Space while focused) toggles its selected state and runs the
 * {@link #setOnClick(ClickListener) click handler}. A chip can also be made
 * removable, which paints a trailing close affordance that fires the
 * {@link #setOnRemove(ClickListener) remove handler}.</p>
 *
 * <p>Colors resolve like every other widget: a CSS declaration wins, then a
 * per-instance override, then the theme. A selected chip paints the theme
 * accent behind white text; an unselected one paints the control surface
 * behind the foreground color.</p>
 */
public class Chip extends Component {

    /** Logger for click-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Chip.class.getName());

    /** Horizontal inner padding, logical units. */
    public static final float PADDING_HORIZONTAL = 10.0f;

    /** Vertical inner padding, logical units. */
    public static final float PADDING_VERTICAL = 4.0f;

    /** Width of the close affordance, including its gap, logical units. */
    public static final float REMOVE_WIDTH = 16.0f;

    private String text;
    private boolean selected;
    private boolean removable;
    private ClickListener onClick;
    private ClickListener onRemove;
    private Property<String> textProperty;
    private Property<Boolean> selectedProperty;

    private Integer backgroundOverride;
    private Integer selectedBackgroundOverride;
    private Integer textColorOverride;
    private Integer borderColorOverride;

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint textPaint;
    private Paint markPaint;

    /**
     * Creates an empty chip with no explicit bounds.
     */
    public Chip() {
        this("");
    }

    /**
     * Creates a chip showing the supplied caption.
     *
     * @param text the caption (null is treated as empty)
     */
    public Chip(String text) {
        super();
        this.text = text == null ? "" : text;
        // A chip is a control, so it is keyboard reachable.
        setFocusable(true);
    }

    @Override
    protected String defaultStyleTag() {
        return "chip";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.CHIP;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null ? accessibleNameOverride() : text;
    }

    /**
     * Gets the chip caption.
     *
     * @return the caption, never null
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the observable caption property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the caption property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            textProperty = new Property<>(propertyOwner(), text, newText -> {
                this.text = newText == null ? "" : newText;
                invalidateLayout();
            });
        }
        return textProperty;
    }

    /**
     * Sets the chip caption.
     *
     * @param text the new caption (null is treated as empty)
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * @return true when the chip is in the selected state
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Returns the observable selected property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the selected property (never null after first access)
     */
    public Property<Boolean> selectedProperty() {
        if (selectedProperty == null) {
            selectedProperty = new Property<>(propertyOwner(), selected,
                    value -> {
                        this.selected = value != null && value;
                        invalidatePaint();
                    });
        }
        return selectedProperty;
    }

    /**
     * Sets the selected state. This does not run the click handler; the
     * handler only fires on an actual click or keyboard activation.
     *
     * @param selected the new state
     */
    public void setSelected(boolean selected) {
        selectedProperty().set(selected);
    }

    /**
     * @return true when the chip paints a close affordance
     */
    public boolean isRemovable() {
        return removable;
    }

    /**
     * Controls whether the chip paints a trailing close affordance that fires
     * the remove handler.
     *
     * @param removable the new policy
     */
    public void setRemovable(boolean removable) {
        if (this.removable == removable) {
            return;
        }
        this.removable = removable;
        invalidateLayout();
    }

    /**
     * Registers the click handler, run on every activation.
     *
     * @param onClick the handler, or null to clear it
     */
    public void setOnClick(ClickListener onClick) {
        this.onClick = onClick;
    }

    /**
     * Registers the remove handler, run when the close affordance is
     * activated.
     *
     * @param onRemove the handler, or null to clear it
     */
    public void setOnRemove(ClickListener onRemove) {
        this.onRemove = onRemove;
    }

    /**
     * Gets the pill fill: the selected background when selected, the control
     * surface otherwise.
     *
     * @return the fill color (ARGB)
     */
    public int getBackgroundColor() {
        if (selected) {
            return resolveIntStyle(StyleProperty.ACCENT_COLOR,
                    selectedBackgroundOverride, getTheme().getAccentColor());
        }
        return resolveIntStyle(StyleProperty.BACKGROUND, backgroundOverride,
                getTheme().getControlNormalColor());
    }

    /**
     * Overrides the unselected fill. Pass null to follow the theme.
     *
     * @param background the new fill color (ARGB) or null
     */
    public void setBackgroundColor(Integer background) {
        if (java.util.Objects.equals(this.backgroundOverride, background)) {
            return;
        }
        this.backgroundOverride = background;
        invalidatePaint();
    }

    /**
     * Overrides the selected fill. Pass null to follow the theme accent.
     *
     * @param background the new fill color (ARGB) or null
     */
    public void setSelectedBackgroundColor(Integer background) {
        if (java.util.Objects.equals(this.selectedBackgroundOverride, background)) {
            return;
        }
        this.selectedBackgroundOverride = background;
        invalidatePaint();
    }

    /**
     * Gets the caption color: white on a selected chip, the theme foreground
     * otherwise.
     *
     * @return the caption color (ARGB)
     */
    public int getTextColor() {
        int fallback = selected ? 0xFFFFFFFF : getTheme().getForegroundColor();
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride, fallback);
    }

    /**
     * Overrides the caption color. Pass null to follow the theme.
     *
     * @param textColor the new caption color (ARGB) or null
     */
    public void setTextColor(Integer textColor) {
        if (java.util.Objects.equals(this.textColorOverride, textColor)) {
            return;
        }
        this.textColorOverride = textColor;
        invalidatePaint();
    }

    private int borderColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, borderColorOverride,
                getTheme().getBorderColor());
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    private float contentWidth() {
        float w = font().measureTextWidth(text);
        return removable ? w + REMOVE_WIDTH : w;
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return contentWidth() + 2.0f * PADDING_HORIZONTAL;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        FontMetrics metrics = font().getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return line + 2.0f * PADDING_VERTICAL;
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
        float radius = Math.min(height, width) / 2.0f;
        bgPaint.setColor(getBackgroundColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, bgPaint);

        if (state == ComponentState.HOVER) {
            borderPaint.setColor(getTheme().getControlHoverColor());
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            canvas.drawRRect(0.5f, 0.5f, width - 1.0f, height - 1.0f,
                    radius, radius, borderPaint);
        } else if (!selected) {
            borderPaint.setColor(borderColor());
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            canvas.drawRRect(0.5f, 0.5f, width - 1.0f, height - 1.0f,
                    radius, radius, borderPaint);
        }

        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (height - line) / 2.0f - metrics.getAscent();
        textPaint.setColor(enabled
                ? getTextColor()
                : getTheme().getControlTextDisabledColor());
        canvas.drawString(text, PADDING_HORIZONTAL, baseline, textPaint, f);

        if (removable) {
            // A small x centred in the trailing slot.
            float cx = width - PADDING_HORIZONTAL - REMOVE_WIDTH / 2.0f;
            float cy = height / 2.0f;
            float arm = 3.0f;
            markPaint.setColor(enabled
                    ? getTextColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy + arm, markPaint);
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy + arm, markPaint);
        }
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (markPaint == null || markPaint.isClosed()) {
            markPaint = new Paint();
            markPaint.setAntiAlias(true);
            markPaint.setMode(PaintMode.STROKE);
            markPaint.setStrokeWidth(1.5f);
        }
    }

    /**
     * @param localX an x-coordinate in the widget's local space
     * @return true when the point falls on the close affordance
     */
    private boolean isOnRemove(float localX) {
        return removable && localX >= width - PADDING_HORIZONTAL - REMOVE_WIDTH;
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
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
                    if (isOnRemove(event.getX() - x)) {
                        runHandler(onRemove);
                    } else {
                        setSelected(!selected);
                        runHandler(onClick);
                    }
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
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        if (event.getKeyCode() == GlyphKeys.ENTER || event.getKeyCode() == GlyphKeys.SPACE) {
            setSelected(!selected);
            runHandler(onClick);
        } else if (event.getKeyCode() == GlyphKeys.DELETE
                || event.getKeyCode() == GlyphKeys.BACKSPACE) {
            if (removable) {
                runHandler(onRemove);
            }
        }
    }

    /**
     * Runs a handler, catching runtime exceptions so a failing callback can
     * never leave the chip in an inconsistent visual state.
     */
    private void runHandler(ClickListener handler) {
        if (handler == null) {
            return;
        }
        try {
            handler.onClick();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Chip handler threw", e);
        }
    }

    @Override
    protected void onDispose() {
        closeQuietly(bgPaint);
        bgPaint = null;
        closeQuietly(borderPaint);
        borderPaint = null;
        closeQuietly(textPaint);
        textPaint = null;
        closeQuietly(markPaint);
        markPaint = null;
    }

    private static void closeQuietly(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
