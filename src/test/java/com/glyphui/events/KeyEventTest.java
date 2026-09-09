package com.glyphui.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.EnumSet;

/**
 * Unit tests for KeyEvent class.
 */
public class KeyEventTest {

    @Test
    public void testKeyEventConstruction() {
        EnumSet<KeyModifier> modifiers = EnumSet.of(KeyModifier.CTRL);
        KeyEvent event = new KeyEvent(
            KeyEventType.PRESS,
            65, // 'A' key code
            'a',
            modifiers
        );

        assertEquals(KeyEventType.PRESS, event.getType());
        assertEquals(65, event.getKeyCode());
        assertEquals('a', event.getKeyChar());
        assertTrue(event.isCtrlDown());
        assertFalse(event.isShiftDown());
        assertFalse(event.isAltDown());
    }

    @Test
    public void testKeyEventWithDifferentTypes() {
        KeyEvent pressEvent = new KeyEvent(KeyEventType.PRESS, 0, '\0', EnumSet.noneOf(KeyModifier.class));
        KeyEvent releaseEvent = new KeyEvent(KeyEventType.RELEASE, 0, '\0', EnumSet.noneOf(KeyModifier.class));

        assertEquals(KeyEventType.PRESS, pressEvent.getType());
        assertEquals(KeyEventType.RELEASE, releaseEvent.getType());
    }

    @Test
    public void testKeyEventWithDifferentModifiers() {
        KeyEvent noMod = new KeyEvent(KeyEventType.PRESS, 0, '\0', EnumSet.noneOf(KeyModifier.class));
        KeyEvent shiftMod = new KeyEvent(KeyEventType.PRESS, 0, '\0', EnumSet.of(KeyModifier.SHIFT));
        KeyEvent ctrlMod = new KeyEvent(KeyEventType.PRESS, 0, '\0', EnumSet.of(KeyModifier.CTRL));
        KeyEvent altMod = new KeyEvent(KeyEventType.PRESS, 0, '\0', EnumSet.of(KeyModifier.ALT));

        assertFalse(noMod.isShiftDown() || noMod.isCtrlDown() || noMod.isAltDown());
        assertTrue(shiftMod.isShiftDown());
        assertTrue(ctrlMod.isCtrlDown());
        assertTrue(altMod.isAltDown());
    }

    @Test
    public void testKeyEventWithKeyCodeAndChar() {
        KeyEvent event = new KeyEvent(KeyEventType.PRESS, 32, ' ', EnumSet.noneOf(KeyModifier.class));

        assertEquals(32, event.getKeyCode(), "Space bar key code");
        assertEquals(' ', event.getKeyChar(), "Space character");
    }

    @Test
    public void testKeyEventEnterKey() {
        KeyEvent event = new KeyEvent(KeyEventType.PRESS, 257, '\n', EnumSet.noneOf(KeyModifier.class));

        assertEquals(257, event.getKeyCode(), "Enter key code (GLFW_KEY_ENTER)");
        assertEquals('\n', event.getKeyChar());
    }
}
