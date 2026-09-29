package com.glyphui.core;

import com.glyphui.graphics.Canvas;
import com.glyphui.ui.Component;
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

    /** When true, the GPU backend is skipped and a raster surface is used directly. */
    private boolean forceRasterSurface;

    /**
     * On-demand rendering flag: true when something changed (events, component
     * mutations, resize) and the next loop iteration should repaint.
     */
    private volatile boolean paintDirty;

    /**
     * Optional animation callback. While it returns true, the event loop keeps
     * repainting every frame (continuous animations). May be null.
     */
    private Runnable animationCallback;

    /** Sleep interval for the raster backend idle wait (no hardware vsync there). */
    private static final long RASTER_IDLE_SLEEP_MS = 16;

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
        this.forceRasterSurface = false;
        this.paintDirty = true; // first frame must always be painted
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
     * @param useRasterSurface if true, forces the raster backend and skips the GPU
     *                         initialization attempt entirely (for testing)
     * @return true if initialization was successful
     */
    public boolean init(String title, int width, int height, boolean useRasterSurface) {
        try {
            this.forceRasterSurface = useRasterSurface;

            // Create window
            window = new Window(title, width, height);
            if (!window.create()) {
                return false;
            }

            // Initialize Skija surface: try GPU first, automatically degrade to
            // raster when the GPU backend is unavailable (headless / no driver).
            if (useRasterSurface) {
                initRasterSurface();
            } else {
                try {
                    initSurface();
                } catch (Throwable gpuFailure) {
                    System.err.println("Warning: GPU backend unavailable ("
                        + gpuFailure.getMessage() + "). Falling back to raster surface.");
                    this.forceRasterSurface = true;
                    initRasterSurface();
                }
            }

            // Route repaint requests from components to this application
            Component.setRepaintRequester(this::requestRepaint);

            // Setup callbacks
            setupCallbacks();

            // Set initial root panel size
            rootPanel.setWidth(width);
            rootPanel.setHeight(height);

            lastFrameTime = glfwGetTime();
            running = true;
            paintDirty = true; // always paint the first frame

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

        // Create surface wrapping the OpenGL framebuffer.
        // Skija throws IllegalStateException when the GL context is not usable,
        // so wrap it to allow callers to fall back to raster.
        try {
            surface = Surface.wrapBackendRenderTarget(
                directContext,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getSRGB()
            );
        } catch (RuntimeException e) {
            throw new RuntimeException("Failed to wrap backend render target: " + e.getMessage(), e);
        }

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
     * Recreates a CPU-backed raster surface at the given size and rebinds it
     * to the existing canvas wrapper. Extracted from {@link #recreateSurface}
     * so tests can exercise the raster resize path without a GL context.
     *
     * @param width  the new width
     * @param height the new height
     */
    void recreateRasterSurface(int width, int height) {
        if (canvas == null) {
            throw new IllegalStateException("Canvas must be initialized before recreating a raster surface");
        }

        if (surface != null) {
            surface.close();
            surface = null;
        }

        surface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));

        if (surface == null) {
            throw new RuntimeException("Failed to recreate raster surface");
        }

        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas.setNativeCanvas(skijaCanvas, surface);
        canvas.resize(width, height);

        requestRepaint();
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
            requestRepaint();
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
            requestRepaint();
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
            requestRepaint();
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
            requestRepaint();
        };
        GLFWKeyCallback.create(keyCallback).set(windowHandle);
    }

    /**
     * Recreates the Skija surface after window resize.
     * Branches on the active backend: raster surfaces are recreated with
     * {@code Surface.makeRaster}, GPU surfaces wrap the window framebuffer.
     *
     * @param width  the new width
     * @param height the new height
     */
    private void recreateSurface(int width, int height) {
        if (surface != null) {
            surface.close();
            surface = null;
        }

        if (!isGpuBackend()) {
            // Raster backend: allocate a fresh CPU-backed surface at the new size
            recreateRasterSurface(width, height);
        } else {
            // GPU backend: wrap the window framebuffer in a new render target
            int[] fbIdArray = new int[1];
            GL11.glGetIntegerv(GL_FRAMEBUFFER_BINDING, fbIdArray);
            int fbId = fbIdArray[0];

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

            surface = Surface.wrapBackendRenderTarget(
                directContext,
                renderTarget,
                SurfaceOrigin.BOTTOM_LEFT,
                SurfaceColorFormat.RGBA_8888,
                ColorSpace.getSRGB()
            );

            // The wrapped surface takes ownership of the render target
            renderTarget.close();

            if (surface == null) {
                throw new RuntimeException("Failed to recreate Skija surface");
            }

            io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
            canvas.setNativeCanvas(skijaCanvas);
        }

        canvas.resize(width, height);

        // Update root panel size
        rootPanel.setWidth(width);
        rootPanel.setHeight(height);

        requestRepaint();
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
     * Flips an image vertically. Used for GPU backend where surface origin is BOTTOM_LEFT.
     * @param image the image to flip
     * @return a new flipped image (caller must close it)
     */
    private Image flipVertically(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        
        // For GPU images, we need to read pixels via DirectContext
        // Create a temporary raster surface to read into
        Surface tempSurface = Surface.makeRaster(ImageInfo.makeN32Premul(width, height));
        if (tempSurface == null) {
            System.err.println("Warning: Failed to create temporary raster surface");
            return null;
        }
        
        try {
            // Draw the original image onto the temp surface
            io.github.humbleui.skija.Canvas tempCanvas = tempSurface.getCanvas();
            tempCanvas.drawImage(image, 0, 0);
            
            // Now read pixels from the raster surface
            Bitmap bitmap = new Bitmap();
            bitmap.allocN32Pixels(width, height);
            
            try {
                if (!tempSurface.readPixels(bitmap, 0, 0)) {
                    System.err.println("Warning: Failed to read pixels from temp surface");
                    return null;
                }
                
                // Get pixel data as ByteBuffer
                java.nio.ByteBuffer buffer = bitmap.peekPixels();
                if (buffer == null) {
                    System.err.println("Warning: peekPixels returned null");
                    return null;
                }
                
                // Ensure buffer is at position 0
                buffer.rewind();
                
                // Read bytes from buffer
                byte[] pixels = new byte[buffer.remaining()];
                buffer.get(pixels);
                
                // Flip pixel data vertically
                int bytesPerPixel = 4; // RGBA
                int rowBytes = width * bytesPerPixel;
                byte[] flipped = new byte[pixels.length];
                
                for (int y = 0; y < height; y++) {
                    int srcRow = y * rowBytes;
                    int dstRow = (height - 1 - y) * rowBytes;
                    System.arraycopy(pixels, srcRow, flipped, dstRow, rowBytes);
                }
                
                // Create new image with flipped pixels
                ImageInfo info = ImageInfo.makeN32Premul(width, height);
                Image flippedImage = Image.makeRaster(info, flipped, rowBytes);
                if (flippedImage == null) {
                    System.err.println("Warning: Failed to create flipped image");
                    return null;
                }
                return flippedImage;
            } finally {
                bitmap.close();
            }
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
     * For GPU backend, flips the image vertically since Skija renders BOTTOM_LEFT.
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
     * Requests a repaint on the next event-loop iteration.
     * Widgets call this after state changes; safe to call from any thread
     * (the flag is volatile and setting it repeatedly is harmless).
     */
    public void requestRepaint() {
        paintDirty = true;
    }

    /**
     * Returns the current on-demand rendering state for testing/diagnostics.
     *
     * @return true if a repaint is pending
     */
    public boolean isPaintDirty() {
        return paintDirty;
    }

    /**
     * Sets an animation callback that keeps the loop repainting every frame.
     * Pass null to disable animations and return to pure on-demand rendering.
     *
     * @param animationCallback callback advanced once per loop iteration while set
     */
    public void setAnimationCallback(Runnable animationCallback) {
        this.animationCallback = animationCallback;
        if (animationCallback != null) {
            requestRepaint();
        }
    }

    /**
     * Returns true when the GPU (OpenGL) backend is active, false for raster.
     * The raster backend never creates a DirectContext, so a null context
     * identifies it reliably.
     *
     * @return true if GPU backend is active
     */
    public boolean isGpuBackend() {
        return directContext != null;
    }

    /**
     * Runs the application main loop with on-demand rendering:
     * frames are only produced when something changed (input events, component
     * mutations, resize) or an animation is active. On the GPU path there is no
     * manual FPS cap because glfwSwapInterval(1) in Window.create() provides
     * vsync; the raster path sleeps briefly while idle to avoid busy-waiting.
     */
    public void run() {
        if (!running) {
            return;
        }

        while (!window.shouldClose()) {
            // Poll events (callbacks may mark paintDirty)
            window.pollEvents();

            // Advance animation while one is registered (continuous repainting)
            boolean animating = (animationCallback != null);
            if (animating) {
                animationCallback.run();
            }

            if (paintDirty || animating) {
                lastFrameTime = glfwGetTime();
                render();
            } else if (!isGpuBackend()) {
                // Raster backend has no hardware vsync: sleep a short interval
                // so the loop does not consume CPU while idle.
                try {
                    Thread.sleep(RASTER_IDLE_SLEEP_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            // GPU backend with a clean frame: swapBuffers blocks on vsync during
            // painted frames, so no additional pacing is required here.
        }
    }

    /**
     * Renders the current frame and consumes the repaint request.
     */
    private void render() {
        paintDirty = false;

        // Clear canvas with background color
        canvas.clear(Color.makeARGB(255, 30, 30, 30));

        // Render root panel and all children
        rootPanel.render(canvas);

        // Flush drawing commands to the backend
        canvas.flush();

        if (isGpuBackend()) {
            // Flush DirectContext and present the frame via buffer swap
            directContext.flush();
            window.swapBuffers();
        }
        // Raster backend draws into a CPU surface; no presentation needed.
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
