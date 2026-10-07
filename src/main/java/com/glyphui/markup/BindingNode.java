package com.glyphui.markup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A path interpolation such as {@code {user.name}} found in element content.
 *
 * <p>The tokenizer has already split the dotted path into its identifier
 * segments ({@link #getSegments()}), so consumers never need to re-parse it.
 * Grammar accepted inside a binding: {@code ident(.ident)*} — anything else
 * (operators, calls, literals) is rejected by the lexer with a positioned
 * {@link MarkupException}.</p>
 */
public final class BindingNode extends AstNode {

    /** Dot-separated identifier segments, e.g. ["user", "name"]. */
    private final List<String> segments;

    /** The raw path text exactly as written between the braces. */
    private final String rawPath;

    /** 1-based line of the closing brace (0 when unknown). */
    private int closeLine;

    /** 1-based column of the closing brace (0 when unknown). */
    private int closeColumn;

    /**
     * Creates a binding node.
     *
     * @param segments one or more identifier segments, must not be empty
     * @param rawPath  the raw source text of the path (for diagnostics)
     * @param line     1-based line of the opening brace
     * @param column   1-based column of the opening brace
     */
    public BindingNode(List<String> segments, String rawPath, int line, int column) {
        super(line, column);
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("binding path must have at least one segment");
        }
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
        this.rawPath = rawPath;
    }

    /**
     * @return the unmodifiable list of dot-separated identifier segments
     */
    public List<String> getSegments() {
        return segments;
    }

    /**
     * @return the raw path text as written in the source, e.g. {@code user.name}
     */
    public String getRawPath() {
        return rawPath;
    }

    /**
     * Records the position of the closing brace consumed by the parser.
     *
     * @param line   1-based line of the '}' character
     * @param column 1-based column of the '}' character
     */
    void setClosePosition(int line, int column) {
        this.closeLine = line;
        this.closeColumn = column;
    }

    /**
     * @return 1-based line of the closing brace, or 0 when unknown
     */
    public int getCloseLine() {
        return closeLine;
    }

    /**
     * @return 1-based column of the closing brace, or 0 when unknown
     */
    public int getCloseColumn() {
        return closeColumn;
    }

    @Override
    public String toString() {
        return "Binding({" + rawPath + "}) at " + line + ":" + column;
    }
}
