package com.glyphui.core.backend;

import com.glyphui.core.WindowConfig;
import com.glyphui.events.GlyphKeys;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.glfw.GLFWVidMode;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryStack.stackPush;

/**
 * Optional legacy {@link WindowBackend} built on LWJGL/GLFW.
 *
 * <p>This class contains the entire GLFW surface of the toolkit: after the
 * Phase&nbsp;1 window abstraction, no other production class references
 * {@code org.lwjgl} or GLFW constants directly. The code was moved verbatim
 * from the historical {@code Window} implementation (hints from
 * {@code WindowConfig} hints translation below, window creation, cached-size
 * callbacks, runtime attributes via {@code glfwSetWindowAttrib}, clipboard
 * through {@code glfwSetClipboardString}/{@code glfwGetClipboardString}).</p>
 *
 * <p>GLFW key codes and modifier bits already coincide with the toolkit's
 * internal vocabulary ({@link GlyphKeys}: Enter=257, Backspace=259, ...;
 * {@code GLFW_MOD_*} = {@code GlyphMods} bit layout), so this backend can
 * forward raw codes without a translation table — unlike JWM, which needs
 * {@link JwmWindowBackend#mapKey(int)}.</p>
 */
public class GlfwWindowBackend extends AbstractWindowBackend {

    private long windowHandle = MemoryUtil.NULL;

    // Native callback refs kept so they can be freed in destroy()
    private org.lwjgl.glfw.GLFWWindowSizeCallback windowSizeCbRef;
    private org.lwjgl.glfw.GLFWFramebufferSizeCallback framebufferSizeCbRef;
    private org.lwjgl.glfw.GLFWWindowContentScaleCallback contentScaleCbRef;
    private org.lwjgl.glfw.GLFWCursorPosCallback cursorPosCbRef;
    private org.lwjgl.glfw.GLFWCharCallback charCbRef;
    private org.lwjgl.glfw.GLFWKeyCallback keyCbRef;
    private org.lwjgl.glfw.GLFWMouseButtonCallback mouseButtonCbRef;
    private GLFWErrorCallback errorCallbackRef;

    public GlfwWindowBackend(String title, int width, int height, WindowConfig config) {
        super(title, width, height, config);
    }

    private static int glfwBool(boolean value) {
        return value ? GLFW_TRUE : GLFW_FALSE;
    }

    /**
     * Applies the configuration as GLFW window hints. Always starts from
     * {@code glfwDefaultWindowHints()} so hints leaked from a previous
     * window/configuration cannot bleed through. The numeric values of the
     * toolkit-neutral profile constants (WindowConfig.GL_PROFILE_*) coincide
     * with their GLFW counterparts, so they are forwarded directly.
     */
    private void applyGlfwHints(WindowConfig cfg) {
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_VISIBLE, glfwBool(cfg.visible));
        glfwWindowHint(GLFW_RESIZABLE, glfwBool(cfg.resizable));
        glfwWindowHint(GLFW_DECORATED, glfwBool(cfg.decorated));
        glfwWindowHint(GLFW_MAXIMIZED, glfwBool(cfg.maximized));
        glfwWindowHint(GLFW_FLOATING, glfwBool(cfg.floating));
        glfwWindowHint(GLFW_FOCUSED, glfwBool(cfg.focused));
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, glfwBool(cfg.transparentFramebuffer));
        glfwWindowHint(GLFW_SAMPLES, cfg.samples);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, cfg.glMajor);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, cfg.glMinor);
        glfwWindowHint(GLFW_OPENGL_PROFILE, cfg.glProfile);
        if (cfg.glForwardCompat) {
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE);
        }
    }

    /**
     * Returns the primary monitor's video mode for fullscreen creation, or
     * {@code null} when headless (no monitor attached) — in which case the
     * caller falls back to windowed mode instead of passing NULL to
     * {@code glfwGetVideoMode}.
     */
    private static GLFWVidMode fullscreenVideoMode() {
        long monitor = glfwGetPrimaryMonitor();
        if (monitor == MemoryUtil.NULL) {
            return null;
        }
        return glfwGetVideoMode(monitor);
    }

    @Override
    public boolean create() {
        // Setup error callback. Keep the CbRef so we can free it on failure
        // paths and in destroy() (a bare .set() would orphan the native stub).
        errorCallbackRef = GLFWErrorCallback.createPrint(System.err);
        errorCallbackRef.set();

        // Initialize GLFW
        if (!glfwInit()) {
            System.err.println("Unable to initialize GLFW");
            // Uninstall our callback (restores GLFW's default) and free its
            // native resources before bailing out.
            GLFWErrorCallback installed = glfwSetErrorCallback(null);
            if (installed != null) {
                installed.free();
            }
            errorCallbackRef = null;
            return false;
        }

        // Configure GLFW window hints from the (possibly user-supplied) config.
        // applyGlfwHints() starts with glfwDefaultWindowHints(), so no stale
        // hints can leak from a previous window in this process.
        applyGlfwHints(config);

        // Create the window (fullscreen on the primary monitor when asked and
        // a monitor is actually attached; otherwise windowed).
        GLFWVidMode fullscreenMode = config.fullscreen
            ? fullscreenVideoMode() : null;
        if (fullscreenMode != null) {
            windowHandle = glfwCreateWindow(
                fullscreenMode.width(), fullscreenMode.height(), title,
                glfwGetPrimaryMonitor(), MemoryUtil.NULL);
        } else {
            windowHandle = glfwCreateWindow(config.width, config.height, title,
                MemoryUtil.NULL, MemoryUtil.NULL);
        }
        if (windowHandle == MemoryUtil.NULL) {
            System.err.println("Failed to create GLFW window");
            // Clean up process-wide GLFW state AND our error callback before
            // returning, otherwise a re-init leaks the native callback stub.
            GLFWErrorCallback installed = glfwSetErrorCallback(null);
            if (installed != null) {
                installed.free();
            }
            errorCallbackRef = null;
            glfwTerminate();
            return false;
        }

        // Make OpenGL context current
        glfwMakeContextCurrent(windowHandle);

        // Enable vsync (configurable: 1 = on, 0 = off, N = every Nth refresh)
        glfwSwapInterval(config.swapInterval);

        // Initialize LWJGL OpenGL capabilities
        GL.createCapabilities();

        // Get actual logical window size and physical framebuffer size
        try (org.lwjgl.system.MemoryStack stack = stackPush()) {
            var lw = stack.mallocInt(1);
            var lh = stack.mallocInt(1);
            glfwGetWindowSize(windowHandle, lw, lh);
            this.windowWidth = lw.get(0);
            this.windowHeight = lh.get(0);

            var fbw = stack.mallocInt(1);
            var fbh = stack.mallocInt(1);
            glfwGetFramebufferSize(windowHandle, fbw, fbh);
            this.framebufferWidth = fbw.get(0);
            this.framebufferHeight = fbh.get(0);

            var xs = stack.mallocFloat(1);
            var ys = stack.mallocFloat(1);
            glfwGetWindowContentScale(windowHandle, xs, ys);
            this.contentScaleX = xs.get(0);
            this.contentScaleY = ys.get(0);
        }

        // Install internal GLFW callbacks that keep cached state in sync and
        // forward to user listeners. These are registered before any user
        // callback so that getWidth()/getFramebufferWidth() already reflect
        // the new sizes when Application's listeners run.
        windowSizeCbRef = org.lwjgl.glfw.GLFWWindowSizeCallback.create((w, width, height) -> {
            notifyWindowSize(width, height);
        }).set(windowHandle);

        framebufferSizeCbRef = org.lwjgl.glfw.GLFWFramebufferSizeCallback.create((w, width, height) -> {
            notifyFramebufferSize(width, height);
        }).set(windowHandle);

        contentScaleCbRef = org.lwjgl.glfw.GLFWWindowContentScaleCallback.create((w, xscale, yscale) -> {
            // DPI change (e.g. window moved between monitors with different scale)
            notifyContentScale(xscale, yscale);
        }).set(windowHandle);

        // Input plumbing: forward raw GLFW events into the neutral listener
        // API. GLFW key codes / mods already match GlyphKeys/GlyphMods.
        cursorPosCbRef = org.lwjgl.glfw.GLFWCursorPosCallback.create((w, xpos, ypos) -> {
            lastCursorX = xpos;
            lastCursorY = ypos;
            notifyCursorPos(xpos, ypos);
        }).set(windowHandle);

        charCbRef = org.lwjgl.glfw.GLFWCharCallback.create((w, codepoint) -> {
            notifyChar(codepoint);
        }).set(windowHandle);

        keyCbRef = org.lwjgl.glfw.GLFWKeyCallback.create((w, key, scancode, action, mods) -> {
            notifyKey(key, action != GLFW_RELEASE, mapNativeModifiers(mods));
        }).set(windowHandle);

        mouseButtonCbRef = org.lwjgl.glfw.GLFWMouseButtonCallback.create((w, button, action, mods) -> {
            // GLFW button ids: 0=left, 1=right, 2=middle — same as the
            // toolkit convention documented in WindowBackend.MouseListener.
            notifyMouseButton(button, action == GLFW_PRESS, lastCursorX, lastCursorY,
                mapNativeModifiers(mods));
        }).set(windowHandle);

        // Center the window (unless disabled, or already fullscreen).
        // Guard against headless setups: glfwGetPrimaryMonitor() returns NULL
        // with no display attached, and glfwGetVideoMode(NULL) is UB.
        // Note: Wayland ignores glfwSetWindowPos (client-side decorations);
        // that is a backend limitation, not something we can work around here.
        if (config.center && fullscreenMode == null) {
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
        }

        // Make the window visible (skipped when created hidden via
        // WindowConfig.visible(false); call show() yourself later).
        if (config.visible) {
            glfwShowWindow(windowHandle);
        }

        return true;
    }

    /** Cursor position tracked from the cursor-pos callback for mouse events. */
    private double lastCursorX;
    private double lastCursorY;

    @Override
    public void swapBuffers() {
        if (windowHandle != MemoryUtil.NULL) {
            glfwSwapBuffers(windowHandle);
        }
    }

    @Override
    public void pollEvents() {
        glfwPollEvents();
    }

    @Override
    public void waitEvents() {
        glfwWaitEvents();
    }

    @Override
    public void waitEventsTimeout(double seconds) {
        glfwWaitEventsTimeout(seconds);
    }

    @Override
    public void postEmptyEvent() {
        glfwPostEmptyEvent();
    }

    @Override
    public long getWindowHandle() {
        return windowHandle == MemoryUtil.NULL ? 0L : windowHandle;
    }

    @Override
    protected boolean hasLiveWindow() {
        return windowHandle != MemoryUtil.NULL;
    }

    @Override
    protected void applyNativeTitle(String title) {
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowTitle(windowHandle, title);
        }
    }

    @Override
    protected void applyNativeShouldClose(boolean shouldClose) {
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowShouldClose(windowHandle, shouldClose);
        }
    }

    @Override
    protected boolean queryNativeShouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    @Override
    protected int[] queryNativeFramebufferSize() {
        if (windowHandle == MemoryUtil.NULL) {
            return null;
        }
        int[] fbw = new int[1];
        int[] fbh = new int[1];
        glfwGetFramebufferSize(windowHandle, fbw, fbh);
        return new int[]{fbw[0], fbh[0]};
    }

    @Override
    public void show() {
        if (windowHandle != MemoryUtil.NULL) {
            glfwShowWindow(windowHandle);
        }
    }

    @Override
    public void hide() {
        if (windowHandle != MemoryUtil.NULL) {
            glfwHideWindow(windowHandle);
        }
    }

    @Override
    public void setDecorated(boolean decorated) {
        config.decorated = decorated;
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowAttrib(windowHandle, GLFW_DECORATED, decorated ? GLFW_TRUE : GLFW_FALSE);
        }
    }

    @Override
    public void setResizable(boolean resizable) {
        config.resizable = resizable;
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowAttrib(windowHandle, GLFW_RESIZABLE, resizable ? GLFW_TRUE : GLFW_FALSE);
        }
    }

    @Override
    public void setFloating(boolean floating) {
        config.floating = floating;
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowAttrib(windowHandle, GLFW_FLOATING, floating ? GLFW_TRUE : GLFW_FALSE);
        }
    }

    @Override
    public void setOpacity(float opacity) {
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowOpacity(windowHandle, Math.max(0f, Math.min(1f, opacity)));
        }
    }

    @Override
    public void setPosition(int x, int y) {
        this.posX = x;
        this.posY = y;
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowPos(windowHandle, x, y);
        }
    }

    @Override
    public int[] getPosition() {
        if (windowHandle != MemoryUtil.NULL) {
            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
                java.nio.IntBuffer px = stack.mallocInt(1);
                java.nio.IntBuffer py = stack.mallocInt(1);
                glfwGetWindowPos(windowHandle, px, py);
                posX = px.get(0);
                posY = py.get(0);
            } catch (Exception e) {
                // fall back to the cached value below
            }
        }
        return new int[]{posX, posY};
    }

    @Override
    public void setSize(int width, int height) {
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetWindowSize(windowHandle, width, height);
        } else {
            updateDimensions(width, height);
        }
    }

    @Override
    public void setMaximized(boolean maximized) {
        if (windowHandle != MemoryUtil.NULL) {
            if (maximized) {
                glfwMaximizeWindow(windowHandle);
            } else {
                glfwRestoreWindow(windowHandle);
            }
        }
    }

    @Override
    public void setFullscreen(boolean fullscreen) {
        if (windowHandle == MemoryUtil.NULL) {
            return;
        }
        long monitor = fullscreen ? glfwGetPrimaryMonitor() : MemoryUtil.NULL;
        if (fullscreen && monitor == MemoryUtil.NULL) {
            return; // headless: nothing to go fullscreen on
        }
        GLFWVidMode mode = fullscreen ? glfwGetVideoMode(monitor) : null;
        if (fullscreen && mode == null) {
            return;
        }
        // Only flip the config flag when the native call is actually applied
        // (headless contract shared with every backend).
        config.fullscreen = fullscreen;
        if (fullscreen) {
            glfwSetWindowMonitor(windowHandle, monitor, 0, 0,
                mode.width(), mode.height(), mode.refreshRate());
        } else {
            // Restore to the last known logical size at a fixed offset.
            glfwSetWindowMonitor(windowHandle, MemoryUtil.NULL, 100, 100,
                windowWidth, windowHeight, GLFW_DONT_CARE);
        }
    }

    @Override
    public void setClipboardString(String text) {
        if (windowHandle != MemoryUtil.NULL) {
            glfwSetClipboardString(windowHandle, text);
        }
    }

    @Override
    public String getClipboardString() {
        if (windowHandle != MemoryUtil.NULL) {
            return glfwGetClipboardString(windowHandle);
        }
        return null;
    }

    @Override
    public void destroy() {
        if (windowHandle == MemoryUtil.NULL && errorCallbackRef == null) {
            return; // already destroyed / never created
        }

        if (windowHandle != MemoryUtil.NULL) {
            // Free our window callbacks explicitly and null out the Java refs
            // so nobody can later invoke a stub pointing at freed native
            // memory. (glfwFreeCallbacks would do this too, but leaves the
            // fields dangling.)
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
            if (cursorPosCbRef != null) {
                cursorPosCbRef.free();
                cursorPosCbRef = null;
            }
            if (charCbRef != null) {
                charCbRef.free();
                charCbRef = null;
            }
            if (keyCbRef != null) {
                keyCbRef.free();
                keyCbRef = null;
            }
            if (mouseButtonCbRef != null) {
                mouseButtonCbRef.free();
                mouseButtonCbRef = null;
            }

            glfwDestroyWindow(windowHandle);
            windowHandle = MemoryUtil.NULL;
        }

        // Uninstall + free the GLFW error callback BEFORE glfwTerminate():
        // terminate resets GLFW's internal state, and touching the error
        // callback afterwards is not guaranteed to be well-defined.
        GLFWErrorCallback previousErrorCallback = glfwSetErrorCallback(null);
        if (previousErrorCallback != null) {
            previousErrorCallback.free();
        }
        errorCallbackRef = null;

        // LWJGL: drop the thread-local GL capabilities bound to the context
        // we just destroyed, otherwise function-pointer tables leak.
        GL.setCapabilities(null);

        glfwTerminate();
    }
}
