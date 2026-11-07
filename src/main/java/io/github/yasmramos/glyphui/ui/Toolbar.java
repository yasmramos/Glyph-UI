package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Paint;

/**
 * A horizontal strip of controls pinned to the top of a surface.
 *
 * <p>A toolbar is a {@link Panel} that lays its children out in a single row,
 * centres them on the cross axis, and paints a surface with a hairline border
 * along the bottom edge, so it reads as chrome rather than as page content.
 * Controls keep their own preferred sizes; a {@code flex-grow} child (typically
 * a search field) absorbs the slack.</p>
 *
 * <p>Colors resolve like every other widget: a CSS declaration wins, then a
 * per-instance override, then the theme.</p>
 */
public class Toolbar extends Panel {

    /** Horizontal padding inside the strip, logical units. */
    public static final float CONTENT_PADDING = 6.0f;

    /** Gap between two controls, logical units. */
    public static final float ITEM_GAP = 6.0f;

    private Integer borderColorOverride;

    private Paint bgPaint;
    private Paint borderPaint;

    /**
     * Creates an empty toolbar with no explicit bounds.
     */
    public Toolbar() {
        super();
        setLayoutManager(new ToolbarLayout());
        // Toolbars are chrome: they never take focus themselves, only their
        // controls do.
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "toolbar";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TOOLBAR;
    }

    /**
     * Gets the strip fill. Resolution order: a CSS {@code background}
     * declaration, then an explicit {@link Panel#setBackgroundColor(int)
     * override}, then the theme control surface.
     *
     * @return the fill color (ARGB)
     */
    @Override
    public int getBackgroundColor() {
        Style s = getComputedStyle();
        if (s != null && s.has(StyleProperty.BACKGROUND)) {
            return s.getInt(StyleProperty.BACKGROUND, getTheme().getControlNormalColor());
        }
        return super.getBackgroundColor();
    }

    /**
     * Gets the bottom border color.
     *
     * @return the border color (ARGB)
     */
    public int getBorderColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, borderColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the bottom border color. Pass null to follow the theme.
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

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        float tallest = 0.0f;
        for (Component child : getChildren()) {
            tallest = Math.max(tallest, child.getPreferredHeight());
        }
        return tallest + 2.0f * CONTENT_PADDING;
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        doLayout();
        ensurePaints();
        bgPaint.setColor(getBackgroundColor());
        canvas.drawRect(0, 0, width, height, bgPaint);
        borderPaint.setColor(getBorderColor());
        canvas.drawRect(0, height - 1.0f, width, 1.0f, borderPaint);

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
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
        }
    }

    /** Row flow with the toolbar's padding and gap baked in. */
    private static final class ToolbarLayout extends FlexLayout {
        ToolbarLayout() {
            super(Direction.ROW, Justify.FLEX_START, Align.CENTER);
            setGap(ITEM_GAP);
            setPadding(CONTENT_PADDING);
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
    }
}
