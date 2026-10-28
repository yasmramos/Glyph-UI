package io.github.yasmramos.glyphui.markup;

/**
 * An enum-backed group of actions.
 *
 * <p>The enum constants are the action vocabulary: their {@link Enum#name()}
 * values are the exact keys registered in an {@link ActionRegistry}.
 * Implementations declare the constants in {@link #ids()} and bind handlers
 * through the registrar:</p>
 *
 * <pre>{@code
 * enum FileAction { SAVE, OPEN }
 *
 * Actions<FileAction> fileActions = new Actions<>() {
 *     public FileAction[] ids() { return FileAction.values(); }
 *     public void register(ActionRegistrar<FileAction> r) {
 *         r.on(FileAction.SAVE, ctx -> save());
 *         r.on(FileAction.OPEN, this::open);
 *     }
 * };
 * }</pre>
 *
 * @param <E> the action enum
 */
public interface Actions<E extends Enum<E>> {

    /**
     * @return the declared action ids, never null
     */
    E[] ids();

    /**
     * Binds handlers to ids through the registrar.
     *
     * @param registrar the registrar supplied by the action registry
     */
    void register(ActionRegistrar<E> registrar);
}
