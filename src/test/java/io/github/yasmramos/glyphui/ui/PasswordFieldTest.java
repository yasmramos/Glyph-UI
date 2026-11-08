package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link PasswordField}: the editing model keeps the real
 * value while only the painted string is masked. Paints are lazy, so the whole
 * state machine runs headlessly.
 */
class PasswordFieldTest extends TestBase {

    private PasswordField field;

    @BeforeEach
    void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
        field = new PasswordField(0, 0, 200, 30);
        // The focus manager only accepts visible candidates; a standalone
        // widget created via the geometry constructor starts hidden.
        field.setVisible(true);
        field.requestFocus();
    }

    @AfterEach
    void tearDown() {
        if (field != null) {
            field.dispose();
        }
    }

    private static KeyEvent key(KeyEventType type, int keyCode) {
        return new KeyEvent(type, keyCode, ' ', EnumSet.noneOf(KeyModifier.class));
    }

    @Test
    void reportsThePasswordAccessibleRole() {
        assertEquals(AccessibleRole.PASSWORD_FIELD, field.getAccessibleRole());
    }

    @Test
    void masksEveryCharacterByDefault() {
        field.setText("s3cr3t");

        assertEquals("s3cr3t", field.getText(),
                "the real value stays readable through the editing model");
        assertEquals("\u2022\u2022\u2022\u2022\u2022\u2022", field.displayText(),
                "one bullet per character");
        assertEquals(PasswordField.DEFAULT_MASK_CHAR, field.getMaskChar());
    }

    @Test
    void honoursACustomMaskCharacter() {
        field.setMaskChar('*');
        field.setText("abcd");

        assertEquals('*', field.getMaskChar());
        assertEquals("****", field.displayText());
    }

    @Test
    void maskedDisplayTracksTypedInput() {
        field.onCharTyped('h');
        field.onCharTyped('i');

        assertEquals("hi", field.getText());
        assertEquals(2, field.displayText().length(),
                "the mask length follows the buffer length");
        assertNotEquals(field.getText(), field.displayText(),
                "the display must not leak the real characters");
    }

    @Test
    void backspaceEditsTheRealValueAndTheMask() {
        field.setText("secret");
        field.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.BACKSPACE));

        assertEquals("secre", field.getText());
        assertEquals("secre".length(), field.displayText().length());
    }

    @Test
    void emptyValueDisplaysNothing() {
        field.setText("");

        assertEquals("", field.getText());
        assertEquals("", field.displayText());
    }

    @Test
    void zeroMaskCharFallsBackToTheRawText() {
        field.setText("plain");
        field.setMaskChar((char) 0);

        assertEquals("plain", field.displayText());
    }

    @Test
    void inheritsTheTextFieldStyleTag() {
        // The masked field must keep resolving the same CSS `input { ... }`
        // rules as a plain text field.
        assertTrue(field.isFocusable());
        assertEquals("input", new TextFieldStyleProbe(field).styleTag());
    }

    /** Reads the protected style tag without widening the widget API. */
    private static final class TextFieldStyleProbe {
        private final PasswordField field;

        TextFieldStyleProbe(PasswordField field) {
            this.field = field;
        }

        String styleTag() {
            return field.defaultStyleTag();
        }
    }
}
