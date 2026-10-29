package io.github.yasmramos.glyphui.style;

import io.github.yasmramos.glyphui.style.StyleEngine;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.Selector;
import io.github.yasmramos.glyphui.style.StyleSheet;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Panel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for CSS parsing into {@link StyleSheet} using the ph-css backend.
 */
class StyleParseTest {

    @AfterEach
    void clearInlineStyleFonts() {
        // The cascade caches fonts built from CSS font-* declarations;
        // release them between tests to avoid leaking Skija handles.
        io.github.yasmramos.glyphui.core.internal.TestHooks.disposeStyleFonts();
    }

    @Test
    void parsesTypeClassAndIdRules() {
        String css = """
                button { color: #FF0000; padding: 8px; }
                .primary { background: rgb(0, 128, 255); }
                #title { font-size: 24px; font-weight: bold; }
                """;
        StyleSheet sheet = StyleSheet.parse(css);

        assertEquals(3, sheet.getRules().size());

        assertRuleHas(sheet, "button", StyleProperty.COLOR, 0xFFFF0000);
        assertRuleHasFloat(sheet, "button", StyleProperty.PADDING, 8f);
        assertRuleHas(sheet, ".primary", StyleProperty.BACKGROUND, 0xFF0080FF);
        assertRuleHasFloat(sheet, "#title", StyleProperty.FONT_SIZE, 24f);
        assertRuleHas(sheet, "#title", StyleProperty.FONT_WEIGHT, 700);
    }

    @Test
    void parsesHexShorthandAndOpacity() {
        String css = "label { color: #abc; opacity: 0.5; }";
        StyleSheet sheet = StyleSheet.parse(css);
        Style s = sheet.getRules().get(0).style();
        // #abc -> rgb(170,187,204) fully opaque -> ARGB 0xFFAABBCC
        assertEquals(0xFFAABBCC, s.getInt(StyleProperty.COLOR, 0));
        assertEquals(0.5f, s.getFloat(StyleProperty.OPACITY, 0f), 1e-6);
    }

    @Test
    void collectsRootVariablesAndResolvesVarReferences() {
        String css = """
                :root { --brand: #123456; }
                button { background: var(--brand); }
                """;
        StyleSheet sheet = StyleSheet.parse(css);
        assertEquals("#123456", sheet.getRootVariables().get("--brand"));
        // :root contributes no matching rule of its own.
        assertEquals(1, sheet.getRules().size());
        assertEquals(0xFF123456,
                sheet.getRules().get(0).style().getInt(StyleProperty.BACKGROUND, 0));
    }

    @Test
    void unknownPropertiesAreIgnoredWithWarning() {
        String css = "div { position: absolute; display: flex; }";
        StyleSheet sheet = StyleSheet.parse(css);
        assertTrue(sheet.getUnsupportedProperties().contains("position"),
                "position should be reported as unsupported");
        assertFalse(sheet.getUnsupportedProperties().contains("display"),
                "display is supported and must not be reported");
        assertEquals("flex",
                sheet.getRules().get(0).style().getString(StyleProperty.DISPLAY, null));
    }

    @Test
    void descendantAndChildCombinatorsAreParsed() {
        StyleSheet sheet = StyleSheet.parse("""
                div > button.flat { margin: 4px; }
                div button.deep { margin: 8px; }
                """);
        assertEquals(2, sheet.getRules().size());
        assertNotNull(sheet.getRules().get(0).selector());

        // Child combinator requires direct parenthood. Panels default to the "div" tag.
        Panel root = new Panel(0, 0, 400, 300);
        Panel inner = new Panel(0, 0, 400, 100);
        Button direct = new Button(0, 0, 80, 30, "a");
        direct.addStyleClass("flat");
        Button nested = new Button(0, 0, 80, 30, "b");
        nested.addStyleClass("deep");
        inner.add(nested);
        root.add(direct);
        root.add(inner);
        StyleEngine.apply(root, sheet);

        assertEquals(4f, direct.getComputedStyle().getFloat(StyleProperty.MARGIN, 0f), 1e-6,
                "'div > button.flat' matches the direct child only");
        assertEquals(8f, nested.getComputedStyle().getFloat(StyleProperty.MARGIN, 0f), 1e-6,
                "'div button.deep' matches a descendant at any depth");
    }

    @Test
    void invalidSelectorThrowsParseException() {
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("..broken"));
    }

    @Test
    void parseInlineHandlesMultipleDeclarationsAndVars() {
        Map<String, String> vars = new HashMap<>();
        vars.put("--accent", "#00FF00");
        Style style = Style.parseInline("color: var(--accent); width: 120px;", vars);
        assertEquals(0xFF00FF00, style.getInt(StyleProperty.COLOR, 0));
        assertEquals(120f, style.getFloat(StyleProperty.WIDTH, 0f), 1e-6);
    }

    // ------------------------------------------------------------------
    // Block 5 item 5.29 — parseInline warning parity with StyleSheet.parse
    // ------------------------------------------------------------------

    @Test
    void parseInlineWarnsOnUnsupportedPropertiesLikeStylesheetParse() {
        java.io.PrintStream original = System.err;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        try {
            System.setErr(new java.io.PrintStream(captured, true));
            // Two bad declarations: an unknown property name and a supported
            // name whose value form is unparseable (4-value padding shorthand).
            Style style = Style.parseInline("color: red; bogus-prop: 1; padding: 1 2 3 4", Map.of());

            String log = captured.toString();
            assertTrue(log.contains("[GlyphUI] Warning")
                            && log.contains("bogus-prop"),
                    "unknown inline properties must be warned about, like StyleSheet.parse does");
            assertTrue(log.contains("padding"),
                    "supported names with unsupported value forms must also warn");
            assertEquals(0xFFFF0000, style.getInt(StyleProperty.COLOR, 0),
                    "valid declarations in the same string must still parse");
            assertFalse(style.has(StyleProperty.PADDING),
                    "the unparseable declaration must still be dropped");
        } finally {
            System.setErr(original);
        }
    }

    @Test
    void parseInlineWarningIsDeduplicatedPerProperty() {
        java.io.PrintStream original = System.err;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        try {
            System.setErr(new java.io.PrintStream(captured, true));
            Style.parseInline("bogus-prop: 1; bogus-prop: 2; bogus-prop: 3", Map.of());

            String log = captured.toString();
            int first = log.indexOf("bogus-prop");
            long count = java.util.Arrays.stream(log.split("\n"))
                    .filter(line -> line.contains("bogus-prop")).count();
            assertTrue(first >= 0, "the dropped property must be reported at least once");
            assertEquals(1, count,
                    "repeated bad declarations must warn once per distinct property, "
                            + "mirroring StyleSheet.parse deduplication");
        } finally {
            System.setErr(original);
        }
    }

    @Test
    void parseInlineStaysSilentForMalformedSegmentsWithoutPropertyNames() {
        java.io.PrintStream original = System.err;
        java.io.ByteArrayOutputStream captured = new java.io.ByteArrayOutputStream();
        try {
            System.setErr(new java.io.PrintStream(captured, true));
            // Trailing semicolons, colon-less fragments and custom properties
            // carry no reportable property name: they must stay silent so
            // routine hand-written markup does not spam the log.
            Style.parseInline("noColon; ; --var: 1", Map.of());

            assertEquals("", captured.toString(),
                    "segments without a property name must not produce warnings");
        } finally {
            System.setErr(original);
        }
    }

    private static void assertRuleHas(StyleSheet sheet, String selectorText,
                                      StyleProperty property, Object expected) {
        StyleSheet.Rule rule = findRule(sheet, selectorText);
        assertEquals(expected, rule.style().get(property), selectorText + "/" + property);
    }

    private static void assertRuleHasFloat(StyleSheet sheet, String selectorText,
                                           StyleProperty property, float expected) {
        StyleSheet.Rule rule = findRule(sheet, selectorText);
        Object actual = rule.style().get(property);
        assertNotNull(actual, selectorText + "/" + property + " missing");
        assertEquals(expected, ((Number) actual).floatValue(), 1e-6,
                selectorText + "/" + property);
    }

    private static StyleSheet.Rule findRule(StyleSheet sheet, String selectorText) {
        return sheet.getRules().stream()
                .filter(r -> r.selector().toString().equals(selectorText))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no rule with selector: " + selectorText));
    }
}
