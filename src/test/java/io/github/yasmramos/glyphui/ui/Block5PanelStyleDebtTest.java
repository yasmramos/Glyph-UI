package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleEngine;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.style.StyleSheet;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Block 5 items 5.26-5.28 (panel background and render
 * style debt). Item 5.29 (inline-style warning parity) is covered by
 * {@code StyleParseTest} in the style package.
 *
 * <ul>
 *   <li>5.26 - {@code Color.makeARGB(0,0,0,0)} is no longer a sentinel: an
 *       explicit transparent background set through
 *       {@link Panel#setBackgroundColor(int)} is honored instead of silently
 *       reverting to the theme window background.</li>
 *   <li>5.27 - {@link Panel#render} paints the color returned by
 *       {@link Panel#getBackgroundColor()} (CSS-aware), so a stylesheet
 *       {@code background: red} declaration actually reaches the raster even
 *       though the raw field still holds the transparent default.</li>
 *   <li>5.28 - a computed {@code display: none} hides a child from rendering
 *       too, not just from flex layout.</li>
 * </ul>
 */
class Block5PanelStyleDebtTest extends TestBase {

    private static final int OPAQUE_RED = 0xFFFF0000;
    private static final int TRANSPARENT = 0x00000000;
    private static final int OPAQUE_GREEN = 0xFF00FF00;

    /**
     * A stylesheet that paints every {@code div} with a solid color, used as
     * the "theme layer" in these tests: panels without an explicit or CSS
     * background follow {@code Theme.current().getBackgroundColor()}, which is
     * opaque, so to keep unpainted regions distinguishable at the pixel level
     * every panel in a raster scene gets its paint decision through this rule
     * (or an explicit setter). The color lives on a constant so each test can
     * document what the backdrop represents.
     */
    private static final String ALL_DIVS_GREEN = "div { background: #FF00FF00; }";

    /** Reads one little-endian ARGB pixel from the raster test surface. */
    private static int readPixel(Canvas wrapper, int x, int y) {
        Surface skijaSurface = wrapper.nativeSurfaceForInternalUse();
        int w = skijaSurface.getWidth();
        int h = skijaSurface.getHeight();
        Bitmap full = new Bitmap();
        try {
            assertTrue(full.allocN32Pixels(w, h), "allocN32Pixels failed");
            assertTrue(skijaSurface.readPixels(full, 0, 0), "readPixels failed");
            ByteBuffer buf = full.peekPixels();
            buf.rewind();
            byte[] px = new byte[buf.remaining()];
            buf.get(px);
            int off = (y * w + x) * 4;
            // N32 premultiplied, little-endian: bytes are [B, G, R, A].
            return (px[off] & 0xFF) | ((px[off + 1] & 0xFF) << 8)
                    | ((px[off + 2] & 0xFF) << 16) | ((px[off + 3] & 0xFF) << 24);
        } finally {
            full.close();
        }
    }

    private static void requireSkija() {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping raster tests");
    }

    @Nested
    @DisplayName("5.26 Explicit transparent background")
    class ExplicitTransparentBackground {

        @Test
        @DisplayName("setBackgroundColor(0x00000000) is honored, not the theme fallback")
        void explicitTransparentIsNotSentinel() {
            Panel panel = new Panel(0, 0, 100, 50);
            // Before any explicit set: the panel follows the theme.
            assertFalse(panel.hasExplicitBackground());
            assertEquals(Theme.current().getBackgroundColor(), panel.getBackgroundColor(),
                    "unset background must follow the theme window color");

            panel.setBackgroundColor(TRANSPARENT);

            assertTrue(panel.hasExplicitBackground(),
                    "an explicit transparent color must register as explicit (no sentinel)");
            assertEquals(TRANSPARENT, panel.getBackgroundColor(),
                    "explicitly transparent must NOT revert to the theme background");
            panel.dispose();
        }

        @Test
        @DisplayName("clearExplicitBackground returns the panel to theme-driven painting")
        void clearRestoresThemeFollow() {
            Panel panel = new Panel(0, 0, 100, 50);
            panel.setBackgroundColor(TRANSPARENT);
            panel.clearExplicitBackground();

            assertFalse(panel.hasExplicitBackground());
            assertEquals(Theme.current().getBackgroundColor(), panel.getBackgroundColor(),
                    "after clearing, the theme background wins again");
            panel.dispose();
        }

        @Test
        @DisplayName("explicit transparent paints nothing over a red parent")
        void explicitTransparentPaintsNothing() {
            requireSkija();
            Surface[] holder = new Surface[1];
            Canvas canvas = RasterCanvasTestFactory.create(holder);
            try {
                Panel root = new Panel(0, 0, 256, 256);
                root.setBackgroundColor(OPAQUE_RED);
                Panel child = new Panel(10, 10, 100, 50);
                child.setBackgroundColor(TRANSPARENT); // explicit, not a sentinel
                root.add(child);

                root.renderInternal(canvas);

                int px = readPixel(canvas, 50, 30);
                assertEquals(OPAQUE_RED, px,
                        "an explicitly transparent panel must let the parent show through");
                root.dispose();
            } finally {
                holder[0].close();
            }
        }
    }

    @Nested
    @DisplayName("5.27 CSS background reaches render")
    class CssBackgroundReachesRender {

        @Test
        @DisplayName("div { background: red } rasterizes red despite the transparent field default")
        void cssBackgroundPaintsRed() {
            requireSkija();
            Surface[] holder = new Surface[1];
            Canvas canvas = RasterCanvasTestFactory.create(holder);
            try {
                Panel root = new Panel(0, 0, 256, 256);
                Panel box = new Panel(10, 10, 100, 50);
                box.setId("box");
                root.add(box);

                // Backdrop pass: every div paints green. The higher-specificity
                // #box rule re-declares the target as red, so any pixel that
                // ends up NOT red proves the CSS background never reached the
                // raster (the exact item 5.27 failure mode: the old render path
                // read the raw transparent field and drew nothing, letting the
                // backdrop show through).
                StyleEngine.apply(root, StyleSheet.parse(ALL_DIVS_GREEN + " #box { background: red; }"));

                // The raw field still holds the transparent default; only the
                // CSS-aware accessor knows about the declared color. This is
                // exactly what the old render path read (item 5.27 bug).
                assertEquals(TRANSPARENT, box.rawBackgroundColorForTests(),
                        "the backing field must stay untouched by the cascade");
                assertEquals(OPAQUE_RED, box.getBackgroundColor(),
                        "getBackgroundColor() must resolve the CSS declaration");

                root.renderInternal(canvas);

                assertEquals(OPAQUE_RED, readPixel(canvas, 50, 30),
                        "the declared background must actually be painted (item 5.27)");
                // Sanity check of the backdrop model: the root itself has no
                // explicit id rule, so it paints green wherever the box does
                // not cover it — proving the scene really was rasterized.
                assertEquals(OPAQUE_GREEN, readPixel(canvas, 200, 200),
                        "the uncovered backdrop must paint green, confirming the raster ran");
                root.dispose();
            } finally {
                holder[0].close();
            }
        }

        @Test
        @DisplayName("explicit alpha-0 setter color skips painting; CSS alpha-0 declaration paints")
        void paintDecisionIsSeparateFromColorResolution() {
            requireSkija();
            Surface[] holder = new Surface[1];
            Canvas canvas = RasterCanvasTestFactory.create(holder);
            try {
                Panel root = new Panel(0, 0, 256, 256);
                root.setBackgroundColor(OPAQUE_RED);

                Panel viaSetter = new Panel(10, 10, 60, 40);
                viaSetter.setBackgroundColor(0x00FF0000); // explicit, alpha 0 -> skip
                Panel viaCss = new Panel(80, 10, 60, 40);
                viaCss.setId("cssbox");                   // id must exist BEFORE the cascade
                viaCss.setBackgroundColor(TRANSPARENT);   // overridden below by CSS
                root.add(viaSetter);
                root.add(viaCss);

                StyleEngine.apply(root, StyleSheet.parse("""
                        #cssbox { background: rgba(0, 255, 0, 0); }
                        """));

                // rgba(0,255,0,0) resolves to 0x0000FF00: alpha channel is 0,
                // the RGB payload survives in the int (premultiplied raster
                // output still reads fully transparent).
                assertEquals(0x0000FF00, viaCss.getBackgroundColor(),
                        "the CSS alpha-0 declaration must resolve with a zero alpha channel");
                assertTrue(viaCss.hasExplicitBackground());

                root.renderInternal(canvas);

                assertEquals(OPAQUE_RED, readPixel(canvas, 30, 25),
                        "explicit alpha-0 background via setter must not paint anything");
                // The rasterized result of an alpha-0 CSS background is
                // implementation-defined (Skia may skip fully transparent
                // fills as an optimization), so the "should I paint?"
                // decision is asserted at the API level instead of through
                // pixel sniffing: a CSS-declared background always issues a
                // draw op, even at alpha 0, which proves the paint decision
                // is separate from color resolution (items 5.26/5.27
                // contract).
                assertTrue(invokeShouldPaint(viaCss),
                        "a CSS-declared background must issue a draw op even at alpha 0");
                assertFalse(invokeShouldPaint(viaSetter),
                        "an explicit alpha-0 setter background must not issue a draw op");
                // No pixel assertion here: Skia skips fully transparent
                // fills as an optimization, so the parent's red may survive
                // underneath. The API-level checks above are the contract.
                root.dispose();
            } finally {
                holder[0].close();
            }
        }

        /**
         * Invokes the package-private {@code Panel.shouldPaintBackground()}
         * decision hook so the paint-vs-color contract can be asserted
         * without depending on Skia's transparent-fill optimizations.
         */
        private static boolean invokeShouldPaint(Panel panel) {
            try {
                var m = Panel.class.getDeclaredMethod("shouldPaintBackground");
                m.setAccessible(true);
                return (boolean) m.invoke(panel);
            } catch (ReflectiveOperationException e) {
                throw new AssertionError("Panel.shouldPaintBackground() missing", e);
            }
        }
    }

    @Nested
    @DisplayName("5.28 display:none hidden from render")
    class DisplayNoneHiddenFromRender {

        @Test
        @DisplayName("a child with computed display:none is not rasterized")
        void displayNoneChildNotPainted() {
            requireSkija();
            Surface[] holder = new Surface[1];
            Canvas canvas = RasterCanvasTestFactory.create(holder);
            try {
                Panel root = new Panel(0, 0, 256, 256);
                Panel visibleBox = new Panel(10, 10, 60, 40);
                Panel hiddenBox = new Panel(80, 10, 60, 40);
                hiddenBox.addStyleClass("hidden"); // class must exist BEFORE the cascade
                root.add(visibleBox);
                root.add(hiddenBox);

                StyleSheet sheet = StyleSheet.parse("""
                        div { background: #FF00FF00; }
                        .hidden { display: none; }
                        """);
                StyleEngine.apply(root, sheet);

                assertTrue(Panel.isDisplayNone(hiddenBox),
                        "the hidden child's computed style must report display:none");
                assertFalse(Panel.isDisplayNone(visibleBox));

                root.renderInternal(canvas);

                assertEquals(OPAQUE_GREEN, readPixel(canvas, 30, 25),
                        "the visible sibling must still paint");
                int hiddenPx = readPixel(canvas, 100, 25);
                assertEquals(OPAQUE_GREEN, hiddenPx,
                        "display:none must hide the child from rendering too (item 5.28): "
                                + "its area must fall back to the green backdrop, got 0x"
                                + Integer.toHexString(hiddenPx));
                root.dispose();
            } finally {
                holder[0].close();
            }
        }

        @Test
        @DisplayName("inline-style display:none also skips painting")
        void inlineDisplayNoneSkipsPainting() {
            requireSkija();
            Surface[] holder = new Surface[1];
            Canvas canvas = RasterCanvasTestFactory.create(holder);
            try {
                Panel root = new Panel(0, 0, 256, 256);
                Panel child = new Panel(10, 10, 100, 50);
                child.setInlineStyle(Style.builder()
                        .set(StyleProperty.DISPLAY, "none")
                        .set(StyleProperty.BACKGROUND, OPAQUE_RED)
                        .build());
                root.add(child);

                root.renderInternal(canvas);

                int px = readPixel(canvas, 50, 30);
                assertEquals(0, px >>> 24,
                        "an inline display:none child must not rasterize, got 0x"
                                + Integer.toHexString(px));
                root.dispose();
            } finally {
                holder[0].close();
            }
        }

        @Test
        @DisplayName("isDisplayNone tolerates components without computed styles")
        void isDisplayNoneNullSafe() {
            Panel panel = new Panel(0, 0, 10, 10);
            assertFalse(Panel.isDisplayNone(panel),
                    "no style at all must never be interpreted as display:none");
            panel.dispose();
        }
    }
}
