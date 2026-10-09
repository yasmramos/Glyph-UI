package com.glyphui.markup;

import com.glyphui.graphics.Image;
import com.glyphui.layout.FlexLayout;
import com.glyphui.layout.FlowLayout;
import com.glyphui.style.Style;
import com.glyphui.style.StyleSheet;
import com.glyphui.ui.Button;
import com.glyphui.ui.Component;
import com.glyphui.ui.ImageView;
import com.glyphui.ui.Label;
import com.glyphui.ui.Panel;
import com.glyphui.ui.TextField;

import java.io.IOException;
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
 * widgets and styling comes from CSS (see {@link com.glyphui.style}).</p>
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
 * exposed through {@link #getLastStyleSheet()}. Inline {@code <style>} blocks
 * are <b>not</b> supported because {@code &#123;} is the interpolation
 * delimiter in {@code .glyph}.</p>
 *
 * <p><b>Not a browser:</b> there is no JavaScript engine and no DOM — this
 * is a structural loader for a fixed tag subset.</p>
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

    private Object controller;
    private ImageProvider imageProvider;
    private StyleSheet lastStyleSheet;
    private Document lastDocument;
    private final List<String> warnings = new ArrayList<>();
    private final Map<String, Component> componentsById = new HashMap<>();

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
     * The stylesheet discovered while parsing (a linked {@code .css} file next
     * to the document, or resolvable from the classpath). Callers can feed it
     * to {@link com.glyphui.style.StyleEngine}.
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
        switch (tag) {
            case "button": {
                Button b = new Button(0, 0, 120, 32, textOf(element));
                return b;
            }
            case "label":
            case "p":
            case "h1":
            case "h2":
            case "h3":
            case "h4":
            case "h5":
            case "h6": {
                String sizeAttr = attr(element, "font-size");
                Label l = new Label(0, 0, 200, 24, textOf(element));
                if (!sizeAttr.isEmpty()) {
                    try {
                        l.setFontSize(Float.parseFloat(sizeAttr.replaceAll("[^0-9.]", "")));
                    } catch (NumberFormatException e) {
                        warn("Bad font-size '" + sizeAttr + "' on <" + tag + ">");
                    }
                }
                return l;
            }
            case "input":
            case "textarea": {
                TextField tf = new TextField();
                String value = !attr(element, "value").isEmpty()
                        ? attr(element, "value") : textOf(element);
                if (!value.isEmpty()) {
                    tf.setText(value);
                }
                return tf;
            }
            case "img": {
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
            case "div":
            case "body":
            case "section":
            case "span":
            case "panel":
            case "root":
            case "ui":
                return new Panel(0, 0, Float.NaN, Float.NaN);
            default: {
                // Unknown tag: generic panel that still matches its own type selector.
                warn("Unknown tag <" + tag + ">, mapped to generic Panel");
                Panel p = new Panel(0, 0, Float.NaN, Float.NaN);
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
            consumer.accept(Float.parseFloat(raw.trim()));
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
            case "flex":
                panel.setLayoutManager(new FlexLayout());
                break;
            case "flow":
                panel.setLayoutManager(new FlowLayout());
                break;
            default:
                warn("Unknown layout '" + layout + "' on <" + element.getTagName() + ">");
        }
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
            button.setOnClick(r);
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
                } catch (Exception e) {
                    LOG.warning("Handler '" + methodName + "' failed: " + e);
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
                    } catch (Exception ex) {
                        LOG.warning("Handler '" + methodName + "' failed: " + ex);
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

    /**
     * Reads an attribute value from the raw AST.
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

    /**
     * Concatenates the direct text children of an element; an explicit
     * {@code text} attribute takes precedence. Binding interpolations are not
     * resolved here — a warning is emitted instead.
     *
     * @param element the element whose text is requested
     * @return the element text (may be empty)
     */
    private String textOf(Element element) {
        String explicit = attr(element, "text");
        if (!explicit.isEmpty()) {
            return explicit;
        }
        StringBuilder sb = new StringBuilder();
        for (AstNode child : element.getChildren()) {
            if (child instanceof TextNode textNode) {
                sb.append(textNode.getText());
            } else if (child instanceof BindingNode binding) {
                warn("Binding {" + binding.getRawPath() + "} in <"
                        + element.getTagName()
                        + "> ignored: reactive bindings are not wired yet");
            }
        }
        return sb.toString();
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
        LOG.warning("[UiLoader] " + message);
    }
}
