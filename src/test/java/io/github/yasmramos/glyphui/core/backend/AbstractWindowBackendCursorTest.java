package io.github.yasmramos.glyphui.core.backend;

import io.github.yasmramos.glyphui.events.GlyphCursor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the cursor plumbing shared by every concrete backend:
 * {@link AbstractWindowBackend#setCursor(GlyphCursor)} normalizes null,
 * de-duplicates repeated shapes and forwards only real changes to the
 * native hook, while the headless default (no live window) stays a safe
 * no-op.
 */
class AbstractWindowBackendCursorTest {

    /**
     * Records every shape that reaches the native hook. Extends the concrete
     * JWM backend so the shared de-duplication in {@link AbstractWindowBackend}
     * is the code under test; the backend stays headless (no native window),
     * so the recorded hook is the only thing that runs.
     */
    private static final class RecordingBackend extends JwmWindowBackend {
        final List<GlyphCursor> applied = new ArrayList<>();

        RecordingBackend() {
            super("recording", 100, 100);
        }

        @Override
        protected boolean hasLiveWindow() {
            return false;
        }

        @Override
        protected void applyNativeCursor(GlyphCursor cursor) {
            applied.add(cursor);
        }
    }

    @Test
    void firstSetIsForwardedToTheNativeHook() {
        RecordingBackend backend = new RecordingBackend();

        backend.setCursor(GlyphCursor.POINTING_HAND);

        assertEquals(List.of(GlyphCursor.POINTING_HAND), backend.applied);
    }

    @Test
    void repeatingTheSameShapeDoesNotReachTheNativeHookTwice() {
        RecordingBackend backend = new RecordingBackend();

        backend.setCursor(GlyphCursor.TEXT);
        backend.setCursor(GlyphCursor.TEXT);
        backend.setCursor(GlyphCursor.TEXT);

        assertEquals(List.of(GlyphCursor.TEXT), backend.applied,
                "an unchanged shape must not be pushed to the native layer");
    }

    @Test
    void aDifferentShapeIsForwarded() {
        RecordingBackend backend = new RecordingBackend();

        backend.setCursor(GlyphCursor.POINTING_HAND);
        backend.setCursor(GlyphCursor.TEXT);

        assertEquals(List.of(GlyphCursor.POINTING_HAND, GlyphCursor.TEXT),
                backend.applied);
    }

    @Test
    void nullIsNormalizedToTheDefaultShape() {
        RecordingBackend backend = new RecordingBackend();
        backend.setCursor(GlyphCursor.TEXT);

        backend.setCursor(null);

        assertEquals(List.of(GlyphCursor.TEXT, GlyphCursor.DEFAULT),
                backend.applied, "null must fall back to the arrow, not crash");
    }

    @Test
    void settingTheDefaultShapeFirstIsANoOp() {
        RecordingBackend backend = new RecordingBackend();

        // DEFAULT is the initial cached shape, so this must not touch native.
        backend.setCursor(GlyphCursor.DEFAULT);

        assertTrue(backend.applied.isEmpty(),
                "the default shape is already applied at construction");
    }
}
