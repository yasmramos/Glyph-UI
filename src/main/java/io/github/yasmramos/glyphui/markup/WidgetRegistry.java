package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.ui.Accordion;
import io.github.yasmramos.glyphui.ui.Alert;
import io.github.yasmramos.glyphui.ui.Avatar;
import io.github.yasmramos.glyphui.ui.Badge;
import io.github.yasmramos.glyphui.ui.Breadcrumb;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Card;
import io.github.yasmramos.glyphui.ui.CheckBox;
import io.github.yasmramos.glyphui.ui.Chip;
import io.github.yasmramos.glyphui.ui.ColorPicker;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Dialog;
import io.github.yasmramos.glyphui.ui.Dropdown;
import io.github.yasmramos.glyphui.ui.GroupBox;
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Link;
import io.github.yasmramos.glyphui.ui.ListBox;
import io.github.yasmramos.glyphui.ui.Pagination;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.ProgressBar;
import io.github.yasmramos.glyphui.ui.RadioButton;
import io.github.yasmramos.glyphui.ui.Rating;
import io.github.yasmramos.glyphui.ui.ScrollPane;
import io.github.yasmramos.glyphui.ui.Separator;
import io.github.yasmramos.glyphui.ui.Skeleton;
import io.github.yasmramos.glyphui.ui.Slider;
import io.github.yasmramos.glyphui.ui.Spinner;
import io.github.yasmramos.glyphui.ui.SplitPane;
import io.github.yasmramos.glyphui.ui.Stepper;
import io.github.yasmramos.glyphui.ui.Table;
import io.github.yasmramos.glyphui.ui.Tabs;
import io.github.yasmramos.glyphui.ui.TextField;
import io.github.yasmramos.glyphui.ui.ToggleSwitch;
import io.github.yasmramos.glyphui.ui.Toolbar;
import io.github.yasmramos.glyphui.ui.TreeView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
     * {@code spinner} / {@code number} → {@link Spinner},
     * {@code progress} → {@link ProgressBar}, {@code select} → {@link Dropdown},
     * {@code scroll} → {@link ScrollPane}, {@code separator} / {@code hr} →
     * {@link Separator}, {@code badge} → {@link Badge}, {@code card} →
     * {@link Card}, {@code tabs} / {@code tabpanel} → {@link Tabs},
     * {@code alert} → {@link Alert}, {@code avatar} → {@link Avatar},
     * {@code accordion} → {@link Accordion}, {@code chip} → {@link Chip},
     * {@code breadcrumb} → {@link Breadcrumb} and {@code pagination} →
     * {@link Pagination}, {@code a} → {@link Link}, {@code rating} →
     * {@link Rating}, {@code skeleton} → {@link Skeleton}, {@code stepper} →
     * {@link Stepper}, {@code toolbar} → {@link Toolbar}, {@code groupbox} →
     * {@link GroupBox}, {@code listbox} → {@link ListBox} and
     * {@code splitpane} → {@link SplitPane}.</p>
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
                "h5", "h6", "item"}) {
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

        WidgetFactory spinner = (element, ctx) -> {
            Spinner stepper = new Spinner();
            stepper.setRange(ctx.parseFloatAttr(element, "min", 0.0f),
                    ctx.parseFloatAttr(element, "max", 100.0f));
            stepper.setStep(ctx.parseFloatAttr(element, "step", 1.0f));
            String value = ctx.attr(element, "value");
            if (!value.isEmpty()) {
                try {
                    stepper.setValue(Float.parseFloat(value.trim()));
                } catch (NumberFormatException e) {
                    ctx.warn("Bad value '" + value + "' on <" + element.getTagName() + ">");
                }
            }
            return stepper;
        };
        registry.register("spinner", spinner);
        registry.register("number", spinner);

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

        registry.register("badge", (element, ctx) -> new Badge(ctx.textOf(element)));

        registry.register("card", (element, ctx) -> {
            Card card = new Card();
            String title = ctx.attr(element, "title");
            if (title.isEmpty()) {
                title = ctx.attr(element, "header");
            }
            if (!title.isEmpty()) {
                card.setTitle(title);
            }
            return card;
        });

        registry.register("tabs", (element, ctx) -> new Tabs());
        registry.register("tabpanel", (element, ctx) -> new Tabs());

        registry.register("alert", (element, ctx) -> {
            Alert.Level level = Alert.Level.INFO;
            String raw = ctx.attr(element, "level");
            if (raw.isEmpty()) {
                raw = ctx.attr(element, "type");
            }
            if (!raw.isEmpty()) {
                String normalised = raw.trim().toUpperCase(Locale.ROOT);
                Alert.Level parsed = null;
                for (Alert.Level candidate : Alert.Level.values()) {
                    if (candidate.name().equals(normalised)) {
                        parsed = candidate;
                    }
                }
                if (parsed == null) {
                    ctx.warn("Unknown level '" + raw + "' on <" + element.getTagName() + ">");
                } else {
                    level = parsed;
                }
            }
            return new Alert(level, ctx.textOf(element));
        });

        registry.register("avatar", (element, ctx) -> {
            Avatar avatar = new Avatar(ctx.textOf(element));
            String src = ctx.attr(element, "src");
            if (!src.isEmpty()) {
                UiLoader.ImageProvider provider = ctx.imageProvider();
                if (provider == null) {
                    ctx.warn("<avatar src=\"" + src + "\"> ignored: no ImageProvider set");
                } else {
                    Image image = provider.load(src);
                    if (image == null) {
                        ctx.warn("ImageProvider returned null for src '" + src + "'");
                    } else {
                        avatar.setImage(image);
                    }
                }
            }
            return avatar;
        });

        registry.register("accordion", (element, ctx) -> new Accordion());

        registry.register("chip", (element, ctx) -> {
            Chip chip = new Chip(ctx.textOf(element));
            if (ctx.hasAttr(element, "selected") || ctx.hasAttr(element, "active")) {
                chip.setSelected(true);
            }
            if (ctx.hasAttr(element, "removable") || ctx.hasAttr(element, "closable")) {
                chip.setRemovable(true);
            }
            return chip;
        });

        registry.register("breadcrumb", (element, ctx) -> {
            Breadcrumb breadcrumb = new Breadcrumb();
            String separator = ctx.attr(element, "separator");
            if (!separator.isEmpty()) {
                breadcrumb.setSeparator(separator);
            }
            return breadcrumb;
        });

        registry.register("pagination", (element, ctx) -> {
            Pagination pagination = new Pagination();
            pagination.setPageCount((int) ctx.parseFloatAttr(element, "pages", 1.0f));
            String current = ctx.attr(element, "page");
            if (!current.isEmpty()) {
                try {
                    pagination.setCurrentPage(Integer.parseInt(current.trim()) - 1);
                } catch (NumberFormatException e) {
                    ctx.warn("Bad page '" + current + "' on <" + element.getTagName() + ">");
                }
            }
            return pagination;
        });

        // "link" is reserved for document metadata (<link rel=...>), so the
        // hyperlink widget takes the classic "a" anchor tag.
        registry.register("a", (element, ctx) -> {
            Link link = new Link(ctx.textOf(element));
            String target = ctx.attr(element, "href");
            if (target.isEmpty()) {
                target = ctx.attr(element, "target");
            }
            if (!target.isEmpty()) {
                link.setTarget(target);
            }
            if (ctx.hasAttr(element, "visited")) {
                link.setVisited(true);
            }
            if (ctx.hasAttr(element, "underline")) {
                link.setAlwaysUnderline(true);
            }
            return link;
        });

        registry.register("rating", (element, ctx) -> {
            Rating rating = new Rating((int) ctx.parseFloatAttr(element, "max", 5.0f));
            // Half-star granularity must be enabled before the value is set:
            // otherwise a value like 2.5 snaps to the whole-star grid.
            if (ctx.hasAttr(element, "half") || ctx.hasAttr(element, "allow-half")) {
                rating.setAllowHalf(true);
            }
            String value = ctx.attr(element, "value");
            if (!value.isEmpty()) {
                try {
                    rating.setValue(Float.parseFloat(value.trim()));
                } catch (NumberFormatException e) {
                    ctx.warn("Bad value '" + value + "' on <rating>");
                }
            }
            if (ctx.hasAttr(element, "readonly") || ctx.hasAttr(element, "read-only")) {
                rating.setReadOnly(true);
            }
            return rating;
        });

        registry.register("skeleton", (element, ctx) -> {
            Skeleton.Shape shape = Skeleton.Shape.TEXT;
            String raw = ctx.attr(element, "shape");
            if (!raw.isEmpty()) {
                String normalised = raw.trim().toUpperCase(Locale.ROOT);
                Skeleton.Shape parsed = null;
                for (Skeleton.Shape candidate : Skeleton.Shape.values()) {
                    if (candidate.name().equals(normalised)) {
                        parsed = candidate;
                    }
                }
                if (parsed == null) {
                    ctx.warn("Unknown shape '" + raw + "' on <" + element.getTagName() + ">");
                } else {
                    shape = parsed;
                }
            }
            Skeleton skeleton = new Skeleton(shape);
            if (ctx.hasAttr(element, "static")) {
                skeleton.setAnimated(false);
            }
            return skeleton;
        });

        registry.register("stepper", (element, ctx) -> {
            Stepper stepper = new Stepper();
            String steps = ctx.attr(element, "steps");
            if (!steps.isEmpty()) {
                for (String label : steps.split(",")) {
                    stepper.addStep(label.trim());
                }
            }
            String current = ctx.attr(element, "current");
            if (!current.isEmpty()) {
                try {
                    stepper.setCurrentStep(Integer.parseInt(current.trim()) - 1);
                } catch (NumberFormatException e) {
                    ctx.warn("Bad current '" + current + "' on <stepper>");
                }
            }
            if (ctx.hasAttr(element, "free") || ctx.hasAttr(element, "non-linear")) {
                stepper.setLinear(false);
            }
            return stepper;
        });

        registry.register("toolbar", (element, ctx) -> new Toolbar());

        registry.register("groupbox", (element, ctx) -> {
            GroupBox box = new GroupBox();
            String title = ctx.attr(element, "title");
            if (title.isEmpty()) {
                title = ctx.attr(element, "label");
            }
            if (!title.isEmpty()) {
                box.setTitle(title);
            }
            return box;
        });

        registry.register("listbox", (element, ctx) -> new ListBox());

        registry.register("splitpane", (element, ctx) -> {
            SplitPane pane = new SplitPane();
            String orientation = ctx.attr(element, "orientation");
            if (!orientation.isEmpty()) {
                String normalised = orientation.trim().toUpperCase(Locale.ROOT);
                if ("VERTICAL".equals(normalised)) {
                    pane.setOrientation(SplitPane.Orientation.VERTICAL);
                } else if (!"HORIZONTAL".equals(normalised)) {
                    ctx.warn("Unknown orientation '" + orientation
                            + "' on <splitpane>");
                }
            }
            String ratio = ctx.attr(element, "ratio");
            if (!ratio.isEmpty()) {
                try {
                    pane.setRatio(Float.parseFloat(ratio.trim()));
                } catch (NumberFormatException e) {
                    ctx.warn("Bad ratio '" + ratio + "' on <splitpane>");
                }
            }
            return pane;
        });

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

        registry.register("table", (element, ctx) -> {
            Table table = new Table();
            String cols = ctx.attr(element, "columns");
            if (!cols.isEmpty()) {
                for (String caption : cols.split(",")) {
                    table.addColumn(caption.trim());
                }
            }
            return table;
        });

        registry.register("treeview", (element, ctx) -> new TreeView());

        registry.register("dialog", (element, ctx) -> {
            Dialog dialog = new Dialog(ctx.attr(element, "title"), ctx.textOf(element));
            String actions = ctx.attr(element, "actions");
            if (!actions.isEmpty()) {
                for (String label : actions.split(",")) {
                    dialog.addAction(label.trim());
                }
            }
            if (ctx.hasAttr(element, "static")
                    || ctx.hasAttr(element, "non-dismissible")) {
                dialog.setDismissible(false);
            }
            return dialog;
        });

        registry.register("colorpicker", (element, ctx) -> {
            ColorPicker picker = new ColorPicker();
            String palette = ctx.attr(element, "palette");
            if (!palette.isEmpty()) {
                List<Integer> parsed = new ArrayList<>();
                for (String token : palette.split(",")) {
                    Integer color = ColorPicker.parseHex(token);
                    if (color == null) {
                        ctx.warn("Bad palette color '" + token
                                + "' on <" + element.getTagName() + ">");
                    } else {
                        parsed.add(color);
                    }
                }
                if (!parsed.isEmpty()) {
                    int[] colors = new int[parsed.size()];
                    for (int i = 0; i < colors.length; i++) {
                        colors[i] = parsed.get(i);
                    }
                    picker.setPalette(colors);
                }
            }
            String value = ctx.attr(element, "value");
            if (!value.isEmpty()) {
                if (!picker.setHex(value)) {
                    ctx.warn("Bad value '" + value + "' on <colorpicker>");
                }
            }
            String columns = ctx.attr(element, "columns");
            if (!columns.isEmpty()) {
                try {
                    picker.setColumns(Integer.parseInt(columns.trim()));
                } catch (NumberFormatException e) {
                    ctx.warn("Bad columns '" + columns + "' on <colorpicker>");
                }
            }
            if (ctx.hasAttr(element, "no-preview")) {
                picker.setShowPreview(false);
            }
            if (ctx.hasAttr(element, "no-hex")) {
                picker.setShowHex(false);
            }
            return picker;
        });

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
