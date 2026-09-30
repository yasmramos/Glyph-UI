package com.glyphui.ui;

import java.util.ArrayList;
import java.util.List;
import com.glyphui.layout.LayoutManager;
import io.github.humbleui.skija.Color;

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
    private io.github.humbleui.skija.Paint bgPaint;

    /**
     * Creates a new Panel.
     *
     * @param x      the x-coordinate of the panel
     * @param y      the y-coordinate of the panel
     * @param width  the width of the panel
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

    /** Panels are the generic container element, equivalent to HTML {@code div}. */
    @Override
    protected String defaultStyleTag() {
        return "div";
    }

    @Override
    public com.glyphui.ui.AccessibleRole getAccessibleRole() {
        return com.glyphui.ui.AccessibleRole.PANEL;
    }

    /**
     * Adds a child component to this panel.
     *
     * @param component the component to add
     */
    public void add(Component component) {
        children.add(component);
        component.setParent(this);
        markLayoutDirty();
        invalidate();
    }

    /**
     * Removes a child component from this panel.
     *
     * @param component the component to remove
     */
    public void remove(Component component) {
        children.remove(component);
        component.setParent(null);
        component.close();
        markLayoutDirty();
        invalidate();
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
        invalidate();
    }

    /**
     * Releases this panel's resources and cascades {@link Component#close()}
     * to every child so nested widgets free their native Skija objects too.
     * The child list is cleared afterwards.
     */
    @Override
    public void dispose() {
        for (Component child : children) {
            child.setParent(null);
            child.close();
        }
        children.clear();
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
            bgPaint = new io.github.humbleui.skija.Paint();
            bgPaint.setAntiAlias(true);
        }
    }

    /**
     * Marks the layout as dirty, triggering a relayout on next render.
     * Also requests a repaint so the application re-renders when the
     * layout result changes the visual tree (on-demand rendering).
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
     * Gets the layout manager for this panel.
     *
     * @return the layout manager
     */
    public LayoutManager getLayoutManager() {
        return layoutManager;
    }

    /**
     * Sets the layout manager for this panel and triggers an immediate relayout if there are children.
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
        float oldWidth = this.width;
        super.setWidth(width);
        if (oldWidth != width) {
            markLayoutDirty();
        }
    }

    @Override
    public void setHeight(float height) {
        float oldHeight = this.height;
        super.setHeight(height);
        if (oldHeight != height) {
            markLayoutDirty();
        }
    }

    /**
     * Gets the effective background color. Resolution order: matching CSS
     * rule ({@code background} property) → explicit per-instance override →
     * theme window background (when no explicit color was set, the
     * transparent default, so panels follow light/dark switching).
     *
     * @return the effective background color (as ARGB int)
     */
    public int getBackgroundColor() {
        com.glyphui.style.Style s = getComputedStyle();
        if (s != null && s.has(com.glyphui.style.StyleProperty.BACKGROUND)) {
            return s.getInt(com.glyphui.style.StyleProperty.BACKGROUND, 0xFF000000);
        }
        if (backgroundColor == Color.makeARGB(0, 0, 0, 0)) {
            return com.glyphui.graphics.Theme.current().getBackgroundColor();
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
    public void render(com.glyphui.graphics.Canvas canvas) {
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
                    child.render(canvas);
                }
            }
        } finally {
            // Restore the canvas state
            canvas.restoreToCount(saveCount);
        }
    }

    @Override
    public boolean onMouseEvent(com.glyphui.events.MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }

        // Convert mouse coordinates to local coordinate system for children
        int localX = event.getX() - (int)x;
        int localY = event.getY() - (int)y;
        
        // Create a new MouseEvent with local coordinates
        com.glyphui.events.MouseEvent localEvent = new com.glyphui.events.MouseEvent(
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
    public void onKeyEvent(com.glyphui.events.KeyEvent event) {
        if (!visible || !enabled) {
            return;
        }

        com.glyphui.core.FocusManager fm = com.glyphui.core.FocusManager.getGlobalFocusManager();
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
