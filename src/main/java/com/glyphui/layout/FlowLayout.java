package com.glyphui.layout;

import com.glyphui.graphics.Dimension;
import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;

/**
 * A flow layout manager that arranges components in a left-to-right flow.
 */
public class FlowLayout extends LayoutManager {
    private float gap;
    private float padding;

    /**
     * Creates a new FlowLayout.
     *
     * @param gap     the gap between components
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
    }

    @Override
    public void layout(Panel panel) {
        // Use local coordinates - start at padding within the panel
        float currentX = padding;
        float currentY = padding;
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
            float remainingWidth = Math.max(0.0f, panelWidth - padding - currentX);
            if (currentX > padding && preferred.getWidth() > remainingWidth) {
                currentX = padding;
                currentY = maxY + gap;
                remainingWidth = Math.max(0.0f, panelWidth - padding - currentX);
            }

            // Re-measure under the actual available constraints so that
            // components which adapt their size to the given width
            // (word-wrapping labels, etc.) are laid out with their true size.
            Dimension size = component.measure(remainingWidth, Float.POSITIVE_INFINITY);

            // Apply the measured size to the component
            component.setWidth(size.getWidth());
            component.setHeight(size.getHeight());

            // Position the component in local coordinates
            component.setX(currentX);
            component.setY(currentY);

            // Update position for next component
            currentX += size.getWidth() + gap;
            maxY = Math.max(maxY, currentY + size.getHeight());
        }
    }
}
