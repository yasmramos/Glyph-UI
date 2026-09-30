package com.glyphui.graphics;

import io.github.humbleui.skija.Font;
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
     * @param size the font size in points
     * @return a new {@code Font} backed by the shared typeface
     */
    public static Font createDefaultFont(float size) {
        return new Font(getDefaultTypeface(), size);
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
