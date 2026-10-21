package com.glyphui.markup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A markup element: {@code <Box ...>children</Box>} or the self-closing form
 * {@code <Button/>}.
 *
 * <p>The node keeps attributes and children in source order and records the
 * position of its opening {@code &lt;} character. Attribute prefixes such as
 * {@code bind:} / {@code on:} and control attributes such as {@code if} or
 * {@code for-each} are <b>not</b> interpreted here — this is the raw tree.</p>
 */
public final class Element extends AstNode {

    /** Tag name exactly as written (case preserved). */
    private final String tagName;

    /** Whether the element was written in self-closing form {@code <Tag/>}. */
    private boolean selfClosing;

    /** Position of the matching end tag, or -1:-1 when absent/self-closing. */
    private int closeLine = -1;
    private int closeColumn = -1;

    private final List<Attribute> attributes = new ArrayList<>();
    private final List<AstNode> children = new ArrayList<>();

    /**
     * Creates an element.
     *
     * @param tagName     tag name, must not be null/empty
     * @param selfClosing true for {@code <Tag/>} syntax
     * @param line        1-based line of the opening {@code &lt;}
     * @param column      1-based column of the opening {@code &lt;}
     */
    public Element(String tagName, boolean selfClosing, int line, int column) {
        super(line, column);
        if (tagName == null || tagName.isEmpty()) {
            throw new IllegalArgumentException("tag name must not be empty");
        }
        this.tagName = tagName;
        this.selfClosing = selfClosing;
    }

    /**
     * @return the tag name as written in the source
     */
    public String getTagName() {
        return tagName;
    }

    /**
     * @return true when declared as {@code <Tag/>}
     */
    public boolean isSelfClosing() {
        return selfClosing;
    }

    /**
     * Marks this element as self-closing (parser-internal, set when the
     * start tag ends with {@code />}).
     *
     * @param selfClosing true for the {@code <Tag/>} form
     */
    void setSelfClosing(boolean selfClosing) {
        this.selfClosing = selfClosing;
    }

    /**
     * Adds an attribute to this element (parser-internal).
     *
     * @param attribute the attribute to append
     */
    void addAttribute(Attribute attribute) {
        attributes.add(attribute);
    }

    /**
     * Appends a child node (parser-internal).
     *
     * @param child the child to append
     */
    void addChild(AstNode child) {
        children.add(child);
    }

    /**
     * Records the position of the matching end tag (parser-internal).
     *
     * @param closeLine   1-based line of {@code &lt;/}
     * @param closeColumn 1-based column of {@code &lt;/}
     */
    void setClosePosition(int closeLine, int closeColumn) {
        this.closeLine = closeLine;
        this.closeColumn = closeColumn;
    }

    /**
     * @return unmodifiable list of attributes in source order
     */
    public List<Attribute> getAttributes() {
        return Collections.unmodifiableList(attributes);
    }

    /**
     * Finds an attribute by exact (case-sensitive) raw name.
     *
     * @param name the attribute name to look up
     * @return the attribute, or null when absent
     */
    public Attribute findAttribute(String name) {
        for (Attribute a : attributes) {
            if (a.name().equals(name)) {
                return a;
            }
        }
        return null;
    }

    /**
     * @return unmodifiable list of children (text, bindings, elements) in
     *         source order
     */
    public List<AstNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * @return 1-based line of the matching end tag, or -1 when there is none
     */
    public int getCloseLine() {
        return closeLine;
    }

    /**
     * @return 1-based column of the matching end tag, or -1 when there is none
     */
    public int getCloseColumn() {
        return closeColumn;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Element(<").append(tagName);
        for (Attribute a : attributes) {
            sb.append(' ').append(a);
        }
        sb.append(selfClosing ? "/>" : ">");
        if (!children.isEmpty()) {
            sb.append(formatChildren(children)).append("</").append(tagName).append('>');
        }
        sb.append(" at ").append(line).append(':').append(column);
        return sb.toString();
    }
}
