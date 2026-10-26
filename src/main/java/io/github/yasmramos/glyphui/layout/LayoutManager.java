package io.github.yasmramos.glyphui.layout;

import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.ui.Panel;

/**
 * Abstract base class for layout managers.
 *
 * <p>A layout manager participates in two passes of the UI pipeline:</p>
 * <ul>
 *   <li>{@link #measure(Panel, float, float)} — computes the intrinsic size a
 *       panel would like to have for its children (used when the panel is
 *       itself measured by an ancestor layout).</li>
 *   <li>{@link #layout(Panel)} — positions and sizes the children inside the
 *       panel's current bounds.</li>
 * </ul>
 */
public abstract class LayoutManager {

    /**
     * The panel this manager is currently attached to, or {@code null} when
     * detached. Set by {@link Panel#setLayoutManager(LayoutManager)} so that
     * configuration setters on concrete layouts can notify the container of
     * geometry changes (relayout request) without callers wiring callbacks.
     */
    protected Panel owner;

    /**
     * Computes the preferred size of {@code panel} for its children under the
     * given constraints.
     *
     * <p>The default implementation ignores the layout configuration and
     * delegates to the plain component measurement path (explicit bounds or
     * the base defaults), preserving the historical behavior for layouts that
     * do not implement content-based sizing. Concrete layouts that pack
     * children should override this and return the bounding box of the
     * arranged content plus their own padding.</p>
     *
     * @param panel     the panel being measured
     * @param maxWidth  maximum available width (may be
     *                  {@link Float#POSITIVE_INFINITY})
     * @param maxHeight maximum available height (may be
     *                  {@link Float#POSITIVE_INFINITY})
     * @return the measured dimension, never negative
     */
    public Dimension measure(Panel panel, float maxWidth, float maxHeight) {
        return panel.measureContent(maxWidth, maxHeight);
    }

    /**
     * Arranges the components within the panel.
     *
     * <p><strong>Contract:</strong> {@code layout()} must not change the size
     * of the panel it lays out (no {@code panel.setWidth/setHeight}). The
     * layout pass runs while the panel is painting; resizing the panel from
     * here would dirty the layout again and produce an invalidation loop. If
     * a layout genuinely needs to resize its panel, the new size must be
     * idempotent — computing and assigning the same value every pass — so the
     * second run detects "no change" and does not re-dirty anything.</p>
     *
     * @param panel the panel to arrange components in
     */
    public abstract void layout(Panel panel);

    /**
     * Attaches this manager to its owning panel. Called by
     * {@link Panel#setLayoutManager(LayoutManager)}; passing {@code null}
     * detaches. Overriding implementations that need the notification hook
     * (e.g. config setters requesting a relayout) get it for free through
     * {@link #notifyOwner()}.
     *
     * @param panel the owning panel or null
     */
    public void attach(Panel panel) {
        this.owner = panel;
    }

    /**
     * Requests a relayout of the owning panel. No-op while detached. Use from
     * configuration setters ({@code setGap}, {@code setDirection}, ...) so a
     * change becomes visible on the next frame without waiting for some other
     * mutation to dirty the layout.
     */
    protected void notifyOwner() {
        if (owner != null) {
            // Full invalidation chain: mark the panel layout-dirty AND walk up
            // the tree requesting a repaint, so a configuration change made
            // from user code becomes visible immediately under on-demand
            // rendering. Panel exposes the same operation publicly because
            // layout managers live in a different package than Component.
            owner.invalidateLayoutTree();
        }
    }
}
