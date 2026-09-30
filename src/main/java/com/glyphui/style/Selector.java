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

    private Selector(List<SimpleSelector> chain, List<Boolean> childCombinators,
                     String source, int[] specificity) {
        this.chain = chain;
        this.childCombinators = childCombinators;
        this.source = source;
        this.specificity = specificity;
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
        // Tokenize: each '>' is its own token; everything else splits on
        // whitespace. Links between adjacent simple selectors are child ('>')
        // or descendant (whitespace).
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(">|[^\\s>]+").matcher(trimmed);
        while (m.find()) {
            tokens.add(m.group());
        }
        List<SimpleSelector> chain = new ArrayList<>();
        List<Boolean> links = new ArrayList<>(); // links.get(i): between i and i+1
        int ids = 0, cls = 0, typ = 0;
        for (int i = 0; i < tokens.size(); i++) {
            String part = tokens.get(i);
            if (">".equals(part)) {
                if (chain.isEmpty() || i == tokens.size() - 1) {
                    throw new IllegalArgumentException("Misplaced '>' in: " + trimmed);
                }
                links.set(links.size() - 1, Boolean.TRUE);
                continue;
            }
            SimpleSelector s = parseSimple(part);
            if (!chain.isEmpty()) {
                links.add(Boolean.FALSE); // descendant until '>' upgrades it
            }
            if (s.id != null) ids++;
            cls += s.classes.size() + s.pseudos.size();
            if (s.rootMarker) cls++;
            if (s.type != null) typ++;
            chain.add(s);
        }
        if (chain.isEmpty()) {
            throw new IllegalArgumentException("Empty selector: " + trimmed);
        }
        return new Selector(Collections.unmodifiableList(chain),
                Collections.unmodifiableList(links), trimmed,
                new int[]{ids, cls, typ});
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
            String name = token.substring(start, i).toLowerCase();
            if (c == ':' && name.equals("root")) {
                s.rootMarker = true; // :root is a tree-position marker
                continue;
            }
            if (name.isEmpty()) {
                throw new IllegalArgumentException("Malformed selector token: " + token);
            }
            switch (c) {
                case '.' -> s.classes.add(name);
                case '#' -> s.id = name;
                case ':' -> s.pseudos.add(parsePseudo(name, token));
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
        int ci = chain.size() - 1;
        if (!matchesSimple(chain.get(ci), path.get(path.size() - 1))) {
            return false;
        }
        ci--;
        int pi = path.size() - 2;
        // Walk remaining ancestors from right to left.
        while (ci >= 0) {
            boolean childLink = childCombinators.get(ci);
            if (childLink) {
                // Must match the immediate parent.
                if (pi < 0 || !matchesSimple(chain.get(ci), path.get(pi))) {
                    return false;
                }
                pi--;
                ci--;
            } else {
                // Descendant: scan upward until a match is found.
                boolean found = false;
                while (pi >= 0) {
                    if (matchesSimple(chain.get(ci), path.get(pi))) {
                        found = true;
                        pi--;
                        break;
                    }
                    pi--;
                }
                if (!found) {
                    return false;
                }
                ci--;
            }
        }
        return true;
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
