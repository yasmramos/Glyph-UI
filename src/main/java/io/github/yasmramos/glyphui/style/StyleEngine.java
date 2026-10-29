package io.github.yasmramos.glyphui.style;

import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.graphics.FontManager;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Panel;

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
 * {@link Theme} through {@code var(--accent)} and friends. CSS wins: a
 * stylesheet that declares one of these names (typically under {@code :root})
 * overrides the theme-provided default.</p>
 *
 * <p>Note: {@code :root} is a variable-scope only — it contributes custom
 * properties to the sheet-wide map but produces no cascade rule, so regular
 * declarations like {@code :root { color: red }} are intentionally ignored.
 * Style the root component with the {@code panel} tag selector instead.</p>
 *
 * <h2>State re-evaluation</h2>
 * Pseudo-class rules ({@code :hover}, {@code :focus}, {@code :disabled},
 * {@code :active}) depend on the mutable {@link io.github.yasmramos.glyphui.ui.ComponentState}.
 * Rather than tracking state changes per component, {@code Application} calls
 * {@link #apply(Component)} once per frame before rendering — the tree is
 * small and recomputation is cheap. Alternatively callers may invoke
 * {@code apply} whenever a state change occurs.
 */
public final class StyleEngine {

    private StyleSheet styleSheet;
    private Theme theme;
    /**
     * Per-rule resolved-style cache (keyed by {@link StyleSheet#ruleId}).
     * Reused across {@link #apply(Component)} passes — the dominant cost it
     * avoids is re-running {@code resolveVars} on every frame for rules that
     * carry unresolved {@code var()} declarations. Cleared whenever the
     * stylesheet or the variable-affecting theme changes, so stale entries
     * can never be served.
     */
    private final Map<Integer, Style> resolvedCache = new HashMap<>();
    /** Last theme identity seen while building variables (change detection). */
    private Theme lastThemeSource;
    /**
     * Identity of the stylesheet whose rules populate {@link #resolvedCache}
     * (Block 5 item 5.24 change detection). The cache is keyed by rule id,
     * which is only unique <em>within</em> one sheet — two different sheets
     * both start their ids at 0 — so the engine must know which sheet the
     * cached entries belong to and drop them as soon as that changes.
     */
    private StyleSheet cachedSheetSource;

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
     * Replaces the current stylesheet. The per-rule resolved cache is keyed
     * by rule identity, so it is cleared here to avoid holding styles (and
     * rule references) from the discarded sheet.
     *
     * @param styleSheet the new stylesheet
     */
    public void setStyleSheet(StyleSheet styleSheet) {
        this.styleSheet = styleSheet != null ? styleSheet : StyleSheet.empty();
        // Block 5 item 5.24: drop the per-rule cache eagerly whenever the
        // sheet reference changes, even when it is set to the very instance
        // already in use. StyleSheet is immutable today, but a future mutable
        // sheet could be "replaced" with the same object after edits; without
        // clearing here the engine would keep serving stale resolved styles.
        this.resolvedCache.clear();
        this.cachedSheetSource = null;
        // A new sheet changes selector matching for the whole tree.
        io.github.yasmramos.glyphui.ui.Component.invalidateStyle();
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
     * <p>This overload uses the active theme ({@link Theme#current()}) for
     * {@code var()} resolution. Note that theme-derived custom properties
     * such as {@code --bg} carry non-zero defaults in several components, so
     * a mismatching theme can silently override intended colors. Prefer
     * {@link #apply(Component, StyleSheet, Theme)} in tests and anywhere
     * the expected theme is known.</p>
     *
     * @param root       the subtree root component
     * @param styleSheet the stylesheet to apply
     */
    public static void apply(Component root, StyleSheet styleSheet) {
        apply(root, styleSheet, Theme.current());
    }

    /**
     * Convenience entry point with an explicit theme source for custom
     * property resolution ({@code var(--bg)}, {@code var(--accent)}, ...).
     *
     * @param root       the subtree root component
     * @param styleSheet the stylesheet to apply
     * @param theme      the theme providing derived variables (not null)
     */
    public static void apply(Component root, StyleSheet styleSheet, Theme theme) {
        new StyleEngine(styleSheet, theme).apply(root);
    }

    /**
     * Runs a full cascade pass over the subtree rooted at {@code root}.
     *
     * <p>Before cascading, each component's stored computed style is reset
     * to empty. Without this, a second pass over the same tree would merge
     * stale values from the previous pass into the fresh one (the merge in
     * {@code applyRecursive} treats the previously stored style as the
     * lowest-priority layer), leaking e.g. padding or margin declarations
     * onto nodes whose selectors no longer match.</p>
     *
     * @param root the subtree root (no-op when null)
     */
    public void apply(Component root) {
        if (root == null) {
            return;
        }
        // Block 5 item 5.24: the per-rule cache is keyed by StyleSheet#ruleId,
        // which restarts at 0 for every sheet, so ids from two different sheets
        // collide. The setStyleSheet() clear alone only covers engines whose
        // sheet is swapped through that setter — a static apply(root, sheet…)
        // pass builds a throwaway engine per call, and any future path that
        // assigns styleSheet directly would leak entries from the previous
        // sheet under identical ids. Track the sheet identity the cached
        // entries belong to and drop them whenever it differs from the one
        // about to be applied. StyleSheet is immutable today, so this is
        // defense against id collisions across sheets (and against a sheet
        // being "re-set" as the same mutated instance in the future).
        if (cachedSheetSource != styleSheet) {
            resolvedCache.clear();
            cachedSheetSource = styleSheet;
        }
        resetComputedStyles(root);
        Map<String, String> variables = buildVariables();
        // Theme-derived variables are not known at parse time, so rules that
        // reference var(--accent) & friends keep their raw declaration text
        // under StyleProperty.RAW_VALUES. Re-parse those lazily with this
        // engine's variable map (results cached per rule identity). The cache
        // is kept across passes and keyed by StyleSheet#ruleId, which is
        // stable for the lifetime of an immutable stylesheet; entries from a
        // replaced sheet are dropped eagerly on setStyleSheet() and via the
        // sheet-identity check above.
        applyRecursive(root, null, variables, resolvedCache);
    }

    /**
     * Clears every stored computed style in the subtree so the upcoming
     * cascade starts from a clean slate. Inline styles are untouched — they
     * live separately and always win via {@code Component#getComputedStyle()}.
     *
     * @param component the subtree root
     */
    private static void resetComputedStyles(Component component) {
        component.setComputedStyle(Style.EMPTY);
        if (component instanceof Panel panel) {
            for (Component child : new ArrayList<>(panel.getChildren())) {
                resetComputedStyles(child);
            }
        }
    }

    private void applyRecursive(Component component, Style parentComputed,
                                Map<String, String> variables,
                                Map<Integer, Style> resolvedCache) {
        List<StyleNode> path = buildPath(component);
        Style.Builder builder = Style.builder();
        // Cascade: matchingRules returns lowest-priority first, merge in order.
        // RAW_VALUES is a synthetic per-rule carrier of unresolved var()
        // declarations; it must never leak typed values from a lower-priority
        // rule into a higher-priority merge (see Style#withoutRawValues).
        for (StyleSheet.Rule rule : styleSheet.matchingRules(path)) {
            int id = rule.id();
            // resolveVars() drops RAW_VALUES and folds unresolved var()
            // declarations into typed values; the result is a clean style.
            Style resolved = resolvedCache.computeIfAbsent(id,
                    i -> rule.style().resolveVars(variables));
            builder.putAll(resolved);
        }
        // Inheritance source for this node's children. Only the stylesheet
        // cascade result is inherited — NOT the inline style overlay. Inline
        // declarations live solely on the component itself (applied at read
        // time via Component#getEffectiveStyle); inheriting them would leak
        // non-inherited box values (width/height seeded by constructors) into
        // descendants, where they would out-rank ancestor stylesheet rules
        // and break child/descendant combinators for grandchildren.
        Style cascaded = builder.build();
        Style inheritBase = cascaded.inheritFrom(parentComputed);
        component.setComputedStyle(inheritBase);

        // Highest cascade priority after the sheet: the component's own
        // inline style overrides matched rules for exactly the properties it
        // declares (CSS semantics). Applied last so it never propagates.
        Style inline = component.getInlineStyle();
        if (inline != null && !inline.isEmpty()) {
            component.setComputedStyle(inheritBase.overrideWith(inline));
        }

        if (component instanceof Panel panel) {
            // Iterate over a snapshot: the children list is mutable and a
            // widget reacting to its newly computed style could add or remove
            // children mid-cascade (ConcurrentModificationException otherwise).
            for (Component child : new ArrayList<>(panel.getChildren())) {
                applyRecursive(child, inheritBase, variables, resolvedCache);
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
     * Variable map used for {@code var()} resolution: theme-derived defaults
     * first, then stylesheet custom properties (which override). CSS wins:
     * a user's {@code :root { --accent: ... }} declaration takes precedence
     * over the theme-provided default for the well-known names.
     */
    private Map<String, String> buildVariables() {
        Map<String, String> vars = new HashMap<>();
        Theme t = theme != null ? theme : Theme.current();
        // Cached resolutions depend on the theme-derived variables; if the
        // active theme instance changed since the last pass, drop the cache
        // so no stale var() result is served.
        if (t != lastThemeSource) {
            resolvedCache.clear();
            lastThemeSource = t;
        }
        if (t != null) {
            // Format as 6-digit hex (#RRGGBB): the engine's own theme colors
            // are opaque, and emitting #RRGGBBAA would be re-parsed with the
            // CSS Color 4 RGBA ordering (alpha last), swapping channels.
            vars.put("--bg", String.format("#%06X", t.getBackgroundColor() & 0xFFFFFF));
            vars.put("--fg", String.format("#%06X", t.getForegroundColor() & 0xFFFFFF));
            vars.put("--accent", String.format("#%06X", t.getAccentColor() & 0xFFFFFF));
            vars.put("--border", String.format("#%06X", t.getBorderColor() & 0xFFFFFF));
        }
        // Stylesheet custom properties override the theme defaults, so users
        // can re-define --bg/--fg/--accent/--border from CSS (:root or any
        // other block — custom properties are collected sheet-wide).
        vars.putAll(styleSheet.getRootVariables());
        // Unit references for the relative length units (em/rem/vw/vh) are
        // seeded last with environment values, but a stylesheet may still
        // override them via :root custom properties (checked explicitly so
        // putAll above is not clobbered by Map.put).
        seedUnitReferences(vars);
        return vars;
    }

    /**
     * Seeds {@code --font-size-base}, {@code --root-font-size},
     * {@code --viewport-width} and {@code --viewport-height} when the
     * stylesheet does not define them. These variables provide the reference
     * sizes that {@link CssValues#parseLength(String, Map)} uses to resolve
     * {@code em}/{@code rem} (body font size from {@link FontManager}) and
     * {@code vw}/{@code vh} (window size from the running
     * {@link Application}). Outside an application context (headless tests,
     * embedded usage without a window) the viewport entries are omitted and
     * viewport units degrade to their documented numeric fallback.
     */
    private static void seedUnitReferences(Map<String, String> vars) {
        if (!vars.containsKey("--font-size-base") && !vars.containsKey("--root-font-size")) {
            float body = FontManager.getDefaultFontSize(Theme.FontRole.BODY);
            String px = body + "px";
            vars.put("--font-size-base", px);
            vars.put("--root-font-size", px);
        } else if (!vars.containsKey("--font-size-base")) {
            vars.put("--font-size-base", vars.get("--root-font-size"));
        } else if (!vars.containsKey("--root-font-size")) {
            vars.put("--root-font-size", vars.get("--font-size-base"));
        }
        if (!vars.containsKey("--viewport-width") || !vars.containsKey("--viewport-height")) {
            Application app = Application.getCurrent();
            if (app != null && app.getWindow() != null) {
                vars.putIfAbsent("--viewport-width", app.getWindow().getWidth() + "px");
                vars.putIfAbsent("--viewport-height", app.getWindow().getHeight() + "px");
            }
        }
    }
}
