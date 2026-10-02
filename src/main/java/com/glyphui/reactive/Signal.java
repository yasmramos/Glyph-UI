package com.glyphui.reactive;

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

    @Override
    public Subscription subscribe(Observer observer) {
        return observers.add(observer);
    }

    /** Number of registered observers; lets tests verify that nothing leaks. */
    int observerCount() {
        return observers.size();
    }
}
