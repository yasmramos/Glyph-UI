package com.glyphui.style;

import com.glyphui.graphics.Theme;
import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies a {@link StyleSheet} to a live component tree.
 *
 * <p>The engine performs the classic CSS cascade for every component:</p>
 * <ol>
 *   <li>collect matching rules via {@link StyleSheet#matchingRules(StyleNode)}
 *       (already sorted lowest → highest priority);</li>
 *   <li>merge them into a rule style (later wins);</li>
 *   <li>inherit textual properties from the parent's computed style;</li>
 *   <li>store the result on the component with
 *       {@link Component#setComputedStyle(Style)}. Inline styles are merged
 *       last by {@link Component#getComputedStyle()} so they always win.</li>
 * </ol>
 *
 * <p>{@code :root} custom properties are resolved at parse time; in addition,
 * theme-derived variables ({@code --bg}, {@code --fg}, {@code --accent},
 * {@code --border}) are injected so stylesheets can reference the active
 * {@link Theme} through {@code var(--accent)} and friends.</p>
 *
 * <h2>State re-evaluation</h2>
 * Pseudo-class rules ({@code :hover}, {@code :focus}, {@code :disabled},
 * {@code :active}) depend on the mutable {@link com.glyphui.ui.ComponentState}.
 * Rather than tracking state changes per component, {@code Application} calls
 * {@link #apply(Component)} once per frame before rendering — the tree is
 * small and recomputation is cheap. Alternatively callers may invoke
 * {@code apply} whenever a state change occurs.
 */
public final class StyleEngine {

    private StyleSheet styleSheet;
    private Theme theme;

    /**
     * Creates an engine bound to a stylesheet. The active theme
     * ({@link Theme#current()}) is consulted lazily on each pass.
     *
     * @param styleSheet the stylesheet to apply (may be replaced later)
     */
    public StyleEngine(StyleSheet styleSheet) {
        this(styleSheet, null);
    }

    /**
     * Creates an engine with an explicit theme source for
     * {@code var(--bg/--fg/--accent/--border)} resolution.
     *
     * @param styleSheet the stylesheet (null treated as empty sheet)
     * @param theme      the theme (null → {@link Theme#current()} per pass)
     */
    public StyleEngine(StyleSheet styleSheet, Theme theme) {
        this.styleSheet = styleSheet != null ? styleSheet : StyleSheet.empty();
        this.theme = theme;
    }

    /**
     * Replaces the current stylesheet.
     *
     * @param styleSheet the new stylesheet
     */
    public void setStyleSheet(StyleSheet styleSheet) {
        this.styleSheet = styleSheet != null ? styleSheet : StyleSheet.empty();
    }

    /**
     * The currently applied stylesheet.
     *
     * @return the stylesheet, never null
     */
    public StyleSheet getStyleSheet() {
        return styleSheet;
    }

    /**
     * Convenience entry point: computes styles for the whole subtree rooted
     * at {@code root} using the given stylesheet.
     *
     * @param root       the subtree root component
     * @param styleSheet the stylesheet to apply
     */
    public static void apply(Component root, StyleSheet styleSheet) {
        new StyleEngine(styleSheet).apply(root);
    }

    /**
     * Runs a full cascade pass over the subtree rooted at {@code root}.
     *
     * @param root the subtree root (no-op when null)
     */
    public void apply(Component root) {
        if (root == null) {
            return;
        }
        Map<String, String> variables = buildVariables();
        // Theme-derived variables are not known at parse time, so rules that
        // reference var(--accent) & friends keep their raw declaration text
        // under StyleProperty.RAW_VALUES. Re-parse those lazily with this
        // engine's variable map (results cached per rule index).
        List<StyleSheet.Rule> allRules = styleSheet.getRules();
        Map<Integer, Style> resolvedCache = new HashMap<>();
        applyRecursive(root, null, variables, allRules, resolvedCache);
    }

    private void applyRecursive(Component component, Style parentComputed,
                                Map<String, String> variables,
                                List<StyleSheet.Rule> allRules,
                                Map<Integer, Style> resolvedCache) {
        List<StyleNode> path = buildPath(component);
        Style.Builder builder = Style.builder();
        // Cascade: matchingRules returns lowest-priority first, merge in order.
        // RAW_VALUES is a synthetic per-rule carrier of unresolved var()
        // declarations; it must never leak typed values from a lower-priority
        // rule into a higher-priority merge (see Style#withoutRawValues).
        for (StyleSheet.Rule rule : styleSheet.matchingRules(path)) {
            int index = allRules.indexOf(rule);
            // resolveVars() drops RAW_VALUES and folds unresolved var()
            // declarations into typed values; the result is a clean style.
            Style resolved = resolvedCache.computeIfAbsent(index,
                    i -> rule.style().resolveVars(variables));
            builder.putAll(resolved);
        }
        Style own = builder.build();
        Style computed = own.inheritFrom(parentComputed);
        component.setComputedStyle(computed);

        if (component instanceof Panel panel) {
            for (Component child : panel.getChildren()) {
                applyRecursive(child, computed, variables, allRules,
                        resolvedCache);
            }
        }
    }

    /**
     * Builds the ancestor path (root → component) used for descendant
     * selector matching. Kept local so the engine does not rely on cached
     * paths across frames (the tree may mutate between passes).
     */
    private static List<StyleNode> buildPath(Component component) {
        List<StyleNode> path = new ArrayList<>();
        for (Component c = component; c != null; ) {
            path.add(c);
            c = c.getParent();
        }
        java.util.Collections.reverse(path);
        return path;
    }

    /**
     * Variable map used for {@code var()} resolution: stylesheet custom
     * properties first, then theme-derived values (which override, mirroring
     * "theme wins" for the well-known names).
     */
    private Map<String, String> buildVariables() {
        Map<String, String> vars = new HashMap<>(styleSheet.getRootVariables());
        Theme t = theme != null ? theme : Theme.current();
        if (t != null) {
            // Format as 6-digit hex (#RRGGBB): the engine's own theme colors
            // are opaque, and emitting #RRGGBBAA would be re-parsed with the
            // CSS Color 4 RGBA ordering (alpha last), swapping channels.
            vars.put("--bg", String.format("#%06X", t.getBackgroundColor() & 0xFFFFFF));
            vars.put("--fg", String.format("#%06X", t.getForegroundColor() & 0xFFFFFF));
            vars.put("--accent", String.format("#%06X", t.getAccentColor() & 0xFFFFFF));
            vars.put("--border", String.format("#%06X", t.getBorderColor() & 0xFFFFFF));
        }
        return vars;
    }
}
