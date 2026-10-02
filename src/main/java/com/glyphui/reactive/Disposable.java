package com.glyphui.reactive;

/**
 * A resource with an explicit lifetime.
 *
 * <p>Glyph UI does not rely on garbage collection or weak references to
 * release observers, bindings or native handles: whoever creates a
 * {@code Disposable} is responsible for disposing it. {@code Disposable}
 * extends {@link AutoCloseable} so every disposable can be registered with
 * {@code Component.track(AutoCloseable)} or used in try-with-resources
 * without checked exceptions.</p>
 */
public interface Disposable extends AutoCloseable {

    /**
     * Releases this resource. Calling it again after the first call must have
     * no effect (idempotent).
     */
    void dispose();

    /**
     * Tells whether {@link #dispose()} has already been called.
     *
     * @return true once disposed
     */
    boolean isDisposed();

    /** Delegates to {@link #dispose()}. */
    @Override
    default void close() {
        dispose();
    }
}
