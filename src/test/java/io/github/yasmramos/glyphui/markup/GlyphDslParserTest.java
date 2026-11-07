package io.github.yasmramos.glyphui.markup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Compose-style {@link GlyphDslParser}: statement/argument
 * grammar, name normalisation, positional values, bindings, stylesheet header,
 * comments, error recovery and the {@link GlyphDslParser#looksLikeDsl} router.
 */
class GlyphDslParserTest {

    private static Document parseOk(String src) {
        ParseResult r = GlyphDslParser.parse(src);
        assertTrue(r.isSuccess(), () -> "unexpected errors: " + r.errors());
        return r.document();
    }

    private static List<ParseError> parseFail(String src) {
        ParseResult r = GlyphDslParser.parse(src);
        assertFalse(r.isSuccess(), "expected at least one error");
        return r.errors();
    }

    private static Element firstElement(Document doc) {
        Element el = doc.getRootElement();
        assertNotNull(el, "expected a root element");
        return el;
    }

    private static String attr(Element element, String name) {
        Attribute a = element.findAttribute(name);
        return a == null ? null : a.value();
    }

    @Nested
    @DisplayName("document shape")
    class DocumentShape {

        @Test
        void singleStatementBecomesARootElement() {
            Document doc = parseOk("Text(\"Hi\")");
            Element root = firstElement(doc);
            assertEquals("Text", root.getTagName());
            assertEquals(1, root.getChildren().size());
            TextNode text = (TextNode) root.getChildren().get(0);
            assertEquals("Hi", text.getText());
        }

        @Test
        void nestedBlocksBecomeChildElements() {
            Document doc = parseOk("""
                    Column {
                        Text("a")
                        Row {
                            Button("b")
                        }
                    }
                    """);
            Element column = firstElement(doc);
            assertEquals(2, column.getChildren().size());
            Element row = (Element) column.getChildren().get(1);
            assertEquals("Row", row.getTagName());
            Element button = (Element) row.getChildren().get(0);
            assertEquals("Button", button.getTagName());
        }

        @Test
        void statementWithoutABlockIsSelfClosing() {
            Document doc = parseOk("TextField(id = \"name\")");
            Element field = firstElement(doc);
            assertTrue(field.isSelfClosing());
        }

        @Test
        void emptyInputYieldsAnEmptyDocument() {
            Document doc = parseOk("");
            assertNull(doc.getRootElement());
        }
    }

    @Nested
    @DisplayName("arguments")
    class Arguments {

        @Test
        void positionalStringBecomesTextContent() {
            Element el = firstElement(parseOk("Button(\"Greet\")"));
            assertEquals("Greet", ((TextNode) el.getChildren().get(0)).getText());
        }

        @Test
        void namedArgumentsBecomeAttributes() {
            Element el = firstElement(parseOk("Button(\"Go\", id = \"go\", class = \"primary\")"));
            assertEquals("go", attr(el, "id"));
            assertEquals("primary", attr(el, "class"));
        }

        @Test
        void bareNumericValueIsKeptVerbatim() {
            Element el = firstElement(parseOk("Slider(min = 0, max = 100, value = 40)"));
            assertEquals("0", attr(el, "min"));
            assertEquals("100", attr(el, "max"));
            assertEquals("40", attr(el, "value"));
        }

        @Test
        void booleanTrueBecomesAPresenceFlagAndFalseIsDropped() {
            Element el = firstElement(parseOk(
                    "Progress(value = 0.5, indeterminate = true, disabled = false)"));
            assertNotNull(el.findAttribute("indeterminate"));
            assertEquals(null, attr(el, "indeterminate"), "flag must be valueless");
            assertEquals(null, el.findAttribute("disabled"), "false must be dropped");
        }

        @Test
        void positionalBareWordBecomesText() {
            Element el = firstElement(parseOk("Chip(NEW)"));
            assertEquals("NEW", ((TextNode) el.getChildren().get(0)).getText());
        }

        @Test
        void camelCaseArgumentsAreNormalisedToTheCanonicalSpelling() {
            Element el = firstElement(parseOk(
                    "Label(fontSize = 20, textColor = \"#fff\")"));
            assertEquals("20", attr(el, "font-size"));
            assertEquals("#fff", attr(el, "text-color"));
        }

        @Test
        void eventHandlersCollapseToTheOnPrefixForm() {
            Element el = firstElement(parseOk("Button(\"Go\", onClick = sayHello)"));
            assertEquals("sayHello", attr(el, "onclick"));
        }
    }

    @Nested
    @DisplayName("bindings and stylesheet")
    class BindingsAndStylesheet {

        @Test
        void positionalBindingBecomesABindingNode() {
            Element el = firstElement(parseOk("Text({user.name})"));
            BindingNode binding = (BindingNode) el.getChildren().get(0);
            assertEquals(List.of("user", "name"), binding.getSegments());
        }

        @Test
        void stylesheetHeaderBecomesALinkElement() {
            Document doc = parseOk("Stylesheet(\"app.css\")\nColumn {}");
            List<AstNode> roots = doc.getChildren();
            Element link = (Element) roots.get(0);
            assertEquals("link", link.getTagName());
            assertEquals("stylesheet", attr(link, "rel"));
            assertEquals("app.css", attr(link, "href"));
        }

        @Test
        void bindingAsAttributeValueIsReportedNotSilentlyDropped() {
            List<ParseError> errors = parseFail("Text(color = {theme.fg})");
            assertTrue(errors.stream().anyMatch(e -> e.message().contains("binding")),
                    () -> "expected a binding diagnostic, got " + errors);
        }
    }

    @Nested
    @DisplayName("comments and trivia")
    class CommentsAndTrivia {

        @Test
        void lineAndBlockCommentsAreSkipped() {
            Document doc = parseOk("""
                    // a line comment
                    /* a block
                       comment */
                    Column {
                        Text("x") // trailing
                    }
                    """);
            assertEquals("Column", firstElement(doc).getTagName());
        }

        @Test
        void unterminatedBlockCommentIsReported() {
            assertFalse(GlyphDslParser.parse("/* never closed").isSuccess());
        }

        @Test
        void unterminatedStringAborts() {
            assertThrows(MarkupException.class,
                    () -> GlyphDslParser.parse("Text(\"oops)"));
        }
    }

    @Nested
    @DisplayName("errors")
    class Errors {

        @Test
        void missingParenAfterNameIsReported() {
            List<ParseError> errors = parseFail("Button \"Go\"");
            assertTrue(errors.stream().anyMatch(e -> e.message().contains("'('")),
                    () -> "got " + errors);
        }

        @Test
        void unclosedBlockIsReported() {
            List<ParseError> errors = parseFail("Column {\n  Text(\"x\")");
            assertTrue(errors.stream().anyMatch(e -> e.message().contains("unclosed block")),
                    () -> "got " + errors);
        }

        @Test
        void emptyBindingIsReported() {
            List<ParseError> errors = parseFail("Text({})");
            assertTrue(errors.stream().anyMatch(e -> e.message().contains("empty binding")),
                    () -> "got " + errors);
        }

        @Test
        void missingCommaBetweenArgumentsIsReported() {
            List<ParseError> errors = parseFail("Button(\"a\" \"b\")");
            assertFalse(errors.isEmpty());
        }
    }

    @Nested
    @DisplayName("format detection")
    class Detection {

        @Test
        void angleBracketSourceIsNotDsl() {
            assertFalse(GlyphDslParser.looksLikeDsl("<body><label>x</label></body>"));
            assertFalse(GlyphDslParser.looksLikeDsl("  \n\t<Button/>"));
        }

        @Test
        void callStyleSourceIsDsl() {
            assertTrue(GlyphDslParser.looksLikeDsl("Column { }"));
            assertTrue(GlyphDslParser.looksLikeDsl("\n\n// c\nText(\"x\")"));
        }

        @Test
        void emptyAndNullAreNotDsl() {
            assertFalse(GlyphDslParser.looksLikeDsl(""));
            assertFalse(GlyphDslParser.looksLikeDsl("   "));
            assertFalse(GlyphDslParser.looksLikeDsl(null));
        }
    }

    @Nested
    @DisplayName("positions")
    class Positions {

        @Test
        void elementsCarryTheirSourcePosition() {
            Document doc = parseOk("\n\n  Text(\"x\")");
            Element el = firstElement(doc);
            assertEquals(3, el.getLine());
            assertEquals(3, el.getColumn());
        }

        @Test
        void childrenCarryTheirOwnPositions() {
            Document doc = parseOk("Column {\n  Text(\"x\")\n}");
            Element column = firstElement(doc);
            Element child = (Element) column.getChildren().get(0);
            assertEquals(2, child.getLine());
            assertEquals(3, child.getColumn());
        }

        @Test
        void parentLinksAreEstablished() {
            Document doc = parseOk("Column { Text(\"x\") }");
            Element column = firstElement(doc);
            Element child = (Element) column.getChildren().get(0);
            assertEquals(column, child.getParent());
            assertInstanceOf(TextNode.class, child.getChildren().get(0));
        }
    }
}
