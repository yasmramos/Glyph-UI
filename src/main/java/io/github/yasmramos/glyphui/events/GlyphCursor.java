package io.github.yasmramos.glyphui.events;

/**
 * Toolkit-owned mouse-pointer vocabulary.
 *
 * <p>The widget layer never refers to a native cursor shape. Widgets declare
 * the pointer they want through {@link io.github.yasmramos.glyphui.ui.Component#setCursor(GlyphCursor)},
 * the application resolves the shape under the pointer on every move and
 * hands the value to {@link io.github.yasmramos.glyphui.core.Window#setCursor(GlyphCursor)},
 * and the active backend maps it onto its own native cursor before applying
 * it. Keeping the vocabulary here mirrors how {@link GlyphKeys} and
 * {@link GlyphMods} isolate the toolkit from native input identifiers.</p>
 *
 * <p>The set is deliberately small: it covers the shapes a widget toolkit
 * actually needs. Backends map an unsupported shape onto their closest
 * available cursor rather than failing.</p>
 */
public enum GlyphCursor {
    /** The platform's default arrow. */
    DEFAULT,
    /** A pointing hand, shown over clickable controls (buttons, links). */
    POINTING_HAND,
    /** A text-insertion caret, shown over editable text. */
    TEXT,
    /** A crosshair, shown over precision-picking surfaces. */
    CROSSHAIR,
    /** A resize cursor for the north/south axis. */
    RESIZE_NS,
    /** A resize cursor for the east/west axis. */
    RESIZE_EW,
    /** A "busy" cursor for long-running operations. */
    WAIT,
    /** A "forbidden" cursor for disabled drop targets. */
    NOT_ALLOWED
}
