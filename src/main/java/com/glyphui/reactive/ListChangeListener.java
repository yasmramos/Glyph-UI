package com.glyphui.reactive;

/**
 * Receives the structural changes of a {@link SignalList}.
 *
 * @param <T> the element type
 */
@FunctionalInterface
public interface ListChangeListener<T> {

    /**
     * Called after the list changed, once per elementary change.
     *
     * @param change the change that was applied
     */
    void onChange(SignalList.Change<T> change);
}
