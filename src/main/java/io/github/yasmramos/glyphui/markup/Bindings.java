package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.reactive.Trackable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global binding paths used by {@code {path}} references.
 *
 * <p>A binding maps a dotted path such as {@code "viewModel.title"} to a
 * {@link Trackable}. {@link #scoped()} creates a child view with its own map:
 * local values ({@code for-each} items, loop indices, ...) are registered
 * there and shadow the parent, while any path not found locally falls back to
 * the parent. Locals are normally constant trackables created by the caller,
 * not {@code Signal}s.</p>
 */
public final class Bindings {

    private final Map<String, Trackable<?>> values = new LinkedHashMap<>();
    private final Bindings parent;

    /**
     * Creates a root bindings map.
     */
    public Bindings() {
        this(null);
    }

    private Bindings(Bindings parent) {
        this.parent = parent;
    }

    /**
     * Registers (or replaces) a path in this scope.
     *
     * @param path  the binding path, non-blank
     * @param value the trackable value
     * @throws IllegalArgumentException when the path is blank or the value is null
     */
    public void register(String path, Trackable<?> value) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Binding path must not be blank");
        }
        if (value == null) {
            throw new IllegalArgumentException(
                    "Binding for path '" + path + "' must not be null");
        }
        values.put(path, value);
    }

    /**
     * Resolves a path in this scope, falling back to the parent scope.
     *
     * @param path the binding path
     * @return the registered trackable
     * @throws IllegalArgumentException when the path is unknown in this scope
     *                                  and all its ancestors
     */
    public Trackable<?> resolve(String path) {
        Trackable<?> value = path == null ? null : values.get(path);
        if (value != null) {
            return value;
        }
        if (parent != null) {
            return parent.resolve(path);
        }
        throw new IllegalArgumentException("Unknown binding path: '" + path + "'");
    }

    /**
     * @param path the binding path
     * @return true when the path is visible from this scope
     */
    public boolean knows(String path) {
        if (path != null && values.containsKey(path)) {
            return true;
        }
        return parent != null && parent.knows(path);
    }

    /**
     * Creates a child scope that sees this scope's bindings through a fallback
     * chain. Registering a path in the child shadows the parent value.
     *
     * @return a new child bindings view
     */
    public Bindings scoped() {
        return new Bindings(this);
    }
}
