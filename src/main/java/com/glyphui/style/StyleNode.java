package com.glyphui.style;

/**
 * Minimal read-only view of a component used by {@link Selector} matching and
 * by {@link StyleEngine} cascade computation. Decoupling the style subsystem
 * from {@code com.glyphui.ui.Component} keeps the parser/testable without a
 * rendering context; {@code StyleEngine} adapts real components to this
 * interface internally.
 */
public interface StyleNode {

    /**
     * The HTML-equivalent tag name used by type selectors (e.g. "button",
     * "div", "label"). Lower-case.
     *
     * @return the style tag
     */
    String styleTag();

    /**
     * The component id used by {@code #id} selectors.
     *
     * @return the id (never null)
     */
    String id();

    /**
     * Checks membership of a style class used by {@code .class} selectors.
     *
     * @param className the class name (lower-case, no dot)
     * @return true when the node carries the class
     */
    boolean hasClass(String className);

    /**
     * Evaluates a pseudo-class against the node's current visual state.
     *
     * @param pseudo the pseudo-class
     * @return true when the node currently matches
     */
    boolean matchesPseudo(Selector.PseudoClass pseudo);

    /**
     * The parent node, or null at the tree root.
     *
     * @return the ancestor
     */
    StyleNode parent();
}
