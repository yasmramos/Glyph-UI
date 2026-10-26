package io.github.yasmramos.glyphui.ui;

import java.util.ArrayList;
import java.util.List;
import io.github.yasmramos.glyphui.layout.LayoutManager;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Paint;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Dimension;
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
     * Set once the user calls {@code setWidth}/{@code setHeight} on this
     * panel. Combined with the constructor flag (see
     * {@code Component.isCtorSizeExplicit}) it defines "user-pinned size" for
     * {@code Panel.measure}, distinguishing direct user mutations from bounds
     * assigned by an outer layout manager through {@code applyLayoutSize}.
     */
    private boolean userSetSize;

    /**
     * Reusable background paint, created lazily on first render and closed in
     * {@link #dispose()} to avoid per-frame native allocations.
     */
    private Paint bgPaint;

    /**
     * Creates a zero-sized panel to be sized by {@code setWidth}/{@code
     * setHeight} or measured from content by an attached layout manager. This
     * mirrors the protected no-bounds constructor in {@link Component}: a
     * panel built without bounds is not considered user-size-explicit until a
     * direct size mutation occurs (Block 3 item 3.2).
     */
    public Panel() {
        super();
        // The delegating Component constructor does not initialize the
        // container state, so mirror the bounds-carrying constructor here.
        this.children = new ArrayList<>();
        this.layoutManager = null;
        this.layoutDirty = false;
        this.backgroundColor = Color.makeARGB(0, 0, 0, 0); // Transparent by default
        this.userSetSize = false;
        // Containers do not take keyboard focus unless a subclass opts in
        setFocusable(false);
    }

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
     * Measures the panel. A size pinned by this panel's own constructor or by
     * a direct {@code setWidth}/{@code setHeight} call always wins (plain
     * component path), as does any explicit CSS width/height declaration;
     * otherwise, when a layout manager is attached, the layout decides the
     * preferred size from the arranged content. Bounds assigned by an outer
     * layout manager (via {@code applyLayoutSize}) do <em>not</em> count as
     * user-explicit, so nested panels keep reporting their content size.
     */
    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSize = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        // A size pinned by this panel's own bounds-carrying constructor wins
        // through the plain component path, as does any explicit CSS
        // width/height declaration; otherwise, when a layout manager is
        // attached, the layout decides the preferred size from the arranged
        // content. Later {@code setWidth}/{@code setHeight} calls do NOT pin
        // the container: they only mark the current bounds as explicit so an
        // inner layout keeps each child's declared geometry, while the panel
        // itself stays content-sized and keeps delegating to its layout
        // manager (Block 3 item 3.2). Layout-assigned bounds (via
        // {@code applyLayoutSize}) never count as explicit either, so nested
        // panels keep reporting their content size.
        if (layoutManager == null || cssSize || isCtorSizeExplicit()) {
            return super.measure(maxWidth, maxHeight);
        }
        return layoutManager.measure(this, maxWidth, maxHeight);
    }

    /**
     * Whether the user pinned this panel's size: either through a bounds-
     * carrying constructor or through a later {@code setWidth}/{@code
     * setHeight} call. Layout-assigned bounds are excluded on purpose — they
     * must not disable content-based measurement (Block 3 item 3.2).
     *
     * @return true when an explicit user size exists on this panel
     */
    @SuppressWarnings("unused")
    private boolean isUserSizeExplicit() {
        return isCtorSizeExplicit() || userSetSize;
    }

    /**
     * Assigns the panel's drawing bounds without marking them as user-explicit.
     * Test-support hook mirroring what a root frame does when it sizes its
     * content panel through {@code applyLayoutSize}: the panel keeps a concrete
     * rendering surface while {@link #measure(float, float)} still delegates to
     * the attached layout manager (Block 3 item 3.2). Never part of the frozen
     * public API surface; cross-package tests invoke it via reflection.
     *
     * @param width the layout-assigned width
     * @param height the layout-assigned height
     */
    void setSizeForTests(float width, float height) {
        applyLayoutSize(width, height);
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
        // Structural mutation on THIS panel: flag the layout dirty and run
        // one full invalidation (repaint + ancestor propagation). The local
        // flag is set before invalidate() so the generic upward flush does
        // not double-request; see markLayoutDirtyAndInvalidate().
        markLayoutDirtyAndInvalidate();
    }

    /**
     * Removes a child component from this panel. The component is only
     * detached (parent cleared) — it is NOT closed or disposed, so it stays
     * fully usable and may be re-added to any panel afterwards (this is what
     * makes {@link #add(Component)} reparenting work). Removing a component
     * that is not a child of this panel is a no-op. Use
     * {@link #removeAndDispose(Component)} when you want destructive removal.
     *
     * @param component the component to remove
     */
    public void remove(Component component) {
        if (children.remove(component)) {
            component.setParent(null);
        }
        // Same single-invalidation path as add().
        markLayoutDirtyAndInvalidate();
    }

    /**
     * Removes a child component from this panel AND disposes it, releasing
     * its native resources. After this call the component must not be reused.
     * Removing a component that is not a child of this panel is a no-op.
     *
     * @param component the component to remove and dispose
     */
    public void removeAndDispose(Component component) {
        if (children.remove(component)) {
            component.setParent(null);
            component.dispose();
        }
        markLayoutDirtyAndInvalidate();
    }

    /**
     * Removes all child components from this panel without disposing them;
     * every detached child may be re-added to any panel afterwards.
     */
    public void clear() {
        for (Component child : children) {
            child.setParent(null);
        }
        children.clear();
        markLayoutDirtyAndInvalidate();
    }

    /**
     * Removes all child components from this panel and disposes each one,
     * releasing their native resources. After this call the removed children
     * must not be reused. Prefer {@link #clear()} when the children might be
     * re-parented later.
     */
    public void clearAndDispose() {
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
        markLayoutDirtyAndInvalidate();
        if (failure != null) {
            throw failure;
        }
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
     * flushes any pending invalidation intent accumulated on this panel (see
     * {@code Component.invalidate()}): without the flush, a flag set by an
     * upward propagation could linger forever when nothing else repaints the
     * tree under on-demand rendering.
     */
    public void markLayoutDirty() {
        // Pure flag-setter by design: layout managers reach this from inside
        // the paint pass (doLayout), where requesting a repaint would recurse
        // into the invalidation chain. The current frame continues normally;
        // the flag guarantees the layout runs before the next paint. External
        // mutators that also need a repaint go through invalidateLayout().
        layoutDirty = true;
    }

    /**
     * Marks this panel's layout dirty AND requests a repaint for the whole
     * tree, without running the generic upward flush in
     * {@link Component#invalidate()} first.
     *
     * <p>This is the correct entry point for structural mutations made
     * directly on a panel ({@code add}/{@code remove}/{@code clear}): there
     * is no child component whose per-instance dirty flags need flushing, so
     * setting the local flag and calling {@code invalidate()} keeps exactly
     * one repaint request while still propagating to ancestors.</p>
     */
    private void markLayoutDirtyAndInvalidate() {
        layoutDirty = true;
        invalidate();
    }

    /**
     * Marks the layout dirty and requests a repaint through the full
     * invalidation chain (ancestors up to the root plus the application
     * repaint hook). Public counterpart of the protected
     * {@code Component.invalidateLayout()} for callers outside the
     * {@code ui} package — notably {@link LayoutManager#notifyOwner()},
     * which must make a layout-configuration change visible immediately
     * instead of only flagging the panel locally.
     */
    public void invalidateLayoutTree() {
        // Deferred request pattern (same ordering as Component's protected
        // invalidate* helpers): set the per-node layout flag BEFORE running
        // the upward invalidation chain, because invalidate() flushes pending
        // requests at every node it visits — flushing first would drain this
        // panel's freshly-set flag before any paint pass could see it.
        markLayoutDirtyRequested();
        invalidate();
    }

    /**
     * Records a pending relayout request on this panel's own component-level
     * flag. Package-private counterpart of {@code markLayoutDirty()} for the
     * deferred invalidation path used by {@link #invalidateLayoutTree()};
     * keeps the two flags (component-level and panel-level) in sync so both
     * the aggregated and local dirty views report the request consistently.
     */
    @Override
    void markLayoutDirtyRequested() {
        super.markLayoutDirtyRequested();
        layoutDirty = true;
    }

    /**
     * Whether the next paint of this panel requires running its layout
     * manager. Internal instrumentation hook for tests (see
     * {@code Component.getInvalidateCount()} for the boundary policy).
     *
     * <p>Aggregated view: true when this panel holds a pending relayout
     * request <em>or</em> any descendant component still has an unconsumed
     * one. Descendants intentionally keep their own flag set until a layout
     * pass actually runs (lazy drain, see {@link #doLayout()}), so callers
     * must not inspect raw child flags to decide whether work is pending.</p>
     *
     * @return true when a relayout is pending anywhere in this subtree
     */
    boolean isLayoutDirty() {
        return isLayoutDirtyFlag() || hasDirtyDescendant();
    }

    /**
     * Raw local relayout request on this panel only, excluding deferred
     * descendant flags. Test-support hook: assertions that check whether a
     * specific mutation dirtied THIS panel must use this view, because leaf
     * components keep their own flag until an ancestor layout pass consumes
     * it (see {@code Component.flushInvalidationRequests}).
     *
     * @return true when this panel itself has a pending relayout request
     */
    boolean isLocalLayoutDirty() {
        return isLayoutDirtyFlag();
    }

    /**
     * Walks the subtree rooted at this panel looking for a component with a
     * pending relayout request on its own node. Panels drain their local
     * flag eagerly inside {@link #doLayout()}, but leaf components keep
     * theirs until the ancestor panel's next layout pass consumes it; this
     * scan reports those deferred requests.
     *
     * @return true when some descendant requested a relayout not yet consumed
     */
    private boolean hasDirtyDescendant() {
        List<Component> stack = new ArrayList<>(children);
        while (!stack.isEmpty()) {
            Component c = stack.remove(stack.size() - 1);
            if (c.isLayoutDirtyFlag()) {
                return true;
            }
            if (c instanceof Panel) {
                stack.addAll(((Panel) c).children);
            }
        }
        return false;
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
        // Detach the previous manager first so its config setters stop
        // notifying this panel, then attach the new one.
        if (this.layoutManager != null && this.layoutManager != layoutManager) {
            this.layoutManager.attach(null);
        }
        this.layoutManager = layoutManager;
        if (layoutManager != null) {
            layoutManager.attach(this);
        }
        if (layoutManager != null && !children.isEmpty()) {
            // Explicit eager pass: callers expect positions to be updated
            // right after wiring a layout manager over existing children
            // (historical contract). The pass consumes the request — the
            // work is done; a later add() will dirty it again. An empty
            // panel skips the run and keeps the deferred request below,
            // so no layout pass is performed for zero children.
            layoutManager.layout(this);
            layoutDirty = false;
        } else if (layoutManager != null) {
            // Wiring a layout manager over an empty panel is not itself a
            // relayout request: there is nothing to position yet, and add()
            // will dirty the panel when the first child arrives. Keep the
            // flag clean so doLayout() on a still-empty panel stays a no-op.
            layoutDirty = false;
        }
    }

    /**
     * Performs the layout if a layout manager is set and layout is dirty.
     */
    public void doLayout() {
        if (layoutManager != null && layoutDirty) {
            layoutManager.layout(this);
            layoutDirty = false;
            // The relayout ran, so this panel's own deferred request (set by
            // invalidateLayoutTree via markLayoutDirtyRequested) is consumed
            // too — otherwise the aggregated dirty view would keep reporting
            // work that has already been done.
            clearLayoutRequest();
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
            userSetSize = true;
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
            userSetSize = true;
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
