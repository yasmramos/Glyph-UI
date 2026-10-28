package io.github.yasmramos.glyphui.style;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A parsed CSS selector supporting the Glyph UI subset:
 *
 * <pre>
 *   button              type selector (matches Component.getStyleTag())
 *   *                   universal selector (matches any style tag)
 *   .warning            class selector (matches Component.hasStyleClass)
 *   #submit             id selector (matches Component.getId())
 *   panel button        descendant combinator (any depth)
 *   panel > button      child combinator (direct parent only)
 *   :root               matches the tree root (variables-only convention)
 *   button:hover        pseudo-class mapped to ComponentState
 * </pre>
 *
 * Pseudo-classes: {@code :hover}, {@code :focus}, {@code :disabled},
 * {@code :active} (pressed) — evaluated against the component's current
 * state, not against a live mouse — plus the structural subset
 * {@code :first-child}, {@code :last-child}, {@code :only-child} and
 * {@code :nth-child(an+b | odd | even)}, which are evaluated against the
 * node's position among its parent's children at match time (item 5.11).
 * Structural pseudo-classes contribute class-level specificity, like in CSS.
 * {@code :root} is accepted as a
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
        boolean universal;              // "*" — matches any style tag
        final List<String> classes = new ArrayList<>();
        String id;                      // nullable
        final List<PseudoClass> pseudos = new ArrayList<>();
        /** Structural predicates (:first-child, :nth-child(...), …). */
        final List<StructuralPseudo> structural = new ArrayList<>();
        boolean rootMarker;             // ":root" — matches only the tree root

        /** True when this compound carries at least one structural test. */
        boolean hasStructural() {
            return !structural.isEmpty();
        }
    }

    /** Pseudo-classes mapped onto {@link io.github.yasmramos.glyphui.ui.ComponentState}. */
    public enum PseudoClass {
        HOVER, FOCUS, DISABLED, ACTIVE
    }

    /**
     * Structural pseudo-class predicate (item 5.11): evaluated against the
     * node's index among its parent's children rather than visual state.
     * Stored on the compound next to {@link SimpleSelector#pseudos}; kept
     * separate because it carries parameters ({@code nth}) and needs the
     * sibling context that {@link StyleNode#childIndex()} provides.
     */
    static final class StructuralPseudo {
        /** Kind of structural test. */
        enum Kind { FIRST_CHILD, LAST_CHILD, ONLY_CHILD, NTH_CHILD }

        final Kind kind;
        /** Step size {@code a} of the {@code an+b} formula (0 when unused). */
        final int step;
        /** Offset {@code b} of the {@code an+b} formula (1-based position). */
        final int offset;

        StructuralPseudo(Kind kind) {
            this(kind, 0, 0);
        }

        StructuralPseudo(Kind kind, int step, int offset) {
            this.kind = kind;
            this.step = step;
            this.offset = offset;
        }

        /**
         * Evaluates this predicate for a node.
         *
         * @param index   1-based child index (from {@link StyleNode#childIndex()})
         * @param siblings total number of children in the parent (0/1 unknown
         *                 tolerated: last/only-child degrade gracefully)
         * @return true when the node matches
         */
        boolean test(int index, int siblings) {
            return switch (kind) {
                case FIRST_CHILD -> index == 1;
                // Without sibling information there is no way to prove the
                // node is the last one; treat unknown counts conservatively.
                case LAST_CHILD -> siblings > 0 && index == siblings;
                case ONLY_CHILD -> siblings > 0 ? siblings == 1 : index == 1;
                case NTH_CHILD -> matchesNth(index);
            };
        }

        private boolean matchesNth(int index) {
            if (index <= 0) {
                return false; // unknown position: never match
            }
            if (step == 0) {
                return index == offset; // pure "b" form
            }
            // an+b with a possibly negative a: accept any non-negative n.
            int delta = index - offset;
            if (delta % step != 0) {
                return false;
            }
            return delta / step >= 0;
        }
    }

    /**
     * Parses an {@code nth} micro-expression: {@code odd}, {@code even}, a
     * plain 1-based integer {@code b}, or the general {@code an+b} form
     * (including {@code -n}, {@code 2n-1}, {@code n+3}).
     *
     * @param arg raw argument text between parentheses
     * @return the parsed predicate, or null when malformed
     */
    private static StructuralPseudo parseNth(String arg) {
        String s = arg.toLowerCase().replace(" ", "");
        if (s.isEmpty()) {
            return null;
        }
        if (s.equals("odd")) {
            return new StructuralPseudo(StructuralPseudo.Kind.NTH_CHILD, 2, 1);
        }
        if (s.equals("even")) {
            return new StructuralPseudo(StructuralPseudo.Kind.NTH_CHILD, 2, 2);
        }
        // Plain integer: "3", "+5".
        if (s.matches("[+-]?\\d+")) {
            try {
                return new StructuralPseudo(
                        StructuralPseudo.Kind.NTH_CHILD, 0, Integer.parseInt(s));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        // General an+b: optional sign + digits before 'n', optional signed b.
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("([+-]?\\d*)n([+-]\\d+)?").matcher(s);
        if (!m.matches()) {
            return null;
        }
        String aPart = m.group(1);
        int a;
        if (aPart == null || aPart.isEmpty()) {
            a = 1;
        } else if (aPart.equals("+")) {
            a = 1;
        } else if (aPart.equals("-")) {
            a = -1;
        } else {
            a = Integer.parseInt(aPart);
        }
        int b = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
        return new StructuralPseudo(StructuralPseudo.Kind.NTH_CHILD, a, b);
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
        // Reject unsupported syntax. '+' is only allowed inside a functional
        // pseudo-class argument (:nth-child(2n+1)), so it is scanned outside
        // balanced parentheses; the remaining characters are banned anywhere.
        int plusScan = 0;
        while ((plusScan = trimmed.indexOf('+', plusScan)) >= 0) {
            if (nthChildPlusContext(trimmed, plusScan) < 0) {
                throw new IllegalArgumentException(
                        "Unsupported selector syntax in \"" + trimmed
                                + "\" (combinator '+' / attributes / selector lists are not part of the subset)");
            }
            plusScan++;
        }
        for (char c : new char[]{'~', '[', ','}) {
            if (trimmed.indexOf(c) >= 0) {
                throw new IllegalArgumentException(
                        "Unsupported selector syntax in \"" + trimmed + "\" (combinator '"
                                + c + "' / attributes / selector lists are not part of the subset)");
            }
        }
        // Tokenize with a single regex pass so misplaced combinators are
        // detected from token positions ("a > > b" yields two consecutive
        // '>' tokens; a leading/trailing '>' lands at the ends). A functional
        // pseudo-class keeps its parenthesised argument glued to the name —
        // including spaces inside it, e.g. "li:nth-child(2n + 1)" is one token.
        List<String> tokens = new ArrayList<>();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile(">|[^\\s>]+(?:\\([^)]*\\))?").matcher(trimmed);
        while (m.find()) {
            String tok = m.group();
            // Whitespace inside a functional pseudo-class argument splits the
            // raw token stream ("li:nth-child(n + 4)" -> "li:nth-child(n",
            // "+", "4)"). Re-merge fragments until the open group is closed so
            // parseSimple always sees the whole compound as one token.
            if (!tokens.isEmpty() && hasUnclosedGroup(tokens.get(tokens.size() - 1))) {
                int last = tokens.size() - 1;
                tokens.set(last, tokens.get(last) + " " + tok);
            } else {
                tokens.add(tok);
            }
        }
        // Parenthesis validation is done over the raw text, not per token:
        // the tokenizer splits on whitespace, so "li:nth-child(n + 4)" yields
        // ["li:nth-child(n", "+", "4)"] even though it is valid nth syntax.
        // Every '(' must directly follow a functional pseudo-class name and
        // every such name must be followed by a terminated group.
        validateParentheses(trimmed);
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
            cls += s.classes.size() + s.pseudos.size() + s.structural.size();
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
        if (token.equals("*")) {
            // Universal selector: matches any style tag, zero specificity
            // contribution for the type part (per CSS spec).
            SimpleSelector star = new SimpleSelector();
            star.universal = true;
            return star;
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
            String typeName = token.substring(start, i);
            if ("*".equals(typeName)) {
                // Universal prefix of a compound, e.g. "*.big" or "*:hover".
                s.universal = true;
            } else {
                s.type = typeName.toLowerCase();
            }
        }
        while (i < token.length()) {
            char c = token.charAt(i);
            start = ++i;
            // A functional pseudo-class (:nth-child(...)) consumes its
            // parenthesised argument as part of this segment.
            if (c == ':' && i < token.length() && token.charAt(i) != '.') {
                int lookahead = token.indexOf('(', i);
                int nextMarker = nextMarkerIndex(token, i);
                if (lookahead >= 0 && (nextMarker < 0 || lookahead < nextMarker)) {
                    int close = token.indexOf(')', lookahead);
                    if (close < 0) {
                        throw new IllegalArgumentException(
                                "Unterminated functional pseudo-class in: " + token);
                    }
                    String name = token.substring(start, lookahead);
                    String arg = token.substring(lookahead + 1, close);
                    i = close + 1;
                    if (name.equalsIgnoreCase("nth-child")) {
                        StructuralPseudo nth = parseNth(arg);
                        if (nth == null) {
                            throw new IllegalArgumentException(
                                    "Invalid :nth-child argument \"" + arg + "\" in \"" + token + "\"");
                        }
                        s.structural.add(nth);
                        continue;
                    }
                    throw new IllegalArgumentException(
                            "Unsupported pseudo-class :" + name + " in \"" + token + "\"");
                }
            }
            while (i < token.length() && token.charAt(i) != '.' && token.charAt(i) != '#'
                    && token.charAt(i) != ':') {
                i++;
            }
            String name = token.substring(start, i);
            if (c == ':' && name.equalsIgnoreCase("root")) {
                s.rootMarker = true; // :root is a tree-position marker
                continue;
            }
            if (c == ':' && isStructuralName(name)) {
                s.structural.add(parseStructural(name, token));
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
        if (s.type == null && !s.universal
                && s.classes.isEmpty() && s.id == null
                && s.pseudos.isEmpty() && s.structural.isEmpty()) {
            throw new IllegalArgumentException("Empty simple selector: " + token);
        }
        return s;
    }

    /** Index of the next '.'/'#'/':' marker at or after {@code from}, or -1. */
    private static int nextMarkerIndex(String token, int from) {
        for (int k = from; k < token.length(); k++) {
            char ch = token.charAt(k);
            if (ch == '.' || ch == '#' || ch == ':') {
                return k;
            }
        }
        return -1;
    }

    /**
     * Checks whether a {@code '+'} occurring at {@code pos} in the selector
     * text is legal — i.e. it sits inside the argument of a functional
     * pseudo-class such as {@code :nth-child(2n+1)}. Returns the index of the
     * opening parenthesis of that pseudo-class, or -1 when the plus sign is
     * not enclosed by any parenthesised group (in which case it would be the
     * adjacent-sibling combinator, which the subset does not support).
     *
     * @param text the full selector text
     * @param pos  index of the '+' character
     * @return the '(' index of the enclosing group, or -1
     */
    private static int nthChildPlusContext(String text, int pos) {
        // Scan backwards tracking parenthesis depth: the first unmatched '('
        // before pos opens the group containing the '+'.
        int depth = 0;
        for (int k = pos - 1; k >= 0; k--) {
            char ch = text.charAt(k);
            if (ch == ')') {
                depth++;
            } else if (ch == '(') {
                if (depth == 0) {
                    return k;
                }
                depth--;
            }
        }
        return -1;
    }

    /**
     * Checks whether the '(' at {@code openPos} directly follows a functional
     * pseudo-class name, i.e. the text before it ends with {@code ":name"}
     * where name is identifier characters (letters, digits, '-', '_'). Used by
     * {@link #parse(String)} to allow {@code :nth-child(2n+1)} while rejecting
     * stray parentheses such as {@code button(2)}.
     *
     * @param text    the full selector text
     * @param openPos index of the '(' character
     * @return true when the parenthesis belongs to a functional pseudo-class
     */
    private static boolean isFunctionalPseudoName(String text, int openPos) {
        int k = openPos - 1;
        if (k < 0 || !isPseudoNameChar(text.charAt(k))) {
            return false;
        }
        while (k >= 0 && isPseudoNameChar(text.charAt(k))) {
            k--;
        }
        return k >= 0 && text.charAt(k) == ':';
    }

    /** True when {@code token} contains a '(' without its matching ')'. */
    private static boolean hasUnclosedGroup(String token) {
        int depth = 0;
        for (int k = 0; k < token.length(); k++) {
            char ch = token.charAt(k);
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
            }
        }
        return depth > 0;
    }

    /**
     * Validates parenthesis usage over the raw selector text. A '(' is legal
     * only when it directly follows a functional pseudo-class name (e.g.
     * {@code :nth-child(2n+1)}) and that group is terminated by ')'. A ')'
     * without a matching open group, or an unterminated group, is rejected.
     * This runs on the original text rather than per token because whitespace
     * inside a group ({@code nth-child(n + 4)}) splits tokens but stays valid.
     *
     * @param text the trimmed selector text
     * @throws IllegalArgumentException on stray or unbalanced parentheses
     */
    private static void validateParentheses(String text) {
        int depth = 0;
        for (int k = 0; k < text.length(); k++) {
            char ch = text.charAt(k);
            if (ch == '(') {
                if (depth > 0 || !isFunctionalPseudoName(text, k)) {
                    throw new IllegalArgumentException(
                            "Unsupported selector syntax in \"" + text + "\" (stray parenthesis)");
                }
                depth++;
            } else if (ch == ')') {
                if (depth == 0) {
                    throw new IllegalArgumentException(
                            "Unsupported selector syntax in \"" + text + "\" (stray parenthesis)");
                }
                depth--;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException(
                    "Unterminated functional pseudo-class in: " + text);
        }
    }

    private static boolean isPseudoNameChar(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '-' || ch == '_';
    }

    private static boolean isStructuralName(String name) {
        return name.equalsIgnoreCase("first-child")
                || name.equalsIgnoreCase("last-child")
                || name.equalsIgnoreCase("only-child");
    }

    private static StructuralPseudo parseStructural(String name, String token) {
        return switch (name.toLowerCase()) {
            case "first-child" -> new StructuralPseudo(
                    StructuralPseudo.Kind.FIRST_CHILD);
            case "last-child" -> new StructuralPseudo(
                    StructuralPseudo.Kind.LAST_CHILD);
            case "only-child" -> new StructuralPseudo(
                    StructuralPseudo.Kind.ONLY_CHILD);
            default -> throw new IllegalArgumentException(
                    "Unsupported pseudo-class :" + name + " in \"" + token + "\"");
        };
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
        // Universal selector ("*") matches every style tag; contributes no
        // type specificity (see the typ counter in parse()).
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
        // Structural pseudo-classes are evaluated against the node's position
        // among its parent's children (item 5.11). childIndex()/siblingCount()
        // default to 0 ("unknown") on StyleNode implementations without a real
        // parent, so predicates degrade conservatively there.
        if (s.hasStructural()) {
            int index = node.childIndex();
            int siblings = node.siblingCount();
            for (StructuralPseudo sp : s.structural) {
                if (!sp.test(index, siblings)) {
                    return false;
                }
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
