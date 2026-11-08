package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

/**
 * A titled surface that groups related content.
 *
 * <p>A card is a {@link Panel} that paints a bordered, rounded surface and an
 * optional title strip along the top. Its children are laid out below that
 * strip, so content never overlaps the title. The title can be driven from a
 * background thread through {@link #titleProperty()}.</p>
 *
 * <p>Colors resolve like every other widget: a CSS declaration wins, then a
 * per-instance override, then the theme. With no declaration the card paints
 * the theme's input-surface color with a border, which reads as a raised
 * panel distinct from the page background.</p>
 */
public class Card extends Panel {

    /** Inner padding between the surface edge and the content, logical units. */
    public static final float CONTENT_PADDING = 12.0f;

    /** Gap between the title strip and the first child, logical units. */
    public static final float TITLE_GAP = 8.0f;

    private String title;
    private Integer borderColorOverride;
    private Property<String> titleProperty;

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint titlePaint;

    /**
     * Creates an empty, untitled card with no explicit bounds.
     */
    public Card() {
        this("");
    }

    /**
     * Creates a card with the supplied title.
     *
     * @param title the title (null is treated as empty, meaning "no header")
     */
    public Card(String title) {
        super();
        this.title = title == null ? "" : title;
        // A column flow stacks the content under the title strip. The layout
        // reserves the content padding; the title strip is added on top of it
        // by the layout override below.
        setLayoutManager(new CardLayout());
    }

    /**
     * Column flow that keeps the content clear of the title strip. The base
     * pass repositions every child from the panel's top-left on each run, so
     * adding the header offset afterwards is deterministic and cannot
     * accumulate across passes.
     */
    private final class CardLayout extends FlexLayout {

        CardLayout() {
            super(Direction.COLUMN, Justify.FLEX_START, Align.STRETCH);
            setPadding(CONTENT_PADDING);
        }

        @Override
        public void layout(Panel panel) {
            super.layout(panel);
            float header = headerHeight();
            if (header > 0.0f) {
                for (Component child : panel.getChildren()) {
                    child.setY(child.getY() + header);
                }
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "card";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.CARD;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return title.isEmpty() ? super.getAccessibleName() : title;
    }

    /**
     * Gets the card title.
     *
     * @return the title, never null (empty means "no header")
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the observable title property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the title property (never null after first access)
     */
    public Property<String> titleProperty() {
        if (titleProperty == null) {
            titleProperty = new Property<>(propertyOwner(), title, newTitle -> {
                this.title = newTitle == null ? "" : newTitle;
                // The header height feeds the content offset and the size.
                invalidateLayout();
            });
        }
        return titleProperty;
    }

    /**
     * Sets the card title. An empty title removes the header strip.
     *
     * @param title the new title (null is treated as empty)
     */
    public void setTitle(String title) {
        titleProperty().set(title);
    }

    /**
     * Gets the surface fill color. Resolution order: a matching CSS
     * {@code background} rule → an explicit {@link Panel#setBackgroundColor(int)}
     * override → the theme input-surface color (which reads as a raised panel
     * distinct from the page background).
     *
     * @return the fill color (ARGB)
     */
    @Override
    public int getBackgroundColor() {
        Style s = getComputedStyle();
        if (s != null && s.has(StyleProperty.BACKGROUND)) {
            return s.getInt(StyleProperty.BACKGROUND,
                    getTheme().getInputBackgroundColor());
        }
        if (hasExplicitBackground()) {
            return super.getBackgroundColor();
        }
        return getTheme().getInputBackgroundColor();
    }

    /**
     * Gets the border color (CSS {@code border-color}, then per-instance
     * override, then the theme border color).
     *
     * @return the border color (ARGB)
     */
    public int getBorderColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, borderColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the border color. Pass null to follow the theme again.
     *
     * @param borderColor the new border color (ARGB) or null
     */
    public void setBorderColor(Integer borderColor) {
        if (java.util.Objects.equals(this.borderColorOverride, borderColor)) {
            return;
        }
        this.borderColorOverride = borderColor;
        invalidatePaint();
    }

    /**
     * Height of the title strip: the heading line height plus the gap below
     * it, or zero when the card has no title.
     *
     * @return the header height in logical units
     */
    private float headerHeight() {
        if (title.isEmpty()) {
            return 0.0f;
        }
        FontMetrics metrics = resolveFont(Theme.FontRole.HEADING).getMetrics();
        return metrics.getDescent() - metrics.getAscent() + TITLE_GAP;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSize = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (cssSize || isCtorSizeExplicit()) {
            return super.measure(maxWidth, maxHeight);
        }
        // The layout already accounts for the content padding; the title strip
        // is the only extra room the card reserves on top of it.
        Dimension content = super.measure(maxWidth, maxHeight);
        return new Dimension(content.getWidth(), content.getHeight() + headerHeight());
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        doLayout();
        ensurePaints();

        float radius = getTheme().getControlCornerRadius();
        // A card is an in-flow raised surface: a subtle lift separates it from
        // the page without the heavier depth of a dialog or popup.
        canvas.drawShadow(0, 0, width, height, radius, Canvas.Elevation.LOW);
        bgPaint.setColor(getBackgroundColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, bgPaint);
        borderPaint.setColor(getBorderColor());
        canvas.drawRRect(0.5f, 0.5f, width - 1.0f, height - 1.0f,
                radius, radius, borderPaint);

        if (!title.isEmpty()) {
            Font f = resolveFont(Theme.FontRole.HEADING);
            FontMetrics metrics = f.getMetrics();
            float baseline = CONTENT_PADDING - metrics.getAscent();
            titlePaint.setColor(enabled
                    ? getTheme().getForegroundColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(title, CONTENT_PADDING, baseline, titlePaint, f);
        }

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            for (Component child : children) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
        }
        if (titlePaint == null || titlePaint.isClosed()) {
            titlePaint = new Paint();
            titlePaint.setAntiAlias(true);
        }
    }

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
        if (titlePaint != null) {
            titlePaint.close();
            titlePaint = null;
        }
    }
}
