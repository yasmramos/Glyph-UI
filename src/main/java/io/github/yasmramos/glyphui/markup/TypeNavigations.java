package io.github.yasmramos.glyphui.markup;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of declared model types and their member navigations.
 *
 * <p>Only metadata traversal ({@link Class#getSuperclass()},
 * {@link Class#getInterfaces()}) is used to resolve a registered supertype or
 * interface; reflection is never used to read fields or invoke methods.
 * A lookup checks the exact class first, then its superclass chain, then its
 * interfaces (depth-first, skipping duplicates).</p>
 */
public final class TypeNavigations {

    private final Map<Class<?>, TypeNavigation<?>> navigations = new LinkedHashMap<>();

    /**
     * Starts (or continues) the declaration for a type.
     *
     * <p>Registering the same class twice returns the existing navigation so
     * members can be added from separate configuration blocks.</p>
     *
     * @param type the model type
     * @param <T>  the model type
     * @return a fluent navigation builder for the type
     * @throws IllegalArgumentException when the type is null
     */
    public <T> TypeNavigation<T> register(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Model type must not be null");
        }
        TypeNavigation<?> existing = navigations.get(type);
        if (existing != null) {
            @SuppressWarnings("unchecked")
            TypeNavigation<T> typed = (TypeNavigation<T>) existing;
            return typed;
        }
        TypeNavigation<T> created = new TypeNavigation<>(type, this);
        navigations.put(type, created);
        return created;
    }

    /**
     * Resolves the navigation for a type, falling back to registered
     * superclasses and interfaces.
     *
     * @param type the exact type or a subtype of a registered type
     * @param <T>  the model type
     * @return the navigation
     * @throws IllegalArgumentException when neither the type nor any of its
     *                                  supertypes is registered
     */
    public <T> TypeNavigation<T> get(Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("Model type must not be null");
        }
        TypeNavigation<?> found = find(type, new HashSet<>());
        if (found == null) {
            throw new IllegalArgumentException(
                    "No TypeNavigation registered for " + type.getName()
                            + " or its supertypes");
        }
        @SuppressWarnings("unchecked")
        TypeNavigation<T> typed = (TypeNavigation<T>) found;
        return typed;
    }

    /**
     * @param type the type to test
     * @return true when the exact type or one of its supertypes is registered
     */
    public boolean knows(Class<?> type) {
        return type != null && find(type, new HashSet<>()) != null;
    }

    private TypeNavigation<?> find(Class<?> type, Set<Class<?>> visited) {
        if (type == null || !visited.add(type)) {
            return null;
        }
        TypeNavigation<?> exact = navigations.get(type);
        if (exact != null) {
            return exact;
        }
        TypeNavigation<?> fromSuperclass = find(type.getSuperclass(), visited);
        if (fromSuperclass != null) {
            return fromSuperclass;
        }
        for (Class<?> iface : type.getInterfaces()) {
            TypeNavigation<?> fromInterface = find(iface, visited);
            if (fromInterface != null) {
                return fromInterface;
            }
        }
        return null;
    }
}
