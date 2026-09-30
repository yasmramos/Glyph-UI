package com.glyphui.graphics;

import com.glyphui.core.Application;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * An observable, thread-safe value holder — the basic building block of the
 * Glyph UI data model.
 *
 * <h2>Threading</h2>
 * <p>{@link #set(Object)} is safe to call from <em>any</em> thread:</p>
 * <ul>
 *   <li>On the UI thread (or before {@code Application.run()} has started) the
 *       new value is applied and listeners are notified synchronously.</li>
 *   <li>From any other thread the change is marshalled onto the UI thread via
 *       {@link Application#invokeLater(Runnable)}, which also marks the frame
 *       dirty and wakes up an idle event loop. Listeners therefore always run
 *       on the UI thread and widget repaints stay race-free.</li>
 * </ul>
 *
 * <h2>Binding</h2>
 * <p>{@link #bind(Property)} mirrors another property's value one-way (source
 * → this). Bindings are idempotent and propagate transitively because each
 * link simply forwards sets through the same thread-safe path.</p>
 *
 * @param <T> the value type
 */
public final class Property<T> {

    /** Current value. Volatile so {@link #get()} is lock-free and fresh. */
    private volatile T value;

    /** The application used for UI-thread marshalling; may be null. */
    private final Application application;

    /** Optional hook applied on every set (e.g. widget state update + repaint). */
    private final Consumer<T> onChange;

    /** Registered change listeners (iteration-safe for add/remove during notify). */
    private final List<ChangeListener<T>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Creates a property with an initial value.
     *
     * @param application the owning application (used for thread marshalling);
     *                    may be {@code null} in headless/test contexts, in
     *                    which case cross-thread sets apply directly
     * @param initialValue the starting value
     * @param onChange     hook invoked with the new value on the UI thread
     *                     whenever it actually changes; may be {@code null}
     */
    public Property(Application application, T initialValue, Consumer<T> onChange) {
        this.application = application;
        this.value = initialValue;
        this.onChange = onChange;
    }

    /**
     * Convenience constructor without an application or change hook.
     *
     * @param initialValue the starting value
     */
    public Property(T initialValue) {
        this(null, initialValue, null);
    }

    /**
     * Returns the current value. Safe to call from any thread.
     *
     * @return the current value (may be null)
     */
    public T get() {
        return value;
    }

    /**
     * Sets the property value.
     *
     * <p>If called from a non-UI thread the set is re-invoked on the UI
     * thread through {@link Application#invokeLater(Runnable)}; otherwise the
     * value is applied immediately. Equal values (per {@code Objects.equals})
     * are ignored and do not notify listeners.</p>
     *
     * @param newValue the new value (may be null)
     */
    public void set(T newValue) {
        if (application != null) {
            if (!application.isUiThread()) {
                // Marshal onto the UI thread; re-entering set() there will
                // find isUiThread() true and apply the value exactly once.
                application.invokeLater(() -> set(newValue));
                return;
            }
        } else if (Application.getCurrent() != null && !Application.getCurrent().isUiThread()) {
            // Property created before any application was running: fall back
            // to the current application's queue so background sets still get
            // marshalled onto the UI thread.
            Application.invokeOnCurrent(() -> set(newValue));
            return;
        }
        applyOnUiThread(newValue);
    }

    /**
     * Applies the value, notifies listeners and runs the change hook. Must
     * only execute on the UI thread (see {@link #set(Object)}).
     */
    private void applyOnUiThread(T newValue) {
        T oldValue = this.value;
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        this.value = newValue;
        if (onChange != null) {
            onChange.accept(newValue);
        }
        for (ChangeListener<T> listener : listeners) {
            listener.changed(this, oldValue, newValue);
        }
    }

    /**
     * Registers a change listener. Listeners are invoked on the UI thread in
     * registration order, after the value has been updated.
     *
     * @param listener the listener to add
     */
    public void addListener(ChangeListener<T> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered change listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(ChangeListener<T> listener) {
        listeners.remove(listener);
    }

    /**
     * Binds this property one-way to {@code source}: every future change of
     * the source is mirrored into this property. The current source value is
     * applied immediately. Binding twice to the same source is a no-op.
     *
     * @param source the property to observe; must not be this instance
     */
    public void bind(Property<T> source) {
        Objects.requireNonNull(source, "source");
        if (source == this) {
            throw new IllegalArgumentException("Cannot bind a property to itself");
        }
        // Idempotency: drop any previous binding to this same source.
        ChangeListener<T> existing = boundListeners.get(source);
        if (existing != null) {
            source.removeListener(existing);
        }
        ChangeListener<T> forwarder = (prop, oldV, newV) -> set(newV);
        boundListeners.put(source, forwarder);
        source.addListener(forwarder);
        set(source.get());
    }

    /** Tracks forwarder listeners so re-binding the same source is idempotent. */
    private final java.util.Map<Property<T>, ChangeListener<T>> boundListeners =
            new java.util.HashMap<>();

    /**
     * Unbinds this property from a previously bound source.
     *
     * @param source the source property to detach from
     */
    public void unbind(Property<T> source) {
        ChangeListener<T> forwarder = boundListeners.remove(source);
        if (forwarder != null) {
            source.removeListener(forwarder);
        }
    }

    /**
     * Static factory mirroring common JavaFX-style APIs.
     *
     * @param initialValue the starting value
     * @param <T>          value type
     * @return a new simple property
     */
    public static <T> Property<T> of(T initialValue) {
        return new Property<>(initialValue);
    }

    /**
     * Returns a read-only view of this property.
     *
     * @return a supplier that reads the current value
     */
    public Supplier<T> readOnly() {
        return this::get;
    }

    @Override
    public String toString() {
        return "Property[" + value + "]";
    }
}
