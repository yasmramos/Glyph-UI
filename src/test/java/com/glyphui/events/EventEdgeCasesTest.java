package com.glyphui.events;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Additional unit tests for MouseEvent and KeyEvent edge cases:
 * immutability, null modifiers, multiple simultaneous modifiers.
 */
public class EventEdgeCasesTest {

    // ---------- MouseEvent ----------

    @Test
    public void testMouseEventIsImmutable() {
        MouseEvent event = new MouseEvent(MouseEventType.PRESS, 10, 20, MouseButton.LEFT, 2);

        assertEquals(MouseEventType.PRESS, event.getType());
        assertEquals(10, event.getX());
        assertEquals(20, event.getY());
        assertEquals(MouseButton.LEFT, event.getButton());
        assertEquals(2, event.getClickCount());

        // No setters exist on MouseEvent; verify via reflection that the
        // declared fields are all final.
        for (var field : MouseEvent.class.getDeclaredFields()) {
            assertTrue(java.lang.reflect.Modifier.isFinal(field.getModifiers()),
                    "Field " + field.getName() + " should be final");
        }
    }

    @Test
    public void testMouseEventWithNegativeCoordinates() {
        MouseEvent event = new MouseEvent(MouseEventType.MOVE, -50, -100, MouseButton.RIGHT, 0);

        assertEquals(-50, event.getX());
        assertEquals(-100, event.getY());
        assertEquals(MouseButton.RIGHT, event.getButton());
        assertEquals(0, event.getClickCount());
    }

    @Test
    public void testMouseButtonEnumValues() {
        assertNotNull(MouseButton.valueOf("LEFT"));
        assertNotNull(MouseButton.valueOf("RIGHT"));
        assertNotNull(MouseButton.valueOf("MIDDLE"));
        assertThrows(IllegalArgumentException.class, () -> MouseButton.valueOf("WHEEL9"));
    }

    @Test
    public void testMouseEventTypeEnumValues() {
        assertNotNull(MouseEventType.valueOf("PRESS"));
        assertNotNull(MouseEventType.valueOf("RELEASE"));
        assertNotNull(MouseEventType.valueOf("MOVE"));
    }

    // ---------- KeyEvent ----------

    @Test
    public void testNullModifiersBecomeEmptySet() {
        KeyEvent event = new KeyEvent(KeyEventType.PRESS, 65, 'A', null);

        assertNotNull(event.getModifiers(), "Null modifiers should default to empty EnumSet");
        assertTrue(event.getModifiers().isEmpty());
        assertFalse(event.isShiftDown());
        assertFalse(event.isCtrlDown());
        assertFalse(event.isAltDown());
    }

    @Test
    public void testMultipleSimultaneousModifiers() {
        EnumSet<KeyModifier> mods = EnumSet.of(KeyModifier.SHIFT, KeyModifier.CTRL, KeyModifier.ALT);
        KeyEvent event = new KeyEvent(KeyEventType.PRESS, 83, 'S', mods);

        assertTrue(event.isShiftDown());
        assertTrue(event.isCtrlDown());
        assertTrue(event.isAltDown());
        assertEquals(3, event.getModifiers().size());
    }

    @Test
    public void testKeyEventIsKeyChar() {
        KeyEvent event = new KeyEvent(KeyEventType.RELEASE, 0, '\t', EnumSet.noneOf(KeyModifier.class));

        assertEquals('\t', event.getKeyChar());
        assertEquals(KeyEventType.RELEASE, event.getType());
    }

    @Test
    public void testKeyEventTypeAndModifierEnumsExist() {
        assertNotNull(KeyEventType.valueOf("PRESS"));
        assertNotNull(KeyEventType.valueOf("RELEASE"));
        assertNotNull(KeyModifier.valueOf("SHIFT"));
        assertNotNull(KeyModifier.valueOf("CTRL"));
        assertNotNull(KeyModifier.valueOf("ALT"));
    }
}
