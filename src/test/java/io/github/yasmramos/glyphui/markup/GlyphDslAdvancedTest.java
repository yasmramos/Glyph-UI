package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.reactive.Signal;
import io.github.yasmramos.glyphui.reactive.SignalList;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Dropdown;
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.ProgressBar;
import io.github.yasmramos.glyphui.ui.RadioButton;
import io.github.yasmramos.glyphui.ui.ToggleSwitch;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the advanced {@code .glyph} features: reactive {@code {path}}
 * interpolations, Compose-style PascalCase tags and the control-flow
 * directives {@code for-each} / {@code if} / {@code elif} / {@code else}.
 */
class GlyphDslAdvancedTest {

    private static Component load(UiLoader loader, String source) {
        return loader.loadFromString(source);
    }

    private static Component firstChild(Component root) {
        return ((Panel) root).getChildren().get(0);
    }

    private static List<Component> children(Component component) {
        return ((Panel) component).getChildren();
    }

    @Test
    void pascalCaseTagsMapToTheSameWidgetsAsTheirHtmlSpellings() {
        UiLoader loader = new UiLoader();
        Component root = load(loader, """
                Column {
                    ImageView(id = "i")
                    ProgressBar(id = "p")
                    Dropdown(id = "d")
                    RadioButton(id = "r")
                    ToggleSwitch(id = "s")
                    Hyperlink(id = "h")
                }
                """);
        assertInstanceOf(ImageView.class, UiLoader.findById(root, "i"));
        assertInstanceOf(ProgressBar.class, UiLoader.findById(root, "p"));
        assertInstanceOf(Dropdown.class, UiLoader.findById(root, "d"));
        assertInstanceOf(RadioButton.class, UiLoader.findById(root, "r"));
        assertInstanceOf(ToggleSwitch.class, UiLoader.findById(root, "s"));
        assertInstanceOf(io.github.yasmramos.glyphui.ui.Link.class,
                UiLoader.findById(root, "h"));
    }

    @Test
    void interpolationResolvesOnLoadAndFollowsTheSignal() {
        UiLoader loader = new UiLoader();
        Signal<String> title = new Signal<>("Glyph");
        loader.registerBinding("view.title", title);
        Component root = load(loader, """
                Column {
                    Text("App: {view.title}", id = "t")
                }
                """);
        Label label = (Label) UiLoader.findById(root, "t");
        assertEquals("App: Glyph", label.getText());

        title.set("Glyph-UI");
        assertEquals("App: Glyph-UI", label.getText());
    }

    @Test
    void mixedStaticTextAndInterpolationsAreConcatenated() {
        UiLoader loader = new UiLoader();
        Signal<Integer> count = new Signal<>(0);
        loader.registerBinding("count", count);
        Component root = load(loader, """
                Column {
                    Text("Items: {count} total", id = "t")
                }
                """);
        Label label = (Label) UiLoader.findById(root, "t");
        assertEquals("Items: 0 total", label.getText());
        count.set(3);
        assertEquals("Items: 3 total", label.getText());
    }

    @Test
    void bindTextAttributeAlsoDrivesTheCaption() {
        UiLoader loader = new UiLoader();
        Signal<String> value = new Signal<>("first");
        loader.registerBinding("v", value);
        Component root = load(loader, """
                Column {
                    Text(bindText = "v", id = "t")
                }
                """);
        Label label = (Label) UiLoader.findById(root, "t");
        assertEquals("first", label.getText());
        value.set("second");
        assertEquals("second", label.getText());
    }

    @Test
    void unknownInterpolationWarnsAndLeavesStaticTextIntact() {
        UiLoader loader = new UiLoader();
        Component root = load(loader, """
                Column {
                    Text("Hello {ghost}", id = "t")
                }
                """);
        Label label = (Label) UiLoader.findById(root, "t");
        assertEquals("Hello", label.getText(), "the unknown path contributes nothing");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("{ghost}")),
                () -> "expected an unknown-path warning: " + loader.getWarnings());
    }

    @Test
    void bindingOnANonTextWidgetWarns() {
        UiLoader loader = new UiLoader();
        loader.registerBinding("v", new Signal<>("x"));
        load(loader, """
                Column {
                    ImageView({v}, id = "i")
                }
                """);
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("no caption")),
                () -> "expected a no-caption warning: " + loader.getWarnings());
    }

    @Test
    void forEachExpandsOneRowPerItemWithItemAndIndexLocals() {
        UiLoader loader = new UiLoader();
        SignalList<String> items = new SignalList<>(List.of("a", "b", "c"));
        loader.registerBinding("items", items);
        Component root = load(loader, """
                Column {
                    Row(forEach = "items") {
                        Text("{index}: {item}")
                    }
                }
                """);
        Panel column = (Panel) firstChild(root);
        List<Component> rows = children(column);
        assertEquals(3, rows.size());
        assertEquals("0: a", ((Label) firstChild(rows.get(0))).getText());
        assertEquals("1: b", ((Label) firstChild(rows.get(1))).getText());
        assertEquals("2: c", ((Label) firstChild(rows.get(2))).getText());
    }

    @Test
    void forEachOverAMapExposesEntriesAsItemDotKey() {
        UiLoader loader = new UiLoader();
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("name", "Ada");
        SignalList<Map<String, String>> rows = new SignalList<>(List.of(entry));
        loader.registerBinding("rows", rows);
        Component root = load(loader, """
                Column {
                    Row(forEach = "rows") {
                        Text("Hi {item.name}")
                    }
                }
                """);
        Label label = (Label) firstChild(firstChild(firstChild(root)));
        assertEquals("Hi Ada", label.getText());
    }

    @Test
    void forEachOverANonListWarnsAndBuildsNothing() {
        UiLoader loader = new UiLoader();
        loader.registerBinding("notList", new Signal<>("nope"));
        Component root = load(loader, """
                Column {
                    Row(forEach = "notList") { Text("x") }
                }
                """);
        assertTrue(children(firstChild(root)).isEmpty());
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("expects a list")),
                () -> "expected a list warning: " + loader.getWarnings());
    }

    @Test
    void ifChainTogglesVisibilityReactively() {
        UiLoader loader = new UiLoader();
        Signal<Boolean> loading = new Signal<>(true);
        Signal<String> error = new Signal<>(null);
        loader.registerBinding("loading", loading);
        loader.registerBinding("error", error);
        Component root = load(loader, """
                Column {
                    Text("Loading", if = "loading", id = "a")
                    Text("Error", elif = "error", id = "b")
                    Text("Ready", else, id = "c")
                }
                """);
        Label a = (Label) UiLoader.findById(root, "a");
        Label b = (Label) UiLoader.findById(root, "b");
        Label c = (Label) UiLoader.findById(root, "c");
        assertTrue(a.isVisible());
        assertTrue(!b.isVisible() && !c.isVisible());

        loading.set(false);
        assertTrue(!a.isVisible() && !b.isVisible() && c.isVisible(),
                "only the else branch is visible when nothing matched");

        error.set("boom");
        assertTrue(!a.isVisible() && b.isVisible() && !c.isVisible(),
                "the first matching branch wins");
    }

    @Test
    void elseOnlyChainIsVisibleWhenNoIfMatched() {
        UiLoader loader = new UiLoader();
        Signal<Boolean> flag = new Signal<>(false);
        loader.registerBinding("flag", flag);
        Component root = load(loader, """
                Column {
                    Text("A", if = "flag", id = "a")
                    Text("B", else, id = "b")
                }
                """);
        assertTrue(!((Label) UiLoader.findById(root, "a")).isVisible());
        assertTrue(((Label) UiLoader.findById(root, "b")).isVisible());
        flag.set(true);
        assertTrue(((Label) UiLoader.findById(root, "a")).isVisible());
        assertTrue(!((Label) UiLoader.findById(root, "b")).isVisible());
    }

    @Test
    void danglingElifOrElseWarns() {
        UiLoader loader = new UiLoader();
        load(loader, """
                Column {
                    Text("orphan", else)
                }
                """);
        assertTrue(loader.getWarnings().stream()
                        .anyMatch(w -> w.contains("without a preceding 'if'")),
                () -> "expected a dangling-directive warning: " + loader.getWarnings());
    }

    @Test
    void controlFlowWorksInTheXmlFrontEndToo() {
        UiLoader loader = new UiLoader();
        SignalList<String> items = new SignalList<>(List.of("x", "y"));
        loader.registerBinding("items", items);
        Component root = load(loader, """
                <body>
                  <div for-each="items"><label>{item}</label></div>
                </body>
                """);
        List<Component> rows = children(root);
        assertEquals(2, rows.size());
        assertEquals("x", ((Label) firstChild(rows.get(0))).getText());
        assertEquals("y", ((Label) firstChild(rows.get(1))).getText());
    }

    @Test
    void deeplyNestedBindingsAreDisposedWithTheirComponent() {
        UiLoader loader = new UiLoader();
        Signal<String> value = new Signal<>("live");
        loader.registerBinding("v", value);
        Component root = load(loader, """
                Column {
                    Text("{v}", id = "t")
                }
                """);
        Label label = (Label) UiLoader.findById(root, "t");
        assertEquals("live", label.getText());
        label.dispose();
        // After disposal the binding is gone: a later change must not throw.
        value.set("changed");
        assertEquals("live", label.getText(), "a disposed widget no longer follows");
    }
}
