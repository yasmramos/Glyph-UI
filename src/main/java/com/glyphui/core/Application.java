package com.glyphui.core;

import com.glyphui.graphics.Canvas;
import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;
import com.glyphui.ui.TextField;
import com.glyphui.events.*;
import io.github.humbleui.skija.*;
import org.lwjgl.glfw.GLFWKeyCallback;
import org.lwjgl.glfw.GLFWMouseButtonCallback;
import org.lwjgl.glfw.GLFWCursorPosCallback;
import org.lwjgl.glfw.GLFWKeyCallbackI;
import org.lwjgl.glfw.GLFWMouseButtonCallbackI;
import org.lwjgl.glfw.GLFWCursorPosCallbackI;
import org.lwjgl.glfw.GLFWCharCallback;
import org.lwjgl.glfw.GLFWCharCallbackI;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL30.*;

/**
 * Main application class that manages the event loop, rendering, and window lifecycle.
 *
 * <p>{@code Application} owns every native resource created during
 * {@link #init()} (Skija surface, GPU context and GLFW window), so it
 * implements {@link AutoCloseable} and is intended to be used with
 * try-with-resources:</p>
 *
 * <pre>{@code
 * try (Application app = new Application()) {
 *     if (app.init("Glyph UI", 800, 600)) {
 *         app.getRootPanel().add(new Button("Click here!"));
 *         app.run();
 *     }
 * }
 * }</pre>
 *
 * <p>{@link #close()} releases resources in dependency order: the component
 * tree first (widgets close their paints/fonts), then the shared font cache,
 * then the Skija surface, then the DirectContext that created it, and finally
 * the GLFW window.</p>
 */
public class Application implements AutoCloseable {
    private Window window;
    private Surface surface;
    private Canvas canvas;
    private Panel rootPanel;
    private DirectContext directContext;
    private boolean running;
    private double lastFrameTime;
    private int targetFPS;
    private boolean useRasterSurface;

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

    /**
     * The UI thread: the thread that called {@link #run()}. All widget state
     * changes, layout and rendering happen on this thread. Null before the
     * loop starts.
     */
    private volatile Thread uiThread;

    /**
     * Queue of tasks posted from other threads via
     * {@link #invokeLater(Runnable)}. Consumed by the UI thread on every
     * event-loop iteration.
     */
    private final ConcurrentLinkedQueue<Runnable> uiTaskQueue = new ConcurrentLinkedQueue<>();

    // Mouse state
    private double mouseX;
    private double mouseY;
    private boolean[] mouseButtons = new boolean[10];

    /**
     * The most recently started application instance, used by widgets to
     * obtain the UI-thread marshalling target lazily (properties are created
     * on first access, which may happen before {@code init()} completes).
     * Cleared when the application closes. package-private for tests.
     */
    static volatile Application current;

    /**
     * Returns the currently running application instance, or {@code null} in
     * headless/test contexts where no application has been started. Widget
     * properties use this to marshal cross-thread sets automatically.
     *
     * @return the current application, or null
     */
    public static Application getCurrent() {
        return current;
    }

    /**
     * Creates a new Application.
     */
    public Application() {
        this.targetFPS = 60;
        this.running = false;
        this.forceRasterSurface = false;
        this.paintDirty = true; // first frame must always be painted
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

            // Set initial root panel size to the LOGICAL window size
            rootPanel.setWidth(window.getWidth());
            rootPanel.setHeight(window.getHeight());

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
     *
     * <p>Coordinate policy: mouse/cursor coordinates from GLFW are in the
     * logical window space and are propagated unchanged to the component
     * tree (which is also in logical space thanks to the content-scale
     * canvas transform). No manual fb/window ratio conversion is needed.</p>
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
            requestRepaint();
        };
        GLFWMouseButtonCallback.create(mouseButtonCallback).set(windowHandle);

        // Cursor position callback — GLFW reports LOGICAL coordinates, which
        // match the logical-space component tree directly (HiDPI-safe)
        GLFWCursorPosCallbackI cursorCallback = (w, xpos, ypos) -> {
            mouseX = xpos;
            mouseY = ypos;

            MouseEvent event = new MouseEvent(MouseEventType.MOVE, (int) mouseX, (int) mouseY, MouseButton.LEFT, 0);
            rootPanel.onMouseEvent(event);
            requestRepaint();
        };
        GLFWCursorPosCallback.create(cursorCallback).set(windowHandle);

        // Focus manager: owns Tab / Shift+Tab traversal and the focused widget
        focusManager = new FocusManager(this::requestRepaint);
        focusManager.setRoot(rootPanel);
        FocusManager.setGlobalFocusManager(focusManager);

        // Clipboard bridge for TextField (copy/paste via GLFW clipboard API)
        TextField.setClipboardHooks(
            () -> {
                Component focused = focusManager.getFocused();
                if (focused instanceof TextField) {
                    String sel = ((TextField) focused).getSelectedTextOrNull();
                    if (sel != null) {
                        glfwSetClipboardString(windowHandle, sel);
                    }
                }
            },
            () -> {
                String clip = glfwGetClipboardString(windowHandle);
                if (clip != null && !clip.isEmpty()) {
                    Component focused = focusManager.getFocused();
                    if (focused instanceof TextField) {
                        ((TextField) focused).insertText(clip);
                    }
                }
            });

        // Key callback -- Tab / Shift+Tab are intercepted by the FocusManager;
        // every other key is dispatched down the tree (widgets only react
        // when they hold the keyboard focus).
        GLFWKeyCallbackI keyCallback = (w, key, scancode, action, mods) -> {
            if (action == GLFW_RELEASE && key == GLFW_KEY_ESCAPE) {
                window.setShouldClose(true);
                return;
            }

            if (key == GLFW_KEY_TAB && (action == GLFW_PRESS || action == GLFW_REPEAT)) {
                boolean shift = (mods & GLFW_MOD_SHIFT) != 0;
                if (shift) {
                    focusManager.focusPrevious();
                } else {
                    focusManager.focusNext();
                }
                return; // Tab never reaches individual widgets
            }

            KeyEventType type = (action == GLFW_PRESS) ? KeyEventType.PRESS : KeyEventType.RELEASE;
            EnumSet<KeyModifier> modifiers = getModifiers(mods);

            char keyChar = (action == GLFW_RELEASE) ? 0 : mapKeyToChar(key);
            KeyEvent event = new KeyEvent(type, key, keyChar, modifiers);
            rootPanel.onKeyEvent(event);
            requestRepaint();
        };
        GLFWKeyCallback.create(keyCallback).set(windowHandle);

        // Char callback -- printable text input goes straight to the focused
        // TextField. NOTE: GLFW has no IME API, so real input-method
        // composition (CJK candidate windows, dead-key composition) is not
        // supported in v0.1; see README.
        GLFWCharCallbackI charCallback = (w, codepoint) -> {
            Component focused = focusManager.getFocused();
            if (focused instanceof TextField) {
                ((TextField) focused).onCharTyped((char) codepoint);
            }
        };
        GLFWCharCallback.create(charCallback).set(windowHandle);
    }

    /**
     * Maps the most common special keys to control characters so widgets can
     * recognize them through {@link KeyEvent#getKeyChar()} without depending
     * on LWJGL constants. Printable characters arrive through the GLFW char
     * callback instead; unmapped keys produce NUL.
     *
     * @param glfwKey the GLFW key code
     * @return the mapped character, or 0 when there is no mapping
     */
    private static char mapKeyToChar(int glfwKey) {
        switch (glfwKey) {
            case GLFW_KEY_ENTER:
            case GLFW_KEY_KP_ENTER:
                return '\r';
            case GLFW_KEY_TAB:
                return '\t';
            case GLFW_KEY_BACKSPACE:
                return 8;
            case GLFW_KEY_ESCAPE:
                return 27;
            default:
                return 0;
        }
    }

    /**
     * Recreates the Skija surface after window resize.
     * Branches on the active backend: raster surfaces are recreated with
     * {@code Surface.makeRaster}, GPU surfaces wrap the window framebuffer.
     *
     * @param fbWidth  the new physical framebuffer width
     * @param fbHeight the new physical framebuffer height
     */
    private void recreateSurface(int fbWidth, int fbHeight) {
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
     * Returns the thread that started {@link #run()} (the UI thread), or
     * {@code null} if the event loop has not started yet.
     *
     * @return the UI thread, or null
     */
    public Thread getUiThread() {
        return uiThread;
    }

    /**
     * Returns true when the calling thread is the UI thread. Before
     * {@link #run()} starts there is no UI thread yet, so widget mutation
     * (building the initial tree) is also considered "on the UI thread".
     *
     * @return true if it is safe to mutate widgets directly from this thread
     */
    public boolean isUiThread() {
        Thread thread = uiThread;
        return thread == null || thread == Thread.currentThread();
    }

    /**
     * Throws {@link IllegalStateException} when called from a thread other
     * than the UI thread. Used by native-resource-owning APIs (fonts, paints)
     * to fail fast instead of corrupting Skija state.
     */
    public void checkThread() {
        if (!isUiThread()) {
            throw new IllegalStateException(
                "Operation must run on the Glyph UI thread; use Application.invokeLater(...) "
                + "or Property.set(...) which marshals automatically.");
        }
    }

    /**
     * Schedules a task to run on the UI thread during the next event-loop
     * iteration. If called from the UI thread the task runs immediately.
     *
     * <p>This is the escape hatch for composite operations that cannot be
     * expressed as a single property change. Most callers do not need it:
     * {@link com.glyphui.graphics.Property#set(Object)} marshals through this
     * method automatically.</p>
     *
     * <p>The posted task marks the frame dirty and wakes up a UI thread
     * blocked in {@code glfwWaitEvents()} via {@code glfwPostEmptyEvent()},
     * so it executes promptly even when the window is idle.</p>
     *
     * @param task the runnable to execute on the UI thread
     */
    public void invokeLater(Runnable task) {
        if (task == null) {
            return;
        }
        if (isUiThread()) {
            task.run();
            return;
        }
        uiTaskQueue.add(task);
        requestRepaint();
        // Wake the UI thread if it is blocked waiting for events. Only valid
        // once GLFW is initialized; before that the queue is drained on the
        // first run() iteration anyway.
        if (window != null) {
            glfwPostEmptyEvent();
        }
    }

    /**
     * Drains the UI task queue on the UI thread. Exceptions from user tasks
     * are reported but do not stop the loop or the remaining tasks.
     */
    /**
     * Package-private test hook: drains the posted-task queue on the calling
     * thread and returns how many tasks ran. Used by unit tests that simulate
     * the UI loop without GLFW/Skija initialization.
     *
     * @return number of tasks executed
     */
    public int drainUiTasksForTests() {
        int count = 0;
        Runnable task;
        while ((task = uiTaskQueue.poll()) != null) {
            count++;
            try {
                task.run();
            } catch (RuntimeException e) {
                System.err.println("Exception in posted UI task: " + e.getMessage());
            }
        }
        return count;
    }

    private void drainUiTasks() {
        Runnable task;
        while ((task = uiTaskQueue.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                System.err.println("Exception in UI task: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    /**
     * Runs all tasks queued on the static current-application reference.
     * This lets widgets whose properties were created before {@code run()}
     * (and therefore captured a null application) still marshal background
     * property sets onto the UI thread through {@link #invokeOnCurrent}.
     */
    private static void drainCurrent() {
        Application app = current;
        if (app == null) {
            return;
        }
        Runnable task;
        while ((task = app.uiTaskQueue.poll()) != null) {
            try {
                task.run();
            } catch (RuntimeException e) {
                System.err.println("Exception in posted UI task: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Queues a task on the current application's UI-thread queue and wakes up
     * its event loop. Used by {@link com.glyphui.graphics.Property} when the
     * widget captured no application instance at creation time. A no-op when
     * no application is running (headless/test contexts).
     *
     * @param task the runnable to execute on the UI thread
     */
    public static void invokeOnCurrent(Runnable task) {
        if (task == null) {
            return;
        }
        Application app = current;
        if (app != null) {
            app.invokeLater(task);
        }
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
        uiThread = Thread.currentThread();
        // Publish this instance so lazily created widget properties can find
        // the marshalling target (see Application.getCurrent()).
        current = this;

        while (!window.shouldClose()) {
            // Poll events (callbacks may mark paintDirty)
            window.pollEvents();

            // Run tasks posted from other threads via invokeLater(...)
            drainUiTasks();
            drainCurrent();

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
            } else {
                // GPU backend with a clean frame: block in glfwWaitEvents()
                // instead of busy-polling. The loop is woken up by real input
                // events or by glfwPostEmptyEvent() from invokeLater(...).
                window.waitEvents();
            }
        }
    }

    /**
     * Renders the current frame and consumes the repaint request.
     */
    private void render() {
        paintDirty = false;

        // Clear canvas with background color
        canvas.clear(Color.makeARGB(255, 30, 30, 30));

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
     *
     * <p>Release order follows native-resource ownership: the component tree
     * first (widgets close their paints/fonts), then the shared font cache,
     * then the Skija surface, then the {@code DirectContext} that created it,
     * and finally the GLFW window. This method is idempotent.</p>
     */
    @Override
    public void close() {
        running = false;
        if (current == this) {
            current = null;
        }

        // Dispose all components in the root panel (cascades to children)
        if (rootPanel != null) {
            rootPanel.dispose();
        }

        // Release the shared typeface cache after every widget font is closed
        com.glyphui.graphics.Fonts.close();

        // Close the surface before the context that owns it
        if (surface != null) {
            surface.close();
            surface = null;
        }

        if (directContext != null) {
            directContext.close();
            directContext = null;
        }

        // Destroy window (GLFW) last
        if (window != null) {
            window.destroy();
        }
    }

    /**
     * Legacy alias for {@link #close()}, kept for backward compatibility.
     * New code should prefer try-with-resources on {@code Application}.
     */
    public void destroy() {
        close();
    }
}
