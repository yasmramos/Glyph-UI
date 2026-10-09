package com.glyphui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed CSS selector supporting the Glyph UI subset:
 *
 * <pre>
 *   button              type selector (matches Component.getStyleTag())
 *   .warning            class selector (matches Component.hasStyleClass)
 *   #submit             id selector (matches Component.getId())
 *   panel button        descendant combinator (any depth)
 *   panel > button      child combinator (direct parent only)
 *   :root               matches the tree root (variables-only convention)
 *   button:hover        pseudo-class mapped to ComponentState
 * </pre>
 *
 * Pseudo-classes: {@code :hover}, {@code :focus}, {@code :disabled},
 * {@code :active} (pressed). They are evaluated against the component's
 * current state, not against a live mouse. {@code :root} is accepted as a
 * selector-level marker for the top-most node (custom-property blocks).
 * A compound such as {@code :root .foo} is also valid: the {@code :root}
 * part pins matching to the tree root while the remaining parts constrain
 * descendants of it, following normal descendant/child semantics.
 *
 * <p>ID selectors ({@code #name}) are case-sensitive, like HTML ids; type
 * and class names are matched case-insensitively (lower-cased at parse
 * time) against the component's normalized style tag and style classes.</p>
 *
 * Specificity follows the classic CSS model as a tuple
 * {@code (idCount, classCount, typeCount)} compared lexicographically.
 */
public final class Selector {

    /** One compound selector segment, e.g. {@code button.big:hover}. */
    static final class SimpleSelector {
        String type;                    // nullable
        final List<String> classes = new ArrayList<>();
        String id;                      // nullable
        final List<PseudoClass> pseudos = new ArrayList<>();
        boolean rootMarker;             // ":root" — matches only the tree root
    }

    /** Pseudo-classes mapped onto {@link com.glyphui.ui.ComponentState}. */
    public enum PseudoClass {
        HOVER, FOCUS, DISABLED, ACTIVE
    }

    private final List<SimpleSelector> chain; // chain.get(0) == leftmost/ancestor
    /**
     * Per-link combinator: {@code childCombinators.get(i)} applies between
     * {@code chain.get(i)} and {@code chain.get(i+1)} (true = direct child
     * {@code >}, false = descendant).
     */
    private final List<Boolean> childCombinators;
    private final String source;
    private final int[] specificity;          // {ids, classes+pseudos, types}
    /**
     * True when the rightmost simple selector carries the {@code :root}
     * marker. Such selectors are position-anchored at the tree root, so
     * {@link #matches(List)} must not walk ancestors past index 0.
     */
    private final boolean anchoredAtRoot;

    private Selector(List<SimpleSelector> chain, List<Boolean> childCombinators,
                     String source, int[] specificity, boolean anchoredAtRoot) {
        this.chain = chain;
        this.childCombinators = childCombinators;
        this.source = source;
        this.specificity = specificity;
        this.anchoredAtRoot = anchoredAtRoot;
    }

    /**
     * Parses a selector string of the supported subset.
     *
     * @param text e.g. {@code "panel.toolbar > button.primary:hover"}
     * @return the compiled selector
     * @throws IllegalArgumentException when the selector uses unsupported
     *                                  syntax (sibling combinators,
     *                                  attribute selectors, commas…)
     */
    public static Selector parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Empty selector");
        }
        String trimmed = text.trim();
        for (char c : new char[]{'+', '~', '[', ','}) {
            if (trimmed.indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                        "Unsupported selector syntax in \"" + trimmed + "\" (combinator '"
                                + c + "' / attributes / selector lists are not part of the subset)");
            }
        }
        // Tokenize with a single regex pass so misplaced combinators are
        // detected from token positions ("a > > b" yields two consecutive
        // '>' tokens; a leading/trailing '>' lands at the ends).
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(">|[^\\s>]+").matcher(trimmed);
        while (m.find()) {
            tokens.add(m.group());
        }
        List<SimpleSelector> chain = new ArrayList<>();
        List<Boolean> links = new ArrayList<>(); // links.get(i): between i and i+1
        boolean pendingChild = false;            // a '>' seen since the last simple selector
        int ids = 0, cls = 0, typ = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String part = tokens.get(i);
            if (">".equals(part)) {
                if (chain.isEmpty() || i == tokens.size() - 1
                        || ">".equals(tokens.get(i + 1))) {
                    throw new IllegalArgumentException("Misplaced '>' in: " + trimmed);
                }
                // A link entry is appended only when the *next* simple
                // selector is parsed, so we cannot mutate one here; remember
                // the child combinator and apply it at that point instead.
                // (The old code did links.set(size-1, TRUE) here, which threw
                // IndexOutOfBoundsException on any "a > b" selector.)
                pendingChild = true;
                continue;
            }
            SimpleSelector s = parseSimple(part);
            if (!chain.isEmpty()) {
                links.add(pendingChild); // direct child after '>', descendant otherwise
            }
            pendingChild = false;
            if (s.id != null) ids++;
            cls += s.classes.size() + s.pseudos.size();
            if (s.rootMarker) cls++;
            if (s.type != null) typ++;
            chain.add(s);
        }
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("Empty selector: " + trimmed);
        }
        assert links.size() == chain.size() - 1
                : "combinator links must be one fewer than chain segments";
        return new Selector(Collections.unmodifiableList(chain),
                Collections.unmodifiableList(links), trimmed,
                new int[]{ids, cls, typ},
                chain.get(chain.size() - 1).rootMarker);
    }

    private static SimpleSelector parseSimple(String token) {
        // Special case: ":root" is a single marker pseudo-class with no type,
        // id or class part. Without this, the generic loop below would parse
        // an empty name and reject it as "empty simple selector".
        if (token.equalsIgnoreCase(":root")) {
            SimpleSelector root = new SimpleSelector();
            root.rootMarker = true;
            return root;
        }
        SimpleSelector s = new SimpleSelector();
        int i = 0;
        // Leading type name (identifier before first '.' '#' ':')
        int start = i;
        while (i < token.length() && token.charAt(i) != '.' && token.charAt(i) != '#'
                && token.charAt(i) != ':') {
            i++;
        }
        if (i > start) {
            s.type = token.substring(start, i).toLowerCase();
        }
        while (i < token.length()) {
            char c = token.charAt(i);
            start = ++i;
            while (i < token.length() && token.charAt(i) != '.' && token.charAt(i) != '#'
                    && token.charAt(i) != ':') {
                i++;
            }
            String name = token.substring(start, i);
            if (c == ':' && name.equalsIgnoreCase("root")) {
                s.rootMarker = true; // :root is a tree-position marker
                continue;
            }
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Malformed selector token: " + token);
            }
            switch (c) {
                // Type names and class names are lower-cased to match the
                // component's normalized styleTag()/style classes. IDs are
                // case-sensitive per HTML/CSS semantics, so '#id' keeps its
                // original casing and is compared verbatim against
                // Component.getId(). Pseudo-class names are matched
                // case-insensitively in parsePseudo.
                case '.' -> s.classes.add(name.toLowerCase());
                case '#' -> s.id = name;
                case ':' -> s.pseudos.add(parsePseudo(name.toLowerCase(), token));
                default -> throw new IllegalArgumentException("Malformed selector token: " + token);
            }
        }
        if (s.type == null && s.classes.isEmpty() && s.id == null && s.pseudos.isEmpty()) {
            throw new IllegalArgumentException("Empty simple selector: " + token);
        }
        return s;
    }

    private static PseudoClass parsePseudo(String name, String token) {
        return switch (name) {
            case "hover" -> PseudoClass.HOVER;
            case "focus" -> PseudoClass.FOCUS;
            case "disabled" -> PseudoClass.DISABLED;
            case "active" -> PseudoClass.ACTIVE;
            default -> throw new IllegalArgumentException(
                    "Unsupported pseudo-class :" + name + " in \"" + token + "\"");
        };
    }

    /**
     * Matches this selector against a node path ordered root → leaf (the
     * rightmost element being the candidate). Descendant links may skip
     * ancestors; child links ('>') require immediate adjacency.
     *
     * @param path the ancestor chain ending with the candidate itself
     * @return true when the candidate (and its ancestors) match
     */
    public boolean matches(List<StyleNode> path) {
        if (path.isEmpty()) {
            return false;
        }
        // Rightmost simple selector must match the last path element.
        if (!matchesSimple(chain.get(chain.size() - 1), path.get(path.size() - 1))) {
            return false;
        }
        // A selector whose RIGHTMOST compound is ":root" is anchored to the
        // tree root: only the single-node path [root] can match it. Without
        // this guard, matchesSimple's parent==null test passes for every node
        // in a fake/partial path, making ":root" match anywhere. (The intent
        // "button inside the root element" is expressed by putting :root on
        // the LEFT segment — e.g. ":root button" — which the path-bounded
        // walk below handles correctly.)
        if (anchoredAtRoot) {
            return path.size() == 1;
        }
        // Remaining chain segments must map onto a strictly decreasing
        // sequence of ancestor indices honouring each combinator. The
        // descendant link greedily picks the first match scanning upward,
        // but that choice can strand an earlier '>' link (e.g. "div > .x y"
        // against [div, span.x, div, button.y] would otherwise wrongly
        // match by binding 'y' to the outer div). Backtracking here keeps
        // matching correct for mixed combinators at negligible cost — the
        // chain length is tiny and paths are shallow.
        return matchChain(path, chain.size() - 2, path.size() - 2);
    }

    /**
     * Recursive right-to-left matcher with backtracking.
     *
     * @param path root → leaf node path
     * @param ci   index of the next chain segment to place
     * @param pi   highest path index still available for {@code ci}
     * @return true when every remaining segment fits
     */
    private boolean matchChain(List<StyleNode> path, int ci, int pi) {
        if (ci < 0) {
            return true; // all segments placed
        }
        boolean childLink = childCombinators.get(ci);
        if (childLink) {
            // '>' requires immediate adjacency with the already-placed
            // segment on the right (at pi + 1), so only path[pi] qualifies.
            return pi >= 0
                    && matchesSimple(chain.get(ci), path.get(pi))
                    && matchChain(path, ci - 1, pi - 1);
        }
        // Descendant: try every ancestor position from nearest to farthest;
        // on failure continue with the next candidate (backtracking).
        for (int k = pi; k >= 0; k--) {
            if (matchesSimple(chain.get(ci), path.get(k))
                    && matchChain(path, ci - 1, k - 1)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesSimple(SimpleSelector s, StyleNode node) {
        if (s.rootMarker && node.parent() != null) {
            return false; // :root only matches the top-most node
        }
        if (s.type != null && !s.type.equals(node.styleTag())) {
            return false;
        }
        if (s.id != null && !s.id.equals(node.id())) {
            return false;
        }
        for (String c : s.classes) {
            if (!node.hasClass(c)) {
                return false;
            }
        }
        for (PseudoClass p : s.pseudos) {
            if (!node.matchesPseudo(p)) {
                return false;
            }
        }
        return true;
    }

    /**
     * CSS-style specificity as {@code (ids, classesAndPseudos, types)}.
     *
     * @return an array of three ints
     */
    public int[] getSpecificity() {
        return specificity.clone();
    }

    /**
     * Compares specificity with another selector.
     *
     * @param other the other selector
     * @return negative/zero/positive like a comparator
     */
    public int compareSpecificity(Selector other) {
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(specificity[i], other.specificity[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    /**
     * The original selector text.
     *
     * @return source string
     */
    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return source;
    }
}
