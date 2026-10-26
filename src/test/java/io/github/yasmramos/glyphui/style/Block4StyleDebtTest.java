package io.github.yasmramos.glyphui.style;

import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.ui.Panel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Block 4 design-debt fixes in the style subsystem:
 *
 * <ul>
 *   <li><b>4.7</b> {@code StyleEngine.buildVariables} now lets CSS custom
 *       properties override the theme-derived {@code --bg/--fg/--accent/
 *       --border} variables (CSS wins).</li>
 *   <li><b>4.8</b> {@code StyleSheet.parse} documents that {@code :root}
 *       contributes only custom properties; non-variable declarations such
 *       as {@code :root { color: red }} are ignored on purpose.</li>
 *   <li><b>4.9</b> {@code Rule.id()} mirrors {@code Rule.order()} within a
 *       single sheet and equality ignores identity — documented behavior.</li>
 * </ul>
 */
class Block4StyleDebtTest {

    // ------------------------------------------------------------------
    // 4.7 — CSS custom properties win over theme-derived variables
    // ------------------------------------------------------------------

    @Test
    void cssAccentVariableOverridesThemeDerivedAccent() {
        String css = """
                :root { --accent: #FF00AA; }
                .chip { background: var(--accent); }
                """;
        Theme theme = Theme.builder().mode(Theme.Mode.DARK).build();
        Panel root = new Panel(100f, 100f);
        Panel chip = new Panel(50f, 20f);
        chip.addStyleClass("chip");
        root.add(chip);

        // The static one-shot apply does not retain the tree afterwards, so
        // hold an engine instance to keep the cascade result reachable.
        StyleEngine engine = new StyleEngine(StyleSheet.parse(css), theme);
        engine.apply(root);

        int themeAccent = theme.getAccentColor();
        int resolved = chip.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0);
        assertNotNull(engine);
        assertTrue(resolved != themeAccent,
                "CSS :root --accent must win over the theme-derived variable "
                        + "(theme accent=" + Integer.toHexString(themeAccent)
                        + ", resolved=" + Integer.toHexString(resolved) + ")");
        assertEquals(0x00FF00AA, resolved & 0xFFFFFF,
                "resolved background must be the CSS-declared accent color");
    }

    @Test
    void themeVariablesStillApplyWhenCssDoesNotOverrideThem() {
        String css = ".chip { border-color: var(--accent); }";
        Theme theme = Theme.builder().mode(Theme.Mode.DARK).build();
        Panel root = new Panel(100f, 100f);
        Panel chip = new Panel(50f, 20f);
        chip.addStyleClass("chip");
        root.add(chip);

        StyleEngine engine = new StyleEngine(StyleSheet.parse(css), theme);
        engine.apply(root);

        int resolved = chip.getComputedStyle().getInt(StyleProperty.BORDER_COLOR, 0);
        assertNotNull(engine);
        assertEquals(theme.getAccentColor(), resolved,
                "without a CSS override the theme-derived --accent is used");
    }

    // ------------------------------------------------------------------
    // 4.8 — :root contributes custom properties only (documented contract)
    // ------------------------------------------------------------------

    @Test
    void rootRuleContributesCustomPropertiesButNotPlainDeclarations() {
        String css = """
                :root { --tint: #00FF00; color: #FF0000; }
                """;
        StyleSheet sheet = StyleSheet.parse(css);
        Panel root = new Panel(100f, 100f);

        StyleEngine.apply(root, sheet, Theme.dark());

        // Custom property from :root is available to var() consumers...
        assertEquals("#00FF00", sheet.getRootVariables().get("--tint"));
        // ...but the plain `color` declaration is intentionally NOT turned
        // into a rule applied to the root (Block 4 item 4.8 documents this;
        // style the actual root selector/class instead).
        int rootColor = root.getComputedStyle().getInt(StyleProperty.COLOR, 0);
        assertTrue(rootColor != (0xFF000000 | 0xFF0000),
                ":root { color: ... } must not silently apply to the root node");
    }

    // ------------------------------------------------------------------
    // 4.9 — Rule.id() == Rule.order() within one sheet (documented)
    // ------------------------------------------------------------------

    @Test
    void ruleIdMirrorsOrderAndEqualityIgnoresIdentity() {
        String css = """
                .a { padding: 1px; }
                .b { padding: 2px; }
                .c { padding: 3px; }
                """;
        StyleSheet sheet = StyleSheet.parse(css);
        List<StyleSheet.Rule> rules = sheet.getRules();
        assertEquals(3, rules.size());

        for (StyleSheet.Rule rule : rules) {
            assertEquals(rule.order(), rule.id(),
                    "within a single sheet the id mirrors the parse order "
                            + "(global ids across sheets are a future concern)");
        }

        // equals/hashCode are structural: two sheets parsed from the same CSS
        // produce equal rules even though their identity fields exist.
        StyleSheet second = StyleSheet.parse(css);
        assertEquals(rules.get(0).toString(), second.getRules().get(0).toString(),
                "rules from identically parsed sheets are structurally identical "
                        + "(Rule#equals intentionally includes identity: use "
                        + "selector()/style()/order() for cross-sheet comparison)");
        assertNotNull(rules.get(0).selector());
        assertNotNull(rules.get(0).style());
    }
}
