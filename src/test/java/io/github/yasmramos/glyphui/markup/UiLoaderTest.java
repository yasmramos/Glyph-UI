package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.markup.UiLoader;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.layout.FlowLayout;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.CheckBox;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Dropdown;
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.ProgressBar;
import io.github.yasmramos.glyphui.ui.RadioButton;
import io.github.yasmramos.glyphui.ui.RadioGroup;
import io.github.yasmramos.glyphui.ui.Slider;
import io.github.yasmramos.glyphui.ui.TextField;
import io.github.yasmramos.glyphui.ui.ToggleSwitch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

    @Test
    void newWidgetTagsMapToTheirWidgets() {
        String glyph = """
                <body>
                  <div id="form">
                    <progress id="p" value="0.25" show-text/>
                    <slider id="s" min="0" max="100" step="5" value="40"/>
                    <switch id="w" checked>Wi-Fi</switch>
                    <radio id="r" selected>Choice</radio>
                    <checkbox id="c" checked>Agree</checkbox>
                  </div>
                </body>
                """;
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(glyph);

        ProgressBar bar = (ProgressBar) UiLoader.findById(root, "p");
        assertNotNull(bar);
        assertEquals(0.25f, bar.getValue(), 1e-6f);
        assertTrue(bar.isShowText());

        Slider slider = (Slider) UiLoader.findById(root, "s");
        assertNotNull(slider);
        assertEquals(0.0f, slider.getMin(), 1e-6f);
        assertEquals(100.0f, slider.getMax(), 1e-6f);
        assertEquals(5.0f, slider.getStep(), 1e-6f);
        assertEquals(40.0f, slider.getValue(), 1e-6f);

        ToggleSwitch sw = (ToggleSwitch) UiLoader.findById(root, "w");
        assertNotNull(sw);
        assertTrue(sw.isOn());
        assertEquals("Wi-Fi", sw.getText());

        RadioButton radio = (RadioButton) UiLoader.findById(root, "r");
        assertNotNull(radio);
        assertTrue(radio.isSelected());
        assertEquals("Choice", radio.getText());

        CheckBox check = (CheckBox) UiLoader.findById(root, "c");
        assertNotNull(check);
        assertTrue(check.isChecked());
    }

    @Test
    void selectTagsMapToADropdownWithOptions() {
        String glyph = """
                <body>
                  <div id="form">
                    <select id="sel" placeholder="Choose...">
                      <option>Red</option>
                      <option value="G">Green</option>
                      <option selected>Blue</option>
                    </select>
                  </div>
                </body>
                """;
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(glyph);

        Dropdown dd = (Dropdown) UiLoader.findById(root, "sel");
        assertNotNull(dd);
        assertEquals("Choose...", dd.getPlaceholder());
        assertEquals(List.of("Red", "Green", "Blue"), dd.getItems());
        // The option carrying `selected` wins over the implicit first option.
        assertEquals(2, dd.getSelectedIndex());
        assertEquals("Blue", dd.getSelectedItem());
    }

    @Test
    void selectWithoutOptionsOpensNothing() {
        String glyph = """
                <body><select id="sel"/></body>
                """;
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(glyph);

        Dropdown dd = (Dropdown) UiLoader.findById(root, "sel");
        assertNotNull(dd);
        assertEquals(0, dd.getItemCount());
        assertEquals(-1, dd.getSelectedIndex());
    }

    @Test
    void radiosSharingANameJoinOneGroup() {
        String glyph = """
                <body>
                  <div id="form">
                    <radio id="a" name="size">Small</radio>
                    <radio id="b" name="size">Medium</radio>
                    <radio id="c" name="size">Large</radio>
                    <radio id="loose">Loose</radio>
                  </div>
                </body>
                """;
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(glyph);

        RadioButton a = (RadioButton) UiLoader.findById(root, "a");
        RadioButton b = (RadioButton) UiLoader.findById(root, "b");
        RadioButton c = (RadioButton) UiLoader.findById(root, "c");
        RadioButton loose = (RadioButton) UiLoader.findById(root, "loose");

        RadioGroup size = loader.getRadioGroup("size");
        assertNotNull(size);
        assertEquals(3, size.size());
        assertSame(size, a.getGroup());
        assertSame(size, b.getGroup());
        assertSame(size, c.getGroup());
        // A radio with no `name` stays independent.
        assertNull(loose.getGroup());
    }

    @Test
    void radiosInAGroupAreMutuallyExclusive() {
        String glyph = """
                <body>
                  <radio id="a" name="size">Small</radio>
                  <radio id="b" name="size" selected>Medium</radio>
                  <radio id="c" name="size">Large</radio>
                </body>
                """;
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(glyph);

        RadioButton a = (RadioButton) UiLoader.findById(root, "a");
        RadioButton b = (RadioButton) UiLoader.findById(root, "b");
        RadioButton c = (RadioButton) UiLoader.findById(root, "c");

        // The `selected` attribute picked b; selecting c must clear it.
        assertFalse(a.isSelected());
        assertTrue(b.isSelected());

        c.setSelected(true);
        assertFalse(b.isSelected());
        assertTrue(c.isSelected());

        RadioGroup size = loader.getRadioGroup("size");
        assertSame(c, size.getSelected());
    }

    @Test
    void twoNamesCreateTwoIndependentGroups() {
        String glyph = """
                <body>
                  <radio id="s" name="size">Small</radio>
                  <radio id="l" name="size">Large</radio>
                  <radio id="r" name="colour">Red</radio>
                  <radio id="g" name="colour">Green</radio>
                </body>
                """;
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(glyph);

        assertEquals(2, loader.getRadioGroups().size());

        RadioButton small = (RadioButton) UiLoader.findById(root, "s");
        RadioButton red = (RadioButton) UiLoader.findById(root, "r");

        small.setSelected(true);
        red.setSelected(true);
        // Selecting in one group never disturbs the other.
        assertTrue(small.isSelected());
        assertTrue(red.isSelected());
    }

    @Test
    void radioGroupsAreResetBetweenLoads() {
        UiLoader loader = new UiLoader();
        loader.loadFromString("<body><radio name=\"size\">A</radio></body>");
        assertNotNull(loader.getRadioGroup("size"));

        loader.loadFromString("<body><radio name=\"colour\">R</radio></body>");
        assertNull(loader.getRadioGroup("size"));
        assertNotNull(loader.getRadioGroup("colour"));
    }

    @Test
    void indeterminateProgressBarAttribute() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><progress id=\"p\" indeterminate/></body>");
        ProgressBar bar = (ProgressBar) UiLoader.findById(root, "p");
        assertNotNull(bar);
        assertTrue(bar.isIndeterminate());
    }

    @Test
    void badSliderNumbersWarnAndKeepDefaults() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><slider id=\"s\" min=\"oops\" value=\"nope\"/></body>");
        Slider slider = (Slider) UiLoader.findById(root, "s");
        assertNotNull(slider);
        assertEquals(0.0f, slider.getMin(), 1e-6f, "unparsable min falls back to 0");
        assertEquals(0.0f, slider.getValue(), 1e-6f, "unparsable value is ignored");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("min")),
                "a bad min should warn");
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("value")),
                "a bad value should warn");
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
