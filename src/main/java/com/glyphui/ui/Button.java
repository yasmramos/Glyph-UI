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

    /** Observable text property (lazily created). */
    private Property<String> textProperty;

    @Override
    protected String defaultStyleTag() {
        return "button";
    }

    // Optional per-button color overrides; null means "use the computed
    // style / theme". The primitive setters keep the legacy API and store
    // into these boxed fields.
    private Integer normalColorOverride;
    private Integer hoverColorOverride;
    private Integer pressedColorOverride;
    private Integer textColorOverride;
    private Integer borderColorOverride;
    private Float borderRadiusOverride;

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
     * Initializes default paint objects. Colors and fonts are not stored
     * here: they resolve lazily through the computed style / theme so that
     * CSS rules and theme switches apply without re-touching each widget.
     */
    private void initDefaults() {
        this.onClick = null;

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
     * Gets the normal state color. Resolution order: matching CSS rule
     * (e.g. {@code button:hover} when the state is HOVER) → per-instance
     * override → theme default.
     *
     * @return the normal color (as ARGB int)
     */
    public int getNormalColor() {
        return resolveIntStyle(com.glyphui.style.StyleProperty.BACKGROUND,
                normalColorOverride, getTheme().getControlNormalColor());
    }

    /**
     * Overrides the normal state color for this button. Pass null to follow
     * the current theme again.
     *
     * @param normalColor the new normal color (as ARGB int) or null
     */
    public void setNormalColor(Integer normalColor) {
        if (!java.util.Objects.equals(this.normalColorOverride, normalColor)) {
            this.normalColorOverride = normalColor;
            requestRepaint();
        }
    }

    /**
     * Gets the hover state color (CSS rule → override → theme default).
     *
     * @return the hover color (as ARGB int)
     */
    public int getHoverColor() {
        return resolveIntStyle(com.glyphui.style.StyleProperty.BACKGROUND,
                hoverColorOverride, getTheme().getControlHoverColor());
    }

    /**
     * Overrides the hover state color for this button. Pass null to follow
     * the current theme again.
     *
     * @param hoverColor the new hover color (as ARGB int) or null
     */
    public void setHoverColor(Integer hoverColor) {
        if (!java.util.Objects.equals(this.hoverColorOverride, hoverColor)) {
            this.hoverColorOverride = hoverColor;
            requestRepaint();
        }
    }

    /**
     * Gets the pressed state color (CSS rule → override → theme default).
     *
     * @return the pressed color (as ARGB int)
     */
    public int getPressedColor() {
        return resolveIntStyle(com.glyphui.style.StyleProperty.BACKGROUND,
                pressedColorOverride, getTheme().getControlPressedColor());
    }

    /**
     * Overrides the pressed state color for this button. Pass null to follow
     * the current theme again.
     *
     * @param pressedColor the new pressed color (as ARGB int) or null
     */
    public void setPressedColor(Integer pressedColor) {
        if (!java.util.Objects.equals(this.pressedColorOverride, pressedColor)) {
            this.pressedColorOverride = pressedColor;
            requestRepaint();
        }
    }

    /**
     * Gets the text color (CSS rule → override → theme default).
     *
     * @return the text color (as ARGB int)
     */
    public int getTextColor() {
        return resolveIntStyle(com.glyphui.style.StyleProperty.COLOR,
                textColorOverride, getTheme().getControlTextColor());
    }

    /**
     * Overrides the text color for this button. Pass null to follow the
     * current theme again.
     *
     * @param textColor the new text color (as ARGB int) or null
     */
    public void setTextColor(Integer textColor) {
        if (!java.util.Objects.equals(this.textColorOverride, textColor)) {
            this.textColorOverride = textColor;
            requestRepaint();
        }
    }

    /**
     * Gets the border color (CSS rule → override → theme default).
     *
     * @return the border color (as ARGB int)
     */
    public int getBorderColor() {
        return resolveIntStyle(com.glyphui.style.StyleProperty.BORDER_COLOR,
                borderColorOverride, getTheme().getBorderColor());
    }

    /**
     * Overrides the border color for this button. Pass null to follow the
     * current theme again.
     *
     * @param borderColor the new border color (as ARGB int) or null
     */
    public void setBorderColor(Integer borderColor) {
        if (!java.util.Objects.equals(this.borderColorOverride, borderColor)) {
            this.borderColorOverride = borderColor;
            requestRepaint();
        }
    }

    /**
     * Gets the border radius (CSS rule → override → theme default).
     *
     * @return the border radius
     */
    public float getBorderRadius() {
        return resolveFloatStyle(com.glyphui.style.StyleProperty.BORDER_RADIUS,
                borderRadiusOverride, getTheme().getControlCornerRadius());
    }

    /**
     * Overrides the border radius for this button. Pass null to follow the
     * current theme again.
     *
     * @param borderRadius the new border radius or null
     */
    public void setBorderRadius(Float borderRadius) {
        if (!java.util.Objects.equals(this.borderRadiusOverride, borderRadius)) {
            this.borderRadiusOverride = borderRadius;
            requestRepaint();
        }
    }

    /**
     * Gets the font used by this button: a CSS-resolved font when
     * {@code font-*} declarations apply, otherwise the shared theme BUTTON
     * font (owned by the caches/{@code FontManager}; do not close).
     *
     * @return the button font
     */
    io.github.humbleui.skija.Font getFont() {
        return resolveFont(com.glyphui.graphics.Theme.FontRole.BUTTON);
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
        // An explicit CSS width/height (or one set through the base
        // measurement path) wins over text-based intrinsic sizing.
        com.glyphui.style.Style s = getComputedStyle();
        if (s != null && (s.has(com.glyphui.style.StyleProperty.WIDTH)
                || s.has(com.glyphui.style.StyleProperty.HEIGHT))) {
            return super.measure(maxWidth, maxHeight);
        }
        io.github.humbleui.skija.Font f = resolveFont(com.glyphui.graphics.Theme.FontRole.BUTTON);
        float textWidth = f.measureTextWidth(text == null ? "" : text);
        io.github.humbleui.skija.FontMetrics metrics = f.getMetrics();
        float lineHeight = metrics.getDescent() - metrics.getAscent();
        com.glyphui.graphics.Theme theme = getTheme();
        // CSS padding (when declared) overrides the theme's button padding.
        float padH = resolveFloatStyle(com.glyphui.style.StyleProperty.PADDING, null,
                theme != null ? theme.getButtonPaddingHorizontal() : 8f);
        float padV = resolveFloatStyle(com.glyphui.style.StyleProperty.PADDING, null,
                theme != null ? theme.getButtonPaddingVertical() : 4f);
        float w = textWidth + 2.0f * padH;
        float h = lineHeight + 2.0f * padV;
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

    /**
     * Programmatically performs a click: runs the registered onClick handler
     * if present. Useful for tests, keyboard shortcuts and event wiring from
     * markup controllers.
     *
     * @return true if a handler was invoked, false if none is registered
     */
    public boolean performClick() {
        if (onClick != null) {
            onClick.run();
            return true;
        }
        return false;
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
    @Override
    protected void onDispose() {
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
