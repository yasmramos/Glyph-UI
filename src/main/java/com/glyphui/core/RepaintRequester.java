package com.glyphui.core;

/**
 * Callback interface used by UI components to request a repaint from the
 * owning {@link Application}. An implementation is typically provided via
 * {@link Application#setRepaintRequester(RepaintRequester)}.
 */
@FunctionalInterface
public interface RepaintRequester {
    /**
     * Requests that the application schedules a new paint pass.
     */
    void requestRepaint();
}
