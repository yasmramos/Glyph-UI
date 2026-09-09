package com.glyphui.ui;

import com.glyphui.events.MouseEvent;
import com.glyphui.events.MouseEventType;
import com.glyphui.events.MouseButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Panel component.
 */
public class PanelTest {

    @Test
    public void testAddComponent() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);

        panel.add(comp);

        assertEquals(1, panel.getChildren().size(), "Panel should have one child");
        assertTrue(panel.getChildren().contains(comp), "Panel should contain the added component");
        assertEquals(panel, comp.getParent(), "Component's parent should be set to panel");
    }

    @Test
    public void testRemoveComponent() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);

        panel.add(comp);
        panel.remove(comp);

        assertEquals(0, panel.getChildren().size(), "Panel should have no children after removal");
        assertNull(comp.getParent(), "Component's parent should be null after removal");
    }

    @Test
    public void testClearComponents() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp1 = new TestComponent(10, 10, 50, 30);
        TestComponent comp2 = new TestComponent(70, 10, 50, 30);

        panel.add(comp1);
        panel.add(comp2);

        panel.clear();

        assertEquals(0, panel.getChildren().size(), "Panel should have no children after clear");
        assertNull(comp1.getParent(), "First component's parent should be null");
        assertNull(comp2.getParent(), "Second component's parent should be null");
    }

    @Test
    public void testParentAssignmentOnAdd() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);

        assertNull(comp.getParent(), "Component should have no parent initially");

        panel.add(comp);

        assertEquals(panel, comp.getParent(), "Component's parent should be set to panel");
    }

    @Test
    public void testParentClearingOnRemove() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);

        panel.add(comp);
        panel.remove(comp);

        assertNull(comp.getParent(), "Component's parent should be cleared on remove");
    }

    @Test
    public void testEventPropagationToVisibleEnabledChild() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);
        
        panel.add(comp);
        
        // Create a mouse event within the component bounds
        MouseEvent event = new MouseEvent(MouseEventType.MOVE, 20, 20, MouseButton.LEFT, 1);
        
        panel.onMouseEvent(event);
        
        assertTrue(comp.isMouseEventHandled(), "Visible and enabled child should receive mouse event");
    }

    @Test
    public void testEventNotPropagatedToInvisibleChild() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);
        
        comp.setVisible(false);
        panel.add(comp);
        
        MouseEvent event = new MouseEvent(MouseEventType.MOVE, 20, 20, MouseButton.LEFT, 1);
        
        panel.onMouseEvent(event);
        
        assertFalse(comp.isMouseEventHandled(), "Invisible child should not receive mouse event");
    }

    @Test
    public void testEventNotPropagatedToDisabledChild() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);
        
        comp.setEnabled(false);
        panel.add(comp);
        
        MouseEvent event = new MouseEvent(MouseEventType.MOVE, 20, 20, MouseButton.LEFT, 1);
        
        panel.onMouseEvent(event);
        
        assertFalse(comp.isMouseEventHandled(), "Disabled child should not receive mouse event");
    }
}
