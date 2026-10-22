package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dispose contracts of {@link Component} (Phase 1 checklist):
 *
 * <ul>
 *   <li>{@code dispose()} is idempotent: a second call does nothing and never
 *       releases the same resource twice.</li>
 *   <li>The release order is fixed: children first, then tracked resources in
 *       registration order, and finally the {@code onDispose()} hook.</li>
 *   <li>{@code track()} closes resources in registration order.</li>
 *   <li>{@code track()} after disposal closes the resource immediately, so
 *       late registration cannot leak.</li>
 * </ul>
 */
class DisposeContractTest extends TestBase {

    /** Records every lifecycle callback it receives, shared across widgets. */
    private static final class Recorder {
        final List<String> events = new ArrayList<>();

        void add(String event) {
            events.add(event);
        }
    }

    /** A leaf component that records its hooks. */
    private static class RecordingComponent extends TestComponent {
        private final Recorder recorder;
        private final String name;

        RecordingComponent(Recorder recorder, String name) {
            super(0, 0, 10, 10);
            this.recorder = recorder;
            this.name = name;
        }

        @Override
        protected void onDispose() {
            recorder.add("onDispose:" + name);
        }
    }

    /** A panel that records both the children phase and its own hook. */
    private static class RecordingPanel extends Panel {
        private final Recorder recorder;
        private final String name;

        RecordingPanel(Recorder recorder, String name) {
            super(0, 0, 100, 100);
            this.recorder = recorder;
            this.name = name;
        }

        @Override
        protected void disposeChildren() {
            recorder.add("disposeChildren:" + name);
            super.disposeChildren(); // disposes children through their dispose()
        }

        @Override
        protected void onDispose() {
            recorder.add("onDispose:" + name);
        }
    }

    /** An AutoCloseable that counts how many times close() ran. */
    private static final class CountingResource implements AutoCloseable {
        int closeCount;
        private final Recorder recorder;
        private final String name;

        CountingResource(Recorder recorder, String name) {
            this.recorder = recorder;
            this.name = name;
        }

        @Override
        public void close() {
            closeCount++;
            if (recorder != null) {
                recorder.add("close:" + name);
            }
        }
    }

    @Test
    void disposeIsIdempotentAndNeverReleasesTwice() {
        RecordingComponent component = new RecordingComponent(new Recorder(), "leaf");
        CountingResource resource = new CountingResource(null, "res");
        component.track(resource);

        component.dispose();
        assertTrue(component.isDisposed());

        // A second (and third) dispose must be a silent no-op: no exception,
        // no double-close of tracked resources, no repeated onDispose hook.
        assertDoesNotThrow(component::dispose);
        assertDoesNotThrow(component::dispose);

        assertEquals(1, resource.closeCount,
                "tracked resources must be closed exactly once across repeated dispose() calls");
    }

    @Test
    void disposeRunsChildrenThenResourcesThenOnDispose() {
        Recorder recorder = new Recorder();
        RecordingPanel parent = new RecordingPanel(recorder, "parent");
        RecordingComponent child = new RecordingComponent(recorder, "child");
        parent.add(child);

        // Registration order matters for the tracked-resource phase.
        parent.track(new CountingResource(recorder, "first"));
        parent.track(new CountingResource(recorder, "second"));

        parent.dispose();

        assertEquals(
                List.of(
                        "disposeChildren:parent",
                        "onDispose:child",          // children go first...
                        "close:first",              // ...then tracked resources,
                        "close:second",             // in registration order...
                        "onDispose:parent"),         // ...and the hook last
                recorder.events);
    }

    @Test
    void trackClosesResourcesInRegistrationOrder() {
        Recorder recorder = new Recorder();
        RecordingComponent component = new RecordingComponent(recorder, "c");
        component.track(new CountingResource(recorder, "a"));
        component.track(new CountingResource(recorder, "b"));
        component.track(new CountingResource(recorder, "c"));

        component.dispose();

        assertEquals(List.of("close:a", "close:b", "close:c", "onDispose:c"),
                recorder.events, "tracked resources close in registration order, before onDispose");
    }

    @Test
    void trackAfterDisposeClosesImmediately() {
        Recorder recorder = new Recorder();
        RecordingComponent component = new RecordingComponent(recorder, "c");
        component.dispose();

        // Late registration must not leak: the resource is closed on the spot.
        CountingResource late = new CountingResource(recorder, "late");
        CountingResource returned = component.track(late);

        assertEquals(1, late.closeCount,
                "tracking after dispose must close the resource immediately");
        assertSame(returned, late, "track() returns the resource for chaining");

        // And disposing again changes nothing (idempotency holds).
        component.dispose();
        assertEquals(1, late.closeCount);
    }

    @Test
    void trackingNullIsRejected() {
        RecordingComponent component = new RecordingComponent(new Recorder(), "c");
        assertThrows(NullPointerException.class, () -> component.track(null));
        assertFalse(component.isDisposed());
    }

    private static void assertSame(Object expected, Object actual, String message) {
        org.junit.jupiter.api.Assertions.assertSame(expected, actual, message);
    }
}
