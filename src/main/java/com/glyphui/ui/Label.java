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
    /** Reusable text paint: created once, reused every frame, closed in dispose(). */
    private Paint textPaint;

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
        initDefaults();
    }

    /**
     * Creates a new Label without explicit bounds. The size will be derived
     * from the preferred (measured) text size when a layout manager runs.
     *
     * @param text the text to display
     */
    public Label(String text) {
        super();
        this.text = text;
        initDefaults();
    }

    /**
     * Creates a new Label with default (unset) bounds and empty text.
     */
    public Label() {
        this("");
    }

    /**
     * Initializes default color, alignment and font.
     */
    private void initDefaults() {
        this.textColor = Color.makeARGB(255, 255, 255, 255);
        this.alignment = TextAlignment.LEFT;

        // Initialize font from the shared cached typeface (cheap Font wrapper)
        this.font = com.glyphui.graphics.Fonts.createDefaultFont(14.0f);
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
        if (!java.util.Objects.equals(this.text, text)) {
            this.text = text;
            requestRepaint();
        }
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
        if (this.textColor != textColor) {
            this.textColor = textColor;
            requestRepaint();
        }
    }

    /**
     * Gets the preferred width of this label: measured text width without any
     * decorative padding. Falls back to the base defaults when the user
     * provided explicit bounds.
     *
     * @return the preferred width
     */
    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        if (font == null || font.isClosed()) {
            return DEFAULT_WIDTH;
        }
        return font.measureTextWidth(text == null ? "" : text);
    }

    /**
     * Gets the preferred height of this label based on its font size.
     *
     * @return the preferred height
     */
    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
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
        if (this.alignment != alignment) {
            this.alignment = alignment;
            requestRepaint();
        }
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
        if (font.getSize() != size) {
            Typeface typeface = font.getTypeface();
            Font oldFont = this.font;
            this.font = new Font(typeface, size);
            oldFont.close();
            requestRepaint();
        }
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

        // Draw text using the reusable paint field (no per-frame allocations)
        ensureTextPaint();
        textPaint.setColor(textColor);
        canvas.drawString(text, textX, textY, textPaint, font);
    }

    /**
     * Lazily creates the reusable text paint so {@link #render} never
     * allocates native objects per frame.
     */
    private void ensureTextPaint() {
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public void onMouseEvent(MouseEvent event) {
        // Labels typically don't handle mouse events
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Labels typically don't handle key events
    }

    /**
     * Releases the native Skija resources owned by this label (font and text
     * paint). The shared {@code Typeface} from {@link com.glyphui.graphics.Fonts}
     * is intentionally NOT closed here: it is owned by the cache.
     */
    @Override
    public void dispose() {
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
        if (font != null) {
            font.close();
            font = null;
        }
    }
}
