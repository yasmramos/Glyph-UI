package io.github.yasmramos.glyphui.markup;

import java.util.List;
import java.util.Objects;

/**
 * Outcome of {@link Parser#parse(String)}: the (possibly partial) syntax tree
 * together with every recoverable diagnostic found along the way.
 *
 * @param document the parsed document; never null, may be empty or partial when
 * {@link #errors()} is not empty
 * @param errors unmodifiable list of recoverable parse errors in the order they
 * were detected
 */
public record ParseResult(Document document, List<ParseError> errors) {

    /**
     * Normalises the error list to an unmodifiable snapshot.
     */
    public ParseResult {
        // Item 5.19: the contract says "never null"; enforce it so a broken
        // producer fails fast at construction instead of surfacing as an NPE
        // far away in the loader/binder.
        Objects.requireNonNull(document, "document");
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
