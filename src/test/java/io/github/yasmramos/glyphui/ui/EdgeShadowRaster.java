package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Assumptions;

/**
 * Shared raster check for the {@link EdgeShadow} fades: renders a scrolling
 * widget on a real Skija surface and hands the pixels to the caller, so each
 * widget can assert its own fold geometry without repeating the surface setup.
 */
final class EdgeShadowRaster {

    private EdgeShadowRaster() {
    }

    /** Receives the rendered surface and its pixels for sampling. */
    @FunctionalInterface
    interface Pixels {
        void check(Surface surface, Pixmap pixmap);
    }

    /**
     * Sum of the colour channels, used to compare two samples for "darker".
     * The edge shadow is a translucent black, so a faded pixel always has a
     * lower sum than the untouched background.
     *
     * @param argb a pixel as returned by {@code Pixmap.getColor}
     * @return the red + green + blue sum, 0..765
     */
    static int luma(int argb) {
        return ((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF);
    }

    /**
     * Renders {@code component} at the origin on a fresh white surface and runs
     * {@code pixels} against the result. Skipped when the Skija native library
     * cannot be loaded on the host.
     *
     * @param component the widget to render (positioned at (0, 0))
     * @param pixels    the pixel checks to run on the rendered frame
     */
    static void render(Component component, Pixels pixels) {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host");
        Surface[] holder = new Surface[1];
        Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            canvas.clear(0xFFFFFFFF);
            component.renderInternal(canvas);
            canvas.flush();
            try (Pixmap pixmap = new Pixmap()) {
                org.junit.jupiter.api.Assertions.assertTrue(holder[0].peekPixels(pixmap),
                        "peekPixels should succeed");
                pixels.check(holder[0], pixmap);
            }
        } finally {
            component.dispose();
            holder[0].close();
        }
    }
}
