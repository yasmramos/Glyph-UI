package com.glyphui.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * A container component that can hold other components.
 */
public class Panel extends Component {
    protected List<Component> children;

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
    }

    /**
     * Adds a child component to this panel.
     *
     * @param component the component to add
     */
    public void add(Component component) {
        children.add(component);
        component.setParent(this);
    }

    /**
     * Removes a child component from this panel.
     *
     * @param component the component to remove
     */
    public void remove(Component component) {
        children.remove(component);
        component.setParent(null);
    }

    /**
     * Removes all child components from this panel.
     */
    public void clear() {
        for (Component child : children) {
            child.setParent(null);
        }
        children.clear();
    }

    /**
     * Gets the list of child components.
     *
     * @return the list of children
     */
    public List<Component> getChildren() {
        return children;
    }

    @Override
    public void render(com.glyphui.graphics.Canvas canvas) {
        if (!visible) {
            return;
        }

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
