package io.github.yasmramos.glyphui.core.backend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for the scroll plumbing shared by every concrete backend: a backend
 * that supports the wheel forwards the notch deltas and pointer position to
 * the registered {@link WindowBackend.ScrollListener}, and an absent listener
 * is a safe no-op.
 */
class AbstractWindowBackendScrollTest {

    /** Exposes {@link AbstractWindowBackend#notifyScroll} for the test. */
    private static final class RecordingBackend extends JwmWindowBackend {
        final List<String> scrolls = new ArrayList<>();

        RecordingBackend() {
            super("scroll-recording", 100, 100);
        }

        @Override
        protected boolean hasLiveWindow() {
            return false;
        }

        void fire(float dx, float dy, double x, double y) {
            notifyScroll(dx, dy, x, y);
        }
    }

    @Test
    void scrollIsForwardedToTheRegisteredListener() {
        RecordingBackend backend = new RecordingBackend();
        backend.setScrollListener((dx, dy, x, y) ->
                backend.scrolls.add(dx + "," + dy + "@" + x + "," + y));

        backend.fire(0f, 1f, 12.0, 34.0);

        assertEquals(List.of("0.0,1.0@12.0,34.0"), backend.scrolls);
    }

    @Test
    void scrollWithoutAListenerIsASafeNoOp() {
        RecordingBackend backend = new RecordingBackend();

        backend.fire(0f, 1f, 12.0, 34.0);

        assertTrue(backend.scrolls.isEmpty());
    }

    @Test
    void clearingTheListenerStopsDelivery() {
        RecordingBackend backend = new RecordingBackend();
        backend.setScrollListener((dx, dy, x, y) -> backend.scrolls.add("seen"));

        backend.fire(0f, 1f, 1.0, 1.0);
        backend.setScrollListener(null);
        backend.fire(0f, 1f, 1.0, 1.0);

        assertEquals(List.of("seen"), backend.scrolls);
    }
}
