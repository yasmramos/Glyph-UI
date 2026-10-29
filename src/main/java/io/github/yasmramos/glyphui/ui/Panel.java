package io.github.yasmramos.glyphui.ui;

import java.util.ArrayList;
import java.util.List;
import io.github.yasmramos.glyphui.layout.AbsoluteLayout;
import io.github.yasmramos.glyphui.layout.FlexLayout;
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

    /**
     * Shared default layout manager for panels whose user never picked one
     * (Swing/AWT lesson #3). A stateless COLUMN FlexLayout: per-panel
     * configuration lives in the computed style the layout reads at pass
     * time, so a single instance is safe to share. Never attached to any
     * panel (no {@code attach}/{@code owner}), so its config setters cannot
     * notify a random container.
     */
    private static final LayoutManager DEFAULT_LAYOUT_MANAGER =
            new FlexLayout(FlexLayout.Direction.COLUMN);

    protected List<Component> children;
    protected LayoutManager layoutManager;
    protected boolean layoutDirty;
    protected int backgroundColor;

    /**
     * True once the user explicitly calls {@link #setBackgroundColor(int)}.
     * Replaces the previous transparent-color sentinel (Block 5 item 5.26):
     * an explicit fully transparent background is now honored instead of
     * silently falling back to the theme window background.
     */
    private boolean hasExplicitBackground;

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
        initPanel();
    }

    /**
     * Creates a panel with an explicit size but no position.
     *
     * @param width the panel width
     * @param height the panel height
     */
    public Panel(float width, float height) {
        this(0f, 0f, width, height);
    }

    /**
     * Creates a new Panel.
     *
     * <p>A freshly constructed panel installs {@link FlexLayout} as its
     * default layout manager (Swing/AWT lesson #3): the container and its
     * layout start coupled in the supported direction — the layout reads the
     * panel's computed style ({@code flex-direction}, {@code gap}, padding)
     * instead of carrying private defaults that ignore the stylesheet. The
     * field itself stays {@code null} until the first child arrives (see
     * {@link #add(Component)}), so empty panels never pay for a layout pass.</p>
     *
     * @param x the x-coordinate of the panel
     * @param y the y-coordinate of the panel
     * @param width the width of the panel
     * @param height the height of the panel
     */
    public Panel(float x, float y, float width, float height) {
        super(x, y, width, height);
        initPanel();
    }

    /**
     * Shared container initialization for all constructors. Mirrors the
     * bounds-carrying constructor state because the delegating protected
     * {@link Component} constructor does not initialize container fields.
     */
    private void initPanel() {
        this.children = new ArrayList<>();
        this.layoutManager = null;
        this.layoutDirty = false;
        this.backgroundColor = Color.makeARGB(0, 0, 0, 0); // Transparent by default
        this.userSetSize = false;
        // Containers do not take keyboard focus unless a subclass opts in
        setFocusable(false);
    }

    /**
     * Tracks whether {@link #setLayoutManager(LayoutManager)} was called
     * explicitly (with any value, including {@code null}). Once the user has
     * chosen — or deliberately declined — a layout manager, {@link
     * #add(Component)} must not second-guess that decision. Package-private
     * so tests can assert the contract without reflection.
     */
    boolean layoutManagerExplicitlySet;

    /**
     * Returns the shared default layout manager for panels whose user never
     * picked one (Swing/AWT lesson #3: containers and their layout start
     * coupled through the supported channel — the layout reads the panel's
     * computed style: flex-direction, gap and padding come from the
     * stylesheet instead of being ignored).
     *
     * <p><strong>Deliberately not installed into the field.</strong> An
     * earlier iteration attached a fresh {@code FlexLayout} on the first
     * {@code add()}, which silently overrode managers configured BEFORE
     * attachment (tests do {@code flex.setDirection(COLUMN); panel.add(...)})
     * and made every default panel flow-row even when its stylesheet asked
     * for a column. The default lives per-call instead: {@link #measure}
     * delegates to it for content sizing, and {@link #doLayout()} runs it
     * when no explicit manager exists. Panels without children still pay no
     * layout pass, subclasses with their own manager are untouched, and
     * externally configured managers keep working exactly as before.</p>
     *
     * @return the default layout manager, never {@code null}
     */
    private io.github.yasmramos.glyphui.layout.LayoutManager defaultLayoutManager() {
        return DEFAULT_LAYOUT_MANAGER;
    }

    /**
     * Content-based preferred width for panels without an explicit size and
     * without a layout manager (Block 4 item 4.3). Instead of falling back to
     * {@code DEFAULT_WIDTH}, the value is derived from the children's measured
     * widths plus declared horizontal padding, so a bare container reports the
     * size its content needs. With a layout manager attached, measurement goes
     * through {@link #measure(float, float)} instead and this fallback is not
     * consulted by the layout itself.
     *
     * @return the content-derived preferred width
     */
    @Override
    public float getPreferredWidth() {
        if (layoutManager != null || children.isEmpty()) {
            return super.getPreferredWidth();
        }
        float maxRight = 0f;
        for (Component child : children) {
            Dimension d = child.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            float right = Math.max(0f, child.getX()) + d.getWidth();
            if (right > maxRight) {
                maxRight = right;
            }
        }
        return maxRight > 0f ? maxRight : super.getPreferredWidth();
    }

    /**
     * Content-based preferred height for panels without an explicit size and
     * without a layout manager (Block 4 item 4.3). Mirrors
     * {@link #getPreferredWidth()} using each child's measured bottom edge.
     *
     * @return the content-derived preferred height
     */
    @Override
    public float getPreferredHeight() {
        if (layoutManager != null || children.isEmpty()) {
            return super.getPreferredHeight();
        }
        float maxBottom = 0f;
        for (Component child : children) {
            Dimension d = child.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            float bottom = Math.max(0f, child.getY()) + d.getHeight();
            if (bottom > maxBottom) {
                maxBottom = bottom;
            }
        }
        return maxBottom > 0f ? maxBottom : super.getPreferredHeight();
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
        if (cssSize || isCtorSizeExplicit()) {
            return super.measure(maxWidth, maxHeight);
        }
        if (layoutManager != null) {
            return layoutManager.measure(this, maxWidth, maxHeight);
        }
        // No chosen manager: keep the plain content-bounding-box path. The
        // default column flow only rewrites positions at paint time (see
        // doLayout()), so measuring raw bounds stays consistent with what
        // getPreferredWidth/Height report for unmanaged panels.
        return super.measure(maxWidth, maxHeight);
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
        // No eager layout here (historical contract): positions stay exactly
        // as the user left them until the next paint runs doLayout(). Panels
        // that never chose a manager are not frozen in absolute mode either —
        // doLayout() applies the default column flow when the dirty flag set
        // below reaches a render pass (Swing/AWT lesson #3).
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
        // Guard for the render path's deferred-cleanup contract: Component
        // schedules dispose on the event queue, so a tree disposed while its
        // paint is still executing must not recurse into children that
        // render() intentionally left alive after the loop finished.
        if (children.isEmpty()) {
            return;
        }
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
     * <p>Disposed descendants are pruned (Block 5 item 5.28 hardening): the
     * public {@link #getChildren()} list is live and external code may have
     * scheduled a child's disposal without removing it from the tree yet, so
     * the scan must not cross into subtrees whose native resources are
     * already released.</p>
     *
     * @return true when some descendant requested a relayout not yet consumed
     */
    private boolean hasDirtyDescendant() {
        List<Component> stack = new ArrayList<>();
        for (Component child : children) {
            if (!child.isDisposed()) {
                stack.add(child);
            }
        }
        while (!stack.isEmpty()) {
            Component c = stack.remove(stack.size() - 1);
            if (c.isLayoutDirtyFlag()) {
                return true;
            }
            if (c instanceof Panel) {
                for (Component grandchild : ((Panel) c).children) {
                    if (!grandchild.isDisposed()) {
                        stack.add(grandchild);
                    }
                }
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
     * Extension point for subtree-level theming (Swing/AWT lesson #8).
     *
     * <p>The default implementation returns {@code null}, meaning "no scoped
     * theme declared here": descendants keep walking up the ancestor chain
     * until they find a {@link Component#setTheme(Theme)} override or fall
     * back to {@link Theme#current()}. Subclasses (or anonymous panels) may
     * override this hook to provide a dynamic or externally-managed theme
     * context without touching per-widget state.</p>
     *
     * <p>For one-off scoping prefer {@code setTheme(...)} on the container
     * itself; use this hook when the theme comes from another source (a
     * window's app context, a plugin registry, etc.).</p>
     *
     * @return a scoped theme for this subtree, or {@code null} to inherit
     */
    protected Theme resolveTheme() {
        return null;
    }

    /**
     * Sets the layout manager for this panel and triggers an immediate relayout
     * if there are children.
     *
     * <p><strong>Passing {@code null} is allowed but discouraged.</strong> A
     * null layout means this panel performs no arrangement at all: children
     * keep whatever {@code setX}/{@code setY}/{@code setWidth}/{@code
     * setHeight} assigned, exactly like Swing's {@code setLayout(null)} —
     * historically a beginner trap that produces UIs which break under font,
     * DPI or locale changes (Swing/AWT lesson #4). If manual positioning is
     * what you want, install {@link AbsoluteLayout} instead: it makes the
     * intent explicit in the code, keeps every panel on the uniform layout
     * pipeline, and lets a future switch to a flow-based layout reposition
     * children without stale-state surprises. For everything else prefer
     * {@link io.github.yasmramos.glyphui.layout.FlexLayout} or
     * {@link io.github.yasmramos.glyphui.layout.FlowLayout}.</p>
     *
     * @param layoutManager the layout manager to set, or {@code null} to opt
     *                      out of automatic layout (see
     *                      {@link AbsoluteLayout} for the supported way)
     */
    public void setLayoutManager(LayoutManager layoutManager) {
        // Any explicit choice (including null) disables the lazy default
        // installation performed by add() (Swing/AWT lesson #3/#4).
        layoutManagerExplicitlySet = true;
        // Detach the previous manager first so its config setters stop
        // notifying this panel, then attach the new one.
        if (this.layoutManager != null && this.layoutManager != layoutManager) {
            this.layoutManager.attach(null);
        }
        this.layoutManager = layoutManager;
        if (layoutManager != null) {
            layoutManager.attach(this);
        }
        if (layoutManager != null) {
            // Eager pass whenever there is work pending: children already
            // attached (historical contract — callers expect positions
            // updated right after wiring a manager over existing children)
            // or the layout flag is still dirty from a structural mutation
            // that has not run any pass yet (e.g. add() before the first
            // render). Without consuming the pending flag here, a panel
            // whose default lazy layout was installed AFTER a child was
            // added would keep deferring the pass forever under Absolute-
            // style managers that honor explicit child bounds as-is, and
            // the test harness (which renders without a windowing relayout
            // trigger) would paint stale positions.
            if (!children.isEmpty() || layoutDirty) {
                layoutManager.layout(this);
            }
            layoutDirty = false;
        } else {
            // Nulling the manager means no automatic arrangement will run,
            // so there is no pending work to keep: clear the flag.
            layoutDirty = false;
        }
    }

    /**
     * Performs the layout if a layout manager is set and layout is dirty.
     */
    public void doLayout() {
        if (layoutManager == null && !layoutManagerExplicitlySet && layoutDirty
                && !children.isEmpty()) {
            // Default flow keeps running for panels that never chose a
            // manager (lesson #3): structural/style mutations re-stack the
            // column instead of leaving stale positions behind.
            defaultLayoutManager().layout(this);
            layoutDirty = false;
            clearLayoutRequest();
            return;
        }
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
        // Block 5 item 5.26: an explicit boolean flag replaces the old
        // transparent-color sentinel, so setBackgroundColor(0x00000000) is
        // honored as "explicitly transparent" instead of reverting to the
        // theme window background.
        if (!hasExplicitBackground) {
            // Block 5 item 5.25: resolve through Component.getTheme() for
            // consistency with every other themed accessor; a future
            // per-subtree theme hook only needs to override that one method.
            //
            // ThemeContext nuance (Swing/AWT lesson #8): getTheme() resolves
            // own-override → ancestor context → Theme.current(). A panel that
            // carries a scoped theme is the CONTEXT itself — it must not
            // inherit that theme as its own background (otherwise "theming a
            // window" would silently repaint the window container). So when
            // no ANCESTOR declares a context, fall back to the process-wide
            // theme directly instead of this panel's own override; a themed
            // card inside a themed window still reads the outer theme because
            // the ancestor lookup wins first.
            Theme ancestor = resolveAncestorThemeContext();
            return (ancestor != null ? ancestor : Theme.current()).getBackgroundColor();
        }
        return backgroundColor;
    }

    /**
     * Whether the user explicitly set a background color on this panel via
     * {@link #setBackgroundColor(int)} (Block 5 item 5.26). Package-private
     * test seam; public API is {@link #getBackgroundColor()} and
     * {@link #clearExplicitBackground()}.
     *
     * @return true when an explicit background was set and not cleared
     */
    boolean hasExplicitBackground() {
        return hasExplicitBackground;
    }

    /**
     * Raw (non-CSS-resolved) value of the {@code backgroundColor} field.
     * Test-support hook used by the Block 5 regression suite to prove the
     * cascade never writes the per-instance field (item 5.27): the render
     * path must go through {@link #getBackgroundColor()}, not this value.
     * Never part of the frozen public API surface.
     *
     * @return the raw field value
     */
    int rawBackgroundColorForTests() {
        return backgroundColor;
    }

    /**
     * Whether the effective background color should be painted. True when a
     * CSS rule declares {@code background}, or when the user explicitly set a
     * background whose alpha channel is non-zero. An unset or fully
     * transparent background means "the theme paints behind me", so the panel
     * itself must not draw anything (Block 5 items 5.26 and 5.27).
     *
     * @return true when {@link #render} must fill the panel bounds
     */
    boolean shouldPaintBackground() {
        Style s = getComputedStyle();
        if (s != null && s.has(StyleProperty.BACKGROUND)) {
            // A CSS-declared background always issues a draw op, even at
            // alpha 0: the user explicitly asked for that layer, and the
            // "should I paint?" decision stays separate from color
            // resolution (Block 5 items 5.26/5.27 contract). Skia may skip
            // fully transparent fills as an optimization, so the rasterized
            // result of an alpha-0 CSS background is implementation-defined;
            // tests assert the draw-op intent through this helper instead of
            // pixel sniffing.
            return true;
        }
        // ThemeContext (Swing/AWT lesson #8): a panel whose effective theme
        // comes from a scoped context — its own override or an ancestor
        // declaring one — paints that theme's window background over its own
        // bounds, so the subtree is actually themed at the pixel level.
        // Without this, setTheme(...) would only affect descendants' control
        // colors while containers stayed transparent and the outer backdrop
        // bled through — visually identical to "the scoped theme does
        // nothing". The root of a scoped tree (the node that carries the
        // override itself) is excluded: theming a window must not silently
        // repaint the window container when no context surrounds it (see the
        // matching exclusion in getBackgroundColor()).
        if (resolveAncestorThemeContext() != null) {
            return true;
        }
        return hasExplicitBackground && (backgroundColor >>> 24) != 0;
    }

    /**
     * Sets the background color of this panel. Passing a fully transparent
     * color is honored as an explicit choice (the panel paints nothing but no
     * longer follows the theme background); call
     * {@link #clearExplicitBackground()} to return to theme-driven painting.
     *
     * @param backgroundColor the new background color (as ARGB int)
     */
    public void setBackgroundColor(int backgroundColor) {
        if (this.backgroundColor != backgroundColor || !hasExplicitBackground) {
            this.backgroundColor = backgroundColor;
            this.hasExplicitBackground = true;
            requestRepaint();
        }
    }

    /**
     * Clears any explicit background so the panel again follows the current
     * theme's window background (Block 5 item 5.26).
     */
    public void clearExplicitBackground() {
        if (hasExplicitBackground) {
            hasExplicitBackground = false;
            backgroundColor = Color.makeARGB(0, 0, 0, 0);
            requestRepaint();
        }
    }

    /**
     * Whether a component's computed style declares {@code display: none}
     * (Block 5 item 5.28). Shared by the render loop and exposed as a static
     * helper so layout managers keep using their own equivalent check.
     *
     * @param child the component to inspect
     * @return true when the computed display is exactly "none"
     */
    static boolean isDisplayNone(Component child) {
        Style s = child.getComputedStyle();
        return s != null && "none".equals(s.getString(StyleProperty.DISPLAY, null));
    }

    @Override
    protected void render(io.github.yasmramos.glyphui.graphics.Canvas canvas) {
        // Ensure layout is applied before rendering children. The framework
        // template Component.renderInternal() has already checked visibility,
        // saved the canvas and translated the origin to this panel's top-left
        // corner (and restores it afterwards), so everything here draws in
        // local coordinates relative to (0, 0).
        doLayout();

        // Draw the effective background when it should be painted. Block 5
        // item 5.27: consult getBackgroundColor() (CSS-aware) instead of the
        // raw field, so a stylesheet `background: red` actually paints; the
        // separate shouldPaintBackground() check keeps theme-only panels from
        // drawing anything themselves. The paint object is reused across
        // frames to avoid per-frame native allocations.
        if (shouldPaintBackground()) {
            ensureBgPaint();
            bgPaint.setColor(getBackgroundColor());
            canvas.drawRect(0, 0, width, height, bgPaint);
        }

        // Clip to the panel's bounds so that children cannot draw outside
        // the panel. The surrounding save/restore is owned by the framework
        // template (Swing lesson #7: no widget can forget "super"), so only
        // the extra clip needs saving here.
        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);

        try {
            // Render all children in local coordinates. Block 5 item 5.28:
            // a computed `display: none` hides the child from rendering too,
            // not just from flex layout (FlexLayout already filters it out).
            for (Component child : children) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            // Restore the clip (the template restores the outer state).
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
