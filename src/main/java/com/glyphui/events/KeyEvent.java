package com.glyphui.events;

import java.util.EnumSet;

/**
 * Represents a keyboard event with key code, character, and modifier information.
 */
public class KeyEvent {
    private final KeyEventType type;
    private final int keyCode;
    private final char keyChar;
    private final EnumSet<KeyModifier> modifiers;

    /**
     * Creates a new KeyEvent.
     *
     * @param type     the type of key event
     * @param keyCode  the numeric code of the key
     * @param keyChar  the character representation of the key
     * @param modifiers the set of modifier keys pressed
     */
    public KeyEvent(KeyEventType type, int keyCode, char keyChar, EnumSet<KeyModifier> modifiers) {
        this.type = type;
        this.keyCode = keyCode;
        this.keyChar = keyChar;
        this.modifiers = modifiers != null ? modifiers : EnumSet.noneOf(KeyModifier.class);
    }

    /**
     * Gets the type of key event.
     *
     * @return the key event type
     */
    public KeyEventType getType() {
        return type;
    }

    /**
     * Gets the numeric code of the key.
     *
     * @return the key code
     */
    public int getKeyCode() {
        return keyCode;
    }

    /**
     * Gets the character representation of the key.
     *
     * @return the key character
     */
    public char getKeyChar() {
        return keyChar;
    }

    /**
     * Gets the set of modifier keys pressed.
     *
     * @return the modifier keys
     */
    public EnumSet<KeyModifier> getModifiers() {
        return modifiers;
    }

    /**
     * Checks if the SHIFT modifier is pressed.
     *
     * @return true if SHIFT is pressed
     */
    public boolean isShiftDown() {
        return modifiers.contains(KeyModifier.SHIFT);
    }

    /**
     * Checks if the CTRL modifier is pressed.
     *
     * @return true if CTRL is pressed
     */
    public boolean isCtrlDown() {
        return modifiers.contains(KeyModifier.CTRL);
    }

    /**
     * Checks if the ALT modifier is pressed.
     *
     * @return true if ALT is pressed
     */
    public boolean isAltDown() {
        return modifiers.contains(KeyModifier.ALT);
    }
}
