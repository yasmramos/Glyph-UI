package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Paint;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;

import java.util.ArrayList;
import java.util.List;

/**
 * The window-sized, top-most layer that hosts floating popups such as
 * {@link Menu}s.
 *
 * <p>{@link io.github.yasmramos.glyphui.core.Application} owns one instance
 * and gives it two privileges the normal scene graph does not have:</p>
 *
 * <ol>
 *   <li><b>Paint order</b> — the layer renders after the root panel, so its
 *       children overlay page content even though there is no z-index in the
 *       toolkit.</li>
 *   <li><b>Input order</b> — mouse events reach the layer before the root
 *       panel, and any event that lands on popup chrome is swallowed instead
 *       of falling through to the widgets underneath.</li>
 * </ol>
 *
 * <p>That same hook provides native-style dismissal: a press outside every
 * popup (and outside the popup's trigger, so clicking a menu button again
 * still toggles it) closes the open menus. It also implements the menu-bar
 * hover rule — while a menu is open, moving onto a sibling trigger switches
 * to that menu, exactly like JavaFX and native menu bars. {@link
 * #closeOnEscape()} backs the ESC gesture.</p>
 *
 * <p>Keys behave the same way: while a popup is open, {@link
 * #routeKeyEvent(KeyEvent)} and {@link #routeCharTyped(int)} route them to
 * the top-most popup first, which swallows them so the page underneath
 * cannot react to arrows or letters typed into a menu.</p>
 *
 * <p>The layer itself is transparent and never claims hover: {@link #hitTest}
 * reports {@code null} when the pointer is not over a popup, which is how
 * {@code Application} routes hover to the page underneath.</p>
 */
public class PopupLayer extends Panel {

    // Drop-shadow paints, created lazily on first render.
    private Paint haloPaint;
    private Paint dropPaint;

    /**
     * Menus that belong to this layer, including closed ones. Needed for the
     * menu-bar hover rule: while one menu is open, hovering the trigger of a
     * sibling has to open that sibling, and a closed menu is not a child of
     * the layer yet.
     */
    private final List<Menu> menus = new ArrayList<>();

    /**
     * Creates an empty layer covering the window (the application sizes it).
     */
    public PopupLayer() {
        super(0, 0, 0, 0);
        // The layer is pure plumbing: it never takes focus and has no
        // chrome of its own.
        setFocusable(false);
    }

    /**
     * Routes an event in window coordinates through the popups.
     *
     * <p>Children get first refusal (top-most popup first). Anything left
     * over that still lands on a popup is swallowed so it cannot click
     * through to the page; a leftover press outside all popups dismisses
     * every menu whose trigger was not hit.</p>
     *
     * @param event the event in window coordinates
     * @return true when the event was consumed by a popup
     */
    @Override
    public boolean onMouseEvent(MouseEvent event) {
        boolean consumed = super.onMouseEvent(event);

        float windowX = event.getX();
        float windowY = event.getY();

        if (!consumed && popupAt(windowX, windowY) != null) {
            // Popup chrome (padding/border, or a row that declined the event):
            // swallow it so nothing underneath reacts to a click on the menu.
            consumed = true;
        }

        if (!consumed && event.getType() == MouseEventType.PRESS) {
            for (Component child : children) {
                if (child instanceof Menu menu && menu.isOpen()
                        && !isOnTrigger(menu, windowX, windowY)) {
                    menu.close();
                }
            }
        }

        // Native menu-bar behaviour (also JavaFX's): once a menu is showing,
        // sliding the pointer onto a sibling's trigger switches to it without
        // another click. With every menu closed hovering does nothing.
        if (!consumed && event.getType() == MouseEventType.MOVE) {
            switchMenuUnderPointer(windowX, windowY);
        }
        if (!consumed && (event.getType() == MouseEventType.MOVE
                || event.getType() == MouseEventType.DRAG)) {
            // The pointer is off every popup, so no row will receive the
            // move: drop the hover paint while keeping a keyboard selection.
            for (Component child : children) {
                if (child instanceof Menu menu && menu.isOpen()) {
                    menu.clearPointerHover();
                }
            }
        }
        return consumed;
    }

    /**
     * Routes a key event to the top-most open popup.
     *
     * @param event the key event
     * @return true when a popup was open and swallowed the event, so the page
     *         underneath must not receive it
     */
    public boolean routeKeyEvent(KeyEvent event) {
        Component top = topVisiblePopup();
        if (top == null) {
            return false;
        }
        if (top instanceof Menu menu) {
            menu.onKeyEvent(event);
        }
        return true;
    }

    /**
     * Routes printable input to the top-most open popup, where it jumps
     * between rows by their initial letter.
     *
     * @param codepoint the typed character
     * @return true when a popup was open and swallowed the character
     */
    public boolean routeCharTyped(int codepoint) {
        Component top = topVisiblePopup();
        if (top == null) {
            return false;
        }
        if (top instanceof Menu menu) {
            menu.onCharTyped(codepoint);
        }
        return true;
    }

    /**
     * Whether any popup is currently showing.
     *
     * @return true when the layer has a visible child
     */
    public boolean hasOpenPopup() {
        return topVisiblePopup() != null;
    }

    /** The newest visible popup, i.e. the one painted on top. */
    private Component topVisiblePopup() {
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (child.isVisible()) {
                return child;
            }
        }
        return null;
    }

    /**
     * Registers a menu so hover-switching can find its trigger even while the
     * menu is closed.
     *
     * @param menu the menu to track
     */
    void registerMenu(Menu menu) {
        if (menu != null && !menus.contains(menu)) {
            menus.add(menu);
        }
    }

    /**
     * Stops tracking a menu (called when it is disposed).
     *
     * @param menu the menu to forget
     */
    void unregisterMenu(Menu menu) {
        menus.remove(menu);
    }

    /**
     * Opens the menu whose trigger is under the pointer when a different menu
     * is already open, closing the previous one.
     */
    private void switchMenuUnderPointer(float windowX, float windowY) {
        Menu open = null;
        for (Component child : children) {
            if (child instanceof Menu menu && menu.isOpen()) {
                open = menu;
                break;
            }
        }
        if (open == null) {
            return;
        }
        for (Menu candidate : menus) {
            if (candidate == open || candidate.isOpen()
                    || candidate.getTrigger() == null) {
                continue;
            }
            if (isOnTrigger(candidate, windowX, windowY)) {
                open.close();
                candidate.open();
                return;
            }
        }
    }

    /**
     * Dismisses the top-most open popup.
     *
     * @return true when a popup was open (and is now closed), false when
     *         there was nothing to dismiss
     */
    public boolean closeOnEscape() {
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (child.isVisible()) {
                closeChild(child);
                return true;
            }
        }
        return false;
    }

    /**
     * Dismisses every open popup. Called when the window loses the focus, so
     * a menu does not stay on screen while another application is in front.
     *
     * @return true when at least one popup was open
     */
    public boolean closeAll() {
        boolean closed = false;
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (child.isVisible()) {
                closeChild(child);
                closed = true;
            }
        }
        return closed;
    }

    private static void closeChild(Component child) {
        if (child instanceof Menu menu) {
            menu.close();
        } else {
            child.setVisible(false);
        }
    }

    /**
     * Hit-tests the popups in window coordinates.
     *
     * @param windowX the x-coordinate in window space
     * @param windowY the y-coordinate in window space
     * @return the top-most popup component under the point, or null when the
     *         pointer is not over any popup
     */
    public Component hitTest(float windowX, float windowY) {
        Component hit = getComponentAt(windowX, windowY);
        return hit == this ? null : hit;
    }

    /**
     * Whether the point, in window coordinates, lies over the top-most
     * visible popup.
     *
     * @param windowX the x-coordinate in window space
     * @param windowY the y-coordinate in window space
     * @return the popup under the point, or null
     */
    private Component popupAt(float windowX, float windowY) {
        float localX = windowX - x;
        float localY = windowY - y;
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (child.isVisible() && child.contains(localX, localY)) {
                return child;
            }
        }
        return null;
    }

    /**
     * Whether the window-coordinate point lies on the popup's trigger. The
     * trigger usually lives in a different subtree (the title bar), so the
     * point is converted into the trigger's parent space first.
     *
     * @param menu    the menu whose trigger is tested
     * @param windowX the x-coordinate in window space
     * @param windowY the y-coordinate in window space
     * @return true when the press landed on the trigger
     */
    private static boolean isOnTrigger(Menu menu, float windowX, float windowY) {
        Component trigger = menu.getTrigger();
        if (trigger == null) {
            return false;
        }
        float absX = 0.0f;
        float absY = 0.0f;
        for (Component c = trigger; c != null; c = c.getParent()) {
            absX += c.getX();
            absY += c.getY();
        }
        // Trigger bounds live in its parent's space; strip the parent's own
        // window offset so contains() receives coordinates it understands.
        float parentOffsetX = absX - trigger.getX();
        float parentOffsetY = absY - trigger.getY();
        return trigger.contains(windowX - parentOffsetX, windowY - parentOffsetY);
    }

    @Override
    protected void render(Canvas canvas) {
        // Drop shadows are painted first, in this layer's parent space, so
        // the popups rendered by super() cover their own shadow.
        if (visible) {
            if (haloPaint == null) {
                haloPaint = new Paint();
                haloPaint.setColor(0x38000000);
            }
            if (dropPaint == null) {
                dropPaint = new Paint();
                dropPaint.setColor(0x50000000);
            }
            for (Component child : children) {
                if (!child.isVisible()) {
                    continue;
                }
                float left = x + child.getX();
                float top = y + child.getY();
                canvas.drawRect(left - 3.0f, top - 2.0f,
                        child.getWidth() + 6.0f, child.getHeight() + 5.0f, haloPaint);
                canvas.drawRect(left + 2.0f, top + 3.0f,
                        child.getWidth(), child.getHeight(), dropPaint);
            }
        }
        super.render(canvas);
    }
}
