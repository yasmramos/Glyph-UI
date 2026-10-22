package io.github.yasmramos.glyphui;

import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.graphics.FontManager;
import io.github.yasmramos.glyphui.ui.Component;
import org.junit.jupiter.api.AfterEach;

/**
 * Common base class for all Glyph UI tests.
 *
 * <p>Glyph UI keeps a small amount of process-wide mutable state that is
 * normally owned by {@link Application}: the global repaint hook on
 * {@link Component}, the global {@link FocusManager}, and the current
 * application instance. When several test classes run in the same JVM, state
 * leaked from one test can silently change the behaviour of the next (for
 * example, a stale repaint counter inflating invalidation counts, or an old
 * focus manager swallowing {@code requestFocus()} calls).</p>
 *
 * <p>Extending {@code TestBase} guarantees a clean slate after every test:</p>
 * <ul>
 *   <li>{@code Component.setGlobalRepaintRequester(null)} — removes the
 *       global repaint hook (the legacy per-component
 *       {@code setRepaintRequester} hook was removed from the API; this is
 *       its replacement).</li>
 *   <li>{@code FocusManager.setGlobalFocusManager(null)} — detaches the
 *       global focus manager.</li>
 *   <li>{@code Application.getCurrent().close()} — closes the current
 *       application if a test started one without try-with-resources
 *       ({@link Application#close()} clears the static hooks itself).</li>
 *   <li>{@code FontManager.clearCaches()} — releases cached native Skija
 *       fonts so no dangling {@code Font} handles leak across tests.</li>
 * </ul>
 */
public abstract class TestBase {

    /**
     * Resets all process-wide toolkit state touched by tests. Runs after
     * every test method, even if the test failed or threw.
     */
    @AfterEach
    public void resetGlobalToolkitState() {
        // Close any application a test forgot to close. close() is
        // idempotent and also nulls Component/FocusManager globals it owns,
        // but we clear them unconditionally below as well so tests that
        // installed hooks manually are cleaned up too.
        Application app = Application.getCurrent();
        if (app != null) {
            try {
                app.close();
            } catch (Exception | Error e) {
                // Best-effort cleanup: never mask the actual test failure.
                System.err.println("TestBase: error closing Application: " + e);
            }
        }

        // Clear the global repaint hook (and, historically, the removed
        // per-component hook). Must happen after Application.close() since
        // close() may re-null things in dependency order.
        Component.setGlobalRepaintRequester(null);

        // Detach the global focus manager.
        FocusManager.setGlobalFocusManager(null);

        // Release cached native fonts (Skija handles) created during the
        // test run. Safe no-op when Skija was never initialized... except
        // clearCaches touches Font objects only if they exist, so guard it.
        try {
            FontManager.clearCaches();
        } catch (Throwable t) {
            // Skija not available on this host: nothing was cached, ignore.
        }
    }
}
