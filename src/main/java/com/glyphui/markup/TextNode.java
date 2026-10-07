package com.glyphui.markup;

/**
 * A literal text run inside element content.
 *
 * <p>The value is already unescaped by the tokenizer: the key escapes
 * {@code &#123;&#123;} and {@code &#125;&#125;} have been folded into single
 * {@code &#123;} / {@code &#125;} characters, so a TextNode never contains
 * markup-significant braces.</p>
 */
public final class TextNode extends AstNode {

    /** The literal text (escapes resolved). */
    private final String text;

    /**
     * Creates a text node.
     *
     * @param text   the literal text, must not be null
     * @param line   1-based start line
     * @param column 1-based start column
     */
    public TextNode(String text, int line, int column) {
        super(line, column);
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        this.text = text;
    }

    /**
     * @return the literal text of this node
     */
    public String getText() {
        return text;
    }

    @Override
    public String toString() {
        return "Text(\"" + text + "\") at " + line + ":" + column;
    }
}
