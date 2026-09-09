package com.glyphui.core;

import com.glyphui.graphics.Canvas;
import com.glyphui.ui.Panel;
import com.glyphui.events.*;
import io.github.humbleui.skija.*;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;

import java.util.EnumSet;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Main application class that manages the event loop, rendering, and window lifecycle.
 */
public class Application {
    private Window window;
    private Surface surface;
    private Canvas canvas;
    private Panel rootPanel;
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
        try {
            // Create window
            window = new Window(title, width, height);
            if (!window.create()) {
                return false;
            }

            // Initialize Skija surface
            initSurface();

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
     * Initializes the Skija surface for rendering.
     */
    private void initSurface() {
        int width = window.getWidth();
        int height = window.getHeight();

        // Create Skija surface using raster backend (no OpenGL context needed)
        surface = Surface.makeRaster(
            ImageInfo.makeN32Premul(width, height)
        );

        if (surface == null) {
            throw new RuntimeException("Failed to create Skija surface");
        }

        // Create canvas wrapper
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas = new Canvas(skijaCanvas, surface, width, height);
    }

    /**
     * Sets up GLFW callbacks for events.
     */
    private void setupCallbacks() {
        long windowHandle = window.getWindowHandle();

        // Framebuffer resize callback
        GLFWFramebufferSizeCallbackI framebufferCallback = (w, width, height) -> {
            window.updateDimensions(width, height);
            recreateSurface(width, height);
        };
        GLFWFramebufferSizeCallback.create(windowHandle, framebufferCallback).set();

        // Mouse button callback
        GLFWMouseButtonCallbackI mouseButtonCallback = (w, button, action, mods) -> {
            MouseButton glyphButton = convertMouseButton(button);
            MouseEventType type = (action == GLFW_PRESS) ? MouseEventType.PRESS : MouseEventType.RELEASE;
            
            mouseButtons[button] = (action == GLFW_PRESS);
            
            MouseEvent event = new MouseEvent(type, (int) mouseX, (int) mouseY, glyphButton, 1);
            rootPanel.onMouseEvent(event);
        };
        GLFWMouseButtonCallback.create(windowHandle, mouseButtonCallback).set();

        // Cursor position callback
        GLFWCursorPosCallbackI cursorCallback = (w, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;
            
            MouseEvent event = new MouseEvent(MouseEventType.MOVE, (int) xpos, (int) ypos, MouseButton.LEFT, 0);
            rootPanel.onMouseEvent(event);
        };
        GLFWCursorPosCallback.create(windowHandle, cursorCallback).set();

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
        GLFWKeyCallback.create(windowHandle, keyCallback).set();
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

        surface = Surface.makeRaster(
            ImageInfo.makeN32Premul(width, height)
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

        // Flush drawing commands
        canvas.flush();
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
        if (surface != null) {
            surface.close();
        }
        if (window != null) {
            window.destroy();
        }
    }
}
