package com.glyphui.markup;

/**
 * Thrown by {@link Tokenizer} and {@link Parser} when the input cannot be
 * processed at all (unlike the recoverable diagnostics collected in a
 * {@link ParseResult}).
 *
 * <p><b>Error strategy (documented contract):</b> structural problems that
 * have an obvious recovery point (unclosed tag, stray closing tag, duplicate
 * attribute, ...) are accumulated as {@link ParseError} entries so a single
 * run reports every issue in the file. Only unrecoverable lexer conditions —
 * illegal characters inside a {@code {path}} binding, unterminated strings,
 * unterminated comments, unexpected end of input inside a tag or binding —
 * abort parsing immediately with this exception. All messages carry a 1-based
 * {@code line:column} position.</p>
 */
public class MarkupException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 1-based line where the problem was detected. */
    private final int line;

    /** 1-based column where the problem was detected. */
    private final int column;

    /**
     * Creates a positioned markup error.
     *
     * @param message human-readable description (without position prefix)
     * @param line    1-based line
     * @param column  1-based column
     */
    public MarkupException(String message, int line, int column) {
        super(message + " (at line " + line + ", column " + column + ")");
        this.line = line;
        this.column = column;
    }

    /**
     * @return the 1-based line of the offending position
     */
    public int getLine() {
        return line;
    }

    /**
     * @return the 1-based column of the offending position
     */
    public int getColumn() {
        return column;
    }
}
