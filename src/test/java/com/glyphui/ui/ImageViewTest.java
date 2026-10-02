package com.glyphui.ui;

import com.glyphui.graphics.Dimension;
import com.glyphui.graphics.Image;
import com.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link ImageView}: image reference handling, measurement
 * from pixel dimensions with constraint clamping, non-interactive event
 * behavior and rendering (including the null-image no-op path).
 */
public class ImageViewTest {

    @BeforeAll
    static void requireSkija() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping image view tests");
    }

    private static Image tinyImage(int w, int h) throws IOException {
        java.awt.image.BufferedImage awt =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(awt, "png", out);
        return Image.loadBytes(out.toByteArray());
    }

    @Test
    public void emptyViewHoldsNullImageAndMeasuresZero() {
        ImageView view = new ImageView();
        assertNull(view.getImage());
        assertFalse(view.isFocusable(), "images are decorative and never take focus");
        assertEquals(AccessibleRole.IMAGE, view.getAccessibleRole());

        Dimension d = view.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        assertEquals(0.0f, d.getWidth(), 1e-6);
        assertEquals(0.0f, d.getHeight(), 1e-6);
    }

    @Test
    public void setImageRoundTripAndMeasureUsesPixelSize() throws IOException {
        ImageView view = new ImageView();
        try (Image img = tinyImage(30, 20)) {
            view.setImage(img);
            assertSame(img, view.getImage());

            Dimension natural = view.measure(1000, 1000);
            assertEquals(30.0f, natural.getWidth(), 1e-6);
            assertEquals(20.0f, natural.getHeight(), 1e-6);

            // Constraints clamp down but never below zero.
            Dimension clamped = view.measure(10, 5);
            assertEquals(10.0f, clamped.getWidth(), 1e-6);
            assertEquals(5.0f, clamped.getHeight(), 1e-6);

            Dimension negative = view.measure(-4, -9);
            assertEquals(0.0f, negative.getWidth(), 1e-6);
            assertEquals(0.0f, negative.getHeight(), 1e-6);

            // NaN constraints behave like unbounded ones.
            Dimension nan = view.measure(Float.NaN, Float.POSITIVE_INFINITY);
            assertEquals(30.0f, nan.getWidth(), 1e-6);
        }
    }

    @Test
    public void closedImageReportsNotLoaded() throws IOException {
        Image img = tinyImage(8, 8);
        ImageView view = new ImageView(img);
        img.close();
        Dimension d = view.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        assertEquals(0.0f, d.getWidth(), 1e-6);
        assertEquals(0.0f, d.getHeight(), 1e-6);
    }

    @Test
    public void eventsAreNeverConsumed() {
        ImageView view = new ImageView();
        assertFalse(view.onMouseEvent(new com.glyphui.events.MouseEvent(
                com.glyphui.events.MouseEventType.PRESS, 1, 1,
                com.glyphui.events.MouseButton.LEFT, 1)));
        view.onKeyEvent(new com.glyphui.events.KeyEvent(
                com.glyphui.events.KeyEventType.PRESS, 65, 'a',
                java.util.EnumSet.noneOf(com.glyphui.events.KeyModifier.class)));
    }

    @Test
    public void renderIsNoOpWithoutImageAndDrawsWithImage() throws IOException {
        Surface[] holder = new Surface[1];
        com.glyphui.graphics.Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            ImageView view = new ImageView();
            view.render(canvas); // null image branch must not throw

            try (Image img = tinyImage(16, 12)) {
                view.setImage(img);
                view.setWidth(16);
                view.setHeight(12);
                view.render(canvas); // drawImageRect branch
            }
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }
}
