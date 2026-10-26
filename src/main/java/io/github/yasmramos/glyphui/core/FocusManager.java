package io.github.yasmramos.glyphui.core;

import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Panel;

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
     * <p><b>Design note (Block 4 item 4.1):</b> this global instance assumes a
     * single application per process and is kept as a compatibility fallback so
     * {@code Component.requestFocus()} works from anywhere in the tree. In
     * multi-app, preview or embedded scenarios each {@link Application} owns
     * its own {@code FocusManager}; resolve focus through that per-app context
     * and treat the global accessor as the no-context fallback only.</p>
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
        return requestFocus(component, true);
    }

    /**
     * Gives the keyboard focus to a component in response to a pointer press.
     *
     * <p>The component becomes the focus owner (Enter/Space keep working)
     * but it is marked as pointer-focused, so widgets that paint a focus ring
     * keep it hidden until the keyboard is used, matching the web's
     * {@code :focus-visible} behaviour. A press on an already focused widget
     * that currently shows its ring clears that ring.</p>
     *
     * @param component the component to focus (null clears the focus)
     * @return true if the focus state changed as requested
     */
    public boolean requestFocusFromPointer(Component component) {
        return requestFocus(component, false);
    }

    /**
     * Marks the current focus as keyboard-visible (Tab traversal or any key
     * sent to the focused widget). Used so a widget focused by the pointer
     * shows its ring as soon as the user interacts with it from the keyboard.
     *
     * @return true if the visible flag changed
     */
    public boolean revealFocus() {
        Component current = focusedComponent;
        if (current == null || current.isFocusVisible()) {
            return false;
        }
        current.applyFocusChange(true, true);
        repaint();
        return true;
    }

    private boolean requestFocus(Component component, boolean focusVisible) {
        if (component == null) {
            return changeFocus(null, false);
        }
        if (!isFocusCandidate(component)) {
            return false;
        }
        return changeFocus(component, focusVisible);
    }

    /**
     * Clears the focus if (and only if) the given component currently holds
     * it. Called when a focused widget becomes hidden/disabled/unfocusable.
     *
     * @param component the component that lost eligibility
     */
    public void clearFocus(Component component) {
        if (focusedComponent == component) {
            changeFocus(null, false);
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
            changeFocus(null, false);
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
            return changeFocus(null, false);
        }
        int index = focusedComponent == null ? -1 : order.indexOf(focusedComponent);
        int next;
        if (index < 0) {
            // No current focus (or it left the tree): start at first/last
            next = forward ? 0 : order.size() - 1;
        } else {
            next = (index + (forward ? 1 : -1) + order.size()) % order.size();
        }
        // Tab traversal always reveals the ring: the user needs to see where
        // the keyboard focus landed.
        return changeFocus(order.get(next), true);
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
        if (component instanceof Panel panel) {
            for (Component child : panel.getChildren()) {
                collectFocusable(child, out);
            }
        }
    }

    private boolean isFocusCandidate(Component component) {
        return component.isFocusable() && component.isEnabled() && component.isVisible();
    }

    private boolean changeFocus(Component newFocus, boolean focusVisible) {
        Component old = focusedComponent;
        if (old == newFocus) {
            if (newFocus == null || newFocus.isFocusVisible() == focusVisible) {
                return false;
            }
            // Same owner, new acquisition: e.g. a keyboard-focused button is
            // pressed with the pointer and must drop its ring.
            newFocus.applyFocusChange(true, focusVisible);
            repaint();
            return true;
        }
        focusedComponent = newFocus;
        if (old != null) {
            old.applyFocusChange(false);
        }
        if (newFocus != null) {
            newFocus.applyFocusChange(true, focusVisible);
        }
        repaint();
        return true;
    }

    private void repaint() {
        if (repaintCallback != null) {
            repaintCallback.run();
        }
    }
}
