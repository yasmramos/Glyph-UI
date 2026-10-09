package com.glyphui.markup;

import com.glyphui.layout.FlexLayout;
import com.glyphui.layout.FlowLayout;
import com.glyphui.style.StyleProperty;
import com.glyphui.ui.Button;
import com.glyphui.ui.Component;
import com.glyphui.ui.ImageView;
import com.glyphui.ui.Label;
import com.glyphui.ui.Panel;
import com.glyphui.ui.TextField;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the declarative {@code .glyph} loader: tag mapping, attribute
 * handling, controller event binding and stylesheet extraction. The loader is
 * backed by {@link Tokenizer}/{@link Parser}, not by a third-party HTML
 * parser.
 */
class UiLoaderTest {

    /** Controller used to verify onclick/onchange reflection binding. */
    public static class DemoController {
        int clicks;
        int changes;

        public void increment() {
            clicks++;
        }

        public void onChanged() {
            changes++;
        }
    }

    /** Controller exposing a Component-argument handler. */
    public static class ComponentArgController {
        Component received;

        public void onGo(Component component) {
            received = component;
        }
    }

    @Test
    void mapsTagsToWidgets() {
        String glyph = """
                <body>
                  <div id="root-panel" class="card toolbar">
                    <button id="ok">OK</button>
                    <label>Name</label>
                    <p>A paragraph</p>
                    <input id="name-field" value="Ada"/>
                    <img src="logo.png"/>
                  </div>
                </body>
                """;
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(glyph);

        assertTrue(root instanceof Panel);
        Panel container = (Panel) UiLoader.findById(root, "root-panel");
        assertNotNull(container, "div should map to Panel and be findable by id");
        assertEquals("div", container.getStyleTag());
        assertTrue(container.hasStyleClass("card"));
        assertTrue(container.hasStyleClass("toolbar"));

        Button button = (Button) UiLoader.findById(root, "ok");
        assertNotNull(button, "button tag must map to Button");
        assertEquals("OK", buttonText(button));

        TextField field = (TextField) UiLoader.findById(root, "name-field");
        assertNotNull(field, "input tag must map to TextField");
        assertEquals("Ada", field.getText());

        // Count children: 5 widgets inside the div.
        assertEquals(5, container.getChildren().size());

        // p -> Label, label -> Label, img -> ImageView somewhere in the tree.
        int labels = countByType(root, Label.class);
        int images = countByType(root, ImageView.class);
        assertEquals(2, labels, "<label> and <p> both map to Label");
        assertEquals(1, images, "<img> maps to ImageView even without a provider");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("ImageProvider")),
                "missing image provider should produce a warning");
    }

    @Test
    void unknownTagBecomesGenericPanelWithWarning() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("<body><marquee>x</marquee></body>");
        Panel panel = (Panel) ((Panel) root).getChildren().get(0);
        assertEquals("marquee", panel.getStyleTag(),
                "unknown tags keep their name so type selectors still match");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("marquee")));
    }

    @Test
    void inlineStyleAndNumericAttributesAreApplied() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <button id="b" x="10" y="20" width="150" height="40"
                          style="padding: 7px; color: #FF0000;">Hi</button>
                </body>
                """);
        Button b = (Button) UiLoader.findById(root, "b");
        assertEquals(10f, b.getX(), 1e-6);
        assertEquals(20f, b.getY(), 1e-6);
        assertEquals(150f, b.getWidth(), 1e-6);
        assertEquals(40f, b.getHeight(), 1e-6);
        assertEquals(7f, b.getInlineStyle().getFloat(StyleProperty.PADDING, 0f), 1e-6);
        assertEquals(0xFFFF0000, b.getInlineStyle().getInt(StyleProperty.COLOR, 0));
    }

    @Test
    void layoutAttributeInstallsLayoutManagers() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <div id="f" layout="flex"></div>
                  <div id="w" layout="flow"></div>
                  <div id="z" layout="grid"></div>
                </body>
                """);
        assertInstanceOf(FlexLayout.class,
                ((Panel) UiLoader.findById(root, "f")).getLayoutManager());
        assertInstanceOf(FlowLayout.class,
                ((Panel) UiLoader.findById(root, "w")).getLayoutManager());
        assertNull(((Panel) UiLoader.findById(root, "z")).getLayoutManager());
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("grid")),
                "unknown layout value warns");
    }

    @Test
    void onclickIsBoundToControllerByReflection() {
        DemoController controller = new DemoController();
        UiLoader loader = new UiLoader(controller);
        Component root = loader.loadFromString(
                "<body><button id=\"go\" onclick=\"increment()\">Go</button></body>");
        Button go = (Button) UiLoader.findById(root, "go");
        assertNotNull(go);
        go.performClick();
        assertEquals(1, controller.clicks, "onclick handler must run through the controller");
    }

    @Test
    void componentArgHandlerReceivesTheWidget() {
        ComponentArgController controller = new ComponentArgController();
        UiLoader loader = new UiLoader(controller);
        Component root = loader.loadFromString(
                "<body><button id=\"go\" onclick=\"onGo\">Go</button></body>");
        Button go = (Button) UiLoader.findById(root, "go");
        assertNotNull(go);
        go.performClick();
        assertSame(go, controller.received,
                "Component-arg handler must receive the widget matched by id");
    }

    @Test
    void onchangeIsBoundForTextFields() {
        DemoController controller = new DemoController();
        UiLoader loader = new UiLoader(controller);
        Component root = loader.loadFromString(
                "<body><input id=\"t\" onchange=\"onChanged\"/></body>");
        TextField t = (TextField) UiLoader.findById(root, "t");
        t.setText("typed");
        assertEquals(1, controller.changes, "onchange fires when the text changes");
    }

    @Test
    void missingHandlerMethodWarnsAndDoesNotBind() {
        UiLoader loader = new UiLoader(new DemoController());
        Component root = loader.loadFromString(
                "<body><button id=\"nope\" onclick=\"ghostMethod()\">x</button></body>");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("ghostMethod")));
        // No exception thrown; the app simply has no click behavior bound.
        Button b = (Button) UiLoader.findById(root, "nope");
        assertNotNull(b);
    }

    @Test
    void linkedStyleSheetIsResolvedRelativeToTheDocument(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("app.css"), "#hit { padding: 3px; }");
        Path ui = dir.resolve("ui.glyph");
        Files.writeString(ui, """
                <link rel="stylesheet" href="app.css"/>
                <body><button id="hit">x</button></body>
                """);
        UiLoader loader = new UiLoader();
        Component root = loader.load(ui);
        assertNotNull(root);
        assertNotNull(loader.getLastStyleSheet());
        assertEquals(1, loader.getLastStyleSheet().getRules().size());
    }

    @Test
    void inlineStyleElementIsUnsupportedAndWarns() {
        UiLoader loader = new UiLoader();
        loader.loadFromString("<style>#x { color: red; }</style><body></body>");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("inline <style>")),
                "inline <style> must warn because '{' is the binding delimiter");
    }

    @Test
    void bindingInterpolationsWarnAndAreNotResolved() {
        UiLoader loader = new UiLoader();
        loader.loadFromString("<body><label>Hi {name}</label></body>");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("Binding {name}")),
                "unsupported interpolations should be reported");
    }

    @Test
    void recoverableParseErrorsSurfaceAsWarnings() {
        UiLoader loader = new UiLoader();
        loader.loadFromString("<body><button>x</body>");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("parse error")),
                "recoverable syntax problems should be collected as warnings");
    }

    @Test
    void singleTopLevelElementBecomesTheRoot() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<div id=\"root\" class=\"page\"><label>Hi</label></div>");
        assertTrue(root instanceof Panel);
        assertTrue(((Panel) root).hasStyleClass("page"));
        assertSame(root, UiLoader.findById(root, "root"));
        assertEquals(1, ((Panel) root).getChildren().size());
    }

    @Test
    void htmlWithoutBodyUsesItselfAsTheContainerAndSkipsMetadata() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<html><head><title>x</title></head>"
                        + "<div id=\"app\"><label>Hi</label></div></html>");
        assertNotNull(UiLoader.findById(root, "app"));
        assertEquals(1, ((Panel) root).getChildren().size(),
                "head/title must not become widgets");
    }

    private static int countByType(Component c, Class<?> type) {
        int n = type.isInstance(c) ? 1 : 0;
        if (c instanceof Panel p) {
            for (Component child : p.getChildren()) {
                n += countByType(child, type);
            }
        }
        return n;
    }

    private static String buttonText(Button b) {
        try {
            java.lang.reflect.Method m = Button.class.getMethod("getText");
            return (String) m.invoke(b);
        } catch (Exception e) {
            return null;
        }
    }
}
