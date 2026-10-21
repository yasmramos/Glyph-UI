package com.glyphui;

import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.Assumptions;

/**
 * Centralized availability check for the Skija native library.
 *
 * <p>Some tests exercise real rendering/font measurement and therefore need
 * the Skija natives (libskija.so/dll/dylib plus its OpenGL/EGL dependencies)
 * to load successfully. On machines where they cannot be loaded (minimal CI
 * containers, missing libEGL, unsupported glibc), those tests must be
 * <em>skipped</em>, not failed.</p>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @BeforeEach
 * void requireSkija() {
 *     SkijaTestSupport.assumeAvailable();
 * }
 * }</pre>
 *
 * <p>The result is computed once per JVM and cached: probing with a 1x1
 * raster surface is cheap but not free, and native availability does not
 * change mid-run.</p>
 */
public final class SkijaTestSupport {

    private static final boolean AVAILABLE = probe();

    private SkijaTestSupport() {
    }

    /**
     * Probes native availability by creating and immediately closing a
     * minimal raster surface. Any throwable (UnsatisfiedLinkError, runtime
     * exceptions from a partially-loaded library) means "unavailable".
     */
    private static boolean probe() {
        try (Surface surface = Surface.makeRasterN32Premul(1, 1)) {
            return surface != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * @return true when Skija native libraries load on this machine
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Aborts the current test (as <em>skipped</em>, not failed) when Skija
     * natives are unavailable. Call from {@code @BeforeAll}/{@code @BeforeEach}
     * or at the top of individual tests that render or measure text.
     */
    public static void assumeAvailable() {
        Assumptions.assumeTrue(AVAILABLE,
                "Skija native library unavailable on this host; skipping test");
    }
}
