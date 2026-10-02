package com.glyphui.reactive;

/**
 * A readable value that can be observed.
 *
 * <p>Reading through {@link #get()} while a {@code Computed} is being
 * evaluated registers this source as one of its dependencies (see
 * {@link Reactor}). All reactive state lives on the UI thread.</p>
 *
 * @param <T> the value type
 */
public interface Trackable<T> {

    /**
     * Gets the current value.
     *
     * @return the value
     */
    T get();

    /**
     * Registers an observer that is invalidated whenever the value changes.
     * The observer stays registered until the returned subscription is closed.
     *
     * @param observer the observer to notify
     * @return a handle that unregisters the observer; closing it twice is harmless
     */
    Subscription subscribe(Observer observer);

    /** Handle returned by {@link #subscribe(Observer)}; {@link #close()} never throws. */
    @FunctionalInterface
    interface Subscription extends AutoCloseable {

        /** Unregisters the observer. Idempotent. */
        @Override
        void close();
    }
}
