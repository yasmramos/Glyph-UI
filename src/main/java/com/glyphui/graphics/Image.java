package com.glyphui.graphics;

import io.github.humbleui.skija.EncodedImageFormat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AutoCloseable wrapper around a native Skija {@code Image}.
 *
 * <p>Skija images are off-heap resources; this wrapper makes ownership
 * explicit so widgets and caches can release them deterministically via
 * {@link #close()} (idempotent).</p>
 *
 * <pre>{@code
 * try (Image img = Image.load("assets/logo.png")) {
 *     canvas.drawImage(img, 10, 10, img.getWidth(), img.getHeight());
 * }
 * }</pre>
 */
public class Image implements AutoCloseable {

    /** The wrapped native image; null after {@link #close()}. */
    private io.github.humbleui.skija.Image nativeImage;

    /**
     * Wraps an existing native Skija image. Ownership of the native object
     * transfers to this wrapper.
     *
     * @param nativeImage the native image (may be null for an empty wrapper)
     */
    Image(io.github.humbleui.skija.Image nativeImage) {
        this.nativeImage = nativeImage;
    }

    /**
     * Internal factory used by the toolkit and mirrored-package tests to wrap
     * a native Skija image. NOT public API since the 0.1 surface freeze:
     * application code should use {@link #load(String)},
     * {@link #loadResource(String)} or {@link #loadBytes(byte[])} instead, so
     * that {@code io.github.humbleui.skija.*} types never appear on the
     * supported API surface. Reached from outside this package through
     * {@code com.glyphui.core.internal.TestHooks#imageFromNative}.
     *
     * @param nativeImage the native image (may be null for an empty wrapper)
     * @return a wrapper owning the native image
     */
    static Image fromNativeForInternalUse(
            io.github.humbleui.skija.Image nativeImage) {
        return new Image(nativeImage);
    }

    /**
     * Wraps a native Skija image behind a fully opaque, non-native signature.
     * This is the supported seam for cross-package internal callers (e.g.
     * {@code com.glyphui.core.internal.TestHooks}) that need to construct
     * wrappers from raw handles without leaking {@code io.github.humbleui.*}
     * types onto the frozen public API surface.
     *
     * @param handle the native {@code io.github.humbleui.skija.Image}, passed
     *               as {@code Object}; must be null or that exact type
     * @return a wrapper owning the native image
     * @throws IllegalArgumentException if {@code handle} is not a native image
     */
    public static Image fromOpaqueHandle(Object handle) {
        if (handle == null) {
            return new Image(null);
        }
        if (!(handle instanceof io.github.humbleui.skija.Image)) {
            throw new IllegalArgumentException(
                    "Expected io.github.humbleui.skija.Image, got "
                            + handle.getClass().getName());
        }
        return new Image((io.github.humbleui.skija.Image) handle);
    }

    /**
     * Loads an image from a file path using {@code Image.makeFromEncoded}.
     *
     * @param path the file system path (PNG/JPEG/... supported by Skia codecs)
     * @return the loaded image
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if the bytes are not a valid image
     */
    public static Image load(String path) throws IOException {
        byte[] encoded = Files.readAllBytes(Path.of(path));
        return loadBytes(encoded);
    }

    /**
     * Loads an image bundled as a classpath resource.
     *
     * @param name absolute resource name, e.g. {@code /images/logo.png}
     * @return the loaded image
     * @throws IOException              if the resource is missing or unreadable
     * @throws IllegalArgumentException if the bytes are not a valid image
     */
    public static Image loadResource(String name) throws IOException {
        try (InputStream in = Image.class.getResourceAsStream(name)) {
            if (in == null) {
                throw new IOException("Image resource not found: " + name);
            }
            return loadBytes(in.readAllBytes());
        }
    }

    /**
     * Decodes an image from encoded bytes (PNG, JPEG, ...).
     *
     * @param encoded the encoded image bytes
     * @return the decoded image
     * @throws IllegalArgumentException if decoding fails
     */
    public static Image loadBytes(byte[] encoded) {
        io.github.humbleui.skija.Image img = io.github.humbleui.skija.Image.makeFromEncoded(encoded);
        if (img == null) {
            throw new IllegalArgumentException("Failed to decode image bytes");
        }
        return new Image(img);
    }

    /**
     * Checks whether this wrapper currently holds a native image.
     *
     * @return true if an image is loaded
     */
    public boolean isLoaded() {
        return nativeImage != null;
    }

    /**
     * Gets the image width in pixels.
     *
     * @return the width, or 0 when closed
     */
    public int getWidth() {
        return nativeImage != null ? nativeImage.getWidth() : 0;
    }

    /**
     * Gets the image height in pixels.
     *
     * @return the height, or 0 when closed
     */
    public int getHeight() {
        return nativeImage != null ? nativeImage.getHeight() : 0;
    }

    /**
     * Gets the wrapped native Skija image for direct drawing.
     *
     * @return the native image, or null when closed
     */
    io.github.humbleui.skija.Image nativeImageForInternalUse() {
        return nativeImage;
    }

    /**
     * Encodes this image as PNG bytes (used by tests and snapshot tooling).
     *
     * @return the PNG-encoded bytes
     * @throws IllegalStateException if the image is closed or encoding fails
     */
    public byte[] encodeToPng() {
        if (nativeImage == null) {
            throw new IllegalStateException("Image is closed");
        }
        try (io.github.humbleui.skija.Data data =
                     nativeImage.encodeToData(EncodedImageFormat.PNG)) {
            if (data == null) {
                throw new IllegalStateException("Failed to encode image to PNG");
            }
            return data.getBytes();
        }
    }

    /**
     * Releases the underlying native image. Safe to call multiple times.
     */
    @Override
    public void close() {
        if (nativeImage != null) {
            nativeImage.close();
            nativeImage = null;
        }
    }
}
