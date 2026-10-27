package io.github.yasmramos.glyphui.markup;

/**
 * A command invoked by markup, a menu item or a keyboard shortcut.
 *
 * <p>Actions are registered by name in an {@link ActionRegistry}; the name is
 * always the enum constant's {@link Enum#name()}. The functional shape keeps
 * action implementations as plain lambdas.</p>
 */
@FunctionalInterface
public interface Action {

    /**
     * Runs the action.
     *
     * @param context invocation context (source widget and visible bindings)
     */
    void perform(ActionContext context);
}
