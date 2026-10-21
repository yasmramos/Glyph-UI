package com.glyphui.core.internal;

import com.glyphui.ui.Component;

/**
 * Internal test/diagnostics hooks for the widget layer.
 *
 * <p>Per {@code docs/architecture/internal-boundary.md}, instrumentation that
 * is not part of the application-facing API lives under an {@code internal}
 * package. This class centralizes counter inspection/reset helpers so tests
 * do not depend on shim methods scattered across production widgets.</p>
 *
 * <p><strong>Not public API:</strong> may change or disappear in any release,
 * including patch releases.</p>
 */
public final class TestHooks {

    private TestHooks() {
    } // utility class

    /**
     * Returns how many times the component has been invalidated. Intended
     * exclusively for tests and diagnostics.
     *
     * @param component the component to inspect (not null)
     * @return the invalidate count
     */
    public static int getInvalidateCount(Component component) {
        return component.getInvalidateCount();
    }

    /**
     * Resets the component's invalidate counter to zero. Intended
     * exclusively for tests and diagnostics.
     *
     * @param component the component to reset (not null)
     */
    public static void resetInvalidateCount(Component component) {
        component.resetInvalidateCount();
    }
}
