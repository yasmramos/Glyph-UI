package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.Component;

/**
 * Creates the widget for one markup tag.
 *
 * <p>Factories are plain lambdas stored in a {@link WidgetRegistry}: no
 * reflection, no class-name lookup. The raw {@link Element} is passed so a
 * factory can read the attributes intrinsic to its widget (text, {@code src},
 * a slider's range, ...); common attributes such as {@code id}, {@code class}
 * and {@code style} are still applied by the loader after creation.</p>
 *
 * <p>{@link WidgetContext} exposes the loader services a factory may need
 * (attribute helpers, image resolution, radio grouping, diagnostics) without
 * coupling the factory to {@link UiLoader}.</p>
 */
@FunctionalInterface
public interface WidgetFactory {

    /**
     * Creates the component for the supplied element.
     *
     * @param element the raw markup element (never null)
     * @param context the services of the load in progress (never null)
     * @return the new component, never null
     */
    Component create(Element element, WidgetContext context);
}
