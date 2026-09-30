package com.glyphui.graphics;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Typeface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for the {@link Canvas} wrapper, executed against a real in-memory
 * Skia raster surface (no GPU / windowing required). Tests are skipped with an
 * assumption if the Skija native library cannot be loaded on the host.
 */
class CanvasTest {

    private static final int OPAQUE_BLUE = 0xFF0000FF;
    private static final int OPAQUE_RED = 0xFFFF0000;

    @BeforeAll
    static void checkSkijaAvailable() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping raster tests");
    }

    private Surface[] surfaceHolder;
    private Canvas canvas;

    @BeforeEach
    void setUp() {
        surfaceHolder = new Surface[1];
        canvas = RasterCanvasTestFactory.create(surfaceHolder);
    }

    @AfterEach
    void tearDown() {
        if (surfaceHolder[0] != null) {
            surfaceHolder[0].close();
        }
    }

    @Test
    @DisplayName("Constructor stores dimensions and exposes native objects")
    void constructorExposesNativeObjectsAndDimensions() {
        assertNotNull(canvas.getNativeCanvas());
        assertSame(surfaceHolder[0], canvas.getSurface());
        assertEquals(RasterCanvasTestFactory.SIZE, canvas.getWidth());
        assertEquals(RasterCanvasTestFactory.SIZE, canvas.getHeight());
    }

    @Test
    @DisplayName("resize updates width/height without touching native state")
    void resizeUpdatesDimensions() {
        canvas.resize(800, 600);
        assertEquals(800, canvas.getWidth());
        assertEquals(600, canvas.getHeight());

        canvas.resize(0, 0);
        assertEquals(0, canvas.getWidth());
        assertEquals(0, canvas.getHeight());
    }

    @Test
    @DisplayName("clear paints every pixel with the given color")
    void clearFillsSurfaceWithColor() {
        canvas.clear(OPAQUE_RED);
        canvas.flush();
        assertTrue(allPixelsEqual(OPAQUE_RED), "expected entire surface to be red");
    }

    @Test
    @DisplayName("drawRect fills the requested rectangle only")
    void drawRectFillsRequestedArea() {
        canvas.clear(OPAQUE_BLUE);
        try (Paint paint = new Paint()) {
            paint.setColor(OPAQUE_RED);
            canvas.drawRect(10, 10, 50, 50, paint);
        }
        canvas.flush();

        assertEquals(OPAQUE_RED, pixelAt(30, 30), "inside the rect should be red");
        assertEquals(OPAQUE_BLUE, pixelAt(5, 5), "outside the rect should stay blue");
    }

    @Test
    @DisplayName("drawRRect renders rounded rectangle covering its center")
    void drawRRectCoversCenter() {
        canvas.clear(OPAQUE_BLUE);
        try (Paint paint = new Paint()) {
            paint.setColor(OPAQUE_RED);
            canvas.drawRRect(10, 10, 100, 100, 20, 20, paint);
        }
        canvas.flush();

        assertEquals(OPAQUE_RED, pixelAt(60, 60));
        assertEquals(OPAQUE_BLUE, pixelAt(0, 0));
    }

    @Test
    @DisplayName("drawCircle renders a filled disc")
    void drawCircleFillsDisc() {
        canvas.clear(OPAQUE_BLUE);
        try (Paint paint = new Paint()) {
            paint.setColor(OPAQUE_RED);
            canvas.drawCircle(100, 100, 40, paint);
        }
        canvas.flush();

        assertEquals(OPAQUE_RED, pixelAt(100, 100), "center of circle should be red");
        assertEquals(OPAQUE_BLUE, pixelAt(10, 10), "far corner should stay blue");
    }

    @Test
    @DisplayName("drawLine paints pixels along the line")
    void drawLinePaintsAlongPath() {
        canvas.clear(OPAQUE_BLUE);
        try (Paint paint = new Paint()) {
            paint.setColor(OPAQUE_RED);
            paint.setStrokeWidth(3.0f);
            canvas.drawLine(0, 100, 200, 100, paint);
        }
        canvas.flush();

        assertEquals(OPAQUE_RED, pixelAt(100, 100), "midpoint of line should be red");
        assertEquals(OPAQUE_BLUE, pixelAt(100, 50), "away from line should stay blue");
    }

    @Test
    @DisplayName("measureText returns positive width proportional to text length")
    void measureTextIsPositiveAndMonotonic() {
        try (Typeface tf = Typeface.makeFromName(null, FontStyle.NORMAL);
             Font font = new Font(tf, 20.0f)) {

            float empty = canvas.measureText("", font);
            float shortText = canvas.measureText("a", font);
            float longText = canvas.measureText("abcdef", font);

            assertEquals(0.0f, empty, 1e-6);
            assertTrue(shortText > 0.0f);
            assertTrue(longText > shortText, "longer text must measure wider");
        }
    }

    @Test
    @DisplayName("getTextHeight returns positive height for a valid font")
    void getTextHeightIsPositive() {
        try (Typeface tf = Typeface.makeFromName(null, FontStyle.NORMAL);
             Font font = new Font(tf, 24.0f)) {

            float h = canvas.getTextHeight(font);
            assertTrue(h > 0.0f, "text height should be positive, was " + h);
        }
    }

    @Test
    @DisplayName("drawString renders glyphs onto the surface")
    void drawStringRendersVisibleGlyphs() {
        canvas.clear(OPAQUE_BLUE);
        try (Typeface tf = Typeface.makeFromName(null, FontStyle.NORMAL);
             Font font = new Font(tf, 48.0f);
             Paint paint = new Paint()) {

            paint.setColor(OPAQUE_RED);
            canvas.drawString("X", 20, 100, paint, font);
        }
        canvas.flush();

        boolean anyRed = false;
        for (int y = 50; y < 110 && !anyRed; y++) {
            for (int x = 20; x < 90; x++) {
                if (pixelAt(x, y) == OPAQUE_RED) {
                    anyRed = true;
                    break;
                }
            }
        }
        assertTrue(anyRed, "drawing text should produce red pixels");
    }

    @Test
    @DisplayName("flush completes without error on a raster surface")
    void flushDoesNotThrow() {
        canvas.clear(OPAQUE_BLUE);
        canvas.flush();
        canvas.flush(); // flushing twice must be safe
    }

    // ---- pixel helpers -----------------------------------------------------

    /**
     * Reads the surface pixels into a {@link io.github.humbleui.skija.Pixmap} and
     * extracts a single color. Skia returns premultiplied values, which for fully
     * opaque colors equal plain ARGB.
     */
    private int pixelAt(int x, int y) {
        try (io.github.humbleui.skija.Pixmap pixmap = new io.github.humbleui.skija.Pixmap()) {
            assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
            int color = pixmap.getColor(x, y);
            return color;
        }
    }

    private boolean allPixelsEqual(int expected) {
        try (io.github.humbleui.skija.Pixmap pixmap = new io.github.humbleui.skija.Pixmap()) {
            if (!surfaceHolder[0].peekPixels(pixmap)) {
                return false;
            }
            int stride = RasterCanvasTestFactory.SIZE;
            for (int y = 0; y < RasterCanvasTestFactory.SIZE; y += 16) {
                for (int x = 0; x < RasterCanvasTestFactory.SIZE; x += 16) {
                    if (pixmap.getColor(x, y) != expected) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
