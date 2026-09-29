package com.glyphui.layout;

import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;

/**
 * A flow layout manager that arranges components in a left-to-right flow.
 */
public class FlowLayout extends LayoutManager {
    private int gap;
    private int padding;

    /**
     * Creates a new FlowLayout.
     *
     * @param gap     the gap between components
     * @param padding the padding around the panel
     */
    public FlowLayout(int gap, int padding) {
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
    public int getGap() {
        return gap;
    }

    /**
     * Sets the gap between components.
     *
     * @param gap the new gap
     */
    public void setGap(int gap) {
        this.gap = gap;
    }

    /**
     * Gets the padding around the panel.
     *
     * @return the padding
     */
    public int getPadding() {
        return padding;
    }

    /**
     * Sets the padding around the panel.
     *
     * @param padding the new padding
     */
    public void setPadding(int padding) {
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

            // Get the preferred size of the component. When the user did not
            // provide explicit bounds, the layout assigns the preferred size
            // so widgets fit their content (e.g. measured button text).
            float preferredWidth = component.getPreferredWidth();
            float preferredHeight = component.getPreferredHeight();
            if (!component.isSizeExplicitlySet()) {
                component.applyLayoutSize(preferredWidth, preferredHeight);
            }

            // Check if component fits in current row (using local coordinates)
            if (currentX + component.getWidth() > panelWidth - padding) {
                // Move to next row
                currentX = padding;
                currentY = maxY + gap;
            }

            // Position the component in local coordinates
            component.setX(currentX);
            component.setY(currentY);

            // Update position for next component
            currentX += component.getWidth() + gap;
            maxY = Math.max(maxY, currentY + preferredHeight);
        }
    }
}
