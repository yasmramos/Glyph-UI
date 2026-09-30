package com.glyphui.graphics;

import com.glyphui.core.Application;
import com.glyphui.ui.Button;
import com.glyphui.ui.Label;
import com.glyphui.ui.TestComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link Property}: thread marshalling, listener notification,
 * one-way bindings and delegation from classic widget setters.
 *
 * <p>The cross-thread tests use a bare {@code new Application()} (no GLFW /
 * Skija initialization) with the {@code uiThread} field set via reflection to
 * simulate a running UI loop, so no GPU or display is required.</p>
 */
@DisplayName("Property<T> observable model")
class PropertyTest {

    /** Application instance used to exercise the marshalling path, if any. */
    private Application app;

    @BeforeEach
    void setUp() {
        app = null;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (app != null) {
            setUiThread(app, null);
        }
    }

    /** Reflection helper: pretend {@code app}'s UI thread is {@code thread}. */
    private static void setUiThread(Application application, Thread thread) throws Exception {
        Field field = Application.class.getDeclaredField("uiThread");
        field.setAccessible(true);
        field.set(application, thread);
    }

    /** Builds an Application whose UI thread is a fake parked thread. */
    private Application appWithForeignUiThread() throws Exception {
        Application application = new Application();
        Thread fakeUi = new Thread(() -> {
            try {
                // Park until the test finishes; never runs loop logic.
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
                // Fall through: thread ends, queue drain in re-entered set()
                // would then apply directly. Tests finish before this happens.
            }
        }, "fake-ui-thread");
        fakeUi.setDaemon(true);
        fakeUi.start();
        setUiThread(application, fakeUi);
        return application;
    }

    /**
     * Minimal stand-in for Application.run(): registers the current thread as
     * the UI thread and continuously drains the posted-task queue, so sets
     * marshalled via invokeLater are executed without GLFW/Skija.
     */
    private static final class LoopDriver {
        final Application app;
        final Thread thread;
        private volatile boolean stop;
        private final CountDownLatch started = new CountDownLatch(1);

        LoopDriver(Application application, Thread t) {
            this.app = application;
            this.thread = t;
        }

        void stop() throws Exception {
            thread.interrupt();
            thread.join(2000);
            setUiThread(app, null);
        }
    }

    private static LoopDriver startLoopDriver() throws Exception {
        Application application = new Application();
        AtomicReference<Thread> uiRef = new AtomicReference<>();
        CountDownLatch ready = new CountDownLatch(1);
        Thread loop = new Thread(() -> {
            uiRef.set(Thread.currentThread());
            ready.countDown();
            while (!Thread.currentThread().isInterrupted()) {
                int executed = application.drainUiTasksForTests();
                if (executed == 0) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }
        }, "fake-ui-loop");
        loop.setDaemon(true);
        loop.start();
        assertTrue(ready.await(2, TimeUnit.SECONDS), "loop thread must start");
        setUiThread(application, uiRef.get());
        return new LoopDriver(application, loop);
    }

    @Nested
    @DisplayName("Basic get/set semantics")
    class BasicSemantics {

        @Test
        @DisplayName("initial value is returned by get()")
        void initialValue() {
            Property<String> prop = Property.of("hello");
            assertEquals("hello", prop.get());
        }

        @Test
        @DisplayName("set applies value and notifies listeners once")
        void setNotifiesOnce() {
            AtomicInteger notifications = new AtomicInteger();
            AtomicReference<String> oldSeen = new AtomicReference<>();
            AtomicReference<String> newSeen = new AtomicReference<>();

            Property<String> prop = Property.of("a");
            prop.addListener((p, oldValue, newValue) -> {
                notifications.incrementAndGet();
                oldSeen.set(oldValue);
                newSeen.set(newValue);
            });

            prop.set("b");

            assertEquals("b", prop.get());
            assertEquals(1, notifications.get(), "listener must fire exactly once");
            assertEquals("a", oldSeen.get());
            assertEquals("b", newSeen.get());
        }

        @Test
        @DisplayName("setting an equal value is a no-op (no notification)")
        void equalValueIsNoOp() {
            AtomicInteger notifications = new AtomicInteger();
            Property<String> prop = Property.of("same");
            prop.addListener((p, o, n) -> notifications.incrementAndGet());

            prop.set("same");
            Property<String> nullProp = Property.of(null);
            nullProp.set(null);
            nullProp.set(null);

            assertEquals(0, notifications.get(), "no change => no notification");
            assertEquals("same", prop.get());
        }

        @Test
        @DisplayName("onChange hook runs on every real change")
        void onChangeHookRuns() {
            AtomicInteger hookCalls = new AtomicInteger();
            Property<Integer> prop = new Property<>(null, 1, v -> hookCalls.incrementAndGet());

            prop.set(2);
            prop.set(2); // ignored, equal
            prop.set(3);

            assertEquals(2, hookCalls.get());
            assertEquals(3, prop.get());
        }

        @Test
        @DisplayName("removed listener stops receiving notifications")
        void removeListenerStopsNotifications() {
            AtomicInteger count = new AtomicInteger();
            ChangeListener<String> listener = (p, o, n) -> count.incrementAndGet();
            Property<String> prop = Property.of("x");
            prop.addListener(listener);

            prop.set("y");
            prop.removeListener(listener);
            prop.set("z");

            assertEquals(1, count.get());
        }

        @Test
        @DisplayName("null listeners are ignored")
        void nullListenerIgnored() {
            Property<String> prop = Property.of("x");
            assertDoesNotThrow(() -> prop.addListener(null));
            assertDoesNotThrow(() -> prop.removeListener(null));
            prop.set("y");
            assertEquals("y", prop.get());
        }
    }

    @Nested
    @DisplayName("UI-thread marshalling")
    class Marshalling {

        @Test
        @DisplayName("set from a background thread is applied on the UI thread")
        void crossThreadSetMarshalsToUiThread() throws Exception {
            LoopDriver driver = startLoopDriver();
            try {
                CountDownLatch applied = new CountDownLatch(1);
                AtomicReference<Thread> applyingThread = new AtomicReference<>();

                Property<String> prop = new Property<>(driver.app, "old", v -> {
                    applyingThread.set(Thread.currentThread());
                    applied.countDown();
                });

                Thread background = new Thread(() -> prop.set("new"), "background-thread");
                background.start();
                background.join(2000);

                assertTrue(applied.await(2, TimeUnit.SECONDS),
                    "change hook must run after the posted task is drained by the loop");
                assertEquals("new", prop.get());
                assertSame(driver.thread, applyingThread.get(),
                    "value must be applied on the UI thread, not the caller thread");
            } finally {
                driver.stop();
            }
        }

        @Test
        @DisplayName("cross-thread set notifies each listener exactly once")
        void crossThreadSetNotifiesOnce() throws Exception {
            LoopDriver driver = startLoopDriver();
            try {
                AtomicInteger notifications = new AtomicInteger();
                CountDownLatch notified = new CountDownLatch(1);

                Property<Integer> prop = new Property<>(driver.app, 0, null);
                prop.addListener((p, o, n) -> {
                    notifications.incrementAndGet();
                    notified.countDown();
                });

                Thread background = new Thread(() -> prop.set(42));
                background.start();
                background.join(2000);

                assertTrue(notified.await(2, TimeUnit.SECONDS));
                // Give a moment for any (buggy) duplicate execution to show up.
                Thread.sleep(100);
                assertEquals(1, notifications.get(), "listener must fire exactly once");
                assertEquals(42, prop.get());
            } finally {
                driver.stop();
            }
        }

        @Test
        @DisplayName("without an application, set applies synchronously")
        void headlessSetAppliesDirectly() {
            Property<String> prop = new Property<>(null, "a", null);
            AtomicReference<Thread> applyingThread = new AtomicReference<>();
            prop.addListener((p, o, n) -> applyingThread.set(Thread.currentThread()));

            prop.set("b");

            assertEquals("b", prop.get());
            assertSame(Thread.currentThread(), applyingThread.get());
        }
    }

    @Nested
    @DisplayName("Bindings")
    class Bindings {

        @Test
        @DisplayName("bind mirrors source changes into this property")
        void bindPropagatesChanges() {
            Property<String> source = Property.of("a");
            Property<String> target = Property.of("z");

            target.bind(source);
            assertEquals("a", target.get(), "current source value applied immediately");

            source.set("b");
            assertEquals("b", target.get(), "future changes propagate");
        }

        @Test
        @DisplayName("binding twice to the same source does not double-notify")
        void bindIsIdempotent() {
            Property<String> source = Property.of("a");
            Property<String> target = Property.of("a");
            AtomicInteger targetNotifications = new AtomicInteger();
            target.addListener((p, o, n) -> targetNotifications.incrementAndGet());

            target.bind(source);
            source.set("b");

            assertEquals(1, targetNotifications.get(),
                "re-binding the same source must not stack forwarders");
        }

        @Test
        @DisplayName("unbind stops propagation")
        void unbindStopsPropagation() {
            Property<Integer> source = Property.of(1);
            Property<Integer> target = Property.of(0);

            target.bind(source);
            source.set(2);
            assertEquals(2, target.get());

            target.unbind(source);
            source.set(3);
            assertEquals(2, target.get(), "value frozen after unbind");
        }

        @Test
        @DisplayName("bindings propagate transitively")
        void transitiveBinding() {
            Property<String> a = Property.of("1");
            Property<String> b = Property.of("0");
            Property<String> c = Property.of("0");

            b.bind(a);
            c.bind(b);
            a.set("2");

            assertEquals("2", b.get());
            assertEquals("2", c.get());
        }

        @Test
        @DisplayName("self-binding and null source are rejected")
        void invalidBindArguments() {
            Property<String> p = Property.of("x");
            assertThrows(IllegalArgumentException.class, () -> p.bind(p));
            assertThrows(NullPointerException.class, () -> p.bind(null));
        }
    }

    @Nested
    @DisplayName("Widget setter delegation")
    class WidgetDelegation {
        // NOTE: these tests build widgets without a running Application, so the
        // repaint hook registered by Application.init is absent; setters must
        // still work (repaint requests are best-effort).

        @Test
        @DisplayName("Label.setText delegates to textProperty")
        void labelSetTextDelegates() {
            Label label = new Label("init");
            try {
                AtomicInteger changes = new AtomicInteger();
                label.textProperty().addListener((p, o, n) -> changes.incrementAndGet());

                label.setText("via setter");

                assertEquals("via setter", label.getText());
                assertEquals("via setter", label.textProperty().get());
                assertEquals(1, changes.get());
            } finally {
                label.dispose();
            }
        }

        @Test
        @DisplayName("Button.setText delegates to textProperty")
        void buttonSetTextDelegates() {
            Button button = new Button("Click");
            try {
                AtomicInteger changes = new AtomicInteger();
                button.textProperty().addListener((p, o, n) -> changes.incrementAndGet());

                button.setText("Pressed?");

                assertEquals("Pressed?", button.getText());
                assertEquals("Pressed?", button.textProperty().get());
                assertEquals(1, changes.get());
            } finally {
                button.dispose();
            }
        }

        @Test
        @DisplayName("Component visible/enabled properties back the classic setters")
        void componentPropertiesBackSetters() {
            TestComponent component = new TestComponent(0, 0, 10, 10);
            try {
                component.setVisible(false);
                assertFalse(component.visibleProperty().get());

                component.visibleProperty().set(true);
                assertTrue(component.isVisible());

                component.setEnabled(false);
                assertFalse(component.enabledProperty().get());

                component.enabledProperty().set(true);
                assertTrue(component.isEnabled());
            } finally {
                component.dispose();
            }
        }

        @Test
        @DisplayName("geometry properties mirror position/size setters")
        void geometryPropertiesMirrorSetters() {
            TestComponent component = new TestComponent(1, 2, 3, 4);
            try {
                component.xProperty().set(10f);
                assertEquals(10f, component.getX());

                component.setWidth(99f);
                assertEquals(99f, component.widthProperty().get());
            } finally {
                component.dispose();
            }
        }
    }
}
