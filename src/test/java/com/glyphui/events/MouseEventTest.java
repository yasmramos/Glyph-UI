package com.glyphui.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MouseEvent class.
 */
public class MouseEventTest {

    @Test
    public void testMouseEventConstruction() {
        MouseEvent event = new MouseEvent(
            MouseEventType.PRESS,
            100, 200,
            MouseButton.LEFT,
            1
        );

        assertEquals(MouseEventType.PRESS, event.getType());
        assertEquals(100, event.getX());
        assertEquals(200, event.getY());
        assertEquals(MouseButton.LEFT, event.getButton());
        assertEquals(1, event.getClickCount());
    }

    @Test
    public void testMouseEventWithDifferentTypes() {
        MouseEvent pressEvent = new MouseEvent(MouseEventType.PRESS, 0, 0, MouseButton.LEFT, 1);
        MouseEvent releaseEvent = new MouseEvent(MouseEventType.RELEASE, 0, 0, MouseButton.LEFT, 1);
        MouseEvent moveEvent = new MouseEvent(MouseEventType.MOVE, 0, 0, MouseButton.LEFT, 1);
        MouseEvent dragEvent = new MouseEvent(MouseEventType.DRAG, 0, 0, MouseButton.LEFT, 1);

        assertEquals(MouseEventType.PRESS, pressEvent.getType());
        assertEquals(MouseEventType.RELEASE, releaseEvent.getType());
        assertEquals(MouseEventType.MOVE, moveEvent.getType());
        assertEquals(MouseEventType.DRAG, dragEvent.getType());
    }

    @Test
    public void testMouseEventWithDifferentButtons() {
        MouseEvent leftEvent = new MouseEvent(MouseEventType.PRESS, 0, 0, MouseButton.LEFT, 1);
        MouseEvent rightEvent = new MouseEvent(MouseEventType.PRESS, 0, 0, MouseButton.RIGHT, 1);
        MouseEvent middleEvent = new MouseEvent(MouseEventType.PRESS, 0, 0, MouseButton.MIDDLE, 1);

        assertEquals(MouseButton.LEFT, leftEvent.getButton());
        assertEquals(MouseButton.RIGHT, rightEvent.getButton());
        assertEquals(MouseButton.MIDDLE, middleEvent.getButton());
    }

    @Test
    public void testMouseEventClickCount() {
        MouseEvent singleClick = new MouseEvent(MouseEventType.PRESS, 0, 0, MouseButton.LEFT, 1);
        MouseEvent doubleClick = new MouseEvent(MouseEventType.PRESS, 0, 0, MouseButton.LEFT, 2);

        assertEquals(1, singleClick.getClickCount());
        assertEquals(2, doubleClick.getClickCount());
    }
}
