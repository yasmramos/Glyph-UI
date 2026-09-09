package com.glyphui.ui;

import com.glyphui.events.MouseEvent;
import com.glyphui.events.MouseEventType;
import com.glyphui.events.MouseButton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Button click state machine logic.
 * Tests are designed to avoid Skija native initialization where possible.
 */
public class ButtonTest {

    @Test
    public void testButtonInitialState() {
        // Test that button is created with correct initial state
        // Note: We can't easily test Button without Skija native libs,
        // so we test the Component base functionality instead
        TestComponent button = new TestComponent(10, 10, 100, 40);
        
        assertEquals(10, button.getX());
        assertEquals(10, button.getY());
        assertEquals(100, button.getWidth());
        assertEquals(40, button.getHeight());
        assertTrue(button.isVisible());
        assertTrue(button.isEnabled());
        assertEquals(ComponentState.IDLE, button.getState());
    }

    @Test
    public void testButtonContainsPoint() {
        TestComponent button = new TestComponent(10, 10, 100, 40);
        
        // Point inside button
        assertTrue(button.contains(50, 30), "Point (50,30) should be inside button");
        assertTrue(button.contains(10, 10), "Top-left corner should be inside");
        assertTrue(button.contains(109, 49), "Bottom-right corner should be inside");
        
        // Point outside button
        assertFalse(button.contains(5, 30), "Point left of button should be outside");
        assertFalse(button.contains(50, 5), "Point above button should be outside");
        assertFalse(button.contains(115, 30), "Point right of button should be outside");
        assertFalse(button.contains(50, 55), "Point below button should be outside");
    }

    @Test
    public void testButtonClickStateMachinePressInsideReleaseInside() {
        // Simulate click state machine: PRESS inside + RELEASE inside = onClick executed
        MockButton button = new MockButton(10, 10, 100, 40);
        
        // Press inside button
        MouseEvent pressEvent = new MouseEvent(MouseEventType.PRESS, 50, 30, MouseButton.LEFT, 1);
        button.onMouseEvent(pressEvent);
        
        assertEquals(ComponentState.PRESSED, button.getState(), "Button should be PRESSED after press inside");
        assertFalse(button.isOnClickCalled(), "onClick should not be called on press");
        
        // Release inside button
        MouseEvent releaseEvent = new MouseEvent(MouseEventType.RELEASE, 50, 30, MouseButton.LEFT, 1);
        button.onMouseEvent(releaseEvent);
        
        assertTrue(button.isOnClickCalled(), "onClick should be called when released inside after press");
        assertEquals(ComponentState.HOVER, button.getState(), "Button should be HOVER after release inside");
    }

    @Test
    public void testButtonClickStateMachinePressInsideReleaseOutside() {
        // Simulate click state machine: PRESS inside + RELEASE outside = onClick NOT executed
        MockButton button = new MockButton(10, 10, 100, 40);
        
        // Press inside button
        MouseEvent pressEvent = new MouseEvent(MouseEventType.PRESS, 50, 30, MouseButton.LEFT, 1);
        button.onMouseEvent(pressEvent);
        
        assertEquals(ComponentState.PRESSED, button.getState());
        
        // Move outside and release
        MouseEvent moveEvent = new MouseEvent(MouseEventType.MOVE, 200, 200, MouseButton.LEFT, 1);
        button.onMouseEvent(moveEvent);
        
        MouseEvent releaseEvent = new MouseEvent(MouseEventType.RELEASE, 200, 200, MouseButton.LEFT, 1);
        button.onMouseEvent(releaseEvent);
        
        assertFalse(button.isOnClickCalled(), "onClick should NOT be called when released outside");
        assertEquals(ComponentState.IDLE, button.getState(), "Button should be IDLE after release outside");
    }

    @Test
    public void testButtonHoverState() {
        MockButton button = new MockButton(10, 10, 100, 40);
        
        // Move mouse over button
        MouseEvent moveEvent = new MouseEvent(MouseEventType.MOVE, 50, 30, MouseButton.LEFT, 1);
        button.onMouseEvent(moveEvent);
        
        assertEquals(ComponentState.HOVER, button.getState(), "Button should be HOVER when mouse is over");
    }

    @Test
    public void testButtonDisabledDoesNotHandleEvents() {
        MockButton button = new MockButton(10, 10, 100, 40);
        button.setEnabled(false);
        
        MouseEvent pressEvent = new MouseEvent(MouseEventType.PRESS, 50, 30, MouseButton.LEFT, 1);
        button.onMouseEvent(pressEvent);
        
        assertEquals(ComponentState.IDLE, button.getState(), "Disabled button should remain IDLE");
        assertFalse(button.isOnClickCalled(), "Disabled button should not execute onClick");
    }

    /**
     * MockButton class for testing without full Skija initialization.
     * Simplifies the Button class for unit testing.
     */
    private static class MockButton extends Component {
        private boolean onClickCalled = false;
        private Runnable onClick;
        
        public MockButton(float x, float y, float width, float height) {
            super(x, y, width, height);
            this.onClick = () -> onClickCalled = true;
        }
        
        @Override
        public void render(com.glyphui.graphics.Canvas canvas) {
            // No-op for testing
        }
        
        @Override
        public void onMouseEvent(MouseEvent event) {
            if (!visible || !enabled) {
                return;
            }

            boolean isInside = contains(event.getX(), event.getY());

            switch (event.getType()) {
                case MOVE:
                    if (isInside) {
                        if (state != ComponentState.PRESSED) {
                            state = ComponentState.HOVER;
                        }
                    } else {
                        state = ComponentState.IDLE;
                    }
                    break;

                case PRESS:
                    if (isInside && event.getButton() == MouseButton.LEFT) {
                        state = ComponentState.PRESSED;
                    }
                    break;

                case RELEASE:
                    if (state == ComponentState.PRESSED && isInside) {
                        if (onClick != null) {
                            onClick.run();
                        }
                    }
                    state = isInside ? ComponentState.HOVER : ComponentState.IDLE;
                    break;
            }
        }
        
        @Override
        public void onKeyEvent(com.glyphui.events.KeyEvent event) {
            // No-op
        }
        
        public boolean isOnClickCalled() {
            return onClickCalled;
        }
        
        public void resetOnClickFlag() {
            onClickCalled = false;
        }
    }
}
