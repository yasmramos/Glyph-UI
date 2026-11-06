package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.CheckBox;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Dropdown;
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.ProgressBar;
import io.github.yasmramos.glyphui.ui.RadioButton;
import io.github.yasmramos.glyphui.ui.ScrollPane;
import io.github.yasmramos.glyphui.ui.Separator;
import io.github.yasmramos.glyphui.ui.Slider;
import io.github.yasmramos.glyphui.ui.TextField;
import io.github.yasmramos.glyphui.ui.ToggleSwitch;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The tag-to-widget vocabulary of the declarative loader, and the single
 * source of truth for it.
 *
 * <p>Registration is explicit: a tag is bound to a {@link WidgetFactory}
 * lambda, so adding a widget to the markup vocabulary never depends on
 * reflection or on class-name conventions. Re-registering a tag replaces the
 * previous factory, which lets applications override a default widget.</p>
 *
 * <p>A factory only builds the widget. Common attributes ({@code id},
 * {@code class}, {@code style}, bounds), layout and event handlers stay with
 * the loader, which applies them after creation.</p>
 *
 * <p>Tags are matched case-insensitively; the canonical spelling is lower
 * case, matching how markup is written.</p>
 */
public final class WidgetRegistry {

    private final Map<String, WidgetFactory> factories = new LinkedHashMap<>();

    /**
     * Registers (or replaces) the factory for a tag.
     *
     * @param tag     the markup tag, non-blank; matched case-insensitively
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
        factories.put(key(tag), factory);
    }

    /**
     * Resolves the factory for a tag.
     *
     * @param tag the markup tag
     * @return the registered factory
     * @throws IllegalArgumentException when no factory is registered for the tag
     */
    public WidgetFactory get(String tag) {
        WidgetFactory factory = tag == null ? null : factories.get(key(tag));
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
        return tag != null && factories.containsKey(key(tag));
    }

    /**
     * @return the registered tags, in registration order
     */
    public Set<String> tags() {
        return Collections.unmodifiableSet(factories.keySet());
    }

    private static String key(String tag) {
        return tag.toLowerCase(Locale.ROOT);
    }

    /**
     * Creates the base markup vocabulary.
     *
     * <p>The tags mirror the HTML spelling the loader accepts: {@code div}
     * (and the other container tags) → {@link Panel}, {@code button},
     * {@code label} (and the heading tags) → {@link Label}, {@code input} /
     * {@code textarea} → {@link TextField}, {@code img} → {@link ImageView},
     * {@code checkbox} → {@link CheckBox}, {@code radio} → {@link RadioButton},
     * {@code switch} → {@link ToggleSwitch}, {@code slider} → {@link Slider},
     * {@code progress} → {@link ProgressBar}, {@code select} → {@link Dropdown},
     * {@code scroll} → {@link ScrollPane} and {@code separator} / {@code hr} →
     * {@link Separator}.</p>
     *
     * @return a registry with the default tags registered
     */
    public static WidgetRegistry defaults() {
        WidgetRegistry registry = new WidgetRegistry();

        WidgetFactory container = (element, ctx) -> new Panel(Float.NaN, Float.NaN);
        for (String tag : new String[]{"div", "body", "section", "span",
                "panel", "root", "ui"}) {
            registry.register(tag, container);
        }
        registry.register("box", (element, ctx) -> {
            Panel panel = new Panel();
            panel.setLayoutManager(new FlexLayout(FlexLayout.Direction.COLUMN,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.STRETCH));
            return panel;
        });
        // A stack has no layout manager: children keep their explicit x/y and
        // are painted in insertion order (last one on top).
        registry.register("stack", (element, ctx) -> new Panel());

        registry.register("button", (element, ctx) -> new Button(ctx.textOf(element)));

        WidgetFactory labelled = (element, ctx) -> {
            Label label = new Label(ctx.textOf(element));
            String size = ctx.attr(element, "font-size");
            if (!size.isEmpty()) {
                try {
                    label.setFontSize(Float.valueOf(size.replaceAll("[^0-9.]", "")));
                } catch (NumberFormatException e) {
                    ctx.warn("Bad font-size '" + size + "' on <"
                            + element.getTagName() + ">");
                }
            }
            return label;
        };
        for (String tag : new String[]{"label", "p", "h1", "h2", "h3", "h4",
                "h5", "h6"}) {
            registry.register(tag, labelled);
        }

        WidgetFactory textField = (element, ctx) -> {
            TextField field = new TextField();
            String value = ctx.attr(element, "value");
            String text = !value.isEmpty() ? value : ctx.textOf(element);
            if (!text.isEmpty()) {
                field.setText(text);
            }
            return field;
        };
        registry.register("input", textField);
        registry.register("textarea", textField);

        registry.register("img", (element, ctx) -> {
            ImageView view = new ImageView();
            String src = ctx.attr(element, "src");
            if (!src.isEmpty()) {
                UiLoader.ImageProvider provider = ctx.imageProvider();
                if (provider == null) {
                    ctx.warn("<img src=\"" + src + "\"> ignored: no ImageProvider set");
                } else {
                    Image image = provider.load(src);
                    if (image == null) {
                        ctx.warn("ImageProvider returned null for src '" + src + "'");
                    } else {
                        view.setImage(image);
                    }
                }
            }
            return view;
        });

        registry.register("checkbox", (element, ctx) -> {
            CheckBox box = new CheckBox(ctx.textOf(element));
            if (ctx.hasAttr(element, "checked")) {
                box.setChecked(true);
            }
            return box;
        });

        registry.register("radio", (element, ctx) -> {
            RadioButton radio = new RadioButton(ctx.textOf(element));
            // A shared `name` makes sibling radios mutually exclusive: they all
            // join the same RadioGroup (HTML's radio semantics).
            String groupName = ctx.attr(element, "name");
            if (!groupName.isEmpty()) {
                ctx.radioGroup(groupName).add(radio);
            }
            if (ctx.hasAttr(element, "selected") || ctx.hasAttr(element, "checked")) {
                radio.setSelected(true);
            }
            return radio;
        });

        registry.register("switch", (element, ctx) -> {
            ToggleSwitch toggle = new ToggleSwitch(ctx.textOf(element));
            if (ctx.hasAttr(element, "on") || ctx.hasAttr(element, "checked")) {
                toggle.setOn(true);
            }
            return toggle;
        });

        registry.register("slider", (element, ctx) -> {
            Slider slider = new Slider();
            slider.setRange(ctx.parseFloatAttr(element, "min", 0.0f),
                    ctx.parseFloatAttr(element, "max", 1.0f));
            slider.setStep(ctx.parseFloatAttr(element, "step", 0.0f));
            String value = ctx.attr(element, "value");
            if (!value.isEmpty()) {
                try {
                    slider.setValue(Float.parseFloat(value.trim()));
                } catch (NumberFormatException e) {
                    ctx.warn("Bad value '" + value + "' on <slider>");
                }
            }
            return slider;
        });

        registry.register("progress", (element, ctx) -> {
            ProgressBar bar = new ProgressBar();
            String value = ctx.attr(element, "value");
            if (!value.isEmpty()) {
                try {
                    bar.setValue(Float.parseFloat(value.trim()));
                } catch (NumberFormatException e) {
                    ctx.warn("Bad value '" + value + "' on <progress>");
                }
            }
            if (ctx.hasAttr(element, "indeterminate")) {
                bar.setIndeterminate(true);
            }
            if (ctx.hasAttr(element, "show-text")) {
                bar.setShowText(true);
            }
            return bar;
        });

        registry.register("select", (element, ctx) -> {
            Dropdown dropdown = new Dropdown();
            String placeholder = ctx.attr(element, "placeholder");
            if (!placeholder.isEmpty()) {
                dropdown.setPlaceholder(placeholder);
            }
            return dropdown;
        });

        registry.register("scroll", (element, ctx) -> new ScrollPane());
        registry.register("scrollpane", (element, ctx) -> new ScrollPane());

        WidgetFactory separator = (element, ctx) -> {
            Separator line = new Separator();
            String orientation = ctx.attr(element, "orientation");
            if (!orientation.isEmpty()) {
                String normalised = orientation.trim().toUpperCase(Locale.ROOT);
                Separator.Orientation parsed = null;
                for (Separator.Orientation candidate : Separator.Orientation.values()) {
                    if (candidate.name().equals(normalised)) {
                        parsed = candidate;
                    }
                }
                if (parsed == null) {
                    ctx.warn("Unknown orientation '" + orientation + "' on <"
                            + element.getTagName() + ">");
                } else {
                    line.setOrientation(parsed);
                }
            }
            return line;
        };
        registry.register("separator", separator);
        registry.register("hr", separator);

        return registry;
    }

    /**
     * Convenience for tests and diagnostics: creates the widget for a tag.
     *
     * @param tag     the markup tag
     * @param element the raw element passed to the factory
     * @param context the loader services passed to the factory
     * @return the created component
     */
    public Component create(String tag, Element element, WidgetContext context) {
        return get(tag).create(element, context);
    }
}
