package io.github.yasmramos.glyphui.ui;

/**
 * Functional callback invoked when a right-click requests a context menu on a
 * component.
 *
 * <p>The coordinates are in window space, matching what a {@link ContextMenu}
 * needs to anchor itself under the pointer. This is the handler type installed
 * by {@link Component#setOnContextMenuRequested(ContextMenuListener)} and by
 * {@link ContextMenu#attachTo(Component)}.</p>
 */
@FunctionalInterface
public interface ContextMenuListener {

    /**
     * Invoked when the component receives a context-menu request.
     *
     * @param x the x-coordinate of the request, in window space
     * @param y the y-coordinate of the request, in window space
     */
    void onContextMenuRequested(float x, float y);
}
