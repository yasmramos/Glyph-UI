package com.glyphui.core;

import com.glyphui.graphics.Canvas;
import com.glyphui.ui.Panel;
import com.glyphui.events.*;
import io.github.humbleui.skija.*;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWFramebufferSizeCallback;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWScrollCallback;
import org.lwjgl.glfw.GLFWWindowContentScaleCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWFramebufferSizeCallbackI;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.glfw.GLFWScrollCallbackI;
import org.lwjgl.glfw.GLFWWindowContentScaleCallbackI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.EnumSet;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Main application class that manages the event loop, rendering, and window lifecycle.
 *
 * <p><b>Coordinate model:</b> this application uses a "logical layout" model. All UI
 * components are laid out and all {@link MouseEvent}s are dispatched in GLFW's logical
 * window coordinates (the same space reported by {@code glfwGetWindowSize} and the
 * cursor/scroll callbacks). No HiDPI multiplication is applied to events.</p>
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

    // Mouse state (logical window coordinates, exactly as reported by GLFW)
    private double mouseX;
    private double mouseY;

    // Cached HiDPI scale factors, refreshed on framebuffer/content-scale callbacks
    // instead of being queried per mouse event.
    private float dpiScaleX = 1.0f;
    private float dpiScaleY = 1.0f;

    // Character produced by the most recent char callback, consumed by the next
    // key press event (GLFW delivers char and key events separately).
    private char lastChar = 0;

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
     * <p>If any step after window creation fails, partially allocated resources are
     * released through {@link #destroy()} before returning {@code false}.</p>
     *
     * @param title  the window title
     * @param width  the window width
     * @param height the window height
     * @param useRasterSurface if true, uses a raster surface instead of GPU backend.
     *        Note: a raster surface is not presented to the window (see
     *        {@link #initRasterSurface()}); it is intended for headless testing only.
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height, boolean useRasterSurface) {
        try {
            // Create window
            window = new Window(title, width, height);
            if (!window.create()) {
                window = null;
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

            // Cache initial DPI scale factors
            updateDpiScale();

            // Set initial root panel size
            rootPanel.setWidth(width);
            rootPanel.setHeight(height);

            lastFrameTime = glfwGetTime();
            running = true;

            return true;
        } catch (Exception e) {
            System.err.println("Failed to initialize application: " + e.getMessage());
            e.printStackTrace();
            // Release any partially created resources (all steps are null-safe)
            destroy();
            return false;
        }
    }

    /**
     * Initializes the Skija surface with GPU backend.
     */
    private void initSurface() {
        int width = window.getWidth();
        int height = window.getHeight();

        // OpenGL context is already current from Window.create()

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

        // wrapBackendRenderTarget does NOT take ownership of the render target;
        // close it immediately to avoid leaking the native handle.
        renderTarget.close();

        if (surface == null) {
            throw new RuntimeException("Failed to create Skija surface");
        }

        // Create canvas wrapper
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas = new Canvas(skijaCanvas, surface, width, height);
    }

    /**
     * Initializes the Skija surface with raster backend (for testing).
     *
     * <p><b>Important:</b> a raster surface renders into an off-screen bitmap and is
     * never presented to the GLFW window (no GL framebuffer is involved). This mode
     * exists exclusively for headless/testing scenarios such as screenshot capture;
     * visible output on screen requires the GPU backend ({@code useRasterSurface=false}).</p>
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
     * Sets up GLFW callbacks for events.
     *
     * <p>All mouse-related callbacks dispatch events in logical window coordinates;
     * no HiDPI conversion is performed (see class-level Javadoc).</p>
     */
    private void setupCallbacks() {
        long windowHandle = window.getWindowHandle();

        // Framebuffer resize callback
        GLFWFramebufferSizeCallbackI framebufferCallback = (w, width, height) -> {
            window.updateDimensions(width, height);
            recreateSurface(width, height);
            updateDpiScale();
        };
        GLFWFramebufferSizeCallback.create(framebufferCallback).set(windowHandle);

        // Content scale callback (HiDPI monitor changes / display scaling)
        GLFWWindowContentScaleCallbackI contentScaleCallback = (w, xscale, yscale) -> {
            dpiScaleX = xscale;
            dpiScaleY = yscale;
        };
        GLFWWindowContentScaleCallback.create(contentScaleCallback).set(windowHandle);

        // Mouse button callback. mouseX/mouseY already hold logical coordinates as
        // reported by the cursor callback; do not scale them again here.
        GLFWMouseButtonCallbackI mouseButtonCallback = (w, button, action, mods) -> {
            MouseButton glyphButton = convertMouseButton(button);
            MouseEventType type = (action == GLFW_PRESS) ? MouseEventType.PRESS : MouseEventType.RELEASE;

            MouseEvent event = new MouseEvent(type, (int) mouseX, (int) mouseY, glyphButton, 1);
            rootPanel.onMouseEvent(event);
        };
        GLFWMouseButtonCallback.create(mouseButtonCallback).set(windowHandle);

        // Cursor position callback: store raw logical coordinates from GLFW.
        GLFWCursorPosCallbackI cursorCallback = (w, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;

            MouseEvent event = new MouseEvent(MouseEventType.MOVE, (int) mouseX, (int) mouseY, MouseButton.LEFT, 0);
            rootPanel.onMouseEvent(event);
        };
        GLFWCursorPosCallback.create(cursorCallback).set(windowHandle);

        // Scroll callback: emit SCROLL events with the wheel deltas.
        GLFWScrollCallbackI scrollCallback = (w, xOffset, yOffset) -> {
            MouseEvent event = new MouseEvent(
                MouseEventType.SCROLL, (int) mouseX, (int) mouseY, MouseButton.LEFT, 0,
                xOffset, yOffset);
            rootPanel.onMouseEvent(event);
        };
        GLFWScrollCallback.create(scrollCallback).set(windowHandle);

        // Char callback: remember the typed Unicode character so the next PRESS
        // key event can carry it (GLFW fires char callbacks independently of keys).
        GLFWCharCallbackI charCallback = (w, codepoint) -> {
            lastChar = (char) codepoint;
            KeyEvent event = new KeyEvent(KeyEventType.PRESS, 0, lastChar, EnumSet.noneOf(KeyModifier.class));
            rootPanel.onKeyEvent(event);
        };
        GLFWCharCallback.create(charCallback).set(windowHandle);

        // Key callback
        GLFWKeyCallbackI keyCallback = (w, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE && key == GLFW_KEY_ESCAPE) {
                window.setShouldClose(true);
                return;
            }

            KeyEventType type = (action == GLFW_PRESS) ? KeyEventType.PRESS : KeyEventType.RELEASE;
            EnumSet<KeyModifier> modifiers = getModifiers(mods);

            // Attach the real typed character on press events, then clear it.
            char keyChar = (type == KeyEventType.PRESS) ? lastChar : 0;
            lastChar = 0;

            KeyEvent event = new KeyEvent(type, key, keyChar, modifiers);
            rootPanel.onKeyEvent(event);
        };
        GLFWKeyCallback.create(keyCallback).set(windowHandle);
    }

    /**
     * Refreshes the cached HiDPI scale factors from the current framebuffer and
     * window sizes. Called on creation and on framebuffer resize only, never from
     * per-event paths.
     */
    private void updateDpiScale() {
        long windowHandle = window.getWindowHandle();
        int[] fbWidth = new int[1];
        int[] fbHeight = new int[1];
        glfwGetFramebufferSize(windowHandle, fbWidth, fbHeight);
        int[] winWidth = new int[1];
        int[] winHeight = new int[1];
        glfwGetWindowSize(windowHandle, winWidth, winHeight);

        if (winWidth[0] > 0 && winHeight[0] > 0) {
            dpiScaleX = (float) fbWidth[0] / winWidth[0];
            dpiScaleY = (float) fbHeight[0] / winHeight[0];
        }
    }

    /**
     * Character produced by the most recent char callback, consumed by the next
     * key press event (GLFW delivers char and key events separately). Exposed
     * for testing: simulates what the GLFW char callback would store.
     */
    void setLastCharForTesting(char c) {
        this.lastChar = c;
    }

    /**
     * Simulates a full key-press dispatch (char callback + key callback), which is
     * exactly the sequence GLFW emits when a printable key is pressed. Used by
     * tests to verify that KeyEvent carries the real typed character without
     * requiring an interactive window.
     *
     * @param keyCode the GLFW key code
     * @param keyChar the Unicode character produced by the keypress
     * @param mods    the GLFW modifier bit flags
     * @return the dispatched KeyEvent
     */
    KeyEvent dispatchKeyPressForTesting(int keyCode, char keyChar, int mods) {
        lastChar = keyChar;
        EnumSet<KeyModifier> modifiers = getModifiers(mods);
        KeyEvent event = new KeyEvent(KeyEventType.PRESS, keyCode, lastChar, modifiers);
        lastChar = 0;
        rootPanel.onKeyEvent(event);
        return event;
    }

    /**
     * Recreates the Skija surface after window resize.
     *
     * <p>Package-private so integration tests can exercise the resize path
     * (surface/canvas rebinding) without needing a real window manager event.</p>
     *
     * @param width  the new width
     * @param height the new height
     */
    void recreateSurface(int width, int height) {
        if (surface != null) {
            surface.close();
            surface = null;
        }

        if (isGpuBackend()) {
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

            // wrapBackendRenderTarget does NOT take ownership of the render target;
            // close it immediately to avoid leaking the native handle.
            renderTarget.close();

            if (surface == null) {
                throw new RuntimeException("Failed to recreate Skija surface");
            }
        } else {
            // Raster backend (headless/testing): recreate an off-screen surface.
            surface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));
            if (surface == null) {
                throw new RuntimeException("Failed to recreate raster surface");
            }
        }

        // Rebind the canvas wrapper to the new surface; keeping the old native
        // canvas would reference the closed surface (stale pointer).
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas.updateSurface(surface, skijaCanvas);
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
     * Returns true if using GPU backend, false for raster.
     * @return true if GPU backend is active
     */
    public boolean isGpuBackend() {
        return directContext != null;
    }

    /**
     * Gets the cached HiDPI scale factor along the X axis.
     *
     * @return the X DPI scale
     */
    public float getDpiScaleX() {
        return dpiScaleX;
    }

    /**
     * Gets the cached HiDPI scale factor along the Y axis.
     *
     * @return the Y DPI scale
     */
    public float getDpiScaleY() {
        return dpiScaleY;
    }

    /**
     * Renders a single frame. Exposed for testing purposes.
     */
    public void renderFrame() {
        render();
    }

    /**
     * Flips an image vertically using pure Skia drawing operations. Used for the
     * GPU backend where the surface origin is BOTTOM_LEFT.
     *
     * <p>The flip is done entirely on the native side via a temporary surface
     * (translate + negative-Y scale + drawImage + snapshot); pixel data never
     * crosses onto the Java heap.</p>
     *
     * @param image the image to flip (not closed by this method)
     * @return a new flipped image (caller must close it), or null on failure
     */
    private Image flipVertically(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();

        Surface tempSurface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));
        if (tempSurface == null) {
            System.err.println("Warning: Failed to create temporary raster surface");
            return null;
        }

        try {
            io.github.humbleui.skija.Canvas tempCanvas = tempSurface.getCanvas();
            // Move origin to the bottom-left, then mirror the Y axis so the image
            // is drawn upside-down into the temp surface.
            tempCanvas.translate(0, height);
            tempCanvas.scale(1, -1);
            tempCanvas.drawImage(image, 0, 0);
            return tempSurface.makeImageSnapshot();
        } catch (Exception e) {
            System.err.println("Error flipping image: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            tempSurface.close();
        }
    }

    /**
     * Captures the current frame to a PNG file.
     * For the GPU backend, the image is flipped vertically since Skija renders
     * with BOTTOM_LEFT origin.
     *
     * <p><b>Note:</b> this is an expensive operation (full render + GPU readback +
     * PNG encoding). It is intended for tests and debugging, not production use.</p>
     *
     * @param file the output file path
     * @throws RuntimeException if capture fails
     */
    public void captureToPng(java.io.File file) {
        // Render the current frame first
        renderFrame();

        // Flush GPU context if using GPU backend
        if (directContext != null) {
            directContext.flush();
        }

        // Create directory if it doesn't exist
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
        }

        // Take a snapshot of the surface
        Image image = surface.makeImageSnapshot();
        if (image == null) {
            throw new RuntimeException("Failed to create image snapshot");
        }

        Image imageToEncode = null;
        boolean needsFlip = isGpuBackend();

        try {
            // For GPU backend, flip the image vertically
            if (needsFlip) {
                imageToEncode = flipVertically(image);
                if (imageToEncode == null) {
                    throw new RuntimeException("Failed to flip image for GPU backend");
                }
            } else {
                imageToEncode = image;
            }

            // Encode to PNG format
            Data data = imageToEncode.encodeToData(EncodedImageFormat.PNG);

            // If encoding failed, try alternative approach
            if (data == null) {
                data = imageToEncode.encodeToData();
            }

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
            if (needsFlip && imageToEncode != null && imageToEncode != image) {
                imageToEncode.close();
            }
        }
    }

    /**
     * Runs the application main loop.
     *
     * <p>Uses {@code glfwWaitEventsTimeout} instead of busy-waiting: the thread
     * sleeps until either an event arrives or the remaining slice of the target
     * frame time elapses, so an idle application consumes essentially zero CPU.</p>
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

            // Wait for events, but wake up in time for the next frame at most.
            double waitTime = Math.max(0.0, targetFrameTime - deltaTime);
            glfwWaitEventsTimeout(waitTime);
            glfwPollEvents();

            currentTime = glfwGetTime();
            deltaTime = currentTime - lastFrameTime;
            if (deltaTime < targetFrameTime) {
                continue;
            }

            lastFrameTime = currentTime;

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
     * Cleans up resources and destroys the application. Safe to call multiple
     * times and safe to call after a partially failed {@code init()}.
     */
    public void destroy() {
        // Dispose all components in the root panel
        if (rootPanel != null) {
            rootPanel.dispose();
        }

        // Close the canvas wrapper first: it references the surface's native canvas,
        // which becomes invalid once the surface is closed.
        canvas = null;

        // Close the Skija surface BEFORE the DirectContext: the surface depends
        // on the context, so releasing the context first would leave a dangling
        // GPU-backed surface.
        if (surface != null) {
            surface.close();
            surface = null;
        }

        if (directContext != null) {
            directContext.close();
            directContext = null;
        }

        // Destroy window
        if (window != null) {
            window.destroy();
            window = null;
        }

        running = false;
    }
}
