package com.glyphui.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;
import com.glyphui.ui.TestComponent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ComponentDisposeTest {

    /** Leaf that logs when its native-resource hook runs. */
    private static final class LoggingLeaf extends TestComponent {
        private final String name;
        private final List<String> log;

        LoggingLeaf(String name, List<String> log) {
            super(0, 0, 10, 10);
            this.name = name;
            this.log = log;
        }

        @Override
        protected void onDispose() {
            log.add(name + ":onDispose");
        }
    }

    /** Panel that logs when its native-resource hook runs. */
    private static final class LoggingPanel extends Panel {
        private final List<String> log;

        LoggingPanel(List<String> log) {
            super(0, 0, 100, 100);
            this.log = log;
        }

        @Override
        protected void onDispose() {
            log.add("panel:onDispose");
            super.onDispose();
        }
    }

    private final List<String> log = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        Reactor.reset();
        log.clear();
    }

    @AfterEach
    public void tearDown() {
        Reactor.reset();
    }

    @Test
    public void componentIsDisposableAndStartsAlive() {
        Component component = new TestComponent(0, 0, 10, 10);

        assertTrue(component instanceof Disposable);
        assertTrue(!component.isDisposed());
    }

    @Test
    public void cascadesChildrenBeforeTrackedResourcesBeforeNativeHooks() {
        LoggingPanel panel = new LoggingPanel(log);
        LoggingLeaf first = new LoggingLeaf("child1", log);
        LoggingLeaf second = new LoggingLeaf("child2", log);
        panel.add(first);
        panel.add(second);
        first.track(() -> log.add("child1:resource"));
        second.track(() -> log.add("child2:resource"));
        panel.track(() -> log.add("panel:resource1"));
        panel.track(() -> log.add("panel:resource2"));

        panel.dispose();

        assertEquals(List.of(
                "child1:resource", "child1:onDispose",
                "child2:resource", "child2:onDispose",
                "panel:resource1", "panel:resource2",
                "panel:onDispose"), log);
        assertTrue(panel.isDisposed());
        assertTrue(first.isDisposed());
        assertTrue(second.isDisposed());
    }

    @Test
    public void cascadesThroughNestedPanels() {
        Panel root = new Panel(0, 0, 100, 100);
        Panel middle = new Panel(0, 0, 50, 50);
        LoggingLeaf leaf = new LoggingLeaf("leaf", log);
        root.add(middle);
        middle.add(leaf);
        middle.track(() -> log.add("middle:resource"));
        root.track(() -> log.add("root:resource"));

        root.dispose();

        assertEquals(List.of("leaf:onDispose", "middle:resource", "root:resource"), log);
        assertTrue(leaf.isDisposed());
        assertTrue(middle.isDisposed());
    }

    @Test
    public void disposingDetachesTheChildren() {
        Panel panel = new Panel(0, 0, 100, 100);
        TestComponent child = new TestComponent(0, 0, 10, 10);
        panel.add(child);

        panel.dispose();

        assertTrue(panel.getChildren().isEmpty());
        assertNull(child.getParent());
    }

    @Test
    public void disposeIsIdempotent() {
        LoggingPanel panel = new LoggingPanel(log);
        LoggingLeaf child = new LoggingLeaf("child", log);
        panel.add(child);
        int[] closed = {0};
        panel.track(() -> closed[0]++);

        panel.dispose();
        panel.dispose();
        panel.close();

        assertEquals(1, closed[0]);
        assertEquals(List.of("child:onDispose", "panel:onDispose"), log);
    }

    @Test
    public void trackReturnsTheResource() {
        Component component = new TestComponent(0, 0, 10, 10);
        Signal<Integer> signal = new Signal<>(0);
        Binding<Integer> binding = new Binding<>(signal, v -> { });

        assertSame(binding, component.track(binding));
    }

    @Test
    public void bindingsAndComputedValuesAreDisposedWithTheComponent() {
        Component component = new TestComponent(0, 0, 10, 10);
        Signal<Integer> signal = new Signal<>(1);
        Computed<Integer> doubled = new Computed<>(() -> signal.get() * 2);
        List<Integer> applied = new ArrayList<>();
        Binding<Integer> binding = component.track(new Binding<>(doubled, applied::add));
        component.track(doubled);

        signal.set(2);
        assertEquals(List.of(2, 4), applied);

        component.dispose();

        assertTrue(binding.isDisposed());
        assertTrue(doubled.isDisposed());
        assertEquals(0, signal.observerCount());
        signal.set(3);
        assertEquals(List.of(2, 4), applied);
    }

    @Test
    public void bindingsOfAChildAreReleasedByDisposingTheParent() {
        Panel parent = new Panel(0, 0, 100, 100);
        TestComponent child = new TestComponent(0, 0, 10, 10);
        parent.add(child);
        Signal<Integer> signal = new Signal<>(0);
        child.track(new Binding<>(signal, v -> { }));
        assertEquals(1, signal.observerCount());

        parent.dispose();

        assertEquals(0, signal.observerCount());
    }

    @Test
    public void trackingAfterDisposeClosesTheResourceImmediately() {
        Component component = new TestComponent(0, 0, 10, 10);
        component.dispose();
        int[] closed = {0};

        component.track(() -> closed[0]++);

        assertEquals(1, closed[0]);
    }

    @Test
    public void aFailureDoesNotSkipSiblingsResourcesOrNativeHooks() {
        LoggingPanel panel = new LoggingPanel(log);
        TestComponent failing = new TestComponent(0, 0, 10, 10) {
            @Override
            protected void onDispose() {
                throw new IllegalArgumentException("child failed");
            }
        };
        LoggingLeaf sibling = new LoggingLeaf("sibling", log);
        panel.add(failing);
        panel.add(sibling);
        panel.track(() -> log.add("panel:resource"));
        panel.track(() -> {
            throw new IllegalStateException("resource failed");
        });

        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, panel::dispose);

        assertEquals(List.of("sibling:onDispose", "panel:resource", "panel:onDispose"), log);
        assertEquals(1, thrown.getSuppressed().length);
        assertTrue(thrown.getSuppressed()[0] instanceof IllegalStateException);
        assertTrue(panel.isDisposed());
        // A second call stays quiet: the failure is reported once
        panel.dispose();
    }

    @Test
    public void panelClearStillDisposesRemovedChildren() {
        Panel panel = new Panel(0, 0, 100, 100);
        LoggingLeaf child = new LoggingLeaf("child", log);
        panel.add(child);

        panel.clear();

        assertEquals(List.of("child:onDispose"), log);
        assertTrue(child.isDisposed());
    }

    // ------------------------------------------------------------------
    // Leak checks
    // ------------------------------------------------------------------

    private static final int CYCLES = 100;

    /**
     * Creates and disposes {@code CYCLES} components that are all wired to the
     * same long-lived signal. Lives in its own frame so no local keeps the last
     * component reachable.
     */
    private static List<WeakReference<Component>> runCycles(Signal<Integer> shared, boolean dispose) {
        List<WeakReference<Component>> refs = new ArrayList<>();
        for (int i = 0; i < CYCLES; i++) {
            Panel panel = new Panel(0, 0, 100, 100);
            TestComponent child = new TestComponent(0, 0, 10, 10);
            panel.add(child);
            // The effect captures the components: a leaked binding would pin them
            panel.track(new Binding<>(shared, v -> panel.getX()));
            Computed<Float> derived = new Computed<>(() -> shared.get() + child.getX());
            derived.get(); // evaluate once so it subscribes to the shared signal
            child.track(derived);
            child.track(new Binding<>(shared, v -> child.getX()));
            refs.add(new WeakReference<>(panel));
            refs.add(new WeakReference<>(child));
            if (dispose) {
                panel.dispose();
            }
        }
        return refs;
    }

    private static int countCleared(List<WeakReference<Component>> refs) throws InterruptedException {
        for (int attempt = 0; attempt < 10; attempt++) {
            System.gc();
            Thread.sleep(20);
            int cleared = 0;
            for (WeakReference<Component> ref : refs) {
                if (ref.get() == null) {
                    cleared++;
                }
            }
            if (cleared == refs.size()) {
                return cleared;
            }
        }
        int cleared = 0;
        for (WeakReference<Component> ref : refs) {
            if (ref.get() == null) {
                cleared++;
            }
        }
        return cleared;
    }

    @Test
    public void disposedComponentsLeaveNothingBehindAndAreCollectable() throws InterruptedException {
        Signal<Integer> shared = new Signal<>(0);

        List<WeakReference<Component>> refs = runCycles(shared, true);

        assertEquals(0, shared.observerCount(), "every binding and computed must have unsubscribed");
        assertEquals(refs.size(), countCleared(refs), "disposed components must become garbage");
        // The long-lived signal still works and notifies nobody
        shared.set(1);
    }

    @Test
    public void controlUndisposedComponentsStayRegistered() throws InterruptedException {
        Signal<Integer> shared = new Signal<>(0);

        List<WeakReference<Component>> refs = runCycles(shared, false);

        // Control: without dispose the shared signal keeps every binding (and thus
        // every component) alive. This proves the test above can fail.
        assertEquals(CYCLES * 3, shared.observerCount(),
                "2 bindings + 1 computed subscription per cycle");
        assertTrue(countCleared(refs) < refs.size(), "bindings pin their components");
    }
}
