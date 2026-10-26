package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.graphics.Theme;

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
        Application app = Application.getCurrent();
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
        Application app = Application.getCurrent();
        if (getParent() == null && app != null) {
            app.getPopupLayer().add(this);
        }
        if (getParent() instanceof PopupLayer layer) {
            attachToLayer(layer);
        }
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
        canvas.drawRect(x + 0.5f, y + 0.5f, width - 1.0f, height - 1.0f, borderPaint);
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
