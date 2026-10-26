package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MenuItem}: content accessors, intrinsic sizing, the
 * activation sequence (toggle → handler → close menu) and the disabled /
 * hover states. Everything runs headless: no {@code Application}, no window.
 */
public class MenuItemTest extends TestBase {

    /** Builds a mouse event at the given position. */
    private static MouseEvent ev(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    /**
     * Opens a one-item menu and lays it out so the row has real bounds to
     * hit-test against. Returns the positioned row.
     */
    private static MenuItem openRow(Menu menu, MenuItem item) {
        menu.open();
        menu.doLayout();
        return item;
    }

    /** Presses and releases the row at its own top-left corner. */
    private static void click(MenuItem item) {
        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        item.onMouseEvent(ev(MouseEventType.PRESS, x, y));
        item.onMouseEvent(ev(MouseEventType.RELEASE, x, y));
    }

    @Test
    public void contentAccessorsRoundTrip() {
        MenuItem item = new MenuItem("Guardar");
        assertEquals("Guardar", item.getText());
        assertNull(item.getIcon());
        assertNull(item.getAcceleratorText());
        assertFalse(item.isCheckable());
        assertFalse(item.isChecked());
        assertEquals(AccessibleRole.MENU_ITEM, item.getAccessibleRole());

        item.setAcceleratorText("Ctrl+S");
        assertEquals("Ctrl+S", item.getAcceleratorText());

        item.setCheckable(true);
        assertTrue(item.isCheckable());
        item.setChecked(true);
        assertTrue(item.isChecked());

        item.setText("Save");
        assertEquals("Save", item.getText());
    }

    @Test
    public void measureGrowsWithTextAndAccelerator() {
        MenuItem plain = new MenuItem("Guardar");
        MenuItem withAccelerator = new MenuItem("Guardar");
        withAccelerator.setAcceleratorText("Ctrl+S");

        float plainWidth = plain.getPreferredWidth();
        float acceleratorWidth = withAccelerator.getPreferredWidth();

        assertTrue(plainWidth > 0.0f, "row must be wider than nothing");
        assertTrue(acceleratorWidth > plainWidth + 10.0f,
                "accelerator must add its own room (plain=" + plainWidth
                        + ", withAccel=" + acceleratorWidth + ")");
        assertTrue(plain.getPreferredHeight() > 0.0f);
    }

    @Test
    public void measureIsClampedToTheConstraints() {
        MenuItem item = new MenuItem("A fairly long menu entry");
        float clamped = item.measure(40.0f, Float.POSITIVE_INFINITY).getWidth();
        assertTrue(clamped <= 40.0f);
        assertTrue(clamped > 0.0f);
    }

    @Test
    public void clickRunsTheHandlerAndClosesTheOwningMenu() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Guardar");
        openRow(menu, item);

        boolean[] fired = {false};
        item.setOnClick(() -> fired[0] = true);

        click(item);

        assertTrue(fired[0], "handler must run on press+release inside the row");
        assertFalse(menu.isOpen(), "the owning menu must close after activation");
    }

    @Test
    public void checkableItemTogglesItsCheckOnEveryClick() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Barra de estado");
        item.setCheckable(true);
        openRow(menu, item);

        assertFalse(item.isChecked());
        click(item);
        assertTrue(item.isChecked(), "first activation checks the row");
        click(item);
        assertFalse(item.isChecked(), "second activation unchecks it");
    }

    @Test
    public void handlerRunsBeforeTheMenuClosesSoItCanReadTheNewCheckState() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Línea de reglas");
        item.setCheckable(true);
        openRow(menu, item);

        boolean[] seenChecked = {false};
        item.setOnClick(() -> seenChecked[0] = item.isChecked());

        click(item);

        assertTrue(seenChecked[0], "handler must observe the toggled state");
        assertFalse(menu.isOpen());
    }

    @Test
    public void aThrowingHandlerStillClosesTheMenu() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Salir");
        openRow(menu, item);
        item.setOnClick(() -> {
            throw new IllegalStateException("handler failure");
        });

        click(item);

        assertFalse(menu.isOpen(),
                "a failing handler must not leave a stale popup on screen");
    }

    @Test
    public void disabledRowsIgnoreThePointerEntirely() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Pegar");
        openRow(menu, item);

        boolean[] fired = {false};
        item.setOnClick(() -> fired[0] = true);
        item.setEnabled(false);

        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        assertFalse(item.onMouseEvent(ev(MouseEventType.PRESS, x, y)));
        assertFalse(item.onMouseEvent(ev(MouseEventType.RELEASE, x, y)));
        assertFalse(fired[0], "a disabled row must never run its handler");
    }

    @Test
    public void hoveringARowMarksItHoverAndIsConsumed() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Cortar");
        openRow(menu, item);

        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        assertTrue(item.onMouseEvent(ev(MouseEventType.MOVE, x, y)));
        assertEquals(ComponentState.HOVER, item.getState());
    }

    @Test
    public void releasingOutsideTheRowDoesNotActivate() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Copiar");
        openRow(menu, item);

        boolean[] fired = {false};
        item.setOnClick(() -> fired[0] = true);

        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        item.onMouseEvent(ev(MouseEventType.PRESS, x, y));
        // Release outside the row: the row reports the miss and skips the
        // activation entirely (press state is cleaned by the next move).
        assertFalse(item.onMouseEvent(ev(MouseEventType.RELEASE, x - 100, y - 100)));
        assertFalse(fired[0]);
    }

    @Test
    public void rowsOutsideAMenuCloseNothing() {
        MenuItem orphan = new MenuItem("Huérfano");
        orphan.setX(0);
        orphan.setY(0);
        orphan.applyLayoutSize(80.0f, 24.0f);
        orphan.setOnClick(() -> { });

        // No menu in the parent chain: activation must not throw.
        orphan.onMouseEvent(ev(MouseEventType.PRESS, 4, 4));
        orphan.onMouseEvent(ev(MouseEventType.RELEASE, 4, 4));
        assertEquals(ComponentState.HOVER, orphan.getState());
    }
}
