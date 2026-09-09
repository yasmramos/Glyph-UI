package com.glyphui.core;

import com.glyphui.graphics.Canvas;
import com.glyphui.ui.Panel;
import com.glyphui.events.*;
import io.github.humbleui.skija.*;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.EnumSet;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Main application class that manages the event loop, rendering, and window lifecycle.
 */
public class Application {
    private Window window;
    private Surface surface;
    private Canvas canvas;
    private Panel rootPanel;
    private DirectContext directContext;
    private boolean running;
    private double lastFrameTime;
    private int targetFPS;

    // Mouse state
    private double mouseX;
    private double mouseY;
    private boolean[] mouseButtons = new boolean[10];

    /**
     * Creates a new Application.
     */
    public Application() {
        this.targetFPS = 60;
        this.running = false;
        this.rootPanel = new Panel(0, 0, 800, 600);
    }

    /**
     * Initializes the application with default window settings.
     *
     * @return true if initialization was successful
     */
    public boolean init() {
        return init("Glyph UI", 800, 600);
    }

    /**
     * Initializes the application.
     *
     * @param title  the window title
     * @param width  the window width
     * @param height the window height
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height) {
        return init(title, width, height, false);
    }
    
    /**
     * Initializes the application.
     *
     * @param title  the window title
     * @param width  the window width
     * @param height the window height
     * @param useRasterSurface if true, uses a raster surface instead of GPU backend (for testing)
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height, boolean useRasterSurface) {
        try {
            // Create window
            window = new Window(title, width, height);
            if (!window.create()) {
                return false;
            }

            // Initialize Skija surface
            if (useRasterSurface) {
                initRasterSurface();
            } else {
                initSurface();
            }

            // Setup callbacks
            setupCallbacks();

            // Set initial root panel size
            rootPanel.setWidth(width);
            rootPanel.setHeight(height);

            lastFrameTime = glfwGetTime();
            running = true;

            return true;
        } catch (Exception e) {
            System.err.println("Failed to initialize application: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initializes the Skija surface with GPU backend.
     */
    private void initSurface() {
        int width = window.getWidth();
        int height = window.getHeight();

        // Create OpenGL context is already current from Window.create()
        
        // Create Skija DirectContext for GPU backend
        directContext = DirectContext.makeGL();
        if (directContext == null) {
            throw new RuntimeException("Failed to create Skija DirectContext");
        }

        // Get framebuffer ID (0 for default framebuffer)
        int[] fbIdArray = new int[1];
        GL11.glGetIntegerv(GL_FRAMEBUFFER_BINDING, fbIdArray);
        int fbId = fbIdArray[0];
        
        // Create BackendRenderTarget for the OpenGL framebuffer
        // Parameters: width, height, samples, stencil, fbId, format (GR_GL_RGBA8 = 0x8058)
        BackendRenderTarget renderTarget = BackendRenderTarget.makeGL(
            width, 
            height, 
            0,      // samples
            0,      // stencil
            fbId, 
            0x8058  // GL_RGBA8 constant
        );

        if (renderTarget == null) {
            throw new RuntimeException("Failed to create BackendRenderTarget");
        }

        // Create surface wrapping the OpenGL framebuffer
        surface = Surface.wrapBackendRenderTarget(
            directContext,
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.getSRGB()
        );

        if (surface == null) {
            throw new RuntimeException("Failed to create Skija surface");
        }

        // Create canvas wrapper
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas = new Canvas(skijaCanvas, surface, width, height);
    }

    /**
     * Initializes the Skija surface with raster backend (for testing).
     */
    private void initRasterSurface() {
        int width = window.getWidth();
        int height = window.getHeight();

        // Create raster surface (no OpenGL context needed)
        surface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));
        
        if (surface == null) {
            throw new RuntimeException("Failed to create raster surface");
        }

        // Create canvas wrapper
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas = new Canvas(skijaCanvas, surface, width, height);
    }

    /**
     * Sets up GLFW callbacks for events with HiDPI support.
     */
    private void setupCallbacks() {
        long windowHandle = window.getWindowHandle();

        // Framebuffer resize callback
        GLFWFramebufferSizeCallbackI framebufferCallback = (w, width, height) -> {
            window.updateDimensions(width, height);
            recreateSurface(width, height);
        };
        GLFWFramebufferSizeCallback.create(framebufferCallback).set(windowHandle);

        // Mouse button callback with HiDPI coordinate conversion
        GLFWMouseButtonCallbackI mouseButtonCallback = (w, button, action, mods) -> {
            MouseButton glyphButton = convertMouseButton(button);
            MouseEventType type = (action == GLFW_PRESS) ? MouseEventType.PRESS : MouseEventType.RELEASE;
            
            mouseButtons[button] = (action == GLFW_PRESS);
            
            // Convert screen coordinates to framebuffer coordinates for HiDPI displays
            int[] fbWidth = new int[1];
            int[] fbHeight = new int[1];
            glfwGetFramebufferSize(windowHandle, fbWidth, fbHeight);
            int[] winWidth = new int[1];
            int[] winHeight = new int[1];
            glfwGetWindowSize(windowHandle, winWidth, winHeight);
            
            float scaleX = (float) fbWidth[0] / winWidth[0];
            float scaleY = (float) fbHeight[0] / winHeight[0];
            
            int fbX = (int) (mouseX * scaleX);
            int fbY = (int) (mouseY * scaleY);
            
            MouseEvent event = new MouseEvent(type, fbX, fbY, glyphButton, 1);
            rootPanel.onMouseEvent(event);
        };
        GLFWMouseButtonCallback.create(mouseButtonCallback).set(windowHandle);

        // Cursor position callback with HiDPI coordinate conversion
        GLFWCursorPosCallbackI cursorCallback = (w, xpos, ypos) -> {
            // Convert screen coordinates to framebuffer coordinates for HiDPI displays
            int[] fbWidth = new int[1];
            int[] fbHeight = new int[1];
            glfwGetFramebufferSize(windowHandle, fbWidth, fbHeight);
            int[] winWidth = new int[1];
            int[] winHeight = new int[1];
            glfwGetWindowSize(windowHandle, winWidth, winHeight);
            
            float scaleX = (float) fbWidth[0] / winWidth[0];
            float scaleY = (float) fbHeight[0] / winHeight[0];
            
            mouseX = xpos * scaleX;
            mouseY = ypos * scaleY;
            
            MouseEvent event = new MouseEvent(MouseEventType.MOVE, (int) mouseX, (int) mouseY, MouseButton.LEFT, 0);
            rootPanel.onMouseEvent(event);
        };
        GLFWCursorPosCallback.create(cursorCallback).set(windowHandle);

        // Key callback
        GLFWKeyCallbackI keyCallback = (w, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE && key == GLFW_KEY_ESCAPE) {
                window.setShouldClose(true);
                return;
            }

            KeyEventType type = (action == GLFW_PRESS) ? KeyEventType.PRESS : KeyEventType.RELEASE;
            EnumSet<KeyModifier> modifiers = getModifiers(mods);
            
            KeyEvent event = new KeyEvent(type, key, (char) 0, modifiers);
            rootPanel.onKeyEvent(event);
        };
        GLFWKeyCallback.create(keyCallback).set(windowHandle);
    }

    /**
     * Recreates the Skija surface after window resize.
     *
     * @param width  the new width
     * @param height the new height
     */
    private void recreateSurface(int width, int height) {
        if (surface != null) {
            surface.close();
        }

        // Get framebuffer ID (0 for default framebuffer)
        int[] fbIdArray = new int[1];
        GL11.glGetIntegerv(GL_FRAMEBUFFER_BINDING, fbIdArray);
        int fbId = fbIdArray[0];
        
        // Create BackendRenderTarget for the OpenGL framebuffer
        // Parameters: width, height, samples, stencil, fbId, format (GR_GL_RGBA8 = 0x8058)
        BackendRenderTarget renderTarget = BackendRenderTarget.makeGL(
            width, 
            height, 
            0,      // samples
            0,      // stencil
            fbId, 
            0x8058  // GL_RGBA8 constant
        );

        if (renderTarget == null) {
            throw new RuntimeException("Failed to recreate BackendRenderTarget");
        }

        // Create surface wrapping the OpenGL framebuffer
        surface = Surface.wrapBackendRenderTarget(
            directContext,
            renderTarget,
            SurfaceOrigin.BOTTOM_LEFT,
            SurfaceColorFormat.RGBA_8888,
            ColorSpace.getSRGB()
        );

        if (surface == null) {
            throw new RuntimeException("Failed to recreate Skija surface");
        }

        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas.resize(width, height);
        
        // Update root panel size
        rootPanel.setWidth(width);
        rootPanel.setHeight(height);
    }

    /**
     * Converts GLFW mouse button to Glyph MouseButton.
     *
     * @param button the GLFW button code
     * @return the corresponding MouseButton
     */
    private MouseButton convertMouseButton(int button) {
        switch (button) {
            case GLFW_MOUSE_BUTTON_RIGHT:
                return MouseButton.RIGHT;
            case GLFW_MOUSE_BUTTON_MIDDLE:
                return MouseButton.MIDDLE;
            default:
                return MouseButton.LEFT;
        }
    }

    /**
     * Gets modifier keys from GLFW mods.
     *
     * @param mods the GLFW modifier flags
     * @return the set of KeyModifiers
     */
    private EnumSet<KeyModifier> getModifiers(int mods) {
        EnumSet<KeyModifier> modifiers = EnumSet.noneOf(KeyModifier.class);
        
        if ((mods & GLFW_MOD_SHIFT) != 0) {
            modifiers.add(KeyModifier.SHIFT);
        }
        if ((mods & GLFW_MOD_CONTROL) != 0) {
            modifiers.add(KeyModifier.CTRL);
        }
        if ((mods & GLFW_MOD_ALT) != 0) {
            modifiers.add(KeyModifier.ALT);
        }
        
        return modifiers;
    }

    /**
     * Gets the root panel of the application.
     *
     * @return the root panel
     */
    public Panel getRootPanel() {
        return rootPanel;
    }

    /**
     * Gets the canvas for rendering operations.
     *
     * @return the canvas
     */
    public Canvas getCanvas() {
        return canvas;
    }

    /**
     * Renders a single frame. Exposed for testing purposes.
     */
    public void renderFrame() {
        render();
    }

    /**
     * Captures the current frame to a PNG file.
     *
     * @param file the output file path
     * @throws RuntimeException if capture fails
     */
    public void captureToPng(java.io.File file) {
        // Render the current frame first
        renderFrame();
        
        // Create directory if it doesn't exist
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }
        
        // Take a snapshot of the surface
        Image image = surface.makeImageSnapshot();
        if (image == null) {
            throw new RuntimeException("Failed to create image snapshot");
        }
        
        try {
            // Encode to PNG format
            Data data = image.encodeToData(EncodedImageFormat.PNG);
            if (data == null) {
                throw new RuntimeException("Failed to encode image to PNG");
            }
            
            try {
                // Write bytes to file
                byte[] bytes = data.getBytes();
                try {
                    java.nio.file.Files.write(file.toPath(), bytes);
                } catch (java.io.IOException e) {
                    throw new RuntimeException("Failed to write PNG file: " + e.getMessage(), e);
                }
            } finally {
                data.close();
            }
        } finally {
            image.close();
        }
    }

    /**
     * Runs the application main loop.
     */
    public void run() {
        if (!running) {
            return;
        }

        while (!window.shouldClose()) {
            // Calculate delta time
            double currentTime = glfwGetTime();
            double deltaTime = currentTime - lastFrameTime;
            double targetFrameTime = 1.0 / targetFPS;

            // Frame rate limiting
            if (deltaTime < targetFrameTime) {
                try {
                    Thread.sleep((long) ((targetFrameTime - deltaTime) * 1000));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }

            lastFrameTime = currentTime;

            // Poll events
            window.pollEvents();

            // Render
            render();
        }
    }

    /**
     * Renders the current frame.
     */
    private void render() {
        // Clear canvas with background color
        canvas.clear(Color.makeARGB(255, 30, 30, 30));

        // Render root panel and all children
        rootPanel.render(canvas);

        // Flush drawing commands to GPU
        canvas.flush();
        
        // Flush DirectContext if available
        if (directContext != null) {
            directContext.flush();
        }
        
        // Swap buffers to present the frame
        window.swapBuffers();
    }

    /**
     * Stops the application.
     */
    public void stop() {
        running = false;
    }

    /**
     * Cleans up resources and destroys the application.
     */
    public void destroy() {
        // Dispose all components in the root panel
        if (rootPanel != null) {
            rootPanel.dispose();
        }
        
        // Close Skija DirectContext
        if (directContext != null) {
            directContext.close();
        }
        
        // Close Skija surface
        if (surface != null) {
            surface.close();
        }
        
        // Destroy window
        if (window != null) {
            window.destroy();
        }
    }
}
