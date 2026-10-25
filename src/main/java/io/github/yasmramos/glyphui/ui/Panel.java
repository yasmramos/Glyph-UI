package io.github.yasmramos.glyphui.ui;

import java.util.ArrayList;
import java.util.List;
import io.github.yasmramos.glyphui.layout.LayoutManager;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Paint;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;

/**
 * A container component that can hold other components.
 */
public class Panel extends Component {

    protected List<Component> children;
    protected LayoutManager layoutManager;
    protected boolean layoutDirty;
    protected int backgroundColor;

    /**
     * Reusable background paint, created lazily on first render and closed in
     * {@link #dispose()} to avoid per-frame native allocations.
     */
    private Paint bgPaint;

    public Panel(float width, float height) {
        this(0, 0, width, height);
    }

    /**
     * Creates a new Panel.
     *
     * @param x the x-coordinate of the panel
     * @param y the y-coordinate of the panel
     * @param width the width of the panel
     * @param height the height of the panel
     */
    public Panel(float x, float y, float width, float height) {
        super(x, y, width, height);
        this.children = new ArrayList<>();
        this.layoutManager = null;
        this.layoutDirty = false;
        this.backgroundColor = Color.makeARGB(0, 0, 0, 0); // Transparent by default
        // Containers do not take keyboard focus unless a subclass opts in
        setFocusable(false);
    }

    /**
     * Panels are the generic container element, equivalent to HTML {@code div}.
     */
    @Override
    protected String defaultStyleTag() {
        return "div";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PANEL;
    }

    /**
     * Adds a child component to this panel.
     *
     * @param component the component to add
     */
    public void add(Component component) {
        // Reparent first: a component already attached to another panel must
        // never end up in two trees at once (the old parent would keep it in
        // its children list while the new one also references it).
        Panel previous = component.getParent();
        if (previous != null && previous != this) {
            previous.remove(component);
        }
        children.add(component);
        component.setParent(this);
        // Single invalidation for the whole mutation: markLayoutDirty()
        // already routes requestRepaint() -> invalidate() through this
        // subtree. Calling invalidate() again here double-counted every
        // structural change (and would have been copied into the next 45
        // widgets).
        markLayoutDirty();
    }

    /**
     * Removes a child component from this panel. The component is detached
     * (parent cleared) and then closed so nested widgets release their native
     * resources; removal itself does not destroy the component, and it may be
     * re-added to any panel afterwards. Removing a component that is not a
     * child of this panel is a no-op.
     *
     * @param component the component to remove
     */
    public void remove(Component component) {
        if (children.remove(component)) {
            try (component) {
                component.setParent(null);
            }
        }
        // Same single-invalidation path as add(): markLayoutDirty() already
        // requests the repaint through this subtree.
        markLayoutDirty();
    }

    /**
     * Removes all child components from this panel, closing each one.
     */
    public void clear() {
        for (Component child : children) {
            child.setParent(null);
            child.close();
        }
        children.clear();
        markLayoutDirty();
    }

    /**
     * Disposes every child (detaching it first) and clears the child list.
     * Called by {@link Component#dispose()} before this panel releases anything
     * of its own. A failing child does not prevent its siblings from being
     * disposed.
     */
    @Override
    protected void disposeChildren() {
        List<Component> snapshot = new ArrayList<>(children);
        children.clear();
        RuntimeException failure = null;
        for (Component child : snapshot) {
            child.setParent(null);
            try {
                child.dispose();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /**
     * Releases the reusable background paint.
     */
    @Override
    protected void onDispose() {
        if (bgPaint != null) {
            bgPaint.close();
            bgPaint = null;
        }
    }

    /**
     * Lazily creates the reusable background paint. Kept out of the render
     * loop's allocation path: it is created once and reused every frame.
     */
    private void ensureBgPaint() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
    }

    /**
     * Marks the layout as dirty, triggering a relayout on next render. Also
     * requests a repaint so the application re-renders when the layout result
     * changes the visual tree (on-demand rendering).
     */
    protected void markLayoutDirty() {
        layoutDirty = true;
        requestRepaint();
    }

    /**
     * Gets the list of child components.
     *
     * @return the list of children
     */
    public List<Component> getChildren() {
        return children;
    }

    /**
     * Descends into the children to find the deepest component at a point.
     * Children are laid out in this panel's local space, so the point is
     * translated by this panel's origin before testing them. The topmost child
     * wins: children render in insertion order, so the last one is on top.
     *
     * @param px the x-coordinate in the parent's space
     * @param py the y-coordinate in the parent's space
     * @return the deepest component at the point, this panel when no child
     *         claims it, or null when the point is outside the panel
     */
    @Override
    public Component getComponentAt(float px, float py) {
        if (!visible || !contains(px, py)) {
            return null;
        }
        float lx = px - x;
        float ly = py - y;
        for (int i = children.size() - 1; i >= 0; i--) {
            Component hit = children.get(i).getComponentAt(lx, ly);
            if (hit != null) {
                return hit;
            }
        }
        return this;
    }

    /**
     * Gets the layout manager for this panel.
     *
     * @return the layout manager
     */
    public LayoutManager getLayoutManager() {
        return layoutManager;
    }

    /**
     * Sets the layout manager for this panel and triggers an immediate relayout
     * if there are children.
     *
     * @param layoutManager the layout manager to set
     */
    public void setLayoutManager(LayoutManager layoutManager) {
        this.layoutManager = layoutManager;
        if (layoutManager != null && !children.isEmpty()) {
            doLayout();
        }
    }

    /**
     * Performs the layout if a layout manager is set and layout is dirty.
     */
    public void doLayout() {
        if (layoutManager != null && layoutDirty) {
            layoutManager.layout(this);
            layoutDirty = false;
        }
    }

    @Override
    public void setWidth(float width) {
        // Mark layout dirty *before* delegating: super.setWidth() performs
        // the single invalidation for the whole mutation, so the layout flag
        // is already set when the repaint propagates. Calling it afterwards
        // would request a second repaint (double-count bug).
        boolean changed = this.width != width;
        if (changed) {
            layoutDirty = true;
        }
        super.setWidth(width);
    }

    @Override
    public void setHeight(float height) {
        // Same pre-mark pattern as setWidth: exactly one invalidation per
        // user-set resize.
        boolean changed = this.height != height;
        if (changed) {
            layoutDirty = true;
        }
        super.setHeight(height);
    }

    /**
     * Layout-assigned size (does not mark the bounds as explicit). A nested
     * panel still has to re-run its own layout when its size changes.
     */
    @Override
    public void applyLayoutSize(float width, float height) {
        boolean changed = this.width != width || this.height != height;
        super.applyLayoutSize(width, height);
        if (changed) {
            markLayoutDirty();
        }
    }

    /**
     * Gets the effective background color. Resolution order: matching CSS rule
     * ({@code background} property) → explicit per-instance override → theme
     * window background (when no explicit color was set, the transparent
     * default, so panels follow light/dark switching).
     *
     * @return the effective background color (as ARGB int)
     */
    public int getBackgroundColor() {
        Style s = getComputedStyle();
        if (s != null && s.has(StyleProperty.BACKGROUND)) {
            return s.getInt(StyleProperty.BACKGROUND, 0xFF000000);
        }
        if (backgroundColor == Color.makeARGB(0, 0, 0, 0)) {
            return Theme.current().getBackgroundColor();
        }
        return backgroundColor;
    }

    /**
     * Sets the background color of this panel.
     *
     * @param backgroundColor the new background color (as ARGB int)
     */
    public void setBackgroundColor(int backgroundColor) {
        if (this.backgroundColor != backgroundColor) {
            this.backgroundColor = backgroundColor;
            requestRepaint();
        }
    }

    @Override
    protected void render(io.github.yasmramos.glyphui.graphics.Canvas canvas) {
        if (!visible) {
            return;
        }

        // Ensure layout is applied before rendering children
        doLayout();

        // Draw background if color is set (non-transparent). The paint object
        // is reused across frames to avoid per-frame native allocations.
        // Compare the alpha channel directly: io.github.humbleui.skija.Color
        // is an int value class that does not override equals(), so reference
        // comparison against a freshly built transparent color is unreliable.
        if ((backgroundColor >>> 24) != 0) {
            ensureBgPaint();
            bgPaint.setColor(backgroundColor);
            canvas.drawRect(x, y, width, height, bgPaint);
        }

        // Save the canvas state, translate to the panel's local coordinate
        // system and clip to its bounds so that children cannot draw outside
        // the panel. The saved state is restored with restoreToCount().
        int saveCount = canvas.save();
        canvas.translate(x, y);
        canvas.clipRect(0, 0, width, height);

        try {
            // Render all children in local coordinates
            for (Component child : children) {
                if (child.isVisible()) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            // Restore the canvas state
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }

        // Convert mouse coordinates to local coordinate system for children
        int localX = event.getX() - (int) x;
        int localY = event.getY() - (int) y;

        // Create a new MouseEvent with local coordinates
        MouseEvent localEvent = new MouseEvent(
                event.getType(),
                localX,
                localY,
                event.getButton(),
                event.getClickCount()
        );

        // Propagate the event to children in reverse order (top-most first).
        // Only children whose bounds contain the pointer receive the event,
        // and propagation stops as soon as a child consumes it.
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (!child.isVisible() || !child.isEnabled()) {
                continue;
            }
            if (!child.contains(localX, localY)) {
                continue;
            }
            if (child.onMouseEvent(localEvent)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled) {
            return;
        }

        FocusManager fm = FocusManager.getGlobalFocusManager();
        Component focused = (fm != null) ? fm.getFocused() : null;

        if (focused != null && isSelfOrDescendant(focused)) {
            // Focus-driven routing: deliver the key to the focused widget
            // (this panel included, e.g. a focusable custom container).
            if (focused != this) {
                focused.onKeyEvent(event);
            }
            // A plain Panel is not a key handler itself (Component's
            // onKeyEvent is abstract); focusable subclasses override it.
            return;
        }

        // Legacy fallback: no focus model active — broadcast to children as
        // before so applications without a FocusManager keep working.
        for (Component child : children) {
            if (child.isVisible() && child.isEnabled()) {
                child.onKeyEvent(event);
            }
        }
    }

    /**
     * Checks whether the given component is this panel or one of its
     * (transitively nested) descendants.
     *
     * @param component the candidate component
     * @return true if it belongs to this subtree
     */
    protected boolean isSelfOrDescendant(Component component) {
        Component c = component;
        while (c != null) {
            if (c == this) {
                return true;
            }
            c = c.getParent();
        }
        return false;
    }
}
