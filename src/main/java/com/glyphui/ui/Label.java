package com.glyphui.ui;

import com.glyphui.core.Application;
import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.Property;
import com.glyphui.style.Style;
import com.glyphui.style.StyleProperty;
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
    /** Reusable text paint: created once, reused every frame, closed in dispose(). */
    private Paint textPaint;
    /** Observable text property (lazily created). */
    private Property<String> textProperty;

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
     * Initializes default state: left alignment and no keyboard focus
     * (labels are static, non-interactive widgets). Colors and fonts are
     * resolved from the computed style / {@code Theme.current()} lazily at
     * render time, so nothing native is allocated here.
     */
    private void initDefaults() {
        this.alignment = TextAlignment.LEFT;
        // Static labels do not accept keyboard focus
        setFocusable(false);
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.LABEL;
    }

    @Override
    public String getAccessibleName() {
        // Labels are named by their caption unless explicitly overridden.
        // The base default is now the style tag (never the auto-generated
        // id), so an explicit check for the override is all that remains.
        return accessibleNameOverride() != null ? accessibleNameOverride() : text;
    }

    @Override
    protected String defaultStyleTag() {
        return "label";
    }

    /**
     * Gets the font used by this label. Resolution order: CSS
     * {@code font-family}/{@code font-size} declarations (through
     * {@link Component#resolveFont}), then the per-instance size override,
     * then the shared theme BODY font — owned by {@code FontManager} or the
     * style-font cache, do not close.
     *
     * @return the label font
     */
    private io.github.humbleui.skija.Font getFont() {
        Style s = getComputedStyle();
        if (s.has(StyleProperty.FONT_FAMILY) || s.has(StyleProperty.FONT_SIZE)) {
            return resolveFont(com.glyphui.graphics.Theme.FontRole.BODY);
        }
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
     * Returns the observable text property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the text property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            textProperty = new Property<>(Application.getCurrent(), text, newText -> {
                this.text = newText;
                // Single invalidation: requestRepaint() delegates to
                // invalidate(), calling both would double-count and
                // double-propagate up the parent chain.
                invalidate();
            });
        }
        return textProperty;
    }

    /**
     * Sets the label text. Delegates to {@link #textProperty()} for
     * backward compatibility and cross-thread safety.
     *
     * @param text the new text
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * Gets the text color (CSS {@code color}, then per-instance override,
     * then the theme's foreground color).
     *
     * @return the text color (as ARGB int)
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride,
                getTheme().getForegroundColor());
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
     * Gets the preferred width of this label: the measured caption width
     * plus the theme padding. Falls back to the base defaults when the user
     * provided explicit bounds.
     *
     * @return the preferred width
     */
    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        // The widest line governs: a wrapped caption must not be measured as if
        // every line started at the same x.
        io.github.humbleui.skija.Font f = getFont();
        float widest = 0.0f;
        for (String line : textLines()) {
            widest = Math.max(widest, f.measureTextWidth(line));
        }
        return widest + 2.0f * getTheme().getPadding();
    }

    /**
     * Gets the preferred height of this label: one line of the resolved font
     * plus the theme padding.
     *
     * @return the preferred height
     */
    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        FontMetrics metrics = getFont().getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return line * textLines().length + 2.0f * getTheme().getPadding();
    }

    /**
     * Splits the caption into rendered lines. {@code null} and the empty
     * string both yield a single empty line so the label keeps a sane height,
     * and a trailing break still counts as a line rather than being dropped.
     *
     * @return the lines to render (never null or empty)
     */
    private String[] textLines() {
        String value = text == null ? "" : text;
        // Normalise Windows line endings so "a\r\nb" is two lines, not three.
        return value.split("\r?\n", -1);
    }

    /**
     * Measures the label with its resolved font plus padding, clamped to the
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
        if (this.alignment != alignment) {
            this.alignment = alignment;
            invalidate();
        }
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
     * current theme again. Fonts are shared through {@code FontManager} /
     * the style-font cache so nothing needs to be closed here.
     *
     * @param size the new font size or null
     */
    public void setFontSize(Float size) {
        this.fontSizeOverride = size;
        invalidate();
    }

    @Override
    protected void render(Canvas canvas) {
        if (!visible) {
            return;
        }

        io.github.humbleui.skija.Font f = getFont();
        io.github.humbleui.skija.FontMetrics metrics = f.getMetrics();
        String[] lines = textLines();
        float lineStep = metrics.getDescent() - metrics.getAscent();

        // The whole block is centered vertically, so with a single line this
        // reduces exactly to the single-line baseline below. drawString takes
        // a baseline: the glyph box spans [baseline + ascent, baseline +
        // descent] (ascent is negative), so centering that box puts the first
        // baseline at top + (height - blockHeight) / 2 - ascent.
        float blockHeight = lineStep * lines.length;
        float firstBaseline = y + (height - blockHeight) / 2.0f - metrics.getAscent();

        // Draw text using the reusable paint field (no per-frame allocations)
        ensureTextPaint();
        textPaint.setColor(enabled ? getTextColor() : getTheme().getControlTextDisabledColor());

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            float lineWidth = canvas.measureText(line, f);
            float textX;
            switch (alignment) {
                case CENTER:
                    textX = x + (width - lineWidth) / 2.0f;
                    break;
                case RIGHT:
                    textX = x + width - lineWidth;
                    break;
                default:
                    textX = x;
                    break;
            }
            canvas.drawString(line, textX, firstBaseline + i * lineStep, textPaint, f);
        }
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
    public boolean onMouseEvent(MouseEvent event) {
        // Labels are inert: they never consume mouse events
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Labels typically don't handle key events
    }

    /**
     * Releases the native Skija resources owned by this label (the text
     * paint). The shared {@code Typeface}/{@code Font} instances are owned
     * by {@link com.glyphui.graphics.Fonts} and the style-font cache and are
     * intentionally NOT closed here.
     */
    @Override
    protected void onDispose() {
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
    }
}
