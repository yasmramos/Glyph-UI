package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.SkijaTestSupport;
import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Panel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Contracts of {@link AppContext}: the per-application context propagated
 * through the component tree by ownership, replacing the former process-wide
 * {@code Application.getCurrent()} pointer for widget-side service lookups.
 *
 * <p>Encoded rules:</p>
 * <ul>
 *   <li>A successful {@code init()} installs a context on the root panel that
 *       resolves back to that exact application instance.</li>
 *   <li>Every descendant resolves its context through the parent chain
 *       ({@code Component.context()}), so two live applications never share
 *       a marshalling target even when they start in sequence.</li>
 *   <li>Children added after the root was bound inherit the context at add
 *       time (ownership propagation).</li>
 *   <li>{@code close()} clears both the owned context and the global fallback
 *       slot, leaving no stale pointer behind.</li>
 * </ul>
 */
class AppContextContractTest extends TestBase {

    private Application app1;
    private Application app2;

    @AfterEach
    void tearDown() {
        if (app2 != null) {
            app2.close();
        }
        if (app1 != null) {
            app1.close();
        }
    }

    /**
     * Creates an application with a fresh root panel and runs a headless-safe
     * {@code init}. When Skija natives or the window backend are unavailable
     * on this host, the test aborts via assumption instead of failing.
     */
    private Application initApp() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to initialize an Application");
        Application app = new Application();
        if (!app.init("app-context-contract-test", 320, 240, true)) {
            app.close();
            Assumptions.abort("Application.init() unavailable on this host");
        }
        return app;
    }

    @Test
    void initInstallsOwnedContextOnRootPanel() {
        app1 = initApp();
        Panel root = app1.getRootPanel();

        AppContext ctx = root.getContext();
        assertNotNull(ctx, "init() must bind an AppContext to the root panel");
        assertSame(app1, ctx.app(), "root context must resolve to the app that owns it");
    }

    @Test
    void descendantsResolveContextThroughParentChain() {
        app1 = initApp();
        Panel root = app1.getRootPanel();
        Panel nested = new Panel();
        Button button = new Button("Click");
        nested.add(button);
        root.add(nested);

        assertSame(app1, button.context().app(),
                "deep descendants must resolve the owning app via the parent chain");
        assertSame(app1, nested.context().app());
    }

    @Test
    void childrenAddedAfterInitInheritContext() {
        app1 = initApp();
        Panel root = app1.getRootPanel();

        Panel late = new Panel();
        Button lateButton = new Button("Late");
        late.add(lateButton);
        root.add(late);

        assertNotNull(late.getContext(), "panels added post-init inherit the owner context");
        assertSame(app1, lateButton.context().app());
    }

    @Test
    void secondApplicationDoesNotRebindFirstTree() {
        app1 = initApp();
        Button button1 = new Button("One");
        app1.getRootPanel().add(button1);

        app2 = initApp();
        Button button2 = new Button("Two");
        app2.getRootPanel().add(button2);

        // The global fallback now points at app2 (last started wins), but the
        // owned-context resolution must still route each tree to its own app.
        assertSame(app1, button1.context().app(),
                "tree 1 must keep resolving to app 1 despite app 2 starting later");
        assertSame(app2, button2.context().app());
        assertSame(app2, AppContext.fallback(),
                "fallback slot holds the most recently initialized app");
    }

    @Test
    void closeClearsOwnedContextAndFallback() {
        app1 = initApp();
        Panel root = app1.getRootPanel();
        Button button = new Button("Bye");
        root.add(button);
        assertNotNull(button.context());

        app1.close();
        app1 = null; // tearDown must not close twice

        assertNull(root.getContext(), "close() must unbind the owned context");
        assertNull(button.context(), "detached-of-owner widgets resolve nothing after close");
        assertNull(AppContext.fallback(), "close() must clear the global fallback slot");
    }
}
