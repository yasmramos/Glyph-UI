package com.glyphui.style;

import com.helger.css.ECSSVersion;
import com.helger.css.decl.CSSDeclaration;
import com.helger.css.decl.CSSExpressionMemberTermSimple;
import com.helger.css.decl.CSSImportRule;
import com.helger.css.decl.CSSSelector;
import com.helger.css.decl.CSSStyleRule;
import com.helger.css.decl.CascadingStyleSheet;
import com.helger.css.reader.CSSReader;
import com.helger.css.writer.CSSWriterSettings;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A parsed stylesheet: a list of {@code (selector -> Style)} rules plus the
 * custom properties ({@code --name: value}) declared under {@code :root} for
 * {@code var(--name)} resolution against the active theme.
 *
 * <p>Parsing is delegated to <b>ph-css</b>. Declaration values are mapped to
 * the supported {@link StyleProperty} set; anything unsupported is skipped
 * with a warning (the documented subset lives in README.md).</p>
 *
 * <p>The cascade implemented by {@link #computeMatches(StyleNode)} follows
 * CSS ordering: rules are applied in ascending specificity, ties broken by
 * source order — later/higher-specificity wins.</p>
 */
public final class StyleSheet {

    /** One selector + its declaration block. */
    public record Rule(Selector selector, Style style, int order) {
    }

    private final List<Rule> rules;
    private final Map<String, String> rootVariables;
    private final Set<String> unsupportedProperties;

    private StyleSheet(List<Rule> rules, Map<String, String> rootVariables,
                       Set<String> unsupportedProperties) {
        this.rules = Collections.unmodifiableList(rules);
        this.rootVariables = Collections.unmodifiableMap(rootVariables);
        this.unsupportedProperties = Collections.unmodifiableSet(unsupportedProperties);
    }

    /**
     * An empty stylesheet.
     *
     * @return a stylesheet with no rules
     */
    public static StyleSheet empty() {
        return new StyleSheet(List.of(), Map.of(), Set.of());
    }

    /**
     * Parses CSS text into a stylesheet.
     *
     * @param css the CSS source text
     * @return the parsed stylesheet
     * @throws StyleParseException when the CSS itself fails to parse or a
     *                              selector uses unsupported syntax
     */
    public static StyleSheet parse(String css) {
        CascadingStyleSheet sheet = CSSReader.readFromString(css, StandardCharsets.UTF_8,
                ECSSVersion.CSS30);
        if (sheet == null) {
            throw new StyleParseException("Failed to parse CSS source");
        }
        List<Rule> rules = new ArrayList<>();
        Map<String, String> variables = new HashMap<>();
        Set<String> unsupported = new LinkedHashSet<>();
        // Two passes: custom properties (--vars) are collected first so that
        // var(...) references in any rule resolve regardless of declaration
        // order.
        collectVariables(sheet, variables);
        int order = 0;
        for (int i = 0; i < sheet.getStyleRuleCount(); i++) {
            CSSStyleRule sr = sheet.getStyleRuleAtIndex(i);
            // ph-css keeps comma-separated selectors as separate members.
            for (int sIdx = 0; sIdx < sr.getSelectorCount(); sIdx++) {
                String selText = selectorToString(sr.getSelectorAtIndex(sIdx));
                Selector selector;
                try {
                    selector = Selector.parse(selText);
                } catch (IllegalArgumentException e) {
                    throw new StyleParseException(e.getMessage(), e);
                }
                if (":root".equals(selText.trim())) {
                    // :root contributes only custom properties (collected in
                    // pass 1); it produces no cascade rule of its own.
                    continue;
                }
                Style.Builder builder = Style.builder();
                Map<StyleProperty, String> rawValues = new EnumMap<>(StyleProperty.class);
                for (int d = 0; d < sr.getDeclarationCount(); d++) {
                    CSSDeclaration decl = sr.getDeclarationAtIndex(d);
                    String prop = decl.getProperty().toLowerCase(Locale.ROOT);
                    if (prop.startsWith("--")) {
                        continue; // already captured as a variable
                    }
                    String rawValue = decl.getExpressionAsCSSString();
                    StyleProperty target = propertyFor(prop);
                    if (target == null) {
                        if (unsupported.add(prop)) {
                            System.err.println("[GlyphUI] Warning: unsupported CSS property \""
                                    + prop + "\" (" + rawValue + ") ignored");
                        }
                        continue;
                    }
                    Object typed = CssValues.parse(prop, rawValue, variables);
                    if (typed == null && rawValue.contains("var(")) {
                        // var() references against theme-derived variables are
                        // only resolvable at cascade time by StyleEngine; keep
                        // the raw text so it can be re-parsed per engine.
                        rawValues.put(target, rawValue);
                        continue;
                    }
                    if (typed == null) {
                        // Supported property name but unparseable/unsupported
                        // value form (e.g. 4-value padding shorthand).
                        if (unsupported.add(prop)) {
                            System.err.println("[GlyphUI] Warning: unsupported CSS property \""
                                    + prop + "\" (" + rawValue + ") ignored");
                        }
                        continue;
                    }
                    builder.set(target, typed);
                }
                if (!rawValues.isEmpty()) {
                    builder.set(StyleProperty.RAW_VALUES,
                            Collections.unmodifiableMap(rawValues));
                }
                rules.add(new Rule(selector, builder.build(), order++));
            }
        }
        for (int i = 0; i < sheet.getImportRuleCount(); i++) {
            CSSImportRule ir = sheet.getImportRuleAtIndex(i);
            System.err.println("[GlyphUI] Warning: @import \""
                    + ir.getLocationString() + "\" not supported, skipped");
        }
        rules.sort(Comparator.comparingInt(Rule::order));
        return new StyleSheet(rules, variables, unsupported);
    }

    private static void collectVariables(CascadingStyleSheet sheet,
                                         Map<String, String> variables) {
        for (int i = 0; i < sheet.getStyleRuleCount(); i++) {
            CSSStyleRule sr = sheet.getStyleRuleAtIndex(i);
            for (int d = 0; d < sr.getDeclarationCount(); d++) {
                CSSDeclaration decl = sr.getDeclarationAtIndex(d);
                String prop = decl.getProperty().toLowerCase(Locale.ROOT);
                if (prop.startsWith("--")) {
                    variables.put(prop, decl.getExpressionAsCSSString().trim());
                }
            }
        }
    }

    /**
     * The parsed rules in source order.
     *
     * @return unmodifiable rule list
     */
    public List<Rule> getRules() {
        return rules;
    }

    /**
     * Custom properties declared anywhere in the sheet (typically under
     * {@code :root}), keyed by their {@code --name}.
     *
     * @return unmodifiable variable map
     */
    public Map<String, String> getRootVariables() {
        return rootVariables;
    }

    /**
     * Properties encountered during parsing that are outside the supported
     * subset and were dropped (each warned once at parse time).
     *
     * @return unmodifiable set of property names
     */
    public Set<String> getUnsupportedProperties() {
        return unsupportedProperties;
    }

    /**
     * Finds every rule matching a node and returns them ordered so that the
     * <b>last</b> entry has the highest priority (specificity first, then
     * source order). Callers merge in iteration order.
     *
     * @param node the node to test
     * @return matching rules, lowest priority first
     */
    public List<Rule> matchingRules(StyleNode node) {
        return matchingRules(buildPath(node));
    }

    /**
     * Same as {@link #matchingRules(StyleNode)} but taking a precomputed
     * root → leaf ancestor path (used by {@code StyleEngine} which builds
     * paths iteratively while walking the tree).
     *
     * @param path the ancestor chain ending with the candidate
     * @return matching rules, lowest priority first
     */
    public List<Rule> matchingRules(List<StyleNode> path) {
        // Standard right-to-left matching over the root → leaf path built by
        // StyleEngine (each compound must match some ancestor, '>' requires
        // immediate adjacency).
        List<Rule> matched = new ArrayList<>();
        for (Rule r : rules) {
            if (r.selector().matches(path)) {
                matched.add(r);
            }
        }
        matched.sort(Comparator.<Rule>comparingInt(r -> weight(r.selector()))
                .thenComparingInt(Rule::order));
        return matched;
    }

    /**
     * Flattens a selector's specificity into a single comparable number
     * (base-100 encoding, consistent with CSS (a,b,c) ordering).
     */
    private static int weight(Selector selector) {
        int[] s = selector.getSpecificity();
        return s[0] * 10000 + s[1] * 100 + s[2];
    }

    private static List<StyleNode> buildPath(StyleNode node) {
        List<StyleNode> path = new ArrayList<>();
        for (StyleNode n = node; n != null; n = n.parent()) {
            path.add(n);
        }
        Collections.reverse(path);
        return path;
    }

    /**
     * Maps a CSS property name onto its {@link StyleProperty} enum constant.
     * Supports the shorthands {@code border}, {@code border-radius} aliases
     * and the {@code flex} shorthand (grow only in this subset).
     *
     * @param prop lower-case property name
     * @return the enum constant
     */
    static StyleProperty propertyFor(String prop) {
        return switch (prop) {
            case "color" -> StyleProperty.COLOR;
            case "background", "background-color" -> StyleProperty.BACKGROUND;
            case "accent-color" -> StyleProperty.ACCENT_COLOR;
            case "border-color" -> StyleProperty.BORDER_COLOR;
            case "border-width" -> StyleProperty.BORDER_WIDTH;
            case "border", "border-radius" ->
                    prop.equals("border") ? StyleProperty.BORDER_WIDTH : StyleProperty.BORDER_RADIUS;
            case "padding" -> StyleProperty.PADDING;
            case "margin" -> StyleProperty.MARGIN;
            case "width" -> StyleProperty.WIDTH;
            case "height" -> StyleProperty.HEIGHT;
            case "display" -> StyleProperty.DISPLAY;
            case "font-family" -> StyleProperty.FONT_FAMILY;
            case "font-size" -> StyleProperty.FONT_SIZE;
            case "font-weight" -> StyleProperty.FONT_WEIGHT;
            case "opacity" -> StyleProperty.OPACITY;
            case "flex-direction" -> StyleProperty.FLEX_DIRECTION;
            case "justify-content" -> StyleProperty.JUSTIFY_CONTENT;
            case "align-items" -> StyleProperty.ALIGN_ITEMS;
            case "gap" -> StyleProperty.GAP;
            case "flex-grow", "flex" -> StyleProperty.FLEX_GROW;
            default -> null; // unknown property: caller decides (warn/skip)
        };
    }

    private static String selectorToString(CSSSelector selector) {
        // CSSWriterSettings implements ICSSWriterSettings; a small indent is fine.
        return selector.getAsCSSString(new CSSWriterSettings(ECSSVersion.CSS30), 0).trim();
    }

    /** Thrown when CSS cannot be parsed into the supported model. */
    public static class StyleParseException extends RuntimeException {
        public StyleParseException(String message) {
            super(message);
        }

        public StyleParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
