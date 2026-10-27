package io.github.yasmramos.glyphui.graphics;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontEdging;
import io.github.humbleui.skija.FontHinting;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Surface;
import io.github.yasmramos.glyphui.SkijaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Priority 1 visual quality ("no jagged edges"):
 * global paint-level antialiasing defaults on the {@link Canvas} wrapper,
 * font rendering quality settings (subpixel edging + slight hinting) and
 * the MSAA sample-count resolution used by the GL backend factory.
 */
class VisualQualityTest {

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
    // 1. Global antialiasing on geometric draws
    // ------------------------------------------------------------------

    @Test
    void withQualityDefaultsEnablesAntiAliasOnPaint() {
        try (Paint paint = new Paint()) {
            // Deliberately clear the flag: on some Skia builds a fresh Paint
            // already reports AA enabled, so the helper's guarantee must be
            // verified from an explicitly non-AA starting state.
            paint.setAntiAlias(false);
            Paint returned = Canvas.withQualityDefaults(paint);
            assertSame(paint, returned, "helper should return the same instance");
            assertTrue(paint.isAntiAlias(), "quality default must enable AA");
        }
    }

    @Test
    void drawMethodsDoNotDisableExplicitAntiAlias() {
        Canvas canvas = createCanvas();
        try (Paint paint = new Paint()) {
            paint.setAntiAlias(true);
            canvas.drawCircle(32, 32, 10, paint);
            assertTrue(paint.isAntiAlias(),
                    "the quality helper must never turn AA off");
        }
    }

    @Test
    void drawRectForcesAntiAliasEvenWithBarePaint() {
        Canvas canvas = createCanvas();
        try (Paint paint = new Paint()) {
            canvas.drawRect(10, 10, 50, 50, paint);
            assertTrue(paint.isAntiAlias(),
                    "drawRect must apply the global AA default to the caller's paint");
        }
    }

    @Test
    void drawRRectForcesAntiAliasEvenWithBarePaint() {
        Canvas canvas = createCanvas();
        try (Paint paint = new Paint()) {
            canvas.drawRRect(4, 4, 60, 30, 8, 8, paint);
            assertTrue(paint.isAntiAlias(),
                    "rounded rectangles are the classic staircase case; AA must be forced");
        }
    }

    @Test
    void drawCircleForcesAntiAliasEvenWithBarePaint() {
        Canvas canvas = createCanvas();
        try (Paint paint = new Paint()) {
            canvas.drawCircle(64, 64, 20, paint);
            assertTrue(paint.isAntiAlias(),
                    "circles must never render with jagged edges");
        }
    }

    @Test
    void drawLineForcesAntiAliasEvenWithBarePaint() {
        Canvas canvas = createCanvas();
        try (Paint paint = new Paint()) {
            canvas.drawLine(0, 0, 100, 73, paint);
            assertTrue(paint.isAntiAlias(),
                    "diagonal lines must be smoothed by the global default");
        }
    }

    // ------------------------------------------------------------------
    // 2. Font rendering quality
    // ------------------------------------------------------------------

    @Test
    void createDefaultFontUsesSubpixelEdgingAndSlightHinting() {
        try (Font font = Fonts.createDefaultFont(14f)) {
            assertEquals(FontEdging.SUBPIXEL_ANTI_ALIAS, font.getEdging(),
                    "text must be rendered with subpixel antialiasing by default");
            assertEquals(FontHinting.SLIGHT, font.getHinting(),
                    "slight hinting balances glyph shape fidelity and legibility");
        }
    }

    @Test
    void applyQualityDefaultsConfiguresAnyFontAndChains() {
        try (Font font = new Font(Fonts.getDefaultTypeface(), 12f)) {
            Font returned = Fonts.applyQualityDefaults(font);
            assertSame(font, returned, "helper should return the same font instance");
            assertEquals(FontEdging.SUBPIXEL_ANTI_ALIAS, font.getEdging());
            assertEquals(FontHinting.SLIGHT, font.getHinting());
        }
    }

    @Test
    void fontManagerRoleFontsCarryQualityDefaults() {
        Font body = FontManager.getFont(Theme.FontRole.BODY);
        assertNotNull(body, "role fonts must resolve");
        assertEquals(FontEdging.SUBPIXEL_ANTI_ALIAS, body.getEdging(),
                "cached role fonts must go through the same quality pipeline");
        assertEquals(FontHinting.SLIGHT, body.getHinting());
        // Cached instance: second lookup returns the identical object.
        assertSame(body, FontManager.getFont(Theme.FontRole.BODY));
    }

    // ------------------------------------------------------------------
    // 3. MSAA sample resolution in the GL backend
    // ------------------------------------------------------------------

    @Test
    void msaaResolutionAcceptsOnlyValidSampleCounts() throws Exception {
        java.lang.reflect.Method m = Class.forName(
                        "io.github.yasmramos.glyphui.core.backend.GlSurfaceFactory")
                .getDeclaredMethod("resolveMsaaSamples");
        m.setAccessible(true);

        String key = "glyph.gl.samples";
        String original = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertEquals(4, m.invoke(null), "default must be MSAA x4");

            System.setProperty(key, "0");
            assertEquals(0, m.invoke(null), "0 disables hardware AA as documented");

            System.setProperty(key, "2");
            assertEquals(2, m.invoke(null));

            System.setProperty(key, "8");
            assertEquals(8, m.invoke(null));

            System.setProperty(key, "3");
            assertEquals(4, m.invoke(null), "invalid counts fall back to the default");

            System.setProperty(key, "-1");
            assertEquals(4, m.invoke(null), "negative counts fall back to the default");

            System.setProperty(key, "abc");
            assertEquals(4, m.invoke(null), "non-numeric values fall back to the default");
        } finally {
            if (original == null) {
                System.clearProperty(key);
            } else {
                System.setProperty(key, original);
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Canvas createCanvas() {
        surface = Surface.makeRasterN32Premul(128, 128);
        return new Canvas(surface.getCanvas(), surface, 128, 128);
    }
}
