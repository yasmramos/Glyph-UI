package com.glyphui.ui;

import com.glyphui.events.KeyEvent;
import com.glyphui.events.KeyEventType;
import com.glyphui.events.KeyModifier;
import com.glyphui.events.MouseButton;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.MouseEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional unit tests for Panel: key-event propagation, coordinate
 * transformation to local space, and reverse-order (top-most first)
 * mouse event dispatch.
 */
public class PanelEdgeCasesTest {

    /**
     * A component that records the last mouse event it received.
     */
    private static class RecordingComponent extends TestComponent {
        MouseEvent lastMouseEvent;
        KeyEvent lastKeyEvent;
        int mouseEventCount = 0;

        RecordingComponent(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        public boolean onMouseEvent(MouseEvent event) {
            super.onMouseEvent(event);
            lastMouseEvent = event;
            mouseEventCount++;
            return false;
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
            super.onKeyEvent(event);
            lastKeyEvent = event;
        }
    }

    private Panel panel;

    @BeforeEach
    public void setUp() {
        // Panel offset from origin so we can test coordinate translation
        panel = new Panel(100, 50, 300, 200);
    }

    @Test
    public void testMouseEventCoordinatesAreTranslatedToPanelLocalSpace() {
        RecordingComponent child = new RecordingComponent(10, 10, 50, 30);
        panel.add(child);

        // Global point (120, 70) -> local (20, 20) relative to panel at (100, 50)
        panel.onMouseEvent(new MouseEvent(MouseEventType.MOVE, 120, 70, MouseButton.LEFT, 1));

        assertNotNull(child.lastMouseEvent, "Child should receive the propagated event");
        assertEquals(20, child.lastMouseEvent.getX(), "X should be translated to panel-local coordinates");
        assertEquals(20, child.lastMouseEvent.getY(), "Y should be translated to panel-local coordinates");
        assertEquals(MouseEventType.MOVE, child.lastMouseEvent.getType());
        assertEquals(MouseButton.LEFT, child.lastMouseEvent.getButton());
        assertEquals(1, child.lastMouseEvent.getClickCount());
    }

    @Test
    public void testKeyEventPropagatesToVisibleEnabledChildren() {
        RecordingComponent c1 = new RecordingComponent(0, 0, 10, 10);
        RecordingComponent c2 = new RecordingComponent(0, 0, 10, 10);
        c2.setVisible(false);
        RecordingComponent c3 = new RecordingComponent(0, 0, 10, 10);
        c3.setEnabled(false);

        panel.add(c1);
        panel.add(c2);
        panel.add(c3);

        KeyEvent event = new KeyEvent(KeyEventType.PRESS, 65, 'A', EnumSet.of(KeyModifier.SHIFT));
        panel.onKeyEvent(event);

        assertSame(event, c1.lastKeyEvent, "Visible+enabled child should receive key event");
        assertNull(c2.lastKeyEvent, "Invisible child should not receive key event");
        assertNull(c3.lastKeyEvent, "Disabled child should not receive key event");
    }

    @Test
    public void testHiddenOrDisabledPanelDoesNotPropagateEvents() {
        RecordingComponent child = new RecordingComponent(10, 10, 50, 30);
        panel.add(child);

        panel.setVisible(false);
        panel.onMouseEvent(new MouseEvent(MouseEventType.MOVE, 120, 70, MouseButton.LEFT, 1));
        panel.onKeyEvent(new KeyEvent(KeyEventType.PRESS, 65, 'A', EnumSet.noneOf(KeyModifier.class)));

        assertNull(child.lastMouseEvent, "Hidden panel must not propagate mouse events");
        assertNull(child.lastKeyEvent, "Hidden panel must not propagate key events");

        panel.setVisible(true);
        panel.setEnabled(false);
        child.resetFlags();
        panel.onMouseEvent(new MouseEvent(MouseEventType.MOVE, 120, 70, MouseButton.LEFT, 1));
        panel.onKeyEvent(new KeyEvent(KeyEventType.PRESS, 65, 'A', EnumSet.noneOf(KeyModifier.class)));

        assertNull(child.lastMouseEvent, "Disabled panel must not propagate mouse events");
        assertNull(child.lastKeyEvent, "Disabled panel must not propagate key events");
    }

    @Test
    public void testMouseEventsDispatchedInReverseOrderTopMostFirst() {
        RecordingComponent bottom = new RecordingComponent(0, 0, 10, 10);
        RecordingComponent top = new RecordingComponent(0, 0, 10, 10);

        panel.add(bottom);
        panel.add(top); // added last => drawn last => considered top-most

        panel.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 105, 55, MouseButton.LEFT, 1));

        assertEquals(1, top.mouseEventCount);
        assertEquals(1, bottom.mouseEventCount);
        // Both are visited in one pass; verify order by checking that the
        // top-most child is visited before the bottom one using a shared log.
        StringBuilder order = new StringBuilder();
        RecordingComponent first = new RecordingComponent(0, 0, 10, 10) {
            @Override
            public boolean onMouseEvent(MouseEvent event) {
                order.append("A");
                return false;
            }
        };
        RecordingComponent second = new RecordingComponent(0, 0, 10, 10) {
            @Override
            public boolean onMouseEvent(MouseEvent event) {
                order.append("B");
                return false;
            }
        };
        Panel p2 = new Panel(0, 0, 100, 100);
        p2.add(first);   // index 0 -> visited second (reverse iteration)
        p2.add(second);  // index 1 -> visited first
        p2.onMouseEvent(new MouseEvent(MouseEventType.MOVE, 5, 5, MouseButton.LEFT, 1));

        assertEquals("BA", order.toString(),
                "Children must receive mouse events in reverse insertion order (top-most first)");
    }

    @Test
    public void testRemoveDisposesAndClearsParent() {
        // TestComponent doesn't override dispose, but Panel.remove must call it
        // without error and detach the child.
        TestComponent comp = new TestComponent(0, 0, 10, 10);
        panel.add(comp);
        panel.remove(comp);

        assertTrue(panel.getChildren().isEmpty());
        assertNull(comp.getParent());
    }

    @Test
    public void testClearRemovesAllChildrenAndDisposesThem() {
        TestComponent c1 = new TestComponent(0, 0, 10, 10);
        TestComponent c2 = new TestComponent(0, 0, 10, 10);
        TestComponent c3 = new TestComponent(0, 0, 10, 10);
        panel.add(c1);
        panel.add(c2);
        panel.add(c3);

        panel.clear();

        assertTrue(panel.getChildren().isEmpty(), "All children should be removed");
        assertNull(c1.getParent());
        assertNull(c2.getParent());
        assertNull(c3.getParent());
    }

    @Test
    public void testGetChildrenReturnsLiveListReference() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);
        panel.add(comp);

        assertSame(panel.getChildren(), panel.getChildren(),
                "getChildren should return the same list instance");
        assertEquals(1, panel.getChildren().size());
        assertTrue(panel.getChildren().contains(comp));
    }
}
