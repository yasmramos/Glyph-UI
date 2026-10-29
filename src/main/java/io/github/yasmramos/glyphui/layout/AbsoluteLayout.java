package io.github.yasmramos.glyphui.layout;

import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Panel;

/**
 * A layout manager that leaves every child exactly where its {@code x}/{@code
 * y}/{@code width}/{@code height} properties put it. This is the explicit,
 * supported replacement for Swing's infamous {@code setLayout(null)} idiom.
 *
 * <p>Swing allowed a container to have <em>no</em> layout at all, which made
 * absolute positioning look like the default path and trapped beginners into
 * UIs that break when the font size, DPI or locale changes (lesson #4 of the
 * Swing/AWT post-mortem). Glyph-UI inverts that: a panel always has a layout
 * manager, and manual positioning must be requested on purpose by installing
 * an {@code AbsoluteLayout}.</p>
 *
 * <p>Prefer {@link FlexLayout} or {@link FlowLayout} for real UIs. Use this
 * layout only for genuinely fixed-position content: canvas-like editors,
 * decorative overlays, or porting code that already computes positions
 * itself.</p>
 *
 * <pre>{@code
 * Panel board = new Panel();
 * board.setLayoutManager(new AbsoluteLayout());
 * token.setX(120); token.setY(40);   // positions are honored verbatim
 * board.add(token);
 * }</pre>
 */
public class AbsoluteLayout extends LayoutManager {

    /**
     * No-op: children keep the geometry assigned through their setters. The
     * method still exists so the framework can treat every panel uniformly
     * and so a later switch to a flow-based layout does not silently freeze
     * stale positions.
     *
     * @param panel the panel whose children are positioned manually
     */
    @Override
    public void layout(Panel panel) {
        for (Component child : panel.getChildren()) {
            // Touching the size getters keeps this pass meaningful under
            // future measure-on-demand optimizations; today the contract is
            // simply "do not move anything".
            child.getWidth();
            child.getHeight();
        }
    }
}
