package com.glyphui.core.backend;

import com.glyphui.core.Window;
import io.github.humbleui.skija.BackendRenderTarget;
import io.github.humbleui.skija.ColorSpace;
import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.SurfaceColorFormat;
import io.github.humbleui.skija.SurfaceOrigin;
// LWJGL-free framebuffer query: the GL path only ever renders into the
// default window framebuffer (fbId 0), which every backend binds before
// handing the context to Skija. The historical code read
// GL_FRAMEBUFFER_BINDING via LWJGL purely as a sanity check; that binding
// library dependency was removed with the window-backend abstraction.

/**
 * GPU (OpenGL) {@link SurfaceFactory}: creates a Skija
 * {@link DirectContext} over the window's current GL context and wraps the
 * default framebuffer in a {@link BackendRenderTarget}-backed surface.
 *
 * <p><strong>Failure-path ownership:</strong> every native handle allocated
 * here ({@code DirectContext}, {@code BackendRenderTarget}) is released via
 * try/finally before any exception propagates, so a failed GPU init never
 * leaks handles into the caller's raster fallback.</p>
 */
public class GlSurfaceFactory implements SurfaceFactory {

    /** GR_GL_RGBA8 internal format for the framebuffer render target. */
    private static final int GR_GL_RGBA8 = 0x8058;

    /** GL id of the default window framebuffer (always bound by the backend). */
    private static final int DEFAULT_FRAMEBUFFER_ID = 0;

    private DirectContext directContext;

    @Override
    public SurfaceResult create(Window window) {
        int fbWidth = window.getFramebufferWidth();
        int fbHeight = window.getFramebufferHeight();
        int logicalWidth = window.getWidth();
        int logicalHeight = window.getHeight();

        // Create OpenGL context is already current from Window.create()

        // Create Skija DirectContext for GPU backend
        this.directContext = DirectContext.makeGL();
        if (directContext == null) {
            throw new RuntimeException("Failed to create Skija DirectContext");
        }

        // Current draw-framebuffer id (normally 0 = default window
        // framebuffer; queried live so an offscreen FBO binding would still
        // be honoured).
        int fbId = resolveFramebufferId();

        // Create BackendRenderTarget for the OpenGL framebuffer using the
        // PHYSICAL (framebuffer) size — HiDPI: this is larger than logical
        // Parameters: width, height, samples, stencil, fbId, format (GR_GL_RGBA8 = 0x8058)
        BackendRenderTarget renderTarget = BackendRenderTarget.makeGL(
            fbWidth,
            fbHeight,
            0,           // samples
            0,           // stencil
            fbId,
            GR_GL_RGBA8  // GL_RGBA8 constant
        );

        if (renderTarget == null) {
            closeDirectContextQuietly();
            throw new RuntimeException("Failed to create BackendRenderTarget");
        }

        Surface surface = wrapRenderTarget(renderTarget, "Failed to create Skija surface");

        return new SurfaceResult(surface, directContext,
            logicalWidth, logicalHeight, fbWidth, fbHeight);
    }

    @Override
    public SurfaceResult recreate(Window window, int fbWidth, int fbHeight) {
        if (directContext == null) {
            throw new IllegalStateException(
                "Cannot recreate a GL surface: no DirectContext (call create first)");
        }

        // Logical size derived from the physical framebuffer via the DPI factor
        float contentScaleX = Math.max(window.getContentScaleX(), 1.0f);
        float contentScaleY = Math.max(window.getContentScaleY(), 1.0f);
        int logicalWidth = Math.max(1, Math.round(fbWidth / contentScaleX));
        int logicalHeight = Math.max(1, Math.round(fbHeight / contentScaleY));

        // GPU backend: wrap the window framebuffer in a new render target
        int fbId = resolveFramebufferId();

        // Parameters: width, height, samples, stencil, fbId, format (GR_GL_RGBA8 = 0x8058)
        BackendRenderTarget renderTarget = BackendRenderTarget.makeGL(
            fbWidth,
            fbHeight,
            0,           // samples
            0,           // stencil
            fbId,
            GR_GL_RGBA8  // GL_RGBA8 constant
        );

        if (renderTarget == null) {
            // The previous surface was already closed by the caller; the
            // existing context stays alive (it owns nothing else right now),
            // but the resize attempt itself produced no new handle to free.
            throw new RuntimeException("Failed to recreate BackendRenderTarget");
        }

        Surface surface = wrapRenderTarget(renderTarget,
            "Failed to recreate Skija surface");

        return new SurfaceResult(surface, directContext,
            logicalWidth, logicalHeight, fbWidth, fbHeight);
    }

    /**
     * Wraps a freshly created render target in a Skija surface, centralising
     * the ownership rules shared by the init and resize paths:
     *
     * <ul>
     *   <li>On wrap failure — exception or null return — Skija did <b>not</b>
     *       take ownership of the render target, so it must be closed here
     *       before rethrowing; otherwise its native handle leaks.</li>
     *   <li>Consistent between both call sites: do NOT close the render target
     *       after wrapping. Skija's wrapped surface may keep referencing the
     *       native target for its lifetime; closing it eagerly risks use of
     *       a freed handle on the next flush/draw. The DirectContext owns
     *       the lifecycle once wrapped (see also issue #24: both paths must
     *       follow the same ownership rule).</li>
     * </ul>
     *
     * @param renderTarget the target to wrap (consumed on success, closed on failure)
     * @param nullMessage message used when the wrap returns null instead of throwing
     * @return the wrapped surface, never null
     */
    private Surface wrapRenderTarget(BackendRenderTarget renderTarget, String nullMessage) {
        // Skija throws IllegalStateException when the GL context is not
        // usable, so wrap it to allow callers to fall back to raster.
        boolean wrapped = false;
        try {
            Surface surface = Surface.wrapBackendRenderTarget(
                directContext,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getSRGB()
            );
            if (surface == null) {
                // Wrap returned null: Skija did not take ownership of the
                // render target, so release it below (same rule as the catch
                // block) before signalling the failure.
                throw new RuntimeException(nullMessage);
            }
            wrapped = true;
            return surface;
        } catch (RuntimeException e) {
            if (!wrapped) {
                renderTarget.close();
            }
            // Rethrow with the original message when we produced the final
            // failure ourselves; otherwise annotate the Skija cause.
            if (nullMessage.equals(e.getMessage())) {
                throw e;
            }
            throw new RuntimeException("Failed to wrap backend render target: "
                + e.getMessage(), e);
        }
    }

    /**
     * Resolves the GL id of the render-target framebuffer. Glyph-UI always
     * draws into the default window framebuffer, which the active backend
     * binds before creating the surface, so this is 0 for every supported
     * backend (GLFW makes the window context current whose default FBO is
     * id 0; JWM does the same after {@code Window.makeCurrent()}). A future
     * offscreen-FBO pipeline would override this through the backend.
     *
     * @return the framebuffer id to wrap in the render target
     */
    private static int resolveFramebufferId() {
        return DEFAULT_FRAMEBUFFER_ID;
    }

    @Override
    public boolean isGpu() {
        return true;
    }

    @Override
    public void close() {
        closeDirectContextQuietly();
    }

    /**
     * Idempotent best-effort release of the DirectContext. Used by every
     * failure path so a failed GPU init/recreation never leaves an orphaned
     * native context behind; on success the application takes ownership and
     * closes it during {@code Application.close()}.
     */
    private void closeDirectContextQuietly() {
        if (directContext != null) {
            try {
                directContext.close();
            } catch (RuntimeException ignored) {
                // Best-effort release during an already-failing path.
            }
            directContext = null;
        }
    }

    /** Exposed for tests that need to inject/spy the context holder. */
    DirectContext directContextOrNull() {
        return directContext;
    }
}
