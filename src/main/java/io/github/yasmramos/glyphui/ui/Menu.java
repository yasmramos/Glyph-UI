package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.yasmramos.glyphui.core.AppContext;
import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.graphics.Theme;

import java.util.ArrayList;
import java.util.List;

/**
 * A drop-down menu: a vertical stack of {@link MenuItem}s (optionally grouped
 * by {@link MenuSeparator}s) that opens below a trigger component and closes
 * when an item is picked, when the user presses outside it, or on ESC.
 *
 * <p>The menu is a normal {@link Panel} that lives in the application's
 * {@link PopupLayer} (see {@link Application#getPopupLayer()}): the layer
 * paints it above the page and routes the pointer to it first, which is what
 * makes "click outside to dismiss" work. {@link #open()} attaches the menu to
 * that layer automatically on first use, so typical wiring is just:</p>
 *
 * <pre>{@code
 * Menu menu = new Menu();
 * menu.addItem("Save").setOnClick(() -> save());
 * menu.setTrigger(toolbarButton);
 * toolbarButton.setOnClick(menu::toggle);
 * }</pre>
 *
 * <p>The menu sizes itself from its rows: width is the widest row plus the
 * chrome padding, height is the stack of rows. Opening near the bottom edge
 * of the layer flips the menu above its trigger instead of clipping.</p>
 *
 * <p>While open, the menu also handles the keyboard: Up/Down move a single
 * highlighted row (skipping separators and disabled rows, wrapping at the
 * ends), Home/End jump to the first/last enabled row, Enter or Space
 * activates the highlighted row and typing a letter jumps to the next row
 * starting with it. The pointer and the keyboard share that one highlight,
 * so only one row ever looks selected.</p>
 */
public class Menu extends Panel {

    /** Inner padding between the popup border and its rows. */
    static final float PADDING = 4.0f;

    /** Alpha applied to the theme border colour for the popup hairline. */
    private static final int BORDER_ALPHA = 160;

    private Component trigger;
    private Paint borderPaint;

    /** Layer this menu is registered with, if any (for hover switching). */
    private PopupLayer registeredLayer;

    /** Row highlighted by the keyboard, or null when no row is highlighted. */
    private MenuItem highlighted;

    /**
     * Creates a closed menu with no rows and no trigger.
     */
    public Menu() {
        super(0, 0, 0, 0);
        // Popups start hidden: an invisible menu is skipped by the layer's
        // hit-testing and rendering, and isOpen() reports false.
        setVisible(false);
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.MENU;
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /**
     * Appends a plain menu item.
     *
     * @param text the row label
     * @return the new row, for further configuration
     */
    public MenuItem addItem(String text) {
        return addItem(text, null);
    }

    /**
     * Appends a menu item with a leading icon.
     *
     * @param text the row label
     * @param icon the leading icon, or null for none
     * @return the new row, for further configuration
     */
    public MenuItem addItem(String text, Image icon) {
        MenuItem item = new MenuItem(text, icon);
        add(item);
        return item;
    }

    /**
     * Appends a divider row after the last item.
     *
     * @return the new separator
     */
    public MenuSeparator addSeparator() {
        MenuSeparator separator = new MenuSeparator();
        add(separator);
        return separator;
    }

    // ------------------------------------------------------------------
    // Trigger / open state
    // ------------------------------------------------------------------

    /**
     * Sets the component (usually the title-bar button) this menu drops down
     * from. The menu aligns its left edge with the trigger and opens just
     * below it (or above it when there is no room below).
     *
     * @param trigger the trigger component (null keeps the current position)
     */
    public void setTrigger(Component trigger) {
        this.trigger = trigger;
        AppContext ctx = context() != null ? context() : AppContext.ofCurrent();
        Application app = ctx != null ? ctx.app() : null;
        if (trigger != null && app != null) {
            // Register eagerly: hover-switching must know this menu's trigger
            // before the menu has ever been opened.
            attachToLayer(app.getPopupLayer());
        } else if (trigger == null) {
            detachFromLayer();
        }
    }

    /**
     * Gets the trigger component.
     *
     * @return the trigger, or null when none is set
     */
    public Component getTrigger() {
        return trigger;
    }

    /**
     * Whether the menu is currently showing.
     *
     * @return true while open
     */
    public boolean isOpen() {
        return isVisible();
    }

    /**
     * Opens the menu below its trigger, sizing it to its rows.
     *
     * <p>On first use the menu joins the application's popup layer (when an
     * {@link Application} is running and the menu is not parented yet). With
     * no application — pure unit tests — the menu still sizes, positions and
     * shows itself; nothing renders it.</p>
     */
    public void open() {
        AppContext ctx = context() != null ? context() : AppContext.ofCurrent();
        Application app = ctx != null ? ctx.app() : null;
        if (getParent() == null && app != null) {
            app.getPopupLayer().add(this);
        }
        if (getParent() instanceof PopupLayer layer) {
            attachToLayer(layer);
        }
        // Every open starts with no selection: the first arrow key chooses
        // the first (Down) or last (Up) enabled row.
        clearHighlight();
        setWidth(getPreferredWidth());
        setHeight(getPreferredHeight());
        positionNearTrigger();
        setVisible(true);
        // Lay the rows out immediately: Panel.render() would do it on the next
        // frame, but the pointer can reach the popup before that frame is
        // painted, and a row without bounds is not hit-testable (the click
        // would be swallowed by the popup chrome and the menu would ignore it).
        doLayout();
        requestRepaint();
    }

    /**
     * Closes the menu (no-op when it is already closed).
     */
    public void close() {
        if (isVisible()) {
            clearHighlight();
            setVisible(false);
        }
    }

    /**
     * Opens the menu when closed, closes it when open.
     */
    public void toggle() {
        if (isOpen()) {
            close();
        } else {
            open();
        }
    }

    // ------------------------------------------------------------------
    // Keyboard navigation
    // ------------------------------------------------------------------

    /**
     * Gets the row the keyboard has highlighted.
     *
     * @return the highlighted row, or null when none is selected
     */
    public MenuItem getHighlightedItem() {
        return highlighted;
    }

    /**
     * Handles menu navigation keys while the popup is open: Up/Down move the
     * highlight, Home/End jump to the edges, Enter/Space activate the
     * highlighted row. Key releases are ignored so a single press activates
     * once, and closing the menu (from the item handler) leaves nothing
     * highlighted.
     *
     * @param event the key event routed by the popup layer
     */
    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        int key = event.getKeyCode();
        if (key == GlyphKeys.DOWN) {
            moveHighlight(1);
        } else if (key == GlyphKeys.UP) {
            moveHighlight(-1);
        } else if (key == GlyphKeys.HOME) {
            highlightEdge(true);
        } else if (key == GlyphKeys.END) {
            highlightEdge(false);
        } else if (key == GlyphKeys.ENTER || key == GlyphKeys.SPACE) {
            if (highlighted != null) {
                highlighted.fire();
            }
        }
    }

    /**
     * Jumps to the next enabled row whose label starts with the typed letter,
     * cycling through same-initial rows on repeated presses (native menu-bar
     * behaviour). Case is ignored.
     *
     * @param codepoint the printable character typed by the user
     * @return true when a row matched and is now highlighted
     */
    public boolean onCharTyped(int codepoint) {
        if (!visible || !enabled) {
            return false;
        }
        char target = Character.toLowerCase((char) codepoint);
        List<MenuItem> rows = enabledRows();
        if (rows.isEmpty()) {
            return false;
        }
        int start = 0;
        if (highlighted != null) {
            int index = rows.indexOf(highlighted);
            if (index >= 0) {
                start = index + 1;
            }
        }
        for (int i = 0; i < rows.size(); i++) {
            MenuItem row = rows.get((start + i) % rows.size());
            String text = row.getText();
            if (text == null) {
                continue;
            }
            String label = text.stripLeading();
            if (!label.isEmpty()
                    && Character.toLowerCase(label.charAt(0)) == target) {
                setHighlighted(row);
                return true;
            }
        }
        return false;
    }

    /**
     * Pointer moves take over the keyboard highlight: when the pointer lands
     * on a different row (or off the rows entirely) the old highlighted row
     * is cleared, so the row under the cursor is the only one highlighted.
     */
    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (event.getType() == MouseEventType.MOVE
                || event.getType() == MouseEventType.DRAG) {
            float localX = event.getX() - getX();
            float localY = event.getY() - getY();
            MenuItem underPointer = itemAt(localX, localY);
            // Clear unless the pointer is already on the keyboard-selected
            // row: that also drops a stale mouse hover after the pointer
            // leaves the rows (only the row under the pointer gets a MOVE).
            if (underPointer == null || underPointer != highlighted) {
                clearHighlight();
            }
        }
        return super.onMouseEvent(event);
    }

    private void moveHighlight(int direction) {
        List<MenuItem> rows = enabledRows();
        if (rows.isEmpty()) {
            return;
        }
        int index = (highlighted == null) ? -1 : rows.indexOf(highlighted);
        if (index < 0) {
            setHighlighted(direction > 0 ? rows.get(0) : rows.get(rows.size() - 1));
            return;
        }
        int next = Math.floorMod(index + direction, rows.size());
        setHighlighted(rows.get(next));
    }

    private void highlightEdge(boolean first) {
        List<MenuItem> rows = enabledRows();
        if (rows.isEmpty()) {
            return;
        }
        setHighlighted(first ? rows.get(0) : rows.get(rows.size() - 1));
    }

    /**
     * The enabled rows in visual order: disabled rows and separators never
     * receive the keyboard highlight.
     */
    private List<MenuItem> enabledRows() {
        List<MenuItem> rows = new ArrayList<>();
        for (Component child : children) {
            if (child instanceof MenuItem row && row.isVisible() && row.isEnabled()) {
                rows.add(row);
            }
        }
        return rows;
    }

    /**
     * Finds the row under a point expressed in this menu's local coordinates.
     */
    private MenuItem itemAt(float localX, float localY) {
        for (Component child : children) {
            if (child instanceof MenuItem row && row.isVisible()
                    && row.isEnabled() && child.contains(localX, localY)) {
                return row;
            }
        }
        return null;
    }

    /**
     * Selects one row and clears every other row's hover state, so the
     * keyboard and the pointer can never leave two rows looking selected.
     */
    private void setHighlighted(MenuItem item) {
        if (highlighted == item) {
            return;
        }
        highlighted = item;
        for (Component child : children) {
            if (!(child instanceof MenuItem row)) {
                continue;
            }
            if (row == item) {
                row.setState(ComponentState.HOVER);
            } else if (row.getState() == ComponentState.HOVER) {
                row.setState(ComponentState.IDLE);
            }
        }
    }

    /**
     * Clears a pointer hover when the pointer leaves the popup, keeping a
     * keyboard selection intact. Rows only receive a MOVE while the pointer
     * is over them, so without this the last hovered row would stay painted.
     */
    void clearPointerHover() {
        for (Component child : children) {
            if (child instanceof MenuItem row && row != highlighted
                    && row.getState() == ComponentState.HOVER) {
                row.setState(ComponentState.IDLE);
            }
        }
    }

    /** Drops the keyboard selection (also clears a pointer hover). */
    private void clearHighlight() {
        highlighted = null;
        for (Component child : children) {
            if (child instanceof MenuItem row
                    && row.getState() == ComponentState.HOVER) {
                row.setState(ComponentState.IDLE);
            }
        }
    }

    /**
     * Places the menu against its trigger: left-aligned, directly below it,
     * flipping above when the layer has no room below. Without a trigger the
     * current position is kept.
     */
    private void positionNearTrigger() {
        if (trigger == null) {
            return;
        }
        float triggerX = 0.0f;
        float triggerY = 0.0f;
        for (Component c = trigger; c != null; c = c.getParent()) {
            triggerX += c.getX();
            triggerY += c.getY();
        }

        float belowY = triggerY + trigger.getHeight();
        float menuHeight = getHeight();
        Panel layer = getParent();
        float layerHeight = (layer != null && layer.getHeight() > 0.0f)
                ? layer.getHeight() : Float.MAX_VALUE;

        setX(triggerX);
        setY(belowY + menuHeight <= layerHeight
                ? belowY
                : Math.max(0.0f, triggerY - menuHeight));
    }

    // ------------------------------------------------------------------
    // Sizing / layout
    // ------------------------------------------------------------------

    @Override
    public float getPreferredWidth() {
        float rows = 0.0f;
        for (Component child : children) {
            rows = Math.max(rows, child.getPreferredWidth());
        }
        return 2.0f * PADDING + rows;
    }

    @Override
    public float getPreferredHeight() {
        float rows = 0.0f;
        for (Component child : children) {
            rows += child.getPreferredHeight();
        }
        return 2.0f * PADDING + rows;
    }

    /**
     * Stacks the rows top to bottom inside the popup. Runs on every render
     * (the Panel contract), which keeps rows glued together even when the
     * item set changes while the menu is open.
     */
    @Override
    public void doLayout() {
        if (!visible) {
            return;
        }
        float innerWidth = Math.max(0.0f, width - 2.0f * PADDING);
        float rowY = PADDING;
        for (Component child : children) {
            float rowHeight = child.getPreferredHeight();
            child.setX(PADDING);
            child.setY(rowY);
            // Layout-assigned size, not an explicit one: rows keep measuring
            // themselves from their text so the menu can re-size on reopen.
            child.applyLayoutSize(innerWidth, rowHeight);
            rowY += rowHeight;
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        // Popup surface reads as chrome above the page: the control colour
        // separates it from the window background beneath. Use the setter,
        // not the field: Panel only fills its bounds when the background is
        // explicit (CSS or setBackgroundColor), otherwise the page shows
        // through the popup and the rows look translucent.
        Theme theme = getTheme();
        setBackgroundColor(theme.getControlNormalColor());
        super.render(canvas);

        // Hairline border on top of everything: rows are inset by PADDING,
        // so the outline never crosses their highlight.
        if (borderPaint == null) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        int color = theme.getBorderColor();
        borderPaint.setColor((BORDER_ALPHA << 24) | (color & 0x00FFFFFF));
        canvas.drawRect(0.5f, 0.5f, width - 1.0f, height - 1.0f, borderPaint);
    }

    /**
     * Tracks this menu in a popup layer so hover-switching can find it while
     * it is closed. Idempotent.
     */
    private void attachToLayer(PopupLayer layer) {
        if (layer == null || layer == registeredLayer) {
            return;
        }
        detachFromLayer();
        registeredLayer = layer;
        layer.registerMenu(this);
    }

    private void detachFromLayer() {
        if (registeredLayer != null) {
            registeredLayer.unregisterMenu(this);
            registeredLayer = null;
        }
    }

    @Override
    protected void onDispose() {
        detachFromLayer();
        if (borderPaint != null) {
            borderPaint.close();
            borderPaint = null;
        }
        super.onDispose();
    }
}
