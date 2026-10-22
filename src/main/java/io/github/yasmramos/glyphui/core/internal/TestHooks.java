package io.github.yasmramos.glyphui.core.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Internal test/diagnostics hooks for the toolkit.
 *
 * <p>Per {@code docs/architecture/internal-boundary.md}, instrumentation that
 * is not part of the application-facing API lives under an {@code internal}
 * package. This class centralizes every test-only shim so production classes
 * ({@code Application}, {@code Component}) expose a clean public surface:</p>
 *
 * <ul>
 *   <li>{@link #getInvalidateCount(Object)} /
 *       {@link #resetInvalidateCount(Object)} — widget repaint-counter
 *       instrumentation (argument: a {@code io.github.yasmramos.glyphui.ui.Component}).</li>
 *   <li>{@link #renderFrame(Object)} — render one frame without entering the
 *       event loop (argument: a {@code io.github.yasmramos.glyphui.core.Application}).</li>
 *   <li>{@link #isPaintDirty(Object)} — inspect the paint-dirty flag.</li>
 *   <li>{@link #recreateRasterSurfaceForTesting(Object)} — force the raster
 *       surface recreation path.</li>
 *   <li>{@link #drainUiTasksForTests(Object)} — run queued UI tasks and
 *       return how many executed.</li>
 *   <li>{@link #disposeStyleFonts()} — release the process-wide style-font
 *       cache (JVM-shutdown/test isolation cleanup).</li>
 * </ul>
 *
 * <p>All entry points are reflective by design: the backing methods became
 * package-private when the public API was frozen (see
 * {@code docs/architecture/api-stability.md}), and this package must not
 * create compile-time dependency cycles back into {@code core} or
 * {@code ui}. Reflection here is confined to in-repo test code paths;
 * drift surfaces as {@link IllegalStateException} instead of being
 * swallowed.</p>
 *
 * <p><strong>Not public API:</strong> may change or disappear in any release,
 * including patch releases.</p>
 */
public final class TestHooks {

    private TestHooks() {
    } // utility class

    // ------------------------------------------------------------------
    // Component instrumentation
    // ------------------------------------------------------------------

    /**
     * Returns how many times the component has been invalidated. Intended
     * exclusively for tests and diagnostics.
     *
     * @param component a {@code io.github.yasmramos.glyphui.ui.Component} instance
     * @return the invalidate count
     */
    public static int getInvalidateCount(Object component) {
        return (int) invoke(component, "getInvalidateCount");
    }

    /**
     * Resets the component's invalidate counter to zero. Intended
     * exclusively for tests and diagnostics.
     *
     * @param component a {@code io.github.yasmramos.glyphui.ui.Component} instance
     */
    public static void resetInvalidateCount(Object component) {
        invoke(component, "resetInvalidateCount");
    }

    // ------------------------------------------------------------------
    // Application instrumentation
    // ------------------------------------------------------------------

    /**
     * Renders a single frame on the given application without entering the
     * event loop. Must be called on the application's UI thread (or before
     * {@code run()} starts). Intended exclusively for tests.
     *
     * @param application a {@code io.github.yasmramos.glyphui.core.Application} instance
     */
    public static void renderFrame(Object application) {
        invoke(application, "renderFrame");
    }

    /**
     * Returns the application's current paint-dirty state. Intended
     * exclusively for tests and diagnostics.
     *
     * @param application a {@code io.github.yasmramos.glyphui.core.Application} instance
     * @return true when a repaint has been requested but not yet performed
     */
    public static boolean isPaintDirty(Object application) {
        return (boolean) invoke(application, "isPaintDirty");
    }

    /**
     * Forces the application down its raster-surface recreation path.
     * Intended exclusively for tests (HiDPI re-creation scenarios).
     *
     * @param application a {@code io.github.yasmramos.glyphui.core.Application} instance
     */
    public static void recreateRasterSurfaceForTesting(Object application) {
        invoke(application, "recreateRasterSurfaceForTesting");
    }

    /**
     * Drains the application's queued UI-task queue on the calling thread
     * and returns how many tasks executed. Intended exclusively for tests.
     *
     * @param application a {@code io.github.yasmramos.glyphui.core.Application} instance
     * @return the number of tasks executed
     */
    public static int drainUiTasksForTests(Object application) {
        return (int) invoke(application, "drainUiTasksForTests");
    }

    // ------------------------------------------------------------------
    // Style-font cache
    // ------------------------------------------------------------------

    /**
     * Releases every cached style-derived font held by the global
     * {@code Component.STYLE_FONTS} cache. See
     * {@code docs/architecture/font-resource-model.md} for the ownership
     * contract of that cache. Call from JVM-shutdown/test-isolation paths
     * only; safe no-op if the cache is already empty.
     */
    public static void disposeStyleFonts() {
        try {
            Class<?> component = Class.forName("io.github.yasmramos.glyphui.ui.Component");
            Method m = component.getDeclaredMethod("disposeStyleFonts");
            m.setAccessible(true);
            m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Unable to reach Component.disposeStyleFonts()", e);
        }
    }

    // ------------------------------------------------------------------
    // Reflection helper
    // ------------------------------------------------------------------

    private static Object invoke(Object target, String methodName) {
        // Walk the class hierarchy: instrumentation hooks live on shared
        // base classes (e.g. Component.getInvalidateCount), while tests
        // often pass concrete or locally-declared subclasses whose own
        // getDeclaredMethod would not see inherited package-private members.
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(methodName);
                m.setAccessible(true);
                return m.invoke(target);
            } catch (NoSuchMethodException e) {
                // keep walking up
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(
                        "Test hook '" + methodName + "' not accessible", e);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException) cause;
                }
                if (cause instanceof Error) {
                    throw (Error) cause;
                }
                throw new IllegalStateException(
                        "Test hook '" + methodName + "' failed", cause);
            }
        }
        throw new IllegalStateException(
                "Test hook '" + methodName + "' is missing on "
                        + target.getClass().getName()
                        + "; internal shim drifted from TestHooks");
    }
}
