package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.backend.HeadlessTestBackend;
import io.github.yasmramos.glyphui.core.internal.TestHooks;
import io.github.yasmramos.glyphui.events.GlyphCursor;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * End-to-end contract for the per-widget pointer shape: a pointer move must
 * resolve the component under the mouse and drive the backend with its
 * {@link GlyphCursor}. Uses the in-memory {@link HeadlessTestBackend} so the
 * whole pipeline runs without a native window.
 */
class CursorPropagationTest extends TestBase {

    private Application app;
    private HeadlessTestBackend backend;
    private Window window;

    @BeforeEach
    void setUp() {
        app = new Application();
        backend = new HeadlessTestBackend("cursor-test", 200, 200);
        window = new Window(backend);
    }

    @AfterEach
    void tearDown() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * Initializes the application or aborts the test when the environment
     * cannot build a surface (headless CI without the native Skija
     * dependencies). The unit-level backend test covers the shared plumbing
     * regardless.
     */
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

    /** Places a widget at a known location. */
    private static void place(Component c, float x, float y, float w, float h) {
        c.setX(x);
        c.setY(y);
        c.setWidth(w);
        c.setHeight(h);
    }

    @Test
    void movingOverAButtonAppliesThePointingHand() {
        initOrSkip();
        Button button = new Button("Click");
        place(button, 10, 10, 100, 40);
        app.getRootPanel().add(button);

        backend.cursorPosListener.onCursorPos(20, 20);

        assertEquals(GlyphCursor.POINTING_HAND, backend.lastCursor);
    }

    @Test
    void movingOverATextFieldAppliesTheTextCaret() {
        initOrSkip();
        TextField field = new TextField();
        place(field, 10, 10, 120, 30);
        app.getRootPanel().add(field);

        backend.cursorPosListener.onCursorPos(20, 20);

        assertEquals(GlyphCursor.TEXT, backend.lastCursor);
    }

    @Test
    void movingOffAnInteractiveWidgetFallsBackToTheArrow() {
        initOrSkip();
        Button button = new Button("Click");
        place(button, 10, 10, 60, 40);
        Label label = new Label("plain");
        place(label, 100, 10, 60, 40);
        app.getRootPanel().add(button);
        app.getRootPanel().add(label);

        backend.cursorPosListener.onCursorPos(20, 20);
        assertEquals(GlyphCursor.POINTING_HAND, backend.lastCursor);

        backend.cursorPosListener.onCursorPos(120, 20);
        assertEquals(GlyphCursor.DEFAULT, backend.lastCursor);
    }

    @Test
    void aDisabledWidgetDoesNotDriveThePointer() {
        initOrSkip();
        Button button = new Button("Click");
        place(button, 10, 10, 100, 40);
        button.setEnabled(false);
        app.getRootPanel().add(button);

        backend.cursorPosListener.onCursorPos(20, 20);

        assertEquals(GlyphCursor.DEFAULT, backend.lastCursor,
                "a disabled control must fall back to the arrow");
    }

    @Test
    void anExplicitOverrideWinsOverTheWidgetDefault() {
        initOrSkip();
        Button button = new Button("Click");
        place(button, 10, 10, 100, 40);
        button.setCursor(GlyphCursor.CROSSHAIR);
        app.getRootPanel().add(button);

        backend.cursorPosListener.onCursorPos(20, 20);

        assertEquals(GlyphCursor.CROSSHAIR, backend.lastCursor);
    }

    @Test
    void repeatedMovesOverTheSameWidgetStillReachTheBackendOnce() {
        initOrSkip();
        Button button = new Button("Click");
        place(button, 10, 10, 100, 40);
        app.getRootPanel().add(button);

        backend.cursorPosListener.onCursorPos(20, 20);
        int afterFirst = backend.cursorApplyCount;
        backend.cursorPosListener.onCursorPos(25, 25);
        backend.cursorPosListener.onCursorPos(30, 30);

        assertEquals(afterFirst + 2, backend.cursorApplyCount,
                "every pointer move drives the backend; de-duplication is "
                        + "the backend's responsibility, not the application's");
    }

    @Test
    void aPopupWinsTheCursorRaceOverThePageBelow() {
        initOrSkip();
        Button button = new Button("Click");
        place(button, 10, 10, 100, 40);
        app.getRootPanel().add(button);

        TextField field = new TextField();
        place(field, 0, 0, 100, 40);
        Panel popup = new Panel();
        place(popup, 10, 10, 100, 40);
        popup.add(field);
        app.getPopupLayer().add(popup);

        backend.cursorPosListener.onCursorPos(20, 20);

        assertEquals(GlyphCursor.TEXT, backend.lastCursor,
                "the popup's text field must win over the button beneath it");
    }

    @Test
    void componentCursorDefaultsToTheArrowAndIsNeverNull() {
        Label label = new Label("plain");
        assertNotNull(label.getCursor());
        assertEquals(GlyphCursor.DEFAULT, label.getCursor());

        // Clearing an override restores the widget default, not the arrow.
        Button button = new Button("Click");
        button.setCursor(GlyphCursor.CROSSHAIR);
        assertEquals(GlyphCursor.CROSSHAIR, button.getCursor());
        button.setCursor(null);
        assertEquals(GlyphCursor.POINTING_HAND, button.getCursor());
    }
}
