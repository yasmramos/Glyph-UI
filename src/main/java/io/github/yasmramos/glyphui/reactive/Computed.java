package io.github.yasmramos.glyphui.reactive;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * A lazily evaluated value derived from other {@link Trackable}s.
 *
 * <ul>
 *   <li><b>Lazy:</b> the function runs on {@link #get()} only, and only when a
 *       dependency changed since the last run.</li>
 *   <li><b>Dynamic dependencies:</b> the sources read during an evaluation are
 *       the dependencies of that evaluation; when a branch is no longer taken,
 *       its sources are unsubscribed.</li>
 *   <li><b>Propagation:</b> a change marks this value dirty and invalidates its
 *       own observers, so chains of computed values and bindings stay correct.</li>
 *   <li><b>Explicit disposal:</b> a computed value stays subscribed to its
 *       sources until {@link #dispose()} is called.</li>
 * </ul>
 *
 * <p>The function must be free of side effects; writing to a signal from it
 * throws. A cyclic dependency throws {@link IllegalStateException}. If the
 * function throws, the exception propagates and the value stays dirty.</p>
 *
 * @param <T> the value type
 */
public final class Computed<T> extends AbstractObserver implements Trackable<T> {

    private final Registry<Observer> observers = new Registry<>();
    private final Thread creator = Thread.currentThread();
    private Supplier<? extends T> function;
    private T value;
    private boolean dirty = true;
    private boolean initialised;
    private boolean evaluating;

    /**
     * Creates a computed value.
     *
     * @param function computes the value from other trackables
     */
    public Computed(Supplier<? extends T> function) {
        this.function = Objects.requireNonNull(function, "function");
    }

    @Override
    public T get() {
        Reactor.checkUiThread(creator, "Computed.get");
        if (isDisposed()) {
            throw new IllegalStateException("Computed has been disposed");
        }
        if (evaluating) {
            throw new IllegalStateException("Cyclic dependency detected while evaluating a Computed");
        }
        Reactor.reportRead(this);
        if (dirty) {
            recompute();
        }
        return value;
    }

    /**
     * Reads the current value without registering a dependency on this
     * computed.
     *
     * <p><b>Lazy semantics:</b> when the value is dirty (a source changed
     * since the last evaluation, or it was never evaluated), {@code peek()}
     * returns the previously cached value without running the function —
     * exactly like reading a snapshot. This keeps peek side-effect free: it
     * never mutates the dependency graph, never re-subscribes and never runs
     * user code with observable consequences beyond the pure computation.
     * Call {@link #get()} when an up-to-date value is required.</p>
     *
     * <p>Useful inside effects and {@code Computed} bodies that must inspect a
     * derived value without adding it as a source.</p>
     *
     * @return the cached value (recomputes only if never evaluated before)
     */
    public T peek() {
        if (isDisposed()) {
            throw new IllegalStateException("Computed has been disposed");
        }
        // First read initialises the cache so peek() and get() agree from the
        // start; afterwards peek() never re-evaluates while dirty.
        if (dirty && !initialised) {
            evaluateFresh();
        }
        return value;
    }

    /** Runs the function outside any tracked context and caches its result. */
    private void evaluateFresh() {
        Observer previous = Reactor.swapCurrent(null);
        evaluating = true;
        try {
            value = tracked(function);
            dirty = false;
            initialised = true;
        } finally {
            evaluating = false;
            Reactor.swapCurrent(previous);
        }
    }

    private void recompute() {
        evaluating = true;
        try {
            value = tracked(function);
            dirty = false;
            initialised = true;
        } finally {
            evaluating = false;
        }
    }

    /**
     * Marks the value dirty and invalidates the observers. Propagation is
     * unconditional on purpose: a value whose last evaluation failed is still
     * dirty, yet its observers must hear about later changes.
     */
    @Override
    protected void onInvalidate() {
        dirty = true;
        observers.forEach(Observer::invalidate);
    }

    @Override
    public Subscription subscribe(Observer observer) {
        if (isDisposed()) {
            throw new IllegalStateException("Computed has been disposed");
        }
        return observers.add(observer);
    }

    @Override
    protected void onDispose() {
        observers.clear();
        function = null;
        value = null;
    }

    /** Number of registered observers; lets tests verify that nothing leaks. */
    int observerCount() {
        return observers.size();
    }
}
