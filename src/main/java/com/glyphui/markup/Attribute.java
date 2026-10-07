package com.glyphui.markup;

/**
 * A raw markup attribute such as {@code id="submit"}, {@code layout=flex} or
 * a valueless flag like {@code disabled}.
 *
 * <p>The parser deliberately stores the <em>raw</em> name and value: prefix
 * semantics ({@code bind:}, {@code on:}) and special attributes
 * ({@code if}, {@code elif}, {@code else}, {@code for-each}) are interpreted
 * by later phases (validator/binder), not here.</p>
 *
 * @param name   raw attribute name exactly as written in the source
 * @param value  raw attribute value with surrounding quotes removed, or
 *               {@code null} when the attribute has no {@code =value} part
 * @param quoted whether the value came from a double-quoted string token
 * @param line   1-based line where the attribute name starts
 * @param column 1-based column where the attribute name starts
 */
public record Attribute(String name, String value, boolean quoted,
                        int line, int column) {

    /**
     * Builds an attribute.
     *
     * @param name   raw name, must not be null/empty
     * @param value  raw value or null for valueless attributes
     * @param quoted true when the value was a quoted string
     * @param line   1-based line of the name
     * @param column 1-based column of the name
     */
    public Attribute {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("attribute name must not be empty");
        }
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "positions are 1-based, got line=" + line + " column=" + column);
        }
    }

    /**
     * @return true when this attribute carries a value at all
     */
    public boolean hasValue() {
        return value != null;
    }

    /**
     * Formats the attribute the way it appears in source (quoted values keep
     * their quotes).
     *
     * @return e.g. {@code id="submit"} or {@code disabled}
     */
    @Override
    public String toString() {
        if (value == null) {
            return name;
        }
        return name + "=" + (quoted ? "\"" + value + "\"" : value);
    }

    /**
     * Convenience factory used by tests and tooling.
     *
     * @param name   attribute name
     * @param value  attribute value
     * @param line   1-based line
     * @param column 1-based column
     * @return a quoted string-valued attribute
     */
    public static Attribute quoted(String name, String value, int line, int column) {
        return new Attribute(name, value, true, line, column);
    }
}
