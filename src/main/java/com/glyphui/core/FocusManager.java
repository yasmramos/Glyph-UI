package com.glyphui.core;

import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;

/**
 * Tracks the keyboard-focused component and implements Tab / Shift+Tab
 * traversal of the component tree.
 *
 * <p>Traversal order is depth-first over {@code Panel.getChildren()} (the
 * same order widgets are added in), skipping components that are invisible,
 * disabled or not focusable. A single global instance is used by
 * {@link Application} (installed via {@link #setGlobalFocusManager}) so that
 * {@code Component.requestFocus()} works from anywhere in the tree.</p>
 */
public class FocusManager {

    private static volatile FocusManager globalFocusManager;

    private Component focusedComponent;
    private Panel traversalRoot;
    private final Runnable repaintCallback;

    /**
     * Creates a focus manager.
     *
     * @param repaintCallback invoked whenever the focus changes so the
     *                        application can schedule a repaint (may be null)
     */
    public FocusManager(Runnable repaintCallback) {
        this.repaintCallback = repaintCallback;
    }

    /**
     * Installs the process-wide focus manager (done by {@link Application}).
     *
     * @param manager the manager to install (or null to clear)
     */
    public static void setGlobalFocusManager(FocusManager manager) {
        globalFocusManager = manager;
    }

    /**
     * Gets the process-wide focus manager.
     *
     * @return the manager, or null when none is installed
     */
    public static FocusManager getGlobalFocusManager() {
        return globalFocusManager;
    }

    /**
     * Gets the component that currently holds the keyboard focus.
     *
     * @return the focused component, or null
     */
    public Component getFocused() {
        return focusedComponent;
    }

    /**
     * Gives the keyboard focus to a component. Non-focusable, hidden or
     * disabled components are rejected.
     *
     * @param component the component to focus (null clears the focus)
     * @return true if the focus changed as requested
     */
    public boolean requestFocus(Component component) {
        if (component == null) {
            return changeFocus(null);
        }
        if (!isFocusCandidate(component)) {
            return false;
        }
        if (component == focusedComponent) {
            return false;
        }
        return changeFocus(component);
    }

    /**
     * Clears the focus if (and only if) the given component currently holds
     * it. Called when a focused widget becomes hidden/disabled/unfocusable.
     *
     * @param component the component that lost eligibility
     */
    public void clearFocus(Component component) {
        if (focusedComponent == component) {
            changeFocus(null);
        }
    }

    /**
     * Hook invoked by {@code Component.setVisible(false)}.
     *
     * @param component the component whose visibility changed
     */
    public void componentVisibilityChanged(Component component) {
        clearFocusIfIneligible(component);
    }

    /**
     * Hook invoked by {@code Component.setEnabled(false)}.
     *
     * @param component the component that was disabled
     */
    public void componentDisabled(Component component) {
        clearFocusIfIneligible(component);
    }

    private void clearFocusIfIneligible(Component component) {
        if (focusedComponent == component && !isFocusCandidate(component)) {
            changeFocus(null);
        }
    }

    /**
     * Moves the focus to the next focusable component in tree order
     * (Tab). Wraps around at the end.
     *
     * @return true if some component gained the focus
     */
    public boolean focusNext() {
        return moveFocus(true);
    }

    /**
     * Moves the focus to the previous focusable component in tree order
     * (Shift+Tab). Wraps around at the beginning.
     *
     * @return true if some component gained the focus
     */
    public boolean focusPrevious() {
        return moveFocus(false);
    }

    /**
     * Sets the root of the tree used for traversal. The default
     * implementation derives the root by walking up from the focused
     * component, so this is only needed for custom hosting scenarios.
     *
     * @param root the traversal root
     */
    public void setRoot(Panel root) {
        this.traversalRoot = root;
    }

    private boolean moveFocus(boolean forward) {
        Panel root = resolveRoot();
        if (root == null) {
            return false;
        }
        java.util.List<Component> order = new java.util.ArrayList<>();
        collectFocusable(root, order);
        if (order.isEmpty()) {
            return changeFocus(null);
        }
        int index = focusedComponent == null ? -1 : order.indexOf(focusedComponent);
        int next;
        if (index < 0) {
            // No current focus (or it left the tree): start at first/last
            next = forward ? 0 : order.size() - 1;
        } else {
            next = (index + (forward ? 1 : -1) + order.size()) % order.size();
        }
        return changeFocus(order.get(next));
    }

    private Panel resolveRoot() {
        if (traversalRoot != null) {
            return traversalRoot;
        }
        Component c = focusedComponent;
        if (c != null) {
            Panel p = c.getParent();
            while (p != null && p.getParent() != null) {
                p = p.getParent();
            }
            if (p != null) {
                return p;
            }
        }
        return null;
    }

    /**
     * Depth-first collection of focus-eligible descendants. Panels are
     * eligible themselves only if they opt in via {@code setFocusable(true)}
     * on a subclass — the base {@code Panel} keeps the default flag but is
     * included like any other component.
     */
    private void collectFocusable(Component component, java.util.List<Component> out) {
        if (component == null || !component.isVisible()) {
            return;
        }
        if (isFocusCandidate(component)) {
            out.add(component);
        }
        if (component instanceof Panel) {
            for (Component child : ((Panel) component).getChildren()) {
                collectFocusable(child, out);
            }
        }
    }

    private boolean isFocusCandidate(Component component) {
        return component.isFocusable() && component.isEnabled() && component.isVisible();
    }

    private boolean changeFocus(Component newFocus) {
        Component old = focusedComponent;
        if (old == newFocus) {
            return false;
        }
        focusedComponent = newFocus;
        if (old != null) {
            old.setFocusedInternal(false);
        }
        if (newFocus != null) {
            newFocus.setFocusedInternal(true);
        }
        if (repaintCallback != null) {
            repaintCallback.run();
        }
        return true;
    }
}
