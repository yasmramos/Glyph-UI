package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.CheckBox;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.ScrollPane;
import io.github.yasmramos.glyphui.ui.Separator;
import io.github.yasmramos.glyphui.ui.TextField;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tag-to-factory map for the declarative loader.
 *
 * <p>Registration is explicit: a tag is bound to a {@link WidgetFactory}
 * lambda, so adding a widget to the markup vocabulary never depends on
 * reflection or on class-name conventions. Re-registering a tag replaces the
 * previous factory, which lets applications override a default widget.</p>
 */
public final class WidgetRegistry {

    private final Map<String, WidgetFactory> factories = new LinkedHashMap<>();

    /**
     * Registers (or replaces) the factory for a tag.
     *
     * @param tag     the markup tag, case-sensitive and non-blank
     * @param factory the factory that creates the widget
     * @throws IllegalArgumentException when the tag is blank or the factory is null
     */
    public void register(String tag, WidgetFactory factory) {
        if (tag == null || tag.isBlank()) {
            throw new IllegalArgumentException("Widget tag must not be blank");
        }
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Widget factory for tag '" + tag + "' must not be null");
        }
        factories.put(tag, factory);
    }

    /**
     * Resolves the factory for a tag.
     *
     * @param tag the markup tag
     * @return the registered factory
     * @throws IllegalArgumentException when no factory is registered for the tag
     */
    public WidgetFactory get(String tag) {
        WidgetFactory factory = tag == null ? null : factories.get(tag);
        if (factory == null) {
            throw new IllegalArgumentException(
                    "Unknown widget tag: '" + tag + "'. Known tags: " + factories.keySet());
        }
        return factory;
    }

    /**
     * @param tag the markup tag
     * @return true when a factory is registered for the tag
     */
    public boolean knows(String tag) {
        return tag != null && factories.containsKey(tag);
    }

    /**
     * @return the registered tags, in registration order
     */
    public Set<String> tags() {
        return Collections.unmodifiableSet(factories.keySet());
    }

    /**
     * Creates the base markup vocabulary.
     *
     * <p>Mappings: {@code Box} → flex column {@link Panel}, {@code Stack} →
     * absolutely positioned {@link Panel}, {@code Label}, {@code Button},
     * {@code Field} → {@link TextField}, {@code Checkbox} → {@link CheckBox},
     * {@code Image} → {@link ImageView}, {@code Separator} and {@code Scroll} →
     * {@link ScrollPane}. Attribute application stays with the loader.</p>
     *
     * @return a registry with the default tags registered
     */
    public static WidgetRegistry defaults() {
        WidgetRegistry registry = new WidgetRegistry();
        registry.register("Box", element -> {
            Panel panel = new Panel();
            panel.setLayoutManager(new FlexLayout(FlexLayout.Direction.COLUMN,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.STRETCH));
            return panel;
        });
        // A stack has no layout manager: children keep their explicit x/y and
        // are painted in insertion order (last one on top).
        registry.register("Stack", element -> new Panel());
        registry.register("Label", element -> new Label());
        registry.register("Button", element -> new Button());
        registry.register("Field", element -> new TextField());
        registry.register("Checkbox", element -> new CheckBox());
        registry.register("Image", element -> new ImageView());
        registry.register("Separator", element -> new Separator());
        registry.register("Scroll", element -> new ScrollPane());
        return registry;
    }

    /**
     * Convenience for tests and diagnostics: creates the widget for a tag.
     *
     * @param tag     the markup tag
     * @param element the raw element passed to the factory
     * @return the created component
     */
    public Component create(String tag, Element element) {
        return get(tag).create(element);
    }
}
