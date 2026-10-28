package io.github.yasmramos.glyphui.style;

import io.github.yasmramos.glyphui.style.StyleNode;
import io.github.yasmramos.glyphui.style.Selector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link Selector} parsing and matching, using a fake
 * {@link StyleNode} implementation so no rendering context or style engine
 * is required. Covers the child-combinator fix (pendingChild flag), misplaced
 * '>' validation, descendant vs child semantics, and mixed chains such as
 * "a b > c".
 */
class SelectorTest {

    /** Minimal in-memory node: just a tag plus optional classes/id/pseudos. */
    private record FakeNode(String styleTag, String id, List<String> classes)
            implements StyleNode {
        @Override
        public boolean hasClass(String className) {
            return classes.contains(className);
        }

        @Override
        public boolean matchesPseudo(Selector.PseudoClass pseudo) {
            return false;
        }

        @Override
        public StyleNode parent() {
            return null; // paths are supplied explicitly to matches(...)
        }
    }

    /** Builds a root-first path from tag names, e.g. path("div", "button"). */
    private static List<StyleNode> path(String... tags) {
        List<StyleNode> nodes = new ArrayList<>();
        for (String t : tags) {
            nodes.add(new FakeNode(t, "", List.of()));
        }
        return nodes;
    }

    // ------------------------------------------------------------------
    // Parsing
    // ------------------------------------------------------------------

    @Test
    void childCombinatorParsesWithoutException() {
        assertDoesNotThrow(() -> Selector.parse("div > button"));
        assertDoesNotThrow(() -> Selector.parse("panel.toolbar > button.primary:hover"));
        assertDoesNotThrow(() -> Selector.parse("a b > c"));
    }

    @Test
    void misplacedChildCombinatorThrows() {
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("> button"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("div >"));
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("a > > b"));
    }

    // ------------------------------------------------------------------
    // Matching: direct child vs grandchild
    // ------------------------------------------------------------------

    @Test
    void childCombinatorMatchesDirectChildOnly() {
        Selector sel = Selector.parse("div > button");
        assertTrue(sel.matches(path("div", "button")),
                "direct child must match");
        assertTrue(sel.matches(path("x", "div", "button")),
                "last two nodes being div,button must match regardless of ancestors");
        assertTrue(sel.matches(path("div", "div", "button")),
                "the button's direct parent is a div, so it matches (CSS semantics)");
        assertFalse(sel.matches(path("div", "div", "span", "button")),
                "grandchild under an extra span must NOT match");
        assertFalse(sel.matches(path("div", "span", "button")),
                "grandchild through a span must NOT match");
    }

    @Test
    void descendantCombinatorMatchesGrandchild() {
        Selector sel = Selector.parse("div button");
        assertTrue(sel.matches(path("div", "button")));
        assertTrue(sel.matches(path("div", "div", "button")),
                "descendant combinator spans any depth");
        assertTrue(sel.matches(path("div", "span", "button")));
    }

    @Test
    void mixedChainRequiresAdjacencyOnlyForChildLink() {
        Selector sel = Selector.parse("a b > c");
        // 'b' must be the DIRECT parent of 'c'; 'a' may be any ancestor of 'b'.
        assertTrue(sel.matches(path("a", "b", "c")));
        assertTrue(sel.matches(path("a", "x", "b", "c")),
                "descendant link a..b tolerates intermediate nodes");
        assertFalse(sel.matches(path("a", "b", "x", "c")),
                "child link b>c forbids an intermediate node");
        assertFalse(sel.matches(path("b", "c")),
                "missing ancestor 'a'");
    }

    @Test
    void childCombinatorAppliesToCorrectEdge() {
        // In "a b > c" the '>' binds b->c, not a->b. A path where a is the
        // direct parent of b but b is NOT the direct parent of c must fail.
        Selector sel = Selector.parse("a b > c");
        assertFalse(sel.matches(path("a", "b", "x", "c")));
        // And the reverse reading (treating '>' as a->b) would also fail here,
        // proving the edge assignment:
        assertTrue(Selector.parse("a > b c").matches(path("a", "b", "x", "c")),
                "'>' in \"a > b c\" binds the a->b edge instead");
    }

    @Test
    void classAndIdSelectorsThroughFakeNodes() {
        List<StyleNode> p = List.of(
                new FakeNode("div", "", List.of("card")),
                new FakeNode("button", "submit", List.of("primary")));
        assertTrue(Selector.parse("div.card > button#submit").matches(p));
        assertTrue(Selector.parse("div.card > .primary").matches(p));
        assertFalse(Selector.parse("div.other > button#submit").matches(p));
    }

    // ------------------------------------------------------------------
    // Universal selector "*" (Block 1.7)
    // ------------------------------------------------------------------

    @Test
    void universalSelectorParsesWithoutException() {
        assertDoesNotThrow(() -> Selector.parse("*"));
        assertDoesNotThrow(() -> Selector.parse("*.big"));
        assertDoesNotThrow(() -> Selector.parse("panel > *"));
        assertDoesNotThrow(() -> Selector.parse("*:hover"));
    }

    @Test
    void universalSelectorMatchesAnyTag() {
        Selector sel = Selector.parse("*");
        assertTrue(sel.matches(path("button")),
                "\"*\" must match any single element");
        assertTrue(sel.matches(path("panel", "label")),
                "\"*\" must match the last node of any path");
        assertTrue(sel.matches(path("whatever-weird-tag", "x", "y")));
    }

    @Test
    void universalCompoundRequiresClassOnAnyTag() {
        List<StyleNode> p = List.of(
                new FakeNode("anything", "", List.of("big")),
                new FakeNode("other", "", List.of("small")));
        assertTrue(Selector.parse("*.big").matches(List.of(p.get(0))),
                "*.big matches any tag carrying class 'big'");
        assertFalse(Selector.parse("*.big").matches(List.of(p.get(1))),
                "*.big must not match without the class");
    }

    @Test
    void universalAsDescendantOrChildTargetMatchesEveryElement() {
        // "panel > *" targets every direct child of a panel.
        Selector child = Selector.parse("panel > *");
        assertTrue(child.matches(path("panel", "button")));
        assertTrue(child.matches(path("panel", "label")));
        assertFalse(child.matches(path("frame", "button")),
                "ancestor constraint still applies to '*'");

        // "panel *" targets every descendant of a panel.
        Selector desc = Selector.parse("panel *");
        assertTrue(desc.matches(path("panel", "box", "button")));
    }

    @Test
    void universalSelectorContributesZeroTypeSpecificity() {
        // Per CSS, "*" adds nothing to specificity; a bare type selector
        // (0,0,1) must outrank it (0,0,0).
        int starSpec = Selector.parse("*").getSpecificity()[2];
        int typeSpec = Selector.parse("button").getSpecificity()[2];
        assertEquals(0, starSpec, "\"*\" must contribute zero type specificity");
        assertEquals(1, typeSpec, "a type selector contributes one element");
    }

    // ------------------------------------------------------------------
    // Structural pseudo-classes: :first-child / :last-child / :only-child
    // / :nth-child(an+b|odd|even)  (item 5.11)
    // ------------------------------------------------------------------

    /** Fake node that also reports its position among siblings. */
    private record PosNode(String styleTag, String id, List<String> classes,
                           int childIndex, int siblingCount) implements StyleNode {
        @Override
        public boolean hasClass(String className) {
            return classes.contains(className);
        }

        @Override
        public boolean matchesPseudo(Selector.PseudoClass pseudo) {
            return false;
        }

        @Override
        public StyleNode parent() {
            return null;
        }

        @Override
        public int childIndex() {
            return childIndex;
        }

        @Override
        public int siblingCount() {
            return siblingCount;
        }
    }

    /** Path of [root, child@position] where child is index-th of total. */
    private static List<StyleNode> pathAt(int index, int total) {
        return List.of(
                new FakeNode("panel", "", List.of()),
                new PosNode("li", "", List.of(), index, total));
    }

    /** Path of [root, button@position] for tests keyed on the "button" tag. */
    private static List<StyleNode> buttonPathAt(int index, int total) {
        return List.of(
                new FakeNode("panel", "", List.of()),
                new PosNode("button", "", List.of(), index, total));
    }

    @Test
    void structuralPseudoClassesParse() {
        assertDoesNotThrow(() -> Selector.parse("button:first-child"));
        assertDoesNotThrow(() -> Selector.parse("button:last-child"));
        assertDoesNotThrow(() -> Selector.parse("button:only-child"));
        assertDoesNotThrow(() -> Selector.parse("li:nth-child(2n+1)"));
        assertDoesNotThrow(() -> Selector.parse("li:nth-child(odd)"));
        assertDoesNotThrow(() -> Selector.parse("li:nth-child(even)"));
        assertDoesNotThrow(() -> Selector.parse("li:nth-child(3)"));
        assertDoesNotThrow(() -> Selector.parse("li:nth-child(-n+2)"));
        assertDoesNotThrow(() -> Selector.parse("li:nth-child(n + 4)"));
        assertThrows(IllegalArgumentException.class,
                () -> Selector.parse("li:nth-child(banana)"));
        assertThrows(IllegalArgumentException.class,
                () -> Selector.parse("li:nth-child(2n+"));
    }

    @Test
    void firstChildMatchesOnlyPositionOne() {
        Selector sel = Selector.parse("button:first-child");
        assertTrue(sel.matches(buttonPathAt(1, 3)));
        assertFalse(sel.matches(buttonPathAt(2, 3)));
        assertFalse(sel.matches(buttonPathAt(3, 3)));
    }

    @Test
    void lastChildMatchesOnlyFinalPosition() {
        Selector sel = Selector.parse("button:last-child");
        assertTrue(sel.matches(buttonPathAt(3, 3)));
        assertFalse(sel.matches(buttonPathAt(1, 3)));
        // Unknown sibling count degrades conservatively: no match.
        assertFalse(sel.matches(buttonPathAt(1, 0)));
    }

    @Test
    void onlyChildRequiresSingleSibling() {
        Selector sel = Selector.parse("button:only-child");
        assertTrue(sel.matches(buttonPathAt(1, 1)));
        assertFalse(sel.matches(buttonPathAt(1, 2)));
        assertFalse(sel.matches(buttonPathAt(2, 2)));
    }

    @Test
    void nthChildOddEvenAndPlainForms() {
        Selector odd = Selector.parse("li:nth-child(odd)");
        assertTrue(odd.matches(pathAt(1, 5)));
        assertTrue(odd.matches(pathAt(3, 5)));
        assertFalse(odd.matches(pathAt(2, 5)));

        Selector even = Selector.parse("li:nth-child(even)");
        assertTrue(even.matches(pathAt(2, 5)));
        assertFalse(even.matches(pathAt(5, 5)));

        Selector third = Selector.parse("li:nth-child(3)");
        assertTrue(third.matches(pathAt(3, 5)));
        assertFalse(third.matches(pathAt(4, 5)));
    }

    @Test
    void nthChildGeneralAnPlusBForm() {
        // 2n+1 == odd positions starting at 1.
        Selector sel = Selector.parse("li:nth-child(2n+1)");
        assertTrue(sel.matches(pathAt(1, 6)));
        assertTrue(sel.matches(pathAt(5, 6)));
        assertFalse(sel.matches(pathAt(6, 6)));

        // -n+2 selects the first two children.
        Selector firstTwo = Selector.parse("li:nth-child(-n+2)");
        assertTrue(firstTwo.matches(pathAt(1, 5)));
        assertTrue(firstTwo.matches(pathAt(2, 5)));
        assertFalse(firstTwo.matches(pathAt(3, 5)));
    }

    @Test
    void structuralCombinesWithOtherParts() {
        // ".row button:nth-child(2)" — class on ancestor, position on leaf.
        List<StyleNode> p = List.of(
                new FakeNode("row", "", List.of("row")),
                new PosNode("button", "", List.of(), 2, 4));
        assertTrue(Selector.parse(".row button:nth-child(2)").matches(p));
        assertFalse(Selector.parse(".row button:nth-child(3)").matches(p));
    }

    @Test
    void structuralPseudoContributesClassSpecificity() {
        // :first-child counts like a class in the (ids, classes, types) tuple.
        int[] withStructural = Selector.parse("button:first-child").getSpecificity();
        assertEquals(1, withStructural[1], "structural pseudo adds class-level specificity");
        assertEquals(1, withStructural[2], "type part still counts once");
        // It must lose to an id but beat a plain type selector.
        assertTrue(Selector.parse("#x").compareSpecificity(
                Selector.parse("button:first-child")) > 0);
        assertTrue(Selector.parse("button:first-child").compareSpecificity(
                Selector.parse("button")) > 0);
    }

    @Test
    void unknownPositionDegradesToNoMatchForFirstAndNth() {
        // Default StyleNode.childIndex() is 0 ("unknown"): :first-child and
        // :nth-child must not fire on unknown positions.
        Selector first = Selector.parse("button:first-child");
        assertFalse(first.matches(path("panel", "button")),
                "FakeNode reports index 0 (unknown), so :first-child must not match");
    }
}
