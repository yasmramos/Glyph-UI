package com.glyphui.layout;

import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;

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
