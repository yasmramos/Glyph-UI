package com.glyphui.markup;

/**
 * Outcome of {@link Parser#parse(String)}: the (possibly partial) syntax tree
 * together with every recoverable diagnostic found along the way.
 *
 * @param document the parsed document; never null, may be empty or partial
 *                 when {@link #errors()} is not empty
 * @param errors   unmodifiable list of recoverable parse errors in the order
 *                 they were detected
 */
public record ParseResult(Document document, java.util.List<ParseError> errors) {

    /**
     * Normalises the error list to an unmodifiable snapshot.
     */
    public ParseResult {
        errors = java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(errors));
    }

    /**
     * @return true when the document parsed without any diagnostics
     */
    public boolean isSuccess() {
        return errors.isEmpty();
    }
}
