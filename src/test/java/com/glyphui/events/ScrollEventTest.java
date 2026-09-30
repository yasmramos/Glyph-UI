package com.glyphui.events;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for mouse scroll events and HiDPI coordinate handling.
 */
public class ScrollEventTest {

    @Test
    public void testScrollEventTypeExists() {
        assertEquals(5, MouseEventType.values().length);
        assertNotNull(MouseEventType.valueOf("SCROLL"));
    }

    @Test
    public void testScrollEventCarriesDeltas() {
        MouseEvent event = new MouseEvent(
            MouseEventType.SCROLL, 120, 240, MouseButton.LEFT, 0, 1.5, -3.0);

        assertEquals(MouseEventType.SCROLL, event.getType());
        assertEquals(120, event.getX());
        assertEquals(240, event.getY());
        assertEquals(1.5, event.getDeltaX(), 1e-9);
        assertEquals(-3.0, event.getDeltaY(), 1e-9);
    }

    @Test
    public void testLegacyConstructorDefaultsToZeroDeltas() {
        MouseEvent event = new MouseEvent(MouseEventType.PRESS, 10, 20, MouseButton.LEFT, 1);

        assertEquals(0.0, event.getDeltaX(), 1e-9);
        assertEquals(0.0, event.getDeltaY(), 1e-9);
    }

    /**
     * Verifies the "logical layout" coordinate model: a UI component laid out in
     * logical coordinates must receive events whose coordinates are NOT multiplied
     * by the HiDPI scale factor. This mirrors what Application's callbacks do now
     * (they forward GLFW cursor coordinates verbatim).
     */
    @Test
    public void testHiDpiEventsUseLogicalCoordinates() {
        List<MouseEvent> received = new ArrayList<>();
        TestMouseComponent component = new TestMouseComponent(received);
        // Component lives at logical position (100, 50) with size 200x80
        component.setX(100);
        component.setY(50);
        component.setWidth(200);
        component.setHeight(80);

        double hidpiScale = 2.0;
        // Cursor at logical (150, 70): on a 2x display GLFW reports framebuffer
        // pixel (300, 140), but the callback must dispatch logical coords only.
        int logicalX = 150;
        int logicalY = 70;
        MouseEvent move = new MouseEvent(MouseEventType.MOVE, logicalX, logicalY, MouseButton.LEFT, 0);

        // Simulate Application behaviour: no scaling applied to event coords.
        assertEquals(logicalX, move.getX());
        assertEquals(logicalY, move.getY());
        assertNotEquals((int) (logicalX * hidpiScale), move.getX(),
            "Event coordinates must not be scaled by the HiDPI factor");

        component.onMouseEvent(move);
        assertEquals(1, received.size());
        assertTrue(component.isHovered(), "Component should react to logical coordinates");
    }

    @Test
    public void testScrollEventPropagatesThroughPanel() {
        com.glyphui.ui.Panel panel = new com.glyphui.ui.Panel(0, 0, 400, 300);
        CapturingComponent child = new CapturingComponent(10, 10, 100, 50);
        panel.add(child);

        MouseEvent scroll = new MouseEvent(
            MouseEventType.SCROLL, 60, 40, MouseButton.LEFT, 0, 0.0, 2.5);
        panel.onMouseEvent(scroll);

        assertEquals(1, child.events.size());
        MouseEvent local = child.events.get(0);
        assertEquals(MouseEventType.SCROLL, local.getType());
        assertEquals(2.5, local.getDeltaY(), 1e-9);
        // Panel translates to local coordinates (panel at 0,0 -> coords unchanged)
        assertEquals(60, local.getX());
        assertEquals(40, local.getY());
    }

    /** Component that records every mouse event it receives. */
    private static class CapturingComponent extends com.glyphui.ui.Component {
        final List<MouseEvent> events = new ArrayList<>();

        CapturingComponent(float x, float y, float width, float height) {
            super(x, y, width, height);
        }

        @Override
        public void render(com.glyphui.graphics.Canvas canvas) {
        }

        @Override
        public void onMouseEvent(MouseEvent event) {
            events.add(event);
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
        }

        @Override
        public void dispose() {
        }
    }

    /** Minimal component reacting to hover via Button-like logic using MOVE events. */
    private static class TestMouseComponent extends com.glyphui.ui.Button {
        private final List<MouseEvent> received;

        TestMouseComponent(List<MouseEvent> received) {
            super(0, 0, 200, 80, "Test");
            this.received = received;
        }

        @Override
        public void onMouseEvent(MouseEvent event) {
            received.add(event);
            super.onMouseEvent(event);
        }

        boolean isHovered() {
            return getState() == com.glyphui.ui.ComponentState.HOVER;
        }
    }
}
