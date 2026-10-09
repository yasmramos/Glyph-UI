package com.glyphui.layout;

import com.glyphui.ui.Panel;
import com.glyphui.ui.TestComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for FlowLayout manager.
 */
public class FlowLayoutTest {

    @Test
    public void testFlowLayoutWithGapAndPadding() {
        Panel panel = new Panel(0, 0, 200, 100);
        FlowLayout layout = new FlowLayout(10, 10); // gap=10, padding=10
        panel.setLayoutManager(layout);

        TestComponent comp1 = new TestComponent(0, 0, 50, 30);
        TestComponent comp2 = new TestComponent(0, 0, 50, 30);
        
        panel.add(comp1);
        panel.add(comp2);

        // Trigger layout
        panel.doLayout();

        // First component should be at (padding, padding) = (10, 10)
        assertEquals(10, comp1.getX(), "First component x should be padding");
        assertEquals(10, comp1.getY(), "First component y should be padding");

        // Second component should be at (padding + width + gap, padding) = (10 + 50 + 10, 10) = (70, 10)
        assertEquals(70, comp2.getX(), "Second component x should be after first with gap");
        assertEquals(10, comp2.getY(), "Second component y should be same as first");
    }

    @Test
    public void testFlowLayoutWrapsToNextLine() {
        Panel panel = new Panel(0, 0, 100, 200); // Narrow panel
        FlowLayout layout = new FlowLayout(5, 10); // gap=5, padding=10
        panel.setLayoutManager(layout);

        // Components wider than what fits in one line
        // Panel width: 100, padding: 10 each side = 80 available
        // Component width: 50, so only one fits per line with gap
        TestComponent comp1 = new TestComponent(0, 0, 50, 30);
        TestComponent comp2 = new TestComponent(0, 0, 50, 30);
        
        panel.add(comp1);
        panel.add(comp2);

        panel.doLayout();

        // First component at (10, 10)
        assertEquals(10, comp1.getX());
        assertEquals(10, comp1.getY());

        // Second component should wrap to next line at (10, 10 + 30 + 5) = (10, 45)
        assertEquals(10, comp2.getX(), "Second component should wrap to next line");
        assertEquals(45, comp2.getY(), "Second component y should be below first with gap");
    }

    @Test
    public void testFlowLayoutMultipleComponentsOnSameLine() {
        Panel panel = new Panel(0, 0, 300, 100);
        FlowLayout layout = new FlowLayout(10, 10);
        panel.setLayoutManager(layout);

        TestComponent comp1 = new TestComponent(0, 0, 60, 30);
        TestComponent comp2 = new TestComponent(0, 0, 60, 30);
        TestComponent comp3 = new TestComponent(0, 0, 60, 30);
        
        panel.add(comp1);
        panel.add(comp2);
        panel.add(comp3);

        panel.doLayout();

        // All three should fit on one line: 10 + 60 + 10 + 60 + 10 + 60 + 10 = 220 < 300
        assertEquals(10, comp1.getX());
        assertEquals(10, comp1.getY());
        
        assertEquals(80, comp2.getX()); // 10 + 60 + 10
        assertEquals(10, comp2.getY());
        
        assertEquals(150, comp3.getX()); // 10 + 60 + 10 + 60 + 10
        assertEquals(10, comp3.getY());
    }

    @Test
    public void testFlowLayoutEmptyPanel() {
        Panel panel = new Panel(0, 0, 200, 100);
        FlowLayout layout = new FlowLayout(10, 10);
        panel.setLayoutManager(layout);

        // Should not throw exception
        assertDoesNotThrow(() -> panel.doLayout());
    }

    @Test
    public void testLayoutAssignsPreferredSizeWhenUnset() {
        Panel panel = new Panel(0, 0, 500, 200);
        FlowLayout layout = new FlowLayout(10, 10);
        panel.setLayoutManager(layout);

        // No explicit bounds: the layout must apply the preferred size.
        TestComponent comp = new TestComponent();
        panel.add(comp);
        panel.doLayout();

        assertEquals(TestComponent.PREFERRED_WIDTH, comp.getWidth(), 0.01f);
        assertEquals(TestComponent.PREFERRED_HEIGHT, comp.getHeight(), 0.01f);
        assertFalse(comp.isSizeExplicitlySet(),
                "Layout-assigned size must not count as user-explicit");
    }

    @Test
    public void testLayoutRespectsExplicitBounds() {
        Panel panel = new Panel(0, 0, 500, 200);
        FlowLayout layout = new FlowLayout(10, 10);
        panel.setLayoutManager(layout);

        TestComponent comp = new TestComponent(0, 0, 60, 25);
        panel.add(comp);
        panel.doLayout();

        assertEquals(60f, comp.getWidth(), 0.01f);
        assertEquals(25f, comp.getHeight(), 0.01f);
        assertTrue(comp.isSizeExplicitlySet());
    }

    @Test
    public void testFlowUsesPreferredWidthForRowPacking() {
        Panel panel = new Panel(0, 0, 130, 200);
        FlowLayout layout = new FlowLayout(10, 10); // gap=10, padding=10
        panel.setLayoutManager(layout);

        // Preferred width is 50; available row space is 130 - 2*10 = 110.
        // Two components fit (10 + 50 + 10 + 50 = 120 > 130? no: second starts
        // at x=70 and ends at 120 <= 120 limit), third wraps.
        TestComponent c1 = new TestComponent();
        TestComponent c2 = new TestComponent();
        TestComponent c3 = new TestComponent();
        panel.add(c1);
        panel.add(c2);
        panel.add(c3);
        panel.doLayout();

        assertEquals(10f, c1.getX(), 0.01f);
        assertEquals(10f, c1.getY(), 0.01f);
        assertEquals(70f, c2.getX(), 0.01f); // 10 + 50 + 10
        assertEquals(10f, c2.getY(), 0.01f);
        assertTrue(c3.getY() > c1.getY(), "Third component should wrap to a new row");
        assertEquals(10f, c3.getX(), 0.01f);
    }
}
