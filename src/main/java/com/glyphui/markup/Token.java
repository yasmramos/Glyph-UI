package com.glyphui.markup;

/**
 * An immutable lexical token produced by {@link Tokenizer}.
 *
 * <p>Every token records its {@link TokenKind kind}, the raw source text it
 * covers ({@link #value()}), and its 1-based start position (line and column)
 * so that downstream diagnostics can point at the exact place in a
 * {@code .glyph} file where a problem occurred.</p>
 *
 * <p>For {@link TokenKind#ERROR} tokens, {@link #value()} carries a
 * human-readable error message instead of source text.</p>
 *
 * @param kind   the token classification
 * @param value  the raw lexeme (or the error message for ERROR tokens)
 * @param line   1-based line where the token starts
 * @param column 1-based column where the token starts
 */
public record Token(TokenKind kind, String value, int line, int column) {

    /**
     * Builds a token.
     *
     * @param kind   token kind, must not be null
     * @param value  raw lexeme or error message, must not be null
     * @param line   1-based start line
     * @param column 1-based start column
     */
    public Token {
        if (kind == null) {
            throw new IllegalArgumentException("kind must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "positions are 1-based, got line=" + line + " column=" + column);
        }
    }

    /**
     * Human-readable representation including the source position, useful in
     * assertion failure messages.
     *
     * @return e.g. {@code TAG_NAME("Box") at 3:1}
     */
    @Override
    public String toString() {
        return kind + "(\"" + value + "\") at " + line + ":" + column;
    }
}
