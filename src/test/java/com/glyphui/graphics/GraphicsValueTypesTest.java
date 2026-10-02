package com.glyphui.graphics;

import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for the small graphics value types: {@link Dimension},
 * {@link Image} and {@link Fonts}. Native-dependent paths run against an
 * in-memory raster surface and are skipped when Skija cannot be loaded.
 */
public class GraphicsValueTypesTest {

    // ------------------------------------------------------------------
    // Dimension
    // ------------------------------------------------------------------

    @Nested
    class DimensionTests {

        @Test
        public void constructorClampsNegativeComponents() {
            Dimension d = new Dimension(-5f, -2f);
            assertEquals(0.0f, d.getWidth(), 1e-6);
            assertEquals(0.0f, d.getHeight(), 1e-6);
        }

        @Test
        public void factoryAndAccessors() {
            Dimension d = Dimension.of(10f, 20f);
            assertEquals(10f, d.width, 1e-6);
            assertEquals(20f, d.height, 1e-6);
            assertEquals(10f, d.getWidth(), 1e-6);
            assertEquals(20f, d.getHeight(), 1e-6);
        }

        @Test
        public void equalsHashCodeAndToStringContract() {
            Dimension a = Dimension.of(3f, 4f);
            Dimension b = Dimension.of(3f, 4f);
            Dimension c = Dimension.of(3f, 5f);

            assertEquals(a, a);
            assertEquals(a, b);
            assertEquals(a.hashCode(), b.hashCode());
            assertFalse(a.equals(c));
            assertFalse(a.equals(null));
            assertFalse(a.equals("not a dimension"));
            assertTrue(a.toString().contains("3.0"), a.toString());
            assertTrue(a.toString().startsWith("Dimension("), a.toString());
        }
    }

    // ------------------------------------------------------------------
    // Image wrapper (native-dependent)
    // ------------------------------------------------------------------

    @Nested
    class ImageTests {

        @BeforeAll
        static void requireSkija() {
            assumeTrue(RasterCanvasTestFactory.isAvailable(),
                    "Skija native library not loadable on this host; skipping image tests");
        }

        private static byte[] pngBytes(int w, int h) throws IOException {
            BufferedImage awt = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(awt, "png", out);
            return out.toByteArray();
        }

        @Test
        public void emptyWrapperReportsNoImage() {
            Image img = new Image(null);
            assertFalse(img.isLoaded());
            assertEquals(0, img.getWidth());
            assertEquals(0, img.getHeight());
            assertNull(img.getNativeImage());
            assertThrows(IllegalStateException.class, img::encodeToPng);
            img.close(); // idempotent on null native
            img.close();
        }

        @Test
        public void loadBytesRoundTripAndClose() throws IOException {
            byte[] png = pngBytes(4, 3);
            Image img = Image.loadBytes(png);
            assertTrue(img.isLoaded());
            assertEquals(4, img.getWidth());
            assertEquals(3, img.getHeight());
            assertNotNull(img.getNativeImage());

            byte[] reencoded = img.encodeToPng();
            assertTrue(reencoded.length > 0);

            img.close();
            assertFalse(img.isLoaded());
            assertEquals(0, img.getWidth());
            assertNull(img.getNativeImage());
            img.close(); // second close is safe
            assertThrows(IllegalStateException.class, img::encodeToPng);
        }

        @Test
        public void missingResourceThrowsIOException() {
            assertThrows(IOException.class, () -> Image.loadResource("/no/such/image.png"));
        }

        @Test
        public void missingFileThrowsIOException() {
            assertThrows(IOException.class, () -> Image.load("/definitely/not/here.png"));
        }
    }

    // ------------------------------------------------------------------
    // Fonts shared typeface cache (native-dependent)
    // ------------------------------------------------------------------

    @Nested
    class FontsTests {

        @BeforeAll
        static void requireSkija() {
            assumeTrue(RasterCanvasTestFactory.isAvailable(),
                    "Skija native library not loadable on this host; skipping font tests");
        }

        @Test
        public void defaultTypefaceIsCachedAndRecreatedAfterClose() {
            io.github.humbleui.skija.Typeface first = Fonts.getDefaultTypeface();
            io.github.humbleui.skija.Typeface again = Fonts.getDefaultTypeface();
            assertSame(first, again, "typeface must be shared across calls");

            Fonts.close();
            Fonts.close(); // idempotent

            io.github.humbleui.skija.Typeface recreated = Fonts.getDefaultTypeface();
            assertNotNull(recreated);
            assertFalse(recreated.isClosed());

            try (io.github.humbleui.skija.Font font = Fonts.createDefaultFont(16f)) {
                assertTrue(font.getSize() > 0);
            }
        }
    }

    // Sanity: the raster factory itself still works (keeps helper covered).
    @Test
    public void rasterSurfaceCreationSucceedsWhenAvailable() {
        assumeTrue(RasterCanvasTestFactory.isAvailable());
        Surface[] holder = new Surface[1];
        Canvas canvas = RasterCanvasTestFactory.create(holder);
        assertNotNull(canvas);
        holder[0].close();
    }
}
