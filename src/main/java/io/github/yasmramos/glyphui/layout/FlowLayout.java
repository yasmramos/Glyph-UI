package io.github.yasmramos.glyphui.layout;

import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Panel;

/**
 * A flow layout manager that arranges components in a left-to-right flow.
 */
public class FlowLayout extends LayoutManager {

    private float gap;
    private float padding;

    /**
     * Creates a new FlowLayout.
     *
     * @param gap the gap between components
     * @param padding the padding around the panel
     */
    public FlowLayout(float gap, float padding) {
        this.gap = gap;
        this.padding = padding;
    }

    /**
     * Creates a new FlowLayout with default values.
     */
    public FlowLayout() {
        this(10, 10);
    }

    /**
     * Gets the gap between components.
     *
     * @return the gap
     */
    public float getGap() {
        return gap;
    }

    /**
     * Sets the gap between components.
     *
     * @param gap the new gap
     */
    public void setGap(float gap) {
        this.gap = gap;
        notifyOwner();
    }

    /**
     * Gets the padding around the panel.
     *
     * @return the padding
     */
    public float getPadding() {
        return padding;
    }

    /**
     * Sets the padding around the panel.
     *
     * @param padding the new padding
     */
    public void setPadding(float padding) {
        this.padding = padding;
        notifyOwner();
    }


    /**
     * Resolves the horizontal spacing between items: programmatic override →
     * panel computed style {@code gap} → constructor default. Reading the
     * container's CSS keeps FlowLayout consistent with FlexLayout, so
     * {@code Panel { gap: 24px }} affects both layouts.
     *
     * @param panel the container being laid out
     * @return the effective gap in logical pixels
     */
    private float effectiveGap(Panel panel) {
        Style style = panel.getComputedStyle();
        if (style != null && style.has(StyleProperty.GAP)) {
            return style.getFloat(StyleProperty.GAP, gap);
        }
        return gap;
    }

    /**
     * Resolves the inner inset: programmatic override → panel computed style
     * {@code padding} → constructor default.
     *
     * @param panel the container being laid out
     * @return the effective padding in logical pixels
     */
    private float effectivePadding(Panel panel) {
        Style style = panel.getComputedStyle();
        if (style != null && style.has(StyleProperty.PADDING)) {
            return style.getFloat(StyleProperty.PADDING, padding);
        }
        return padding;
    }

    /**
     * Measures the flow content: the bounding box of the arranged rows under
     * the given width constraint plus the effective padding. The height is
     * the stacked row heights (flow wraps at the available width), while the
     * width reports the widest single item when unbounded, matching how the
     * layout pass places a first-row item before wrapping.
     */
    @Override
    public Dimension measure(Panel panel, float maxWidth, float maxHeight) {
        float pad = effectivePadding(panel);
        float g = effectiveGap(panel);
        // With no bound we cannot know where rows wrap; report one row.
        float availW = Float.isInfinite(maxWidth)
                ? Math.max(0f, panel.getWidth() - 2 * pad)
                : Math.max(0f, maxWidth - 2 * pad);

        float x = 0f;
        float y = 0f;
        float rowHeight = 0f;
        float maxRowWidth = 0f;
        boolean first = true;
        for (Component c : panel.getChildren()) {
            if (!c.isVisible()) {
                continue;
            }
            Dimension pref = c.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            if (!first && pref.getWidth() > availW - x) {
                maxRowWidth = Math.max(maxRowWidth, x - g);
                x = 0f;
                y += rowHeight + g;
                rowHeight = 0f;
            }
            x += pref.getWidth() + g;
            rowHeight = Math.max(rowHeight, pref.getHeight());
            maxRowWidth = Math.max(maxRowWidth, x - g);
            first = false;
        }
        float contentH = first ? 0f : y + rowHeight;
        float contentW = first ? 0f : maxRowWidth;
        float w = Math.min(Math.max(contentW + 2 * pad, 0f),
                Float.isInfinite(maxWidth) ? Float.POSITIVE_INFINITY : maxWidth);
        float h = Math.min(Math.max(contentH + 2 * pad, 0f),
                Float.isInfinite(maxHeight) ? Float.POSITIVE_INFINITY : maxHeight);
        return new Dimension(w, h);
    }

    @Override
    public void layout(Panel panel) {
        // Gap and padding come from the container CSS when declared, falling
        // back to this manager's fields (see effectiveGap/effectivePadding).
        final float pad = effectivePadding(panel);
        final float g = effectiveGap(panel);
        // Use local coordinates - start at padding within the panel
        float currentX = pad;
        float currentY = pad;
        float maxY = currentY;
        float panelWidth = panel.getWidth();

        for (Component component : panel.getChildren()) {
            if (!component.isVisible()) {
                continue;
            }

            // Preferred (unconstrained) size first, used for line wrapping.
            Dimension preferred = component.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

            // If the preferred width no longer fits in the current row, wrap
            // to a new row before placing the component.
            float remainingWidth = Math.max(0.0f, panelWidth - pad - currentX);
            if (currentX > pad && preferred.getWidth() > remainingWidth) {
                currentX = pad;
                currentY = maxY + g;
                remainingWidth = Math.max(0.0f, panelWidth - pad - currentX);
            }

            // Re-measure under the actual available constraints so that
            // components which adapt their size to the given width
            // (word-wrapping labels, etc.) are laid out with their true size.
            Dimension size = component.measure(remainingWidth, Float.POSITIVE_INFINITY);

            // Apply the size through applyLayoutSize(), NOT setWidth/setHeight:
            // those are the user-facing setters and would flag the bounds as
            // explicit (and pin an inline width/height), so the component
            // could never be re-measured by later layout passes.
            // Height policy mirrors the width policy of this layout: a child
            // whose bounds were set explicitly keeps its current height
            // untouched, unless CSS declares one (then the measurement wins).
            // Children without explicit bounds take the measured height.
            float appliedHeight = size.getHeight();
            if (component.isSizeExplicitlySet()) {
                Style cs = component.getComputedStyle();
                boolean cssHeight = cs != null && cs.has(StyleProperty.HEIGHT);
                if (!cssHeight) {
                    appliedHeight = component.getHeight();
                }
            }
            component.applyLayoutSize(size.getWidth(), appliedHeight);

            // Position the component in local coordinates
            component.setX(currentX);
            component.setY(currentY);

            // Update position for next component
            currentX += size.getWidth() + g;
            // Advance the row by max(applied, measured): an explicit height
            // taller than the measurement must not let the next row overlap
            // it (Block 1 item 1.3 / Block 3 item 3.3), while a short explicit
            // height still reserves the preferred space so rows keep their
            // historical spacing contract.
            maxY = Math.max(maxY, currentY + Math.max(appliedHeight, size.getHeight()));
        }
    }
}
