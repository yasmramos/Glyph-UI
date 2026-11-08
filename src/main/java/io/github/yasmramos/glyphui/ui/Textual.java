package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Property;

/**
 * A widget that carries a single piece of observable caption text.
 *
 * <p>Implemented by every widget whose label can be driven by a
 * {@code {path}} interpolation in the declarative front-end: the loader
 * resolves the path to a {@code Trackable} and pushes each value through
 * {@link #textProperty()}, so the caption follows the data without the widget
 * knowing about markup.</p>
 */
public interface Textual {

    /**
     * Returns the observable caption property.
     *
     * @return the caption property (never null)
     */
    Property<String> textProperty();

    /**
     * Returns the current caption.
     *
     * @return the caption text
     */
    default String getText() {
        return textProperty().get();
    }
}
