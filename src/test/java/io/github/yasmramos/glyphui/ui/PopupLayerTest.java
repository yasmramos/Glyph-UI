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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PopupLayer}: swallowing pointer input that lands on
 * popup chrome, dismissing menus when the user presses outside them (but not
 * on their trigger), ESC dismissal, and hit-testing for hover routing.
 *
 * <p>The layer is driven directly — the same calls
 * {@code io.github.yasmramos.glyphui.core.Application} makes from its mouse
 * and key listeners — so the whole dismissal flow is covered headless.</p>
 */
public class PopupLayerTest extends TestBase {

    /** Builds a mouse event in window (layer) coordinates. */
    private static MouseEvent ev(MouseEventType type, int x, int y) {
        return new MouseEvent(type, x, y, MouseButton.LEFT, 1);
    }

    /** A layer with a single open menu sized and laid out at (0, 0). */
    private static PopupLayer layerWithOpenMenu(Menu menu) {
        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(menu);
        menu.addItem("Guardar");
        menu.addItem("Salir");
        menu.open();
        menu.doLayout();
        return layer;
    }

    /** Builds a key-press event with no modifiers. */
    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, '\0',
                EnumSet.noneOf(KeyModifier.class));
    }

    /** Whether the component is, or lives inside, the given ancestor. */
    private static boolean isWithin(Component component, Component ancestor) {
        for (Component c = component; c != null; c = c.getParent()) {
            if (c == ancestor) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void aPressInsideAnOpenMenuIsSwallowedAndKeepsItOpen() {
        Menu menu = new Menu();
        PopupLayer layer = layerWithOpenMenu(menu);

        // (4, 4) is the first row (rows start after the popup padding).
        boolean consumed = layer.onMouseEvent(ev(MouseEventType.PRESS, 4, 4));

        assertTrue(consumed, "input over popup chrome must not reach the page");
        assertTrue(menu.isOpen(), "pressing inside the menu keeps it open");
    }

    @Test
    public void aPressOnThePopupChromeButOffAnyRowIsAlsoSwallowed() {
        Menu menu = new Menu();
        PopupLayer layer = layerWithOpenMenu(menu);

        // (1, 1) is inside the popup border/padding, above the first row:
        // no row claims it, so only the layer's own swallow rule applies.
        boolean consumed = layer.onMouseEvent(ev(MouseEventType.PRESS, 1, 1));

        assertTrue(consumed, "the popup frame still blocks clicks from falling through");
        assertTrue(menu.isOpen());
    }

    @Test
    public void aPressOutsideEveryPopupFallsThroughAndClosesTheMenu() {
        Menu menu = new Menu();
        PopupLayer layer = layerWithOpenMenu(menu);

        boolean consumed = layer.onMouseEvent(ev(MouseEventType.PRESS, 380, 280));

        assertFalse(consumed, "the page underneath must receive outside presses");
        assertFalse(menu.isOpen(), "an outside press dismisses the menu");
    }

    @Test
    public void aPressOnTheTriggerKeepsTheMenuOpenSoTheButtonTogglesIt() {
        Panel host = new Panel(0, 0, 400, 300);
        Button trigger = new Button(100, 40, 60, 24, "Archivo");
        host.add(trigger);

        Menu menu = new Menu();
        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(menu);
        menu.addItem("Guardar");
        menu.setTrigger(trigger);
        menu.open();
        menu.doLayout();

        boolean consumed = layer.onMouseEvent(ev(MouseEventType.PRESS, 110, 50));

        assertFalse(consumed, "the trigger belongs to the page, not the popup");
        assertTrue(menu.isOpen(),
                "the trigger is spared from dismissal so the button can toggle");
    }

    @Test
    public void clickingAItemRunsItAndThePressIsConsumedByThePopup() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Guardar");
        PopupLayer layer = layerWithOpenMenu(menu);

        boolean[] fired = {false};
        item.setOnClick(() -> fired[0] = true);

        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        assertTrue(layer.onMouseEvent(ev(MouseEventType.PRESS, x, y)));
        assertTrue(layer.onMouseEvent(ev(MouseEventType.RELEASE, x, y)));

        assertTrue(fired[0], "the row activates through the layer dispatch");
        assertFalse(menu.isOpen(), "picking a row closes the menu");
    }

    @Test
    public void escapeClosesTheTopmostPopupFirst() {
        Menu bottom = new Menu();
        Menu top = new Menu();
        PopupLayer layer = new PopupLayer();
        layer.add(bottom);
        layer.add(top);
        bottom.addItem("Uno");
        top.addItem("Dos");
        bottom.open();
        top.open();

        assertTrue(layer.closeOnEscape(), "first ESC closes a popup");
        assertTrue(bottom.isOpen(), "the older popup survives the first ESC");
        assertFalse(top.isOpen(), "the newest popup goes first");

        assertTrue(layer.closeOnEscape(), "second ESC closes the remaining one");
        assertFalse(bottom.isOpen());

        assertFalse(layer.closeOnEscape(), "nothing left to dismiss");
    }

    @Test
    public void closeAllDismissesEveryOpenPopup() {
        Menu first = new Menu();
        Menu second = new Menu();
        PopupLayer layer = new PopupLayer();
        layer.add(first);
        layer.add(second);
        first.addItem("Uno");
        second.addItem("Dos");
        first.open();
        second.open();

        assertTrue(layer.closeAll(), "open popups must be reported as dismissed");
        assertFalse(first.isOpen(), "the first popup closes");
        assertFalse(second.isOpen(), "the second popup closes");

        assertFalse(layer.closeAll(), "nothing left to dismiss");
    }

    @Test
    public void closeAllIgnoresClosedPopups() {
        Menu menu = new Menu();
        PopupLayer layer = layerWithOpenMenu(menu);
        menu.close();

        assertFalse(layer.closeAll(), "a closed popup must not count as dismissed");
    }

    @Test
    public void escapeDoesNothingWhenNoPopupIsOpen() {
        PopupLayer layer = new PopupLayer();
        assertFalse(layer.closeOnEscape());
    }

    @Test
    public void hitTestReportsThePopupUnderThePointerOrNull() {
        Menu menu = new Menu();
        PopupLayer layer = layerWithOpenMenu(menu);

        // hitTest returns the deepest component under the point, so the
        // target is the row — provided it belongs to this menu.
        Component overMenu = layer.hitTest(5.0f, 5.0f);
        assertTrue(isWithin(overMenu, menu),
                "hover over a popup must target a component of that popup, got " + overMenu);

        assertNull(layer.hitTest(380.0f, 280.0f),
                "hover outside every popup leaves the page in charge");

        menu.close();
        assertNull(layer.hitTest(5.0f, 5.0f),
                "a closed popup is invisible to hit-testing");
    }

    @Test
    public void hoveringASiblingTriggerWhileAMenuIsOpenSwitchesMenus() {
        Panel host = new Panel(0, 0, 400, 300);
        Button archivo = new Button(0, 10, 80, 24, "Archivo");
        Button editar = new Button(80, 10, 80, 24, "Editar");
        host.add(archivo);
        host.add(editar);

        Menu file = new Menu();
        file.addItem("Nuevo");
        file.setTrigger(archivo);
        Menu edit = new Menu();
        edit.addItem("Copiar");
        edit.setTrigger(editar);

        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(file);
        layer.add(edit);
        // A menu joins the layer's registry when it opens; close it again so
        // the test starts from the native "nothing showing" state.
        edit.open();
        edit.close();
        file.open();

        boolean consumed = layer.onMouseEvent(ev(MouseEventType.MOVE, 120, 20));

        assertFalse(consumed, "the hover is over the page, not the popup");
        assertFalse(file.isOpen(), "the previous menu closes on hover");
        assertTrue(edit.isOpen(), "the hovered sibling takes over");
    }

    @Test
    public void hoveringAWhollyClosedMenuBarDoesNotOpenAnything() {
        Panel host = new Panel(0, 0, 400, 300);
        Button archivo = new Button(0, 10, 80, 24, "Archivo");
        host.add(archivo);

        Menu file = new Menu();
        file.addItem("Nuevo");
        file.setTrigger(archivo);

        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(file);
        file.open();
        file.close(); // registered, but nothing is showing

        layer.onMouseEvent(ev(MouseEventType.MOVE, 40, 20));

        assertFalse(file.isOpen(),
                "hover alone must not deploy a menu when none is open");
    }

    @Test
    public void hoveringTheOpenMenuTriggerKeepsItOpen() {
        Panel host = new Panel(0, 0, 400, 300);
        Button archivo = new Button(0, 10, 80, 24, "Archivo");
        host.add(archivo);

        Menu file = new Menu();
        file.addItem("Nuevo");
        file.setTrigger(archivo);

        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(file);
        file.open();

        layer.onMouseEvent(ev(MouseEventType.MOVE, 40, 20));

        assertTrue(file.isOpen(),
                "moving over the trigger of the open menu is a no-op");
    }

    @Test
    public void keysReachTheTopMostOpenMenuAndAreSwallowed() {
        Menu bottom = new Menu();
        bottom.addItem("Uno");
        Menu top = new Menu();
        MenuItem topItem = top.addItem("Dos");

        PopupLayer layer = new PopupLayer();
        layer.add(bottom);
        layer.add(top);
        bottom.open();
        top.open();

        assertTrue(layer.routeKeyEvent(key(GlyphKeys.DOWN)),
                "an open popup consumes the key");

        assertSame(topItem, top.getHighlightedItem());
        assertNull(bottom.getHighlightedItem(),
                "only the menu painted on top reacts");
    }

    @Test
    public void typedLettersReachTheOpenMenuAndAreSwallowed() {
        Menu menu = new Menu();
        MenuItem guardar = menu.addItem("Guardar");
        MenuItem salir = menu.addItem("Salir");

        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(menu);
        menu.open();

        assertTrue(layer.routeCharTyped('s'));

        assertSame(salir, menu.getHighlightedItem());
        assertFalse(guardar.getState() == ComponentState.HOVER);
    }

    @Test
    public void aLayerWithoutOpenPopupsSwallowsNeitherKeysNorLetters() {
        PopupLayer layer = new PopupLayer();

        assertFalse(layer.hasOpenPopup());
        assertFalse(layer.routeKeyEvent(key(GlyphKeys.DOWN)));
        assertFalse(layer.routeCharTyped('a'));
    }

    @Test
    public void hasOpenPopupFollowsTheMenuVisibility() {
        Menu menu = new Menu();
        PopupLayer layer = layerWithOpenMenu(menu);

        assertTrue(layer.hasOpenPopup());

        menu.close();
        assertFalse(layer.hasOpenPopup());
    }

    @Test
    public void movingThePointerOffThePopupClearsTheHoveredRow() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Guardar");

        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(menu);
        menu.open();

        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        layer.onMouseEvent(ev(MouseEventType.MOVE, x, y));
        assertEquals(ComponentState.HOVER, item.getState());

        layer.onMouseEvent(ev(MouseEventType.MOVE, 300, 250));

        assertEquals(ComponentState.IDLE, item.getState(),
                "leaving the popup must not leave a row painted as hovered");
        assertTrue(menu.isOpen(), "hovering outside does not dismiss the menu");
    }

    @Test
    public void leavingThePopupKeepsTheKeyboardSelection() {
        Menu menu = new Menu();
        MenuItem item = menu.addItem("Guardar");

        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(menu);
        menu.open();
        menu.onKeyEvent(key(GlyphKeys.DOWN));

        layer.onMouseEvent(ev(MouseEventType.MOVE, 300, 250));

        assertSame(item, menu.getHighlightedItem());
        assertEquals(ComponentState.HOVER, item.getState(),
                "the keyboard row keeps its highlight when the pointer leaves");
    }

    @Test
    public void aClosedPopupNeitherSwallowsNorDismisses() {
        Menu menu = new Menu();
        PopupLayer layer = new PopupLayer();
        layer.setWidth(400.0f);
        layer.setHeight(300.0f);
        layer.add(menu);
        menu.addItem("Oculto");

        assertFalse(layer.onMouseEvent(ev(MouseEventType.PRESS, 5, 5)),
                "an empty layer never intercepts input");
        assertNull(layer.hitTest(5.0f, 5.0f));
    }
}
