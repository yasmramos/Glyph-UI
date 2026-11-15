package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Shared raster check for the overlay scrollbar: renders a widget on a real
 * Skija surface and asserts the bar leaves ink on the trailing edge, where the
 * widget paints nothing else.
 */
final class ScrollbarRaster {

    private ScrollbarRaster() {
    }

    /**
     * @param component a component positioned at (0, 0) whose content overflows
     * @param barLeft   the left x of the bar strip to check
     */
    static void assertBarPaintsOnTrailingEdge(Component component, int barLeft) {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host");
        Surface[] holder = new Surface[1];
        Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            canvas.clear(0xFFFFFFFF);
            component.renderInternal(canvas);
            canvas.flush();

            try (Pixmap pixmap = new Pixmap()) {
                assertTrue(holder[0].peekPixels(pixmap), "peekPixels should succeed");
                // The widgets fill their whole width with the background, so
                // compare the bar strip against a content column at the same
                // rows: the bar tints its strip, the content stays uniform.
                int barX = barLeft + 3;
                int contentX = barLeft - 8;
                boolean tinted = false;
                for (int py = 4; py < RasterCanvasTestFactory.SIZE - 4 && !tinted; py++) {
                    if (pixmap.getColor(barX, py) != pixmap.getColor(contentX, py)) {
                        tinted = true;
                    }
                }
                assertTrue(tinted, "the overlay bar must paint on the trailing edge");
            }
        } finally {
            component.dispose();
            holder[0].close();
        }
    }
}
