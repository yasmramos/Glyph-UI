package com.glyphui.markup;

/**
 * A recoverable syntax diagnostic produced by {@link Parser}.
 *
 * <p>The parser keeps going after these errors (synchronising on the next
 * plausible token) so that one pass reports every structural problem in a
 * document. Unrecoverable lexer conditions instead throw
 * {@link MarkupException}; see that class for the full error strategy.</p>
 *
 * @param message human-readable description of the problem
 * @param line    1-based line where the problem was detected
 * @param column  1-based column where the problem was detected
 */
public record ParseError(String message, int line, int column) {

    /**
     * Builds a positioned parse error.
     *
     * @param message description, must not be null
     * @param line    1-based line
     * @param column  1-based column
     */
    public ParseError {
        if (message == null || message.isEmpty()) {
            throw new IllegalArgumentException("message must not be empty");
        }
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "positions are 1-based, got line=" + line + " column=" + column);
        }
    }

    /**
     * Formats the diagnostic as {@code line:column: message}, matching the
     * style used by most compiler tool-chains.
     *
     * @return the formatted diagnostic string
     */
    @Override
    public String toString() {
        return line + ":" + column + ": " + message;
    }
}
