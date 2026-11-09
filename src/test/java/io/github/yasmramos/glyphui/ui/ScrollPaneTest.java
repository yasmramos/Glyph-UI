package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.layout.AbsoluteLayout;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link ScrollPane}: content scrolling, the overlay
 * scrollbar geometry and its pointer interaction (thumb drag and track paging).
 */
class ScrollPaneTest extends TestBase {

    @BeforeEach
    void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ' ',
                EnumSet.noneOf(KeyModifier.class));
    }

    /** A 100x100 pane whose single child is 300 tall, so 200px are scrollable. */
    private static ScrollPane overflowingPane() {
        ScrollPane pane = new ScrollPane();
        // Absolute layout keeps the child's declared height; the default flow
        // layout would shrink it to the viewport and hide the overflow.
        pane.setLayoutManager(new AbsoluteLayout());
        pane.setX(0);
        pane.setY(0);
        pane.setWidth(100);
        pane.setHeight(100);
        Label content = new Label("tall");
        content.setX(0);
        content.setY(0);
        content.setWidth(100);
        content.setHeight(300);
        pane.add(content);
        return pane;
    }

    private static ScrollPane fittingPane() {
        ScrollPane pane = new ScrollPane();
        pane.setLayoutManager(new AbsoluteLayout());
        pane.setWidth(100);
        pane.setHeight(100);
        Label content = new Label("short");
        content.setWidth(100);
        content.setHeight(20);
        pane.add(content);
        return pane;
    }

    @Test
    void maxScrollIsTheOverflowBeyondTheViewport() {
        assertEquals(200.0f, overflowingPane().getMaxScrollY());
        assertEquals(0.0f, fittingPane().getMaxScrollY());
    }

    @Test
    void scrollOffsetIsClampedToTheContentRange() {
        ScrollPane pane = overflowingPane();

        pane.setScrollY(1000.0f);
        assertEquals(200.0f, pane.getScrollY());

        pane.setScrollY(-50.0f);
        assertEquals(0.0f, pane.getScrollY());
    }

    @Test
    void theScrollbarIsHiddenWhenTheContentFits() {
        assertFalse(fittingPane().isScrollbarVisible());
        assertTrue(overflowingPane().isScrollbarVisible());
    }

    @Test
    void theWheelScrollsOneLineStepPerNotch() {
        ScrollPane pane = overflowingPane();

        assertTrue(pane.onScroll(
                new io.github.yasmramos.glyphui.events.ScrollEvent(0f, 1f, 50, 50)));
        assertEquals(ScrollPane.LINE_STEP, pane.getScrollY());
    }

    @Test
    void theWheelStopsConsumingAtTheEndOfTheContent() {
        ScrollPane pane = overflowingPane();
        pane.setScrollY(200.0f);

        assertFalse(pane.onScroll(
                        new io.github.yasmramos.glyphui.events.ScrollEvent(0f, 1f, 50, 50)),
                "a pane already at the bottom must let the gesture bubble");
        assertEquals(200.0f, pane.getScrollY());
    }

    @Test
    void theArrowAndPageKeysScroll() {
        ScrollPane pane = overflowingPane();

        pane.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals(ScrollPane.LINE_STEP, pane.getScrollY());

        pane.onKeyEvent(key(GlyphKeys.PAGE_DOWN));
        assertEquals(ScrollPane.LINE_STEP + 100.0f, pane.getScrollY());

        pane.onKeyEvent(key(GlyphKeys.UP));
        assertEquals(100.0f, pane.getScrollY());

        pane.onKeyEvent(key(GlyphKeys.PAGE_UP));
        assertEquals(0.0f, pane.getScrollY());
    }

    @Test
    void aPressOnTheThumbStartsADragThatMovesTheOffset() {
        ScrollPane pane = overflowingPane();
        // The thumb starts at the top of the track; grabbing it near its middle
        // and dragging to the bottom of the track reaches the end of the
        // content.
        float thumbGrabY = 2.0f + 12.0f;
        assertTrue(pane.onMouseEvent(mouse(MouseEventType.PRESS, 96, thumbGrabY)));

        // Drag well past the bottom: the offset clamps to the maximum.
        pane.onMouseEvent(mouse(MouseEventType.DRAG, 96, 500));
        assertEquals(200.0f, pane.getScrollY());

        assertTrue(pane.onMouseEvent(mouse(MouseEventType.RELEASE, 96, 500)),
                "the release after a drag is consumed by the bar");
    }

    @Test
    void aDragWithoutAPressOnTheThumbDoesNotMoveTheOffset() {
        ScrollPane pane = overflowingPane();

        pane.onMouseEvent(mouse(MouseEventType.DRAG, 96, 50));
        assertEquals(0.0f, pane.getScrollY(),
                "a drag that did not start on the thumb must not scroll");
    }

    @Test
    void aPressBelowTheThumbPagesDown() {
        ScrollPane pane = overflowingPane();

        // y=90 is on the track but well below the thumb (which ends at ~24).
        assertTrue(pane.onMouseEvent(mouse(MouseEventType.PRESS, 96, 90)));
        assertEquals(100.0f, pane.getScrollY());
    }

    @Test
    void aPressOnTheContentIsNotTheScrollbar() {
        ScrollPane pane = overflowingPane();

        // x=50 is over the content, left of the bar at x>=90.
        pane.onMouseEvent(mouse(MouseEventType.PRESS, 50, 50));
        assertEquals(0.0f, pane.getScrollY(),
                "a press on the content must not page the pane");
    }

    @Test
    void theBarResolvesHoverToThePaneNotTheChildUnderIt() {
        ScrollPane pane = overflowingPane();

        assertSame(pane, pane.getComponentAt(96, 50),
                "a point on the overlay bar belongs to the pane");
        assertFalse(pane.getComponentAt(50, 50) == pane,
                "a point off the bar still resolves through the children");
    }

    @Test
    void aPaneWithNothingToScrollHasNoBarAndNeverPages() {
        ScrollPane pane = fittingPane();

        pane.onMouseEvent(mouse(MouseEventType.PRESS, 96, 50));
        assertEquals(0.0f, pane.getScrollY(),
                "a pane with no overflow must not page from a track press");
    }

    @Test
    void theScrollbarSurvivesAScrollToTheEnd() {
        ScrollPane pane = overflowingPane();
        pane.setScrollY(pane.getMaxScrollY());

        assertTrue(pane.isScrollbarVisible());
        // A point on the bar still resolves to the pane when the thumb is at
        // the bottom of the track.
        assertSame(pane, pane.getComponentAt(96, 95));
    }

    @Test
    void theScrollbarPaintsInkOnTheTrailingEdge() {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host");
        Surface[] holder = new Surface[1];
        Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            canvas.clear(0xFFFFFFFF);
            ScrollPane pane = overflowingPane();
            pane.renderInternal(canvas);
            canvas.flush();

            try (Pixmap pixmap = new Pixmap()) {
                assertTrue(holder[0].peekPixels(pixmap), "peekPixels should succeed");
                // The bar occupies the trailing edge; the content area on the
                // left stays clean, so ink must be present on the bar and
                // absent well away from it.
                assertTrue(hasInk(pixmap, 92, 0, 100, 100),
                        "the overlay bar must paint on the trailing edge");
                assertFalse(hasInk(pixmap, 0, 0, 80, 100),
                        "the content area must not be tinted by the bar");
            }
            pane.dispose();
        } finally {
            holder[0].close();
        }
    }

    private static boolean hasInk(Pixmap pixmap, int x0, int y0, int x1, int y1) {
        for (int py = y0; py < y1; py++) {
            for (int px = x0; px < x1; px++) {
                if ((pixmap.getColor(px, py) & 0x00FFFFFF) != 0x00FFFFFF) {
                    return true;
                }
            }
        }
        return false;
    }
}
