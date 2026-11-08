package io.github.yasmramos.glyphui.graphics;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.ImageInfo;
import io.github.humbleui.skija.Surface;
import io.github.yasmramos.glyphui.SkijaTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the image-sampling half of the visual-quality defaults:
 * a scaled blit must not fall back to Skia's nearest-neighbour filter (which
 * makes enlarged images blocky and HiDPI-downscaled ones alias).
 *
 * <p>The check is behavioural rather than reflective: a one-pixel-tall gradient
 * is scaled up and the rendered row is read back. Interpolating sampling
 * produces far more distinct colours than the source has pixels; nearest
 * sampling can only ever reproduce the source colours.</p>
 */
class ImageSamplingQualityTest {

    /** Distinct colours in the source gradient. */
    private static final int SOURCE_COLOURS = 4;

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

    @Test
    void defaultScalingSmoothlyInterpolatesWhenUpscaling() throws IOException {
        Canvas canvas = createCanvas(64, 1);
        try (Image gradient = gradientImage()) {
            canvas.drawImage(gradient, 0, 0, 64, 1);
            assertTrue(distinctColoursInRow(canvas, 64) > SOURCE_COLOURS,
                    "the default sampling must blend neighbouring pixels, not "
                            + "replicate them");
        }
    }

    @Test
    void pixelatedScalingKeepsHardEdgesWhenUpscaling() throws IOException {
        Canvas canvas = createCanvas(64, 1);
        try (Image gradient = gradientImage()) {
            canvas.drawImage(gradient, 0, 0, 64, 1, Canvas.ImageScaling.PIXELATED);
            assertTrue(distinctColoursInRow(canvas, 64) <= SOURCE_COLOURS,
                    "nearest sampling must only ever reproduce source colours");
        }
    }

    @Test
    void smoothScalingDiffersFromPixelatedAtTheSameSize() throws IOException {
        Canvas smooth = createCanvas(64, 1);
        Canvas pixelated = createCanvas(64, 1);
        try (Image gradient = gradientImage()) {
            smooth.drawImage(gradient, 0, 0, 64, 1, Canvas.ImageScaling.SMOOTH);
            pixelated.drawImage(gradient, 0, 0, 64, 1, Canvas.ImageScaling.PIXELATED);
            assertTrue(distinctColoursInRow(smooth, 64)
                            > distinctColoursInRow(pixelated, 64),
                    "SMOOTH must interpolate where PIXELATED steps");
        }
    }

    @Test
    void theFourArgumentOverloadUsesTheSmoothDefault() throws IOException {
        Canvas viaDefault = createCanvas(64, 1);
        Canvas viaExplicit = createCanvas(64, 1);
        try (Image gradient = gradientImage()) {
            viaDefault.drawImage(gradient, 0, 0, 64, 1);
            viaExplicit.drawImage(gradient, 0, 0, 64, 1, Canvas.ImageScaling.SMOOTH);
            assertEquals(distinctColoursInRow(viaExplicit, 64),
                    distinctColoursInRow(viaDefault, 64),
                    "the convenience overload must follow the SMOOTH default");
        }
    }

    @Test
    void samplingPropertyResolvesToSmoothUnlessPixelatedIsRequested()
            throws Exception {
        Method m = Canvas.class.getDeclaredMethod("resolveImageScaling");
        m.setAccessible(true);

        String key = "glyph.image.sampling";
        String original = System.getProperty(key);
        try {
            System.clearProperty(key);
            assertSame(Canvas.ImageScaling.SMOOTH, m.invoke(null),
                    "the default must be SMOOTH");

            System.setProperty(key, "pixelated");
            assertSame(Canvas.ImageScaling.PIXELATED, m.invoke(null));

            System.setProperty(key, "NEAREST");
            assertSame(Canvas.ImageScaling.PIXELATED, m.invoke(null),
                    "the property is case-insensitive and accepts 'nearest'");

            System.setProperty(key, "smooth");
            assertSame(Canvas.ImageScaling.SMOOTH, m.invoke(null));

            System.setProperty(key, "nonsense");
            assertSame(Canvas.ImageScaling.SMOOTH, m.invoke(null),
                    "unknown values fall back to the smooth default");
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

    private Canvas createCanvas(int w, int h) {
        surface = Surface.makeRasterN32Premul(w, h);
        return new Canvas(surface.getCanvas(), surface, w, h);
    }

    /** A 4x1 horizontal black-to-white gradient as an opaque PNG. */
    private static Image gradientImage() throws IOException {
        BufferedImage awt = new BufferedImage(SOURCE_COLOURS, 1,
                BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < SOURCE_COLOURS; x++) {
            int level = Math.round(255f * x / (SOURCE_COLOURS - 1));
            awt.setRGB(x, 0, 0xFF000000 | (level << 16) | (level << 8) | level);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(awt, "png", out);
        return Image.loadBytes(out.toByteArray());
    }

    /** Counts distinct RGB values along the rendered row. */
    private static int distinctColoursInRow(Canvas canvas, int width) {
        try (Bitmap bmp = new Bitmap()) {
            bmp.allocPixels(ImageInfo.makeN32Premul(width, 1));
            assertTrue(canvas.nativeSurfaceForInternalUse().readPixels(bmp, 0, 0),
                    "reading the raster surface back must succeed");
            Set<Integer> seen = new HashSet<>();
            for (int x = 0; x < width; x++) {
                seen.add(bmp.getColor(x, 0) & 0x00FFFFFF);
            }
            return seen.size();
        }
    }
}
