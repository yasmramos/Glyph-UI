package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.backend.HeadlessTestBackend;
import io.github.yasmramos.glyphui.core.internal.TestHooks;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.ScrollPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end contract for mouse-wheel scrolling: a wheel gesture must reach the
 * widget under the pointer and scroll it, and it must never be mistaken for a
 * click. Uses the in-memory {@link HeadlessTestBackend} so the whole pipeline
 * runs without a native window.
 */
class ScrollPropagationTest extends TestBase {

    private Application app;
    private HeadlessTestBackend backend;
    private Window window;

    @BeforeEach
    void setUp() {
        app = new Application();
        backend = new HeadlessTestBackend("scroll-test", 200, 200);
        window = new Window(backend);
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.close();
        }
    }

    private void initOrSkip() {
        boolean ok;
        try {
            ok = TestHooks.initForTesting(app, window);
        } catch (RuntimeException | LinkageError e) {
            Assumptions.abort("native surface unavailable: " + e.getMessage());
            return;
        }
        Assumptions.assumeTrue(ok, "headless backend could not initialize");
    }

    private static void place(Component c, float x, float y, float w, float h) {
        c.setX(x);
        c.setY(y);
        c.setWidth(w);
        c.setHeight(h);
    }

    /** A pane whose content is taller than the viewport, so it can scroll. */
    private static ScrollPane scrollablePane(float x, float y, float w, float h) {
        ScrollPane pane = new ScrollPane();
        place(pane, x, y, w, h);
        Label tall = new Label("tall");
        place(tall, 0, 0, w, h + 200);
        pane.add(tall);
        return pane;
    }

    @Test
    void aWheelOverAScrollPaneScrollsIt() {
        initOrSkip();
        ScrollPane pane = scrollablePane(0, 0, 100, 100);
        app.getRootPanel().add(pane);

        backend.scrollListener.onScroll(0f, 1f, 50, 50);

        assertEquals(ScrollPane.LINE_STEP, pane.getScrollY(),
                "one notch must scroll one line step");
    }

    @Test
    void theWheelIsRoutedToTheWidgetUnderThePointer() {
        initOrSkip();
        ScrollPane left = scrollablePane(0, 0, 100, 100);
        ScrollPane right = scrollablePane(100, 0, 100, 100);
        app.getRootPanel().add(left);
        app.getRootPanel().add(right);

        backend.scrollListener.onScroll(0f, 1f, 150, 50);

        assertEquals(0f, left.getScrollY(),
                "the pane the pointer is not over must not move");
        assertEquals(ScrollPane.LINE_STEP, right.getScrollY());
    }

    @Test
    void aWheelNeverClicksAControl() {
        initOrSkip();
        AtomicInteger clicks = new AtomicInteger();
        Button button = new Button("Click");
        button.setOnClick(() -> clicks.incrementAndGet());
        place(button, 10, 10, 100, 40);
        app.getRootPanel().add(button);

        backend.scrollListener.onScroll(0f, 1f, 20, 20);

        assertEquals(0, clicks.get(),
                "a wheel gesture must never press or click a control");
    }

    @Test
    void aWheelScrollsTheNearestAncestorThatScrolls() {
        initOrSkip();
        ScrollPane pane = scrollablePane(0, 0, 100, 100);
        app.getRootPanel().add(pane);

        // The pointer is over the label inside the pane: the gesture bubbles
        // up to the pane because the label does not scroll.
        backend.scrollListener.onScroll(0f, 2f, 50, 50);

        assertEquals(2 * ScrollPane.LINE_STEP, pane.getScrollY());
    }

    @Test
    void aNestedScrollPaneWinsOverTheOuterOne() {
        initOrSkip();
        ScrollPane outer = scrollablePane(0, 0, 100, 100);
        ScrollPane inner = scrollablePane(10, 10, 50, 50);
        outer.add(inner);
        app.getRootPanel().add(outer);

        backend.scrollListener.onScroll(0f, 1f, 20, 20);

        assertEquals(ScrollPane.LINE_STEP, inner.getScrollY(),
                "the inner pane under the pointer consumes the gesture");
        assertEquals(0f, outer.getScrollY(),
                "the outer pane must not also scroll");
    }

    @Test
    void aPlainPanelDoesNotConsumeAWheel() {
        Panel plain = new Panel();
        place(plain, 0, 0, 100, 100);

        assertFalse(plain.onScroll(
                        new io.github.yasmramos.glyphui.events.ScrollEvent(0f, 1f, 50, 50)),
                "the default hook must let the gesture bubble");
    }

    @Test
    void aPaneWithNothingToScrollConsumesNothing() {
        initOrSkip();
        ScrollPane pane = new ScrollPane();
        place(pane, 0, 0, 100, 100);
        Label shortContent = new Label("fits");
        place(shortContent, 0, 0, 100, 20);
        pane.add(shortContent);
        app.getRootPanel().add(pane);

        backend.scrollListener.onScroll(0f, 1f, 50, 50);

        assertEquals(0f, pane.getScrollY());
    }

    @Test
    void theScrollListenerIsRegisteredOnTheWindow() {
        initOrSkip();
        assertNotNull(backend.scrollListener,
                "the application must register a scroll listener");
    }
}
