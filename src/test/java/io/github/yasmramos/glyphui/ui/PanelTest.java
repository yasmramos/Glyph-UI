package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Panel component.
 */
public class PanelTest extends TestBase {

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

    // ---- Re-parenting / non-destructive removal (Block-1 regression) ------

    @Test
    public void removedComponentStaysUsableAndCanBeReAdded() {
        // remove() must NOT dispose the child: the Javadoc promises the
        // component may be re-added to any panel afterwards.
        Panel panelA = new Panel(0, 0, 200, 100);
        Panel panelB = new Panel(0, 0, 200, 100);
        TestComponent comp = new TestComponent(10, 10, 50, 30);

        panelA.add(comp);
        panelA.remove(comp);

        assertFalse(comp.isDisposed(), "remove() must not dispose the component");
        assertNull(comp.getParent());

        panelB.add(comp);
        assertSame(panelB, comp.getParent(), "the same instance must be re-addable");
        assertEquals(1, panelB.getChildren().size());
    }

    @Test
    public void addMovesChildBetweenPanelsWithoutDisposing() {
        // Panel.add removes the child from its previous parent; that implicit
        // move must keep the component alive and usable.
        Panel panelA = new Panel(0, 0, 200, 100);
        Panel panelB = new Panel(0, 0, 200, 100);
        Label label = new Label("hello");

        panelA.add(label);
        panelB.add(label);

        assertEquals(0, panelA.getChildren().size());
        assertEquals(1, panelB.getChildren().size());
        assertFalse(label.isDisposed());
        // Still functional after the move: mutation works on a live component.
        label.setText("updated");
        assertEquals("updated", label.getText());
    }

    @Test
    public void clearKeepsChildrenAliveButClearAndDisposeDisposesThem() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent kept = new TestComponent(10, 10, 50, 30);
        TestComponent disposed = new TestComponent(70, 10, 50, 30);
        panel.add(kept);
        panel.add(disposed);

        panel.clear();
        assertFalse(kept.isDisposed(), "clear() is non-destructive");
        assertFalse(disposed.isDisposed());

        Panel panel2 = new Panel(0, 0, 200, 100);
        TestComponent child = new TestComponent(10, 10, 50, 30);
        panel2.add(child);
        panel2.clearAndDispose();
        assertTrue(child.isDisposed(), "clearAndDispose() disposes detached children");
        assertEquals(0, panel2.getChildren().size());
    }

    @Test
    public void removeAndDisposeIsExplicitlyDestructive() {
        Panel panel = new Panel(0, 0, 200, 100);
        TestComponent child = new TestComponent(10, 10, 50, 30);
        panel.add(child);

        panel.removeAndDispose(child);

        assertEquals(0, panel.getChildren().size());
        assertTrue(child.isDisposed(), "removeAndDispose() must dispose the component");
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
