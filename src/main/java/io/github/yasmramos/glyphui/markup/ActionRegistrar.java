package io.github.yasmramos.glyphui.markup;

/**
 * Binds handlers while an {@link Actions} group is being registered.
 *
 * @param <E> the action enum
 */
public interface ActionRegistrar<E extends Enum<E>> {

    /**
     * Binds a handler to an action id.
     *
     * @param id     the enum constant
     * @param action the handler
     */
    void on(E id, Action action);

    /**
     * Binds a no-argument handler to an action id.
     *
     * @param id     the enum constant
     * @param action the handler
     */
    default void on(E id, Runnable action) {
        if (action == null) {
            on(id, (Action) null);
        } else {
            on(id, context -> action.run());
        }
    }
}
