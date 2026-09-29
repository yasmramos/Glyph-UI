package com.glyphui.core;

import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.Callbacks.glfwFreeCallbacks;
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
    private int width;
    private int height;
    private String title;
    private boolean shouldClose;

    /**
     * Creates a new Window.
     *
     * @param title  the window title
     * @param width  the window width
     * @param height the window height
     */
    public Window(String title, int width, int height) {
        this.title = title;
        this.width = width;
        this.height = height;
        this.shouldClose = false;
    }

    /**
     * Initializes the window and GLFW context with OpenGL.
     *
     * @return true if initialization was successful
     */
    public boolean create() {
        // Setup error callback
        GLFWErrorCallback.createPrint(System.err).set();

        // Initialize GLFW
        if (!glfwInit()) {
            System.err.println("Unable to initialize GLFW");
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
        glfwWindowHint(GLFW_SAMPLES, 4); // MSAA

        // Create the window
        windowHandle = glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
        if (windowHandle == MemoryUtil.NULL) {
            System.err.println("Failed to create GLFW window");
            glfwTerminate();
            return false;
        }

        // Make OpenGL context current
        glfwMakeContextCurrent(windowHandle);
        
        // Enable vsync
        glfwSwapInterval(1);
        
        // Initialize LWJGL OpenGL capabilities
        GL.createCapabilities();

        // Get actual window size (may differ on HiDPI displays)
        int[] actualWidth = new int[1];
        int[] actualHeight = new int[1];
        glfwGetFramebufferSize(windowHandle, actualWidth, actualHeight);
        this.width = actualWidth[0];
        this.height = actualHeight[0];

        // Center the window
        GLFWVidMode vidmode = glfwGetVideoMode(glfwGetPrimaryMonitor());
        if (vidmode != null) {
            glfwSetWindowPos(
                windowHandle,
                (vidmode.width() - width) / 2,
                (vidmode.height() - height) / 2
            );
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
     * @return true if the window should close
     */
    public boolean shouldClose() {
        return shouldClose || glfwWindowShouldClose(windowHandle);
    }

    /**
     * Sets the should close flag.
     *
     * @param shouldClose true to request window close
     */
    public void setShouldClose(boolean shouldClose) {
        this.shouldClose = shouldClose;
        glfwSetWindowShouldClose(windowHandle, shouldClose);
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
     * Gets the window handle.
     *
     * @return the GLFW window handle
     */
    public long getWindowHandle() {
        return windowHandle;
    }

    /**
     * Gets the window width.
     *
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the window height.
     *
     * @return the height
     */
    public int getHeight() {
        return height;
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
     * Updates the window dimensions after resize.
     *
     * @param width  the new width
     * @param height the new height
     */
    public void updateDimensions(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Gets the window content scale X factor for HiDPI support.
     *
     * @return the X scale factor
     */
    public float getContentScaleX() {
        float[] xScale = new float[1];
        float[] yScale = new float[1];
        glfwGetWindowContentScale(windowHandle, xScale, yScale);
        return xScale[0];
    }

    /**
     * Gets the window content scale Y factor for HiDPI support.
     *
     * @return the Y scale factor
     */
    public float getContentScaleY() {
        float[] xScale = new float[1];
        float[] yScale = new float[1];
        glfwGetWindowContentScale(windowHandle, xScale, yScale);
        return yScale[0];
    }

    /**
     * Gets the window width in screen coordinates (not framebuffer pixels).
     *
     * @return the width in screen coordinates
     */
    public int getWindowWidth() {
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetWindowSize(windowHandle, w, h);
        return w[0];
    }

    /**
     * Gets the window height in screen coordinates (not framebuffer pixels).
     *
     * @return the height in screen coordinates
     */
    public int getWindowHeight() {
        int[] w = new int[1];
        int[] h = new int[1];
        glfwGetWindowSize(windowHandle, w, h);
        return h[0];
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
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        windowHandle = 0L;
        glfwTerminate();
        // glfwSetErrorCallback(null) uninstalls and returns the previously
        // registered error callback; guard against a null return so we never
        // dereference it, then free its native resources.
        GLFWErrorCallback previousErrorCallback = glfwSetErrorCallback(null);
        if (previousErrorCallback != null) {
            previousErrorCallback.free();
        }
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
