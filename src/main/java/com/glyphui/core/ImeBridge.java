package com.glyphui.core;

import com.glyphui.core.backend.WindowBackend;
import com.glyphui.ui.Component;
import com.glyphui.ui.TextField;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;

/**
 * Bridges the toolkit's focused {@link TextField} to the window backend's
 * native input method (IME), implementing {@link WindowBackend.ImeClient}.
 *
 * <p>When the OS input method asks where the caret is (to position its
 * candidate/composition window), this client computes the caret rectangle of
 * the currently focused text field in <b>logical window coordinates</b> —
 * mirroring the geometry used by {@code TextField.render} — and scales it to
 * physical screen pixels using the window content scale plus the window's
 * on-screen position.</p>
 *
 * <p>Registered by {@link Application} during {@code init()}; backends
 * without IME support (GLFW) simply never query it.</p>
 */
final class ImeBridge implements WindowBackend.ImeClient {

    private final Application application;
    private final Window window;

    ImeBridge(Application application, Window window) {
        this.application = application;
        this.window = window;
    }

    /** @return the focused TextField, or null when no text field has focus. */
    static TextField focusedTextField() {
        FocusManager fm = FocusManager.getGlobalFocusManager();
        if (fm == null) {
            return null;
        }
        Component focused = fm.getFocused();
        return focused instanceof TextField ? (TextField) focused : null;
    }

    @Override
    public String getText() {
        TextField field = focusedTextField();
        return field != null ? field.getText() : null;
    }

    @Override
    public int[] getCursorRect(int insertionStart, int insertionEnd) {
        TextField field = focusedTextField();
        if (field == null || !field.isVisible()) {
            return null;
        }

        // Caret geometry in LOGICAL window coordinates, matching the
        // computation inside TextField.render(): x accumulates through the
        // component's parent chain, offset by the field padding + the text
        // width up to the requested insertion offset.
        float padding = com.glyphui.graphics.Theme.current().getPadding();
        Font font = field.getFontForIme();
        float lineHeight;
        try {
            FontMetrics metrics = font.getMetrics();
            lineHeight = metrics.getDescent() - metrics.getAscent();
        } catch (RuntimeException e) {
            lineHeight = field.getHeight();
        }

        float left = absoluteX(field) + padding + measurePrefix(field, font, insertionStart);
        float right = absoluteX(field) + padding + measurePrefix(field, font, insertionEnd);
        if (right <= left) {
            right = left + 1f; // zero-width caret still needs a non-empty rect
        }
        float top = absoluteY(field) + padding - 2.0f;
        float bottom = top + lineHeight + 4.0f;

        // Convert logical window coords -> physical SCREEN coords: scale by
        // the DPI factor and translate by the window position (which the
        // backend reports in logical screen units).
        float sx = Math.max(window.getContentScaleX(), 0.01f);
        float sy = Math.max(window.getContentScaleY(), 0.01f);
        int[] winPos = window.getPosition();
        int ox = winPos != null ? winPos[0] : 0;
        int oy = winPos != null ? winPos[1] : 0;

        return new int[]{
            Math.round((ox + left) * sx),
            Math.round((oy + top) * sy),
            Math.max(1, Math.round((right - left) * sx)),
            Math.max(1, Math.round((bottom - top) * sy)),
        };
    }

    private static float measurePrefix(TextField field, Font font, int offset) {
        String text = field.getText();
        int clamped = Math.max(0, Math.min(offset, text.length()));
        return clamped == 0 ? 0f : font.measureTextWidth(text.substring(0, clamped));
    }

    private static float absoluteX(Component c) {
        float x = c.getX();
        for (Component p = c.getParent(); p != null; p = p.getParent()) {
            x += p.getX();
        }
        // The root panel sits at the window origin; nothing else to add.
        return x;
    }

    private static float absoluteY(Component c) {
        float y = c.getY();
        for (Component p = c.getParent(); p != null; p = p.getParent()) {
            y += p.getY();
        }
        return y;
    }
}
