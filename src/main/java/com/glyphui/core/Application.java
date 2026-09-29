package com.glyphui.core;

import com.glyphui.graphics.Canvas;
import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;
import com.glyphui.events.*;
import io.github.humbleui.skija.*;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.EnumSet;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Main application class that manages the event loop, rendering, and window lifecycle.
 *
 * <p>HiDPI: the GPU render target ({@link BackendRenderTarget}) uses the
 * <b>physical</b> framebuffer size, while the {@link Canvas} wrapper and the
 * {@code rootPanel} live in <b>logical</b> coordinates. Before each paint the
 * native canvas is scaled by the window content scale (inside save/restore),
 * so logical units map to physical pixels and GLFW mouse coordinates (which
 * are logical) match the component tree without any conversion.</p>
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
    private boolean useRasterSurface;

    /**
     * Paint-dirty flag: when true the next loop iteration renders a frame.
     * Set via {@link #requestRepaint()} (e.g. from {@code Component.invalidate()}).
     * Guarded by this Application instance as monitor.
     */
    private volatile boolean paintDirty = true;

    // Mouse state (logical/window coordinates, as reported by GLFW)
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
        // Propagate component invalidations up to requestRepaint()
        Component.setGlobalRepaintRequester(this::requestRepaint);
    }

    /**
     * Marks the display dirty so the next loop iteration renders a new frame.
     * Safe to call from other threads (e.g. from {@code invokeLater} tasks).
     */
    public void requestRepaint() {
        synchronized (this) {
            paintDirty = true;
            // Wake up the loop if it is blocked in glfwWaitEvents()
            if (window != null && running) {
                window.postEmptyEvent();
            }
        }
    }

    /**
     * Returns the current paint-dirty state (mainly for tests).
     *
     * @return true when a repaint has been requested but not yet performed
     */
    public boolean isPaintDirty() {
        return paintDirty;
    }

    /**
     * Installs a repaint requester as the global hook so that any component
     * {@code invalidate()} reaching the root of the tree marks this
     * application paint-dirty. {@link Component#invalidate()} propagates up
     * the parent chain until it reaches the top-level panel, which then
     * invokes this requester (see {@link #requestRepaint()}).
     *
     * <p>The {@link #Application()} constructor already installs this
     * application as the global requester; this setter allows re-pointing the
     * hook (e.g. when nesting applications in tests or restoring a previous
     * requester).</p>
     *
     * @param requester the repaint requester (may be null to clear)
     */
    public void setRepaintRequester(RepaintRequester requester) {
        Component.setGlobalRepaintRequester(requester);
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
            this.useRasterSurface = useRasterSurface;
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

            // Set initial root panel size to the LOGICAL window size
            rootPanel.setWidth(window.getWidth());
            rootPanel.setHeight(window.getHeight());

            lastFrameTime = glfwGetTime();
            running = true;
            paintDirty = true;

            return true;
        } catch (Exception e) {
            System.err.println("Failed to initialize application: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Initializes the Skija surface with GPU backend.
     *
     * <p>The {@link BackendRenderTarget} is created with the <b>physical</b>
     * framebuffer dimensions; the {@link Canvas} wrapper (and therefore the
     * component tree) uses the <b>logical</b> window dimensions.</p>
     */
    private void initSurface() {
        int fbWidth = window.getFramebufferWidth();
        int fbHeight = window.getFramebufferHeight();
        int logicalWidth = window.getWidth();
        int logicalHeight = window.getHeight();

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
        
        // Create BackendRenderTarget for the OpenGL framebuffer using the
        // PHYSICAL (framebuffer) size — HiDPI: this is larger than logical
        // Parameters: width, height, samples, stencil, fbId, format (GR_GL_RGBA8 = 0x8058)
        BackendRenderTarget renderTarget = BackendRenderTarget.makeGL(
            fbWidth, 
            fbHeight, 
            0,      // samples
            0,      // stencil
            fbId, 
            0x8058  // GL_RGBA8 constant
        );

        if (renderTarget == null) {
            throw new RuntimeException("Failed to create BackendRenderTarget");
        }

        // Create surface wrapping the OpenGL framebuffer (physical pixels)
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

        // Create canvas wrapper in LOGICAL coordinates; the content-scale
        // transform is applied per-frame in render()
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas = new Canvas(skijaCanvas, surface, logicalWidth, logicalHeight);
    }

    /**
     * Initializes the Skija surface with raster backend (for testing).
     *
     * <p>The raster surface is allocated at the <b>physical</b> framebuffer
     * resolution; the canvas wrapper reports <b>logical</b> size and the
     * content scale is applied per-frame in render(), exactly like the GPU
     * path.</p>
     */
    private void initRasterSurface() {
        int fbWidth = window.getFramebufferWidth();
        int fbHeight = window.getFramebufferHeight();
        int logicalWidth = window.getWidth();
        int logicalHeight = window.getHeight();

        // Create raster surface at physical resolution (no OpenGL context needed).
        // Skija raster surfaces always store rows top-to-bottom, so pixel
        // sampling matches the UI coordinate system without any flip.
        surface = Surface.makeRasterN32Premul(fbWidth, fbHeight);
        
        if (surface == null) {
            throw new RuntimeException("Failed to create raster surface");
        }

        // Create canvas wrapper in LOGICAL coordinates
        io.github.humbleui.skija.Canvas skijaCanvas = surface.getCanvas();
        canvas = new Canvas(skijaCanvas, surface, logicalWidth, logicalHeight);
    }

    /**
     * Sets up GLFW callbacks for events with HiDPI support.
     *
     * <p>Coordinate policy: mouse/cursor coordinates from GLFW are in the
     * logical window space and are propagated unchanged to the component
     * tree (which is also in logical space thanks to the content-scale
     * canvas transform). No manual fb/window ratio conversion is needed.</p>
     */
    private void setupCallbacks() {
        long windowHandle = window.getWindowHandle();

        // Physical framebuffer resize: recreate the GPU surface with the new
        // PHYSICAL sizes (Window already updated its cached values via its
        // internal callback before this listener runs)
        window.setFramebufferSizeListener((w, fbWidth, fbHeight) -> {
            if (useRasterSurface) {
                recreateRasterSurface();
            } else {
                recreateSurface(fbWidth, fbHeight);
            }
            requestRepaint();
        });

        // Logical window resize: update rootPanel + canvas logical size only
        window.setWindowSizeListener((w, width, height) -> {
            canvas.resize(width, height);
            rootPanel.setWidth(width);
            rootPanel.setHeight(height);
            requestRepaint();
        });

        // Content scale (DPI) change — e.g. window moved across monitors:
        // only the per-frame scale factor changes; render() reads it live,
        // so we just need a repaint.
        window.setContentScaleListener((w, xscale, yscale) -> requestRepaint());

        // Mouse button callback — coordinates stay in LOGICAL space
        GLFWMouseButtonCallbackI mouseButtonCallback = (w, button, action, mods) -> {
            MouseButton glyphButton = convertMouseButton(button);
            MouseEventType type = (action == GLFW_PRESS) ? MouseEventType.PRESS : MouseEventType.RELEASE;
            
            mouseButtons[button] = (action == GLFW_PRESS);

            MouseEvent event = new MouseEvent(type, (int) mouseX, (int) mouseY, glyphButton, 1);
            rootPanel.onMouseEvent(event);
        };
        GLFWMouseButtonCallback.create(mouseButtonCallback).set(windowHandle);

        // Cursor position callback — GLFW reports LOGICAL coordinates, which
        // match the logical-space component tree directly (HiDPI-safe)
        GLFWCursorPosCallbackI cursorCallback = (w, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;

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
     * Recreates the GPU surface after a framebuffer (physical) resize.
     * The render target uses the new PHYSICAL sizes; the canvas wrapper and
     * root panel keep using the current LOGICAL sizes.
     *
     * @param fbWidth  the new physical framebuffer width
     * @param fbHeight the new physical framebuffer height
     */
    private void recreateSurface(int fbWidth, int fbHeight) {
        if (surface != null) {
            surface.close();
        }

        // Get framebuffer ID (0 for default framebuffer)
        int[] fbIdArray = new int[1];
        GL11.glGetIntegerv(GL_FRAMEBUFFER_BINDING, fbIdArray);
        int fbId = fbIdArray[0];
        
        // Create BackendRenderTarget for the OpenGL framebuffer with the new
        // PHYSICAL size
        // Parameters: width, height, samples, stencil, fbId, format (GR_GL_RGBA8 = 0x8058)
        BackendRenderTarget renderTarget = BackendRenderTarget.makeGL(
            fbWidth, 
            fbHeight, 
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
        // Canvas wrapper stays in LOGICAL coordinates
        int logicalWidth = window.getWidth();
        int logicalHeight = window.getHeight();
        canvas = new Canvas(skijaCanvas, surface, logicalWidth, logicalHeight);

        // Update root panel size (logical)
        rootPanel.setWidth(logicalWidth);
        rootPanel.setHeight(logicalHeight);
    }

    /**
     * Recreates the raster surface after a framebuffer (physical) resize.
     * Same logical/physical split as the GPU path.
     */
    private void recreateRasterSurface() {
        if (surface != null) {
            surface.close();
        }
        initRasterSurface();

        // Update root panel size (logical)
        rootPanel.setWidth(window.getWidth());
        rootPanel.setHeight(window.getHeight());
    }

    /**
     * Test hook: recreates the raster surface so the render target picks up
     * the window's current PHYSICAL framebuffer size (e.g. after simulating
     * a HiDPI content-scale change headlessly).
     */
    public void recreateRasterSurfaceForTesting() {
        recreateRasterSurface();
    }

    /**
     * Gets the underlying GLFW window wrapper.
     *
     * @return the window, or null before {@link #init()} succeeds
     */
    public Window getWindow() {
        return window;
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
     * Returns true if using GPU backend, false for raster.
     * @return true if GPU backend is active
     */
    public boolean isGpuBackend() {
        return directContext != null;
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
     * Runs the application main loop.
     *
     * <p>When nothing is dirty ({@code paintDirty == false}) the loop blocks
     * in {@link Window#waitEvents()} instead of busy-polling, so an idle UI
     * consumes zero CPU. Any OS event — or a {@code glfwPostEmptyEvent()}
     * triggered by {@link #requestRepaint()} from another thread — wakes it.</p>
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

            // Process events: block when there is nothing to repaint (zero
            // CPU at rest); poll normally when a repaint is pending.
            if (paintDirty) {
                window.pollEvents();
            } else {
                window.waitEvents();
                window.pollEvents();
            }

            // Render only when something changed
            if (paintDirty) {
                render();
                paintDirty = false;
            }
        }
    }

    /**
     * Renders the current frame.
     *
     * <p>HiDPI: the surface covers the physical framebuffer, so before
     * painting the (logical-coordinate) component tree the native canvas is
     * scaled by the window content scale, wrapped in save/restore.</p>
     */
    private void render() {
        float contentScaleX = window != null ? window.getContentScaleX() : 1.0f;
        float contentScaleY = window != null ? window.getContentScaleY() : 1.0f;

        io.github.humbleui.skija.Canvas nativeCanvas = canvas.getNativeCanvas();
        int saveCount = nativeCanvas.save();
        try {
            // Clear canvas with background color (covers full physical surface)
            nativeCanvas.clear(Color.makeARGB(255, 30, 30, 30));

            // HiDPI: map logical UI coordinates to physical device pixels
            nativeCanvas.scale(contentScaleX, contentScaleY);

            // Render root panel and all children (in logical coordinates)
            rootPanel.render(canvas);
        } finally {
            nativeCanvas.restoreToCount(saveCount);
        }

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
