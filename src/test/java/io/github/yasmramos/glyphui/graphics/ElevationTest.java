package io.github.yasmramos.glyphui.graphics;

import io.github.humbleui.skija.Surface;
import io.github.yasmramos.glyphui.SkijaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the shared elevation (drop-shadow) language: raised
 * surfaces must paint a soft shadow below them, the levels must get stronger
 * with depth, and {@link Canvas.Elevation#NONE} must draw nothing.
 */
class ElevationTest {

    private Surface surface;

    @BeforeEach
    void requireSkija() {
        SkijaTestSupport.assumeAvailable();
    }

    @AfterEach
    void closeSurface() {
        if (surface != null && !surface.isClosed()) {
            surface.close();
        }
    }

    // ------------------------------------------------------------------
    // Geometry of the levels
    // ------------------------------------------------------------------

    @Test
    void levelsIncreaseInBlurAndOffset() {
        assertEquals(0.0f, Canvas.Elevation.NONE.getSigma());
        assertTrue(Canvas.Elevation.LOW.getSigma() < Canvas.Elevation.MEDIUM.getSigma(),
                "medium must be softer than low");
        assertTrue(Canvas.Elevation.MEDIUM.getSigma() < Canvas.Elevation.HIGH.getSigma(),
                "high must be the softest level");
        for (Canvas.Elevation e : Canvas.Elevation.values()) {
            assertTrue(e.getOffsetY() >= 0.0f, "shadows fall downward, never up");
        }
    }

    // ------------------------------------------------------------------
    // Raster behaviour
    // ------------------------------------------------------------------

    @Test
    void noneDrawsNothing() {
        Canvas canvas = createCanvas();
        canvas.drawShadow(20, 20, 60, 40, 8, Canvas.Elevation.NONE);
        assertEquals(0, alphaBelow(canvas),
                "NONE must leave the page untouched");
    }

    @Test
    void nullElevationDrawsNothing() {
        Canvas canvas = createCanvas();
        canvas.drawShadow(20, 20, 60, 40, 8, null);
        assertEquals(0, alphaBelow(canvas),
                "a null elevation must be a safe no-op");
    }

    @Test
    void lowElevationPaintsAShadowBelowTheSurface() {
        Canvas canvas = createCanvas();
        canvas.drawShadow(20, 20, 60, 40, 8, Canvas.Elevation.LOW);
        assertTrue(alphaBelow(canvas) > 0,
                "LOW must darken the page just below the surface");
    }

    @Test
    void shadowStaysCloseToTheSurface() {
        Canvas canvas = createCanvas();
        canvas.drawShadow(20, 20, 60, 40, 8, Canvas.Elevation.LOW);
        assertEquals(0, alphaAt(canvas, 110, 110),
                "a shadow must not bleed across the whole page");
    }

    @Test
    void higherElevationCastsAStrongerAndWiderShadow() {
        Canvas low = createCanvas();
        low.drawShadow(20, 20, 60, 40, 8, Canvas.Elevation.LOW);
        int lowAlpha = alphaBelow(low);

        Canvas high = createCanvas();
        high.drawShadow(20, 20, 60, 40, 8, Canvas.Elevation.HIGH);
        int highAlpha = alphaBelow(high);

        assertTrue(highAlpha > lowAlpha,
                "HIGH must read darker than LOW at the same probe point");
    }

    @Test
    void emptyBoundsAreIgnored() {
        Canvas canvas = createCanvas();
        canvas.drawShadow(20, 20, 0, 0, 8, Canvas.Elevation.HIGH);
        assertEquals(0, alphaBelow(canvas),
                "a degenerate surface must not cast a shadow");
    }

    @Test
    void shadowColourComesFromTheTheme() {
        // A theme with an opaque red shadow must tint the shadow red, proving
        // the colour is theme-driven rather than hard-coded.
        Theme original = Theme.current();
        Theme red = original.toBuilder().shadowColor(0xFFFF0000).build();
        Theme.setCurrent(red);
        try {
            Canvas canvas = createCanvas();
            canvas.drawShadow(20, 20, 60, 40, 8, Canvas.Elevation.MEDIUM);
            int argb = pixelBelow(canvas);
            int red0 = (argb >> 16) & 0xFF;
            int blue0 = argb & 0xFF;
            assertTrue(red0 > blue0,
                    "the shadow must take its colour from Theme.getShadowColor()");
            assertFalse(isBlank(canvas), "an opaque shadow must be visible");
        } finally {
            Theme.setCurrent(original);
        }
    }

    @Test
    void builtInThemesShipATranslucentShadow() {
        for (Theme t : new Theme[]{Theme.light(), Theme.dark()}) {
            int alpha = (t.getShadowColor() >>> 24) & 0xFF;
            assertTrue(alpha > 0 && alpha < 255,
                    "the shadow token must be translucent so one value works "
                            + "over any page background");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A shadow probe just below the bottom edge of the (20,20,60,40) rect. */
    private static int alphaBelow(Canvas canvas) {
        return alphaAt(canvas, 50, 63);
    }

    private static int pixelBelow(Canvas canvas) {
        return pixelAt(canvas, 50, 63);
    }

    private static boolean isBlank(Canvas canvas) {
        for (int y = 58; y < 72; y++) {
            for (int x = 20; x < 80; x++) {
                if (alphaAt(canvas, x, y) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int alphaAt(Canvas canvas, int x, int y) {
        return (pixelAt(canvas, x, y) >>> 24) & 0xFF;
    }

    private static int pixelAt(Canvas canvas, int x, int y) {
        try (io.github.humbleui.skija.Pixmap pixmap = new io.github.humbleui.skija.Pixmap()) {
            assertTrue(canvas.nativeSurfaceForInternalUse().peekPixels(pixmap),
                    "peekPixels should succeed");
            return pixmap.getColor(x, y);
        }
    }

    private Canvas createCanvas() {
        surface = Surface.makeRasterN32Premul(128, 128);
        return new Canvas(surface.getCanvas(), surface, 128, 128);
    }
}
