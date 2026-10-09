package com.glyphui.markup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Root of a parsed {@code .glyph} document: an ordered list of top-level
 * nodes (elements, free text and bindings) plus the 1-based position where
 * the document starts.
 */
public final class Document {

    /** Top-level children in source order. */
    private final List<AstNode> children = new ArrayList<>();

    /** 1-based line of the first token. */
    private final int line;

    /** 1-based column of the first token. */
    private final int column;

    /**
     * Creates a document rooted at the given position.
     *
     * @param line   1-based start line
     * @param column 1-based start column
     */
    public Document(int line, int column) {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "positions are 1-based, got line=" + line + " column=" + column);
        }
        this.line = line;
        this.column = column;
    }

    /**
     * Appends a top-level node (parser-internal).
     *
     * @param node the node to append
     */
    void addChild(AstNode node) {
        children.add(node);
    }

    /**
     * @return unmodifiable list of top-level nodes in source order
     */
    public List<AstNode> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /**
     * Convenience accessor for documents with a single root element.
     *
     * @return the first child that is an {@link Element}, or null when the
     *         document has no top-level element
     */
    public Element getRootElement() {
        for (AstNode node : children) {
            if (node instanceof Element element) {
                return element;
            }
        }
        return null;
    }

    /**
     * @return the 1-based start line of the document
     */
    public int getLine() {
        return line;
    }

    /**
     * @return the 1-based start column of the document
     */
    public int getColumn() {
        return column;
    }

    @Override
    public String toString() {
        return "Document(" + AstNode.formatChildren(children) + ")";
    }
}
