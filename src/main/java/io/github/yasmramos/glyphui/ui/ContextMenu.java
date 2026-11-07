package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.AppContext;
import io.github.yasmramos.glyphui.core.Application;

/**
 * A menu that pops up at an arbitrary point instead of under a trigger, the
 * classic right-click menu.
 *
 * <p>A context menu is an ordinary {@link Menu} whose position comes from
 * {@link #showAt(float, float)} rather than from a trigger. It lives in the
 * application's {@link PopupLayer}, so it overlays page content, closes on
 * an outside press and is dismissed by ESC exactly like a menu-bar drop-down.
 * The convenient wiring attaches one to a target and lets a right-click open
 * it:</p>
 *
 * <pre>{@code
 * ContextMenu menu = new ContextMenu();
 * menu.addItem("Cut", () -> cut());
 * menu.addItem("Copy", () -> copy());
 * menu.attachTo(editor);
 * }</pre>
 *
 * <p>{@link #attachTo(Component)} installs a pointer listener that opens the
 * menu at the click position; the target keeps its own left-click behaviour
 * untouched. {@link #showAt(float, float)} is also callable directly when the
 * application resolves the coordinates itself.</p>
 */
public class ContextMenu extends Menu {

    /** Margin kept between the menu and the layer edges, logical units. */
    public static final float EDGE_MARGIN = 4.0f;

    private Component target;

    /**
     * Creates a closed context menu with no rows.
     */
    public ContextMenu() {
        super();
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.CONTEXT_MENU;
    }

    /**
     * Opens the menu at the given point in the popup layer's coordinates,
     * clamped so it never spills outside the layer.
     *
     * @param x the x-coordinate, in window space
     * @param y the y-coordinate, in window space
     */
    public void showAt(float x, float y) {
        AppContext ctx = context() != null ? context() : AppContext.ofCurrent();
        Application app = ctx != null ? ctx.app() : null;
        if (getParent() == null && app != null) {
            app.getPopupLayer().add(this);
        }
        prepareForShow();

        Panel layer = getParent();
        float layerWidth = (layer != null && layer.getWidth() > 0.0f)
                ? layer.getWidth() : Float.MAX_VALUE;
        float layerHeight = (layer != null && layer.getHeight() > 0.0f)
                ? layer.getHeight() : Float.MAX_VALUE;

        float px = Math.max(0.0f, Math.min(x, layerWidth - getWidth() - EDGE_MARGIN));
        float py = Math.max(0.0f, Math.min(y, layerHeight - getHeight() - EDGE_MARGIN));
        setX(Math.max(0.0f, px));
        setY(Math.max(0.0f, py));

        setVisible(true);
        // Lay the rows out immediately so the pointer can reach them before
        // the next painted frame (same rationale as Menu.open()).
        doLayout();
        requestRepaint();
    }

    /**
     * Attaches the menu to a target: a right-click on the target opens the
     * menu at the click position. Passing null detaches the previous target.
     *
     * @param target the component whose right-clicks open this menu, or null
     */
    public void attachTo(Component target) {
        if (this.target == target) {
            return;
        }
        if (this.target != null) {
            this.target.setOnContextMenuRequested(null);
        }
        this.target = target;
        if (target != null) {
            target.setOnContextMenuRequested((x, y) -> showAt(x, y));
        }
    }

    /**
     * @return the component whose right-clicks open this menu, or null
     */
    public Component getTarget() {
        return target;
    }

    @Override
    protected void onDispose() {
        if (target != null) {
            target.setOnContextMenuRequested(null);
            target = null;
        }
        super.onDispose();
    }
}
