package com.glyphui.reactive;

/**
 * Receives invalidation notices from a {@link Trackable}.
 *
 * <p>The contract is "something may have changed": an observer that wants the
 * new value must read it again (usually lazily, see {@code Computed}) instead
 * of being handed the value.</p>
 */
@FunctionalInterface
public interface Observer {

    /** Called when a source this observer depends on has changed. */
    void invalidate();
}
