package com.glyphui.markup;

/**
 * Classification of every lexical unit produced by {@link Tokenizer}.
 *
 * <p>The kinds are grouped by the tokenizer mode that can emit them:</p>
 * <ul>
 *   <li><b>CONTENT mode:</b> {@link #TEXT}, {@link #OPEN_TAG},
 *       {@link #OPEN_CLOSE_TAG}, {@link #BINDING_OPEN}</li>
 *   <li><b>TAG mode:</b> {@link #TAG_NAME}, {@link #ATTR_NAME},
 *       {@link #EQUALS}, {@link #STRING}, {@link #CLOSE_TAG},
 *       {@link #CLOSE_SELF_TAG}</li>
 *   <li><b>BINDING mode:</b> {@link #IDENT}, {@link #DOT},
 *       {@link #BINDING_CLOSE}</li>
 *   <li><b>Shared:</b> {@link #EOF} (emitted once at end of input) and
 *       {@link #ERROR} (illegal character sequence; carries a message in
 *       {@link Token#value()}).</li>
 * </ul>
 */
public enum TokenKind {
    /** Free-form text run in element content. */
    TEXT,
    /** {@code &lt;} opening a start tag. */
    OPEN_TAG,
    /** {@code &lt;/} opening an end tag. */
    OPEN_CLOSE_TAG,
    /** {@code &#123;} opening a path interpolation in content. */
    BINDING_OPEN,
    /** Tag name right after {@code &lt;} or {@code &lt;/}. */
    TAG_NAME,
    /** Attribute name inside a start tag. */
    ATTR_NAME,
    /** The {@code =} separating an attribute name from its value. */
    EQUALS,
    /** A double-quoted string attribute value, e.g. {@code "app.css"}. */
    STRING,
    /** Bare (unquoted) attribute value, e.g. {@code flex}. */
    BARE_VALUE,
    /** {@code &gt;} closing a start or end tag. */
    CLOSE_TAG,
    /** {@code /&gt;} closing a self-closing tag. */
    CLOSE_SELF_TAG,
    /** Identifier segment inside a binding path. */
    IDENT,
    /** {@code .} separator between binding path segments. */
    DOT,
    /** {@code &#125;} closing a binding path. */
    BINDING_CLOSE,
    /** End of input (exactly one per token stream). */
    EOF,
    /** Illegal character sequence; {@link Token#value()} holds the message. */
    ERROR
}
