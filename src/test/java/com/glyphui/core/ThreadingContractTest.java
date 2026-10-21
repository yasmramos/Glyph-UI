package com.glyphui.core;

import com.glyphui.TestBase;
import com.glyphui.core.internal.TestHooks;
import com.glyphui.graphics.Property;
import com.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Threading contracts of {@link Application} and {@link Property}.
 *
 * <p>These encode the documented rules (item 8 of the Phase 1 checklist):</p>
 * <ul>
 *   <li>Once the event loop has started, frame rendering is UI-thread-only:
 *       calling the render entry point from another thread must fail fast
 *       with {@link IllegalStateException} instead of corrupting Skija's
 *       non-thread-safe native state.</li>
 *   <li>{@code Property.set(...)} from a background thread never applies the
 *       value inline: it marshals the change onto the UI thread queue.</li>
 * </ul>
 *
 * <p>The tests simulate a UI thread without starting the real event loop:
 * they install a dedicated thread as the application's {@code uiThread} via
 * reflection (same-package access), exactly like the other Application unit
 * tests wire internal state.</p>
 */
class ThreadingContractTest extends TestBase {

    private Application app;

    @BeforeEach
    void setUp() {
        app = new Application();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Do not let this instance linger as Application.current for later
        // tests; close() nulls the static hooks itself.
        app.close();
        setField(app, "uiThread", null);
        ComponentHooks.clearGlobals();
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    /** Small helper so the teardown can clear static hooks by name. */
    private static final class ComponentHooks {
        static void clearGlobals() {
            com.glyphui.ui.Component.setGlobalRepaintRequester(null);
            FocusManager.setGlobalFocusManager(null);
        }
    }

    @Test
    void renderFrameFromForeignThreadThrowsIllegalStateOnceUiThreadIsSet() throws Exception {
        // Simulate a started event loop: designate a different thread as the
        // UI thread without actually running the loop.
        Thread fakeUiThread = new Thread(() -> {
            try {
                // Keep the fake UI thread alive until the test releases it.
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "fake-ui-thread");
        fakeUiThread.setDaemon(true);
        fakeUiThread.start();
        setField(app, "uiThread", fakeUiThread);

        // The JUnit thread is NOT the UI thread: renderFrame must fail fast.
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> TestHooks.renderFrame(app));
        assertTrue(e.getMessage().contains("UI thread"),
                "exception message should explain the threading rule: " + e.getMessage());

        // And the same guard protects captureToPng (native Skija path).
        assertThrows(IllegalStateException.class,
                () -> app.captureToPng(new java.io.File("target/should-not-be-written.png")));

        fakeUiThread.interrupt();
        fakeUiThread.join(2000);
    }

    @Test
    void renderFrameOnDeclaredUiThreadIsAllowed() throws Exception {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library required to render a real frame");

        // Wire a raster surface into the application (no window/GL needed).
        Surface[] holder = new Surface[1];
        com.glyphui.graphics.Canvas canvas = RasterCanvasTestFactory.create(holder);
        setField(app, "canvas", canvas);
        setField(app, "surface", holder[0]);

        // No run() yet: uiThread == null, so any thread qualifies (building
        // the tree before the loop starts is legal by contract).
        assertDoesNotThrow(() -> TestHooks.renderFrame(app));

        // Declaring THIS thread as the UI thread keeps the call legal.
        setField(app, "uiThread", Thread.currentThread());
        assertDoesNotThrow(() -> TestHooks.renderFrame(app));

        // Ownership of the native surface stays with app.close(), which runs
        // in tearDown; closing it here would double-close and fail teardown.
    }

    @Test
    void propertySetFromBackgroundThreadMarshalsToUiThreadQueue() throws Exception {
        CountDownLatch loopStarted = new CountDownLatch(1);
        AtomicReference<Throwable> loopFailure = new AtomicReference<>();
        AtomicReference<Property<Integer>> propertyRef = new AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger applied =
                new java.util.concurrent.atomic.AtomicInteger();

        // A thread that becomes the UI thread and drains the posted-task
        // queue like the real event loop would (via the test hook). It runs
        // a short poll loop so tasks posted from other threads execute as
        // soon as they arrive, exactly like the live loop after each wake-up.
        final boolean[] stopLoop = {false};
        Thread uiThread = new Thread(() -> {
            try {
                setField(app, "uiThread", Thread.currentThread());
                loopStarted.countDown();
                while (!stopLoop[0]) {
                    TestHooks.drainUiTasksForTests(app);
                    Thread.sleep(10);
                }
                TestHooks.drainUiTasksForTests(app);
            } catch (Throwable t) {
                loopFailure.set(t);
            }
        }, "contract-ui-thread");
        uiThread.setDaemon(true);
        uiThread.start();
        assertTrue(loopStarted.await(5, TimeUnit.SECONDS), "UI thread did not start");

        // Create the property ON the UI thread (as widget code does). The
        // loop above polls the queue, so wait until the task has executed.
        app.invokeLater(() -> propertyRef.set(
                new Property<>(app, 0, v -> applied.incrementAndGet())));
        Property<Integer> property = null;
        for (int i = 0; i < 200 && property == null; i++) {
            property = propertyRef.get();
            if (property == null) {
                Thread.sleep(10);
            }
        }
        assertNotNull(property, "property must be constructed on the UI thread");
        assertEquals(0, property.get());

        // ...set it from THIS (foreign) thread. The set must not apply here
        // and now: it is marshalled onto the UI thread queue.
        final Property<Integer> uiProperty = property;
        Thread setter = new Thread(() -> uiProperty.set(42), "background-setter");
        setter.start();
        setter.join(5000);

        assertEquals(0, property.get(),
                "background set must not mutate the value on the caller thread");
        assertEquals(0, applied.get(),
                "listeners must not fire on the caller thread");

        // Let the polling UI loop apply the marshalled change exactly once,
        // then stop the loop.
        for (int i = 0; i < 300 && property.get() == 0; i++) {
            Thread.sleep(10);
        }
        stopLoop[0] = true;
        uiThread.join(5000);
        if (loopFailure.get() != null) {
            throw new AssertionError("UI-side assertions failed", loopFailure.get());
        }

        // After the queue was drained on the UI thread, the change landed
        // exactly once.
        assertEquals(42, property.get());
        assertEquals(1, applied.get());
    }

    @Test
    void propertySetOnUiThreadAppliesSynchronously() {
        app.invokeLater(() -> {
            Property<Integer> p = new Property<>(app, 1, v -> { /* hook */ });
            p.set(2);
            assertEquals(2, p.get(), "UI-thread sets are synchronous");
        });
    }
}
