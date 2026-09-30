package com.glyphui.ui;

import com.glyphui.events.MouseButton;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.MouseEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Panel mouse-event dispatch contract:
 * hit-testing children with contains() before propagation and stopping
 * propagation when a child consumes the event (onMouseEvent returns true).
 */
class PanelEventConsumptionTest {

    private Panel panel;

    @BeforeEach
    void setUp() {
        panel = new Panel(0, 0, 200, 200);
    }

    private MouseEvent clickAt(int x, int y) {
        return new MouseEvent(MouseEventType.PRESS, x, y, MouseButton.LEFT, 1);
    }

    @Test
    @DisplayName("Child outside pointer bounds does not receive the event")
    void childOutsideHitAreaIsNotDispatched() {
        TestComponent child = new TestComponent(10, 10, 50, 50);
        panel.add(child);

        // Pointer at (150, 150) is well outside the child's bounds.
        boolean consumed = panel.onMouseEvent(clickAt(150, 150));

        assertFalse(consumed, "Unconsumed event should report false");
        assertFalse(child.isMouseEventHandled(),
                "Child must not receive events when the pointer is outside its bounds");
    }

    @Test
    @DisplayName("Child inside pointer bounds receives translated coordinates")
    void childInsideHitAreaReceivesLocalCoordinates() {
        TestComponent child = new TestComponent(100, 100, 50, 50);
        panel.add(child);

        // Global (120, 130) -> local (20, 30) relative to the child.
        panel.onMouseEvent(clickAt(120, 130));

        assertTrue(child.isMouseEventHandled(),
                "Child under the pointer must receive the event");
    }

    @Test
    @DisplayName("Consuming top-most child stops propagation to siblings below")
    void consumedEventStopsPropagationToLowerSibling() {
        TestComponent bottom = new TestComponent(0, 0, 100, 100);
        TestComponent top = new TestComponent(0, 0, 100, 100);
        top.setConsumeMouseEvents(true);

        panel.add(bottom);
        panel.add(top); // Added last => drawn/dispatched first (top-most).

        boolean consumed = panel.onMouseEvent(clickAt(50, 50));

        assertTrue(consumed, "Panel should report the event as consumed");
        assertTrue(top.isMouseEventHandled(), "Top-most child should get the event first");
        assertFalse(bottom.isMouseEventHandled(),
                "Lower sibling must not receive an event already consumed by the top-most child");
    }

    @Test
    @DisplayName("Non-consuming top-most child lets the event fall through")
    void unconsumedEventFallsThroughToLowerSibling() {
        TestComponent bottom = new TestComponent(0, 0, 100, 100);
        TestComponent top = new TestComponent(0, 0, 100, 100);
        // Neither child consumes; default is non-consuming.

        panel.add(bottom);
        panel.add(top);

        boolean consumed = panel.onMouseEvent(clickAt(50, 50));

        assertFalse(consumed, "No child consumed the event");
        assertTrue(top.isMouseEventHandled());
        assertTrue(bottom.isMouseEventHandled(),
                "Event must reach lower siblings when no child consumes it");
    }

    @Test
    @DisplayName("Hidden or disabled children are skipped during dispatch")
    void invisibleOrDisabledChildrenAreSkipped() {
        TestComponent hidden = new TestComponent(0, 0, 100, 100);
        hidden.setVisible(false);
        TestComponent disabled = new TestComponent(0, 0, 100, 100);
        disabled.setEnabled(false);
        TestComponent active = new TestComponent(0, 0, 100, 100);

        panel.add(hidden);
        panel.add(disabled);
        panel.add(active);

        panel.onMouseEvent(clickAt(50, 50));

        assertFalse(hidden.isMouseEventHandled(), "Hidden child must not receive events");
        assertFalse(disabled.isMouseEventHandled(), "Disabled child must not receive events");
        assertTrue(active.isMouseEventHandled(), "Visible/enabled child must receive events");
    }

    @Test
    @DisplayName("Nested panels translate coordinates through both levels")
    void nestedPanelHitTestingUsesCombinedOffsets() {
        Panel nested = new Panel(20, 20, 100, 100);
        TestComponent child = new TestComponent(10, 10, 40, 40);
        nested.add(child);
        panel.add(nested);

        // Global (45, 45): nested-local (25, 25), child-local (15, 15) -> inside child.
        panel.onMouseEvent(clickAt(45, 45));
        assertTrue(child.isMouseEventHandled());

        child.resetFlags();

        // Global (150, 150) misses both the nested panel and its child.
        panel.onMouseEvent(clickAt(150, 150));
        assertFalse(child.isMouseEventHandled(),
                "Pointer outside the nested panel must not reach its children");
    }
}
