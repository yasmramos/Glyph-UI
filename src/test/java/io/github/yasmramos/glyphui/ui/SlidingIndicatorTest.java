package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.SkijaTestSupport;
import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.core.Window;
import io.github.yasmramos.glyphui.core.internal.TestHooks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SlidingIndicator}, the shared glide behind the tab
 * underline and the segmented block. The detached cases drive the run
 * explicitly with {@code advance(...)}; the attached case checks that starting
 * a glide registers with the frame loop.
 */
class SlidingIndicatorTest {

    @Test
    @DisplayName("the first target places the indicator without animating")
    void firstTargetPlacesInPlace() {
        SlidingIndicator indicator = new SlidingIndicator();
        Panel owner = new Panel();

        indicator.glideTo(owner, 40.0f, 30.0f);

        assertEquals(40.0f, indicator.left());
        assertEquals(30.0f, indicator.width());
        assertTrue(indicator.isFinished(), "there is no previous position to slide from");
    }

    @Test
    @DisplayName("a later target starts a glide from the current position")
    void laterTargetStartsAGlide() {
        SlidingIndicator indicator = new SlidingIndicator();
        Panel owner = new Panel();
        indicator.glideTo(owner, 0.0f, 30.0f);

        indicator.glideTo(owner, 60.0f, 30.0f);

        assertEquals(0.0f, indicator.left(), "the glide has not been driven yet");
        assertFalse(indicator.isFinished(), "moving to another cell must start a glide");
        indicator.advance(1.0f);
        assertTrue(indicator.isFinished());
        assertEquals(60.0f, indicator.left());
    }

    @Test
    @DisplayName("repeating the current target does not restart the glide")
    void repeatedTargetIsStable() {
        SlidingIndicator indicator = new SlidingIndicator();
        Panel owner = new Panel();
        indicator.glideTo(owner, 0.0f, 30.0f);
        indicator.glideTo(owner, 60.0f, 30.0f);
        indicator.advance(0.05f);

        float midFlight = indicator.left();
        indicator.glideTo(owner, 60.0f, 30.0f);

        assertEquals(midFlight, indicator.left(), "a repeated target must not jump or restart");
        assertFalse(indicator.isFinished(), "the glide is still travelling");
    }

    @Test
    @DisplayName("retargeting mid-flight stays continuous and settles on the new cell")
    void retargetMidFlightSettlesOnTheNewTarget() {
        SlidingIndicator indicator = new SlidingIndicator();
        Panel owner = new Panel();
        indicator.glideTo(owner, 0.0f, 30.0f);

        indicator.glideTo(owner, 120.0f, 30.0f);
        indicator.advance(0.05f);

        indicator.glideTo(owner, 60.0f, 40.0f);

        assertFalse(indicator.isFinished(), "the glide is still travelling");
        indicator.advance(1.0f);
        assertTrue(indicator.isFinished());
        assertEquals(60.0f, indicator.left());
        assertEquals(40.0f, indicator.width());
    }

    @Nested
    @DisplayName("with a live application")
    class Attached {

        private Application app;

        @BeforeAll
        static void checkSkija() {
            Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                    "Skija native library not loadable on this host; skipping");
        }

        @AfterEach
        void tearDown() {
            if (app != null) {
                app.close();
                app = null;
            }
        }

        @Test
        @DisplayName("starting a glide registers with the frame loop")
        void glideRegistersWithTheApplication() {
            app = new Application();
            if (!TestHooks.initForTesting(app, new Window("sliding-indicator-test", 200, 120))) {
                app.close();
                app = null;
                Assumptions.abort("Application.init() unavailable on this host");
            }
            Panel owner = new Panel();
            app.getRootPanel().add(owner);
            // Render twice: the first frame lays the new child out and arms the
            // repaint latch, the second settles it.
            TestHooks.renderFrame(app);
            TestHooks.renderFrame(app);
            assertFalse(TestHooks.isPaintDirty(app), "precondition: the tree is clean");

            SlidingIndicator indicator = new SlidingIndicator();
            indicator.glideTo(owner, 0.0f, 30.0f);
            assertFalse(TestHooks.isPaintDirty(app),
                    "placing the indicator on first sight must not schedule a repaint");

            indicator.glideTo(owner, 60.0f, 30.0f);

            assertTrue(TestHooks.isPaintDirty(app),
                    "starting a glide must register with the loop, which schedules a repaint");
        }
    }
}
