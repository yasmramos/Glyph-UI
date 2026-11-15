package io.github.yasmramos.glyphui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Geometry and state-machine tests for {@link OverlayScrollbar}, the shared bar
 * behind {@link ScrollPane}, {@link ListBox}, {@link Table} and
 * {@link TreeView}.
 */
class OverlayScrollbarTest {

    private static final float W = 100.0f;
    private static final float H = 100.0f;
    private static final float VY = 0.0f;

    @Test
    void hiddenWhenTheContentFits() {
        OverlayScrollbar bar = new OverlayScrollbar();
        assertFalse(bar.isVisible(H, H));
        assertFalse(bar.isVisible(H, H - 1.0f));
    }

    @Test
    void visibleWhenTheContentOverflows() {
        assertTrue(new OverlayScrollbar().isVisible(H, H + 1.0f));
    }

    @Test
    void trackHitTestOnlyMatchesTheTrailingEdge() {
        OverlayScrollbar bar = new OverlayScrollbar();
        assertTrue(bar.isOverTrack(94.0f, 50.0f, W, H, VY, 200.0f));
        assertFalse(bar.isOverTrack(10.0f, 50.0f, W, H, VY, 200.0f),
                "the content area is not the bar");
        assertFalse(bar.isOverTrack(94.0f, 50.0f, W, H, VY, H),
                "a fitting content shows no bar to hit");
    }

    @Test
    void aPressOnTheThumbStartsADrag() {
        OverlayScrollbar bar = new OverlayScrollbar();
        assertEquals(OverlayScrollbar.Press.THUMB,
                bar.press(94.0f, 10.0f, W, H, VY, 200.0f, 0.0f, 100.0f));
        assertTrue(bar.isDragging());
    }

    @Test
    void aPressOnTheTrackPagesInsteadOfDragging() {
        OverlayScrollbar bar = new OverlayScrollbar();
        // At the top the thumb sits high, so a press near the bottom pages down.
        assertEquals(OverlayScrollbar.Press.TRACK_AFTER,
                bar.press(94.0f, 95.0f, W, H, VY, 200.0f, 0.0f, 100.0f));
        assertFalse(bar.isDragging());
        // At the end the thumb sits low, so a press near the top pages up.
        OverlayScrollbar other = new OverlayScrollbar();
        assertEquals(OverlayScrollbar.Press.TRACK_BEFORE,
                other.press(94.0f, 10.0f, W, H, VY, 200.0f, 100.0f, 100.0f));
    }

    @Test
    void aPressOffTheBarIsIgnored() {
        OverlayScrollbar bar = new OverlayScrollbar();
        assertEquals(OverlayScrollbar.Press.NONE,
                bar.press(10.0f, 50.0f, W, H, VY, 200.0f, 0.0f, 100.0f));
        assertFalse(bar.isDragging());
    }

    @Test
    void draggingMapsThePointerToAnOffsetAndClampsIt() {
        OverlayScrollbar bar = new OverlayScrollbar();
        bar.press(94.0f, 10.0f, W, H, VY, 200.0f, 0.0f, 100.0f);
        assertEquals(100.0f, bar.drag(500.0f, H, VY, 200.0f, 100.0f), 0.01f,
                "dragging past the bottom clamps to the maximum offset");
        assertEquals(0.0f, bar.drag(-50.0f, H, VY, 200.0f, 100.0f), 0.01f,
                "dragging past the top clamps to zero");
    }

    @Test
    void releaseEndsTheDrag() {
        OverlayScrollbar bar = new OverlayScrollbar();
        bar.press(94.0f, 10.0f, W, H, VY, 200.0f, 0.0f, 100.0f);
        assertTrue(bar.release());
        assertFalse(bar.isDragging());
        assertFalse(bar.release(), "a second release is a no-op");
        assertTrue(Float.isNaN(bar.drag(10.0f, H, VY, 200.0f, 100.0f)),
                "a drag outside a press is inert");
    }

    @Test
    void hoverChangesOnlyOnce() {
        OverlayScrollbar bar = new OverlayScrollbar();
        assertTrue(bar.setHover(true));
        assertFalse(bar.setHover(true), "staying on the bar needs no repaint");
        assertTrue(bar.setHover(false));
    }
}
