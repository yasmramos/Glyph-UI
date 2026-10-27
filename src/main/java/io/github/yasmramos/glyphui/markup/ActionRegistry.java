package io.github.yasmramos.glyphui.markup;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Name-to-action map used by markup and menus.
 *
 * <p>Action names are always {@link Enum#name()} values, so registration is
 * explicit and case-sensitive: {@code SAVE} never matches {@code save}.
 * Registering an {@link Actions} group validates that every declared id gets a
 * handler and fails loudly instead of leaving a silently dead command.</p>
 */
public final class ActionRegistry {

    private final Map<String, Action> actions = new LinkedHashMap<>();

    /**
     * Registers an enum-backed group of actions.
     *
     * @param group the group whose {@link Actions#ids()} are declared
     * @param <E>   the action enum
     * @throws IllegalArgumentException when the group is null, declares no
     *                                  ids, or leaves an id without a handler
     */
    public <E extends Enum<E>> void register(Actions<E> group) {
        if (group == null) {
            throw new IllegalArgumentException("Action group must not be null");
        }
        E[] ids = group.ids();
        if (ids == null || ids.length == 0) {
            throw new IllegalArgumentException(
                    "Action group " + group.getClass().getName() + " declares no ids");
        }

        Set<E> declared = new LinkedHashSet<>();
        for (E id : ids) {
            if (id == null) {
                throw new IllegalArgumentException(
                        "Action group " + group.getClass().getName()
                                + " declares a null id");
            }
            declared.add(id);
        }

        Registrar<E> registrar = new Registrar<>(declared);
        group.register(registrar);
        registrar.requireComplete();
        registrar.commit();
    }

    /**
     * Registers (or replaces) one action by name.
     *
     * @param name   the exact action name
     * @param action the handler
     * @throws IllegalArgumentException when the name is blank or the action is null
     */
    public void register(String name, Action action) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Action name must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException(
                    "Action handler for '" + name + "' must not be null");
        }
        actions.put(name, action);
    }

    /**
     * Resolves an action by name.
     *
     * @param name the exact action name
     * @return the registered handler
     * @throws IllegalArgumentException when no action is registered
     */
    public Action get(String name) {
        Action action = name == null ? null : actions.get(name);
        if (action == null) {
            throw new IllegalArgumentException(
                    "Unknown action: '" + name + "'. Registered actions: " + actions.keySet());
        }
        return action;
    }

    /**
     * @param name the action name
     * @return true when an action is registered under this exact name
     */
    public boolean knows(String name) {
        return name != null && actions.containsKey(name);
    }

    /**
     * @return the registered action names, in registration order
     */
    public Set<String> names() {
        return Collections.unmodifiableSet(actions.keySet());
    }

    /**
     * Registrar handed to one {@link Actions} group. It rejects ids the group
     * did not declare and records which declared ids still need a handler.
     */
    private final class Registrar<E extends Enum<E>> implements ActionRegistrar<E> {

        private final Set<E> declared;
        private final Set<E> bound = new LinkedHashSet<>();
        private final Map<String, Action> pending = new LinkedHashMap<>();

        Registrar(Set<E> declared) {
            this.declared = declared;
        }

        @Override
        public void on(E id, Action action) {
            if (id == null) {
                throw new IllegalArgumentException("Action id must not be null");
            }
            if (!declared.contains(id)) {
                throw new IllegalArgumentException(
                        "Action id " + id + " was not declared in ids()");
            }
            if (action == null) {
                throw new IllegalArgumentException(
                        "Action handler for " + id + " must not be null");
            }
            pending.put(id.name(), action);
            bound.add(id);
        }

        void requireComplete() {
            if (bound.containsAll(declared)) {
                return;
            }
            Set<String> missing = new LinkedHashSet<>();
            for (E id : declared) {
                if (!bound.contains(id)) {
                    missing.add(id.name());
                }
            }
            throw new IllegalArgumentException(
                    "Action group is missing handlers for " + missing);
        }

        void commit() {
            actions.putAll(pending);
        }
    }
}
