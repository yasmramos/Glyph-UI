package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.ui.TextField;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Surface;
import io.github.humbleui.skija.Bitmap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
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
public class TextFieldEditingTest extends TestBase {

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
    public void everyEditingPathFiresOnTextChanged() {
        // Block-1 regression: typed characters, backspace, delete and cut all
        // mutate through setTextInternal, so the echo binding in App.java
        // updates while typing. Previously only setText(text, true) notified.
        int[] fired = {0};
        field.setOnTextChanged(() -> fired[0]++);
        field.setText("", false);
        fired[0] = 0;

        // Typed character (what onKeyEvent delivers for printable keys).
        field.insertText("x");
        assertEquals(1, fired[0], "insertText must notify");

        // Backspace key removes the char before the caret and notifies.
        field.onKeyEvent(press(KEY_BACKSPACE, '\0'));
        assertEquals("", field.getText());
        assertEquals(2, fired[0], "backspace must notify");

        // Re-populate and delete forward with the Delete key.
        field.setText("ab", false);
        field.onKeyEvent(press(KEY_HOME, '\0'));
        fired[0] = 0;
        field.onKeyEvent(press(KEY_DELETE, '\0'));
        assertEquals("b", field.getText());
        assertEquals(1, fired[0], "delete-forward must notify");

        // Cut removes the selection and notifies exactly once.
        field.selectAll();
        fired[0] = 0;
        field.onKeyEvent(press(KEY_X, 'x', KeyModifier.CTRL));
        assertEquals("", field.getText());
        assertEquals(1, fired[0], "cut must notify exactly once (no double fire)");
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
    public void insertTextDropsControlCharacters() {
        // Windows also delivers Tab through the IME/committed-text path.
        // This is a single-line field, so control characters must never
        // reach the buffer: they render as a missing-glyph box and Tab is a
        // focus key, not content.
        field.setText("ab");
        field.insertText("\t");
        assertEquals("ab", field.getText(), "a lone Tab inserts nothing");
        assertEquals(2, field.getCaret());

        field.insertText("a\tb");
        assertEquals("abab", field.getText(), "tabs are stripped, text kept");
        assertEquals(4, field.getCaret());

        field.insertText("\r\n");
        field.insertText(String.valueOf((char) 127));
        assertEquals("abab", field.getText());

        field.applyImeText("\t", -1, -1);
        field.applyImeText("\n", 0, 2); // must not delete the range either
        assertEquals("abab", field.getText(), "IME control commits are ignored");
        assertEquals(4, field.getCaret());
    }

    // ------------------------------------------------------------------
    // IME committed-text path (applyImeText)
    // ------------------------------------------------------------------

    @Test
    public void imeSentinelMinusOneAppendsAtCaretNotPositionZero() {
        // Windows reports the replacement range as (-1,-1). Each committed
        // character must land at the moving caret, so "hola" types forward.
        field.setText("");
        for (char c : "hola".toCharArray()) {
            field.applyImeText(String.valueOf(c), -1, -1);
        }
        assertEquals("hola", field.getText(), "typing must not reverse to 'aloh'");
        assertEquals(4, field.getCaret());
    }

    @Test
    public void imeEmptyRangeBehavesLikeCaretInsertion() {
        // A (0,0) range carries no information; it must be read as "at the
        // caret", not "replace the front of the buffer".
        field.setText("");
        for (char c : "hola".toCharArray()) {
            field.applyImeText(String.valueOf(c), 0, 0);
        }
        assertEquals("hola", field.getText());
    }

    @Test
    public void imeSentinelReplacesActiveSelection() {
        field.setText("hello world");
        field.selectAll();
        field.applyImeText("X", -1, -1);
        assertEquals("X", field.getText());
        assertEquals(1, field.getCaret());
    }

    @Test
    public void imeExplicitRangeReplacesThatRegion() {
        // A real composition region (start<end) is replaced exactly, with the
        // caret landing after the committed text.
        field.setText("abcdef");
        field.applyImeText("XY", 2, 4); // replace "cd"
        assertEquals("abXYef", field.getText());
        assertEquals(4, field.getCaret());
    }

    @Test
    public void imeDisabledOrEmptyCommitIsIgnored() {
        field.setText("keep");
        field.applyImeText("", -1, -1);
        field.applyImeText(null, -1, -1);
        assertEquals("keep", field.getText());

        field.setEnabled(false);
        field.applyImeText("Z", -1, -1);
        assertEquals("keep", field.getText());
        field.setEnabled(true);
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

    @Test
    public void mouseDragSelectsText() {
        field.setText("hello world");
        // Press at the left edge (maps to index 0) starts the gesture...
        assertTrue(field.onMouseEvent(
                new MouseEvent(MouseEventType.PRESS, 1, 15, MouseButton.LEFT, 1)));
        assertFalse(field.hasSelection()); // a press alone selects nothing

        // ...dragging to the right edge selects the whole content.
        assertTrue(field.onMouseEvent(
                new MouseEvent(MouseEventType.DRAG, 195, 15, MouseButton.LEFT, 1)));
        assertTrue(field.hasSelection());
        assertEquals("hello world", field.getSelectedText());
        assertEquals("hello world".length(), field.getCaret());

        // Dragging back to the start collapses the selection again.
        field.onMouseEvent(new MouseEvent(MouseEventType.DRAG, 1, 15, MouseButton.LEFT, 1));
        assertFalse(field.hasSelection());
    }

    @Test
    public void mouseClickWithoutDragSelectsNothingAndHoverEndsGesture() {
        field.setText("hello");
        field.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 1, 15, MouseButton.LEFT, 1));
        field.onMouseEvent(new MouseEvent(MouseEventType.RELEASE, 1, 15, MouseButton.LEFT, 1));
        assertFalse(field.hasSelection());

        // A hover (MOVE, no button) ends the gesture: a later DRAG without a
        // fresh press must not resurrect a selection from the old anchor.
        field.onMouseEvent(new MouseEvent(MouseEventType.MOVE, 50, 15, MouseButton.LEFT, 0));
        boolean consumed = field.onMouseEvent(
                new MouseEvent(MouseEventType.DRAG, 195, 15, MouseButton.LEFT, 1));
        assertFalse(consumed);
        assertFalse(field.hasSelection());
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
    public void preferredWidthStaysStableWhileTyping() {
        // A layout-managed field (no explicit bounds) used to derive its
        // intrinsic width from the buffer, so every keystroke invalidated the
        // row and widened the widget. Size must now follow the placeholder
        // only, keeping the layout stable while writing.
        TextField intrinsic = new TextField();
        try {
            intrinsic.setPlaceholder("Your name");
            float initial = intrinsic.getPreferredWidth();
            assertEquals(initial, intrinsic.measure(Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY).getWidth(), 1e-3);

            intrinsic.setText("a fairly long name that would have widened it");
            assertEquals(initial, intrinsic.getPreferredWidth(), 1e-3,
                    "text must not drive the intrinsic width");
            assertEquals(initial, intrinsic.measure(Float.POSITIVE_INFINITY,
                    Float.POSITIVE_INFINITY).getWidth(), 1e-3);

            intrinsic.insertText(" and even more");
            assertEquals(initial, intrinsic.getPreferredWidth(), 1e-3);
        } finally {
            intrinsic.dispose();
        }
    }

    @Test
    public void longTextIsClippedAndPannedInsideTheField() {
        Surface[] holder = new Surface[1];
        io.github.yasmramos.glyphui.graphics.Canvas canvas = RasterCanvasTestFactory.create(holder);
        TextField narrow = new TextField(0, 0, 120, 30);
        try {
            narrow.setVisible(true);
            narrow.requestFocus();
            narrow.setText("abcdefghijklmnopqrstuvwxyz0123456789");
            narrow.renderInternal(canvas);

            byte[] pixels = readPixels(holder[0]);
            int surfaceWidth = holder[0].getWidth();

            // Nothing may be painted to the right of the border. The field
            // keeps its width, so overflowing text used to spill over the
            // frame and look like the widget was growing.
            for (int px = 121; px < 200; px++) {
                assertEquals(0, argbAt(pixels, surfaceWidth, px, 15) >>> 24,
                        "text painted outside the field at x=" + px);
            }

            // The caret must remain visible: the text pans instead of being
            // silently clipped away. (The focused border is also accent
            // coloured, so sample the inner area only.)
            int accent = narrow.getTheme().getAccentColor();
            boolean caretVisible = false;
            for (int px = 100; px < 117 && !caretVisible; px++) {
                for (int py = 5; py < 25; py++) {
                    if (argbAt(pixels, surfaceWidth, px, py) == accent) {
                        caretVisible = true;
                        break;
                    }
                }
            }
            assertTrue(caretVisible, "caret must be panned into view");
        } finally {
            narrow.dispose();
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }

    /** Reads the raster surface as N32 premultiplied bytes ([B, G, R, A]). */
    private static byte[] readPixels(Surface surface) {
        int w = surface.getWidth();
        int h = surface.getHeight();
        Bitmap bitmap = new Bitmap();
        try {
            assertTrue(bitmap.allocN32Pixels(w, h), "allocN32Pixels failed");
            assertTrue(surface.readPixels(bitmap, 0, 0), "readPixels failed");
            ByteBuffer buffer = bitmap.peekPixels();
            buffer.rewind();
            byte[] pixels = new byte[buffer.remaining()];
            buffer.get(pixels);
            return pixels;
        } finally {
            bitmap.close();
        }
    }

    private static int argbAt(byte[] pixels, int surfaceWidth, int x, int y) {
        int offset = (y * surfaceWidth + x) * 4;
        return (pixels[offset] & 0xFF) | ((pixels[offset + 1] & 0xFF) << 8)
                | ((pixels[offset + 2] & 0xFF) << 16) | ((pixels[offset + 3] & 0xFF) << 24);
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
        io.github.yasmramos.glyphui.graphics.Canvas canvas = RasterCanvasTestFactory.create(holder);
        try {
            // Empty + unfocused → placeholder branch.
            field.setEnabled(true);
            FocusManager.getGlobalFocusManager().clearFocus(field);
            field.setPlaceholder("ph");
            field.renderInternal(canvas);

            // Focused with selection → highlight + caret branches.
            field.requestFocus();
            field.setText("hello");
            field.selectAll();
            field.renderInternal(canvas);

            // Disabled → dimmed text color branch, no caret.
            field.setEnabled(false);
            field.renderInternal(canvas);
            field.setEnabled(true);

            // Caret at both extremes.
            field.onKeyEvent(press(KEY_HOME, '\0'));
            field.renderInternal(canvas);
            field.onKeyEvent(press(KEY_END, '\0'));
            field.renderInternal(canvas);
        } finally {
            if (holder[0] != null) {
                holder[0].close();
            }
        }
    }
}
