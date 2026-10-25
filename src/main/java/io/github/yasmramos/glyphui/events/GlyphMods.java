package io.github.yasmramos.glyphui.events;

/**
 * Backend-neutral modifier bit flags carried by window-backend input events.
 *
 * <p>{@link io.github.yasmramos.glyphui.core.backend.WindowBackend} listeners receive the
 * modifier state as an {@code int} bitmask using these constants. Each
 * backend translates its native modifier flags to this representation
 * before invoking the listeners:</p>
 *
 * <ul>
 *   <li>JWM: {@code io.github.humbleui.jwm.KeyModifier._mask} bits are
 *       remapped via {@code JwmWindowBackend.mapModifiers(int)}.</li>
 * </ul>
 *
 * <p>The bit layout is stable toolkit API: serialized events and tests
 * already rely on these values, so they must not change.</p>
 */
public final class GlyphMods {

    private GlyphMods() {
    } // utility class

    /** Shift key (either side). */
    public static final int SHIFT = 0x0001;
    /** Control key (Windows/Linux) — mapped from MAC_COMMAND on macOS. */
    public static final int CTRL = 0x0002;
    /** Alt key (Windows/Linux) — mapped from MAC_OPTION on macOS. */
    public static final int ALT = 0x0004;
    /** Super/Win logo key (Windows/Linux) — mapped from MAC_COMMAND on macOS. */
    public static final int SUPER = 0x0008;
    /** Caps-lock active flag (informational; not used for chord matching). */
    public static final int CAPS_LOCK = 0x0010;
    /** Num-lock active flag (informational). */
    public static final int NUM_LOCK = 0x0020;
}
