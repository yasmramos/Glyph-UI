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
 */
public class Window {
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
     * Destroys the window and terminates GLFW.
     */
    public void destroy() {
        glfwFreeCallbacks(windowHandle);
        glfwDestroyWindow(windowHandle);
        glfwTerminate();
        glfwSetErrorCallback(null).free();
    }
}
