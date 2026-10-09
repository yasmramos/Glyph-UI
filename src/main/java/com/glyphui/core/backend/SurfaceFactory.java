package com.glyphui.core.backend;

import com.glyphui.core.Window;

/**
 * Strategy interface for creating and recreating the Skija {@link
 * io.github.humbleui.skija.Surface} that backs an {@link
 * com.glyphui.core.Application}. Each implementation encapsulates the native
 * resource lifecycle (GL context, render targets, CPU buffers) of one
 * rendering backend so the application layer never touches backend-specific
 * handles directly.
 *
 * <p><strong>Ownership contract:</strong> a factory owns every native object
 * it creates. On success, ownership of the GPU render target transfers to the
 * wrapped surface (see {@link GlSurfaceFactory}); on any failure path the
 * factory must release what it allocated before rethrowing, so callers can
 * fall back to another backend without leaking native handles.</p>
 */
public interface SurfaceFactory {

    /**
     * Creates the initial surface for a freshly opened window.
     *
     * @param window the GLFW window whose framebuffer (or CPU buffer, for
     *               raster) the surface should target
     * @return the created surface together with its owning context (nullable
     *         for CPU backends) and the logical/physical dimensions used
     * @throws RuntimeException if the backend cannot produce a usable surface;
     *         all partially created native resources are released before the
     *         exception propagates
     */
    SurfaceResult create(Window window);

    /**
     * Recreates the surface after the window's physical size changed (e.g.
     * framebuffer resize). Implementations close/reallocate their render
     * target internally and return the fresh result; the caller is expected
     * to have closed the previous surface already.
     *
     * @param window   the resized window
     * @param fbWidth  the new physical framebuffer width
     * @param fbHeight the new physical framebuffer height
     * @return the recreated surface result
     * @throws RuntimeException if recreation fails; native resources created
     *         during the attempt are released before the exception propagates
     */
    SurfaceResult recreate(Window window, int fbWidth, int fbHeight);

    /**
     * Returns true when this factory drives a GPU (OpenGL) backend, false for
     * pure CPU/raster backends. Used by the application to decide whether a
     * per-frame context flush and buffer swap are required.
     *
     * @return true for GPU-backed factories
     */
    boolean isGpu();

    /**
     * Releases every native resource still owned by this factory (GPU
     * context, render targets). Must be idempotent and tolerate being called
     * from a failure path where some handles were already freed.
     */
    void close();
}
