package com.glyphui.ui;

import com.glyphui.graphics.Canvas;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;
import io.github.humbleui.skija.*;

/**
 * A label component for displaying text.
 */
public class Label extends Component {
    private String text;
    // Optional per-label color override; null means "use the Theme"
    private Integer textColorOverride;
    private Float fontSizeOverride;
    private TextAlignment alignment;

    /**
     * Text alignment options.
     */
    public enum TextAlignment {
        LEFT,
        CENTER,
        RIGHT
    }

    /**
     * Creates a new Label.
     *
     * @param x      the x-coordinate of the label
     * @param y      the y-coordinate of the label
     * @param width  the width of the label
     * @param height the height of the label
     * @param text   the text to display
     */
    public Label(float x, float y, float width, float height, String text) {
        super(x, y, width, height);
        this.text = text;
        this.alignment = TextAlignment.LEFT;
        // Static labels do not accept keyboard focus
        setFocusable(false);
        // Colors and fonts are resolved from Theme.current() at render time.
    }

    @Override
    public com.glyphui.ui.AccessibleRole getAccessibleRole() {
        return com.glyphui.ui.AccessibleRole.LABEL;
    }

    @Override
    public String getAccessibleName() {
        // Labels are named by their caption unless explicitly overridden
        String base = super.getAccessibleName();
        return (base != null && !base.equals(getId())) ? base : text;
    }

    /**
     * Gets the font used by this label (shared theme BODY font — owned by
     * {@code FontManager}, do not close).
     *
     * @return the label font
     */
    private io.github.humbleui.skija.Font getFont() {
        if (fontSizeOverride != null) {
            return getTheme().getFont(com.glyphui.graphics.Theme.FontRole.BODY, fontSizeOverride);
        }
        return getTheme().getFont(com.glyphui.graphics.Theme.FontRole.BODY);
    }

    /**
     * Gets the label text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the label text.
     *
     * @param text the new text
     */
    public void setText(String text) {
        this.text = text;
        invalidate();
    }

    /**
     * Gets the text color (override or the theme's foreground color).
     *
     * @return the text color (as ARGB int)
     */
    public int getTextColor() {
        return textColorOverride != null ? textColorOverride : getTheme().getForegroundColor();
    }

    /**
     * Overrides the text color for this label. Pass null to follow the
     * current theme again.
     *
     * @param textColor the new text color (as ARGB int) or null
     */
    public void setTextColor(Integer textColor) {
        this.textColorOverride = textColor;
        invalidate();
    }

    /**
     * Gets the preferred height of this label: one line of the theme body
     * font plus the theme padding (helper for the default measure path;
     * prefer {@link #measure(float, float)}).
     *
     * @return the preferred height
     */
    @Override
    public float getPreferredHeight() {
        io.github.humbleui.skija.FontMetrics metrics = getFont().getMetrics();
        return (metrics.getDescent() - metrics.getAscent()) + 2.0f * getTheme().getPadding();
    }

    /**
     * Gets the preferred width of this label: the measured caption width
     * plus the theme padding.
     *
     * @return the preferred width
     */
    @Override
    public float getPreferredWidth() {
        return getFont().measureTextWidth(text == null ? "" : text) + 2.0f * getTheme().getPadding();
    }

    /**
     * Measures the label with its theme font plus padding, clamped to the
     * supplied maximums.
     *
     * @param maxWidth  the maximum available width
     * @param maxHeight the maximum available height
     * @return the measured dimension
     */
    @Override
    public com.glyphui.graphics.Dimension measure(float maxWidth, float maxHeight) {
        float w = getPreferredWidth();
        float h = getPreferredHeight();
        if (Float.isNaN(maxWidth) || maxWidth == Float.POSITIVE_INFINITY) {
            maxWidth = Float.MAX_VALUE;
        }
        if (Float.isNaN(maxHeight) || maxHeight == Float.POSITIVE_INFINITY) {
            maxHeight = Float.MAX_VALUE;
        }
        return new com.glyphui.graphics.Dimension(
                Math.min(w, Math.max(0.0f, maxWidth)),
                Math.min(h, Math.max(0.0f, maxHeight)));
    }

    /**
     * Gets the text alignment.
     *
     * @return the alignment
     */
    public TextAlignment getAlignment() {
        return alignment;
    }

    /**
     * Sets the text alignment.
     *
     * @param alignment the new alignment
     */
    public void setAlignment(TextAlignment alignment) {
        this.alignment = alignment;
        invalidate();
    }

    /**
     * Gets the font size (theme default unless overridden).
     *
     * @return the font size
     */
    public float getFontSize() {
        return getFont().getSize();
    }

    /**
     * Overrides the font size for this label. Pass null to follow the
     * current theme again. The previous font was shared through
     * {@code FontManager} so nothing needs to be closed here.
     *
     * @param size the new font size or null
     */
    public void setFontSize(Float size) {
        this.fontSizeOverride = size;
        invalidate();
    }

    @Override
    public void render(Canvas canvas) {
        if (!visible) {
            return;
        }

        // Calculate text position based on alignment
        io.github.humbleui.skija.Font f = getFont();
        float textWidth = canvas.measureText(text, f);
        float textHeight = canvas.getTextHeight(f);
        float textX;
        
        switch (alignment) {
            case CENTER:
                textX = x + (width - textWidth) / 2.0f;
                break;
            case RIGHT:
                textX = x + width - textWidth;
                break;
            default:
                textX = x;
                break;
        }
        
        float textY = y + (height + textHeight) / 2.0f;

        // Draw text (color comes from the theme unless overridden)
        Paint textPaint = new Paint();
        textPaint.setColor(enabled ? getTextColor() : getTheme().getControlTextDisabledColor());
        textPaint.setAntiAlias(true);
        canvas.drawString(text, textX, textY, textPaint, f);
        textPaint.close();
    }

    @Override
    public void onMouseEvent(MouseEvent event) {
        // Labels typically don't handle mouse events
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Labels typically don't handle key events
    }
}
