package com.glyphui.ui;

import com.glyphui.core.Application;
import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.Property;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;
import io.github.humbleui.skija.*;

/**
 * A button component with text, click handler, and visual states.
 */
public class Button extends Component {
    /** Horizontal padding added on each side of the measured text. */
    public static final float HORIZONTAL_PADDING = 24.0f;
    /** Vertical padding added to the measured text height. */
    public static final float VERTICAL_PADDING = 16.0f;

    private String text;
    private Runnable onClick;
    private int normalColor;
    private int hoverColor;
    private int pressedColor;
    private int textColor;
    private int borderColor;
    private float borderRadius;
    private Font font;
    /** Observable text property (lazily created). */
    private Property<String> textProperty;
    
    // Reusable Paint objects to avoid allocation per frame
    private Paint bgPaint;
    private Paint borderPaint;
    private Paint textPaint;

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
        initDefaults();
    }

    /**
     * Creates a new Button without explicit bounds. The size will be derived
     * from the preferred (measured) text size when a layout manager runs.
     *
     * @param text the text displayed on the button
     */
    public Button(String text) {
        super();
        this.text = text;
        initDefaults();
    }

    /**
     * Creates a new Button with default (unset) bounds and empty text.
     */
    public Button() {
        this("");
    }

    /**
     * Initializes default colors, font and reusable paint objects.
     */
    private void initDefaults() {
        this.onClick = null;

        // Default colors
        this.normalColor = Color.makeARGB(255, 60, 60, 60);
        this.hoverColor = Color.makeARGB(255, 80, 80, 80);
        this.pressedColor = Color.makeARGB(255, 100, 100, 100);
        this.textColor = Color.makeARGB(255, 255, 255, 255);
        this.borderColor = Color.makeARGB(255, 120, 120, 120);
        this.borderRadius = 8.0f;

        // Initialize font from the shared cached typeface (cheap Font wrapper)
        this.font = com.glyphui.graphics.Fonts.createDefaultFont(16.0f);

        // Initialize reusable Paint objects
        this.bgPaint = new Paint();
        this.bgPaint.setAntiAlias(true);

        this.borderPaint = new Paint();
        this.borderPaint.setMode(PaintMode.STROKE);
        this.borderPaint.setStrokeWidth(1.0f);
        this.borderPaint.setAntiAlias(true);

        this.textPaint = new Paint();
        this.textPaint.setAntiAlias(true);
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
     * Returns the observable text property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the text property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            textProperty = new Property<>(Application.getCurrent(), text, newText -> {
                this.text = newText;
                requestRepaint();
            });
        }
        return textProperty;
    }

    /**
     * Sets the button text. Delegates to {@link #textProperty()} for
     * backward compatibility and cross-thread safety.
     *
     * @param text the new text
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * Gets the preferred width of this button: measured text width plus
     * horizontal padding on both sides. Falls back to the base defaults when
     * the user provided explicit bounds.
     *
     * @return the preferred width
     */
    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        if (font == null || font.isClosed()) {
            return Math.max(DEFAULT_WIDTH, HORIZONTAL_PADDING * 2);
        }
        return font.measureTextWidth(text == null ? "" : text) + HORIZONTAL_PADDING * 2;
    }

    /**
     * Gets the preferred height of this button: measured text height plus
     * vertical padding. Falls back to the base defaults when the user provided
     * explicit bounds.
     *
     * @return the preferred height
     */
    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        if (font == null || font.isClosed()) {
            return DEFAULT_HEIGHT;
        }
        FontMetrics metrics = font.getMetrics();
        return (metrics.getDescent() - metrics.getAscent()) + VERTICAL_PADDING;
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
     * <p>The handler is invoked from the GLFW event dispatch inside
     * {@code Application.run()}, so it always executes on the UI thread by
     * construction; widget mutations and repaint requests made from it need
     * no synchronization.</p>
     *
     * @param onClick the runnable to execute on click
     */
    public void setOnClick(Runnable onClick) {
        this.onClick = onClick;
    }

    /**
     * Gets the normal state color.
     *
     * @return the normal color (as ARGB int)
     */
    public int getNormalColor() {
        return normalColor;
    }

    /**
     * Sets the normal state color.
     *
     * @param normalColor the new normal color (as ARGB int)
     */
    public void setNormalColor(int normalColor) {
        if (this.normalColor != normalColor) {
            this.normalColor = normalColor;
            requestRepaint();
        }
    }

    /**
     * Gets the hover state color.
     *
     * @return the hover color (as ARGB int)
     */
    public int getHoverColor() {
        return hoverColor;
    }

    /**
     * Sets the hover state color.
     *
     * @param hoverColor the new hover color (as ARGB int)
     */
    public void setHoverColor(int hoverColor) {
        if (this.hoverColor != hoverColor) {
            this.hoverColor = hoverColor;
            requestRepaint();
        }
    }

    /**
     * Gets the pressed state color.
     *
     * @return the pressed color (as ARGB int)
     */
    public int getPressedColor() {
        return pressedColor;
    }

    /**
     * Sets the pressed state color.
     *
     * @param pressedColor the new pressed color (as ARGB int)
     */
    public void setPressedColor(int pressedColor) {
        if (this.pressedColor != pressedColor) {
            this.pressedColor = pressedColor;
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
     * Gets the border color.
     *
     * @return the border color (as ARGB int)
     */
    public int getBorderColor() {
        return borderColor;
    }

    /**
     * Sets the border color.
     *
     * @param borderColor the new border color (as ARGB int)
     */
    public void setBorderColor(int borderColor) {
        if (this.borderColor != borderColor) {
            this.borderColor = borderColor;
            requestRepaint();
        }
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
        if (this.borderRadius != borderRadius) {
            this.borderRadius = borderRadius;
            requestRepaint();
        }
    }

    /**
     * Gets the background color based on the current state.
     *
     * @return the appropriate background color
     */
    private int getBackgroundColor() {
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

        // Update background color based on state
        bgPaint.setColor(getBackgroundColor());

        // Draw background with rounded corners
        canvas.drawRRect(x, y, width, height, borderRadius, borderRadius, bgPaint);

        // Update border color and draw border
        borderPaint.setColor(borderColor);
        canvas.drawRRect(x + 0.5f, y + 0.5f, width - 1.0f, height - 1.0f, 
                        borderRadius, borderRadius, borderPaint);

        // Calculate text position (centered)
        float textWidth = canvas.measureText(text, font);
        float textHeight = canvas.getTextHeight(font);
        float textX = x + (width - textWidth) / 2.0f;
        float textY = y + (height + textHeight) / 2.0f;

        // Update text color based on enabled state
        textPaint.setColor(enabled ? textColor : Color.makeARGB(255, 150, 150, 150));
        
        // Draw text
        canvas.drawString(text, textX, textY, textPaint, font);
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }

        boolean isInside = contains(event.getX(), event.getY());

        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE:
                if (isInside) {
                    if (state != ComponentState.PRESSED) {
                        setState(ComponentState.HOVER);
                    }
                } else {
                    setState(ComponentState.IDLE);
                }
                break;

            case PRESS:
                if (isInside && event.getButton() == com.glyphui.events.MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                }
                break;

            case RELEASE:
                if (state == ComponentState.PRESSED && isInside) {
                    // Execute click handler
                    if (onClick != null) {
                        onClick.run();
                    }
                }
                setState(isInside ? ComponentState.HOVER : ComponentState.IDLE);
                break;

            default:
                break;
        }

        // Request a repaint only when the visual state actually changed.
        // Events inside the button bounds are consumed so they do not reach
        // components underneath.
        if (state != oldState) {
            requestRepaint();
        }
        return isInside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Buttons typically don't handle key events unless focused
        // This can be extended for keyboard accessibility
    }
    
    /**
     * Releases resources held by this button.
     * Should be called when the button is no longer needed.
     */
    public void dispose() {
        if (bgPaint != null) {
            bgPaint.close();
            bgPaint = null;
        }
        if (borderPaint != null) {
            borderPaint.close();
            borderPaint = null;
        }
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
