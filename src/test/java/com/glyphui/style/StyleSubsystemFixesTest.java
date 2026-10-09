package com.glyphui.style;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.glyphui.ui.Button;
import com.glyphui.ui.Panel;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for the style-subsystem audit fixes:
 *
 * <ul>
 *   <li>{@code >} child combinator used to throw IndexOutOfBoundsException</li>
 *   <li>{@code border: 2px} stored a String under a Float-typed property and
 *       was silently ignored by getFloat</li>
 *   <li>ID selectors were lower-cased at parse time, breaking case-sensitive
 *       matching against Component ids</li>
 *   <li>{@code Style.Builder.set} accepted mistyped values silently</li>
 * </ul>
 */
class StyleSubsystemFixesTest {

    // ------------------------------------------------------------------
    // Bug #1: '>' child combinator crashed Selector.parse
    // ------------------------------------------------------------------

    @Nested
    class ChildCombinator {

        @Test
        @DisplayName("'button > span' parses without throwing (was IOOBE)")
        void parsesChildCombinator() {
            assertDoesNotThrow(() -> Selector.parse("button > span"));
            assertDoesNotThrow(() -> Selector.parse("panel > .foo"));
            assertDoesNotThrow(() -> Selector.parse("a > b > c"));
        }

        @Test
        @DisplayName("'> ' requires adjacency: direct child matches, grandchild does not")
        void childCombinatorRequiresAdjacency() {
            // root(panel) -> mid(button) -> leaf(span)
            TestNode root = new TestNode("panel", "root");
            TestNode mid = new TestNode("button", "mid");
            TestNode leaf = new TestNode("span", "leaf");
            root.children.add(mid);
            mid.parent = root;
            mid.children.add(leaf);
            leaf.parent = mid;

            Selector direct = Selector.parse("button > span");
            assertTrue(direct.matches(leaf.path()),
                    "'button > span' must match an immediate button child");

            // Remove adjacency: attach leaf directly under root.
            mid.children.remove(leaf);
            leaf.parent = root;
            root.children.add(leaf);
            assertFalse(direct.matches(leaf.path()),
                    "'button > span' must NOT match when span is not a direct child");
        }

        @Test
        @DisplayName("misplaced '>' still rejected")
        void rejectsMisplacedCombinator() {
            assertThrows(IllegalArgumentException.class, () -> Selector.parse("> span"));
            assertThrows(IllegalArgumentException.class, () -> Selector.parse("button >"));
            assertThrows(IllegalArgumentException.class, () -> Selector.parse("a > > b"));
        }

        @Test
        @DisplayName("descendant selector still works across levels")
        void descendantStillWorks() {
            TestNode root = new TestNode("panel", "root");
            TestNode mid = new TestNode("button", "mid");
            TestNode leaf = new TestNode("span", "leaf");
            root.children.add(mid);
            mid.parent = root;
            mid.children.add(leaf);
            leaf.parent = mid;

            assertTrue(Selector.parse("panel span").matches(leaf.path()));
        }
    }

    // ------------------------------------------------------------------
    // Bug #2: border: 2px produced a String under BORDER_WIDTH (Float)
    // ------------------------------------------------------------------

    @Nested
    class BorderWidthTyping {

        @Test
        @DisplayName("'border: 2px' resolves to a Float typed BORDER_WIDTH")
        void borderPxIsFloat() {
            StyleSheet sheet = StyleSheet.parse("button { border: 2px; }");
            Style rule = sheet.getRules().get(0).style();
            Object raw = rule.get(StyleProperty.BORDER_WIDTH);
            assertNotNull(raw, "BORDER_WIDTH must be declared");
            assertEquals(Float.class, raw.getClass(),
                    "border length must be stored as Float, not String");
            assertEquals(2f, ((Float) raw), 1e-6f);
        }

        @Test
        @DisplayName("'border: 0' (unitless zero) is accepted")
        void borderZeroWithoutUnit() {
            StyleSheet sheet = StyleSheet.parse("button { border: 0; }");
            Style rule = sheet.getRules().get(0).style();
            assertEquals(0f, rule.getFloat(StyleProperty.BORDER_WIDTH, -1f), 1e-6f);
        }

        @Test
        @DisplayName("multi-token shorthand 'border: 1px solid red' stays unsupported")
        void multiTokenBorderRejected() {
            StyleSheet sheet = StyleSheet.parse("button { border: 1px solid red; }");
            assertFalse(sheet.getRules().get(0).style()
                    .has(StyleProperty.BORDER_WIDTH));
        }

        @Test
        @DisplayName("cascade end-to-end: computed style exposes border width via getFloat")
        void borderWidthReadableAfterCascade() {
            StyleSheet sheet = StyleSheet.parse("button { border: 3px; }");
            TestNode root = new TestNode("panel", "root");
            TestNode btn = new TestNode("button", "btn");
            root.children.add(btn);
            btn.parent = root;

            Style resolved = resolveFor(btn, sheet);
            assertEquals(3f, resolved.getFloat(StyleProperty.BORDER_WIDTH, 0f), 1e-6f,
                    "getFloat(BORDER_WIDTH) must see the parsed value");
        }
    }

    // ------------------------------------------------------------------
    // Bug #13: id selectors are case-sensitive
    // ------------------------------------------------------------------

    @Nested
    class IdCaseSensitivity {

        @Test
        @DisplayName("#myButton matches component id 'myButton' verbatim")
        void camelCaseIdMatches() {
            Selector sel = Selector.parse("#myButton");
            TestNode node = new TestNode("button", "myButton");
            assertTrue(sel.matches(node.path()),
                    "ID selectors must preserve case like HTML ids");
        }

        @Test
        @DisplayName("#myButton does NOT match 'mybutton'")
        void wrongCaseDoesNotMatch() {
            Selector sel = Selector.parse("#myButton");
            TestNode node = new TestNode("button", "mybutton");
            assertFalse(sel.matches(node.path()));
        }
    }

    // ------------------------------------------------------------------
    // Fix #8: Builder.set validates value types
    // ------------------------------------------------------------------

    @Nested
    class BuilderTypeValidation {

        @Test
        @DisplayName("set(WIDTH, \"hello\") throws instead of poisoning the map")
        void rejectsStringForLengthProperty() {
            assertThrows(IllegalArgumentException.class,
                    () -> Style.builder().set(StyleProperty.WIDTH, "hello"));
        }

        @Test
        @DisplayName("set(COLOR, 4.2f) throws (color expects Integer)")
        void rejectsFloatForColorProperty() {
            assertThrows(IllegalArgumentException.class,
                    () -> Style.builder().set(StyleProperty.COLOR, 4.2f));
        }

        @Test
        @DisplayName("numeric widening tolerated: int into float property")
        void acceptsIntegerForFloatProperty() {
            Style s = Style.builder().length(StyleProperty.PADDING, 8f).build();
            assertEquals(8f, s.getFloat(StyleProperty.PADDING, 0f), 1e-6f);
            assertDoesNotThrow(() -> Style.builder()
                    .set(StyleProperty.MARGIN, 5) // boxed Integer
                    .build());
        }

        @Test
        @DisplayName("RAW_VALUES is exempt from validation")
        void rawValuesExempt() {
            Map<StyleProperty, String> raw =
                    Map.of(StyleProperty.COLOR, "var(--fg)");
            assertDoesNotThrow(() -> Style.builder()
                    .set(StyleProperty.RAW_VALUES, raw)
                    .build());
        }

        @Test
        @DisplayName("typed shortcuts color()/keyword() keep working")
        void shortcutsStillWork() {
            Style s = Style.builder()
                    .color(StyleProperty.BACKGROUND, 0xFF00FF00)
                    .keyword(StyleProperty.DISPLAY, "flex")
                    .build();
            assertEquals(0xFF00FF00, s.getInt(StyleProperty.BACKGROUND, 0));
            assertEquals("flex", s.getString(StyleProperty.DISPLAY, null));
        }
    }

    // ------------------------------------------------------------------
    // Fix #3/#17: cascade performance plumbing — ruleId stable + cache reuse
    // ------------------------------------------------------------------

    @Nested
    class RuleIdentity {

        @Test
        @DisplayName("ruleId is stable across calls and unique per rule")
        void ruleIdStableAndKnown() {
            StyleSheet sheet = StyleSheet.parse(
                    "a { color: red; } b { color: blue; } c { color: lime; }");
            var rules = sheet.getRules();
            assertEquals(3, rules.size());
            int id0 = StyleSheet.ruleId(rules.get(0));
            assertEquals(id0, StyleSheet.ruleId(rules.get(0)), "id must be stable");
            // Ids are unique among the sheet's rules (no cache collisions).
            int id1 = StyleSheet.ruleId(rules.get(1));
            int id2 = StyleSheet.ruleId(rules.get(2));
            assertTrue(id0 != id1 && id1 != id2 && id0 != id2,
                    "every rule of a sheet must get a distinct cache key");
        }
    }


    // ------------------------------------------------------------------
    // Fix #1 (ARGB): 8-digit hex uses the project convention #aarrggbb,
    // consistent with the internal ARGB ints used by Skija/Java. This is
    // what makes theme variables and raw-value round-trips keep their
    // alpha byte instead of rotating channels.
    // ------------------------------------------------------------------

    @Nested
    class ArgbHexConvention {

        @Test
        @DisplayName("8-digit hex parses as #aarrggbb without channel rotation")
        void eightDigitHexIsArgb() {
            assertEquals(0x11000000, CssValues.parseColor("#11000000"),
                    "alpha in the most significant byte");
            assertEquals(0xFF00FF00, CssValues.parseColor("#FF00FF00"),
                    "opaque green: alpha first, not last");
            assertEquals(0x80FF8000, CssValues.parseColor("#80FF8000"));
        }

        @Test
        @DisplayName("#rgb/#rrggbb forms stay fully opaque")
        void shortAndSixDigitFormsAreOpaque() {
            assertEquals(0xFF112233, CssValues.parseColor("#123"));
            assertEquals(0xFFFF00FF, CssValues.parseColor("#ff00ff"));
        }

        @Test
        @DisplayName("color round-trips through the raw pipeline unchanged")
        void rawValueRoundTripKeepsAlpha() {
            String raw = "#11000000";
            Integer parsed = CssValues.parseColor(raw);
            assertNotNull(parsed);
            assertEquals(parsed, CssValues.parseColor(Integer.toHexString(parsed).length() == 6
                            ? "#" + Integer.toHexString(parsed)
                            : "#" + String.format("%08x", parsed)),
                    "re-serializing an ARGB int as 8 hex digits must parse back identically");
        }

        @Test
        @DisplayName("var(--c) with an alpha color keeps its alpha byte")
        void varWithAlphaColorResolvesThroughInlineParse() {
            Map<String, String> vars = Map.of("--fg", "#11000000");
            Style s = Style.parseInline("color: var(--fg);", vars);
            assertEquals(0x11000000, s.getInt(StyleProperty.COLOR, 0),
                    "theme-style variable values must not lose the alpha channel");
        }

        @Test
        @DisplayName("theme default colors inherit into children without rotation")
        void engineThemeVariableKeepsAlphaChannel() {
            StyleSheet sheet = StyleSheet.parse("button { color: var(--accent); }");
            Panel root = new Panel(0, 0, 200, 200);
            Button b = new Button(0, 0, 100, 30, "Hi");
            root.add(b);

            com.glyphui.graphics.Theme light = com.glyphui.graphics.Theme.light();
            new StyleEngine(sheet, light).apply(root);

            assertEquals(light.getAccentColor(), b.getComputedStyle().getInt(StyleProperty.COLOR, 0),
                    "var(--accent) from the theme must arrive byte-for-byte (no ARGB rotation)");
        }
    }

    // ------------------------------------------------------------------
    // Fix #4/#17: engine-level regressions (CME-safe iteration, cache reuse)
    // ------------------------------------------------------------------

    @Nested
    class EngineCascade {

        @Test
        @DisplayName("child combinator works end-to-end through StyleEngine")
        void engineAppliesChildCombinator() {
            // Panel's style tag is "div" (the generic container element), so
            // the parent part of the selector must be "div", not "panel".
            StyleSheet sheet = StyleSheet.parse("div > button { padding: 7px; }");
            Panel root = new Panel(0, 0, 200, 200);
            Button direct = new Button(0, 0, 100, 30, "Direct");
            root.add(direct);
            // A grandchild must NOT match the '>' rule. The intermediate node
            // must not itself be a "div": a button directly under a div
            // legitimately matches 'div > button'.
            Panel inner = new Panel(0, 0, 100, 30);
            inner.setStyleTag("section");
            Button nested = new Button(0, 0, 80, 30, "Nested");
            inner.add(nested);
            root.add(inner);

            StyleEngine.apply(root, sheet);

            assertEquals(7f, direct.getComputedStyle().getFloat(StyleProperty.PADDING, 0f),
                    1e-6f, "'div > button' must style a direct child");
            assertEquals(0f, nested.getComputedStyle().getFloat(StyleProperty.PADDING, 0f),
                    1e-6f, "'div > button' must not style a grandchild");
        }

        @Test
        @DisplayName("resolved styles are cached across apply() passes")
        void resolvedCacheReusedAcrossPasses() {
            StyleSheet sheet = StyleSheet.parse(
                    ":root { --pad: 9px; } button { padding: var(--pad); }");
            Panel root = new Panel(0, 0, 200, 200);
            Button b = new Button(0, 0, 100, 30, "Cached");
            root.add(b);

            StyleEngine engine = new StyleEngine(sheet);
            engine.apply(root);
            float first = b.getComputedStyle().getFloat(StyleProperty.PADDING, 0f);
            assertEquals(9f, first, 1e-6f, "var() must resolve via :root variables");

            engine.apply(root);
            assertEquals(first, b.getComputedStyle().getFloat(StyleProperty.PADDING, 0f),
                    1e-6f, "second pass must yield the same cached resolution");
        }

        @Test
        @DisplayName("adding children mid-cascade does not throw CME")
        void mutationDuringCascadeIsSafe() {
            // A panel that lazily adds a child when it receives its computed
            // style — exactly the scenario that used to hit the live list.
            Panel root = new Panel(0, 0, 200, 200) {
                private boolean seeded;

                @Override
                public void setComputedStyle(Style computed) {
                    super.setComputedStyle(computed);
                    if (!seeded) {
                        seeded = true;
                        add(new Button(0, 0, 50, 20, "Lazy"));
                    }
                }
            };
            root.add(new Button(0, 0, 50, 20, "Eager"));

            StyleEngine engine = new StyleEngine(
                    StyleSheet.parse("button { color: red; }"));
            assertDoesNotThrow(() -> engine.apply(root),
                    "cascade iterates a snapshot of the children list");
            assertEquals(2, root.getChildren().size());
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Runs the same resolution StyleEngine performs for one node path. */
    private static Style resolveFor(TestNode node, StyleSheet sheet) {
        java.util.List<StyleNode> path = node.path();
        Style.Builder builder = Style.builder();
        for (StyleSheet.Rule rule : sheet.matchingRules(path)) {
            builder.putAll(rule.style().resolveVars(Map.of()));
        }
        return builder.build();
    }

    /** Minimal in-memory StyleNode for parser/cascade unit tests. */
    private static final class TestNode implements StyleNode {
        final String tag;
        final String id;
        TestNode parent;
        final java.util.List<TestNode> children = new java.util.ArrayList<>();

        TestNode(String tag, String id) {
            this.tag = tag;
            this.id = id;
        }

        java.util.List<StyleNode> path() {
            java.util.List<StyleNode> p = new java.util.ArrayList<>();
            for (TestNode n = this; n != null; n = n.parent) {
                p.add(n);
            }
            java.util.Collections.reverse(p);
            return p;
        }

        @Override
        public String styleTag() {
            return tag;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean hasClass(String className) {
            return false;
        }

        @Override
        public boolean matchesPseudo(Selector.PseudoClass pseudo) {
            return false;
        }

        @Override
        public StyleNode parent() {
            return parent;
        }
    }
}
