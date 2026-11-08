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
import io.github.humbleui.skija.PaintMode;

/**
 * An inline status message with a severity level.
 *
 * <p>An alert paints a tinted, rounded banner with an accent strip on the
 * leading edge and its message text beside it. The {@link Level} selects the
 * accent color; the banner fill is that accent blended over the surface so it
 * stays legible in both themes. The message can be driven from a background
 * thread through {@link #textProperty()}.</p>
 *
 * <p>Alerts are informational: they never take focus and never consume pointer
 * or key events.</p>
 */
public class Alert extends Component implements Textual {

    /** Severity of an alert, which selects its accent color. */
    public enum Level {
        /** Neutral information. */
        INFO,
        /** A successful outcome. */
        SUCCESS,
        /** A recoverable problem. */
        WARNING,
        /** A failed operation. */
        ERROR
    }

    /** Horizontal inner padding, logical units. */
    public static final float PADDING_HORIZONTAL = 12.0f;

    /** Vertical inner padding, logical units. */
    public static final float PADDING_VERTICAL = 8.0f;

    /** Width of the accent strip on the leading edge, logical units. */
    public static final float ACCENT_STRIP_WIDTH = 4.0f;

    /** Fraction of the accent color blended into the surface for the fill. */
    private static final float FILL_BLEND = 0.15f;

    private String text;
    private Level level;
    private Integer accentColorOverride;
    private Property<String> textProperty;

    private Paint bgPaint;
    private Paint accentPaint;
    private Paint textPaint;

    /**
     * Creates an informational alert with no message.
     */
    public Alert() {
        this(Level.INFO, "");
    }

    /**
     * Creates an alert with the supplied level and message.
     *
     * @param level the severity (null defaults to {@link Level#INFO})
     * @param text  the message (null is treated as empty)
     */
    public Alert(Level level, String text) {
        super();
        this.level = level == null ? Level.INFO : level;
        this.text = text == null ? "" : text;
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "alert";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.ALERT;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null ? accessibleNameOverride() : text;
    }

    /**
     * Gets the alert message.
     *
     * @return the message, never null
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the observable message property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the message property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            textProperty = new Property<>(propertyOwner(), text, newText -> {
                this.text = newText == null ? "" : newText;
                invalidateLayout();
            });
        }
        return textProperty;
    }

    /**
     * Sets the alert message.
     *
     * @param text the new message (null is treated as empty)
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * Gets the severity level.
     *
     * @return the level, never null
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Sets the severity level, which selects the accent color.
     *
     * @param level the new level (null defaults to {@link Level#INFO})
     */
    public void setLevel(Level level) {
        Level next = level == null ? Level.INFO : level;
        if (next == level) {
            return;
        }
        this.level = next;
        invalidatePaint();
    }

    /**
     * Gets the accent color for the current level, honouring the CSS
     * {@code accent-color} declaration and a per-instance override first.
     *
     * @return the accent color (ARGB)
     */
    public int getAccentColor() {
        int fallback = switch (level) {
            case SUCCESS -> 0xFF2E7D32;
            case WARNING -> 0xFFED6C02;
            case ERROR -> 0xFFD32F2F;
            case INFO -> getTheme().getAccentColor();
        };
        return resolveIntStyle(StyleProperty.ACCENT_COLOR, accentColorOverride, fallback);
    }

    /**
     * Overrides the accent color. Pass null to follow the level and theme.
     *
     * @param accentColor the new accent color (ARGB) or null
     */
    public void setAccentColor(Integer accentColor) {
        if (java.util.Objects.equals(this.accentColorOverride, accentColor)) {
            return;
        }
        this.accentColorOverride = accentColor;
        invalidatePaint();
    }

    /**
     * The banner fill: the accent blended over the surface color so the tint
     * follows the theme while still reading as the level's color.
     */
    private int fillColor() {
        return blend(getTheme().getInputBackgroundColor(), getAccentColor(), FILL_BLEND);
    }

    /** Linear blend of {@code from} toward {@code to} by {@code t} in [0, 1]. */
    private static int blend(int from, int to, float t) {
        int a = Math.round(((from >>> 24) & 0xFF) * (1 - t) + ((to >>> 24) & 0xFF) * t);
        int r = Math.round(((from >>> 16) & 0xFF) * (1 - t) + ((to >>> 16) & 0xFF) * t);
        int g = Math.round(((from >>> 8) & 0xFF) * (1 - t) + ((to >>> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return font().measureTextWidth(text)
                + ACCENT_STRIP_WIDTH + 2.0f * PADDING_HORIZONTAL;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        FontMetrics metrics = font().getMetrics();
        return metrics.getDescent() - metrics.getAscent() + 2.0f * PADDING_VERTICAL;
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

        float radius = getTheme().getControlCornerRadius();
        bgPaint.setColor(fillColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, bgPaint);

        accentPaint.setColor(getAccentColor());
        canvas.drawRect(0, 0, ACCENT_STRIP_WIDTH, height, accentPaint);

        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (height - line) / 2.0f - metrics.getAscent();
        textPaint.setColor(enabled
                ? getTheme().getForegroundColor()
                : getTheme().getControlTextDisabledColor());
        canvas.drawString(text, ACCENT_STRIP_WIDTH + PADDING_HORIZONTAL, baseline,
                textPaint, f);
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (accentPaint == null || accentPaint.isClosed()) {
            accentPaint = new Paint();
            accentPaint.setAntiAlias(true);
            accentPaint.setMode(PaintMode.FILL);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        // Informational: never consumes pointer events.
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Informational: alerts never take focus or handle keys.
    }

    @Override
    protected void onDispose() {
        if (bgPaint != null) {
            bgPaint.close();
            bgPaint = null;
        }
        if (accentPaint != null) {
            accentPaint.close();
            accentPaint = null;
        }
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
    }
}
