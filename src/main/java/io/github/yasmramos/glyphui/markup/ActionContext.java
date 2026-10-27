package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.Component;

/**
 * Minimal invocation context handed to an {@link Action}.
 *
 * <p>It carries the widget that triggered the action (for source-sensitive
 * commands such as row actions) and the bindings visible at the call site.
 * The pair is also the seam where a future batching layer can collect
 * mutations before flushing them to the UI.</p>
 */
public final class ActionContext {

    private final Component source;
    private final Bindings bindings;

    /**
     * Creates a context.
     *
     * @param source   the widget that triggered the action, or null when the
     *                 action was invoked programmatically
     * @param bindings the bindings visible to the action, never null
     * @throws IllegalArgumentException when bindings is null
     */
    public ActionContext(Component source, Bindings bindings) {
        if (bindings == null) {
            throw new IllegalArgumentException("ActionContext bindings must not be null");
        }
        this.source = source;
        this.bindings = bindings;
    }

    /**
     * @return the triggering widget, or null for programmatic invocations
     */
    public Component getSource() {
        return source;
    }

    /**
     * @return the bindings visible at the invocation site, never null
     */
    public Bindings getBindings() {
        return bindings;
    }
}
