package com.glyphui.core.backend;

import io.github.humbleui.skija.DirectContext;
import io.github.humbleui.skija.Surface;

import java.util.Objects;

/**
 * Immutable value returned by {@link SurfaceFactory} implementations: the
 * freshly created Skija {@link Surface}, the {@link DirectContext} that owns
 * it (null for CPU/raster backends), and the dimensions the application
 * should adopt — {@code logicalWidth}/{@code logicalHeight} for the canvas
 * wrapper and component tree, {@code physicalWidth}/{@code physicalHeight}
 * for the actual pixel buffer.
 */
public final class SurfaceResult {

    private final Surface surface;
    private final DirectContext directContext;
    private final int logicalWidth;
    private final int logicalHeight;
    private final int physicalWidth;
    private final int physicalHeight;

    public SurfaceResult(Surface surface, DirectContext directContext,
                         int logicalWidth, int logicalHeight) {
        this(surface, directContext, logicalWidth, logicalHeight,
             logicalWidth, logicalHeight);
    }

    public SurfaceResult(Surface surface, DirectContext directContext,
                         int logicalWidth, int logicalHeight,
                         int physicalWidth, int physicalHeight) {
        this.surface = Objects.requireNonNull(surface, "surface");
        this.directContext = directContext; // nullable: raster has no context
        this.logicalWidth = logicalWidth;
        this.logicalHeight = logicalHeight;
        this.physicalWidth = physicalWidth;
        this.physicalHeight = physicalHeight;
    }

    /** The created surface; the application takes over closing it. */
    public Surface surface() {
        return surface;
    }

    /** The owning GPU context, or null for CPU backends. */
    public DirectContext directContext() {
        return directContext;
    }

    /** Width in logical (DPI-independent) coordinates. */
    public int logicalWidth() {
        return logicalWidth;
    }

    /** Height in logical (DPI-independent) coordinates. */
    public int logicalHeight() {
        return logicalHeight;
    }

    /** Width in physical pixels of the allocated buffer. */
    public int physicalWidth() {
        return physicalWidth;
    }

    /** Height in physical pixels of the allocated buffer. */
    public int physicalHeight() {
        return physicalHeight;
    }
}
