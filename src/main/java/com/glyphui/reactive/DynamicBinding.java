package com.glyphui.reactive;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A one-level path {@code root -> member}: binds an effect to a member of an
 * observable root object, and follows the root when it changes.
 *
 * <p>When the root changes, the subscription to the old member is dropped and
 * the new member is subscribed, so the effect always reflects the member of the
 * <em>current</em> root. The member is selected by a user-supplied function;
 * there is no navigation by name (no reflection). If the function returns
 * null, the effect receives null.</p>
 *
 * <p>The internal {@code Computed} values and the {@code Binding} are owned by
 * this object and released by {@link #dispose()}.</p>
 *
 * @param <R> the root type
 * @param <T> the member value type
 */
public final class DynamicBinding<R, T> implements Disposable {

    private final Computed<Trackable<T>> member;
    private final Computed<T> value;
    private final Binding<T> binding;
    private boolean disposed;

    /**
     * Creates the binding and applies the current value.
     *
     * @param root     the observable root
     * @param memberOf selects the observable member of a root value
     * @param effect   applied with the initial value and after every change
     */
    public DynamicBinding(Trackable<R> root,
                          Function<? super R, ? extends Trackable<T>> memberOf,
                          Consumer<? super T> effect) {
        this(root, memberOf, effect, Binding.Impact.PAINT);
    }

    /**
     * Creates the binding and applies the current value.
     *
     * @param root     the observable root
     * @param memberOf selects the observable member of a root value
     * @param effect   applied with the initial value and after every change
     * @param impact   what a re-application invalidates
     */
    public DynamicBinding(Trackable<R> root,
                          Function<? super R, ? extends Trackable<T>> memberOf,
                          Consumer<? super T> effect,
                          Binding.Impact impact) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(memberOf, "memberOf");
        Computed<Trackable<T>> memberComputed = new Computed<>(() -> memberOf.apply(root.get()));
        Computed<T> valueComputed = new Computed<>(() -> {
            Trackable<T> current = memberComputed.get();
            return current == null ? null : current.get();
        });
        Binding<T> created;
        try {
            created = new Binding<>(valueComputed, effect, impact);
        } catch (RuntimeException e) {
            valueComputed.dispose();
            memberComputed.dispose();
            throw e;
        }
        this.member = memberComputed;
        this.value = valueComputed;
        this.binding = created;
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        binding.dispose();
        value.dispose();
        member.dispose();
    }

    @Override
    public boolean isDisposed() {
        return disposed;
    }
}
