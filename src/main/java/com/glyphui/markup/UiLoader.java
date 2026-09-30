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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

/**
 * Builds a {@link Component} tree from an HTML-like markup document.
 *
 * <p>This is the declarative front-end of Glyph-UI: UI structure is written
 * in a small HTML subset and styling comes from CSS (see
 * {@link com.glyphui.style}). The loader maps tags to widgets:</p>
 *
 * <ul>
 *   <li>{@code div}, {@code body}, {@code section}, {@code span}, unknown
 *       tags → {@link Panel} (unknown tags log a warning and keep their tag
 *       name as the style tag so type selectors still match)</li>
 *   <li>{@code button} → {@link Button}</li>
 *   <li>{@code label}, {@code p}, {@code h1}..{@code h6} → {@link Label}</li>
 *   <li>{@code input}, {@code textarea} → {@link TextField}</li>
 *   <li>{@code img} → {@link ImageView} (loaded through the optional
 *       {@link ImageProvider})</li>
 * </ul>
 *
 * <p>Recognized attributes:</p>
 * <ul>
 *   <li>{@code id} → {@link Component#setId(String)}</li>
 *   <li>{@code class} → space-separated {@link Component#addStyleClass(String)}</li>
 *   <li>{@code style} → inline declarations via {@link Style#parseInline}</li>
 *   <li>{@code layout="flex|flow"} → installs {@link FlexLayout} or
 *       {@link FlowLayout} on container widgets</li>
 *   <li>{@code x}, {@code y}, {@code width}, {@code height} → numeric bounds</li>
 *   <li>{@code text} or the element's own text content → widget text</li>
 *   <li>{@code src} (img) → resolved through the {@link ImageProvider}</li>
 *   <li>{@code onclick} / {@code onchange} → zero-argument method looked up
 *       by name on the registered controller object (reflection)</li>
 * </ul>
 *
 * <p><b>Not a browser:</b> there is no JavaScript engine and no DOM — this
 * is a structural loader for a fixed tag subset.</p>
 */
public final class UiLoader {

    private static final Logger LOG = Logger.getLogger(UiLoader.class.getName());

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
    private final List<String> warnings = new ArrayList<>();

    /**
     * Creates a loader without a controller.
     */
    public UiLoader() {
    }

    /**
     * Creates a loader whose {@code onclick}/{@code onchange} handlers are
     * resolved against the given controller object.
     *
     * @param controller object exposing zero-argument public handler methods
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
     * The stylesheet discovered while parsing (an {@code <style>} block or a
     * linked {@code .css} file next to the document). Callers can feed it to
     * {@link com.glyphui.style.StyleEngine}.
     *
     * @return the discovered sheet, or null when the document has none
     */
    public StyleSheet getLastStyleSheet() {
        return lastStyleSheet;
    }

    /**
     * Non-fatal problems encountered during the last load (unknown tags,
     * missing handlers, unparsable numbers, ...).
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
     * Parses a markup string and builds the component tree. Any
     * {@code <style>} block found in the document is exposed through
     * {@link #getLastStyleSheet()}.
     *
     * @param html the markup source
     * @return the root component (a {@link Panel} wrapping {@code <body>})
     */
    public Component loadFromString(String html) {
        Document doc = Jsoup.parse(html);
        return loadDocument(doc, null);
    }

    /**
     * Loads a markup file from disk. If the document references stylesheets
     * via {@code <link rel="stylesheet" href="app.css">}, the first existing
     * sibling file is parsed into {@link #getLastStyleSheet()}.
     *
     * @param file the {@code .html} / {@code .ui.xml} path
     * @return the root component
     * @throws IOException when the file cannot be read
     */
    public Component load(Path file) throws IOException {
        String source = Files.readString(file, StandardCharsets.UTF_8);
        Document doc = Jsoup.parse(source);
        return loadDocument(doc, file.toAbsolutePath().getParent());
    }

    /**
     * Loads a markup document from the classpath, e.g.
     * {@code load("/demo/ui.html", controller)}.
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
        Document doc = Jsoup.parse(source);
        return loadDocument(doc, null);
    }

    // ------------------------------------------------------------------
    // Tree construction
    // ------------------------------------------------------------------

    private Component loadDocument(Document doc, Path baseDir) {
        lastStyleSheet = extractStyleSheet(doc, baseDir);

        Element body = doc.body();
        Element root = body != null ? body : doc.selectFirst("root, ui, panel");
        if (root == null) {
            root = doc.getAllElements().isEmpty() ? doc.createElement("div")
                    : doc.getAllElements().first();
        }

        Panel rootPanel = new Panel(0, 0, Float.NaN, Float.NaN);
        rootPanel.setStyleTag(root.tagName());
        applyCommonAttributes(root, rootPanel);
        applyLayout(root, rootPanel);
        for (Element child : root.children()) {
            Component c = buildComponent(child);
            if (c != null) {
                rootPanel.add(c);
            }
        }
        return rootPanel;
    }

    /**
     * Recursively builds a component from one element. Exposed for tests and
     * embedding scenarios where a caller already owns the root.
     *
     * @param element the jsoup element
     * @return the constructed component, or null when the element was skipped
     */
    public Component buildComponent(Element element) {
        String tag = element.tagName().toLowerCase(java.util.Locale.ROOT);
        Component component = createForTag(element, tag);
        if (component == null) {
            return null;
        }

        applyCommonAttributes(element, component);
        applyLayout(element, component);
        bindHandlers(element, component);

        if (component instanceof Panel panel) {
            for (Element child : element.children()) {
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
                bindClick(b, element);
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
                String sizeAttr = element.attr("font-size");
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
                String value = !element.attr("value").isEmpty()
                        ? element.attr("value") : textOf(element);
                if (!value.isEmpty()) {
                    tf.setText(value);
                }
                return tf;
            }
            case "img": {
                ImageView iv = new ImageView();
                String src = element.attr("src");
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
        String id = element.attr("id");
        if (!id.isEmpty()) {
            component.setId(id);
        }
        String classes = element.attr("class");
        if (!classes.isEmpty()) {
            for (String cls : classes.split("\\s+")) {
                if (!cls.isEmpty()) {
                    component.addStyleClass(cls);
                }
            }
        }
        String inline = element.attr("style");
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
        applyNumber(element, "x", v -> component.setX(v));
        applyNumber(element, "y", v -> component.setY(v));
        applyNumber(element, "width", v -> component.setWidth(v));
        applyNumber(element, "height", v -> component.setHeight(v));
    }

    private interface FloatConsumer {
        void accept(float value);
    }

    private void applyNumber(Element element, String attr, FloatConsumer consumer) {
        String raw = element.attr(attr);
        if (raw.isEmpty()) {
            return;
        }
        try {
            consumer.accept(Float.parseFloat(raw.trim()));
        } catch (NumberFormatException e) {
            warn("Bad numeric attribute " + attr + "='" + raw + "' on <"
                    + element.tagName() + ">");
        }
    }

    private void applyLayout(Element element, Component component) {
        String layout = element.attr("layout").trim().toLowerCase(java.util.Locale.ROOT);
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
                warn("Unknown layout '" + layout + "' on <" + element.tagName() + ">");
        }
    }

    private void bindHandlers(Element element, Component component) {
        String change = element.attr("onchange");
        if (!change.isEmpty() && component instanceof TextField field) {
            Runnable r = resolveHandler(change, element, "onchange");
            if (r != null) {
                field.setOnTextChanged(r);
            }
        }
    }

    private void bindClick(Button button, Element element) {
        String click = element.attr("onclick");
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
     * method must be public and take no arguments (a {@code Runnable} /
     * {@code Consumer<Component>} overload is also accepted).
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

    /** Best-effort lookup of the component currently being built. */
    private Component findComponentFor(Element element) {
        String id = element.attr("id");
        if (!id.isEmpty() && lastRoot != null) {
            Component found = findById(lastRoot, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Component lastRoot;

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

    private String textOf(Element element) {
        String explicit = element.attr("text");
        if (!explicit.isEmpty()) {
            return explicit;
        }
        return element.ownText();
    }

    private StyleSheet extractStyleSheet(Document doc, Path baseDir) {
        StringBuilder css = new StringBuilder();
        for (Element style : doc.select("style")) {
            css.append(style.data()).append('\n');
        }
        if (css.length() == 0) {
            for (Element link : doc.select("link[rel=stylesheet]")) {
                String href = link.attr("href");
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
