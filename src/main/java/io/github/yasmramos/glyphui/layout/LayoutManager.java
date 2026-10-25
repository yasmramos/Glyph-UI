package io.github.yasmramos.glyphui.layout;

import io.github.yasmramos.glyphui.ui.Panel;

/**
 * Abstract base class for layout managers.
 */
public abstract class LayoutManager {

    /**
     * Arranges the components within the panel.
     *
     * @param panel the panel to arrange components in
     */
    public abstract void layout(Panel panel);
}
