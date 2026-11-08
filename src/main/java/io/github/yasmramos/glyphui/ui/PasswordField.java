package io.github.yasmramos.glyphui.ui;

/**
 * Single-line password input: a {@link TextField} whose characters are painted
 * as a mask glyph while the real value stays in the editing model.
 *
 * <p>The widget behaves exactly like its superclass — caret movement,
 * selection, clipboard and IME all operate on the unmasked text returned by
 * {@link #getText()} — so a form can read the secret as usual. Only the
 * rendering differs: {@link #displayText()} replaces every character with
 * {@link #getMaskChar()}, which keeps the caret geometry and the horizontal
 * pan identical to a plain field (one mask glyph per character).</p>
 *
 * <p>The default mask is {@code '\u2022'} (a bullet), the common desktop
 * convention. A masked field is a presentation concern, not a security
 * boundary: the value still lives in memory as a plain {@code String}.</p>
 */
public class PasswordField extends TextField {

    /** Default mask glyph painted in place of every character. */
    public static final char DEFAULT_MASK_CHAR = '\u2022';

    private char maskChar = DEFAULT_MASK_CHAR;

    /**
     * Creates a password field.
     *
     * @param x      initial x position
     * @param y      initial y position
     * @param width  initial width
     * @param height initial height
     */
    public PasswordField(float x, float y, float width, float height) {
        super(x, y, width, height);
    }

    /**
     * Creates a zero-sized password field to be positioned by a layout manager.
     */
    public PasswordField() {
        super();
    }

    @Override
    protected String defaultStyleTag() {
        // Same style tag as a plain input so `input { ... }` rules apply.
        return "input";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PASSWORD_FIELD;
    }

    /**
     * Gets the character painted in place of every real character.
     *
     * @return the mask glyph
     */
    public char getMaskChar() {
        return maskChar;
    }

    /**
     * Sets the character painted in place of every real character.
     *
     * @param maskChar the new mask glyph
     */
    public void setMaskChar(char maskChar) {
        if (this.maskChar == maskChar) {
            return;
        }
        this.maskChar = maskChar;
        invalidate();
    }

    /**
     * Returns one mask glyph per character of the buffer, so the painted width
     * (and therefore the caret position) matches the hidden value.
     *
     * @return the masked display string
     */
    @Override
    protected String displayText() {
        String text = getText();
        if (text.isEmpty() || maskChar == 0) {
            return text;
        }
        StringBuilder masked = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            masked.append(maskChar);
        }
        return masked.toString();
    }
}
