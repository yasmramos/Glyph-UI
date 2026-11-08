package io.github.yasmramos.glyphui.markup;

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
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Pagination;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.ProgressBar;
import io.github.yasmramos.glyphui.ui.RadioButton;
import io.github.yasmramos.glyphui.ui.RadioGroup;
import io.github.yasmramos.glyphui.ui.ScrollPane;
import io.github.yasmramos.glyphui.ui.Separator;
import io.github.yasmramos.glyphui.ui.Slider;
import io.github.yasmramos.glyphui.ui.Spinner;
import io.github.yasmramos.glyphui.ui.Table;
import io.github.yasmramos.glyphui.ui.Tabs;
import io.github.yasmramos.glyphui.ui.TextField;
import io.github.yasmramos.glyphui.ui.ToggleSwitch;
import io.github.yasmramos.glyphui.ui.TreeView;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the explicit tag-to-widget registry, the single source of truth for
 * the loader's markup vocabulary.
 */
class WidgetRegistryTest {

    /** Minimal context so factories can run without a full {@link UiLoader}. */
    private static final class TestContext implements WidgetContext {
        private final List<String> warnings = new ArrayList<>();

        @Override
        public String attr(Element element, String name) {
            return "";
        }

        @Override
        public boolean hasAttr(Element element, String name) {
            return false;
        }

        @Override
        public String textOf(Element element) {
            return "";
        }

        @Override
        public float parseFloatAttr(Element element, String name, float defaultValue) {
            return defaultValue;
        }

        @Override
        public void warn(String message) {
            warnings.add(message);
        }

        @Override
        public UiLoader.ImageProvider imageProvider() {
            return null;
        }

        @Override
        public RadioGroup radioGroup(String name) {
            return new RadioGroup();
        }
    }

    private final WidgetContext context = new TestContext();

    @Test
    void defaultsRegisterTheBaseVocabulary() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        for (String tag : new String[]{"div", "body", "section", "span", "panel",
                "root", "ui", "box", "stack", "column", "row", "text", "textfield",
                "button", "label", "p", "h1", "h2",
                "h3", "h4", "h5", "h6", "input", "textarea", "img", "checkbox",
                "radio", "switch", "slider", "spinner", "number", "progress",
                "select", "scroll", "scrollpane", "separator", "hr", "badge",
                "card", "tabs", "tabpanel", "alert", "avatar", "accordion",
                "chip", "breadcrumb", "pagination", "table", "treeview",
                "dialog", "colorpicker",
                // Compose-style spellings that share a factory with the above.
                "image", "imageview", "radiobutton", "toggleswitch", "dropdown",
                "progressbar", "hyperlink"}) {
            assertTrue(registry.knows(tag), "missing default tag " + tag);
        }
    }

    @Test
    void composeStyleAliasesCreateTheSameWidgets() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        assertInstanceOf(ImageView.class, registry.create("Image", null, context));
        assertInstanceOf(ImageView.class, registry.create("ImageView", null, context));
        assertInstanceOf(RadioButton.class, registry.create("RadioButton", null, context));
        assertInstanceOf(ToggleSwitch.class, registry.create("ToggleSwitch", null, context));
        assertInstanceOf(Dropdown.class, registry.create("Dropdown", null, context));
        assertInstanceOf(ProgressBar.class, registry.create("ProgressBar", null, context));
        assertInstanceOf(io.github.yasmramos.glyphui.ui.Link.class,
                registry.create("Hyperlink", null, context));
    }

    @Test
    void defaultsCreateTheMappedWidgets() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        assertInstanceOf(Panel.class, registry.create("div", null, context));
        assertInstanceOf(Panel.class, registry.create("body", null, context));
        assertInstanceOf(Panel.class, registry.create("box", null, context));
        assertInstanceOf(Panel.class, registry.create("column", null, context));
        assertInstanceOf(Panel.class, registry.create("row", null, context));
        assertInstanceOf(Label.class, registry.create("text", null, context));
        assertInstanceOf(TextField.class, registry.create("textfield", null, context));
        assertInstanceOf(Button.class, registry.create("button", null, context));
        assertInstanceOf(Label.class, registry.create("label", null, context));
        assertInstanceOf(Label.class, registry.create("h1", null, context));
        assertInstanceOf(TextField.class, registry.create("input", null, context));
        assertInstanceOf(TextField.class, registry.create("textarea", null, context));
        assertInstanceOf(ImageView.class, registry.create("img", null, context));
        assertInstanceOf(CheckBox.class, registry.create("checkbox", null, context));
        assertInstanceOf(RadioButton.class, registry.create("radio", null, context));
        assertInstanceOf(ToggleSwitch.class, registry.create("switch", null, context));
        assertInstanceOf(Slider.class, registry.create("slider", null, context));
        assertInstanceOf(ProgressBar.class, registry.create("progress", null, context));
        assertInstanceOf(Dropdown.class, registry.create("select", null, context));
        assertInstanceOf(ScrollPane.class, registry.create("scroll", null, context));
        assertInstanceOf(Separator.class, registry.create("separator", null, context));
        assertInstanceOf(Separator.class, registry.create("hr", null, context));
        assertInstanceOf(Spinner.class, registry.create("spinner", null, context));
        assertInstanceOf(Spinner.class, registry.create("number", null, context));
        assertInstanceOf(Badge.class, registry.create("badge", null, context));
        assertInstanceOf(Card.class, registry.create("card", null, context));
        assertInstanceOf(Tabs.class, registry.create("tabs", null, context));
        assertInstanceOf(Tabs.class, registry.create("tabpanel", null, context));
        assertInstanceOf(Alert.class, registry.create("alert", null, context));
        assertInstanceOf(Avatar.class, registry.create("avatar", null, context));
        assertInstanceOf(Accordion.class, registry.create("accordion", null, context));
        assertInstanceOf(Chip.class, registry.create("chip", null, context));
        assertInstanceOf(Breadcrumb.class, registry.create("breadcrumb", null, context));
        assertInstanceOf(Pagination.class, registry.create("pagination", null, context));
        assertInstanceOf(Table.class, registry.create("table", null, context));
        assertInstanceOf(TreeView.class, registry.create("treeview", null, context));
        assertInstanceOf(Dialog.class, registry.create("dialog", null, context));
        assertInstanceOf(ColorPicker.class, registry.create("colorpicker", null, context));
    }

    @Test
    void tagsAreMatchedCaseInsensitively() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        assertTrue(registry.knows("BUTTON"));
        assertTrue(registry.knows("Button"));
        assertInstanceOf(Button.class, registry.create("Button", null, context));
    }

    @Test
    void boxUsesAColumnFlexLayoutAndStackUsesAbsolutePositions() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        Panel box = (Panel) registry.create("box", null, context);
        Panel stack = (Panel) registry.create("stack", null, context);

        assertInstanceOf(FlexLayout.class, box.getLayoutManager());
        assertEquals(FlexLayout.Direction.COLUMN,
                ((FlexLayout) box.getLayoutManager()).getDirection());
        assertEquals(null, stack.getLayoutManager());
    }

    @Test
    void customFactoriesCanRegisterAndReplaceTags() {
        WidgetRegistry registry = new WidgetRegistry();
        WidgetFactory factory = (element, ctx) -> new Label("custom");

        registry.register("custom", factory);

        assertTrue(registry.knows("custom"));
        Component created = registry.create("custom", null, context);
        assertInstanceOf(Label.class, created);
        assertEquals("custom", ((Label) created).getText());

        registry.register("custom", (element, ctx) -> new Button("replacement"));
        assertInstanceOf(Button.class, registry.create("custom", null, context));
    }

    @Test
    void unknownTagsReportTheTagName() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.get("nope"));

        assertTrue(error.getMessage().contains("nope"), error.getMessage());
        assertNotNull(registry.tags());
    }

    @Test
    void invalidRegistrationsAreRejected() {
        WidgetRegistry registry = new WidgetRegistry();

        assertThrows(IllegalArgumentException.class,
                () -> registry.register(null, (e, c) -> new Panel()));
        assertThrows(IllegalArgumentException.class,
                () -> registry.register("  ", (e, c) -> new Panel()));
        assertThrows(IllegalArgumentException.class, () -> registry.register("tag", null));
        assertFalse(registry.knows(null));
        assertFalse(registry.knows("tag"));
    }
}
