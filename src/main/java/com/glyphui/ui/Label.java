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
    private int textColor;
    private Font font;
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
        this.textColor = Color.makeARGB(255, 255, 255, 255);
        this.alignment = TextAlignment.LEFT;
        
        // Initialize font
        Typeface typeface = Typeface.makeFromName(null, FontStyle.NORMAL);
        this.font = new Font(typeface, 14.0f);
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
     * Gets the text color.
     *
     * @return the text color (as ARGB int)
     */
    public int getTextColor() {
        return textColor;
    }

    /**
     * Sets the text color.
     *
     * @param textColor the new text color (as ARGB int)
     */
    public void setTextColor(int textColor) {
        this.textColor = textColor;
        invalidate();
    }

    /**
     * Gets the preferred height of this label based on its font size.
     * 
     * @return the preferred height
     */
    @Override
    public float getPreferredHeight() {
        // Return font size plus some padding for proper spacing
        return font.getSize() + 10.0f;
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
     * Gets the font size.
     *
     * @return the font size
     */
    public float getFontSize() {
        return font.getSize();
    }

    /**
     * Sets the font size.
     *
     * @param size the new font size
     */
    public void setFontSize(float size) {
        Typeface typeface = font.getTypeface();
        this.font = new Font(typeface, size);
        invalidate();
    }

    @Override
    public void render(Canvas canvas) {
        if (!visible) {
            return;
        }

        // Calculate text position based on alignment
        float textWidth = canvas.measureText(text, font);
        float textHeight = canvas.getTextHeight(font);
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

        // Draw text
        Paint textPaint = new Paint();
        textPaint.setColor(textColor);
        textPaint.setAntiAlias(true);
        canvas.drawString(text, textX, textY, textPaint, font);
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
