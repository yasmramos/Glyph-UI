package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.SkijaTestSupport;
import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Panel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Contract tests for {@link Application#close()}: a closed application must
 * leave NO process-wide trace behind. This pins the Phase 1 checklist item
 * "Application.close() leaves current == null, global repaint requester ==
 * null and getGlobalFocusManager() == null".
 *
 * <p>The contract matters because widgets lazily read Component's static
 * repaint hook and {@code FocusManager.getGlobalFocusManager()} to marshal
 * work onto the running loop. If a closed application left either pointer
 * installed, later headless tests (or a second application in the same JVM)
 * would route repaints/focus into dead native state.</p>
 *
 * <p>{@code init()} needs Skija + JWM natives; when they are unavailable on
 * the host the test is skipped via an assumption rather than failing.</p>
 */
class ApplicationCloseContractTest extends TestBase {

    /**
     * Reads Component's package-private global-hook accessor through a tiny
     * reflection seam so this test lives in the core package without adding
     * a public API surface for it (the accessor itself stays non-API).
     */
    private static RepaintRequester globalRepaintHook() {
        try {
            var m = Component.class.getDeclaredMethod("getGlobalRepaintRequester");
            m.setAccessible(true);
            return (RepaintRequester) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("global repaint hook accessor missing", e);
        }
    }

    @Test
    void closeClearsCurrentInstanceGlobalRepaintHookAndGlobalFocusManager() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to initialize an Application");

        Application app = new Application();
        if (!app.init("close-contract-test", 320, 240, true)) {
            // Backend refused to create a window on this host (e.g. no
            // display): nothing to assert, skip instead of failing.
            app.close();
            Assumptions.abort("Application.init() unavailable on this host");
        }

        // A successful init() publishes the instance and installs BOTH
        // process-wide hooks (repaint requester + focus manager).
        assertSame(app, Application.getCurrent(),
                "init() must publish the instance as the current application");
        assertNotNull(FocusManager.getGlobalFocusManager(),
                "init() must install the global focus manager");
        assertNotNull(globalRepaintHook(),
                "init() must install the global repaint hook");

        app.close();

        // The full teardown contract: every static the app installed is gone.
        assertNull(Application.getCurrent(),
                "close() must clear the current-application pointer");
        assertNull(globalRepaintHook(),
                "close() must release the global repaint hook on Component");
        assertNull(FocusManager.getGlobalFocusManager(),
                "close() must detach the global focus manager");
    }

    @Test
    void closeIsIdempotentAndKeepsStaticsCleared() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to initialize an Application");

        Application app = new Application();
        if (!app.init("close-contract-test", 320, 240, true)) {
            app.close();
            Assumptions.abort("Application.init() unavailable on this host");
        }
        app.close();
        // Second close() must not throw and must not resurrect any hook.
        app.close();

        assertNull(Application.getCurrent());
        assertNull(globalRepaintHook());
        assertNull(FocusManager.getGlobalFocusManager());
    }

    @Test
    void closingSecondApplicationDoesNotStealFirstApplicationsHooks() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to initialize an Application");

        Application first = new Application();
        if (!first.init("first-app", 320, 240, true)) {
            first.close();
            Assumptions.abort("Application.init() unavailable on this host");
        }
        Panel firstRoot = first.getRootPanel();

        // Ownership rule documented on setRepaintRequester: close() only
        // clears hooks THIS instance still owns. Re-point the global focus
        // manager away from the first app, then close the first app: the
        // foreign manager must survive (no null-out behind someone else's
        // back), while `current` (identity-guarded) must be released.
        FocusManager foreign = new FocusManager(() -> { });
        FocusManager.setGlobalFocusManager(foreign);

        first.close();

        assertNull(Application.getCurrent(),
                "close() always releases its own current-instance pointer");
        assertSame(foreign, FocusManager.getGlobalFocusManager(),
                "close() must not clear a focus manager it no longer owns");
        assertNotNull(firstRoot, "sanity: reference captured before close");
    }
}
