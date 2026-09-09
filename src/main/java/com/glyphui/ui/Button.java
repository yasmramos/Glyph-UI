package com.glyphui.ui;

import com.glyphui.graphics.Canvas;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;
import io.github.humbleui.skija.*;

/**
 * A button component with text, click handler, and visual states.
 */
public class Button extends Component {
    private String text;
    private Runnable onClick;
    private Color normalColor;
    private Color hoverColor;
    private Color pressedColor;
    private Color textColor;
    private Color borderColor;
    private float borderRadius;
    private Font font;

    /**
     * Creates a new Button.
     *
     * @param x      the x-coordinate of the button
     * @param y      the y-coordinate of the button
     * @param width  the width of the button
     * @param height the height of the button
     * @param text   the text displayed on the button
     */
    public Button(float x, float y, float width, float height, String text) {
        super(x, y, width, height);
        this.text = text;
        this.onClick = null;
        
        // Default colors
        this.normalColor = Color.makeARGB(255, 60, 60, 60);
        this.hoverColor = Color.makeARGB(255, 80, 80, 80);
        this.pressedColor = Color.makeARGB(255, 100, 100, 100);
        this.textColor = Color.makeARGB(255, 255, 255, 255);
        this.borderColor = Color.makeARGB(255, 120, 120, 120);
        this.borderRadius = 8.0f;
        
        // Initialize font
        Typeface typeface = Typeface.makeFromName(null, FontStyle.NORMAL);
        this.font = new Font(typeface, 16.0f);
    }

    /**
     * Gets the button text.
     *
     * @return the text
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the button text.
     *
     * @param text the new text
     */
    public void setText(String text) {
        this.text = text;
    }

    /**
     * Gets the click handler.
     *
     * @return the onClick runnable
     */
    public Runnable getOnClick() {
        return onClick;
    }

    /**
     * Sets the click handler.
     *
     * @param onClick the runnable to execute on click
     */
    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    /**
     * Gets the normal state color.
     *
     * @return the normal color
     */
    public Color getNormalColor() {
        return normalColor;
    }

    /**
     * Sets the normal state color.
     *
     * @param normalColor the new normal color
     */
    public void setNormalColor(Color normalColor) {
        this.normalColor = normalColor;
    }

    /**
     * Gets the hover state color.
     *
     * @return the hover color
     */
    public Color getHoverColor() {
        return hoverColor;
    }

    /**
     * Sets the hover state color.
     *
     * @param hoverColor the new hover color
     */
    public void setHoverColor(Color hoverColor) {
        this.hoverColor = hoverColor;
    }

    /**
     * Gets the pressed state color.
     *
     * @return the pressed color
     */
    public Color getPressedColor() {
        return pressedColor;
    }

    /**
     * Sets the pressed state color.
     *
     * @param pressedColor the new pressed color
     */
    public void setPressedColor(Color pressedColor) {
        this.pressedColor = pressedColor;
    }

    /**
     * Gets the text color.
     *
     * @return the text color
     */
    public Color getTextColor() {
        return textColor;
    }

    /**
     * Sets the text color.
     *
     * @param textColor the new text color
     */
    public void setTextColor(Color textColor) {
        this.textColor = textColor;
    }

    /**
     * Gets the border color.
     *
     * @return the border color
     */
    public Color getBorderColor() {
        return borderColor;
    }

    /**
     * Sets the border color.
     *
     * @param borderColor the new border color
     */
    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
    }

    /**
     * Gets the border radius.
     *
     * @return the border radius
     */
    public float getBorderRadius() {
        return borderRadius;
    }

    /**
     * Sets the border radius.
     *
     * @param borderRadius the new border radius
     */
    public void setBorderRadius(float borderRadius) {
        this.borderRadius = borderRadius;
    }

    /**
     * Gets the background color based on the current state.
     *
     * @return the appropriate background color
     */
    private Color getBackgroundColor() {
        if (!enabled) {
            return Color.makeARGB(255, 50, 50, 50);
        }
        
        switch (state) {
            case PRESSED:
                return pressedColor;
            case HOVER:
                return hoverColor;
            default:
                return normalColor;
        }
    }

    @Override
    public void render(Canvas canvas) {
        if (!visible) {
            return;
        }

        // Draw background with rounded corners
        Paint bgPaint = new Paint();
        bgPaint.setColor(getBackgroundColor());
        bgPaint.setAntiAlias(true);
        canvas.drawRRect(x, y, width, height, borderRadius, borderRadius, bgPaint);

        // Draw border
        Paint borderPaint = new Paint();
        borderPaint.setColor(borderColor);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(1.0f);
        borderPaint.setAntiAlias(true);
        canvas.drawRRect(x + 0.5f, y + 0.5f, width - 1.0f, height - 1.0f, 
                        borderRadius, borderRadius, borderPaint);

        // Calculate text position (centered)
        float textWidth = canvas.measureText(text, font);
        float textHeight = canvas.getTextHeight(font);
        float textX = x + (width - textWidth) / 2.0f;
        float textY = y + (height + textHeight) / 2.0f;

        // Draw text
        Paint textPaint = new Paint();
        textPaint.setColor(enabled ? textColor : Color.makeARGB(255, 150, 150, 150));
        textPaint.setAntiAlias(true);
        canvas.drawString(text, textX, textY, textPaint, font);

        // Clean up paints
        bgPaint.close();
        borderPaint.close();
        textPaint.close();
    }

    @Override
    public void onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return;
        }

        boolean isInside = contains(event.getX(), event.getY());

        switch (event.getType()) {
            case MOVE:
                if (isInside) {
                    if (state != ComponentState.PRESSED) {
                        state = ComponentState.HOVER;
                    }
                } else {
                    state = ComponentState.IDLE;
                }
                break;

            case PRESS:
                if (isInside && event.getButton() == MouseButton.LEFT) {
                    state = ComponentState.PRESSED;
                }
                break;

            case RELEASE:
                if (state == ComponentState.PRESSED && isInside) {
                    // Execute click handler
                    if (onClick != null) {
                        onClick.run();
                    }
                }
                state = isInside ? ComponentState.HOVER : ComponentState.IDLE;
                break;

            default:
                break;
        }
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Buttons typically don't handle key events unless focused
        // This can be extended for keyboard accessibility
    }
}
