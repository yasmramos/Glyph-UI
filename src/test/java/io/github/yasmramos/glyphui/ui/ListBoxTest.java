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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link ListBox}: item management, click and keyboard
 * selection, scrolling window and the selection handler.
 */
class ListBoxTest extends TestBase {

    @BeforeEach
    void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ' ',
                EnumSet.noneOf(KeyModifier.class));
    }

    private static ListBox sizedList(int rows) {
        ListBox list = new ListBox();
        for (int i = 0; i < rows; i++) {
            list.addItem("Item " + i);
        }
        list.setWidth(160.0f);
        list.setHeight(rows * ListBox.ROW_HEIGHT);
        return list;
    }

    @Test
    void defaultsToEmptyAndUnselected() {
        ListBox list = new ListBox();

        assertEquals(0, list.getItemCount());
        assertEquals(-1, list.getSelectedIndex());
        assertEquals(null, list.getSelectedItem());
        assertTrue(list.isFocusable());
        assertEquals(AccessibleRole.LIST_BOX, list.getAccessibleRole());
    }

    @Test
    void varargsConstructorAddsItemsAndNullBecomesEmpty() {
        ListBox list = new ListBox("one", null, "three");

        assertEquals(3, list.getItemCount());
        assertEquals("one", list.getItem(0));
        assertEquals("", list.getItem(1));
        assertEquals("three", list.getItem(2));
    }

    @Test
    void clickingARowSelectsIt() {
        ListBox list = sizedList(4);
        List<Integer> selections = new ArrayList<>();
        list.setOnSelect(selections::add);

        // Row 2 occupies [48, 72); click its middle.
        list.onMouseEvent(mouse(MouseEventType.PRESS, 20, 60));
        list.onMouseEvent(mouse(MouseEventType.RELEASE, 20, 60));

        assertEquals(2, list.getSelectedIndex());
        assertEquals("Item 2", list.getSelectedItem());
        assertEquals(List.of(2), selections);
    }

    @Test
    void keyboardArrowsMoveTheSelection() {
        ListBox list = sizedList(4);

        list.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals(0, list.getSelectedIndex());

        list.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals(1, list.getSelectedIndex());

        list.onKeyEvent(key(GlyphKeys.END));
        assertEquals(3, list.getSelectedIndex());

        list.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0, list.getSelectedIndex());
    }

    @Test
    void selectionScrollsTheVisibleWindow() {
        ListBox list = new ListBox();
        for (int i = 0; i < 20; i++) {
            list.addItem("Item " + i);
        }
        // Only three rows fit, so selecting row 10 must scroll the window.
        list.setWidth(160.0f);
        list.setHeight(3 * ListBox.ROW_HEIGHT);

        list.setSelectedIndex(10);

        // The window scrolled so row 10 is the bottom visible row; clicking the
        // top row of the window therefore selects row 8, not row 0.
        list.onMouseEvent(mouse(MouseEventType.PRESS, 20, 5));
        list.onMouseEvent(mouse(MouseEventType.RELEASE, 20, 5));

        assertEquals(8, list.getSelectedIndex());
    }

    @Test
    void selectionHandlerOnlyFiresOnRealChanges() {
        ListBox list = sizedList(3);
        List<Integer> selections = new ArrayList<>();
        list.setOnSelect(selections::add);

        list.onKeyEvent(key(GlyphKeys.DOWN));
        list.onKeyEvent(key(GlyphKeys.DOWN));
        list.onKeyEvent(key(GlyphKeys.DOWN));
        // Already on the last item: further DOWN/END are no-ops.
        list.onKeyEvent(key(GlyphKeys.DOWN));
        list.onKeyEvent(key(GlyphKeys.END));

        assertEquals(List.of(0, 1, 2), selections);
    }

    @Test
    void removingItemsFixesUpTheSelection() {
        ListBox list = sizedList(3);
        list.setSelectedIndex(2);

        list.removeItem(2);

        assertEquals(2, list.getItemCount());
        assertEquals(1, list.getSelectedIndex());
    }

    @Test
    void disabledListIgnoresInput() {
        ListBox list = sizedList(4);
        list.setEnabled(false);

        assertFalse(list.onMouseEvent(mouse(MouseEventType.PRESS, 20, 60)));
        list.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals(-1, list.getSelectedIndex());
    }

    @Test
    void handlerFailureIsSwallowed() {
        ListBox list = sizedList(4);
        list.setOnSelect(index -> {
            throw new IllegalStateException("boom");
        });

        list.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals(0, list.getSelectedIndex());
    }
}
