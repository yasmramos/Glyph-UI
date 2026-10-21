package com.glyphui.markup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class of every AST node produced by {@link Parser}.
 *
 * <p>Nodes keep the 1-based start position (line/column) of the construct
 * they represent so that later phases (validator, binder, diagnostics) can
 * point back into the original {@code .glyph} source.</p>
 */
public abstract sealed class AstNode permits Element, TextNode, BindingNode {

    /** 1-based line where this node starts. */
    protected final int line;

    /** 1-based column where this node starts. */
    protected final int column;

    /**
     * Records the start position of a node.
     *
     * @param line   1-based line
     * @param column 1-based column
     */
    protected AstNode(int line, int column) {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "positions are 1-based, got line=" + line + " column=" + column);
        }
        this.line = line;
        this.column = column;
    }

    /**
     * @return the 1-based start line of this node
     */
    public int getLine() {
        return line;
    }

    /**
     * @return the 1-based start column of this node
     */
    public int getColumn() {
        return column;
    }

    /**
     * Formats a child list for {@code toString()} implementations.
     *
     * @param children the children to render
     * @return comma-separated rendering wrapped in brackets
     */
    static String formatChildren(List<? extends AstNode> children) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children.get(i));
        }
        return sb.append(']').toString();
    }

    /**
     * Unmodifiable view helper used by subclasses.
     *
     * @param list the backing list
     * @param <T>  element type
     * @return an unmodifiable view of {@code list}
     */
    static <T> List<T> frozen(List<T> list) {
        return Collections.unmodifiableList(list);
    }

    /**
     * Mutable copy helper used by subclass constructors.
     *
     * @param list the source list (may be null → empty)
     * @param <T>  element type
     * @return a new modifiable {@link ArrayList} with the same elements
     */
    static <T> List<T> mutableCopy(List<T> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}
