package com.glyphui.layout;

import com.glyphui.ui.Panel;
import com.glyphui.ui.TestComponent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional unit tests for FlowLayout / LayoutManager edge cases:
 * dirty-flag behavior, invisible components, preferred heights, etc.
 */
public class FlowLayoutEdgeCasesTest {

    /**
     * A layout manager that records how many times it was invoked.
     */
    private static class CountingLayout extends LayoutManager {
        int layoutCount = 0;
        final List<Panel> laidOutPanels = new ArrayList<>();

        @Override
        public void layout(Panel panel) {
            layoutCount++;
            laidOutPanels.add(panel);
        }
    }

    @Test
    public void testCustomLayoutManagerIsUsed() {
        Panel panel = new Panel(0, 0, 200, 100);
        CountingLayout layout = new CountingLayout();

        // Empty panel + setLayoutManager must NOT trigger a layout...
        panel.setLayoutManager(layout);
        assertEquals(0, layout.layoutCount,
                "Assigning a layout manager to an empty panel should not run layout");

        // ...but adding a child marks the layout dirty and doLayout runs it.
        panel.add(new TestComponent(0, 0, 10, 10));
        panel.doLayout();

        assertEquals(1, layout.layoutCount, "Custom layout manager should be invoked once");
        assertSame(panel, layout.laidOutPanels.get(0));
    }

    @Test
    public void testDoLayoutOnlyRunsWhenDirty() {
        Panel panel = new Panel(0, 0, 200, 100);
        CountingLayout layout = new CountingLayout();
        panel.setLayoutManager(layout); // triggers initial layout (children empty -> not dirty? setLayoutManager calls doLayout only if children exist)

        int countAfterSet = layout.layoutCount;

        panel.doLayout();
        panel.doLayout();

        assertEquals(countAfterSet, layout.layoutCount,
                "doLayout on a clean panel should not re-invoke the layout manager");
    }

    @Test
    public void testAddingChildMarksLayoutDirty() {
        Panel panel = new Panel(0, 0, 200, 100);
        CountingLayout layout = new CountingLayout();
        panel.setLayoutManager(layout);

        panel.add(new TestComponent(0, 0, 10, 10));
        panel.doLayout();

        assertEquals(1, layout.layoutCount, "Adding a child should mark layout dirty");

        panel.doLayout();
        assertEquals(1, layout.layoutCount, "Second doLayout without changes should be a no-op");
    }

    @Test
    public void testResizeMarksLayoutDirty() {
        Panel panel = new Panel(0, 0, 200, 100);
        CountingLayout layout = new CountingLayout();
        panel.setLayoutManager(layout);
        panel.add(new TestComponent(0, 0, 10, 10));
        panel.doLayout(); // clears dirty flag

        int before = layout.layoutCount;

        panel.setWidth(300);
        panel.doLayout();
        assertEquals(before + 1, layout.layoutCount, "setWidth should trigger relayout");

        before = layout.layoutCount;
        panel.setHeight(400);
        panel.doLayout();
        assertEquals(before + 1, layout.layoutCount, "setHeight should trigger relayout");
    }

    @Test
    public void testNullLayoutManagerMakesDoLayoutNoOp() {
        Panel panel = new Panel(0, 0, 200, 100);

        assertNull(panel.getLayoutManager());
        assertDoesNotThrow(panel::doLayout);

        panel.add(new TestComponent(0, 0, 10, 10));
        assertDoesNotThrow(panel::doLayout);
    }

    @Test
    public void testFlowLayoutDefaultConstructorValues() {
        FlowLayout layout = new FlowLayout();

        assertEquals(10, layout.getGap());
        assertEquals(10, layout.getPadding());
    }

    @Test
    public void testFlowLayoutGapAndPaddingSetters() {
        FlowLayout layout = new FlowLayout(4, 8);

        layout.setGap(15);
        layout.setPadding(20);

        assertEquals(15, layout.getGap());
        assertEquals(20, layout.getPadding());
    }

    @Test
    public void testFlowLayoutSkipsInvisibleComponents() {
        Panel panel = new Panel(0, 0, 200, 100);
        FlowLayout layout = new FlowLayout(10, 10);
        panel.setLayoutManager(layout);

        TestComponent visible1 = new TestComponent(0, 0, 50, 30);
        TestComponent invisible = new TestComponent(0, 0, 50, 30);
        TestComponent visible2 = new TestComponent(0, 0, 50, 30);
        invisible.setVisible(false);

        panel.add(visible1);
        panel.add(invisible);
        panel.add(visible2);
        panel.doLayout();

        // Invisible component keeps its position; visible2 should be placed
        // as if invisible never existed: x = 10 + 50 + 10 = 70
        assertEquals(10, visible1.getX());
        assertEquals(70, visible2.getX());
        assertEquals(0, invisible.getX(), "Invisible component should not be repositioned");
    }

    @Test
    public void testFlowLayoutUsesPreferredHeightOverConstructorHeight() {
        Panel panel = new Panel(0, 0, 200, 100);
        FlowLayout layout = new FlowLayout(10, 10);
        panel.setLayoutManager(layout);

        // Component whose preferred height differs from its constructor height
        TestComponent tall = new TestComponent(0, 0, 50, 5) {
            @Override
            public float getPreferredHeight() {
                return 40f;
            }
        };
        TestComponent next = new TestComponent(0, 0, 50, 30);

        panel.add(tall);
        panel.add(next);
        panel.doLayout();

        assertEquals(40f, tall.getHeight(), "Layout should apply the preferred height");

        // Force wrap to check row height uses preferred height:
        // panel width 200, padding 10 -> two 50-wide components fit, so wrap manually
        Panel narrow = new Panel(0, 0, 80, 200);
        narrow.setLayoutManager(new FlowLayout(5, 10));
        TestComponent t1 = new TestComponent(0, 0, 50, 5) {
            @Override
            public float getPreferredHeight() {
                return 40f;
            }
        };
        TestComponent t2 = new TestComponent(0, 0, 50, 30);
        narrow.add(t1);
        narrow.add(t2);
        narrow.doLayout();

        assertEquals(10, t2.getX(), "Second component should wrap to a new line");
        assertEquals(10 + 40 + 5, t2.getY(),
                "Next row must start after the preferred height of the tallest component in the previous row");
    }

    @Test
    public void testSetLayoutManagerWithExistingChildrenTriggersImmediateLayout() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(99, 99, 50, 30);
        panel.add(comp);

        FlowLayout layout = new FlowLayout(10, 10);
        panel.setLayoutManager(layout);

        // setLayoutManager should have laid out immediately (no explicit doLayout call)
        assertEquals(10, comp.getX(), "Child should be positioned when a layout manager is assigned");
        assertEquals(10, comp.getY());
    }
}
