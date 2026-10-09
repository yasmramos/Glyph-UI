package com.glyphui.style;

import com.glyphui.ui.Button;
import com.glyphui.ui.Label;
import com.glyphui.ui.Panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the cascade: specificity ordering (inline &gt; id &gt; class &gt;
 * type), textual inheritance, and {@code var(--name)} resolution against the
 * active theme.
 */
class CascadeSpecificityTest {

    @Test
    void idBeatsClassBeatsType() {
        // 8-digit hex colors are CSS Color 4 #rrggbbaa (alpha LAST); the engine
        // stores them internally as ARGB ints (alpha in the most significant byte).
        StyleSheet sheet = StyleSheet.parse("""
                button { color: #11000000; }
                .accent { color: #22000000; }
                #special { color: #33000000; }
                """);
        Panel root = new Panel(0, 0, 400, 300);
        Button b = new Button(0, 0, 100, 30, "hi");
        b.setId("special");
        b.addStyleClass("accent");
        root.add(b);

        StyleEngine.apply(root, sheet);

        assertEquals(0x33000000, b.getComputedStyle().getInt(StyleProperty.COLOR, 0),
                "#id must win over .class and type selectors");
    }

    @Test
    void inlineStyleWinsOverEverything() {
        StyleSheet sheet = StyleSheet.parse("#x { color: #AA000000; }");
        Panel root = new Panel(0, 0, 400, 300);
        Label label = new Label(0, 0, 100, 20, "text");
        label.setId("x");
        label.setInlineStyle(Style.builder()
                .color(StyleProperty.COLOR, 0xFF00FF00).build());
        root.add(label);

        StyleEngine.apply(root, sheet);

        assertEquals(0xFF00FF00, label.getComputedStyle().getInt(StyleProperty.COLOR, 0),
                "inline style has the highest priority");
    }

    @Test
    void sameSpecificityLastRuleWins() {
        StyleSheet sheet = StyleSheet.parse("""
                .a { padding: 4px; }
                .b { padding: 9px; }
                """);
        Panel root = new Panel(0, 0, 400, 300);
        Button b = new Button(0, 0, 100, 30, "hi");
        b.addStyleClass("a");
        b.addStyleClass("b");
        root.add(b);

        StyleEngine.apply(root, sheet);

        assertEquals(9f, b.getComputedStyle().getFloat(StyleProperty.PADDING, 0f), 1e-6,
                "later declaration order wins at equal specificity");
    }

    @Test
    void textualPropertiesInheritFromParent() {
        StyleSheet sheet = StyleSheet.parse("""
                div.card { font-size: 18px; color: #FF00FF00; }
                """);
        Panel root = new Panel(0, 0, 400, 300);
        Panel card = new Panel(0, 0, 400, 200);
        card.addStyleClass("card");
        Label inner = new Label(0, 0, 100, 20, "inherited");
        card.add(inner);
        root.add(card);

        StyleEngine.apply(root, sheet);

        assertEquals(18f, inner.getComputedStyle().getFloat(StyleProperty.FONT_SIZE, 0f), 1e-6,
                "font-size must inherit down the tree");
        assertEquals(0xFF00FF00, inner.getComputedStyle().getInt(StyleProperty.COLOR, 0));
        // Non-inherited properties do not leak to children.
        assertFalse(inner.getComputedStyle().has(StyleProperty.PADDING));
    }

    @Test
    void themeVariablesAreInjectedIntoVarResolution() {
        StyleSheet sheet = StyleSheet.parse("button { background: var(--accent); }");
        Panel root = new Panel(0, 0, 400, 300);
        Button b = new Button(0, 0, 100, 30, "hi");
        root.add(b);

        new StyleEngine(sheet, com.glyphui.graphics.Theme.dark()).apply(root);

        int expected = com.glyphui.graphics.Theme.dark().getAccentColor();
        assertEquals(expected, b.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0),
                "var(--accent) resolves against the engine's theme");
    }

    @Test
    void descendantSelectorMatchesAgainstTreePath() {
        StyleSheet sheet = StyleSheet.parse("div.toolbar button.flat { margin: 6px; }");
        Panel root = new Panel(0, 0, 400, 300);
        // A "div.toolbar" element in markup is a generic container (Panel -> tag "div")
        // carrying the "toolbar" class.
        Panel toolbar = new Panel(0, 0, 400, 40);
        toolbar.addStyleClass("toolbar");
        Button flat = new Button(0, 0, 80, 30, "ok");
        flat.setStyleTag("button");
        flat.addStyleClass("flat");
        toolbar.add(flat);
        Button outside = new Button(0, 0, 80, 30, "no");
        outside.addStyleClass("flat");
        root.add(toolbar);
        root.add(outside);

        StyleEngine.apply(root, sheet);

        assertEquals(6f, flat.getComputedStyle().getFloat(StyleProperty.MARGIN, 0f), 1e-6);
        assertEquals(0f, outside.getComputedStyle().getFloat(StyleProperty.MARGIN, 0f), 1e-6,
                "button outside the toolbar must not match the descendant rule");
    }
}
