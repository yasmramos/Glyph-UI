package io.github.yasmramos.glyphui.core;


import io.github.yasmramos.glyphui.ui.Component;

/**
 * Owns the repaint-request plumbing of one {@link Application} instance.
 *
 * <p>Extracted from {@code Application} (PR 2, phase 1) so the frame loop and
 * the widget tree talk to a small, dedicated collaborator instead of through
 * scattered private methods. Responsibilities:</p>
 *
 * <ul>
 *   <li>The <b>paint-dirty flag</b>: set by any thread when something changed
 *       (input events, component mutations, resize, posted tasks); consumed
 *       atomically by the frame loop through {@link #consumePaintDirty()}.</li>
 *   <li>The <b>wake-up policy</b>: after marking dirty, decide whether the UI
 *       thread needs an explicit native wake-up ({@code postEmptyEvent}) or is
 *       already going to drain state on its next step.</li>
 *   <li>The <b>process-wide repaint hook ownership</b>: exactly one live
 *       application installs itself as {@code Component}'s global repaint
 *       requester; this class tracks that ownership explicitly because method
 *       references have unstable identity and cannot be compared with
 *       {@code ==}.</li>
 * </ul>
 *
 * <p>Instances are not thread-safe by themselves for the cascade bookkeeping,
 * but the paint flag is {@code volatile} and every mutation goes through the
 * owning application's monitor (the same lock the frame loop uses for its
 * test-and-consume), preserving the historical race-window protection.</p>
 */
final class RepaintScheduler {

    /**
     * Sleep interval used by the raster backend idle wait (no hardware vsync
     * there). Exposed so the frame loop keeps using a single shared constant.
     */
    static final long RASTER_IDLE_SLEEP_MS = 16;

    /**
     * Process-wide repaint hook installed on {@code Component} by the most
     * recently initialized application. Package-visible so unit tests that
     * replace or inspect the hook directly (bypassing an owning application)
     * can still assert against the exact value this class installed and
     * transfer ownership explicitly.
     */
    static RepaintRequester globalHook;

    private final Application app;

    /**
     * On-demand rendering flag: true when something changed (events, component
     * mutations, resize) and the next loop iteration should repaint.
     */
    private volatile boolean paintDirty;

    /**
     * True while this application owns the single static repaint hook
     * ({@code Component.setGlobalRepaintRequester}, installed by init).
     * Tracked explicitly because method references have unstable identity
     * and cannot be compared with {@code ==}. Cleared by release() so a
     * closed instance never lingers through globals, and never cleared over
     * a foreign requester installed after our init (e.g. by a unit test).
     */
    private boolean globalHookOwnedByThis;

    RepaintScheduler(Application app) {
        this.app = app;
        // Matches the historical constructor default: the first frame must
        // always be painted.
        this.paintDirty = true;
    }

    /**
     * Marks the display dirty so the next loop iteration renders a new frame,
     * and wakes up the loop when it may be blocked waiting for events. Safe to
     * call from other threads (e.g. from {@code invokeLater} tasks). The whole
     * operation runs under the owning application's monitor — same lock the
     * frame loop uses for its test-and-consume — preserving the historical
     * atomicity guarantees.
     */
    void requestRepaint() {
        synchronized (app) {
            paintDirty = true;
            // Wake up the loop if it is blocked waiting for events
            Window w = app.windowForScheduler();
            if (w != null && app.runningForScheduler()) {
                w.postEmptyEvent();
            }
        }
    }

    /**
     * Atomically tests and consumes the paint-dirty flag under the owning
     * application's monitor, so a concurrent {@link #requestRepaint()} landing
     * between the check and the render cannot be lost (volatile read + write
     * alone has a race window).
     *
     * @param forcePaintWhenAnimating when true (an animation callback reports
     *        continuous animation), the flag is treated as dirty even if no
     *        explicit request arrived, and is cleared so the next call must
     *        re-arm it; matches the historical frameStep test-and-consume
     * @return true when a repaint must happen this iteration
     */
    boolean consumePaintDirty(boolean animating) {
        synchronized (app) {
            boolean dirty = (paintDirty || animating) && !app.framebufferDegenerateForScheduler();
            if (dirty) {
                paintDirty = false;
            }
            return dirty;
        }
    }

    /**
     * Reads the paint-dirty state without consuming it (mainly for tests).
     *
     * @return true when a repaint has been requested but not yet performed
     */
    boolean isPaintDirty() {
        return paintDirty;
    }

    /**
     * Sets the paint-dirty flag directly, without any wake-up. Test-only
     * state wiring used through {@code Application.setPaintDirtyForTests};
     * production code must go through {@link #requestRepaint()} so the loop
     * is woken up together with the flag.
     *
     * @param value the new dirty state
     */
    void setPaintDirty(boolean value) {
        synchronized (app) {
            paintDirty = value;
        }
    }

    /**
     * Wakes up the UI thread after a task was posted from another thread.
     * When the caller is already on the UI thread the queue is drained at the
     * end of the current frame step, so no native wake-up is needed; before
     * the window backend exists the queue drains on the first loop iteration
     * anyway.
     */
    void wakeUpForPostedTask() {
        if (app.isUiThread()) {
            // Already on the UI thread: the queue is drained at the end of
            // the current frameStep(), so no native wake-up is needed.
            return;
        }
        // Wake the UI thread if it is blocked waiting for events. Only valid
        // once the window backend is initialized; before that the queue is
        // drained on the first run() iteration anyway.
        Window w = app.windowForScheduler();
        if (w != null) {
            w.postEmptyEvent();
        }
    }

    /**
     * Installs this application as the process-wide repaint requester invoked
     * by {@code Component.invalidate()} at the tree root. Called once from
     * {@code Application.init()}; ownership is recorded so {@link #release()}
     * only clears the hook when it still belongs to us. The hook value is
     * also mirrored into {@link #globalHook} so tests and re-pointing setters
     * can compare against the exact installed reference.
     */
    void installGlobalHook() {
        RepaintRequester hook = app::requestRepaint;
        Component.setGlobalRepaintRequester(hook);
        globalHook = hook;
        globalHookOwnedByThis = true;
    }

    /**
     * Claims ownership of the process-wide hook after an external re-pointing
     * through {@code Application.setRepaintRequester(...)}: whoever routes a
     * non-null requester through the owning application takes responsibility
     * for clearing it on close. A null requester releases our ownership.
     *
     * @param owned true when this instance now owns the static hook
     */
    void setHookOwnership(boolean owned) {
        globalHookOwnedByThis = owned;
    }

    /**
     * Clears the global repaint hook, but only if this instance still owns
     * it — a foreign requester installed later (e.g. by another application
     * or a unit test) must survive our close().
     */
    void release() {
        if (globalHookOwnedByThis) {
            Component.setGlobalRepaintRequester((RepaintRequester) null);
            globalHook = null;
            globalHookOwnedByThis = false;
        }
    }
}
