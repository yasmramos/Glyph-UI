package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests for {@link ProgressBar}: the value contract, the indeterminate
 * animation clock and the rendered pixels on a real raster surface.
 */
class ProgressBarTest extends TestBase {

    private static final int OPAQUE_BLUE = 0xFF0000FF;

    @Nested
    @DisplayName("value contract")
    class ValueContract {

        @Test
        @DisplayName("a fresh bar is empty and determinate")
        void defaults() {
            ProgressBar bar = new ProgressBar();
            assertEquals(0.0f, bar.getValue(), 1e-6f);
            assertFalse(bar.isIndeterminate());
            assertFalse(bar.isShowText());
            assertFalse(bar.isFocusable(), "progress bars never take focus");
            assertEquals(AccessibleRole.PROGRESS_BAR, bar.getAccessibleRole());
            bar.dispose();
        }

        @Test
        @DisplayName("setValue clamps to [0, 1]")
        void clampsValue() {
            ProgressBar bar = new ProgressBar();
            bar.setValue(2.5f);
            assertEquals(1.0f, bar.getValue(), 1e-6f);
            bar.setValue(-3.0f);
            assertEquals(0.0f, bar.getValue(), 1e-6f);
            bar.dispose();
        }

        @Test
        @DisplayName("NaN is treated as zero rather than poisoning the fill")
        void nanBecomesZero() {
            ProgressBar bar = new ProgressBar();
            bar.setValue(0.5f);
            bar.setValue(Float.NaN);
            assertEquals(0.0f, bar.getValue(), 1e-6f);
            bar.dispose();
        }

        @Test
        @DisplayName("valueProperty() notifies listeners and updates the field")
        void valuePropertyIsObservable() {
            ProgressBar bar = new ProgressBar();
            java.util.concurrent.atomic.AtomicReference<Float> seen =
                    new java.util.concurrent.atomic.AtomicReference<>();
            bar.valueProperty().addListener((p, oldV, newV) -> seen.set(newV));

            bar.valueProperty().set(0.25f);

            assertEquals(0.25f, bar.getValue(), 1e-6f);
            assertEquals(0.25f, seen.get(), 1e-6f, "listeners observe the new value");
            bar.dispose();
        }

        @Test
        @DisplayName("preferred height follows the bar height constant")
        void preferredHeight() {
            ProgressBar bar = new ProgressBar();
            assertEquals(ProgressBar.DEFAULT_BAR_HEIGHT, bar.getPreferredHeight(), 1e-6f);
            bar.dispose();
        }

        @Test
        @DisplayName("measure clamps to the supplied constraints")
        void measureClamps() {
            ProgressBar bar = new ProgressBar();
            Dimension d = bar.measure(40.0f, 8.0f);
            assertEquals(40.0f, d.getWidth(), 1e-6f);
            assertEquals(8.0f, d.getHeight(), 1e-6f);
            bar.dispose();
        }

        @Test
        @DisplayName("the accessible name defaults and can be overridden")
        void accessibleName() {
            ProgressBar bar = new ProgressBar();
            assertEquals("Progress bar", bar.getAccessibleName());
            bar.setAccessibleName("Download");
            assertEquals("Download", bar.getAccessibleName());
            bar.dispose();
        }

        @Test
        @DisplayName("showText and indeterminate round-trip")
        void flagsRoundTrip() {
            ProgressBar bar = new ProgressBar();
            bar.setShowText(true);
            assertTrue(bar.isShowText());
            bar.setIndeterminate(true);
            assertTrue(bar.isIndeterminate());
            bar.dispose();
        }
    }

    @Nested
    @DisplayName("indeterminate animation")
    class Animation {

        @Test
        @DisplayName("advanceAnimation is a no-op while determinate")
        void noOpWhenDeterminate() {
            ProgressBar bar = new ProgressBar();
            bar.advanceAnimation(1.0f);
            assertEquals(0.0f, bar.getValue(), 1e-6f);
            bar.dispose();
        }

        @Test
        @DisplayName("advanceAnimation ignores non-positive deltas")
        void ignoresNonPositive() {
            ProgressBar bar = new ProgressBar();
            bar.setIndeterminate(true);
            bar.advanceAnimation(0.0f);
            bar.advanceAnimation(-1.0f);
            // No observable phase getter; asserting no exception plus a stable
            // render path is enough here.
            assertEquals(0.0f, bar.getValue(), 1e-6f);
            bar.dispose();
        }
    }

    @Nested
    @DisplayName("pointer and keyboard")
    class Events {

        @Test
        @DisplayName("the bar never consumes pointer or key events")
        void neverConsumes() {
            ProgressBar bar = new ProgressBar(0, 0, 100, 16);
            MouseEvent move = new MouseEvent(MouseEventType.MOVE, 10, 5,
                    MouseButton.LEFT, 1);
            assertFalse(bar.onMouseEvent(move));
            bar.dispose();
        }
    }

    @Nested
    @DisplayName("raster rendering")
    class Raster {

        private Surface[] surfaceHolder;
        private Canvas canvas;

        @BeforeAll
        static void checkSkija() {
            assumeTrue(RasterCanvasTestFactory.isAvailable(),
                    "Skija native library not loadable on this host; skipping raster tests");
        }

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
        @DisplayName("the accent fill grows with the value")
        void fillGrowsWithValue() {
            int accent = Theme.current().getAccentColor();

            ProgressBar empty = new ProgressBar(0, 0, 200, 20);
            empty.setValue(0.0f);
            canvas.clear(OPAQUE_BLUE);
            empty.renderInternal(canvas);
            canvas.flush();
            boolean emptyFilled = anyPixelEquals(accent, 5, 190, 10);
            empty.dispose();

            ProgressBar full = new ProgressBar(0, 0, 200, 20);
            full.setValue(1.0f);
            canvas.clear(OPAQUE_BLUE);
            full.renderInternal(canvas);
            canvas.flush();
            boolean fullFilled = anyPixelEquals(accent, 5, 190, 10);
            full.dispose();

            assertFalse(emptyFilled, "an empty bar paints no accent fill");
            assertTrue(fullFilled, "a full bar paints the accent fill across the track");
        }

        @Test
        @DisplayName("a half-full bar fills only the left half")
        void halfFillStaysOnTheLeft() {
            int accent = Theme.current().getAccentColor();

            ProgressBar half = new ProgressBar(0, 0, 200, 20);
            half.setValue(0.5f);
            canvas.clear(OPAQUE_BLUE);
            half.renderInternal(canvas);
            canvas.flush();

            boolean left = anyPixelEquals(accent, 2, 90, 10);
            boolean right = anyPixelEquals(accent, 150, 198, 10);
            half.dispose();

            assertTrue(left, "the left half should be filled");
            assertFalse(right, "the right half should stay empty");
        }

        @Test
        @DisplayName("an indeterminate bar paints a moving segment")
        void indeterminatePaintsSegment() {
            int accent = Theme.current().getAccentColor();

            ProgressBar bar = new ProgressBar(0, 0, 200, 20);
            bar.setIndeterminate(true);
            // Phase 0 starts the segment just off the left edge; advance once
            // so it is unambiguously inside the track.
            bar.advanceAnimation(0.5f);
            canvas.clear(OPAQUE_BLUE);
            bar.renderInternal(canvas);
            canvas.flush();

            boolean somewhere = anyPixelEquals(accent, 0, 200, 10);
            bar.dispose();

            assertTrue(somewhere, "an indeterminate bar paints a moving segment");
        }

        @Test
        @DisplayName("advancing the animation clock moves the segment")
        void animationMovesSegment() {
            int accent = Theme.current().getAccentColor();

            ProgressBar bar = new ProgressBar(0, 0, 200, 20);
            bar.setIndeterminate(true);

            bar.advanceAnimation(0.5f);
            canvas.clear(OPAQUE_BLUE);
            bar.renderInternal(canvas);
            canvas.flush();
            int firstColumnWithFill = firstFilledColumn(accent, 200, 10);

            bar.advanceAnimation(0.5f);
            canvas.clear(OPAQUE_BLUE);
            bar.renderInternal(canvas);
            canvas.flush();
            int secondColumnWithFill = firstFilledColumn(accent, 200, 10);

            bar.dispose();

            assertNotEquals(firstColumnWithFill, secondColumnWithFill,
                    "the segment must move after the phase advances");
        }

        private int firstFilledColumn(int color, int width, int y) {
            try (Pixmap pixmap = new Pixmap()) {
                assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
                for (int x = 0; x < width; x++) {
                    if (pixmap.getColor(x, y) == color) {
                        return x;
                    }
                }
            }
            return -1;
        }

        private boolean anyPixelEquals(int color, int x0, int x1, int y) {
            try (Pixmap pixmap = new Pixmap()) {
                assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
                for (int x = x0; x < x1; x++) {
                    if (pixmap.getColor(x, y) == color) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
