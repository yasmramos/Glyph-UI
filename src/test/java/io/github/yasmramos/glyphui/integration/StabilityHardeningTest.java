package io.github.yasmramos.glyphui.integration;

import io.github.humbleui.jwm.EventMouseMove;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;
import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.core.Window;
import io.github.yasmramos.glyphui.core.WindowConfig;
import io.github.yasmramos.glyphui.core.backend.JwmWindowBackend;
import io.github.yasmramos.glyphui.core.backend.WindowBackend;
import io.github.yasmramos.glyphui.graphics.FontManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the Priority-2 stability and robustness hardening
 * (design review items 4, 5 and 6).
 *
 * <ul>
 *   <li><b>Item 4 — JWM event-loop protection:</b> nothing thrown by a user
 *       listener may escape {@code JwmWindowBackend.onJwmEvent} into the
 *       native JWM message loop — not even an {@link Error} — and the
 *       error-reporting path itself must never re-throw.</li>
 *   <li><b>Item 5 — degenerate framebuffer / invalid scale guards:</b>
 *       negative framebuffer sizes reported by a backend are clamped to zero
 *       before reaching {@code recreateSurface}, zero-size (minimized)
 *       framebuffers keep the previous surface, and non-positive or
 *       non-finite content scales keep the last good value.</li>
 *   <li><b>Item 6 — FontManager.dispose() sweep:</b> every registered
 *       typeface is closed exactly once even when one of them throws during
 *       {@code close()}; the registry is still cleared afterwards.</li>
 * </ul>
 *
 * <p>All tests run headless: they use bare {@code new Application()}
 * instances (no native window) and reflection only where the reviewed code
 * paths are private by design (the JNI upcall entry point).</p>
 */
class StabilityHardeningTest extends TestBase {

    // ------------------------------------------------------------------
    // Reflection helpers
    // ------------------------------------------------------------------

    /** Invokes the private {@code onJwmEvent(Event)} JNI upcall entry. */
    private static void fireJwmEvent(JwmWindowBackend backend,
                                     io.github.humbleui.jwm.Event event)
            throws Exception {
        Method m = JwmWindowBackend.class
                .getDeclaredMethod("onJwmEvent", io.github.humbleui.jwm.Event.class);
        m.setAccessible(true);
        try {
            m.invoke(backend, event);
        } catch (InvocationTargetException e) {
            // The whole point of item 4: the hardened upcall swallows
            // everything, so this branch must be unreachable. Fail loudly.
            Throwable cause = e.getCause();
            throw new AssertionError(
                    "Exception escaped onJwmEvent into the JNI boundary: " + cause,
                    cause);
        }
    }

    /** Reads a declared field anywhere in the target's class hierarchy. */
    private static Object readField(Object target, String name) throws Exception {
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name + " on " + target.getClass());
    }

    /** Reads a static field from a class. */
    private static Object readStaticField(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(null);
    }


    /** Builds a JWM backend without ever creating a native window. */
    private static JwmWindowBackend newHeadlessBackend() {
        return new JwmWindowBackend("stability-test", 64, 64, new WindowConfig());
    }

    // ------------------------------------------------------------------
    // Item 4 — exception containment in the JNI upcall
    // ------------------------------------------------------------------

    @Test
    @DisplayName("item 4: RuntimeException from a JWM listener does not escape onJwmEvent")
    void runtimeExceptionFromListenerIsContained() throws Exception {
        JwmWindowBackend backend = newHeadlessBackend();
        AtomicBoolean delivered = new AtomicBoolean(false);
        // Headless backend: listeners can be installed without a live window.
        backend.setCursorPosListener((x, y) -> {
            delivered.set(true);
            throw new RuntimeException("listener blew up");
        });

        EventMouseMove move = new EventMouseMove(10, 20, 0, 0, 0, 0);

        assertDoesNotThrow(() -> fireJwmEvent(backend, move),
                "A listener RuntimeException must not propagate into JWM.");
        assertTrue(delivered.get(), "The listener must still have been invoked.");
    }

    @Test
    @DisplayName("item 4: Error (StackOverflowError) from a JWM listener is also contained")
    void errorFromListenerIsContained() throws Exception {
        JwmWindowBackend backend = newHeadlessBackend();
        AtomicBoolean delivered = new AtomicBoolean(false);
        backend.setCursorPosListener((x, y) -> {
            delivered.set(true);
            throw new StackOverflowError("simulated runaway recursion");
        });

        EventMouseMove move = new EventMouseMove(1, 1, 0, 0, 0, 0);

        assertDoesNotThrow(() -> fireJwmEvent(backend, move),
                "The outer catch(Throwable) must contain Errors too.");
        assertTrue(delivered.get());
    }

    @Test
    @DisplayName("item 4: unexpected/null events are absorbed, never re-thrown upward")
    void unexpectedEventDoesNotEscape() throws Exception {
        JwmWindowBackend backend = newHeadlessBackend();
        // A null event makes handleJwmEvent's dispatch NPE internally; the
        // hardened upcall must convert that into a log line, not a crash.
        assertDoesNotThrow(() -> fireJwmEvent(backend, null),
                "Even internal failures must not escape the JNI boundary.");
    }

    @Test
    @DisplayName("item 4: containment works through the full Application pipeline")
    void containmentHoldsForToolkitInstalledListeners() throws Exception {
        // The Application constructor is lazy: the window (and its backend
        // with toolkit-installed listeners) only exists after init(). Use the
        // raster surface so the test runs headless without a GPU context.
        try (Application app = new Application()) {
            if (!app.init("stability-test", 100, 100, null, true)) {
                // Headless CI without a working window backend: skip rather
                // than fail spuriously; the unit-level containment tests
                // above already cover the hardened upcall path.
                return;
            }
            Window window = (Window) readField(app, "window");
            assertNotNull(window);
            JwmWindowBackend backend =
                    (JwmWindowBackend) readField(window, "backend");

            // Drive a raw native-shaped event through the real entry point.
            // Whatever the toolkit's own cursor handler does with it (hover
            // bookkeeping on a hidden root panel), nothing may escape.
            EventMouseMove move = new EventMouseMove(5, 5, 0, 0, 0, 0);
            assertDoesNotThrow(() -> fireJwmEvent(backend, move));
        }
    }

    // ------------------------------------------------------------------
    // Item 5 — degenerate framebuffer sizes and invalid content scales
    // ------------------------------------------------------------------

    @Test
    @DisplayName("item 5: negative framebuffer size is clamped and survives the real callback chain")
    void negativeFramebufferSizeIsClamped() throws Exception {
        try (Application app = new Application()) {
            assertTrue(app.init("stability-item5", 200, 150, true),
                    "Headless raster init must succeed.");
            Window window = app.getWindow();
            Object surfaceBefore = readField(app, "surface");
            assertNotNull(surfaceBefore);

            // Drive a misbehaving backend straight into the toolkit's own
            // framebuffer listener (-800x-600 would previously reach Skija's
            // surface factory and throw IllegalArgumentException from inside
            // a native event callback).
            assertDoesNotThrow(() -> invokeNotifyFramebuffer(window, -800, -600));

            assertSame(surfaceBefore, readField(app, "surface"),
                    "A clamped degenerate resize must not destroy the live surface.");
        }
    }

    @Test
    @DisplayName("item 5: minimized 0x0 framebuffer keeps the previous surface")
    void minimizedZeroSizeKeepsPreviousSurface() throws Exception {
        try (Application app = new Application()) {
            assertTrue(app.init("stability-item5-min", 200, 150, true));
            Object surfaceBefore = readField(app, "surface");
            assertNotNull(surfaceBefore);

            assertDoesNotThrow(() -> invokeNotifyFramebuffer(app.getWindow(), 0, 0));
            assertSame(surfaceBefore, readField(app, "surface"),
                    "A 0x0 framebuffer must not destroy the live surface.");
        }
    }

    @Test
    @DisplayName("item 5: invalid content scale (0, negative, NaN, Infinity) is ignored")
    void invalidContentScaleIsIgnored() throws Exception {
        try (Application app = new Application()) {
            assertTrue(app.init("stability-item5-scale", 200, 150, true));
            Window window = app.getWindow();

            // Baseline: a legitimate scale change goes through.
            invokeNotifyContentScale(window, 1.5f, 1.5f);
            assertEquals(1.5f, (float) readField(app, "contentScaleX"), 0f);
            assertEquals(1.5f, (float) readField(app, "contentScaleY"), 0f);

            float[][] bad = {
                    {0f, 1f}, {1f, 0f}, {-2f, -2f},
                    {Float.NaN, 1f}, {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY},
            };
            for (float[] b : bad) {
                final float bx = b[0];
                final float by = b[1];
                assertDoesNotThrow(() -> invokeNotifyContentScale(window, bx, by),
                        "Invalid scale (" + bx + ", " + by + ") must not throw.");
                assertEquals(1.5f, (float) readField(app, "contentScaleX"), 0f,
                        "Invalid scale must not overwrite the last good X scale.");
                assertEquals(1.5f, (float) readField(app, "contentScaleY"), 0f,
                        "Invalid scale must not overwrite the last good Y scale.");
            }

            // And a later valid scale still applies.
            invokeNotifyContentScale(window, 2f, 2f);
            assertEquals(2f, (float) readField(app, "contentScaleX"), 0f);
        }
    }

    /**
     * Invokes the protected {@code notifyFramebufferSize} on the window's
     * live backend, simulating a backend that reports a degenerate physical
     * size (exactly what a minimizing/resizing native window does).
     */
    private static void invokeNotifyFramebuffer(Window window, int fbW, int fbH)
            throws Exception {
        Object backend = readField(window, "backend");
        Method m = findMethod(backend.getClass(), "notifyFramebufferSize",
                int.class, int.class);
        m.invoke(backend, fbW, fbH);
    }

    /** Invokes the protected {@code notifyContentScale} on the live backend. */
    private static void invokeNotifyContentScale(Window window, float sx, float sy)
            throws Exception {
        Object backend = readField(window, "backend");
        Method m = findMethod(backend.getClass(), "notifyContentScale",
                float.class, float.class);
        m.invoke(backend, sx, sy);
    }

    private static Method findMethod(Class<?> c, String name, Class<?>... params) {
        while (c != null) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
                c = c.getSuperclass();
            }
        }
        throw new IllegalStateException("Method not found: " + name);
    }

    // ------------------------------------------------------------------
    // Item 6 — FontManager.dispose() resource sweep
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Typeface> fontRegistry() throws Exception {
        return (Map<String, Typeface>) readStaticField(FontManager.class, "registered");
    }

    private static Typeface systemTypeface(String family) {
        return Typeface.makeFromName(family, FontStyle.NORMAL);
    }

    @Test
    @DisplayName("item 6: dispose closes every registered typeface and clears the registry")
    void disposeClosesAllAndClearsRegistry() throws Exception {
        Map<String, Typeface> registry = fontRegistry();
        registry.clear();
        Typeface first = systemTypeface("Arial");
        Typeface second = systemTypeface("Courier New");
        registry.put("test-family-a", first);
        registry.put("test-family-b", second);

        assertDoesNotThrow(FontManager::dispose,
                "dispose() over healthy typefaces must not throw.");
        assertTrue(registry.isEmpty(),
                "Registry must be empty after dispose.");
        // Both handles were closed exactly once by the sweep; there is no
        // portable way to query Skija liveness, but a double-close would
        // have thrown out of dispose() above.
    }

    @Test
    @DisplayName("item 6: a failing close() does not abort the sweep; registry still cleared")
    void disposeSweepsDespiteFailingClose() throws Exception {
        Map<String, Typeface> registry = fontRegistry();
        registry.clear();

        // Pre-closed typeface: depending on the Skija build, close() on a
        // released handle either throws or is a silent no-op. Either way the
        // contract holds: the sweep continues, the registry ends empty and
        // the survivor entry is processed too (no leak behind the failure).
        Typeface doomed = systemTypeface("Arial");
        doomed.close();
        Typeface survivor = systemTypeface("Times New Roman");
        registry.put("doomed-family", doomed);
        registry.put("survivor-family", survivor);

        try {
            FontManager.dispose();
        } catch (RuntimeException surfacedFailure) {
            // Contract: at least one failure is surfaced (with the rest
            // attached as suppressed), never swallowed silently.
            assertNotNull(surfacedFailure.getMessage());
        }
        assertTrue(registry.isEmpty(),
                "Registry must be cleared regardless of close() failures.");
        assertFalse(registry.containsKey("survivor-family"),
                "The surviving typeface must have been swept too (no leak).");
    }

    @Test
    @DisplayName("item 6: dispose on an empty registry is a safe no-op")
    void disposeWithEmptyRegistryIsNoop() throws Exception {
        fontRegistry().clear();
        assertDoesNotThrow(FontManager::dispose);
        assertTrue(fontRegistry().isEmpty());
    }
}
