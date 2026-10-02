package com.glyphui.reactive;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Base class for observers that own subscriptions to the sources they
 * depend on.
 *
 * <p>Subclasses implement {@link #onInvalidate()}. Dependencies are added
 * either explicitly with {@link #observe(Trackable)} or discovered while
 * {@link #tracked(Supplier)} runs. After each tracked run the subscriptions
 * to sources that were <em>not</em> read again are closed, which is how
 * conditional dependencies are re-subscribed dynamically. {@link #dispose()}
 * closes every subscription.</p>
 */
public abstract class AbstractObserver implements Observer, Disposable {

    private boolean disposed;
    private Map<Trackable<?>, Trackable.Subscription> dependencies = new HashMap<>();
    /** Subscriptions of the previous run, non-null only while {@link #tracked} runs. */
    private Map<Trackable<?>, Trackable.Subscription> stale;

    /** Ignores invalidations once disposed, otherwise delegates to {@link #onInvalidate()}. */
    @Override
    public final void invalidate() {
        if (!disposed) {
            onInvalidate();
        }
    }

    /** Called when one of the observed sources changed. */
    protected abstract void onInvalidate();

    /**
     * Subscribes to {@code source} unless this observer already depends on it.
     *
     * @param source the source to depend on
     */
    protected final void observe(Trackable<?> source) {
        Objects.requireNonNull(source, "source");
        if (disposed || dependencies.containsKey(source)) {
            return;
        }
        Trackable.Subscription subscription = stale != null ? stale.remove(source) : null;
        if (subscription == null) {
            subscription = source.subscribe(this);
        }
        dependencies.put(source, subscription);
    }

    /**
     * Runs {@code body} as the current observer: every source it reads becomes
     * a dependency, and dependencies of the previous run that were not read
     * again are unsubscribed. If {@code body} throws, the previous
     * subscriptions are kept so later changes still reach this observer.
     *
     * @param body the code to evaluate
     * @param <R>  the result type
     * @return the result of {@code body}
     */
    protected final <R> R tracked(Supplier<? extends R> body) {
        Map<Trackable<?>, Trackable.Subscription> outerStale = stale;
        stale = dependencies;
        dependencies = new HashMap<>();
        Observer outer = Reactor.swapCurrent(this);
        boolean completed = false;
        try {
            R result = body.get();
            completed = true;
            return result;
        } finally {
            Reactor.swapCurrent(outer);
            Map<Trackable<?>, Trackable.Subscription> leftover = stale;
            stale = outerStale;
            if (completed || disposed) {
                closeAll(leftover);
            } else {
                dependencies.putAll(leftover);
            }
        }
    }

    @Override
    public final void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        closeAll(dependencies);
        if (stale != null) {
            closeAll(stale);
        }
        onDispose();
    }

    @Override
    public final boolean isDisposed() {
        return disposed;
    }

    /** Hook for subclasses to release their own state; called once by {@link #dispose()}. */
    protected void onDispose() {
    }

    private static void closeAll(Map<Trackable<?>, Trackable.Subscription> subscriptions) {
        for (Trackable.Subscription subscription : subscriptions.values()) {
            subscription.close();
        }
        subscriptions.clear();
    }
}
