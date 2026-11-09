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
import io.github.yasmramos.glyphui.graphics.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Table}: column and row management, mouse and
 * keyboard selection, the scrolling window and the selection handler.
 */
class TableTest extends TestBase {

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

    private static Table sizedTable(int rows) {
        Table table = new Table("Name", "Value");
        for (int i = 0; i < rows; i++) {
            table.addRow("Row " + i, String.valueOf(i));
        }
        table.setWidth(200.0f);
        table.setHeight(Table.HEADER_HEIGHT + rows * Table.ROW_HEIGHT);
        return table;
    }

    @Test
    void columnsAndRowsAreTracked() {
        Table table = new Table("A", "B", "C");
        table.addRow("1", "2", "3");
        table.addRow("4", "5", "6");

        assertEquals(3, table.getColumnCount());
        assertEquals(2, table.getRowCount());
        assertEquals("B", table.getColumnTitle(1));
        assertEquals("5", table.getCell(1, 1));
    }

    @Test
    void missingCellsBecomeEmpty() {
        Table table = new Table("A", "B");
        table.addRow("only one");

        assertEquals("only one", table.getCell(0, 0));
        assertEquals("", table.getCell(0, 1));
    }

    @Test
    void cellOutsideTheRowReadsEmpty() {
        Table table = new Table("A");
        table.addRow("x");

        assertEquals("", table.getCell(0, 9));
    }

    @Test
    void removeColumnDropsItsCells() {
        Table table = new Table("A", "B", "C");
        table.addRow("1", "2", "3");

        table.removeColumn(1);

        assertEquals(2, table.getColumnCount());
        assertEquals("3", table.getCell(0, 1));
    }

    @Test
    void removeColumnRejectsBadIndex() {
        Table table = new Table("A");
        assertThrows(IndexOutOfBoundsException.class, () -> table.removeColumn(3));
    }

    @Test
    void removeRowRejectsBadIndex() {
        Table table = sizedTable(2);
        assertThrows(IndexOutOfBoundsException.class, () -> table.removeRow(5));
    }

    @Test
    void clickingARowSelectsIt() {
        Table table = sizedTable(4);
        float rowY = Table.HEADER_HEIGHT + 1.5f * Table.ROW_HEIGHT;

        table.onMouseEvent(mouse(MouseEventType.PRESS, 10, rowY));
        table.onMouseEvent(mouse(MouseEventType.RELEASE, 10, rowY));

        assertEquals(1, table.getSelectedIndex());
    }

    @Test
    void clickingTheHeaderSelectsNothing() {
        Table table = sizedTable(4);

        table.onMouseEvent(mouse(MouseEventType.PRESS, 10, 5));
        table.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 5));

        assertEquals(-1, table.getSelectedIndex());
    }

    @Test
    void arrowKeysMoveTheSelection() {
        Table table = sizedTable(5);
        table.setSelectedIndex(0);

        table.onKeyEvent(key(GlyphKeys.DOWN));
        table.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals(2, table.getSelectedIndex());

        table.onKeyEvent(key(GlyphKeys.UP));
        assertEquals(1, table.getSelectedIndex());
    }

    @Test
    void homeAndEndJumpToTheBounds() {
        Table table = sizedTable(5);

        table.onKeyEvent(key(GlyphKeys.END));
        assertEquals(4, table.getSelectedIndex());

        table.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0, table.getSelectedIndex());
    }

    @Test
    void pageKeysMoveByOneScreen() {
        Table table = new Table("A");
        for (int i = 0; i < 40; i++) {
            table.addRow("Row " + i);
        }
        table.setWidth(160.0f);
        table.setHeight(Table.HEADER_HEIGHT + 6 * Table.ROW_HEIGHT);
        table.setSelectedIndex(0);

        table.onKeyEvent(key(GlyphKeys.PAGE_DOWN));
        int afterPageDown = table.getSelectedIndex();
        assertTrue(afterPageDown > 0, "Page Down must advance by a screen");

        table.onKeyEvent(key(GlyphKeys.PAGE_UP));
        assertEquals(0, table.getSelectedIndex());
    }

    @Test
    void movingTheSelectionFiresTheHandler() {
        Table table = sizedTable(3);
        List<Integer> seen = new ArrayList<>();
        table.setOnSelect(seen::add);

        table.onKeyEvent(key(GlyphKeys.DOWN));
        table.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals(List.of(0, 1), seen);
    }

    @Test
    void programmaticSelectionDoesNotFireTheHandler() {
        Table table = sizedTable(3);
        List<Integer> seen = new ArrayList<>();
        table.setOnSelect(seen::add);

        table.setSelectedIndex(2);

        assertTrue(seen.isEmpty());
        assertEquals(2, table.getSelectedIndex());
    }

    @Test
    void userDrivenSelectionKeepsTheObservablePropertyInSync() {
        Table table = sizedTable(4);
        Property<Integer> selection = table.selectedIndexProperty();

        table.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals(0, selection.get());
        assertEquals(0, table.getSelectedIndex());
    }

    @Test
    void setRowsKeepsTheObservablePropertyInSync() {
        Table table = sizedTable(4);
        table.setSelectedIndex(3);
        Property<Integer> selection = table.selectedIndexProperty();
        assertEquals(3, selection.get());

        table.setRows(List.<String[]>of(new String[] {"only"}));

        assertEquals(0, selection.get());
        assertEquals(0, table.getSelectedIndex());
    }

    @Test
    void removeRowKeepsTheObservablePropertyInSync() {
        Table table = sizedTable(3);
        table.setSelectedIndex(2);
        Property<Integer> selection = table.selectedIndexProperty();

        table.removeRow(0);

        assertEquals(1, selection.get());
        assertEquals(1, table.getSelectedIndex());
    }

    @Test
    void removingTheSelectedRowSelectsTheNextOne() {
        Table table = sizedTable(3);
        table.setSelectedIndex(1);

        table.removeRow(1);

        assertEquals(1, table.getSelectedIndex());
    }

    @Test
    void removingTheLastRowClearsTheSelection() {
        Table table = sizedTable(1);
        table.setSelectedIndex(0);

        table.removeRow(0);

        assertEquals(-1, table.getSelectedIndex());
    }

    @Test
    void handlerFailureIsSwallowed() {
        Table table = sizedTable(3);
        table.setOnSelect(index -> {
            throw new IllegalStateException("boom");
        });

        table.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals(0, table.getSelectedIndex());
    }

    @Test
    void disabledTableIgnoresInput() {
        Table table = sizedTable(3);
        table.setEnabled(false);

        table.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals(-1, table.getSelectedIndex());
    }

    @Test
    void preferredSizeGrowsWithRowsAndColumns() {
        Table one = new Table("A");
        one.addRow("x");
        Table three = new Table("A", "B", "C");
        three.addRow("x", "y", "z");

        assertTrue(three.getPreferredWidth() > one.getPreferredWidth());
        assertEquals(Table.HEADER_HEIGHT + Table.ROW_HEIGHT, one.getPreferredHeight());
    }

    @Test
    void fixedColumnWidthIsReportedAndAutoIsNegative() {
        Table table = new Table("A", "B");

        assertEquals(-1.0f, table.getColumnWidth(0));
        table.setColumnWidth(0, 80.0f);
        assertEquals(80.0f, table.getColumnWidth(0));
    }

    @Test
    void selectedRowExposesItsCells() {
        Table table = sizedTable(2);
        table.setSelectedIndex(1);

        assertEquals(List.of("Row 1", "1"), table.getSelectedRow());
    }

    @Test
    void selectedRowIsNullWithoutSelection() {
        Table table = sizedTable(2);
        assertFalse(table.getSelectedRow() != null);
    }

    @Test
    void accessibleRoleIsTable() {
        assertEquals(AccessibleRole.TABLE, new Table().getAccessibleRole());
    }

    @Test
    void theWheelScrollsTheBodyWithoutChangingSelection() {
        Table table = new Table("Name", "Value");
        for (int i = 0; i < 20; i++) {
            table.addRow("Row " + i, String.valueOf(i));
        }
        // Three body rows fit below the header.
        table.setWidth(200.0f);
        table.setHeight(Table.HEADER_HEIGHT + 3 * Table.ROW_HEIGHT);
        table.setSelectedIndex(0);

        boolean consumed = table.onScroll(new ScrollEvent(0f, 2f, 20, 40));

        assertTrue(consumed);
        assertEquals(0, table.getSelectedIndex(),
                "the wheel scrolls the view without moving the selection");
    }

    @Test
    void theWheelClampsAtTheEndOfTheBody() {
        Table table = new Table("Name", "Value");
        for (int i = 0; i < 6; i++) {
            table.addRow("Row " + i, String.valueOf(i));
        }
        table.setWidth(200.0f);
        table.setHeight(Table.HEADER_HEIGHT + 3 * Table.ROW_HEIGHT);

        assertTrue(table.onScroll(new ScrollEvent(0f, 100f, 20, 40)));
        assertFalse(table.onScroll(new ScrollEvent(0f, 1f, 20, 40)),
                "a scroll at the end of the body is not consumed");
    }
}
