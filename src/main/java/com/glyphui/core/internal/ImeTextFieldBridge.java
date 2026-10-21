package com.glyphui.core.internal;

import io.github.humbleui.skija.Font;

/**
 * Compile-time seam between the IME subsystem ({@code com.glyphui.core}) and
 * text-input widgets ({@code com.glyphui.ui}).
 *
 * <p>The toolkit freezes its public API surface (see
 * {@code docs/architecture/api-stability.md}): implementation helpers such as
 * "which font does this field lay out with?" or "replace this buffer range"
 * must not become public widget API just because a cross-package collaborator
 * needs them. Widgets that participate in IME composition implement this
 * bridge; {@link com.glyphui.core.ImeBridge} and the application's IME commit
 * path reach the widget through it instead of through type-specific public
 * getters.</p>
 *
 * <p><strong>Not public API:</strong> may change or disappear in any release,
 * including patch releases.</p>
 */
public interface ImeTextFieldBridge {

    /**
     * Returns the font used to lay out the field's text. The IME caret-rect
     * computation must measure exactly like the widget's renderer does.
     *
     * @return the rendering font (never null once a theme exists)
     */
    Font getFontForIme();

    /**
     * Replaces the given absolute buffer range with {@code newText}, placing
     * the caret at {@code newCaret}. Used by the IME commit path, whose
     * replacement ranges are absolute offsets rather than "insert at caret".
     *
     * @param lo       start of the range to replace (clamped by the callee)
     * @param hi       end of the range to replace (clamped, >= lo)
     * @param newText  the committed text
     * @param newCaret caret position after the replacement (clamped)
     */
    void imeReplaceRange(int lo, int hi, String newText, int newCaret);
}
