package io.github.yasmramos.glyphui.markup;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Declared members of one model type.
 *
 * <p>Members are plain {@code Function<T, Object>} accessors (normally
 * method references or lambdas such as {@code task -> task.title}); looking a
 * member up never uses reflection. Instances are created through
 * {@link TypeNavigations#register(Class)}:</p>
 *
 * <pre>{@code
 * TypeNavigations nav = new TypeNavigations();
 * nav.register(Task.class)
 *    .member("id", task -> task.id)
 *    .member("title", task -> task.title)
 *    .done();
 * }</pre>
 *
 * @param <T> the model type
 */
public final class TypeNavigation<T> {

    private final Class<T> type;
    private final TypeNavigations owner;
    private final Map<String, Function<T, Object>> members = new LinkedHashMap<>();

    TypeNavigation(Class<T> type, TypeNavigations owner) {
        this.type = type;
        this.owner = owner;
    }

    /**
     * Declares (or replaces) one member accessor.
     *
     * @param name     the member name used in binding paths
     * @param accessor the lambda that reads the member
     * @return this navigation, for chaining
     * @throws IllegalArgumentException when the name is blank or the accessor is null
     */
    public TypeNavigation<T> member(String name, Function<T, Object> accessor) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException(
                    "Member name for " + type.getName() + " must not be blank");
        }
        if (accessor == null) {
            throw new IllegalArgumentException(
                    "Member accessor for '" + name + "' on " + type.getName()
                            + " must not be null");
        }
        members.put(name, accessor);
        return this;
    }

    /**
     * Ends the fluent declaration.
     *
     * @return the owning registry, for chaining another type
     */
    public TypeNavigations done() {
        return owner;
    }

    /**
     * Reads one member from an instance.
     *
     * @param instance the model instance
     * @param member   the declared member name
     * @return the member value (may be null)
     * @throws IllegalArgumentException when the member is unknown or the
     *                                  instance is null
     */
    public Object get(T instance, String member) {
        Function<T, Object> accessor = member == null ? null : members.get(member);
        if (accessor == null) {
            throw new IllegalArgumentException(
                    "Type " + type.getName() + " has no member '" + member
                            + "'. Declared members: " + members.keySet());
        }
        if (instance == null) {
            throw new IllegalArgumentException(
                    "Cannot read member '" + member + "' from a null "
                            + type.getName() + " instance");
        }
        return accessor.apply(instance);
    }

    /**
     * @param member the member name
     * @return true when the member was declared on this type
     */
    public boolean hasMember(String member) {
        return member != null && members.containsKey(member);
    }

    /**
     * @return the model type
     */
    public Class<T> getType() {
        return type;
    }

    /**
     * @return the declared member names, in declaration order
     */
    public Set<String> members() {
        return Collections.unmodifiableSet(members.keySet());
    }
}
