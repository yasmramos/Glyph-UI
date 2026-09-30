package com.glyphui.ui;

import com.glyphui.core.Application;
import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.Property;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.KeyEvent;
import com.glyphui.events.KeyEventType;
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
    public Button(float x, float y, float width, float height) {
        this(x, y, width, height, "");
    }

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
     * Gets the normal state color (override or theme value).
     *
     * @return the normal color (as ARGB int)
     */
    public int getNormalColor() {
        return normalColorOverride != null ? normalColorOverride : getTheme().getControlNormalColor();
    }

    /**
     * Overrides the normal state color for this button. Pass null to follow
     * the current theme again.
     *
     * @param normalColor the new normal color (as ARGB int) or null
     */
    public void setNormalColor(int normalColor) {
        if (this.normalColor != normalColor) {
            this.normalColor = normalColor;
            requestRepaint();
        }
    }

    /**
     * Gets the hover state color (override or theme value).
     *
     * @return the hover color (as ARGB int)
     */
    public int getHoverColor() {
        return hoverColorOverride != null ? hoverColorOverride : getTheme().getControlHoverColor();
    }

    /**
     * Overrides the hover state color for this button. Pass null to follow
     * the current theme again.
     *
     * @param hoverColor the new hover color (as ARGB int) or null
     */
    public void setHoverColor(int hoverColor) {
        if (this.hoverColor != hoverColor) {
            this.hoverColor = hoverColor;
            requestRepaint();
        }
    }

    /**
     * Gets the pressed state color (override or theme value).
     *
     * @return the pressed color (as ARGB int)
     */
    public int getPressedColor() {
        return pressedColorOverride != null ? pressedColorOverride : getTheme().getControlPressedColor();
    }

    /**
     * Overrides the pressed state color for this button. Pass null to follow
     * the current theme again.
     *
     * @param pressedColor the new pressed color (as ARGB int) or null
     */
    public void setPressedColor(int pressedColor) {
        if (this.pressedColor != pressedColor) {
            this.pressedColor = pressedColor;
            requestRepaint();
        }
    }

    /**
     * Gets the text color (override or theme value).
     *
     * @return the text color (as ARGB int)
     */
    public int getTextColor() {
        return textColorOverride != null ? textColorOverride : getTheme().getControlTextColor();
    }

    /**
     * Overrides the text color for this button. Pass null to follow the
     * current theme again.
     *
     * @param textColor the new text color (as ARGB int) or null
     */
    public void setTextColor(int textColor) {
        if (this.textColor != textColor) {
            this.textColor = textColor;
            requestRepaint();
        }
    }

    /**
     * Gets the border color (override or theme value).
     *
     * @return the border color (as ARGB int)
     */
    public int getBorderColor() {
        return borderColorOverride != null ? borderColorOverride : getTheme().getBorderColor();
    }

    /**
     * Overrides the border color for this button. Pass null to follow the
     * current theme again.
     *
     * @param borderColor the new border color (as ARGB int) or null
     */
    public void setBorderColor(int borderColor) {
        if (this.borderColor != borderColor) {
            this.borderColor = borderColor;
            requestRepaint();
        }
    }

    /**
     * Gets the border radius (override or theme value).
     *
     * @return the border radius
     */
    public float getBorderRadius() {
        return borderRadiusOverride != null ? borderRadiusOverride : getTheme().getControlCornerRadius();
    }

    /**
     * Overrides the border radius for this button. Pass null to follow the
     * current theme again.
     *
     * @param borderRadius the new border radius or null
     */
    public void setBorderRadius(float borderRadius) {
        if (this.borderRadius != borderRadius) {
            this.borderRadius = borderRadius;
            requestRepaint();
        }
    }

    /**
     * Gets the font used by this button (the shared theme BUTTON font; do
     * not close it — it is owned by {@code FontManager}).
     *
     * @return the button font
     */
    private io.github.humbleui.skija.Font getFont() {
        return getTheme().getFont(com.glyphui.graphics.Theme.FontRole.BUTTON);
    }

    @Override
    public com.glyphui.ui.AccessibleRole getAccessibleRole() {
        return com.glyphui.ui.AccessibleRole.BUTTON;
    }

    @Override
    public String getAccessibleName() {
        // Buttons are named by their caption unless explicitly overridden
        return super.getAccessibleName() != null && !super.getAccessibleName().equals(getId())
                ? super.getAccessibleName() : text;
    }

    @Override
    public float getPreferredWidth() {
        return measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).getWidth();
    }

    @Override
    public float getPreferredHeight() {
        return measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).getHeight();
    }

    /**
     * Measures the intrinsic size of the button: the caption width plus the
     * theme's horizontal padding, and the font line height plus the theme's
     * vertical padding, both clamped to the supplied maximums.
     *
     * @param maxWidth  the maximum available width
     * @param maxHeight the maximum available height
     * @return the measured dimension
     */
    @Override
    public com.glyphui.graphics.Dimension measure(float maxWidth, float maxHeight) {
        io.github.humbleui.skija.Font f = getFont();
        float textWidth = f.measureTextWidth(text == null ? "" : text);
        io.github.humbleui.skija.FontMetrics metrics = f.getMetrics();
        float lineHeight = metrics.getDescent() - metrics.getAscent();
        com.glyphui.graphics.Theme theme = getTheme();
        float w = textWidth + 2.0f * theme.getButtonPaddingHorizontal();
        float h = lineHeight + 2.0f * theme.getButtonPaddingVertical();
        return new com.glyphui.graphics.Dimension(
                Math.min(w, sanitizeConstraint(maxWidth)),
                Math.min(h, sanitizeConstraint(maxHeight)));
    }

    /** Clamps non-finite constraints so measure() stays well-defined. */
    private static float sanitizeConstraint(float value) {
        if (Float.isNaN(value) || value == Float.POSITIVE_INFINITY) {
            return Float.MAX_VALUE;
        }
        return Math.max(0.0f, value);
    }

    /**
     * Gets the background color based on the current state.
     *
     * @return the appropriate background color
     */
    private int getBackgroundColor() {
        if (!enabled) {
            return getTheme().getControlDisabledColor();
        }
        
        switch (state) {
            case PRESSED:
                return getPressedColor();
            case HOVER:
                return getHoverColor();
            default:
                return getNormalColor();
        }
    }

    @Override
    public void render(Canvas canvas) {
        if (!visible) {
            return;
        }

        // Update background color based on state
        bgPaint.setColor(getBackgroundColor());

        float radius = getBorderRadius();

        // Draw background with rounded corners
        canvas.drawRRect(x, y, width, height, radius, radius, bgPaint);

        // Update border color and draw border
        borderPaint.setColor(getBorderColor());
        canvas.drawRRect(x + 0.5f, y + 0.5f, width - 1.0f, height - 1.0f,
                        radius, radius, borderPaint);

        // Calculate text position (centered) using the theme font
        io.github.humbleui.skija.Font f = getFont();
        float textWidth = canvas.measureText(text, f);
        float textHeight = canvas.getTextHeight(f);
        float textX = x + (width - textWidth) / 2.0f;
        float textY = y + (height + textHeight) / 2.0f;

        // Update text color based on enabled state
        textPaint.setColor(enabled ? getTextColor() : getTheme().getControlTextDisabledColor());

        // Draw text
        canvas.drawString(text, textX, textY, textPaint, f);

        // Focus ring when this button holds the keyboard focus
        if (isFocused()) {
            renderFocusRing(canvas);
        }
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
                    // Clicking a control gives it the keyboard focus
                    requestFocus();
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

    /** GLFW key codes handled without importing LWJGL into the widget layer. */
    private static final int KEY_ENTER = 257;   // GLFW_KEY_ENTER
    private static final int KEY_KP_ENTER = 335; // GLFW_KEY_KP_ENTER
    private static final int KEY_SPACE = 32;    // GLFW_KEY_SPACE

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Keyboard activation for focused buttons: Enter / Space trigger the
        // click handler on press (and Space on release, matching common UI
        // conventions closely enough for v0.1).
        if (!enabled || !isFocused() || event.getType() != KeyEventType.PRESS) {
            return;
        }
        int key = event.getKeyCode();
        if (key == KEY_ENTER || key == KEY_KP_ENTER || key == KEY_SPACE) {
            if (onClick != null) {
                onClick.run();
            }
        }
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
        // The font comes from FontManager/Theme and is shared — do not close.
    }
}
