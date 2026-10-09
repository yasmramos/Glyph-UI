package com.glyphui.style;

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
}
