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
import io.github.humbleui.skija.PaintMode;

/**
 * A titled border that groups related controls.
 *
 * <p>A group box is the classic fieldset: a rounded outline with the title
 * sitting on the top edge, and its children laid out below the title. Unlike
 * {@link Card}, which paints a filled surface with the title inside, a group box
 * stays transparent so it never fights the surface underneath.</p>
 *
 * <p>The title can be driven from a background thread through
 * {@link #titleProperty()}.</p>
 */
public class GroupBox extends Panel {

    /** Gap between the title strip and the first child, logical units. */
    public static final float TITLE_GAP = 8.0f;

    /** Horizontal padding inside the outline, logical units. */
    public static final float CONTENT_PADDING = 10.0f;

    private String title;
    private Property<String> titleProperty;

    private Integer borderColorOverride;

    private Paint borderPaint;
    private Paint titlePaint;
    private Paint backdropPaint;

    /**
     * Creates an empty, untitled group box.
     */
    public GroupBox() {
        this("");
    }

    /**
     * Creates a group box with the supplied title.
     *
     * @param title the title (null is treated as empty)
     */
    public GroupBox(String title) {
        super();
        this.title = title == null ? "" : title;
        setLayoutManager(new GroupBoxLayout());
        // A group box is a passive container: only its children take focus.
        setFocusable(false);
    }

    /**
     * Column flow that keeps the content clear of the title strip. The base
     * pass repositions every child from the panel's top-left on each run, so
     * adding the header offset afterwards is deterministic and cannot
     * accumulate across passes.
     */
    private final class GroupBoxLayout extends FlexLayout {

        GroupBoxLayout() {
            super(Direction.COLUMN, Justify.FLEX_START, Align.STRETCH);
            setPadding(CONTENT_PADDING);
        }

        @Override
        public void layout(Panel panel) {
            super.layout(panel);
            float header = headerHeight();
            for (Component child : panel.getChildren()) {
                child.setY(child.getY() + header);
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "groupbox";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.GROUP_BOX;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return title.isEmpty() ? super.getAccessibleName() : title;
    }

    /**
     * Gets the group title.
     *
     * @return the title, never null (empty means "no title")
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
                invalidateLayout();
            });
        }
        return titleProperty;
    }

    /**
     * Sets the group title.
     *
     * @param title the new title (null is treated as empty)
     */
    public void setTitle(String title) {
        titleProperty().set(title);
    }

    /**
     * Gets the outline color (CSS {@code border-color}, then the override, then
     * the theme border color).
     *
     * @return the outline color (ARGB)
     */
    public int getBorderColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, borderColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the outline color. Pass null to follow the theme.
     *
     * @param borderColor the new color (ARGB) or null
     */
    public void setBorderColor(Integer borderColor) {
        if (java.util.Objects.equals(this.borderColorOverride, borderColor)) {
            return;
        }
        this.borderColorOverride = borderColor;
        invalidatePaint();
    }

    /** Height of the title strip: the heading line plus the gap below it. */
    private float headerHeight() {
        FontMetrics metrics = resolveFont(Theme.FontRole.HEADING).getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return line + TITLE_GAP;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSize = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (cssSize || sizeExplicitlySet) {
            return super.measure(maxWidth, maxHeight);
        }
        // The layout already reserves the content padding; the title strip is
        // the only extra room the group box needs on top of it.
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
        borderPaint.setColor(getBorderColor());
        borderPaint.setMode(PaintMode.STROKE);
        borderPaint.setStrokeWidth(1.0f);
        canvas.drawRRect(0.5f, 0.5f, Math.max(0.0f, width - 1.0f),
                Math.max(0.0f, height - 1.0f), radius, radius, borderPaint);

        if (!title.isEmpty()) {
            Font f = resolveFont(Theme.FontRole.HEADING);
            FontMetrics metrics = f.getMetrics();
            float line = metrics.getDescent() - metrics.getAscent();
            float baseline = (line - metrics.getAscent()) - TITLE_GAP / 2.0f;
            float textWidth = canvas.measureText(title, f);
            float textX = CONTENT_PADDING + 2.0f;
            // Erase the outline behind the title so the caption reads as a gap
            // in the border rather than crossing it.
            backdropPaint.setColor(getTheme().getBackgroundColor());
            canvas.drawRect(textX - 3.0f, 0.0f, textWidth + 6.0f, 1.0f, backdropPaint);
            titlePaint.setColor(enabled
                    ? getTheme().getForegroundColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(title, textX, baseline, titlePaint, f);
        }

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            for (Component child : getChildren()) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    private void ensurePaints() {
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        if (titlePaint == null || titlePaint.isClosed()) {
            titlePaint = new Paint();
            titlePaint.setAntiAlias(true);
        }
        if (backdropPaint == null || backdropPaint.isClosed()) {
            backdropPaint = new Paint();
            backdropPaint.setAntiAlias(false);
        }
    }

    @Override
    protected void onDispose() {
        if (borderPaint != null) {
            borderPaint.close();
            borderPaint = null;
        }
        if (titlePaint != null) {
            titlePaint.close();
            titlePaint = null;
        }
        if (backdropPaint != null) {
            backdropPaint.close();
            backdropPaint = null;
        }
    }
}
