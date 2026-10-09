package com.glyphui.markup;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the recursive-descent {@link Parser}: AST shape, positions,
 * self-closing tags, mixed text/binding children, comments and the
 * accumulated {@link ParseError} reporting strategy.
 */
class ParserTest {

    /** Parses and asserts success, returning the document. */
    private static Document parseOk(String src) {
        ParseResult r = new Parser().parse(src);
        assertTrue(r.isSuccess(), () -> "unexpected errors: " + r.errors());
        assertNotNull(r.document(), "document must never be null");
        return r.document();
    }

    /** Parses and asserts failure, returning the error list. */
    private static List<ParseError> parseFail(String src) {
        ParseResult r = new Parser().parse(src);
        assertFalse(r.isSuccess(), "expected at least one error");
        return r.errors();
    }

    @Nested
    @DisplayName("valid documents")
    class ValidDocuments {

        @Test
        @DisplayName("nested elements build the expected tree")
        void nesting() {
            Document doc = parseOk("<Box><Row><Label>Hi</Label></Row></Box>");
            Element box = doc.getRootElement();
            assertEquals("Box", box.getTagName());
            Element row = (Element) box.getChildren().get(0);
            assertEquals("Row", row.getTagName());
            Element label = (Element) row.getChildren().get(0);
            assertEquals("Label", label.getTagName());
            AstNode text = label.getChildren().get(0);
            assertEquals("Hi", ((TextNode) text).getText());
        }

        @Test
        @DisplayName("mixed content yields Text/Binding/Text children in order")
        void mixedContent() {
            Document doc = parseOk("<Label>Hola {user.name}!</Label>");
            Element label = doc.getRootElement();
            List<AstNode> kids = label.getChildren();
            assertEquals(3, kids.size());

            assertEquals("Hola ", ((TextNode) kids.get(0)).getText());
            assertEquals(1, kids.get(0).getLine());
            // "<Label>" is 7 chars, so the text run starts at column 8.
            assertEquals(8, kids.get(0).getColumn());

            BindingNode b = (BindingNode) kids.get(1);
            assertEquals(List.of("user", "name"), b.getSegments());
            assertEquals("user.name", b.getRawPath());
            assertEquals(1, b.getLine());
            assertEquals(13, b.getColumn(),
                    "'{' sits right after the 12-char prefix '<Label>Hola '");

            assertEquals("!", ((TextNode) kids.get(2)).getText());
            // '{user.name}' spans columns 13..23 ('}' at 23), so '!' starts at 24.
            assertEquals(24, kids.get(2).getColumn(),
                    "'!' follows the closing brace at column 23");
        }

        @Test
        @DisplayName("attributes keep raw names/values and quoted flag")
        void attributes() {
            Document doc = parseOk(
                    "<Button bind:text=\"model.label\" on:click=\"save\" layout=flex />");
            Element btn = doc.getRootElement();
            assertTrue(btn.isSelfClosing());
            List<Attribute> attrs = btn.getAttributes();
            assertEquals(3, attrs.size());

            Attribute bind = attrs.get(0);
            assertEquals("bind:text", bind.name());
            assertEquals("model.label", bind.value());
            assertTrue(bind.quoted());
            assertTrue(bind.hasValue());

            Attribute on = attrs.get(1);
            assertEquals("on:click", on.name());
            assertEquals("save", on.value());

            Attribute bare = attrs.get(2);
            assertEquals("layout", bare.name());
            assertEquals("flex", bare.value());
            assertFalse(bare.quoted());

            // findAttribute works with the raw name; prefixes are NOT
            // interpreted by the parser.
            assertEquals(on, btn.findAttribute("on:click"));
            assertNull(btn.findAttribute("onclick"));
        }

        @Test
        @DisplayName("self-closing tags produce empty child lists")
        void selfClosing() {
            Document doc = parseOk("<Panel><Button/></Panel>");
            Element panel = doc.getRootElement();
            Element btn = (Element) panel.getChildren().get(0);
            assertEquals("Button", btn.getTagName());
            assertTrue(btn.isSelfClosing());
            assertTrue(btn.getChildren().isEmpty());
            // The element closes where "/>" is found (line 1, col 15).
            assertEquals(1, btn.getCloseLine());
            assertEquals(15, btn.getCloseColumn());
        }

        @Test
        @DisplayName("comments are skipped without touching siblings")
        void comments() {
            Document doc = parseOk(
                    "<Box><!-- a note --><Label>x</Label></Box>");
            Element box = doc.getRootElement();
            assertEquals(1, box.getChildren().size());
            assertEquals("Label", ((Element) box.getChildren().get(0)).getTagName());
        }

        @Test
        @DisplayName("{{ and }} escapes fold into literal text")
        void braceEscapes() {
            Document doc = parseOk("<Label>{{x}}</Label>");
            Element label = doc.getRootElement();
            List<AstNode> kids = label.getChildren();
            assertEquals(1, kids.size(), "escapes fold into one text run");
            TextNode t = (TextNode) kids.get(0);
            assertEquals("{x}", t.getText());
            assertEquals(8, t.getColumn(), "run starts at the '{{' source position");
        }

        @Test
        @DisplayName("elements and attributes record 1-based positions")
        void positions() {
            Document doc = parseOk("<Box>\n  <Label id=\"a\">t</Label>\n</Box>");
            Element box = doc.getRootElement();
            assertEquals(1, box.getLine());
            assertEquals(1, box.getColumn());
            Element label = (Element) box.getChildren().get(0);
            assertEquals(2, label.getLine());
            assertEquals(3, label.getColumn());
            Attribute id = label.getAttributes().get(0);
            assertEquals(2, id.line());
            assertEquals(10, id.column(), "\"id\" starts after '  <Label '");
        }
    }

    @Nested
    @DisplayName("error recovery")
    class ErrorRecovery {

        @Test
        @DisplayName("unclosed tag reports line/column of its opening tag")
        void unclosedTag() {
            List<ParseError> errs = parseFail("<Box><Label>hi</Box>");
            assertEquals(1, errs.size(), () -> "got: " + errs);
            // First error: </Box> does not match the open <Label>; it is
            // reported at the position of the offending end tag ("</" at col 15).
            ParseError e = errs.get(0);
            assertEquals(1, e.line());
            assertEquals(15, e.column(), "'</' of </Box> is at column 15");
            assertTrue(e.message().contains("expected </Label>"), e.message());
            assertTrue(e.message().contains("found </Box>"), e.message());
        }

        @Test
        @DisplayName("end tag without matching start is reported")
        void strayEndTag() {
            List<ParseError> errs = parseFail("<Box></Section></Box>");
            assertEquals(1, errs.size(), () -> "got: " + errs);
            ParseError e = errs.get(0);
            assertEquals(1, e.line());
            assertEquals(6, e.column(), "'</' starts at column 6");
            assertTrue(e.message().contains("no open element"), e.message());
            // Recovery keeps the rest of the tree: Box still closes properly.
            ParseResult r = new Parser().parse("<Box></Section></Box>");
            assertEquals("Box", r.document().getRootElement().getTagName());
        }

        @Test
        @DisplayName("attribute with '=' but no value is reported at the '='")
        void attributeWithoutValue() {
            List<ParseError> errs = parseFail("<Button disabled=>ok</Button>");
            assertEquals(1, errs.size(), () -> "got: " + errs);
            ParseError e = errs.get(0);
            assertEquals(1, e.line());
            // "<Button disabled" is 16 chars, so the '=' sits at column 17;
            // that is exactly where the missing value is anchored.
            assertEquals(17, e.column(),
                    "the '=' at column 17 is where a value was expected");
            assertTrue(e.message().contains("missing a value"), e.message());
        }

        @Test
        @DisplayName("bare flag attributes like else are valid, no value needed")
        void bareFlagAttributeIsValid() {
            Document doc = parseOk("<Label else>Ready.</Label>");
            assertFalse(doc.getRootElement().findAttribute("else").hasValue());
        }

        @Test
        @DisplayName("duplicate attribute in the same element is reported")
        void duplicateAttribute() {
            List<ParseError> errs = parseFail("<Label id=\"a\" id=\"b\">x</Label>");
            assertEquals(1, errs.size());
            ParseError e = errs.get(0);
            assertEquals(1, e.line());
            assertEquals(15, e.column(), "second 'id' starts at column 15");
            assertTrue(e.message().contains("duplicate attribute"), e.message());
            // Recovery: the first value wins and the tree still parses.
            Element label = new Parser().parse("<Label id=\"a\" id=\"b\">x</Label>")
                    .document().getRootElement();
            assertEquals("a", label.findAttribute("id").value());
        }

        @Test
        @DisplayName("stray closing brace in text is reported")
        void unmatchedBrace() {
            List<ParseError> errs = parseFail("<Label>a}b</Label>");
            assertEquals(1, errs.size(), () -> "got: " + errs);
            assertEquals(1, errs.get(0).line());
            assertEquals(9, errs.get(0).column(), "'}' sits at column 9");
            assertTrue(errs.get(0).message().contains("unmatched '}'"),
                    errs.get(0).message());
        }

        @Test
        @DisplayName("invalid binding path characters are reported")
        void invalidBindingPath() {
            List<ParseError> errs = parseFail("<Label>{a && b}</Label>");
            assertFalse(errs.isEmpty());
            ParseError e = errs.get(0);
            assertEquals(1, e.line());
            assertEquals(11, e.column(), "'&' sits at column 11");
            assertTrue(e.message().contains("not allowed in binding path"),
                    e.message());
        }

        @Test
        @DisplayName("EOF inside a tag surfaces as MarkupException from tokenizer")
        void eofInsideTag() {
            MarkupException ex = assertThrows(MarkupException.class,
                    () -> new Parser().parse("<Button id=\"x\""));
            assertEquals(1, ex.getLine());
            assertEquals(1, ex.getColumn(), "reported at the '<' of the tag");
        }

        @Test
        @DisplayName("errors accumulate: multiple problems all reported")
        void multipleErrors() {
            List<ParseError> errs = parseFail(
                    "<Box><Label/><Bad></Other></Box>");
            assertTrue(errs.size() >= 2, () -> "got: " + errs);
        }
    }

    @Nested
    @DisplayName("design example: Box / if / elif / else / for-each")
    class DesignExample {

        private static final String SOURCE = """
                <Box layout="flex">
                  <!-- status area -->
                  <Label if="state.loading">Loading…</Label>
                  <Label elif="state.error">{status.message}</Label>
                  <Label else>Ready.</Label>
                  <List for-each="items" bind:selected="app.selection"
                        on:select="pickItem">
                    <Item key="item.id">{item.name}</Item>
                  </List>
                  <Button/>
                </Box>
                """;

        @Test
        @DisplayName("the minimal design sample parses cleanly to the expected AST")
        void parsesToExpectedAst() {
            Document doc = parseOk(SOURCE);
            Element box = doc.getRootElement();
            assertEquals("Box", box.getTagName());
            assertEquals("flex", box.findAttribute("layout").value());

            // Five element children: Label(if), Label(elif), Label(else),
            // List and the trailing self-closing Button.
            List<AstNode> kids = box.getChildren();
            assertEquals(5, kids.size(), () -> "kids: " + kids);

            Element ifLabel = (Element) kids.get(0);
            assertEquals("if", ifLabel.findAttribute("if").name());
            assertEquals("state.loading", ifLabel.findAttribute("if").value());
            assertEquals("Loading…", ((TextNode) ifLabel.getChildren().get(0)).getText());

            Element elifLabel = (Element) kids.get(1);
            assertEquals("state.error", elifLabel.findAttribute("elif").value());
            BindingNode msg = (BindingNode) elifLabel.getChildren().get(0);
            assertEquals(List.of("status", "message"), msg.getSegments());

            Element elseLabel = (Element) kids.get(2);
            assertFalse(elseLabel.findAttribute("else").hasValue(),
                    "bare 'else' directive carries no value");
            assertEquals("Ready.", ((TextNode) elseLabel.getChildren().get(0)).getText());

            Element list = (Element) kids.get(3);
            assertEquals("items", list.findAttribute("for-each").value());
            assertEquals("app.selection", list.findAttribute("bind:selected").value());
            assertEquals("pickItem", list.findAttribute("on:select").value());

            Element item = (Element) list.getChildren().get(0);
            assertEquals("Item", item.getTagName());
            assertEquals("item.id", item.findAttribute("key").value());
            BindingNode name = (BindingNode) item.getChildren().get(0);
            assertEquals(List.of("item", "name"), name.getSegments());

            Element button = (Element) kids.get(4);
            assertEquals("Button", button.getTagName());
            assertTrue(button.isSelfClosing());
        }

        @Test
        @DisplayName("bare directives and self-closing tags coexist")
        void extraChecks() {
            Document doc = parseOk("<Box><Label else/><Button/></Box>");
            Element box = doc.getRootElement();
            assertEquals(2, box.getChildren().size());
            Element label = (Element) box.getChildren().get(0);
            assertTrue(label.isSelfClosing());
            assertFalse(label.findAttribute("else").hasValue());
        }
    }
}
