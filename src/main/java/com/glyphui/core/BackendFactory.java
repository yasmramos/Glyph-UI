package com.glyphui.core;

import com.glyphui.core.backend.GlfwWindowBackend;
import com.glyphui.core.backend.WindowBackend;

/**
 * Selects the {@link WindowBackend} implementation used by {@link Window}.
 *
 * <p>Resolution order:</p>
 * <ol>
 *   <li>System property {@code glyphui.backend}:
 *       <ul>
 *         <li>{@code glfw} — use the legacy LWJGL/GLFW backend directly.</li>
 *         <li>{@code jwm} — use the JWM backend (reflectively, so the toolkit
 *             does not hard-depend on {@code io.github.humbleui:jwm}; add it
 *             to your classpath).</li>
 *         <li>any other value — a fully-qualified class name implementing
 *             {@link WindowBackend} with a
 *             {@code (String title, int width, int height, WindowConfig config)}
 *             constructor.</li>
 *       </ul></li>
 *   <li>JWM backend if {@code io.github.humbleui.jwm.App} 
 *       class) is on the classpath — see the default list below.</li>
 *   <li>Fallback: the GLFW backend.</li>
 * </ol>
 *
 * <p>The default backend is GLFW for now; once the JWM dependency ships in
 * the POM the factory prefers JWM automatically when present.</p>
 */
public final class BackendFactory {

    /** Class checked to detect the JWM library on the classpath. */
    private static final String JWM_PROBE_CLASS = "io.github.humbleui.jwm.App";

    /** Reflective backend implementation used when JWM is available. */
    private static final String JWM_BACKEND_CLASS =
        "com.glyphui.core.backend.JwmWindowBackend";

    private BackendFactory() {
    }

    /**
     * Creates a new window backend for the given parameters.
     *
     * @param title  initial window title
     * @param width  initial logical width
     * @param height initial logical height
     * @param config window configuration (never null)
     * @return a fresh, not-yet-created backend
     */
    public static WindowBackend create(String title, int width, int height,
                                       WindowConfig config) {
        String choice = System.getProperty("glyphui.backend", "").trim().toLowerCase();
        switch (choice) {
            case "glfw":
                return new GlfwWindowBackend(title, width, height, config);
            case "jwm":
                return instantiate(JWM_BACKEND_CLASS, title, width, height, config);
            case "":
                // auto-detect below
                break;
            default:
                return instantiate(choice, title, width, height, config);
        }

        // Auto: prefer JWM when its classes are on the classpath.
        if (isClassPresent(JWM_PROBE_CLASS)) {
            try {
                return instantiate(JWM_BACKEND_CLASS, title, width, height, config);
            } catch (RuntimeException e) {
                System.err.println("Glyph UI: JWM backend unavailable ("
                    + e.getMessage() + "); falling back to GLFW.");
            }
        }
        return new GlfwWindowBackend(title, width, height, config);
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, BackendFactory.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static WindowBackend instantiate(String className, String title,
                                             int width, int height,
                                             WindowConfig config) {
        try {
            Class<?> clazz = Class.forName(className, true,
                BackendFactory.class.getClassLoader());
            return (WindowBackend) clazz
                .getConstructor(String.class, int.class, int.class, WindowConfig.class)
                .newInstance(title, width, height, config);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Unable to instantiate Glyph UI window backend '" + className
                    + "'. Is the dependency on the classpath?", e);
        }
    }
}
