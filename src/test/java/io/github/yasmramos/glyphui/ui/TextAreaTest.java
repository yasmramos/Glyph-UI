package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.events.ScrollEvent;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behaviour tests for {@link TextArea}: multi-line insertion, newline-aware
 * caret navigation, backspace across a line break and line-based intrinsic
 * sizing. Editing runs headlessly through the key/mouse event API.
 */
class TextAreaTest extends TestBase {

    private TextArea area;

    @BeforeEach
    void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
        area = new TextArea(0, 0, 200, 100);
        area.setVisible(true);
        area.requestFocus();
    }

    @AfterEach
    void tearDown() {
        if (area != null) {
            area.dispose();
        }
    }

    private static KeyEvent key(int keyCode, char ch, KeyModifier... mods) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ch,
                mods.length == 0 ? EnumSet.noneOf(KeyModifier.class)
                                 : EnumSet.of(mods[0]));
    }

    private static KeyEvent key(int keyCode) {
        return key(keyCode, ' ');
    }

    private static String lines(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append('l').append(i);
        }
        return sb.toString();
    }

    @Test
    void reportsTheTextAreaAccessibleRole() {
        assertEquals(AccessibleRole.TEXT_AREA, area.getAccessibleRole());
    }

    @Test
    void enterInsertsANewlineInsteadOfSubmitting() {
        boolean[] submitted = {false};
        area.setOnSubmit(() -> submitted[0] = true);
        area.setText("ab");

        area.onKeyEvent(key(GlyphKeys.ENTER));

        assertEquals("ab\n", area.getText());
        assertFalse(submitted[0], "a text area must not fire the submit handler");
    }

    @Test
    void insertTextKeepsNewlinesAndNormalisesCarriageReturns() {
        area.insertText("line1\r\nline2\rline3");

        assertEquals("line1\nline2\nline3", area.getText());
    }

    @Test
    void insertTextDropsOtherControlCharacters() {
        area.insertText("a\u0001b");

        assertEquals("ab", area.getText());
    }

    @Test
    void upMovesToTheSameColumnOfThePreviousLine() {
        area.setText("ab\ncdef\ng");
        // Caret sits at the end (index 9, last line, column 0).
        area.onKeyEvent(key(GlyphKeys.UP));
        assertEquals(4, area.getCaret(), "up lands on the middle line at the same column");
    }

    @Test
    void downClampsTheColumnToTheTargetLineLength() {
        area.setText("ab\ncdef\ng");
        area.onKeyEvent(key(GlyphKeys.UP)); // last line (col 1) -> middle line, index 4
        assertEquals(4, area.getCaret());

        area.onKeyEvent(key(GlyphKeys.UP)); // -> first line, same column, index 1
        assertEquals(1, area.getCaret());

        area.onKeyEvent(key(GlyphKeys.DOWN)); // back to the middle line, index 4
        assertEquals(4, area.getCaret());

        area.onKeyEvent(key(GlyphKeys.DOWN)); // last line is shorter: clamp to its end
        assertEquals(9, area.getCaret());
    }

    @Test
    void upOnTheFirstLineGoesToTheStart() {
        area.setText("hello\nworld");
        area.onKeyEvent(key(GlyphKeys.UP)); // line 0, column 5 (index 5)
        assertEquals(5, area.getCaret());

        area.onKeyEvent(key(GlyphKeys.UP)); // already on the first line: go to index 0
        assertEquals(0, area.getCaret());
    }

    @Test
    void downOnTheLastLineGoesToTheEnd() {
        area.setText("hello\nworld");
        area.onKeyEvent(key(GlyphKeys.HOME));  // index 6
        area.onKeyEvent(key(GlyphKeys.RIGHT)); // index 7

        area.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals("hello\nworld".length(), area.getCaret());
    }

    @Test
    void homeAndEndAreLineRelative() {
        area.setText("abc\ndefgh");
        area.onKeyEvent(key(GlyphKeys.HOME)); // start of the last line (index 4)
        assertEquals(4, area.getCaret());

        area.onKeyEvent(key(GlyphKeys.END)); // end of the last line
        assertEquals(9, area.getCaret());
    }

    @Test
    void ctrlHomeAndEndJumpToTheBufferEnds() {
        area.setText("abc\ndefgh\nij");

        area.onKeyEvent(key(GlyphKeys.END, ' ', KeyModifier.CTRL));
        assertEquals(area.getText().length(), area.getCaret(),
                "Ctrl+End goes to the buffer end, not the line end");

        area.onKeyEvent(key(GlyphKeys.HOME, ' ', KeyModifier.CTRL));
        assertEquals(0, area.getCaret(),
                "Ctrl+Home goes to the buffer start, not the line start");
    }

    @Test
    void pageDownAndPageUpMoveByAViewport() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText(lines(20));
        area.onKeyEvent(key(GlyphKeys.HOME, ' ', KeyModifier.CTRL)); // caret 0

        area.onKeyEvent(key(GlyphKeys.PAGE_DOWN));
        int afterPageDown = area.getCaret();
        assertTrue(afterPageDown > 0, "Page Down advances the caret");
        assertTrue(area.getText().charAt(afterPageDown - 1) == '\n',
                "Page Down keeps the original column (0) at the target line start");

        area.onKeyEvent(key(GlyphKeys.PAGE_UP));
        assertEquals(0, area.getCaret(), "Page Up returns to the first line");
    }

    @Test
    void repeatedPageDownClampsToTheEnd() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText(lines(20));
        area.onKeyEvent(key(GlyphKeys.HOME, ' ', KeyModifier.CTRL));

        for (int i = 0; i < 50; i++) {
            area.onKeyEvent(key(GlyphKeys.PAGE_DOWN));
        }
        assertEquals(area.getText().length(), area.getCaret(),
                "Page Down clamps to the end of the buffer");
    }

    @Test
    void backspaceDeletesAcrossALineBreak() {
        area.setText("ab\ncd");
        area.onKeyEvent(key(GlyphKeys.HOME)); // start of the last line (index 3)

        area.onKeyEvent(key(GlyphKeys.BACKSPACE));

        assertEquals("abcd", area.getText());
        assertEquals(2, area.getCaret());
    }

    @Test
    void ctrlASelectsAllAndTypingReplacesTheSelection() {
        area.setText("abc\ndef");
        area.onKeyEvent(key(GlyphKeys.A, 'a', KeyModifier.CTRL));

        area.insertText("Z");

        assertEquals("Z", area.getText());
    }

    @Test
    void explicitSizeWinsOverContentMeasurement() {
        TextArea sized = new TextArea(0, 0, 120, 60);

        Dimension measured = sized.measure(1000, 1000);

        assertEquals(120f, measured.getWidth(), 1e-6);
        assertEquals(60f, measured.getHeight(), 1e-6);
        sized.dispose();
    }

    @Test
    void intrinsicHeightGrowsWithLineCount() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        TextArea unsized = new TextArea();
        unsized.setVisible(true);
        unsized.setText("one");

        float single = unsized.getPreferredHeight();
        unsized.setText("one\ntwo\nthree\nfour\nfive\nsix\nseven\neight\nnine\nten");

        assertTrue(unsized.getPreferredHeight() > single,
                "more lines must require more height");
        unsized.dispose();
    }

    @Test
    void draggingFromAPressExtendsTheSelection() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText("hello\nworld");

        area.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 8, 20, MouseButton.LEFT, 1));
        area.onMouseEvent(new MouseEvent(MouseEventType.DRAG, 195, 75, MouseButton.LEFT, 1));

        assertTrue(area.hasSelection(),
                "dragging with the button held must extend a selection");
        area.insertText("X");
        assertTrue(area.getText().startsWith("X"),
                "typing over the selection must replace the selected prefix");
    }

    @Test
    void releasingEndsTheSelectionGesture() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText("hello\nworld");

        area.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 8, 20, MouseButton.LEFT, 1));
        area.onMouseEvent(new MouseEvent(MouseEventType.RELEASE, 8, 20, MouseButton.LEFT, 0));
        // A drag after release must not extend the selection.
        area.onMouseEvent(new MouseEvent(MouseEventType.DRAG, 195, 75, MouseButton.LEFT, 0));

        assertFalse(area.hasSelection(),
                "a drag after release must not extend the selection");
    }

    @Test
    void mousePressOnALowerLineMovesTheCaretThere() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText("aa\nbb\ncc");

        area.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 5, 95,
                MouseButton.LEFT, 1));

        assertTrue(area.getCaret() >= 6,
                "clicking near the bottom must place the caret on the last line");
    }

    @Test
    void theWheelScrollsLongTextAndStopsAtTheEnd() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText("l0\nl1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9");

        // setText reveals the caret (bottom); start from the top explicitly.
        assertTrue(area.onScroll(new ScrollEvent(0f, -1000f, 10, 10)),
                "the wheel scrolls back to the top");
        assertTrue(area.onScroll(new ScrollEvent(0f, 3f, 10, 10)),
                "a wheel that moves a long buffer is consumed");
        assertTrue(area.onScroll(new ScrollEvent(0f, 1000f, 10, 10)),
                "a large wheel clamps to the last line and is consumed");
        assertFalse(area.onScroll(new ScrollEvent(0f, 3f, 10, 10)),
                "scrolling past the end is a no-op once the bottom is reached");
    }

    @Test
    void aBufferThatFitsDoesNotConsumeTheWheel() {
        area.setText("only one line");

        assertFalse(area.onScroll(new ScrollEvent(0f, 3f, 10, 10)),
                "nothing to scroll means the gesture bubbles up");
    }

    @Test
    void theScrollbarPaintsOnTheTrailingEdge() {
        // width 200: bar spans [190, 198).
        area.setText("l0\nl1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9");
        ScrollbarRaster.assertBarPaintsOnTrailingEdge(area, 190);
    }

    @Test
    void draggingTheThumbScrollsTheText() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText("l0\nl1\nl2\nl3\nl4\nl5\nl6\nl7\nl8\nl9");

        // setText reveals the caret (bottom); start at the top so the thumb is
        // at the top of the track before the drag.
        area.onScroll(new ScrollEvent(0f, -1000f, 10, 10));

        // Press the thumb near the top of the bar and drag it to the bottom.
        area.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 195, 10, MouseButton.LEFT, 1));
        area.onMouseEvent(new MouseEvent(MouseEventType.DRAG, 195, 200, MouseButton.LEFT, 1));
        area.onMouseEvent(new MouseEvent(MouseEventType.RELEASE, 195, 200, MouseButton.LEFT, 0));

        // Scrolled to the end, so a click near the top lands on a late line.
        area.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 8, 12, MouseButton.LEFT, 1));
        assertTrue(area.getCaret() >= 9, "the thumb drag scrolled the buffer to the end");
    }
}
