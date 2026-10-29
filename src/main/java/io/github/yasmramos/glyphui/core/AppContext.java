package io.github.yasmramos.glyphui.core;

/**
 * Explicit per-application context propagated through the component tree by
 * ownership (root panel -&gt; children), replacing the former process-wide
 * {@code Application.getCurrent()} lookup.
 *
 * <h2>Why this exists</h2>
 * <p>A static "current application" pointer models one app per process and
 * silently mis-marshals widgets when two applications coexist (parallel
 * tests, embedding). Every widget that needs to reach application services
 * (UI-thread marshalling, popup layer, window) should resolve them through
 * the context of the tree it belongs to, not through whichever app started
 * last.</p>
 *
 * <h2>Resolution order used by widgets</h2>
 * <ol>
 *   <li>The context captured from the owning tree ({@link
 *       io.github.yasmramos.glyphui.ui.Component#context()}) — authoritative
 *       whenever the widget is attached to a root panel.</li>
 *   <li>{@link #fallback()} — the most recently started live application.
 *       This keeps detached widgets (built before any app exists, or created
 *       in headless tests) working exactly as they did with the old static
 *       getter, but callers should prefer the owned context.</li>
 * </ol>
 *
 * <p>Instances are immutable value wrappers around the owning
 * {@link Application}; obtaining one never allocates.</p>
 */
public final class AppContext {

    /**
     * The single fallback slot while the migration finishes: the most
     * recently successfully-initialized application, cleared on close and
     * on failed init. Identical semantics to the former
     * {@code Application.current} field.
     */
    static volatile Application fallbackApp;

    private final Application app;

    private AppContext(Application app) {
        this.app = app;
    }

    /**
     * Wraps an application instance into a context. Package-private so only
     * the toolkit core can mint contexts; user code receives them from
     * {@code Component.context()} or {@link AppContext#ofCurrent()}.
     *
     * @param app the owning application (non-null)
     * @return a context bound to {@code app}
     */
    static AppContext of(Application app) {
        return new AppContext(app);
    }

    /**
     * Returns a context for the current fallback application, or {@code null}
     * when no application is live (headless/test contexts). Callers that have
     * an attached component should use {@code component.context()} instead —
     * this method exists so pre-existing global lookups could be funneled
     * through one auditable place during the AppContext migration.
     *
     * @return the fallback context, or null
     */
    public static AppContext ofCurrent() {
        Application app = fallbackApp;
        return app == null ? null : new AppContext(app);
    }

    /**
     * The most recently started live application, or {@code null}. Internal
     * accessor behind {@link #ofCurrent()}; kept package-private so new code
     * cannot scatter raw global lookups.
     *
     * @return the fallback application instance, or null
     */
    static Application fallback() {
        return fallbackApp;
    }

    /**
     * The application that owns this context.
     *
     * @return the owning application (never null)
     */
    public Application app() {
        return app;
    }

    /**
     * Convenience: queues {@code task} on the owning application's UI-thread
     * queue (no-op semantics identical to {@link Application#invokeLater}).
     *
     * @param task the task to run on the UI thread
     */
    public void invokeLater(Runnable task) {
        app.invokeLater(task);
    }

    /**
     * Convenience: true when the calling thread is this application's UI
     * thread (or no UI thread has been established yet).
     *
     * @return true if it is safe to mutate the tree right now
     */
    public boolean isUiThread() {
        return app.isUiThread();
    }

    @Override
    public String toString() {
        return "AppContext[" + app + "]";
    }
}
