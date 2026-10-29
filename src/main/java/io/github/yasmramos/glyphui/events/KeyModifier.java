package io.github.yasmramos.glyphui.events;

/**
 * Represents modifier keys that can be pressed during a key event.
 */
public enum KeyModifier {
    SHIFT,
    CTRL,
    ALT,
    /**
     * Super/Win logo key on Windows/Linux. On macOS the backend maps the
     * Command key onto {@link #CTRL} (the conventional shortcut modifier),
     * so widgets should treat {@code CTRL} as "Command" when porting from
     * Swing/AWT — see GlyphMods.CTRL javadoc for the remapping rules.
     */
    SUPER
}
