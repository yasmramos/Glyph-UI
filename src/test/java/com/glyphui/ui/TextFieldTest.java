package com.glyphui.ui;

import com.glyphui.core.FocusManager;
import com.glyphui.events.KeyEvent;
import com.glyphui.events.KeyEventType;
import com.glyphui.events.KeyModifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link TextField} editing behavior driven through the
 * keyboard event API (no GLFW window required): caret movement, insertion,
 * backspace/delete, selection and Ctrl+A. Also verifies accessibility role
 * defaults across widgets.
 */
public class TextFieldTest {

    private static final int KEY_LEFT = 263;   // GLFW_KEY_LEFT
    private static final int KEY_HOME = 268;   // GLFW_KEY_HOME
    private static final int KEY_END = 269;    // GLFW_KEY_END
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_A = 29;       // GLFW_KEY_A

    private TextField field;

    @BeforeEach
    public void setUp() {
        // A focus manager is required so that requestFocus() actually sets
        // the focused flag; without a root it focuses components directly.
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
        field = new TextField(0, 0, 200, 30);
        field.requestFocus();
    }

    @AfterEach
    public void tearDown() {
        if (field != null) {
            field.setVisible(false);
        }
        FocusManager.setGlobalFocusManager(null);
    }

    private KeyEvent press(int keyCode, char ch, KeyModifier... mods) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ch,
                mods.length == 0 ? EnumSet.noneOf(KeyModifier.class)
                                 : EnumSet.of(mods[0]));
    }

    @Test
    public void testTypingInsertsAtCaret() {
        field.onCharTyped('H');
        field.onCharTyped('i');
        assertEquals("Hi", field.getText());
    }

    @Test
    public void testSetTextPlacesCaretAtEnd() {
        field.setText("Hello");
        assertEquals("Hello", field.getText());

        // Backspace removes the last character: caret starts at the end.
        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_BACKSPACE, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        field.onKeyEvent(new KeyEvent(KeyEventType.RELEASE, KEY_BACKSPACE, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        assertEquals("Hell", field.getText());
    }

    @Test
    public void testArrowKeysMoveCaretAndHomeEndJump() {
        field.setText("abc");
        // Caret at 3. Move left twice -> 1, then insert 'X'.
        for (int i = 0; i < 2; i++) {
            field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_LEFT, '\0',
                    EnumSet.noneOf(KeyModifier.class)));
        }
        field.onCharTyped('X');
        assertEquals("aXbc", field.getText());

        // Home -> front, End -> back.
        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_HOME, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        field.onCharTyped('1');
        assertEquals("1aXbc", field.getText());

        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_END, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        field.onCharTyped('9');
        assertEquals("1aXbc9", field.getText());
    }

    @Test
    public void testDeleteKeyRemovesCharAfterCaret() {
        field.setText("ab");
        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_HOME, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_DELETE, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        assertEquals("b", field.getText());
    }

    @Test
    public void testCtrlASelectsAllAndTypingReplacesSelection() {
        field.setText("old text");
        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_A, 'a',
                EnumSet.of(KeyModifier.CTRL)));

        assertTrue(field.hasSelection(), "Ctrl+A must select the whole text");

        // Typing over a selection replaces it.
        field.onCharTyped('N');
        assertEquals("N", field.getText());
        assertFalse(field.hasSelection());
    }

    @Test
    public void testUnfocusedFieldIgnoresKeys() {
        field.setText("keep");
        FocusManager manager = new FocusManager(() -> { });
        FocusManager.setGlobalFocusManager(manager);
        Button other = new Button(0, 0, 50, 30);
        manager.setRoot(wrapInPanel(field, other));
        // Route the focus change through the global manager so that the
        // previously focused field actually loses its focus flag.
        field.requestFocus();
        other.requestFocus();

        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_BACKSPACE, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        field.onCharTyped('Z');

        assertEquals("keep", field.getText(),
                "Keys must only reach the focused component");
        assertSame(other, manager.getFocused());
        other.dispose();
        manager.clearFocus(other);
        assertNull(FocusManager.getGlobalFocusManager().getFocused());
    }

    private Panel wrapInPanel(Component a, Component b) {
        Panel panel = new Panel(0, 0, 400, 200);
        panel.add(a);
        panel.add(b);
        return panel;
    }

    // ------------------------------------------------------------------
    // Accessibility defaults (stub API)
    // ------------------------------------------------------------------

    @Test
    public void testAccessibleRolesPerWidget() {
        Button button = new Button(0, 0, 50, 30, "OK");
        Label label = new Label(0, 0, 100, 20, "Hi");
        Panel panel = new Panel(0, 0, 100, 100);
        TextField textField = new TextField(0, 0, 100, 30);

        assertEquals(AccessibleRole.BUTTON, button.getAccessibleRole());
        assertEquals(AccessibleRole.LABEL, label.getAccessibleRole());
        assertEquals(AccessibleRole.PANEL, panel.getAccessibleRole());
        assertEquals(AccessibleRole.TEXT_FIELD, textField.getAccessibleRole());

        // Name falls back to the textual content when not explicitly set.
        assertEquals("Hi", label.getAccessibleName());
        assertEquals("OK", button.getAccessibleName());

        textField.setVisible(false);
    }

    @Test
    public void testGenericComponentDefaults() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);
        assertEquals(AccessibleRole.GENERIC, comp.getAccessibleRole());
        // Description defaults are non-null stubs so callers never NPE.
        assertTrue(comp.getAccessibleDescription() != null);
    }
}
