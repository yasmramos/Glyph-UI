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
    void mousePressOnALowerLineMovesTheCaretThere() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-metric test");
        area.setText("aa\nbb\ncc");

        area.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 5, 95,
                MouseButton.LEFT, 1));

        assertTrue(area.getCaret() >= 6,
                "clicking near the bottom must place the caret on the last line");
    }
}
