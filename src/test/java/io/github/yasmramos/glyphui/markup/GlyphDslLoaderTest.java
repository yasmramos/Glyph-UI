package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.PasswordField;
import io.github.yasmramos.glyphui.ui.TextArea;
import io.github.yasmramos.glyphui.ui.TextField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end tests for the Compose-style {@code .glyph} DSL: the same
 * {@link UiLoader} must accept the call-and-block syntax, route it through
 * {@link GlyphDslParser} and build the same widget tree it would from the XML
 * front-end.
 */
class GlyphDslLoaderTest {

    /** Controller used to verify {@code onClick = method} binding. */
    public static class DemoController {
        int clicks;

        public void sayHello() {
            clicks++;
        }
    }

    @Test
    void dslBuildsTheExpectedWidgetTree() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                Column(id = "root", gap = 8, padding = 24) {
                    Text("Glyph-UI", id = "title", class = "title")
                    Row(id = "card", class = "card", gap = 8) {
                        TextField(id = "name", placeholder = "Your name")
                        Button("Greet", id = "greet", class = "primary")
                    }
                }
                """);

        assertTrue(loader.getWarnings().isEmpty(),
                () -> "unexpected warnings: " + loader.getWarnings());

        Panel rootPanel = (Panel) UiLoader.findById(root, "root");
        assertNotNull(rootPanel, "Column must map to a Panel with id=root");
        FlexLayout rootFlex = (FlexLayout) rootPanel.getLayoutManager();
        assertNotNull(rootFlex);
        assertEquals(FlexLayout.Direction.COLUMN, rootFlex.getDirection());

        Label title = (Label) UiLoader.findById(root, "title");
        assertEquals("Glyph-UI", title.getText());

        Panel card = (Panel) UiLoader.findById(root, "card");
        FlexLayout cardFlex = (FlexLayout) card.getLayoutManager();
        assertEquals(FlexLayout.Direction.ROW, cardFlex.getDirection());

        TextField field = (TextField) UiLoader.findById(root, "name");
        assertEquals("Your name", field.getPlaceholder());

        Button greet = (Button) UiLoader.findById(root, "greet");
        assertEquals("Greet", greet.getText());
        assertTrue(greet.hasStyleClass("primary"));
    }

    @Test
    void gapAndPaddingReachTheLayoutManager() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                Column(id = "col", gap = 12, padding = 20) {
                    Text("a")
                    Text("b")
                }
                """);
        Panel col = (Panel) UiLoader.findById(root, "col");
        col.setWidth(200.0f);
        col.setHeight(200.0f);
        // gap/padding overrides are private, so assert through measure():
        // padding 20 (top+bottom) + two labels + one 12 gap.
        Dimension measured = col.measure(Float.MAX_VALUE, Float.MAX_VALUE);
        assertTrue(measured.getHeight() >= 52.0f,
                () -> "expected padding + gap to inflate the measured height, got "
                        + measured.getHeight());
    }

    @Test
    void positionalTextBecomesTheWidgetLabel() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                Column {
                    Button("Save")
                    Text("Caption")
                }
                """);
        Panel wrapper = (Panel) root;
        Panel panel = (Panel) wrapper.getChildren().get(0);
        assertEquals("Save", ((Button) panel.getChildren().get(0)).getText());
        assertEquals("Caption", ((Label) panel.getChildren().get(1)).getText());
    }

    @Test
    void onClickBindsToTheControllerMethod() {
        DemoController controller = new DemoController();
        UiLoader loader = new UiLoader(controller);
        Component root = loader.loadFromString("""
                Column {
                    Button("Go", id = "go", onClick = sayHello)
                }
                """);
        Button go = (Button) UiLoader.findById(root, "go");
        assertNotNull(go);
        assertNotNull(go.getOnClick(), "onClick must have resolved to a handler");
        go.performClick();
        assertEquals(1, controller.clicks);
    }

    @Test
    void stylesheetHeaderIsTreatedAsMetadata() {
        UiLoader loader = new UiLoader();
        loader.loadFromString("""
                Stylesheet("app.css")
                Column { Text("x") }
                """);
        // No sheet is on the classpath in this unit test, so a "not found"
        // warning is expected; what must NOT happen is a parse error.
        assertTrue(loader.getWarnings().stream()
                        .noneMatch(w -> w.contains("parse error")),
                () -> "unexpected warnings: " + loader.getWarnings());
    }

    @Test
    void xmlMarkupStillRoutesThroughTheXmlParser() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><label id=\"x\">hi</label></body>");
        Label label = (Label) UiLoader.findById(root, "x");
        assertEquals("hi", label.getText());
    }

    @Test
    void camelCaseArgumentsReachTheLoader() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                Column {
                    Text("t", fontSize = 20)
                }
                """);
        Label label = (Label) ((Panel) ((Panel) root).getChildren().get(0))
                .getChildren().get(0);
        assertEquals(20.0f, label.getFontSize());
    }

    @Test
    void textFieldTagMapsToTextField() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                Column {
                    TextField(id = "t")
                }
                """);
        assertInstanceOf(TextField.class, UiLoader.findById(root, "t"));
    }

    @Test
    void dslBuildsMaskedAndMultiLineInputs() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                Column(id = "root", gap = 8) {
                    PasswordField(id = "password", placeholder = "Password")
                    TextArea(id = "notes", placeholder = "Notes…")
                }
                """);

        assertTrue(loader.getWarnings().isEmpty(),
                () -> "unexpected warnings: " + loader.getWarnings());

        PasswordField password = (PasswordField) UiLoader.findById(root, "password");
        assertNotNull(password, "PasswordField must build from the DSL");
        assertEquals("Password", password.getPlaceholder());

        TextArea notes = (TextArea) UiLoader.findById(root, "notes");
        assertNotNull(notes, "TextArea must build from the DSL");
        assertEquals("Notes…", notes.getPlaceholder());
    }
}
