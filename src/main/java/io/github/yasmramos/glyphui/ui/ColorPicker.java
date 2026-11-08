package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphCursor;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A palette of colour swatches with a preview and an optional hex read-out.
 *
 * <p>The picker paints a grid of {@link #addSwatch(int) swatches} and marks the
 * current one with a ring. A click selects the swatch under the pointer and
 * updates {@link #valueProperty()}; the arrow keys move the selection through
 * the grid, Home/End jump to the bounds and Enter/Space re-apply the
 * selection. The current colour can also be set programmatically, either as
 * packed ARGB with {@link #setValue(int)} or as a CSS-style hex string with
 * {@link #setHex(String)}.</p>
 *
 * <p>The swatch list is the source of truth for clicking, but the widget also
 * accepts a colour that is not on the palette (a value loaded from markup, for
 * example): the preview and the hex read-out show it and no swatch is
 * ringed.</p>
 */
public class ColorPicker extends Component {

    /** Logger for change-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(ColorPicker.class.getName());

    /** Side of one swatch, logical units. */
    public static final float SWATCH_SIZE = 22.0f;

    /** Gap between two swatches, logical units. */
    public static final float SWATCH_GAP = 6.0f;

    /** Inner padding between the widget edge and the swatches, logical units. */
    public static final float PADDING = 8.0f;

    /** Height of the preview strip under the grid, logical units. */
    public static final float PREVIEW_HEIGHT = 24.0f;

    /** Gap between the grid and the preview strip, logical units. */
    public static final float PREVIEW_GAP = 8.0f;

    /** Height of the hex read-out line, logical units. */
    public static final float HEX_HEIGHT = 18.0f;

    /** Default number of swatches per row. */
    public static final int DEFAULT_COLUMNS = 8;

    /** Default palette: the classic 16-colour vocabulary, opaque. */
    private static final int[] DEFAULT_PALETTE = {
        0xFF000000, 0xFF444444, 0xFF888888, 0xFFCCCCCC,
        0xFFFFFFFF, 0xFFFF0000, 0xFFFF8800, 0xFFFFDD00,
        0xFF88CC00, 0xFF00AA44, 0xFF00AA88, 0xFF0088CC,
        0xFF0044CC, 0xFF4400CC, 0xFF8800AA, 0xFFAA0044
    };

    private final List<Integer> swatches = new ArrayList<>();

    /** Packed ARGB value currently shown. */
    private int value = 0xFF000000;

    private int columns = DEFAULT_COLUMNS;
    private boolean showHex = true;
    private boolean showPreview = true;

    private IntConsumer onChange;
    private Property<Integer> valueProperty;

    private Integer backgroundOverride;
    private Integer borderColorOverride;
    private Integer textColorOverride;

    private Paint bgPaint;
    private Paint swatchPaint;
    private Paint ringPaint;
    private Paint borderPaint;
    private Paint textPaint;

    /** Index of the swatch under the pointer, or {@code -1}. */
    private int hovered = -1;

    /**
     * Creates a picker with the default palette, showing the first swatch.
     */
    public ColorPicker() {
        super();
        setFocusable(true);
        for (int color : DEFAULT_PALETTE) {
            swatches.add(color);
        }
        value = swatches.get(0);
    }

    /**
     * Creates a picker over an explicit palette.
     *
     * @param palette the swatch colours (ARGB); a null or empty array keeps the
     *                default palette
     */
    public ColorPicker(int... palette) {
        this();
        if (palette != null && palette.length > 0) {
            swatches.clear();
            for (int color : palette) {
                swatches.add(color);
            }
            value = swatches.get(0);
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "colorpicker";
    }

    @Override
    protected GlyphCursor defaultCursor() {
        return GlyphCursor.POINTING_HAND;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.COLOR_PICKER;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return "Color picker, " + getHex();
    }

    // ---- Palette -----------------------------------------------------------

    /**
     * Appends a swatch.
     *
     * @param argb the swatch colour (packed ARGB)
     */
    public void addSwatch(int argb) {
        swatches.add(argb);
        invalidateLayout();
    }

    /**
     * Removes a swatch.
     *
     * @param index the zero-based swatch index
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public void removeSwatch(int index) {
        if (index < 0 || index >= swatches.size()) {
            throw new IndexOutOfBoundsException("Swatch index: " + index);
        }
        swatches.remove(index);
        invalidateLayout();
    }

    /**
     * Replaces the whole palette.
     *
     * @param palette the new swatch colours (ARGB); null clears the palette
     */
    public void setPalette(int... palette) {
        swatches.clear();
        if (palette != null) {
            for (int color : palette) {
                swatches.add(color);
            }
        }
        invalidateLayout();
    }

    /**
     * @return the palette colours, in insertion order
     */
    public List<Integer> getPalette() {
        return java.util.Collections.unmodifiableList(swatches);
    }

    /**
     * @return the number of swatches
     */
    public int getSwatchCount() {
        return swatches.size();
    }

    /**
     * @param index the zero-based swatch index
     * @return the swatch colour (packed ARGB)
     */
    public int getSwatch(int index) {
        return swatches.get(index);
    }

    /**
     * @return the number of swatches per row
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Sets how many swatches are laid out per row.
     *
     * @param columns the column count (values below 1 are treated as 1)
     */
    public void setColumns(int columns) {
        int next = Math.max(1, columns);
        if (this.columns == next) {
            return;
        }
        this.columns = next;
        invalidateLayout();
    }

    // ---- Value -------------------------------------------------------------

    /**
     * @return the current colour (packed ARGB)
     */
    public int getValue() {
        return value;
    }

    /**
     * Sets the current colour without firing the change handler.
     *
     * @param argb the new colour (packed ARGB)
     */
    public void setValue(int argb) {
        apply(argb, false);
    }

    /**
     * Returns the observable value property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the value property (never null after first access)
     */
    public Property<Integer> valueProperty() {
        if (valueProperty == null) {
            valueProperty = new Property<>(propertyOwner(), value,
                    newValue -> {
                        this.value = newValue == null ? 0 : newValue;
                        invalidatePaint();
                    });
        }
        return valueProperty;
    }

    /**
     * Registers the change handler, run when the user picks a colour.
     *
     * @param onChange the handler, or null to clear it
     */
    public void setOnChange(IntConsumer onChange) {
        this.onChange = onChange;
    }

    /**
     * @return the current colour as {@code #RRGGBB}, uppercase
     */
    public String getHex() {
        return String.format(Locale.ROOT, "#%06X", value & 0xFFFFFF);
    }

    /**
     * Sets the current colour from a hex string. Both {@code #RGB} and
     * {@code #RRGGBB} are accepted, with or without the leading {@code #};
     * anything else is ignored and returns false.
     *
     * @param hex the hex string
     * @return true when the string was parsed and applied
     */
    public boolean setHex(String hex) {
        Integer parsed = parseHex(hex);
        if (parsed == null) {
            return false;
        }
        apply(parsed, false);
        return true;
    }

    /**
     * Parses a CSS-style hex colour.
     *
     * @param hex the string to parse
     * @return the packed ARGB colour, or null when the string is not valid
     */
    public static Integer parseHex(String hex) {
        if (hex == null) {
            return null;
        }
        String s = hex.trim();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() == 3) {
            StringBuilder expanded = new StringBuilder(6);
            for (int i = 0; i < 3; i++) {
                expanded.append(s.charAt(i)).append(s.charAt(i));
            }
            s = expanded.toString();
        }
        if (s.length() != 6) {
            return null;
        }
        try {
            return 0xFF000000 | Integer.parseInt(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * @return true when the hex read-out is painted
     */
    public boolean isShowHex() {
        return showHex;
    }

    /**
     * Shows or hides the hex read-out line.
     *
     * @param showHex the new flag
     */
    public void setShowHex(boolean showHex) {
        if (this.showHex == showHex) {
            return;
        }
        this.showHex = showHex;
        invalidateLayout();
    }

    /**
     * @return true when the preview strip is painted
     */
    public boolean isShowPreview() {
        return showPreview;
    }

    /**
     * Shows or hides the preview strip.
     *
     * @param showPreview the new flag
     */
    public void setShowPreview(boolean showPreview) {
        if (this.showPreview == showPreview) {
            return;
        }
        this.showPreview = showPreview;
        invalidateLayout();
    }

    private void apply(int argb, boolean notify) {
        if (argb == value) {
            return;
        }
        value = argb;
        if (valueProperty != null) {
            valueProperty.set(value);
        }
        invalidatePaint();
        if (notify && onChange != null) {
            try {
                onChange.accept(value);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "ColorPicker handler threw", e);
            }
        }
    }

    // ---- Colors ------------------------------------------------------------

    /**
     * Gets the widget fill (CSS {@code background}, then the override, then the
     * theme input surface).
     *
     * @return the fill color (ARGB)
     */
    public int getBackgroundColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, backgroundOverride,
                getTheme().getInputBackgroundColor());
    }

    /**
     * Overrides the widget fill. Pass null to follow the theme.
     *
     * @param background the new color (ARGB) or null
     */
    public void setBackgroundColor(Integer background) {
        if (java.util.Objects.equals(this.backgroundOverride, background)) {
            return;
        }
        this.backgroundOverride = background;
        invalidatePaint();
    }

    /**
     * Gets the border color (CSS {@code border-color}, then the override, then
     * the theme border).
     *
     * @return the border color (ARGB)
     */
    public int getBorderColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, borderColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the border color. Pass null to follow the theme.
     *
     * @param border the new color (ARGB) or null
     */
    public void setBorderColor(Integer border) {
        if (java.util.Objects.equals(this.borderColorOverride, border)) {
            return;
        }
        this.borderColorOverride = border;
        invalidatePaint();
    }

    /**
     * Gets the text color (CSS {@code color}, then the override, then the theme
     * foreground).
     *
     * @return the text color (ARGB)
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride,
                getTheme().getForegroundColor());
    }

    /**
     * Overrides the text color. Pass null to follow the theme.
     *
     * @param textColor the new color (ARGB) or null
     */
    public void setTextColor(Integer textColor) {
        if (java.util.Objects.equals(this.textColorOverride, textColor)) {
            return;
        }
        this.textColorOverride = textColor;
        invalidatePaint();
    }

    // ---- Geometry ----------------------------------------------------------

    private int rows() {
        if (swatches.isEmpty()) {
            return 0;
        }
        return (swatches.size() + columns - 1) / columns;
    }

    private float gridWidth() {
        int cols = Math.min(columns, Math.max(1, swatches.size()));
        return cols * SWATCH_SIZE + Math.max(0, cols - 1) * SWATCH_GAP;
    }

    private float gridHeight() {
        int rows = rows();
        return rows * SWATCH_SIZE + Math.max(0, rows - 1) * SWATCH_GAP;
    }

    private float swatchX(int index) {
        return PADDING + (index % columns) * (SWATCH_SIZE + SWATCH_GAP);
    }

    private float swatchY(int index) {
        return PADDING + (index / columns) * (SWATCH_SIZE + SWATCH_GAP);
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return 2.0f * PADDING + gridWidth();
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        float h = 2.0f * PADDING + gridHeight();
        if (showPreview) {
            h += PREVIEW_GAP + PREVIEW_HEIGHT;
        }
        if (showHex) {
            h += PREVIEW_GAP + HEX_HEIGHT;
        }
        return h;
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

    /** The swatch index under a local point, or {@code -1}. */
    private int swatchAt(float localX, float localY) {
        for (int i = 0; i < swatches.size(); i++) {
            float sx = swatchX(i);
            float sy = swatchY(i);
            if (localX >= sx && localX <= sx + SWATCH_SIZE
                    && localY >= sy && localY <= sy + SWATCH_SIZE) {
                return i;
            }
        }
        return -1;
    }

    // ---- Input -------------------------------------------------------------

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        boolean inside = contains(event.getX(), event.getY());
        float localX = event.getX() - x;
        float localY = event.getY() - y;
        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE -> {
                hovered = swatchAt(localX, localY);
                setState(hovered >= 0 ? ComponentState.HOVER : ComponentState.IDLE);
            }
            case PRESS -> {
                if (inside && event.getButton() == MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                    requestFocusFromPointer();
                }
            }
            case RELEASE -> {
                if (inside && state == ComponentState.PRESSED) {
                    int index = swatchAt(localX, localY);
                    if (index >= 0) {
                        apply(swatches.get(index), true);
                    }
                }
                hovered = swatchAt(localX, localY);
                setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
            }
            default -> {
            }
        }

        if (state != oldState) {
            requestRepaint();
        }
        return inside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        if (swatches.isEmpty()) {
            return;
        }
        int current = swatches.indexOf(value);
        switch (event.getKeyCode()) {
            case GlyphKeys.LEFT -> move(current < 0 ? 0 : current - 1);
            case GlyphKeys.RIGHT -> move(current < 0 ? 0 : current + 1);
            case GlyphKeys.UP -> move(current < 0 ? 0 : current - columns);
            case GlyphKeys.DOWN -> move(current < 0 ? 0 : current + columns);
            case GlyphKeys.HOME -> move(0);
            case GlyphKeys.END -> move(swatches.size() - 1);
            case GlyphKeys.ENTER, GlyphKeys.SPACE -> {
                if (current >= 0) {
                    apply(swatches.get(current), true);
                } else {
                    move(0);
                }
            }
            default -> {
            }
        }
    }

    private void move(int index) {
        int clamped = Math.max(0, Math.min(swatches.size() - 1, index));
        apply(swatches.get(clamped), true);
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        float radius = getTheme().getControlCornerRadius();

        bgPaint.setColor(enabled ? getBackgroundColor()
                : getTheme().getControlDisabledColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, bgPaint);

        for (int i = 0; i < swatches.size(); i++) {
            float sx = swatchX(i);
            float sy = swatchY(i);
            swatchPaint.setColor(swatches.get(i));
            canvas.drawRRect(sx, sy, sx + SWATCH_SIZE, sy + SWATCH_SIZE,
                    radius, radius, swatchPaint);
            if (swatches.get(i) == value) {
                ringPaint.setColor(getTheme().getAccentColor());
                canvas.drawRRect(sx - 1.5f, sy - 1.5f, sx + SWATCH_SIZE + 1.5f,
                        sy + SWATCH_SIZE + 1.5f, radius + 1.5f, radius + 1.5f,
                        ringPaint);
            } else if (i == hovered && enabled) {
                ringPaint.setColor(getTheme().getBorderColor());
                canvas.drawRRect(sx - 1.0f, sy - 1.0f, sx + SWATCH_SIZE + 1.0f,
                        sy + SWATCH_SIZE + 1.0f, radius + 1.0f, radius + 1.0f,
                        ringPaint);
            }
        }

        float y = PADDING + gridHeight();
        if (showPreview) {
            y += PREVIEW_GAP;
            float previewW = Math.max(gridWidth(), SWATCH_SIZE);
            swatchPaint.setColor(value);
            canvas.drawRRect(PADDING, y, PADDING + previewW, y + PREVIEW_HEIGHT,
                    radius, radius, swatchPaint);
            borderPaint.setColor(getBorderColor());
            canvas.drawRRect(PADDING + 0.5f, y + 0.5f,
                    PADDING + previewW - 0.5f, y + PREVIEW_HEIGHT - 0.5f,
                    radius, radius, borderPaint);
            y += PREVIEW_HEIGHT;
        }
        if (showHex) {
            y += PREVIEW_GAP;
            Font f = font();
            FontMetrics m = f.getMetrics();
            textPaint.setColor(enabled ? getTextColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(getHex(), PADDING,
                    y + (HEX_HEIGHT - (m.getDescent() - m.getAscent())) / 2.0f
                            - m.getAscent(),
                    textPaint, f);
        }

        borderPaint.setColor(getBorderColor());
        canvas.drawRRect(0.5f, 0.5f, Math.max(0.0f, width - 1.0f),
                Math.max(0.0f, height - 1.0f), radius, radius, borderPaint);
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (swatchPaint == null || swatchPaint.isClosed()) {
            swatchPaint = new Paint();
            swatchPaint.setAntiAlias(true);
        }
        if (ringPaint == null || ringPaint.isClosed()) {
            ringPaint = new Paint();
            ringPaint.setAntiAlias(true);
            ringPaint.setMode(PaintMode.STROKE);
            ringPaint.setStrokeWidth(2.0f);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    protected void onDispose() {
        closeQuietly(bgPaint);
        bgPaint = null;
        closeQuietly(swatchPaint);
        swatchPaint = null;
        closeQuietly(ringPaint);
        ringPaint = null;
        closeQuietly(borderPaint);
        borderPaint = null;
        closeQuietly(textPaint);
        textPaint = null;
    }

    private static void closeQuietly(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
