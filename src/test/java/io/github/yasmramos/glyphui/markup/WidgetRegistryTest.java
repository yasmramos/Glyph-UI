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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the explicit tag-to-widget registry and its default vocabulary.
 */
class WidgetRegistryTest {

    @Test
    void defaultsRegisterTheBaseVocabulary() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        for (String tag : new String[]{"Box", "Stack", "Label", "Button", "Field",
                "Checkbox", "Image", "Separator", "Scroll"}) {
            assertTrue(registry.knows(tag), "missing default tag " + tag);
        }
        assertEquals(9, registry.tags().size());
    }

    @Test
    void defaultsCreateTheMappedWidgets() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        assertInstanceOf(Panel.class, registry.create("Box", null));
        assertInstanceOf(Panel.class, registry.create("Stack", null));
        assertInstanceOf(Label.class, registry.create("Label", null));
        assertInstanceOf(Button.class, registry.create("Button", null));
        assertInstanceOf(TextField.class, registry.create("Field", null));
        assertInstanceOf(CheckBox.class, registry.create("Checkbox", null));
        assertInstanceOf(ImageView.class, registry.create("Image", null));
        assertInstanceOf(Separator.class, registry.create("Separator", null));
        assertInstanceOf(ScrollPane.class, registry.create("Scroll", null));
    }

    @Test
    void boxUsesAColumnFlexLayoutAndStackUsesAbsolutePositions() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        Panel box = (Panel) registry.create("Box", null);
        Panel stack = (Panel) registry.create("Stack", null);

        assertInstanceOf(FlexLayout.class, box.getLayoutManager());
        assertEquals(FlexLayout.Direction.COLUMN,
                ((FlexLayout) box.getLayoutManager()).getDirection());
        assertEquals(null, stack.getLayoutManager());
    }

    @Test
    void customFactoriesCanRegisterAndReplaceTags() {
        WidgetRegistry registry = new WidgetRegistry();
        WidgetFactory factory = element -> new Label("custom");

        registry.register("Custom", factory);

        assertTrue(registry.knows("Custom"));
        assertFalse(registry.knows("custom"));
        Component created = registry.create("Custom", null);
        assertInstanceOf(Label.class, created);
        assertEquals("custom", ((Label) created).getText());

        registry.register("Custom", element -> new Button("replacement"));
        assertInstanceOf(Button.class, registry.create("Custom", null));
    }

    @Test
    void unknownTagsReportTheTagName() {
        WidgetRegistry registry = WidgetRegistry.defaults();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> registry.get("Nope"));

        assertTrue(error.getMessage().contains("Nope"), error.getMessage());
        assertNotNull(registry.tags());
    }

    @Test
    void invalidRegistrationsAreRejected() {
        WidgetRegistry registry = new WidgetRegistry();

        assertThrows(IllegalArgumentException.class, () -> registry.register(null, e -> new Panel()));
        assertThrows(IllegalArgumentException.class, () -> registry.register("  ", e -> new Panel()));
        assertThrows(IllegalArgumentException.class, () -> registry.register("Tag", null));
        assertFalse(registry.knows(null));
        assertFalse(registry.knows("Tag"));
    }
}
