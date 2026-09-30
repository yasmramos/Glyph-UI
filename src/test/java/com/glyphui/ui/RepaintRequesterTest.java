package com.glyphui.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for the global repaint-requester hook on {@link Component}.
 * The hook is how widgets notify the application that visual state changed
 * and an on-demand repaint should be scheduled.
 */
class RepaintRequesterTest {

    private AtomicInteger repaintCount;

    @BeforeEach
    void setUp() {
        repaintCount = new AtomicInteger(0);
        Component.setRepaintRequester(repaintCount::incrementAndGet);
    }

    @AfterEach
    void tearDown() {
        // Reset to the default no-op so other tests are unaffected.
        Component.setRepaintRequester(null);
    }

    /** Minimal concrete component exposing the protected repaint hook. */
    private static class TestComponent extends Component {
        TestComponent() {
            super(0, 0, 100, 50);
        }

        @Override
        public void render(com.glyphui.graphics.Canvas canvas) {
            // No drawing needed for these tests.
        }

        @Override
        public boolean onMouseEvent(com.glyphui.events.MouseEvent event) {
            // Not exercised by these tests.
            return false;
        }

        @Override
        public void onKeyEvent(com.glyphui.events.KeyEvent event) {
            // Not exercised by these tests.
        }

        void triggerRepaint() {
            requestRepaint();
        }
    }

    @Nested
    class HookRegistration {

        @Test
        void nullRequesterResetsToNoOpAndMutationsAreSafe() {
            Component.setRepaintRequester(null);

            TestComponent component = new TestComponent();
            assertDoesNotThrow(component::triggerRepaint);
            assertEquals(0, repaintCount.get());
        }

        @Test
        void requesterIsStaticAcrossComponentInstances() {
            TestComponent a = new TestComponent();
            TestComponent b = new TestComponent();

            a.triggerRepaint();
            b.triggerRepaint();

            assertEquals(2, repaintCount.get());
        }

        @Test
        void replacingRequesterRoutesToTheNewestOne() {
            AtomicInteger second = new AtomicInteger(0);
            Component.setRepaintRequester(second::incrementAndGet);

            new TestComponent().triggerRepaint();

            assertEquals(0, repaintCount.get(), "old requester must not be called");
            assertEquals(1, second.get());
        }
    }

    @Nested
    class WidgetMutationsRequestRepaint {

        @Test
        void labelSetTextRequestsRepaint() {
            Label label = new Label(0, 0, 100, 20, "initial");
            int before = repaintCount.get();

            label.setText("changed");

            assertEquals(before + 1, repaintCount.get());
        }

        @Test
        void labelSameTextDoesNotRequestRepaint() {
            Label label = new Label(0, 0, 100, 20, "same");
            int before = repaintCount.get();

            label.setText("same");

            assertEquals(before, repaintCount.get());
        }

        @Test
        void buttonStateChangeRequestsRepaint() {
            Button button = new Button(0, 0, 120, 40, "Click");
            int before = repaintCount.get();

            button.setState(ComponentState.HOVER);

            assertEquals(before + 1, repaintCount.get());
        }

        @Test
        void panelAddChildRequestsRepaint() {
            Panel panel = new Panel(0, 0, 200, 200);
            int before = repaintCount.get();

            panel.add(new TestComponent());

            assertEquals(before + 1, repaintCount.get());
        }

        @Test
        void panelResizeRequestsRepaint() {
            Panel panel = new Panel(0, 0, 200, 200);
            int before = repaintCount.get();

            // Component.setWidth requests one repaint; Panel.setWidth also
            // marks the layout dirty (which requests another one).
            panel.setWidth(300);

            assertEquals(before + 2, repaintCount.get());
        }
    }
}
