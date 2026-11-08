package io.github.yasmramos.glyphui.events;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the {@link GlyphCursor} vocabulary: it must stay small, expose
 * a non-null default and keep a stable declaration order (the order documents
 * intent and is what the JWM mapping switches on).
 */
class GlyphCursorTest {

    @Test
    void defaultShapeIsTheArrow() {
        assertEquals(GlyphCursor.DEFAULT, GlyphCursor.valueOf("DEFAULT"));
        assertNotNull(GlyphCursor.DEFAULT);
    }

    @Test
    void exposesTheShapesTheToolkitMapsToNativeCursors() {
        // Every constant the backends switch on must exist; adding one here
        // without updating JwmWindowBackend#toNativeCursor breaks compilation
        // there, which is the intended coupling.
        assertNotNull(GlyphCursor.valueOf("POINTING_HAND"));
        assertNotNull(GlyphCursor.valueOf("TEXT"));
        assertNotNull(GlyphCursor.valueOf("CROSSHAIR"));
        assertNotNull(GlyphCursor.valueOf("RESIZE_NS"));
        assertNotNull(GlyphCursor.valueOf("RESIZE_EW"));
        assertNotNull(GlyphCursor.valueOf("WAIT"));
        assertNotNull(GlyphCursor.valueOf("NOT_ALLOWED"));
    }

    @Test
    void defaultIsDeclaredFirstSoItReadsAsTheBaseline() {
        assertEquals(GlyphCursor.DEFAULT, GlyphCursor.values()[0]);
        assertTrue(GlyphCursor.values().length >= 8);
    }
}
