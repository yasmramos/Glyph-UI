package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.SkijaTestSupport;
import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.core.Window;
import io.github.yasmramos.glyphui.core.internal.TestHooks;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the interactive widgets' colour transitions: with a
 * live {@link Application} a state change starts an eased fade, and with no
 * application (a detached widget) it applies the new colour at once.
 */
class WidgetColorTransitionTest extends TestBase {

    private Application app;
    private Surface[] surfaceHolder;
    private Canvas canvas;

    @BeforeAll
    static void checkSkija() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library not loadable on this host; skipping");
    }

    @AfterEach
    void tearDown() {
        if (surfaceHolder != null && surfaceHolder[0] != null) {
            surfaceHolder[0].close();
            surfaceHolder = null;
        }
        if (app != null) {
            app.close();
            app = null;
        }
    }

    /** Starts an application and attaches {@code widget} to its root panel. */
    private void attach(Component widget) {
        app = new Application();
        if (!TestHooks.initForTesting(app, new Window("color-transition-test", 320, 240))) {
            app.close();
            app = null;
            Assumptions.abort("Application.init() unavailable on this host");
        }
        app.getRootPanel().add(widget);
        surfaceHolder = new Surface[1];
        canvas = RasterCanvasTestFactory.create(surfaceHolder);
    }

    private static MouseEvent move(int x, int y) {
        return new MouseEvent(MouseEventType.MOVE, x, y, MouseButton.LEFT, 0);
    }

    @Test
    @DisplayName("an attached button fades on hover instead of snapping")
    void buttonHoverFadesWhenAttached() {
        Button button = new Button(0, 0, 100, 40, "Hover");
        attach(button);

        // First paint seeds the resting (idle) colour: no fade on appearance.
        button.renderInternal(canvas);
        canvas.flush();
        assertTrue(button.isFinished(), "the first paint must rest at the idle colour");

        button.onMouseEvent(move(50, 20));
        assertTrue(button.isFinished(), "setState alone does not paint");

        // The next paint sees the new state and starts the fade.
        button.renderInternal(canvas);
        canvas.flush();
        assertFalse(button.isFinished(), "an attached button must fade, not snap");

        button.advance(1.0f);
        button.renderInternal(canvas);
        canvas.flush();
        assertTrue(button.isFinished(), "the fade settles once its duration elapses");
    }

    @Test
    @DisplayName("an attached check box fades when checked")
    void checkBoxFadesWhenAttached() {
        CheckBox box = new CheckBox("Enabled");
        box.setWidth(120);
        box.setHeight(24);
        attach(box);

        box.renderInternal(canvas);
        canvas.flush();
        assertTrue(box.isFinished());

        box.setChecked(true);
        box.renderInternal(canvas);
        canvas.flush();
        assertFalse(box.isFinished(), "checking must fade the box fill");

        box.advance(1.0f);
        assertTrue(box.isFinished());
    }

    @Test
    @DisplayName("an attached radio button fades when selected")
    void radioButtonFadesWhenAttached() {
        RadioButton radio = new RadioButton(0, 0, 120, 24);
        attach(radio);

        radio.renderInternal(canvas);
        canvas.flush();
        assertTrue(radio.isFinished());

        radio.setSelected(true);
        radio.renderInternal(canvas);
        canvas.flush();
        assertFalse(radio.isFinished(), "selecting must fade the indicator fill");

        radio.advance(1.0f);
        assertTrue(radio.isFinished());
    }

    @Test
    @DisplayName("a detached button leaves the transition pending for a caller to drive")
    void detachedButtonLeavesRunPending() {
        assertNull(Application.getCurrent(), "precondition: no live application");
        surfaceHolder = new Surface[1];
        canvas = RasterCanvasTestFactory.create(surfaceHolder);

        Button button = new Button(0, 0, 100, 40, "Detached");
        button.renderInternal(canvas);
        canvas.flush();

        button.onMouseEvent(move(50, 20));
        button.renderInternal(canvas);
        canvas.flush();

        assertFalse(button.isFinished(),
                "with no frame loop to drive it, a detached button keeps the transition pending");
        button.advance(1.0f);
        assertTrue(button.isFinished(), "a caller driving advance() settles it");
        button.dispose();
    }
}
