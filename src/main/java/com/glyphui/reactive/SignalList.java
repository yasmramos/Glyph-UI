package com.glyphui.reactive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * An observable list that reports typed, indexed changes.
 *
 * <p>Every mutation emits one {@link Change} per affected element, applied in
 * order: an index is always valid against the list as it was right before that
 * change. Listeners registered with {@link #onListChange(ListChangeListener)}
 * receive the details; the list is also a {@link Trackable}, so reading it
 * inside a {@code Computed} (for example {@code list.size()}) makes the
 * computed value depend on the list's structure.</p>
 *
 * <p>Like every reactive object it is confined to the UI thread, and it may not
 * be modified from inside one of its own notifications.</p>
 *
 * @param <T> the element type
 */
public final class SignalList<T> implements Trackable<SignalList<T>> {

    /**
     * One elementary change.
     *
     * @param kind     what happened
     * @param index    the affected index
     * @param oldValue the removed or replaced element (null for {@code INSERT})
     * @param newValue the inserted or replacing element (null for {@code REMOVE})
     * @param <T>      the element type
     */
    public record Change<T>(Kind kind, int index, T oldValue, T newValue) {

        /** The kind of structural change. */
        public enum Kind { INSERT, REMOVE, REPLACE }
    }

    private final List<T> items = new ArrayList<>();
    private final Registry<ListChangeListener<T>> listeners = new Registry<>();
    private final Registry<Observer> observers = new Registry<>();
    private final Thread creator = Thread.currentThread();

    /** Creates an empty list. */
    public SignalList() {
    }

    /**
     * Creates a list with initial content; no change is emitted for it.
     *
     * @param initial the initial elements
     */
    public SignalList(Collection<? extends T> initial) {
        items.addAll(initial);
    }

    // ------------------------------------------------------------------
    // Reads
    // ------------------------------------------------------------------

    /** Reading registers the list as a dependency of the running {@code Computed}. */
    @Override
    public SignalList<T> get() {
        Reactor.reportRead(this);
        return this;
    }

    /**
     * Gets the element at {@code index}.
     *
     * @param index the index
     * @return the element
     */
    public T get(int index) {
        Reactor.reportRead(this);
        return items.get(index);
    }

    /** @return the number of elements */
    public int size() {
        Reactor.reportRead(this);
        return items.size();
    }

    /** @return true when the list has no elements */
    public boolean isEmpty() {
        Reactor.reportRead(this);
        return items.isEmpty();
    }

    /**
     * @param element the element to look for
     * @return true when the list contains {@code element}
     */
    public boolean contains(Object element) {
        Reactor.reportRead(this);
        return items.contains(element);
    }

    /**
     * @param element the element to look for
     * @return the first index of {@code element}, or -1
     */
    public int indexOf(Object element) {
        Reactor.reportRead(this);
        return items.indexOf(element);
    }

    /** @return an unmodifiable copy of the current content */
    public List<T> snapshot() {
        Reactor.reportRead(this);
        return Collections.unmodifiableList(new ArrayList<>(items));
    }

    // ------------------------------------------------------------------
    // Writes
    // ------------------------------------------------------------------

    /**
     * Appends an element.
     *
     * @param element the element
     */
    public void add(T element) {
        beforeWrite("SignalList.add");
        insert(items.size(), element);
    }

    /**
     * Inserts an element.
     *
     * @param index   the insertion index, from 0 to {@code size()}
     * @param element the element
     */
    public void add(int index, T element) {
        beforeWrite("SignalList.add");
        insert(index, element);
    }

    /**
     * Appends every element, emitting one INSERT per element.
     *
     * @param elements the elements
     */
    public void addAll(Collection<? extends T> elements) {
        beforeWrite("SignalList.addAll");
        for (T element : new ArrayList<>(elements)) {
            insert(items.size(), element);
        }
    }

    /**
     * Removes the element at {@code index}.
     *
     * @param index the index
     * @return the removed element
     */
    public T remove(int index) {
        beforeWrite("SignalList.remove");
        T old = items.remove(index);
        publish(new Change<>(Change.Kind.REMOVE, index, old, null));
        return old;
    }

    /**
     * Replaces the element at {@code index}. Nothing is emitted when the new
     * element equals the current one.
     *
     * @param index   the index
     * @param element the new element
     * @return the previous element
     */
    public T set(int index, T element) {
        beforeWrite("SignalList.set");
        T old = items.get(index);
        if (Objects.equals(old, element)) {
            return old;
        }
        items.set(index, element);
        publish(new Change<>(Change.Kind.REPLACE, index, old, element));
        return old;
    }

    /** Removes every element, emitting one REMOVE per element from the last to the first. */
    public void clear() {
        beforeWrite("SignalList.clear");
        for (int i = items.size() - 1; i >= 0; i--) {
            T old = items.remove(i);
            publish(new Change<>(Change.Kind.REMOVE, i, old, null));
        }
    }

    // ------------------------------------------------------------------
    // Observation
    // ------------------------------------------------------------------

    /**
     * Registers a listener for typed changes.
     *
     * @param listener the listener
     * @return a handle; {@code close()} unregisters the listener
     */
    public Subscription onListChange(ListChangeListener<T> listener) {
        return listeners.add(listener);
    }

    @Override
    public Subscription subscribe(Observer observer) {
        return observers.add(observer);
    }

    /** Number of listeners plus observers; lets tests verify that nothing leaks. */
    int subscriberCount() {
        return listeners.size() + observers.size();
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void beforeWrite(String action) {
        Reactor.checkUiThread(creator, action);
        Reactor.checkWritable(action);
    }

    private void insert(int index, T element) {
        items.add(index, element);
        publish(new Change<>(Change.Kind.INSERT, index, null, element));
    }

    private void publish(Change<T> change) {
        Reactor.notifyChange(() -> {
            RuntimeException failure = null;
            try {
                listeners.forEach(listener -> listener.onChange(change));
            } catch (RuntimeException e) {
                failure = e;
            }
            try {
                observers.forEach(Observer::invalidate);
            } catch (RuntimeException e) {
                failure = Reactor.merge(failure, e);
            }
            if (failure != null) {
                throw failure;
            }
        });
    }
}
