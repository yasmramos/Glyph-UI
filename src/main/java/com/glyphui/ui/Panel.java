package com.glyphui.ui;

import java.util.ArrayList;
import java.util.List;
import com.glyphui.layout.LayoutManager;

/**
 * A container component that can hold other components.
 */
public class Panel extends Component {
    protected List<Component> children;
    protected LayoutManager layoutManager;
    protected boolean layoutDirty;

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
    }

    /**
     * Removes a child component from this panel.
     *
     * @param component the component to remove
     */
    public void remove(Component component) {
        children.remove(component);
        component.setParent(null);
        markLayoutDirty();
    }

    /**
     * Removes all child components from this panel.
     */
    public void clear() {
        for (Component child : children) {
            child.setParent(null);
        }
        children.clear();
        markLayoutDirty();
    }

    /**
     * Marks the layout as dirty, triggering a relayout on next render.
     */
    protected void markLayoutDirty() {
        layoutDirty = true;
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
        super.setWidth(width);
        markLayoutDirty();
    }

    @Override
    public void setHeight(float height) {
        super.setHeight(height);
        markLayoutDirty();
    }

    @Override
    public void render(com.glyphui.graphics.Canvas canvas) {
        if (!visible) {
            return;
        }

        // Ensure layout is applied before rendering children
        doLayout();

        // Render all children
        for (Component child : children) {
            if (child.isVisible()) {
                child.render(canvas);
            }
        }
    }

    @Override
    public void onMouseEvent(com.glyphui.events.MouseEvent event) {
        if (!visible || !enabled) {
            return;
        }

        // Propagate event to children in reverse order (top-most first)
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (child.isVisible() && child.isEnabled()) {
                child.onMouseEvent(event);
            }
        }
    }

    @Override
    public void onKeyEvent(com.glyphui.events.KeyEvent event) {
        if (!visible || !enabled) {
            return;
        }

        // Propagate event to all children
        for (Component child : children) {
            if (child.isVisible() && child.isEnabled()) {
                child.onKeyEvent(event);
            }
        }
    }
}
