package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

/**
 * A small pill-shaped label for counts and short statuses.
 *
 * <p>A badge is decorative: it never takes focus and never consumes pointer or
 * key events. It paints its caption on a filled, fully rounded background, so
 * it reads as a tag rather than as body text. The caption can be driven from a
 * background thread through {@link #textProperty()}.</p>
 *
 * <p>Colors resolve like every other widget: a CSS declaration wins, then a
 * per-instance override, then the theme. With no declaration the badge paints
 * the theme accent color behind white text, the conventional "attention"
 * treatment.</p>
 */
public class Badge extends Component {

    /** Horizontal inner padding, logical units. */
    public static final float PADDING_HORIZONTAL = 8.0f;

    /** Vertical inner padding, logical units. */
    public static final float PADDING_VERTICAL = 2.0f;

    private String text;
    private Integer backgroundOverride;
    private Integer textColorOverride;
    private Property<String> textProperty;

    private Paint bgPaint;
    private Paint textPaint;

    /**
     * Creates an empty badge with no explicit bounds.
     */
    public Badge() {
        this("");
    }

    /**
     * Creates a badge showing the supplied caption.
     *
     * @param text the caption (null is treated as empty)
     */
    public Badge(String text) {
        super();
        this.text = text == null ? "" : text;
        // Static decoration: badges never accept keyboard focus.
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "badge";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.BADGE;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null ? accessibleNameOverride() : text;
    }

    /**
     * Gets the badge caption.
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
                // The caption drives the intrinsic size.
                invalidateLayout();
            });
        }
        return textProperty;
    }

    /**
     * Sets the badge caption.
     *
     * @param text the new caption (null is treated as empty)
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * Gets the background color (CSS {@code background}, then per-instance
     * override, then the theme accent color).
     *
     * @return the background color (ARGB)
     */
    public int getBackgroundColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, backgroundOverride,
                getTheme().getAccentColor());
    }

    /**
     * Overrides the background color. Pass null to follow the theme again.
     *
     * @param background the new background color (ARGB) or null
     */
    public void setBackgroundColor(Integer background) {
        if (java.util.Objects.equals(this.backgroundOverride, background)) {
            return;
        }
        this.backgroundOverride = background;
        invalidatePaint();
    }

    /**
     * Gets the caption color (CSS {@code color}, then per-instance override,
     * then white, which stays legible on the default accent background).
     *
     * @return the caption color (ARGB)
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride, 0xFFFFFFFF);
    }

    /**
     * Overrides the caption color. Pass null to follow the theme again.
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

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return font().measureTextWidth(text) + 2.0f * PADDING_HORIZONTAL;
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

        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (height - line) / 2.0f - metrics.getAscent();
        float textWidth = canvas.measureText(text, f);
        float textX = (width - textWidth) / 2.0f;
        textPaint.setColor(enabled
                ? getTextColor()
                : getTheme().getControlTextDisabledColor());
        canvas.drawString(text, textX, baseline, textPaint, f);
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        // Decorative: never consumes pointer events.
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Decorative: badges never take focus or handle keys.
    }

    @Override
    protected void onDispose() {
        if (bgPaint != null) {
            bgPaint.close();
            bgPaint = null;
        }
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
    }
}
