package io.github.yasmramos.glyphui.reactive;

import java.util.Objects;

/**
 * A mutable, observable value.
 *
 * <ul>
 *   <li>{@link #set(Object)} is skipped when the new value is
 *       {@linkplain Objects#equals(Object, Object) equal} to the current one.</li>
 *   <li>Writing while a notification is running, or from a thread other than
 *       the UI thread (see {@link Reactor}), throws
 *       {@link IllegalStateException}.</li>
 * </ul>
 *
 * @param <T> the value type
 */
public final class Signal<T> implements Trackable<T> {

    private final Registry<Observer> observers = new Registry<>();
    private final Thread creator = Thread.currentThread();
    private T value;

    /**
     * Creates a signal holding {@code initialValue}.
     *
     * @param initialValue the initial value (may be null)
     */
    public Signal(T initialValue) {
        this.value = initialValue;
    }

    /** Creates a signal holding null. */
    public Signal() {
        this(null);
    }

    @Override
    public T get() {
        Reactor.reportRead(this);
        return value;
    }

    /**
     * Reads the current value without registering a dependency.
     *
     * <p>Equivalent to {@code Reactor.untracked(signal::get)} but shorter;
     * useful inside {@code Computed} bodies and effects that must inspect a
     * signal without subscribing to it.</p>
     *
     * @return the current value
     */
    public T peek() {
        return Reactor.untracked(() -> value);
    }

    /**
     * Replaces the value and notifies observers, unless the new value equals
     * the current one.
     *
     * @param newValue the new value (may be null)
     * @throws IllegalStateException when called off the UI thread, during a
     *                               notification, or inside a {@code Computed}
     */
    public void set(T newValue) {
        Reactor.checkUiThread(creator, "Signal.set");
        Reactor.checkWritable("Signal.set");
        if (Objects.equals(value, newValue)) {
            return;
        }
        value = newValue;
        Reactor.notifyChange(() -> observers.forEach(Observer::invalidate));
    }

    /**
     * Atomically replaces the value with the result of applying {@code update}
     * to the current value (read-modify-write).
     *
     * <p>The function runs on the UI thread outside any tracked evaluation, so
     * it cannot accidentally register dependencies. When the produced value
     * equals the current one, no notification is fired — same semantics as
     * {@link #set(Object)}.</p>
     *
     * @param update the read-modify-write function (may return null)
     * @throws NullPointerException     if {@code update} is null
     * @throws IllegalStateException when called off the UI thread, during a
     *                                notification, or inside a {@code Computed}
     */
    public void update(java.util.function.UnaryOperator<T> update) {
        Objects.requireNonNull(update, "update");
        Reactor.checkUiThread(creator, "Signal.update");
        Reactor.checkWritable("Signal.update");
        T next = Reactor.untracked(() -> update.apply(value));
        if (Objects.equals(value, next)) {
            return;
        }
        value = next;
        Reactor.notifyChange(() -> observers.forEach(Observer::invalidate));
    }

    @Override
    public Subscription subscribe(Observer observer) {
        return observers.add(observer);
    }

    /** Number of registered observers; lets tests verify that nothing leaks. */
    int observerCount() {
        return observers.size();
    }
}
