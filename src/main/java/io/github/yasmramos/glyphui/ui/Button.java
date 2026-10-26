package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A button component with text, click handler, and visual states.
 *
 * <p>Design notes:</p>
 * <ul>
 *   <li><b>Lazy paints:</b> the Skija {@link Paint} objects are created on
 *       first {@link #render(Canvas)} call, never in the constructor. This
 *       keeps construction (and therefore unit testing of the event/state
 *       logic) free of any Skija native initialization.</li>
 *   <li><b>Cached measurement:</b> {@link #measure(float, float)} caches its
 *       result; the cache is invalidated whenever the caption, the resolved
 *       style/theme font, padding or explicit-size state changes.</li>
 *   <li><b>Exception-safe handlers:</b> a throwing {@link ClickListener} can
 *       never leave the button stuck in the pressed state.</li>
 * </ul>
 */
public class Button extends Component {
    /**
     * Default horizontal padding added on each side of the measured text.
     * Matches the default theme's button padding; the theme and CSS
     * {@code padding} take precedence at measure time.
     */
    public static final float HORIZONTAL_PADDING = 12.0f;
    /**
     * Default vertical padding added above and below the measured text.
     * Matches the default theme's button padding; the theme and CSS
     * {@code padding} take precedence at measure time.
     */
    public static final float VERTICAL_PADDING = 6.0f;

    /** Logger for click-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Button.class.getName());

    private String text;
    private ClickListener onClick;

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

    // Reusable Paint objects to avoid allocation per frame. Created lazily on
    // the first render() so that constructing a Button (and driving its
    // mouse/key state machine from tests) never touches Skija native code.
    private Paint bgPaint;
    private Paint borderPaint;
    private Paint textPaint;

    // Cached measure() result plus the state it was computed from. Any
    // change to one of those inputs invalidates the cache.
    private io.github.yasmramos.glyphui.graphics.Dimension cachedMeasure;
    private String cachedMeasureText;
    private io.github.humbleui.skija.Font cachedMeasureFont;
    private Float cachedMeasurePadH;
    private Float cachedMeasurePadV;
    private Boolean cachedMeasureExplicit;

    /**
     * Creates a button at (0, 0) with the given size and text - the common
     * case for buttons positioned by their parent's layout.
     *
     * @param width  the width of the button
     * @param height the height of the button
     * @param text   the text displayed on the button
     */
    public Button(float width, float height, String text) {
        this(0, 0, width, height, text);
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
     * Creates a new Button with explicit bounds.
     *
     * @param x      the x-coordinate of the button
     * @param y      the y-coordinate of the button
     * @param width  the width of the button
     * @param height the height of the button
     */
    public Button(float x, float y, float width, float height) {
        this(x, y, width, height, "");
    }

    /**
     * Creates a new Button with explicit bounds and caption.
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
     * Creates a new Button with default (unset) bounds and empty text.
     */
    public Button() {
        this("");
    }

    /**
     * Initializes default button state.
     *
     * <p>No Skija resources are allocated here: the paint objects are built
     * lazily on the first {@link #render(Canvas)} call so the button is fully
     * testable (construction + event handling) without native libraries.</p>
     */
    private void initDefaults() {
        this.onClick = null;
    }

    /** Ensures the reusable paint objects exist (lazy, render-time creation). */
    private void ensurePaints() {
        if (bgPaint == null) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null) {
            borderPaint = new Paint();
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
            borderPaint.setAntiAlias(true);
        }
        if (textPaint == null) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
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
            textProperty = new Property<>(null, text, newText -> {
                this.text = newText;
                invalidateMeasureCache();
                // Caption metrics feed getPreferredWidth(): relayout upward.
                invalidateLayout();
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
     * @return the click listener, or null if none is registered
     */
    public ClickListener getOnClick() {
        return onClick;
    }

    /**
     * Sets the click handler.
     *
     * <p>The handler is invoked from the backend event dispatch inside
     * {@code Application.run()}, so it always executes on the UI thread by
     * construction; widget mutations and repaint requests made from it need
     * no synchronization. Exceptions thrown by the handler are caught and
     * logged so they cannot corrupt the button's visual state.</p>
     *
     * @param onClick the listener to invoke on click (may be null to clear)
     */
    public void setOnClick(ClickListener onClick) {
        this.onClick = onClick;
    }

    /**
     * Clears the current click handler.
     *
     * <p>Note: no {@code setOnClick(Runnable)} overload is provided on
     * purpose. Because {@link ClickListener} is a functional interface with
     * the same abstract-method signature as {@code Runnable#run()}, adding
     * both overloads would make every lambda call site ambiguous at compile
     * time. Code holding a legacy {@code Runnable} should wrap it
     * explicitly, e.g. {@code button.setOnClick(myRunnable::run)}.</p>
     */
    public void clearOnClick() {
        this.onClick = null;
    }

    /**
     * Gets the background color for the button's <em>normal</em> (idle)
     * state. Resolution order: matching CSS rule → per-instance override
     * ({@link #setNormalColor}) → theme default.
     *
     * @return the normal-state color (as ARGB int)
     */
    public int getNormalColor() {
        return resolveStateColor(ComponentState.IDLE);
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
     * Gets the background color shown while the pointer hovers over the
     * button. This getter always reports the <em>hover</em>-state color,
     * regardless of the button's current state. Resolution: matching CSS
     * rule → per-instance override ({@link #setHoverColor}) → theme default.
     *
     * @return the hover-state color (as ARGB int)
     */
    public int getHoverColor() {
        return resolveStateColor(ComponentState.HOVER);
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
     * Gets the background color shown while the button is pressed. Always
     * reports the <em>pressed</em> state color regardless of the button's
     * current state. Resolution: CSS {@code :active} rule → per-instance
     * override ({@link #setPressedColor}) → theme default.
     *
     * @return the pressed-state color (as ARGB int)
     */
    public int getPressedColor() {
        return resolveStateColor(ComponentState.PRESSED);
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
     * Resolves the background color the button uses when it is in the given
     * interaction state, applying the documented cascade: per-instance
     * override ({@code setHoverColor} etc.) → theme default for that state.
     * A CSS {@code background} declaration on the base selector applies to
     * every state and therefore wins over both (see
     * {@link #getNormalColor()}, which documents the same precedence).
     *
     * @param targetState the state whose color is requested
     * @return the ARGB color for that state
     */
    private int resolveStateColor(ComponentState targetState) {
        Integer override;
        int themeDefault;
        switch (targetState) {
            case PRESSED:
                override = pressedColorOverride;
                themeDefault = getTheme().getControlPressedColor();
                break;
            case HOVER:
                override = hoverColorOverride;
                themeDefault = getTheme().getControlHoverColor();
                break;
            default:
                override = normalColorOverride;
                themeDefault = getTheme().getControlNormalColor();
        }
        return resolveIntStyle(io.github.yasmramos.glyphui.style.StyleProperty.BACKGROUND,
                override, themeDefault);
    }

    /**
     * Gets the text color (CSS rule → override → theme default).
     *
     * @return the text color (as ARGB int)
     */
    public int getTextColor() {
        return resolveIntStyle(io.github.yasmramos.glyphui.style.StyleProperty.COLOR,
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
        return resolveIntStyle(io.github.yasmramos.glyphui.style.StyleProperty.BORDER_COLOR,
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
        return resolveFloatStyle(io.github.yasmramos.glyphui.style.StyleProperty.BORDER_RADIUS,
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
        return resolveFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON);
    }

    @Override
    public io.github.yasmramos.glyphui.ui.AccessibleRole getAccessibleRole() {
        return io.github.yasmramos.glyphui.ui.AccessibleRole.BUTTON;
    }

    @Override
    public String getAccessibleName() {
        // Buttons are named by their caption unless explicitly overridden.
        // A null/blank caption must never yield a null accessible name: fall
        // back to a generic role label so assistive tech announces "button"
        // instead of nothing.
        String override = accessibleNameOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        if (text != null && !text.isEmpty()) {
            return text;
        }
        return "Button";
    }

    @Override
    public float getPreferredWidth() {
        // User-provided bounds are the preferred size (see Component).
        if (sizeExplicitlySet) {
            return width;
        }
        return measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).getWidth();
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).getHeight();
    }

    /** Drops the cached measurement so the next measure() recomputes. */
    private void invalidateMeasureCache() {
        cachedMeasure = null;
    }

    /**
     * Measures the intrinsic size of the button: the caption width plus the
     * theme's horizontal padding, and the font line height plus the theme's
     * vertical padding, both clamped to the supplied maximums.
     *
     * <p>The result is cached: repeated layout passes reuse it until the
     * caption, resolved font/padding, or explicit-size state changes. The
     * cache key stores strong references to the inputs it captured, so a
     * later GC of intermediate objects can never make a stale hit look
     * valid.</p>
     *
     * @param maxWidth  the maximum available width
     * @param maxHeight the maximum available height
     * @return the measured dimension
     */
    @Override
    public io.github.yasmramos.glyphui.graphics.Dimension measure(float maxWidth, float maxHeight) {
        // An explicit CSS width/height (or one set through the base
        // measurement path) wins over text-based intrinsic sizing.
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        boolean cssSized = s != null && (s.has(io.github.yasmramos.glyphui.style.StyleProperty.WIDTH)
                || s.has(io.github.yasmramos.glyphui.style.StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            // Explicit bounds (bounds constructor / setWidth) behave like an
            // explicit size: no text-based intrinsic sizing.
            return super.measure(maxWidth, maxHeight);
        }

        io.github.humbleui.skija.Font f = resolveFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON);
        io.github.yasmramos.glyphui.graphics.Theme theme = getTheme();
        // CSS padding (when declared) overrides the theme's button padding.
        float padH = resolveFloatStyle(io.github.yasmramos.glyphui.style.StyleProperty.PADDING, null,
                theme != null ? theme.getButtonPaddingHorizontal() : HORIZONTAL_PADDING);
        float padV = resolveFloatStyle(io.github.yasmramos.glyphui.style.StyleProperty.PADDING, null,
                theme != null ? theme.getButtonPaddingVertical() : VERTICAL_PADDING);

        io.github.yasmramos.glyphui.graphics.Dimension base = cachedMeasure;
        boolean cacheHit = base != null
                && java.util.Objects.equals(cachedMeasureText, text)
                && cachedMeasureFont == f
                && cachedMeasurePadH != null && cachedMeasurePadH == padH
                && cachedMeasurePadV != null && cachedMeasurePadV == padV
                && Boolean.valueOf(sizeExplicitlySet).equals(cachedMeasureExplicit);
        if (!cacheHit) {
            float textWidth = f.measureTextWidth(text == null ? "" : text);
            io.github.humbleui.skija.FontMetrics metrics = f.getMetrics();
            float lineHeight = metrics.getDescent() - metrics.getAscent();
            base = new io.github.yasmramos.glyphui.graphics.Dimension(
                    textWidth + 2.0f * padH, lineHeight + 2.0f * padV);
            // Remember exactly which inputs produced this value.
            cachedMeasure = base;
            cachedMeasureText = text;
            cachedMeasureFont = f;
            cachedMeasurePadH = padH;
            cachedMeasurePadV = padV;
            cachedMeasureExplicit = sizeExplicitlySet;
        }
        return new io.github.yasmramos.glyphui.graphics.Dimension(
                Math.min(base.getWidth(), sanitizeConstraint(maxWidth)),
                Math.min(base.getHeight(), sanitizeConstraint(maxHeight)));
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
            // Consult the computed style first so `button:disabled { ... }`
            // rules win over every fallback (the engine re-cascades on
            // enablement changes, so a :disabled declaration is present
            // here). A base `button { background: ... }` rule also applies
            // while disabled — CSS has no implicit dimming. Only when no CSS
            // declaration exists do we fall back to an explicit per-state
            // override (setNormalColor must not leak into the disabled
            // appearance) and then to the theme's disabled color.
            Theme theme = getTheme();
            if (hasStyleValue(StyleProperty.BACKGROUND)) {
                return resolveIntStyle(StyleProperty.BACKGROUND, null,
                        theme.getControlDisabledColor());
            }
            if (normalColorOverride != null) {
                return normalColorOverride;
            }
            return theme.getControlDisabledColor();
        }

        switch (state) {
            case PRESSED:
                return resolveStateColor(ComponentState.PRESSED);
            case HOVER:
                return resolveStateColor(ComponentState.HOVER);
            default:
                return resolveStateColor(ComponentState.IDLE);
        }
    }

    @Override
    protected void render(Canvas canvas) {
        if (!visible) {
            return;
        }

        // Paints are created on demand: the constructor never touches Skija,
        // so buttons are unit-testable without native libraries.
        ensurePaints();

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
        String caption = text == null ? "" : text;
        float textWidth = canvas.measureText(caption, f);
        float textHeight = canvas.getTextHeight(f);
        float textX = x + (width - textWidth) / 2.0f;
        // drawString takes a baseline. The glyph box spans
        // [baseline + ascent, baseline + descent] (ascent is negative), so
        // centering that box in the button puts the baseline at
        // top + (height - textHeight) / 2 - ascent. The former
        // (height + textHeight) / 2 sat descent pixels too low.
        io.github.humbleui.skija.FontMetrics metrics = f.getMetrics();
        float textY = y + (height - textHeight) / 2.0f - metrics.getAscent();

        // Update text color based on enabled state
        textPaint.setColor(enabled ? getTextColor() : getTheme().getControlTextDisabledColor());

        // Draw text
        canvas.drawString(caption, textX, textY, textPaint, f);

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
                if (isInside && event.getButton() == io.github.yasmramos.glyphui.events.MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                    // Clicking a control gives it the keyboard focus
                    requestFocus();
                }
                break;

            case RELEASE:
                if (state == ComponentState.PRESSED && isInside) {
                    // Execute click handler. runClick() catches handler
                    // exceptions, and the state transition below runs
                    // unconditionally, so a failing handler can never leave
                    // the button stuck in PRESSED.
                    runClick();
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
     * Programmatically performs a click: runs the registered click handler
     * if present. Useful for tests, keyboard shortcuts and event wiring from
     * markup controllers.
     *
     * @return true if a handler was invoked, false if none is registered
     */
    public boolean performClick() {
        return runClick();
    }

    /**
     * Invokes the registered handler defensively: exceptions are caught and
     * logged so a faulty callback cannot propagate into the event loop or
     * leave the widget in an inconsistent state.
     *
     * @return true if a handler was present and called (even if it threw)
     */
    private boolean runClick() {
        ClickListener handler = onClick;
        if (handler == null) {
            return false;
        }
        try {
            handler.onClick();
        } catch (RuntimeException e) {
            // Only runtime exceptions are swallowed; Errors (OOM, StackOverflow)
            // must propagate, matching the Reactor's exception policy.
            LOG.log(Level.WARNING, "Button click handler threw; state kept consistent", e);
        }
        return true;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Keyboard activation for focused buttons. Key codes come from the
        // toolkit's own GlyphKeys constants — never hardcoded native values.
        if (!enabled || !isFocused()) {
            return;
        }
        int key = event.getKeyCode();
        // Enter activates on press (auto-repeat keeps firing while held).
        if (event.getType() == KeyEventType.PRESS && key == GlyphKeys.ENTER) {
            runClick();
            return;
        }
        // Space follows the standard button convention: visually depress on
        // press, activate on release.
        if (key == GlyphKeys.SPACE) {
            if (event.getType() == KeyEventType.PRESS) {
                if (state != ComponentState.PRESSED) {
                    setState(ComponentState.PRESSED);
                }
            } else if (event.getType() == KeyEventType.RELEASE
                    && state == ComponentState.PRESSED) {
                runClick();
                setState(ComponentState.IDLE);
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
        invalidateMeasureCache();
        // The font comes from FontManager/Theme and is shared — do not close.
    }
}
