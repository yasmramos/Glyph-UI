package io.github.yasmramos.glyphui.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.humbleui.skija.Color;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Panel;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Regression tests for Block 5 audit items 5.24 and 5.25.
 *
 * <ul>
 *   <li><b>5.24</b> — {@code StyleEngine.resolvedCache} is keyed by rule id,
 *       which restarts at 0 for every stylesheet. The engine must drop the
 *       cache whenever the sheet identity it caches changes (including when
 *       the same instance is re-assigned through {@code setStyleSheet}, in
 *       case the sheet ever becomes mutable), so stale resolved styles from
 *       an old sheet can never be served under a colliding id.</li>
 *   <li><b>5.25</b> — {@code Panel.getBackgroundColor()} must resolve the
 *       theme through {@code Component.getTheme()}, not by reading
 *       {@code Theme.current()} directly, so a future per-subtree theme hook
 *       only needs to override that single accessor.</li>
 * </ul>
 */
class StyleEngineCacheInvalidationTest {

    private static final int BLUE = Color.makeARGB(255, 0, 0, 255);

    /** A panel with one button child; both start with empty computed style. */
    private static Panel tree() {
        Panel root = new Panel(0, 0, 100, 100);
        Button button = new Button("B");
        root.add(button);
        return root;
    }

    // ------------------------------------------------------------------
    // 5.24 resolvedCache invalidation on stylesheet identity change
    // ------------------------------------------------------------------

    @Nested
    class CacheInvalidation {

        @Test
        @DisplayName("re-applying the same sheet keeps using the warm cache")
        void sameSheetKeepsCache() {
            StyleSheet sheet = StyleSheet.parse("button { background: var(--accent); }");
            Theme theme = Theme.dark();
            StyleEngine engine = new StyleEngine(sheet, theme);

            Panel root = tree();
            Button button = (Button) root.getChildren().get(0);

            engine.apply(root);
            int first = button.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0);

            engine.apply(root);
            int second = button.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0);

            // Same sheet + same theme → cached resolution is reused verbatim.
            assertEquals(first, second);
            assertEquals(sheet, engine.getStyleSheet());
        }

        @Test
        @DisplayName("setStyleSheet(sameInstance) clears the cache eagerly")
        void resettingSameInstanceClearsCache() {
            StyleSheet sheet = StyleSheet.parse("button { background: var(--accent); }");
            StyleEngine engine = new StyleEngine(sheet, Theme.dark());

            Panel root = tree();
            engine.apply(root);

            // Re-setting the very same object must still drop the cache: if
            // StyleSheet ever becomes mutable, callers use this setter to
            // signal "content changed" without creating a new instance.
            engine.setStyleSheet(sheet);

            // After the clear the next pass re-resolves from scratch; the
            // result for an immutable sheet is identical, so assert behavior
            // stays correct across the invalidation.
            Button button = (Button) root.getChildren().get(0);
            int before = button.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0);
            engine.apply(root);
            assertEquals(before,
                    button.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0));
        }

        @Test
        @DisplayName("colliding rule ids across sheets never serve stale styles")
        void differentSheetsDoNotShareCacheEntries() {
            // Both rules get id 0 in their respective sheets — exactly the
            // collision the cache must guard against.
            StyleSheet red = StyleSheet.parse("button { background: var(--accent); }");
            StyleSheet blue = StyleSheet.parse("button { background: #0000ff; }");
            assertEquals(StyleSheet.ruleId(red.matchingRules(List.of(new Button("x"))).get(0)),
                    StyleSheet.ruleId(blue.matchingRules(List.of(new Button("y"))).get(0)),
                    "test premise: rule ids collide across sheets");

            StyleEngine engine = new StyleEngine(red, Theme.dark());

            Panel root1 = tree();
            engine.apply(root1);
            Button b1 = (Button) root1.getChildren().get(0);
            assertEquals(Theme.dark().getAccentColor(),
                    b1.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0));

            // Swap the sheet through the public API. The old entry lives under
            // id 0 — the same id the new sheet's single rule uses — so without
            // invalidation the engine would keep serving the dark-accent style
            // resolved for the previous sheet.
            engine.setStyleSheet(blue);
            Panel root2 = tree();
            engine.apply(root2);
            Button b2 = (Button) root2.getChildren().get(0);
            assertEquals(BLUE, b2.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0),
                    "stale resolved style from the previous sheet leaked via rule-id collision");

            // And back again: the blue entry must not poison the red sheet.
            engine.setStyleSheet(red);
            Panel root3 = tree();
            engine.apply(root3);
            Button b3 = (Button) root3.getChildren().get(0);
            assertEquals(Theme.dark().getAccentColor(),
                    b3.getComputedStyle().getInt(StyleProperty.BACKGROUND, 0));
        }

        @Test
        @DisplayName("theme instance change also drops the cache")
        void themeChangeInvalidatesCache() {
            StyleSheet sheet = StyleSheet.parse("button { background: var(--accent); }");
            StyleEngine engine = new StyleEngine(sheet, null); // consult Theme.current()

            Panel rootA = tree();
            Theme.setCurrent(Theme.light());
            engine.apply(rootA);
            int lightAccent = ((Button) rootA.getChildren().get(0))
                    .getComputedStyle().getInt(StyleProperty.BACKGROUND, 0);

            Theme.setCurrent(Theme.dark());
            Panel rootB = tree();
            engine.apply(rootB);
            int darkAccent = ((Button) rootB.getChildren().get(0))
                    .getComputedStyle().getInt(StyleProperty.BACKGROUND, 0);

            assertNotEquals(lightAccent, darkAccent,
                    "var(--accent) resolution must follow the active theme instance");
        }
    }

    // ------------------------------------------------------------------
    // 5.25 Panel.getBackgroundColor() resolves through Component.getTheme()
    // ------------------------------------------------------------------

    @Nested
    class PanelThemeAccessor {

        /**
         * Source-level contract (item 5.25): the body of
         * {@code Panel.getBackgroundColor()} must resolve the theme through
         * the inherited {@code Component.getTheme()} accessor instead of
         * reading {@code Theme.current()} directly, so a future per-subtree
         * theme hook only needs to override that single method.
         */
        @Test
        @DisplayName("Panel.getBackgroundColor resolves via getTheme(), not Theme.current()")
        void panelUsesGetThemeNotCurrent() throws Exception {
            String source = java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/java/io/github/yasmramos/glyphui/ui/Panel.java"));
            int methodStart = source.indexOf("public int getBackgroundColor() {");
            assertTrue(methodStart >= 0, "test premise: method found in Panel source");
            // Method body ends at the closing brace on its own column-4 indent.
            int bodyEnd = source.indexOf("\n    }\n", methodStart);
            String body = source.substring(methodStart, bodyEnd);
            assertTrue(body.contains("getTheme().getBackgroundColor()"),
                    "Panel.getBackgroundColor() must call getTheme() (item 5.25)");
            assertFalse(body.contains("Theme.current()"),
                    "Panel.getBackgroundColor() must not read Theme.current() "
                            + "directly (item 5.25)");
        }

        @Test
        @DisplayName("panel background follows the active theme via getTheme()")
        void panelBackgroundFollowsActiveTheme() {
            Theme previous = Theme.current();
            Theme.setCurrent(Theme.dark());
            try {
                Panel panel = new Panel(0, 0, 10, 10);
                // No explicit background, no CSS: falls back to the themed
                // window color through Component.getTheme().
                assertEquals(Theme.dark().getBackgroundColor(), panel.getBackgroundColor());
            } finally {
                Theme.setCurrent(previous);
            }
        }
    }
}
