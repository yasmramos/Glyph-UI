package io.github.yasmramos.glyphui.core;

/**
 * Drives the per-frame iteration of one {@link Application} instance.
 *
 * <p>Extracted from {@code Application} (PR 2, phase 2) so the frame-loop
 * policy lives in a small, dedicated collaborator instead of inline in the
 * application facade. Responsibilities:</p>
 *
 * <ul>
 *   <li><b>Backend loop selection</b>: when the backend owns the native event
 *       loop (JWM), hand it the frame body and block until shutdown;
 *       otherwise run the toolkit's polling loop.</li>
 *   <li><b>Idle strategy</b>: after a clean (non-painting) frame, either sleep
 *       a short interval (raster backend, no hardware vsync) or block in
 *       {@code waitEvents()} (GPU backend, woken by real input events or by
 *       {@code postEmptyEvent()} from posted tasks).</li>
 * </ul>
 *
 * <p>The frame step itself stays on {@code Application} because it touches
 * instance-owned state (event pump, task queue drain, animation callback,
 * style-dirty cascade latch, paint-dirty test-and-consume under the instance
 * monitor). This class only decides <em>when</em> and <em>how often</em> that
 * step runs — keeping the two concerns separable for future embedding
 * scenarios where a host supplies its own loop driver.</p>
 */
final class FrameLoop {

    private final Application app;

    FrameLoop(Application app) {
        this.app = app;
    }

    /**
     * Runs the event loop until the window signals close. Blocks the calling
     * (UI) thread. Delegates to the backend-owned loop when applicable, and
     * selects the idle strategy based on the active rendering backend.
     */
    void run() {
        Window window = app.windowForLoop();
        if (window.isAppOwnedLoop()) {
            // The backend owns the UI thread and its native message loop
            // (JWM): hand it the frame body, which it invokes on this thread
            // once per dispatched event batch. This call blocks until the
            // application shuts down, so there is nothing left to do here.
            window.enterBackendEventLoop(app::frameStep);
            return;
        }

        while (!window.shouldClose()) {
            final boolean painted = app.frameStep();
            if (!painted) {
                if (!app.isGpuBackend()) {
                    // Raster backend has no hardware vsync: sleep a short interval
                    // so the loop does not consume CPU while idle.
                    try {
                        Thread.sleep(RepaintScheduler.RASTER_IDLE_SLEEP_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    // GPU backend with a clean frame: block in waitEvents()
                    // instead of busy-polling. The loop is woken up by real input
                    // events or by postEmptyEvent() from invokeLater(...).
                    window.waitEvents();
                }
            }
        }
    }
}
