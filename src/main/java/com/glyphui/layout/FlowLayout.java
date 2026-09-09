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
        float currentX = panel.getX() + padding;
        float currentY = panel.getY() + padding;
        float maxY = currentY;
        float panelWidth = panel.getWidth();

        for (Component component : panel.getChildren()) {
            if (!component.isVisible()) {
                continue;
            }

            // Get the preferred height of the component
            float componentHeight = component.getPreferredHeight();
            
            // Check if component fits in current row
            if (currentX + component.getWidth() > panel.getX() + panelWidth - padding) {
                // Move to next row
                currentX = panel.getX() + padding;
                currentY = maxY + gap;
            }

            // Set the component height to its preferred height
            component.setHeight(componentHeight);

            // Position the component
            component.setX(currentX);
            component.setY(currentY);

            // Update position for next component
            currentX += component.getWidth() + gap;
            maxY = Math.max(maxY, currentY + componentHeight);
        }
    }
}
