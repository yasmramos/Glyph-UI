package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Activity-highlight tests for {@link OverlayScrollbar}: moving the content
 * highlights the bar, and the highlight fades back to rest a moment after the
 * movement stops, so an idle widget stops painting.
 *
 * <p>The movement is detected from the scroll offset the widget paints with,
 * so it is exercised through the real raster {@link OverlayScrollbar#render}
 * path; the fade itself is then pure arithmetic.</p>
 */
class OverlayScrollbarActivityTest {

    private static final float W = 200.0f;
    private static final float H = 100.0f;
    private static final float VY = 0.0f;
    private static final float CONTENT = 400.0f;
    private static final float MAX = 300.0f;

    @Test
    void aFreshBarIsAtRestAndFinished() {
        OverlayScrollbar bar = new OverlayScrollbar();
        assertFalse(bar.isActive(), "a bar that never scrolled is at rest");
        assertTrue(bar.isFinished(), "a resting bar needs no animation");
    }

    @Test
    void movingTheContentHighlightsTheBarOnce() {
        withHighlightedBar(bar -> {
            assertTrue(bar.isActive(), "a changed offset highlights the bar");
            assertTrue(bar.consumeScrollActivity(), "the burst asks for a registration");
            assertFalse(bar.consumeScrollActivity(), "the burst asks only once");
            assertFalse(bar.isFinished(), "a highlighted bar keeps animating");
        });
    }

    @Test
    void theFirstPaintIsNotMovement() {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host");
        Surface[] holder = new Surface[1];
        Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            OverlayScrollbar bar = new OverlayScrollbar();
            bar.render(canvas, W, H, VY, CONTENT, 0.0f, MAX, Theme.dark());
            assertFalse(bar.isActive(), "the first paint only records the offset");
            assertFalse(bar.consumeScrollActivity(),
                    "the first paint must not ask for a registration");
        } finally {
            holder[0].close();
        }
    }

    @Test
    void theHighlightFadesOutAndStopsTheAnimation() {
        withHighlightedBar(bar -> {
            // A short step keeps it active: the fade has not started yet.
            bar.advance(0.1f);
            assertTrue(bar.isActive(), "the highlight lingers briefly after movement");
            assertFalse(bar.isFinished());

            // Past the delay plus the fade, the bar is back at rest and done.
            bar.advance(OverlayScrollbar.FADE_DELAY_SECONDS
                    + OverlayScrollbar.FADE_DURATION_SECONDS);
            assertFalse(bar.isActive(), "the highlight fades back to rest");
            assertTrue(bar.isFinished(), "a rested bar stops the animation loop");
        });
    }

    @Test
    void aNonPositiveDeltaDoesNotAdvanceTheFade() {
        withHighlightedBar(bar -> {
            bar.advance(0.0f);
            bar.advance(-1.0f);
            assertTrue(bar.isActive(), "a stalled or bogus frame must not fade the bar");
        });
    }

    /**
     * Renders a bar twice with a changed offset (the smallest real movement),
     * then hands it to {@code body} while the surface is still alive.
     */
    private static void withHighlightedBar(Consumer<OverlayScrollbar> body) {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host");
        Surface[] holder = new Surface[1];
        Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            OverlayScrollbar bar = new OverlayScrollbar();
            Theme theme = Theme.dark();
            bar.render(canvas, W, H, VY, CONTENT, 0.0f, MAX, theme);
            bar.render(canvas, W, H, VY, CONTENT, 20.0f, MAX, theme);
            body.accept(bar);
        } finally {
            holder[0].close();
        }
    }
}
