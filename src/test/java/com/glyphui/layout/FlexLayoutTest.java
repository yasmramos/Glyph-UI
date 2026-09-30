package com.glyphui.layout;

import com.glyphui.graphics.Dimension;
import com.glyphui.style.Style;
import com.glyphui.style.StyleProperty;
import com.glyphui.ui.Button;
import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FlexLayout}: direction, gap/padding, justify-content,
 * align-items and flex-grow, all driven through {@code measure()} constraints.
 */
class FlexLayoutTest {

    /** A panel whose children report a fixed preferred size via inline style. */
    private static Panel container(float width, float height, FlexLayout layout) {
        Panel panel = new Panel(0, 0, width, height);
        panel.setComputedStyle(Style.builder().build());
        panel.setLayoutManager(layout);
        return panel;
    }

    private static Button sizedButton(String text, float w, float h) {
        Button b = new Button(0, 0, w, h);
        b.setText(text);
        // Fixed measured size independent of font metrics:
        b.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, w)
                .length(StyleProperty.HEIGHT, h)
                .build());
        return b;
    }

    @Test
    void rowDirectionPlacesItemsSideBySideWithGapAndPadding() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setGap(10);
        flex.setPadding(8);
        Panel panel = container(400, 100, flex);

        Button a = sizedButton("A", 50, 30);
        Button b = sizedButton("B", 60, 30);
        panel.add(a);
        panel.add(b);
        flex.layout(panel);

        assertEquals(8f, a.getX(), 0.01f);
        assertEquals(8f + 50f + 10f, b.getX(), 0.01f);
        assertEquals(50f, a.getWidth(), 0.01f);
        assertEquals(60f, b.getWidth(), 0.01f);
    }

    @Test
    void columnDirectionStacksVertically() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.COLUMN);
        flex.setGap(12);
        flex.setPadding(4);
        Panel panel = container(200, 300, flex);

        Button a = sizedButton("A", 50, 40);
        Button b = sizedButton("B", 50, 40);
        panel.add(a);
        panel.add(b);
        flex.layout(panel);

        assertEquals(4f, a.getY(), 0.01f);
        assertEquals(4f + 40f + 12f, b.getY(), 0.01f);
        assertEquals(4f, a.getX(), 0.01f);
    }

    @Test
    void justifyCenterCentersItemsOnMainAxis() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setJustify(FlexLayout.Justify.CENTER);
        flex.setGap(0);
        flex.setPadding(0);
        Panel panel = container(400, 100, flex);

        Button a = sizedButton("A", 100, 30);
        Button b = sizedButton("B", 100, 30);
        panel.add(a);
        panel.add(b);
        flex.layout(panel);

        // Total items width 200 in a 400-wide container -> 100px leading offset.
        assertEquals(100f, a.getX(), 0.01f);
        assertEquals(200f, b.getX(), 0.01f);
    }

    @Test
    void justifySpaceBetweenPinsFirstAndLast() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setJustify(FlexLayout.Justify.SPACE_BETWEEN);
        flex.setPadding(0);
        Panel panel = container(400, 100, flex);

        Button a = sizedButton("A", 50, 30);
        Button b = sizedButton("B", 50, 30);
        Button c = sizedButton("C", 50, 30);
        panel.add(a);
        panel.add(b);
        panel.add(c);
        flex.layout(panel);

        assertEquals(0f, a.getX(), 0.01f);
        assertEquals(350f, c.getX(), 0.01f);
        assertEquals(175f, b.getX(), 0.01f); // evenly distributed
    }

    @Test
    void alignItemsCenterCentersOnCrossAxis() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setAlignItems(FlexLayout.Align.CENTER);
        flex.setPadding(0);
        Panel panel = container(400, 100, flex);

        Button a = sizedButton("A", 50, 40);
        panel.add(a);
        flex.layout(panel);

        assertEquals((100f - 40f) / 2f, a.getY(), 0.01f);
    }

    @Test
    void stretchExpandsItemToCrossSize() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setAlignItems(FlexLayout.Align.STRETCH);
        flex.setPadding(0);
        Panel panel = container(400, 100, flex);

        Button a = sizedButton("A", 50, 40);
        panel.add(a);
        flex.layout(panel);

        assertEquals(100f, a.getHeight(), 0.01f);
        assertEquals(0f, a.getY(), 0.01f);
    }

    @Test
    void flexGrowDistributesLeftoverSpace() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setPadding(0);
        flex.setGap(0);
        Panel panel = container(400, 100, flex);

        Button fixed = sizedButton("F", 100, 30);
        Button grower = sizedButton("G", 100, 30);
        grower.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 100)
                .length(StyleProperty.HEIGHT, 30)
                .set(StyleProperty.FLEX_GROW, 1f)
                .build());
        panel.add(fixed);
        panel.add(grower);
        flex.layout(panel);

        // Leftover 400-200=200 goes entirely to the growing item.
        assertEquals(300f, grower.getWidth(), 0.01f);
        assertEquals(100f, fixed.getWidth(), 0.01f);
    }

    @Test
    void displayNoneChildIsSkipped() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setPadding(0);
        flex.setGap(0);
        Panel panel = container(400, 100, flex);

        Button a = sizedButton("A", 50, 30);
        Button hidden = sizedButton("H", 50, 30);
        hidden.setComputedStyle(Style.builder()
                .keyword(StyleProperty.DISPLAY, "none")
                .build());
        Button b = sizedButton("B", 50, 30);
        panel.add(a);
        panel.add(hidden);
        panel.add(b);
        flex.layout(panel);

        assertEquals(0f, a.getX(), 0.01f);
        assertEquals(50f, b.getX(), 0.01f);
    }

    @Test
    void styleDrivenPropertiesOverrideDefaults() {
        // No programmatic overrides: everything comes from computed style.
        FlexLayout flex = new FlexLayout();
        Panel panel = new Panel(0, 0, 400, 100);
        panel.setComputedStyle(Style.builder()
                .keyword(StyleProperty.FLEX_DIRECTION, "column")
                .keyword(StyleProperty.JUSTIFY_CONTENT, "flex-end")
                .length(StyleProperty.GAP, 6)
                .length(StyleProperty.PADDING, 2)
                .build());
        panel.setLayoutManager(flex);

        Button a = sizedButton("A", 40, 20);
        Button b = sizedButton("B", 40, 20);
        panel.add(a);
        panel.add(b);
        flex.layout(panel);

        // Column + flex-end: items packed at the bottom of the 100-high box.
        assertTrue(a.getY() < b.getY(), "a must sit above b in column mode");
        assertEquals(100f - 2f - 20f, b.getY(), 0.01f);
        assertEquals(b.getY() - 6f - 20f, a.getY(), 0.01f);
    }

    @Test
    void emptyContainerDoesNotThrow() {
        FlexLayout flex = new FlexLayout();
        Panel panel = container(100, 100, flex);
        flex.layout(panel); // no children
        assertTrue(panel.getChildren().isEmpty());
    }
}
