package com.glyphui.ui;

import com.glyphui.core.FocusManager;
import com.glyphui.events.KeyEvent;
import com.glyphui.events.KeyEventType;
import com.glyphui.events.KeyModifier;
import com.glyphui.events.MouseButton;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.MouseEventType;
import com.glyphui.graphics.Dimension;
import com.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Extended editing tests for {@link TextField}: word-wise caret movement,
 * shift-selection, clipboard hooks (copy/cut/paste), placeholder/change
 * notification, disabled behavior and rendering paths against an in-memory
 * raster surface. Complements {@link TextFieldTest}.
 */
public class TextFieldEditingTest {

    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;
    private static final int KEY_BACKSPACE = 259;
    private static final int KEY_DELETE = 261;
    private static final int KEY_ENTER = 257;
    private static final int KEY_A = 29;
    private static final int KEY_C = 54;
    private static final int KEY_X = 57;
    private static final int KEY_V = 56;

    private TextField field;
    private String systemClipboard;

    @BeforeAll
    static void checkSkijaAvailable() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping raster tests");
    }

    @BeforeEach
    public void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
        field = new TextField(0, 0, 200, 30);
        // The focus manager only accepts visible candidates; a standalone
        // widget created via the geometry constructor starts hidden.
        field.setVisible(true);
        field.requestFocus();
        systemClipboard = null;
        TextField.setClipboardHooks(
                () -> systemClipboard = field.getSelectedTextOrNull(),
                () -> field.insertText(systemClipboard == null ? "" : systemClipboard));
    }

    @AfterEach
    public void tearDown() {
        TextField.setClipboardHooks(null, null);
        if (field != null) {
            field.dispose();
        }
        FocusManager.setGlobalFocusManager(null);
    }

    private KeyEvent press(int keyCode, char ch, KeyModifier... mods) {
        EnumSet<KeyModifier> set = mods.length == 0
                ? EnumSet.noneOf(KeyModifier.class)
                : EnumSet.of(mods[0], Arrays.copyOfRange(mods, 1, mods.length));
        return new KeyEvent(KeyEventType.PRESS, keyCode, ch, set);
    }

    // ------------------------------------------------------------------
    // Content API
    // ------------------------------------------------------------------

    @Test
    public void setTextNullBecomesEmptyAndMovesCaretToEnd() {
        field.setText("abc");
        field.setText(null);
        assertEquals("", field.getText());
        assertEquals(0, field.getCaret());
        assertFalse(field.hasSelection());
    }

    @Test
    public void changeListenerFiresOnlyOnActualChangeWhenNotified() {
        int[] fired = {0};
        field.setOnTextChanged(() -> fired[0]++);

        field.setText("a", false);   // programmatic init: no notify
        assertEquals(0, fired[0]);

        field.setText("a", true);    // same value: no change, no notify
        assertEquals(0, fired[0]);

        field.setText("b", true);    // real change: fires
        assertEquals(1, fired[0]);

        field.setOnTextChanged(null);
        assertNull(field.getOnTextChanged());
        field.setText("c", true);    // cleared handler must not NPE
        assertEquals(1, fired[0]);
    }

    @Test
    public void placeholderRoundTripAndNullClears() {
        field.setPlaceholder("Type here");
        assertEquals("Type here", field.getPlaceholder());
        field.setPlaceholder(null);
        assertEquals("", field.getPlaceholder());
    }

    @Test
    public void insertTextReplacesSelectionAndIgnoresNoops() {
        field.setText("hello world");
        field.selectAll();
        field.insertText("X");
        assertEquals("X", field.getText());
        assertEquals(1, field.getCaret());

        // Null / empty insertions are ignored entirely.
        field.insertText(null);
        field.insertText("");
        assertEquals("X", field.getText());
    }

    @Test
    public void selectAllClearSelectionAndSelectedTextOrNull() {
        field.setText("abcd");
        assertFalse(field.hasSelection());
        assertNull(field.getSelectedTextOrNull());

        field.onKeyEvent(press(KEY_HOME, '\0'));
        field.onKeyEvent(press(KEY_RIGHT, '\0', KeyModifier.SHIFT));
        field.onKeyEvent(press(KEY_RIGHT, '\0', KeyModifier.SHIFT));
        assertTrue(field.hasSelection());
        assertEquals("ab", field.getSelectedText());
        assertEquals("ab", field.getSelectedTextOrNull());

        field.clearSelection();
        assertFalse(field.hasSelection());
        assertEquals("", field.getSelectedText()); // empty range at caret
    }

    // ------------------------------------------------------------------
    // Word-wise navigation
    // ------------------------------------------------------------------

    @Test
    public void ctrlArrowJumpsByWords() {
        field.setText("one two three");
        field.onKeyEvent(press(KEY_HOME, '\0'));
        field.onKeyEvent(press(KEY_RIGHT, '\0', KeyModifier.CTRL));
        assertEquals(4, field.getCaret());
        field.onKeyEvent(press(KEY_RIGHT, '\0', KeyModifier.CTRL));
        assertEquals(8, field.getCaret());
        field.onKeyEvent(press(KEY_LEFT, '\0', KeyModifier.CTRL));
        assertEquals(4, field.getCaret());
        field.onKeyEvent(press(KEY_LEFT, '\0', KeyModifier.CTRL));
        assertEquals(0, field.getCaret());
    }

    @Test
    public void ctrlShiftArrowExtendsSelectionByWord() {
        field.setText("alpha beta");
        field.onKeyEvent(press(KEY_HOME, '\0'));
        field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_RIGHT, '\0',
                EnumSet.of(KeyModifier.CTRL, KeyModifier.SHIFT)));
        assertTrue(field.hasSelection());
        // wordForward() lands on the start of the next word (index 6), so
        // the selection covers the leading space: "alpha " is selected.
        assertEquals("alpha ", field.getSelectedText());
    }

    @Test
    public void shiftHomeEndSelectToEdges() {
        field.setText("abcdef");
        field.onKeyEvent(press(KEY_HOME, '\0'));
        field.onKeyEvent(press(KEY_END, '\0', KeyModifier.SHIFT));
        assertEquals("abcdef", field.getSelectedText());

        // A plain (unshifted) HOME collapses the selection at position 0...
        field.onKeyEvent(press(KEY_HOME, '\0'));
        assertFalse(field.hasSelection());
        // ...and SHIFT+END from there re-selects the whole content.
        field.onKeyEvent(press(KEY_END, '\0', KeyModifier.SHIFT));
        assertEquals("abcdef", field.getSelectedText());
    }

    // ------------------------------------------------------------------
    // Backspace / Delete semantics
    // ------------------------------------------------------------------

    @Test
    public void backspaceAtStartIsNoopAndWithSelectionDeletesSelection() {
        field.setText("ab");
        field.onKeyEvent(press(KEY_HOME, '\0'));
        field.onKeyEvent(press(KEY_BACKSPACE, '\0'));
        assertEquals("ab", field.getText()); // nothing before caret

        field.selectAll();
        field.onKeyEvent(press(KEY_BACKSPACE, '\0'));
        assertEquals("", field.getText());
        assertEquals(0, field.getCaret());
    }

    @Test
    public void deleteAtEndIsNoopAndWithSelectionDeletesSelection() {
        field.setText("ab");
        field.onKeyEvent(press(KEY_END, '\0'));
        field.onKeyEvent(press(KEY_DELETE, '\0'));
        assertEquals("ab", field.getText()); // nothing after caret

        field.selectAll();
        field.onKeyEvent(press(KEY_DELETE, '\0'));
        assertEquals("", field.getText());
    }

    @Test
    public void enterCollapsesSelectionWithoutEditing() {
        field.setText("word");
        field.selectAll();
        field.onKeyEvent(press(KEY_ENTER, '\0'));
        assertEquals("word", field.getText());
        assertFalse(field.hasSelection());
    }

    // ------------------------------------------------------------------
    // Clipboard hooks
    // ------------------------------------------------------------------

    @Test
    public void ctrlCopyPopulatesClipboardHook() {
        field.setText("copy me");
        field.selectAll();
        field.onKeyEvent(press(KEY_C, 'c', KeyModifier.CTRL));
        assertEquals("copy me", systemClipboard);
        assertEquals("copy me", field.getText()); // copy does not mutate
    }

    @Test
    public void ctrlCutCopiesThenRemovesSelection() {
        field.setText("cut me out");
        field.selectAll();
        field.onKeyEvent(press(KEY_X, 'x', KeyModifier.CTRL));
        assertEquals("cut me out", systemClipboard);
        assertEquals("", field.getText());
    }

    @Test
    public void ctrlPasteInsertsClipboardContent() {
        systemClipboard = "pasted";
        field.setText("");
        field.onKeyEvent(press(KEY_V, 'v', KeyModifier.CTRL));
        assertEquals("pasted", field.getText());
    }

    @Test
    public void ctrlWithoutHandlersOrSelectionIsInert() {
        TextField.setClipboardHooks(null, null);
        field.setText("keep");
        field.selectAll();
        // Ctrl+A is handled before the clipboard branch, so it works even
        // with no hooks installed.
        field.onKeyEvent(press(KEY_A, 'a', KeyModifier.CTRL));
        assertTrue(field.hasSelection());
        field.onKeyEvent(press(KEY_C, 'c', KeyModifier.CTRL)); // no copy hook
        field.onKeyEvent(press(KEY_V, 'v', KeyModifier.CTRL)); // no paste hook
        field.onKeyEvent(press(KEY_X, 'x', KeyModifier.CTRL)); // cut without hook still deletes
        assertEquals("", field.getText());
    }

    // ------------------------------------------------------------------
    // Disabled state & non-matching events
    // ------------------------------------------------------------------

    @Test
    public void disabledFieldIgnoresTypingAndMousePress() {
        field.setEnabled(false);
        field.onCharTyped('Q');
        assertEquals("", field.getText());

        boolean consumed = field.onMouseEvent(
                new MouseEvent(MouseEventType.PRESS, 5, 5, MouseButton.LEFT, 1));
        assertFalse(consumed);
    }

    @Test
    public void releaseAndUnrelatedKeysAreIgnored() {
        field.setText("x");
        field.onKeyEvent(new KeyEvent(KeyEventType.RELEASE, KEY_BACKSPACE, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        assertEquals("x", field.getText());

        // Unknown key with no modifiers falls through the default branch harmlessly.
        field.onKeyEvent(press(300, 'k'));
        assertEquals("x", field.getText());
    }

    @Test
    public void onCharTypedIgnoresControlCharacters() {
        field.onCharTyped('\t'); // 9 < 32 → ignored
        field.onCharTyped((char) 127); // DEL → ignored
        assertEquals("", field.getText());
        field.onCharTyped('A');
        assertEquals("A", field.getText());
    }

    // ------------------------------------------------------------------
    // Mouse press positions the caret
    // ------------------------------------------------------------------

    @Test
    public void mousePressInsideFieldRequestsFocusAndReturnsTrue() {
        field.setText("click here");
        FocusManager fm = FocusManager.getGlobalFocusManager();
        Panel root = new Panel(0, 0, 400, 200);
        root.setVisible(true);
        root.add(field);
        fm.setRoot(root);
        // Detach the manager's focus pointer without touching the widget's
        // own flag (clearFocus would cascade and unfocus the field too).
        fm.requestFocus(null);
        assertFalse(field.isFocused());

        boolean consumed = field.onMouseEvent(
                new MouseEvent(MouseEventType.PRESS, 10, 10, MouseButton.LEFT, 1));
        assertTrue(consumed);
        assertTrue(field.isFocused());

        // Press outside bounds is not consumed.
        assertFalse(field.onMouseEvent(
                new MouseEvent(MouseEventType.PRESS, 500, 500, MouseButton.LEFT, 1)));
        // Non-press events are not consumed either.
        assertFalse(field.onMouseEvent(
                new MouseEvent(MouseEventType.RELEASE, 10, 10, MouseButton.LEFT, 1)));
    }

    // ------------------------------------------------------------------
    // Measurement
    // ------------------------------------------------------------------

    @Test
    public void measureClampsWidthAndHeight() {
        field.setText("some longer content");
        Dimension tiny = field.measure(10, 5);
        assertEquals(10.0f, tiny.getWidth(), 1e-3);
        assertEquals(5.0f, tiny.getHeight(), 1e-3);

        Dimension roomy = field.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        assertTrue(roomy.getWidth() >= 80.0f, "minimum intrinsic width of 80");
        assertTrue(roomy.getWidth() <= Float.MAX_VALUE);
        assertTrue(field.getPreferredHeight() > 0);

        // NaN constraints behave like unbounded ones.
        Dimension nan = field.measure(Float.NaN, Float.NaN);
        assertTrue(nan.getWidth() > 0);
    }

    @Test
    public void accessibleNameFallsBackToPlaceholder() {
        field.setPlaceholder("Email");
        assertEquals("Email", field.getAccessibleName());
        field.setAccessibleName("Custom");
        assertEquals("Custom", field.getAccessibleName());
    }

    // ------------------------------------------------------------------
    // Rendering paths (raster surface)
    // ------------------------------------------------------------------

    @Test
    public void renderCoversPlaceholderSelectionAndDisabledStates() {
        Surface[] holder = new Surface[1];
        com.glyphui.graphics.Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            // Empty + unfocused → placeholder branch.
            field.setEnabled(true);
            FocusManager.getGlobalFocusManager().clearFocus(field);
            field.setPlaceholder("ph");
            field.render(canvas);

            // Focused with selection → highlight + caret branches.
            field.requestFocus();
            field.setText("hello");
            field.selectAll();
            field.render(canvas);

            // Disabled → dimmed text color branch, no caret.
            field.setEnabled(false);
            field.render(canvas);
            field.setEnabled(true);

            // Caret at both extremes.
            field.onKeyEvent(press(KEY_HOME, '\0'));
            field.render(canvas);
            field.onKeyEvent(press(KEY_END, '\0'));
            field.render(canvas);
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }
}
