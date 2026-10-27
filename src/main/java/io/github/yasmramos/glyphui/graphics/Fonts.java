package io.github.yasmramos.glyphui.graphics;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

/**
 * Shared, lazily-initialized Skija {@link Typeface} cache.
 *
 * <p>{@code Typeface} objects are relatively expensive to create (they load
 * font data from disk), so all widgets should share a single default instance
 * instead of calling {@code Typeface.makeFromName} per component. Creating a
 * {@link Font} from a cached typeface is cheap, so each widget can still own
 * its own {@code Font} with an individual size.</p>
 *
 * <p>The cached typeface is a process-wide resource owned by this class. It
 * must be released exactly once via {@link #close()} — typically done by
 * {@code Application.close()} after all components have been disposed, since
 * live {@code Font} instances keep a native reference to their typeface.</p>
 */
public final class Fonts {

    private static volatile Typeface defaultTypeface;

    private Fonts() {
        // Utility class: not instantiable.
    }

    /**
     * Returns the shared default typeface, creating it lazily on first use.
     * The returned instance is owned by this class; callers must NOT close it.
     *
     * @return the shared default {@code Typeface}
     */
    public static Typeface getDefaultTypeface() {
        Typeface local = defaultTypeface;
        if (local == null || local.isClosed()) {
            synchronized (Fonts.class) {
                local = defaultTypeface;
                if (local == null || local.isClosed()) {
                    local = Typeface.makeFromName(null, FontStyle.NORMAL);
                    defaultTypeface = local;
                }
            }
        }
        return local;
    }

    /**
     * Convenience method that creates a {@link Font} of the given size using
     * the shared default typeface. The returned {@code Font} is owned by the
     * caller and must be closed together with the widget that uses it.
     *
     * <p>The font is configured for crisp "modern web" text rendering:
     * subpixel antialiasing edging (with an automatic grayscale fallback on
     * backends that do not support LCD subpixel rasterization, such as GPU
     * targets) plus slight hinting, which keeps glyph shapes faithful while
     * improving legibility at small sizes.</p>
     *
     * @param size the font size in points
     * @return a new {@code Font} backed by the shared typeface
     */
    public static Font createDefaultFont(float size) {
        return applyQualityDefaults(new Font(getDefaultTypeface(), size));
    }

    /**
     * Applies Glyph-UI's global text-rendering quality settings to a font:
     * {@link FontEdging#SUBPIXEL_ANTI_ALIAS} for maximum sharpness on LCD
     * displays and {@link FontHinting#SLIGHT} as a balance between glyph
     * shape fidelity and legibility. Callers that construct {@code Font}
     * objects directly should route them through this method so every widget
     * renders text consistently.
     *
     * <p>Note: Skia silently falls back to grayscale antialiasing when the
     * destination surface does not support subpixel coverage (e.g. GPU-backed
     * canvases), so this setting never produces artifacts — worst case it is
     * equivalent to {@link FontEdging#ANTI_ALIAS}.</p>
     *
     * @param font the font to configure (not closed by this method)
     * @return the same font instance, for chaining
     */
    public static Font applyQualityDefaults(Font font) {
        java.util.Objects.requireNonNull(font, "font");
        font.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS);
        font.setHinting(FontHinting.SLIGHT);
        return font;
    }

    /**
     * Releases the shared typeface. Should be called once during application
     * shutdown, after every component holding a {@code Font} has been closed.
     * Safe to call multiple times.
     */
    public static void close() {
        synchronized (Fonts.class) {
            Typeface local = defaultTypeface;
            defaultTypeface = null;
            if (local != null && !local.isClosed()) {
                local.close();
            }
        }
    }
}
