package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.layout.FlowLayout;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleSheet;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.TextField;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Builds a {@link Component} tree from a {@code .glyph} declarative markup
 * document.
 *
 * <p>This is the declarative front-end of Glyph-UI. The document is scanned
 * by {@link Tokenizer} and turned into a {@link Document} AST by {@link Parser}
 * (no third-party HTML parser is involved), then this loader maps the AST onto
 * widgets and styling comes from CSS (see {@link io.github.yasmramos.glyphui.style}).</p>
 *
 * <h2>Syntax</h2>
 * <p>{@code .glyph} markup looks like XML: {@code <Tag attr="value">children
 * </Tag>} or the self-closing {@code <Tag/>} form. Comments use
 * {@code <!-- ... -->}. Content may contain {@code {path}} interpolations and
 * the literal brace escapes {@code &#123;&#123;} / {@code &#125;&#125;}; the
 * parser keeps those as {@link BindingNode} children, but this loader does not
 * yet wire them to reactive properties (it records a warning and ignores
 * them).</p>
 *
 * <h2>Tag mapping</h2>
 * <ul>
 *   <li>{@code div}, {@code body}, {@code section}, {@code span}, unknown
 *       tags → {@link Panel} (unknown tags log a warning and keep their tag
 *       name as the style tag so type selectors still match)</li>
 *   <li>{@code button} → {@link Button}</li>
 *   <li>{@code label}, {@code p}, {@code h1}..{@code h6} → {@link Label}</li>
 *   <li>{@code input}, {@code textarea} → {@link TextField}</li>
 *   <li>{@code img} → {@link ImageView} (loaded through the optional
 *       {@link ImageProvider})</li>
 *   <li>{@code head}/{@code title}/{@code meta}/{@code link}/{@code style}/
 *       {@code script}/{@code base} are document metadata and never become
 *       widgets</li>
 * </ul>
 *
 * <h2>Document root</h2>
 * <p>If the document contains a {@code body} element, it becomes the root
 * container (its children populate the returned root {@link Panel}). Otherwise
 * a single top-level widget element is used as the root; with several
 * top-level elements a synthetic {@code body} panel wraps them all.</p>
 *
 * <h2>Recognized attributes</h2>
 * <ul>
 *   <li>{@code id} → {@link Component#setId(String)}</li>
 *   <li>{@code class} → space-separated {@link Component#addStyleClass(String)}</li>
 *   <li>{@code style} → inline declarations via {@link Style#parseInline}</li>
 *   <li>{@code layout="flex|flow"} → installs {@link FlexLayout} or
 *       {@link FlowLayout} on container widgets</li>
 *   <li>{@code x}, {@code y}, {@code width}, {@code height} → numeric bounds</li>
 *   <li>{@code text} or the element's own text content → widget text</li>
 *   <li>{@code src} (img) → resolved through the {@link ImageProvider}</li>
 *   <li>{@code onclick} / {@code onchange} → method looked up by name on the
 *       registered controller object (reflection)</li>
 * </ul>
 *
 * <p><b>Stylesheets:</b> link an external sheet with
 * {@code <link rel="stylesheet" href="app.css"/>}; the resolved sheet is
 * exposed through {@link #getLastStyleSheet()} or, more conveniently, through
 * {@link #loadWithStylesheet(Path)} / {@link #loadWithStylesheet(String, Object)}
 * which return a {@link LoadResult} bundling root + sheet. The loader does
 * <b>not</b> apply the sheet to the running {@code Application} automatically:
 * styling lives on the application's {@link io.github.yasmramos.glyphui.style.StyleEngine},
 * so the documented flow is:
 * {@code UiLoader.LoadResult r = loader.loadWithStylesheet(file); app.applyStyleSheet(r.styleSheet());}.
 * Forgetting the {@code applyStyleSheet} call means CSS classes in the markup
 * have no effect. Inline {@code <style>} blocks are <b>not</b> supported
 * because {@code &#123;} is the interpolation delimiter in {@code .glyph}.</p>
 *
 * <p><b>Layout attributes:</b> containers accept {@code layout="flex|flow"},
 * plus {@code direction}, {@code justify}, {@code align} (flex only) and
 * {@code gap}/{@code padding} (both), which are mapped onto
 * {@link FlexLayout}/{@link FlowLayout}. Unknown values log a warning and keep
 * the manager default. Equivalent CSS properties via style classes also work
 * once the sheet is applied.</p>
 *
 * <p><b>Bindings and control flow:</b> there is no Binder or Validator yet.
 * Markup supports {@code {path}} interpolations in element content only —
 * they are parsed into {@link BindingNode}s but this loader records a warning
 * (once per distinct path per load) and ignores them. Interpolations inside
 * attribute values, {@code bind:} prefixes and {@code if}/{@code for-each}
 * control-flow attributes are not part of the current feature set.</p>
 *
 * <p><b>Not a browser:</b> there is no JavaScript engine and no DOM — this
 * is a structural loader for a fixed tag subset; custom widgets can be added
 * through {@link #registerTag(String, TagFactory)}.</p>
 */
public final class UiLoader {

    private static final Logger LOG = Logger.getLogger(UiLoader.class.getName());

    /**
     * Tags that describe the document rather than the widget tree. They are
     * skipped both when locating the root container and when walking children.
     */
    private static final Set<String> METADATA_TAGS = Set.of(
            "head", "title", "meta", "link", "style", "script", "base");

    /** Loads image bytes for {@code <img src="...">} elements. */
    @FunctionalInterface
    public interface ImageProvider {
        /**
         * Resolves a {@code src} value to an image.
         *
         * @param src the raw attribute value
         * @return the image (ownership transfers to the view), or null to skip
         */
        Image load(String src);
    }

    /**
     * Factory that turns an element into a widget for a custom tag. Register
     * one through {@link UiLoader#registerTag(String, TagFactory)} to support
     * markup such as {@code <Chart/>} without modifying this class.
     */
    @FunctionalInterface
    public interface TagFactory {
        /**
         * Creates the component for the given element. The loader will still
         * apply common attributes (id/class/style/x/y/width/height), layout
         * and event handlers on top of whatever this factory returns.
         *
         * @param element the AST element to materialize
         * @return the new component (must not be null)
         */
        Component create(Element element);
    }

    private Object controller;
    private ImageProvider imageProvider;
    private StyleSheet lastStyleSheet;
    private Document lastDocument;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Component> componentsById = new HashMap<>();
    /** User-registered custom tag factories (lower-cased tag name -> factory). */
    private final Map<String, TagFactory> tagFactories = new HashMap<>();
    /** Binding paths already warned about during the current load (dedupe). */
    private final Set<String> warnedBindings = new java.util.HashSet<>();

    /**
     * Creates a loader without a controller.
     */
    public UiLoader() {
    }

    /**
     * Creates a loader whose {@code onclick}/{@code onchange} handlers are
     * resolved against the given controller object.
     *
     * @param controller object exposing public handler methods
     */
    public UiLoader(Object controller) {
        this.controller = controller;
    }

    /**
     * Sets the controller used to resolve event-handler attributes.
     *
     * @param controller the controller object (may be null)
     */
    public void setController(Object controller) {
        this.controller = controller;
    }

    /**
     * Sets the provider used for {@code <img src>} resolution. Without one,
     * image elements are created empty and a warning is recorded.
     *
     * @param imageProvider the provider (may be null)
     */
    public void setImageProvider(ImageProvider imageProvider) {
        this.imageProvider = imageProvider;
    }

    /**
     * Registers a factory for a custom tag, e.g.
     * {@code loader.registerTag("chart", el -> new ChartWidget())} so that
     * {@code <Chart/>} elements build user widgets instead of falling back to
     * a generic panel. Registered factories take precedence over the built-in
     * tag mapping and suppress the "unknown tag" warning. Tag names are
     * matched case-insensitively.
     *
     * @param tag    the element tag name (without angle brackets)
     * @param factory the widget factory (must not be null)
     */
    public void registerTag(String tag, TagFactory factory) {
        if (tag == null || tag.isEmpty() || factory == null) {
            throw new IllegalArgumentException("tag and factory must not be empty/null");
        }
        tagFactories.put(tag.toLowerCase(Locale.ROOT), factory);
    }

    /**
     * Removes a previously registered custom tag factory.
     *
     * @param tag the tag name to unregister
     */
    public void unregisterTag(String tag) {
        if (tag != null) {
            tagFactories.remove(tag.toLowerCase(Locale.ROOT));
        }
    }

    /**
     * The stylesheet discovered while parsing (a linked {@code .css} file next
     * to the document, or resolvable from the classpath). Callers can feed it
     * to {@link io.github.yasmramos.glyphui.style.StyleEngine}.
     *
     * @return the discovered sheet, or null when the document links none
     */
    public StyleSheet getLastStyleSheet() {
        return lastStyleSheet;
    }

    /**
     * The raw AST produced by the last load, useful for tooling and tests.
     *
     * @return the parsed document, or null before the first load
     */
    public Document getLastDocument() {
        return lastDocument;
    }

    /**
     * Non-fatal problems encountered during the last load (unknown tags,
     * missing handlers, unparsable numbers, ignored bindings, ...).
     *
     * @return unmodifiable list of warning messages
     */
    public List<String> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }

    // ------------------------------------------------------------------
    // Entry points
    // ------------------------------------------------------------------

    /**
     * Parses a {@code .glyph} markup string and builds the component tree.
     *
     * @param glyph the markup source
     * @return the root component (a {@link Panel} wrapping the container)
     * @throws MarkupException on an unrecoverable lexical error
     */
    public Component loadFromString(String glyph) {
        return loadSource(glyph, null);
    }

    /**
     * Loads a {@code .glyph} markup file from disk. When the document links a
     * stylesheet through {@code <link rel="stylesheet" href="app.css">}, the
     * file is resolved relative to the document's directory.
     *
     * @param file the {@code .glyph} path
     * @return the root component
     * @throws IOException when the file cannot be read
     */
    public Component load(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        return loadSource(source, file.toAbsolutePath().getParent());
    }

    /**
     * Loads a {@code .glyph} document from the classpath, e.g.
     * {@code load("/demo/ui.glyph", controller)} (the demo files ship with
     * the separate {@code glyph-ui-examples} module).
     *
     * @param resource   the classpath resource name
     * @param controller object for {@code onclick}/{@code onchange} resolution
     * @return the root component
     * @throws IOException when the resource is missing or unreadable
     */
    public Component load(String resource, Object controller) throws IOException {
        this.controller = controller;
        var stream = UiLoader.class.getResourceAsStream(resource);
        if (stream == null) {
            throw new IOException("Markup resource not found: " + resource);
        }
        String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        return loadSource(source, null);
    }

    /**
     * Shared front-end: tokenize/parse, surface recoverable diagnostics as
     * warnings and build the tree.
     *
     * @param source  the markup text
     * @param baseDir directory used to resolve linked stylesheets, or null
     * @return the root component
     */
    private Component loadSource(String source, Path baseDir) {
        // Reset last-load state up front: a failed parse must not leave the
        // previous document/sheet visible to tests or tooling.
        lastDocument = null;
        lastStyleSheet = null;
        warnedBindings.clear();
        warnings.clear();
        componentsById.clear();
        ParseResult result = new Parser().parse(source);
        for (ParseError error : result.errors()) {
            warn("parse error " + error);
        }
        return loadDocument(result.document(), baseDir);
    }

    // ------------------------------------------------------------------
    // Tree construction
    // ------------------------------------------------------------------

    private Component loadDocument(Document doc, Path baseDir) {
        lastDocument = doc;
        lastStyleSheet = extractStyleSheet(doc, baseDir);

        Panel rootPanel = new Panel(0, 0, Float.NaN, Float.NaN);
        Element container = findContainer(doc);
        if (container != null) {
            rootPanel.setStyleTag(container.getTagName());
            applyCommonAttributes(container, rootPanel);
            applyLayout(container, rootPanel);
            for (Element child : widgetChildren(container)) {
                Component c = buildComponent(child);
                if (c != null) {
                    rootPanel.add(c);
                }
            }
        } else {
            rootPanel.setStyleTag("body");
            for (AstNode node : doc.getChildren()) {
                if (node instanceof Element element && !isMetadata(element)) {
                    Component c = buildComponent(element);
                    if (c != null) {
                        rootPanel.add(c);
                    }
                }
            }
        }
        return rootPanel;
    }

    /**
     * Recursively builds a component from one element. Exposed for tests and
     * embedding scenarios where a caller already owns the root.
     *
     * @param element the AST element
     * @return the constructed component, or null when the element was skipped
     */
    public Component buildComponent(Element element) {
        String tag = element.getTagName().toLowerCase(Locale.ROOT);
        Component component = createForTag(element, tag);
        if (component == null) {
            return null;
        }

        applyCommonAttributes(element, component);
        applyLayout(element, component);
        bindHandlers(element, component);
        if (component instanceof Button button) {
            bindClick(button, element);
        }

        if (component instanceof Panel panel) {
            for (Element child : widgetChildren(element)) {
                Component c = buildComponent(child);
                if (c != null) {
                    panel.add(c);
                }
            }
        }
        return component;
    }

    private Component createForTag(Element element, String tag) {
        // User-registered custom tags win over the built-in mapping (4.17).
        TagFactory custom = tagFactories.get(tag);
        if (custom != null) {
            return custom.create(element);
        }
        switch (tag) {
            case "button" -> {
                // No hardcoded 120x32: content-sized widget; an explicit
                // width/height attribute (or inline style) is applied later by
                // applyCommonAttributes (4.16).
                return new Button(textOf(element));
            }
            case "label", "p", "h1", "h2", "h3", "h4", "h5", "h6" -> {
                String sizeAttr = attr(element, "font-size");
                Label l = new Label(textOf(element));
                if (!sizeAttr.isEmpty()) {
                    try {
                        l.setFontSize(Float.valueOf(sizeAttr.replaceAll("[^0-9.]", "")));
                    } catch (NumberFormatException e) {
                        warn("Bad font-size '" + sizeAttr + "' on <" + tag + ">");
                    }
                }
                return l;
            }
            case "input", "textarea" -> {
                TextField tf = new TextField();
                String value = !attr(element, "value").isEmpty()
                        ? attr(element, "value") : textOf(element);
                if (!value.isEmpty()) {
                    tf.setText(value);
                }
                return tf;
            }
            case "img" -> {
                ImageView iv = new ImageView();
                String src = attr(element, "src");
                if (!src.isEmpty()) {
                    if (imageProvider != null) {
                        Image image = imageProvider.load(src);
                        if (image != null) {
                            iv.setImage(image);
                        } else {
                            warn("ImageProvider returned null for src '" + src + "'");
                        }
                    } else {
                        warn("<img src=\"" + src + "\"> ignored: no ImageProvider set");
                    }
                }
                return iv;
            }
            case "div", "body", "section", "span", "panel", "root", "ui" -> {
                return new Panel(Float.NaN, Float.NaN);
            }
            default -> {
                // Unknown tag: generic panel that still matches its own type selector.
                warn("Unknown tag <" + tag + ">, mapped to generic Panel");
                Panel p = new Panel(Float.NaN, Float.NaN);
                p.setStyleTag(tag);
                return p;
            }
        }
    }

    private void applyCommonAttributes(Element element, Component component) {
        String id = attr(element, "id");
        if (!id.isEmpty()) {
            component.setId(id);
            componentsById.put(id, component);
        }
        String classes = attr(element, "class");
        if (!classes.isEmpty()) {
            for (String cls : classes.split("\\s+")) {
                if (!cls.isEmpty()) {
                    component.addStyleClass(cls);
                }
            }
        }
        String inline = attr(element, "style");
        if (!inline.isEmpty()) {
            try {
                Style style = Style.parseInline(inline,
                        lastStyleSheet != null ? lastStyleSheet.getRootVariables()
                                : Collections.emptyMap());
                component.setInlineStyle(style);
            } catch (RuntimeException e) {
                warn("Unparsable inline style '" + inline + "': " + e.getMessage());
            }
        }
        applyNumber(element, "x", component::setX);
        applyNumber(element, "y", component::setY);
        applyNumber(element, "width", component::setWidth);
        applyNumber(element, "height", component::setHeight);
    }

    private void applyNumber(Element element, String attr, java.util.function.Consumer<Float> consumer) {
        String raw = attr(element, attr);
        if (raw.isEmpty()) {
            return;
        }
        try {
            consumer.accept(Float.valueOf(raw.trim()));
        } catch (NumberFormatException e) {
            warn("Bad numeric attribute " + attr + "='" + raw + "' on <"
                    + element.getTagName() + ">");
        }
    }

    private void applyLayout(Element element, Component component) {
        String layout = attr(element, "layout").trim().toLowerCase(Locale.ROOT);
        if (layout.isEmpty() || !(component instanceof Panel panel)) {
            return;
        }
        switch (layout) {
            case "flex" -> {
                FlexLayout flex = new FlexLayout();
                String direction = attr(element, "direction").trim().toLowerCase(Locale.ROOT);
                if (!direction.isEmpty()) {
                    FlexLayout.Direction parsed = parseEnum(FlexLayout.Direction.class,
                            direction.replace('-', '_'));
                    if (parsed != null) {
                        flex.setDirection(parsed);
                    } else {
                        warn("Unknown direction '" + direction + "' on <"
                                + element.getTagName() + ">");
                    }
                }
                applyFlexKeyword(element, "justify", flex::setJustify,
                        FlexLayout.Justify.class);
                applyFlexKeyword(element, "align", flex::setAlignItems,
                        FlexLayout.Align.class);
                applySpacing(element, value -> flex.setGap(value),
                        value -> flex.setPadding(value));
                panel.setLayoutManager(flex);
            }
            case "flow" -> {
                FlowLayout flow = new FlowLayout();
                applySpacing(element, value -> flow.setGap(value),
                        value -> flow.setPadding(value));
                panel.setLayoutManager(flow);
            }
            default -> warn("Unknown layout '" + layout + "' on <" + element.getTagName() + ">");
        }
    }

    /**
     * Maps a markup attribute to an enum keyword on a layout manager, warning
     * on unknown values instead of throwing.
     */
    private <E extends Enum<E>> void applyFlexKeyword(Element element, String attrName,
            java.util.function.Consumer<E> setter, Class<E> type) {
        String raw = attr(element, attrName).trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return;
        }
        E value = parseEnum(type, raw.replace('-', '_').replace(':', '_'));
        if (value != null) {
            setter.accept(value);
        } else {
            warn("Unknown " + attrName + "='" + raw + "' on <" + element.getTagName() + ">");
        }
    }

    /** Reads the optional {@code gap} and {@code padding} numeric attributes. */
    private void applySpacing(Element element,
            java.util.function.Consumer<Float> gapSetter,
            java.util.function.Consumer<Float> paddingSetter) {
        String gap = attr(element, "gap").trim();
        if (!gap.isEmpty()) {
            try {
                gapSetter.accept(Float.valueOf(gap.replaceAll("[^0-9.]", "")));
            } catch (NumberFormatException e) {
                warn("Bad gap='" + gap + "' on <" + element.getTagName() + ">");
            }
        }
        String padding = attr(element, "padding").trim();
        if (!padding.isEmpty()) {
            try {
                paddingSetter.accept(Float.valueOf(padding.replaceAll("[^0-9.]", "")));
            } catch (NumberFormatException e) {
                warn("Bad padding='" + padding + "' on <" + element.getTagName() + ">");
            }
        }
    }

    /** Case-insensitive enum lookup tolerant of hyphens/underscores. */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
        for (E constant : type.getEnumConstants()) {
            if (constant.name().equalsIgnoreCase(raw)) {
                return constant;
            }
        }
        return null;
    }

    private void bindHandlers(Element element, Component component) {
        String change = attr(element, "onchange");
        if (!change.isEmpty() && component instanceof TextField field) {
            Runnable r = resolveHandler(change, element, "onchange");
            if (r != null) {
                field.setOnTextChanged(r);
            }
        }
    }

    private void bindClick(Button button, Element element) {
        String click = attr(element, "onclick");
        if (click.isEmpty()) {
            return;
        }
        Runnable r = resolveHandler(click, element, "onclick");
        if (r != null) {
            button.setOnClick(r::run);
        }
    }

    /**
     * Resolves a handler name to a Runnable bound to the controller. The
     * method must be public and take no arguments (a {@code Component}
     * parameter overload is also accepted).
     */
    private Runnable resolveHandler(String name, Element element, String attr) {
        if (controller == null) {
            warn(attr + "='" + name + "' ignored: no controller registered");
            return null;
        }
        String methodName = name.endsWith("()")
                ? name.substring(0, name.length() - 2).trim() : name.trim();
        try {
            Method m = controller.getClass().getMethod(methodName);
            return () -> {
                try {
                    m.invoke(controller);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    LOG.warning(() -> "Handler '" + methodName + "' failed: " + e);
                }
            };
        } catch (NoSuchMethodException e) {
            // Fall back to a Component-parameter signature.
            try {
                Method m = controller.getClass().getMethod(methodName, Component.class);
                Component target = findComponentFor(element);
                return () -> {
                    try {
                        m.invoke(controller, target);
                    } catch (IllegalAccessException | InvocationTargetException ex) {
                        LOG.warning(() -> "Handler '" + methodName + "' failed: " + ex);
                    }
                };
            } catch (NoSuchMethodException ex) {
                warn("Controller has no zero-arg (or Component-arg) method '"
                        + methodName + "' for " + attr);
                return null;
            }
        }
    }

    /** Looks up the component built for an element, by its {@code id}. */
    private Component findComponentFor(Element element) {
        String id = attr(element, "id");
        if (id.isEmpty()) {
            return null;
        }
        return componentsById.get(id);
    }

    /**
     * Finds a descendant (or self) by id in a component tree.
     *
     * @param root the subtree root
     * @param id   the id to search for
     * @return the matching component or null
     */
    public static Component findById(Component root, String id) {
        if (root == null || id == null) {
            return null;
        }
        if (id.equals(root.getId())) {
            return root;
        }
        if (root instanceof Panel panel) {
            for (Component child : panel.getChildren()) {
                Component found = findById(child, id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // AST helpers
    // ------------------------------------------------------------------

    /** Reads an attribute value from the raw AST.
     *
     * @param element the element to query
     * @param name    the raw attribute name
     * @return the value, or the empty string when the attribute is absent or
     *         valueless
     */
    private static String attr(Element element, String name) {
        Attribute attribute = element.findAttribute(name);
        return attribute == null || attribute.value() == null ? "" : attribute.value();
    }

    // ------------------------------------------------------------------
    // Load-and-apply convenience (stylesheets are NOT auto-applied; see
    // the class Javadoc "Styling flow" for why).
    // ------------------------------------------------------------------

    /**
     * Result of a load: the built component tree plus the stylesheet the
     * document linked, if any.
     *
     * @param root      the root component
     * @param styleSheet the discovered sheet, or null when the document has none
     */
    public record LoadResult(Component root, StyleSheet styleSheet) {
    }

    /**
     * Loads a {@code .glyph} file and returns both the widget tree and the
     * linked stylesheet, so callers can apply styling without going through
     * {@link #getLastStyleSheet()}.
     *
     * @param file the {@code .glyph} path
     * @return the load result
     * @throws IOException when the file cannot be read
     */
    public LoadResult loadWithStylesheet(Path file) throws IOException {
        Component root = load(file);
        return new LoadResult(root, lastStyleSheet);
    }

    /**
     * Loads a {@code .glyph} resource from the classpath and returns both the
     * widget tree and the linked stylesheet.
     *
     * @param resource   the classpath resource name
     * @param controller object for {@code onclick}/{@code onchange} resolution
     * @return the load result
     * @throws IOException when the resource is missing or unreadable
     */
    public LoadResult loadWithStylesheet(String resource, Object controller) throws IOException {
        Component root = load(resource, controller);
        return new LoadResult(root, lastStyleSheet);
    }

    /**
     * Concatenates the text of an element, recursively including the text of
     * child elements (like DOM {@code textContent}); an explicit {@code text}
     * attribute takes precedence. The result is trimmed so markup such as
     * {@code <Label>\n  Hola\n</Label>} does not render stray whitespace.
     * Binding interpolations are not resolved here — a warning is emitted
     * once per distinct path per load.
     *
     * @param element the element whose text is requested
     * @return the element text (may be empty)
     */
    private String textOf(Element element) {
        String explicit = attr(element, "text");
        if (!explicit.isEmpty()) {
            return explicit.trim();
        }
        StringBuilder sb = new StringBuilder();
        collectText(element, sb);
        return sb.toString().trim();
    }

    /** Depth-first text collection: TextNodes plus nested Element children. */
    private void collectText(Element element, StringBuilder sb) {
        for (AstNode child : element.getChildren()) {
            if (child instanceof TextNode textNode) {
                sb.append(textNode.getText());
            } else if (child instanceof Element nested) {
                // Inline children (<Label>Hola <b>mundo</b></Label>) contribute
                // their own text, matching textContent semantics.
                collectText(nested, sb);
            } else if (child instanceof BindingNode binding) {
                String path = binding.getRawPath();
                if (warnedBindings.add(path)) {
                    warn("Binding {" + path + "} in <"
                            + element.getTagName()
                            + "> ignored: reactive bindings are not wired yet");
                }
            }
        }
    }

    /** @return child elements that map to widgets (metadata tags excluded). */
    private static List<Element> widgetChildren(Element parent) {
        List<Element> out = new ArrayList<>();
        for (AstNode node : parent.getChildren()) {
            if (node instanceof Element element && !isMetadata(element)) {
                out.add(element);
            }
        }
        return out;
    }

    private static boolean isMetadata(Element element) {
        return METADATA_TAGS.contains(element.getTagName().toLowerCase(Locale.ROOT));
    }

    /**
     * Locates the document's root container: a {@code body} element when
     * present, otherwise the single top-level widget element, otherwise null
     * (callers wrap all top-level widgets in a synthetic panel).
     */
    private static Element findContainer(Document doc) {
        Element body = findFirst(doc.getChildren(), "body");
        if (body != null) {
            return body;
        }
        List<Element> topLevel = new ArrayList<>();
        for (AstNode node : doc.getChildren()) {
            if (node instanceof Element element && !isMetadata(element)) {
                topLevel.add(element);
            }
        }
        return topLevel.size() == 1 ? topLevel.get(0) : null;
    }

    private static Element findFirst(List<AstNode> nodes, String tag) {
        for (AstNode node : nodes) {
            if (node instanceof Element element) {
                if (element.getTagName().equalsIgnoreCase(tag)) {
                    return element;
                }
                Element found = findFirst(element.getChildren(), tag);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void findAll(List<AstNode> nodes, String tag, List<Element> out) {
        for (AstNode node : nodes) {
            if (node instanceof Element element) {
                if (element.getTagName().equalsIgnoreCase(tag)) {
                    out.add(element);
                }
                findAll(element.getChildren(), tag, out);
            }
        }
    }

    private StyleSheet extractStyleSheet(Document doc, Path baseDir) {
        List<Element> inlineStyles = new ArrayList<>();
        findAll(doc.getChildren(), "style", inlineStyles);
        if (!inlineStyles.isEmpty()) {
            warn("inline <style> is not supported in .glyph (its '{' delimiter "
                    + "conflicts with bindings); use <link rel=\"stylesheet\"> "
                    + "or load the sheet directly");
        }

        StringBuilder css = new StringBuilder();
        List<Element> links = new ArrayList<>();
        findAll(doc.getChildren(), "link", links);
        for (Element link : links) {
            if (!"stylesheet".equalsIgnoreCase(attr(link, "rel"))) {
                continue;
            }
            String href = attr(link, "href");
            if (href.isEmpty()) {
                continue;
            }
            try {
                if (baseDir != null) {
                    Path p = baseDir.resolve(href);
                    if (Files.isRegularFile(p)) {
                        css.append(Files.readString(p, StandardCharsets.UTF_8));
                        continue;
                    }
                }
                var stream = UiLoader.class.getResourceAsStream(
                        href.startsWith("/") ? href : "/" + href);
                if (stream != null) {
                    css.append(new String(stream.readAllBytes(),
                            StandardCharsets.UTF_8));
                } else {
                    warn("Stylesheet not found: " + href);
                }
            } catch (IOException e) {
                warn("Failed to read stylesheet '" + href + "': " + e.getMessage());
            }
        }
        if (css.length() == 0) {
            return null;
        }
        try {
            return StyleSheet.parse(css.toString());
        } catch (StyleSheet.StyleParseException e) {
            warn("Stylesheet parse error: " + e.getMessage());
            return null;
        }
    }

    private void warn(String message) {
        warnings.add(message);
        LOG.warning(() -> "[UiLoader] " + message);
    }
}
