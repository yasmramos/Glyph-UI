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
        component.dispose();
        markLayoutDirty();
        invalidate();
    }

    /**
     * Removes all child components from this panel.
     */
    public void clear() {
        for (Component child : children) {
            child.setParent(null);
            child.dispose();
        }
        children.clear();
        markLayoutDirty();
        invalidate();
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
        this.backgroundColor = backgroundColor;
        invalidate();
    }

    @Override
    public void render(com.glyphui.graphics.Canvas canvas) {
        if (!visible) {
            return;
        }

        // Ensure layout is applied before rendering children
        doLayout();

        // Draw the effective background: explicit color when set, otherwise
        // the current theme's window background so panels follow light/dark.
        int effectiveBackground = getBackgroundColor();
        io.github.humbleui.skija.Paint bgPaint = new io.github.humbleui.skija.Paint();
        try {
            bgPaint.setColor(effectiveBackground);
            bgPaint.setAntiAlias(true);
            canvas.drawRect(x, y, width, height, bgPaint);
        } finally {
            bgPaint.close();
        }

        // Save the canvas state and translate to panel's local coordinate system
        io.github.humbleui.skija.Canvas nativeCanvas = canvas.getNativeCanvas();
        int saveCount = nativeCanvas.save();
        nativeCanvas.translate(x, y);

        try {
            // Render all children in local coordinates
            for (Component child : children) {
                if (child.isVisible()) {
                    child.render(canvas);
                }
            }
        } finally {
            // Restore the canvas state
            nativeCanvas.restoreToCount(saveCount);
        }
    }

    @Override
    public void onMouseEvent(com.glyphui.events.MouseEvent event) {
        if (!visible || !enabled) {
            return;
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

        // Propagate event to children in reverse order (top-most first)
        for (int i = children.size() - 1; i >= 0; i--) {
            Component child = children.get(i);
            if (child.isVisible() && child.isEnabled()) {
                child.onMouseEvent(localEvent);
            }
        }
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
