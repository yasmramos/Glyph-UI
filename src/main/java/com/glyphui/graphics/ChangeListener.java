package com.glyphui.graphics;

/**
 * Listener notified when a {@link Property} value changes.
 *
 * <p>Callbacks always run on the Glyph UI thread, regardless of which thread
 * called {@link Property#set(Object)}: cross-thread sets are marshalled
 * through {@code Application.invokeLater(...)} before notification.</p>
 *
 * @param <T> the property value type
 */
@FunctionalInterface
public interface ChangeListener<T> {
    /**
     * Invoked after the property value has been updated.
     *
     * @param property the property that changed
     * @param oldValue the value before the change
     * @param newValue the value after the change
     */
    void changed(Property<T> property, T oldValue, T newValue);
}
