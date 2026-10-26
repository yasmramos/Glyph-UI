package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link Menu}: building rows, sizing the popup from its
 * content, stacking rows top to bottom, and positioning it against the
 * trigger (below it, flipping above when there is no room). Everything runs
 * headless: no {@code Application}, no window.
 */
public class MenuTest extends TestBase {

    @Test
    public void menuIsClosedUntilOpenedAndReportsItsRole() {
        Menu menu = new Menu();
        assertFalse(menu.isOpen());
        assertFalse(menu.isVisible());
        assertEquals(AccessibleRole.MENU, menu.getAccessibleRole());

        menu.open();
        assertTrue(menu.isOpen());
        assertTrue(menu.isVisible());

        menu.close();
        assertFalse(menu.isOpen());
    }

    @Test
    public void toggleOpensAndClosesAndCloseIsIdempotent() {
        Menu menu = new Menu();

        menu.toggle();
        assertTrue(menu.isOpen());
        menu.toggle();
        assertFalse(menu.isOpen());

        // Closing an already closed menu must be a no-op, not an error.
        menu.close();
        assertFalse(menu.isOpen());
    }

    @Test
    public void buildingHelpersAppendRowsAndSeparatorsInOrder() {
        Menu menu = new Menu();
        MenuItem first = menu.addItem("Nuevo");
        MenuSeparator separator = menu.addSeparator();
        MenuItem second = menu.addItem("Abrir");

        List<Component> rows = menu.getChildren();
        assertEquals(3, rows.size());
        assertSame(first, rows.get(0));
        assertSame(separator, rows.get(1));
        assertSame(second, rows.get(2));
        assertEquals(AccessibleRole.MENU_SEPARATOR, separator.getAccessibleRole());
    }

    @Test
    public void menuMeasuresItselfFromItsRows() {
        Menu menu = new Menu();
        menu.addItem("Guardar");
        menu.addItem("Salir");
        menu.addSeparator();

        float widestRow = 0.0f;
        float totalRows = 0.0f;
        for (Component row : menu.getChildren()) {
            widestRow = Math.max(widestRow, row.getPreferredWidth());
            totalRows += row.getPreferredHeight();
        }

        assertEquals(2.0f * Menu.PADDING + widestRow, menu.getPreferredWidth(), 0.01f);
        assertEquals(2.0f * Menu.PADDING + totalRows, menu.getPreferredHeight(), 0.01f);
        // A separator adds height but never width.
        assertTrue(menu.getPreferredHeight() > 2.0f * Menu.PADDING + 2.0f
                * menu.getChildren().get(0).getPreferredHeight());
    }

    @Test
    public void rowsStackTopToBottomInsideThePopup() {
        Menu menu = new Menu();
        menu.addItem("Primero");
        menu.addItem("Segundo");
        menu.addSeparator();
        menu.addItem("Tercero");

        menu.open();
        menu.doLayout();

        List<Component> rows = menu.getChildren();
        float innerWidth = menu.getWidth() - 2.0f * Menu.PADDING;
        float expectedY = Menu.PADDING;
        for (Component row : rows) {
            assertEquals(Menu.PADDING, row.getX(), 0.01f, "rows align to the left inset");
            assertEquals(expectedY, row.getY(), 0.01f, "rows stack without gaps");
            assertEquals(innerWidth, row.getWidth(), 0.01f, "rows stretch to the popup width");
            expectedY += row.getHeight();
        }
        // The stack must end exactly at the bottom inset (no drift).
        assertEquals(menu.getHeight() - Menu.PADDING, expectedY, 0.01f);
    }

    @Test
    public void aClosedMenuDoesNotLayOutItsRows() {
        Menu menu = new Menu();
        menu.addItem("Oculto");

        // Rows start at the origin: without open()+doLayout() nothing has
        // positioned them yet, so a closed popup is never hit-testable.
        assertEquals(0.0f, menu.getChildren().get(0).getY());
    }

    @Test
    public void openPlacesThePopupBelowItsTrigger() {
        Panel root = new Panel(0, 0, 400, 300);
        Button trigger = new Button(100, 40, 60, 24, "Archivo");
        root.add(trigger);

        Menu menu = new Menu();
        root.add(menu);
        menu.addItem("Guardar");
        menu.setTrigger(trigger);

        menu.open();

        assertEquals(100.0f, menu.getX(), 0.01f, "left-aligned with the trigger");
        assertEquals(40.0f + 24.0f, menu.getY(), 0.01f, "hangs directly below the trigger");
        assertTrue(menu.getY() + menu.getHeight() <= 300.0f, "fits on screen");
    }

    @Test
    public void openFlipsAboveTheTriggerWhenThereIsNoRoomBelow() {
        Panel root = new Panel(0, 0, 400, 300);
        // Trigger close to the bottom edge: the menu cannot hang below it.
        Button trigger = new Button(100, 280, 60, 24, "Archivo");
        root.add(trigger);

        Menu menu = new Menu();
        root.add(menu);
        menu.addItem("Guardar");
        menu.addItem("Salir");
        menu.setTrigger(trigger);

        menu.open();

        assertEquals(100.0f, menu.getX(), 0.01f);
        assertEquals(280.0f - menu.getHeight(), menu.getY(), 0.01f,
                "flips above the trigger instead of clipping off-screen");
        assertTrue(menu.getY() >= 0.0f, "never escapes the top edge");
    }

    @Test
    public void openLaysTheRowsOutSoTheFirstClickAlreadyHits() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Guardar");
        boolean[] fired = {false};
        item.setOnClick(() -> fired[0] = true);

        // No explicit doLayout(): open() must leave the rows hit-testable,
        // because a pointer press can arrive before the first painted frame.
        menu.open();

        assertEquals(Menu.PADDING, item.getY(), 0.01f);
        assertTrue(item.getHeight() > 0.0f, "rows must have bounds right after open()");

        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        item.onMouseEvent(new MouseEvent(MouseEventType.PRESS, x, y, MouseButton.LEFT, 1));
        item.onMouseEvent(new MouseEvent(MouseEventType.RELEASE, x, y, MouseButton.LEFT, 1));

        assertTrue(fired[0], "a row must be clickable as soon as the menu opens");
        assertFalse(menu.isOpen());
    }

    @Test
    public void aMenuWithoutATriggerKeepsItsOwnPosition() {
        Menu menu = new Menu();
        menu.addItem("Suelto");
        menu.setX(50.0f);
        menu.setY(60.0f);

        menu.open();

        assertEquals(50.0f, menu.getX(), 0.01f);
        assertEquals(60.0f, menu.getY(), 0.01f);
    }

    // ------------------------------------------------------------------
    // Keyboard navigation
    // ------------------------------------------------------------------

    /** Builds a key-press event with no modifiers. */
    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, '\0',
                EnumSet.noneOf(KeyModifier.class));
    }

    @Test
    public void downAndUpWalkEnabledRowsAndWrapAtTheEdges() {
        Menu menu = new Menu();
        MenuItem first = menu.addItem("Primero");
        menu.addSeparator();
        MenuItem disabled = menu.addItem("Deshabilitado");
        disabled.setEnabled(false);
        MenuItem last = menu.addItem("Último");
        menu.open();

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        assertSame(first, menu.getHighlightedItem());
        assertEquals(ComponentState.HOVER, first.getState());

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        assertSame(last, menu.getHighlightedItem(),
                "separators and disabled rows are skipped");
        assertEquals(ComponentState.IDLE, first.getState(),
                "the previous row loses the highlight");

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        assertSame(first, menu.getHighlightedItem(), "Down wraps to the first row");

        menu.onKeyEvent(key(GlyphKeys.UP));
        assertSame(last, menu.getHighlightedItem(), "Up wraps to the last row");
    }

    @Test
    public void upFromNothingSelectsTheLastRowAndHomeEndJumpToTheEdges() {
        Menu menu = new Menu();
        MenuItem first = menu.addItem("Uno");
        MenuItem last = menu.addItem("Dos");
        menu.open();

        menu.onKeyEvent(key(GlyphKeys.UP));
        assertSame(last, menu.getHighlightedItem());

        menu.onKeyEvent(key(GlyphKeys.HOME));
        assertSame(first, menu.getHighlightedItem());

        menu.onKeyEvent(key(GlyphKeys.END));
        assertSame(last, menu.getHighlightedItem());
    }

    @Test
    public void aDisabledRowIsNeverSelectedByTheArrows() {
        Menu menu = new Menu();
        MenuItem disabled = menu.addItem("Pegar");
        disabled.setEnabled(false);
        MenuItem enabled = menu.addItem("Copiar");
        menu.open();

        menu.onKeyEvent(key(GlyphKeys.DOWN));

        assertSame(enabled, menu.getHighlightedItem());
    }

    @Test
    public void enterActivatesTheHighlightedRowAndClosesTheMenu() {
        Menu menu = new Menu();
        menu.addItem("Primero");
        MenuItem target = menu.addItem("Guardar");
        boolean[] fired = {false};
        target.setOnClick(() -> fired[0] = true);
        menu.open();

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        menu.onKeyEvent(key(GlyphKeys.DOWN));
        menu.onKeyEvent(key(GlyphKeys.ENTER));

        assertTrue(fired[0], "Enter runs the highlighted row");
        assertFalse(menu.isOpen(), "picking a row closes the menu");
        assertNull(menu.getHighlightedItem(), "closing clears the selection");
    }

    @Test
    public void spaceActivatesTheHighlightedRowToo() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Abrir");
        boolean[] fired = {false};
        item.setOnClick(() -> fired[0] = true);
        menu.open();

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        menu.onKeyEvent(key(GlyphKeys.SPACE));

        assertTrue(fired[0]);
        assertFalse(menu.isOpen());
    }

    @Test
    public void activatingWithoutAHighlightDoesNothing() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Guardar");
        boolean[] fired = {false};
        item.setOnClick(() -> fired[0] = true);
        menu.open();

        menu.onKeyEvent(key(GlyphKeys.ENTER));
        menu.onKeyEvent(key(GlyphKeys.SPACE));

        assertFalse(fired[0], "no row is selected yet");
        assertTrue(menu.isOpen());
    }

    @Test
    public void aCheckableRowTogglesFromTheKeyboard() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Barra de estado");
        item.setCheckable(true);
        menu.open();

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        menu.onKeyEvent(key(GlyphKeys.ENTER));

        assertTrue(item.isChecked());
    }

    @Test
    public void keyReleasesDoNotMoveOrActivateRows() {
        Menu menu = new Menu();
        MenuItem first = menu.addItem("Uno");
        menu.addItem("Dos");
        menu.open();

        menu.onKeyEvent(new KeyEvent(KeyEventType.RELEASE, GlyphKeys.DOWN, '\0',
                EnumSet.noneOf(KeyModifier.class)));
        assertNull(menu.getHighlightedItem(), "only presses move the highlight");

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        menu.onKeyEvent(new KeyEvent(KeyEventType.RELEASE, GlyphKeys.ENTER, '\0',
                EnumSet.noneOf(KeyModifier.class)));

        assertSame(first, menu.getHighlightedItem());
        assertTrue(menu.isOpen(), "a release must not activate the row");
    }

    @Test
    public void typingALetterJumpsBetweenRowsWithThatInitial() {
        Menu menu = new Menu();
        menu.addItem("Nuevo");
        MenuItem abrir = menu.addItem("Abrir");
        MenuItem anular = menu.addItem("anular");
        menu.open();

        assertTrue(menu.onCharTyped('a'));
        assertSame(abrir, menu.getHighlightedItem());

        assertTrue(menu.onCharTyped('a'), "repeated letters cycle");
        assertSame(anular, menu.getHighlightedItem());

        assertTrue(menu.onCharTyped('A'), "case is ignored and wraps");
        assertSame(abrir, menu.getHighlightedItem());

        assertFalse(menu.onCharTyped('z'), "no row starts with z");
        assertSame(abrir, menu.getHighlightedItem());
    }

    @Test
    public void hoveringARowClearsTheKeyboardHighlightFromTheOldRow() {
        Menu menu = new Menu();
        MenuItem first = menu.addItem("Primero");
        MenuItem second = menu.addItem("Segundo");
        menu.open();
        menu.onKeyEvent(key(GlyphKeys.DOWN));
        assertSame(first, menu.getHighlightedItem());

        int x = (int) second.getX() + 3;
        int y = (int) second.getY() + 3;
        menu.onMouseEvent(new MouseEvent(MouseEventType.MOVE, x, y,
                MouseButton.LEFT, 0));

        assertNull(menu.getHighlightedItem(), "the pointer takes over");
        assertEquals(ComponentState.IDLE, first.getState());
        assertEquals(ComponentState.HOVER, second.getState());

        menu.onMouseEvent(new MouseEvent(MouseEventType.MOVE, 1, 1,
                MouseButton.LEFT, 0));

        assertEquals(ComponentState.IDLE, second.getState(),
                "leaving the rows clears the hover");
    }

    @Test
    public void aClosedMenuIgnoresNavigationInput() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Oculto");

        menu.onKeyEvent(key(GlyphKeys.DOWN));
        assertNull(menu.getHighlightedItem());
        assertFalse(menu.onCharTyped('o'));
        assertFalse(item.getState() == ComponentState.HOVER);
    }
}
