package com.glyphui.reactive;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns a group of resources and releases them together, in registration order.
 *
 * <p>A failure while closing one resource does not prevent the others from
 * being closed: the first failure is rethrown at the end with the rest attached
 * as suppressed exceptions. A resource added after disposal is closed
 * immediately, so late registration cannot leak.</p>
 */
public final class DisposableScope implements Disposable {

    private final List<AutoCloseable> resources = new ArrayList<>();
    private boolean disposed;

    /**
     * Registers a resource.
     *
     * @param resource the resource to close on {@link #dispose()}
     * @param <C>      the resource type
     * @return {@code resource}, for chaining
     */
    public <C extends AutoCloseable> C add(C resource) {
        Objects.requireNonNull(resource, "resource");
        if (disposed) {
            close(resource);
        } else {
            resources.add(resource);
        }
        return resource;
    }

    /** @return the number of registered resources still to be closed */
    public int size() {
        return resources.size();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        List<AutoCloseable> toClose = new ArrayList<>(resources);
        resources.clear();
        RuntimeException failure = null;
        for (AutoCloseable resource : toClose) {
            try {
                close(resource);
            } catch (RuntimeException e) {
                failure = Reactor.merge(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }

    private static void close(AutoCloseable resource) {
        try {
            resource.close();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close " + resource, e);
        }
    }
}
