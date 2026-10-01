package com.glyphui.core;

import org.lwjgl.glfw.GLFWWindowContentScaleCallback;
import org.lwjgl.glfw.GLFWWindowContentScaleCallbackI;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.glfw.GLFWWindowSizeCallback;
import org.lwjgl.glfw.GLFWWindowSizeCallbackI;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Manages the application window and GLFW context.
 *
 * <p>{@code Window} owns the native GLFW window handle, so it implements
 * {@link AutoCloseable}. {@link #close()} destroys the window and terminates
 * GLFW; it is safe to call multiple times.</p>
 */
public class Window implements AutoCloseable {
    private long windowHandle;
    /** Logical window width (glfwGetWindowSize). */
    private int windowWidth;
    /** Logical window height (glfwGetWindowSize). */
    private int windowHeight;
    /** Physical framebuffer width in pixels (glfwGetFramebufferSize). */
    private int framebufferWidth;
    /** Physical framebuffer height in pixels (glfwGetFramebufferSize). */
    private int framebufferHeight;
    /** Cached content scale X (DPI factor), updated via callback. */
    private float contentScaleX = 1.0f;
    /** Cached content scale Y (DPI factor), updated via callback. */
    private float contentScaleY = 1.0f;
    private String title;
    private boolean shouldClose;

    // User-facing resize/scale listeners (invoked from GLFW callbacks)
    private GLFWWindowSizeCallbackI windowSizeListener;
    private GLFWFramebufferSizeCallbackI framebufferSizeListener;
    private GLFWWindowContentScaleCallbackI contentScaleListener;
    private GLFWWindowSizeCallback windowSizeCbRef;
    private GLFWFramebufferSizeCallback framebufferSizeCbRef;
    private GLFWWindowContentScaleCallback contentScaleCbRef;

    /**
     * Creates a new Window.
     *
     * @param title  the window title
     * @param width  the window width (logical)
     * @param height the window height (logical)
     */
    public Window(String title, int width, int height) {
        this.title = title;
        this.windowWidth = width;
        this.windowHeight = height;
        // Until the real framebuffer size is queried, assume 1:1 scaling
        this.framebufferWidth = width;
        this.framebufferHeight = height;
        this.shouldClose = false;
    }

    /**
     * Initializes the window and GLFW context with OpenGL.
     *
     * @return true if initialization was successful
     */
    public boolean create() {
        // Setup error callback. Keep the CbRef so we can free it on failure
        // paths and in destroy() (a bare .set() would orphan the native stub).
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            System.err.println("Unable to initialize GLFW");
            // Uninstall our callback (restores GLFW's default) and free its
            // native resources before bailing out.
            GLFWErrorCallback installed = glfwSetErrorCallback(null);
            if (installed != null) {
                installed.free();
            }
            return false;
        }

        // Configure GLFW for OpenGL context
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);
        
        // OpenGL context settings for Skija GPU backend
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        // MSAA hint removed: the Skija BackendRenderTarget is created with
        // samples=0, so a multisampled default framebuffer was never actually
        // resolved by us. Keep the GL state consistent (no-op on drivers that
        // ignore it anyway) until Skija is configured for real MSAA
        // (samples=4 + stencil=8).

        // Create the window
        windowHandle = glfwCreateWindow(windowWidth, windowHeight, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            System.err.println("Failed to create GLFW window");
            // Clean up process-wide GLFW state AND our error callback before
            // returning, otherwise a re-init leaks the native callback stub.
            GLFWErrorCallback installed = glfwSetErrorCallback(null);
            if (installed != null) {
                installed.free();
            }
            glfwTerminate();
            return false;
        }

        // Make OpenGL context current
        glfwMakeContextCurrent(windowHandle);
        
        // Enable vsync
        glfwSwapInterval(1);
        
        // Initialize LWJGL OpenGL capabilities
        GL.createCapabilities();

        // Get actual logical window size and physical framebuffer size
        int[] lw = new int[1];
        int[] lh = new int[1];
        glfwGetWindowSize(windowHandle, lw, lh);
        this.windowWidth = lw[0];
        this.windowHeight = lh[0];

        int[] fbw = new int[1];
        int[] fbh = new int[1];
        glfwGetFramebufferSize(windowHandle, fbw, fbh);
        this.framebufferWidth = fbw[0];
        this.framebufferHeight = fbh[0];

        float[] xs = new float[1];
        float[] ys = new float[1];
        glfwGetWindowContentScale(windowHandle, xs, ys);
        this.contentScaleX = xs[0];
        this.contentScaleY = ys[0];

        // Install internal GLFW callbacks that keep cached state in sync and
        // forward to user listeners. These are registered before any user
        // callback so that getWidth()/getFramebufferWidth() already reflect
        // the new sizes when Application's listeners run.
        windowSizeCbRef = GLFWWindowSizeCallback.create((w, width, height) -> {
            Window.this.windowWidth = width;
            Window.this.windowHeight = height;
            if (windowSizeListener != null) {
                windowSizeListener.invoke(w, width, height);
            }
        }).set(windowHandle);

        framebufferSizeCbRef = GLFWFramebufferSizeCallback.create((w, width, height) -> {
            Window.this.framebufferWidth = width;
            Window.this.framebufferHeight = height;
            if (framebufferSizeListener != null) {
                framebufferSizeListener.invoke(w, width, height);
            }
        }).set(windowHandle);

        contentScaleCbRef = GLFWWindowContentScaleCallback.create((w, xscale, yscale) -> {
            // DPI change (e.g. window moved between monitors with different scale)
            Window.this.contentScaleX = xscale;
            Window.this.contentScaleY = yscale;
            if (contentScaleListener != null) {
                contentScaleListener.invoke(w, xscale, yscale);
            }
        }).set(windowHandle);

        // Center the window. Guard against headless setups: glfwGetPrimaryMonitor()
        // returns NULL with no display attached, and glfwGetVideoMode(NULL) is UB.
        long primaryMonitor = glfwGetPrimaryMonitor();
        if (primaryMonitor != MemoryUtil.NULL) {
            GLFWVidMode vidmode = glfwGetVideoMode(primaryMonitor);
            if (vidmode != null) {
                glfwSetWindowPos(
                    windowHandle,
                    (vidmode.width() - windowWidth) / 2,
                    (vidmode.height() - windowHeight) / 2
                );
            }
        }

        // Make the window visible
        glfwShowWindow(windowHandle);

        return true;
    }

    /**
     * Swaps the front and back buffers (presents the frame).
     */
    public void swapBuffers() {
        glfwSwapBuffers(windowHandle);
    }

    /**
     * Checks if the window should close.
     *
     * <p>Single source of truth: while a live GLFW window exists we query
     * its flag directly (the local field would only duplicate it and could
     * drift). The cached {@code shouldClose} field is used solely as a
     * fallback before {@link #create()} succeeds, so headless/unit-test
     * contexts can still request closure.</p>
     *
     * @return true if the window should close
     */
    public boolean shouldClose() {
        if (windowHandle != MemoryUtil.NULL) {
            return glfwWindowShouldClose(windowHandle);
        }
        return shouldClose;
    }

    /**
     * Sets the should close flag. Safe to call before {@link #create()}:
     * without a live window handle the request is simply cached (calling
     * {@code glfwSetWindowShouldClose} with handle 0 raises a GLFW error).
     *
     * @param shouldClose true to request window close
     */
    public void setShouldClose(boolean shouldClose) {
        this.shouldClose = shouldClose;
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowShouldClose(windowHandle, shouldClose);
        }
    }

    /**
     * Polls for window events.
     */
    public void pollEvents() {
        glfwPollEvents();
    }

    /**
     * Waits until one or more events have been received and then polls them.
     *
     * <p>This is the blocking counterpart of {@link #pollEvents()} used by the
     * on-demand event loop: the UI thread sleeps here instead of busy-waiting,
     * and another thread can wake it up promptly with
     * {@code glfwPostEmptyEvent()} (see
     * {@link Application#invokeLater(Runnable)}).</p>
     */
    public void waitEvents() {
        glfwWaitEvents();
    }

    /**
     * Waits for events with an upper time bound, then polls them. Useful for
     * low-frequency animations where a pure {@link #waitEvents()} (infinite)
     * or busy polling is too coarse — e.g. call it with 1/30 to cap an idle
     * animation at ~30 FPS without spinning the CPU.
     *
     * @param seconds maximum time to wait, in seconds
     */
    public void waitEventsTimeout(double seconds) {
        glfwWaitEventsTimeout(seconds);
    }

    /**
     * Posts an empty event to the GLFW event queue, waking up a UI thread
     * blocked in {@link #waitEvents()} so that it promptly processes a new
     * repaint request or queued task.
     */
    public void postEmptyEvent() {
        glfwPostEmptyEvent();
    }

    /**
     * Gets the window handle.
     *
     * @return the GLFW window handle
     */
    public long getWindowHandle() {
        return windowHandle;
    }

    /**
     * Gets the logical window width (glfwGetWindowSize coordinate space).
     * This matches the space of mouse coordinates and the UI component tree.
     *
     * @return the logical width
     */
    public int getWidth() {
        return windowWidth;
    }

    /**
     * Gets the logical window height (glfwGetWindowSize coordinate space).
     * This matches the space of mouse coordinates and the UI component tree.
     *
     * @return the logical height
     */
    public int getHeight() {
        return windowHeight;
    }

    /**
     * Gets the physical framebuffer width in pixels. Use this for the GPU
     * render target ({@code BackendRenderTarget.makeGL}) size.
     *
     * @return the physical (framebuffer) width
     */
    public int getFramebufferWidth() {
        return framebufferWidth;
    }

    /**
     * Gets the physical framebuffer height in pixels. Use this for the GPU
     * render target ({@code BackendRenderTarget.makeGL}) size.
     *
     * @return the physical (framebuffer) height
     */
    public int getFramebufferHeight() {
        return framebufferHeight;
    }

    /**
     * Gets the window title.
     *
     * @return the title
     */
    public String getTitle() {
        return title;
    }

    /**
     * Updates the cached <b>logical</b> window dimensions after a resize.
     * The framebuffer dimensions are queried from GLFW since they scale
     * with the DPI factor.
     *
     * @param width  the new logical width
     * @param height the new logical height
     */
    public void updateDimensions(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
        if (windowHandle != MemoryUtil.NULL) {
            int[] fbw = new int[1];
            int[] fbh = new int[1];
            glfwGetFramebufferSize(windowHandle, fbw, fbh);
            this.framebufferWidth = fbw[0];
            this.framebufferHeight = fbh[0];
        } else {
            // No live GLFW window (unit tests): derive physical from scale
            this.framebufferWidth = Math.round(width * getContentScale());
            this.framebufferHeight = Math.round(height * getContentScale());
        }
    }

    /**
     * Updates both the logical and physical (framebuffer) dimensions
     * explicitly. Intended for tests and for platforms where the values
     * come straight from GLFW callbacks.
     *
     * @param windowWidth      the new logical width
     * @param windowHeight     the new logical height
     * @param framebufferWidth the new physical width
     * @param framebufferHeight the new physical height
     */
    public void setSizes(int windowWidth, int windowHeight,
                         int framebufferWidth, int framebufferHeight) {
        this.windowWidth = windowWidth;
        this.windowHeight = windowHeight;
        this.framebufferWidth = framebufferWidth;
        this.framebufferHeight = framebufferHeight;
    }

    /**
     * Gets the window content scale X factor for HiDPI support.
     *
     * @return the X scale factor
     */
    public float getContentScaleX() {
        return contentScaleX;
    }

    /**
     * Gets the window content scale Y factor for HiDPI support.
     *
     * @return the Y scale factor
     */
    public float getContentScaleY() {
        return contentScaleY;
    }

    /**
     * Gets the window content scale (DPI factor). Assumes uniform scaling
     * (x == y), as is the case on all mainstream platforms; returns the X
     * factor. Use {@link #getContentScaleX()}/{@link #getContentScaleY()}
     * if non-uniform scaling must be handled.
     *
     * @return the content scale factor
     */
    public float getContentScale() {
        return contentScaleX;
    }

    /**
     * Sets the cached content scale factors. Normally updated via the
     * {@code glfwSetWindowContentScaleCallback}; exposed for headless
     * simulation/testing (e.g. simulating a 2.0 HiDPI display).
     *
     * @param scaleX the X DPI factor
     * @param scaleY the Y DPI factor
     */
    public void setContentScale(float scaleX, float scaleY) {
        this.contentScaleX = scaleX;
        this.contentScaleY = scaleY;
        if (contentScaleListener != null) {
            // Pass the real handle when available; 0 (no live window) is fine
            // for headless listeners but must never reach a native callback.
            contentScaleListener.invoke(windowHandle, scaleX, scaleY);
        }
    }

    /**
     * Registers a listener for logical window-size changes
     * ({@code glfwSetWindowSizeCallback}).
     *
     * @param listener the listener (may be null to remove)
     */
    public void setWindowSizeListener(GLFWWindowSizeCallbackI listener) {
        this.windowSizeListener = listener;
    }

    /**
     * Registers a listener for physical framebuffer-size changes
     * ({@code glfwSetFramebufferSizeCallback}).
     *
     * @param listener the listener (may be null to remove)
     */
    public void setFramebufferSizeListener(GLFWFramebufferSizeCallbackI listener) {
        this.framebufferSizeListener = listener;
    }

    /**
     * Registers a listener for content-scale (DPI) changes
     * ({@code glfwSetWindowContentScaleCallback}), fired when the window
     * moves between monitors with different DPI.
     *
     * @param listener the listener (may be null to remove)
     */
    public void setContentScaleListener(GLFWWindowContentScaleCallbackI listener) {
        this.contentScaleListener = listener;
    }

    /**
     * Gets the window width in screen coordinates (not framebuffer pixels).
     * Alias of {@link #getWidth()}.
     *
     * @return the width in screen coordinates
     */
    public int getWindowWidth() {
        return windowWidth;
    }

    /**
     * Gets the window height in screen coordinates (not framebuffer pixels).
     * Alias of {@link #getHeight()}.
     *
     * @return the height in screen coordinates
     */
    public int getWindowHeight() {
        return windowHeight;
    }

    /**
     * Destroys the window and terminates GLFW. Idempotent: subsequent calls
     * are no-ops once the window handle has been released.
     *
     * <p><strong>Note:</strong> {@code glfwTerminate()} releases process-wide
     * GLFW state, not just this window's resources. Any other GLFW window in
     * the same process becomes unusable after this call; Glyph-UI assumes a
     * single owning {@code Window} per application.</p>
     */
    public void destroy() {
        if (windowHandle == 0L) {
            return; // already destroyed
        }

        // Free our window callbacks explicitly and null out the Java refs so
        // nobody can later invoke a stub pointing at freed native memory.
        // (glfwFreeCallbacks would do this too, but leaves the fields dangling.)
        if (windowSizeCbRef != null) {
            windowSizeCbRef.free();
            windowSizeCbRef = null;
        }
        if (framebufferSizeCbRef != null) {
            framebufferSizeCbRef.free();
            framebufferSizeCbRef = null;
        }
        if (contentScaleCbRef != null) {
            contentScaleCbRef.free();
            contentScaleCbRef = null;
        }

        // Uninstall + free the GLFW error callback BEFORE glfwTerminate():
        // terminate resets GLFW's internal state, and touching the error
        // callback afterwards is not guaranteed to be well-defined.
        GLFWErrorCallback previousErrorCallback = glfwSetErrorCallback(null);
        if (previousErrorCallback != null) {
            previousErrorCallback.free();
        }

        glfwDestroyWindow(windowHandle);
        windowHandle = 0L;

        // LWJGL: drop the thread-local GL capabilities bound to the context
        // we just destroyed, otherwise function-pointer tables leak.
        GL.setCapabilities(null);

        glfwTerminate();
    }

    /**
     * Releases the native window resources. Equivalent to {@link #destroy()};
     * provided so windows can be used with try-with-resources.
     */
    @Override
    public void close() {
        destroy();
    }
}
