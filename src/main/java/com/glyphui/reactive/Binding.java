package com.glyphui.reactive;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Connects a {@link Trackable} to an effect: the current value is applied
 * immediately and again after every change.
 *
 * <p>Re-applications run once the notification that triggered them has fully
 * propagated, and at most once per notification, so the effect never sees a
 * half-updated graph. The effect must not write to signals (that would be a
 * re-entrant write and throws). A binding stays active until it is closed or
 * disposed; widgets register it with {@code Component.track(...)}.</p>
 *
 * @param <T> the value type
 */
public final class Binding<T> extends AbstractObserver {

    /** What a re-application invalidates; reserved for finer-grained repainting. */
    public enum Impact { PAINT, LAYOUT }

    private final Trackable<T> source;
    private final Consumer<? super T> effect;
    private final Impact impact;
    private boolean pending;

    /**
     * Creates a binding with {@link Impact#PAINT} and applies the current value.
     *
     * @param source the observed value
     * @param effect applied with the initial value and after every change
     */
    public Binding(Trackable<T> source, Consumer<? super T> effect) {
        this(source, effect, Impact.PAINT);
    }

    /**
     * Creates a binding and applies the current value.
     *
     * @param source the observed value
     * @param effect applied with the initial value and after every change
     * @param impact what a re-application invalidates
     */
    public Binding(Trackable<T> source, Consumer<? super T> effect, Impact impact) {
        this.source = Objects.requireNonNull(source, "source");
        this.effect = Objects.requireNonNull(effect, "effect");
        this.impact = Objects.requireNonNull(impact, "impact");
        observe(source);
        try {
            apply();
        } catch (RuntimeException e) {
            dispose(); // do not leave a half-built binding subscribed
            throw e;
        }
    }

    /** @return what a re-application invalidates */
    public Impact impact() {
        return impact;
    }

    @Override
    protected void onInvalidate() {
        if (pending) {
            return;
        }
        pending = true;
        Reactor.enqueueEffect(this::flush);
    }

    private void flush() {
        pending = false;
        if (!isDisposed()) {
            apply();
        }
    }

    private void apply() {
        Reactor.runUntracked(() -> effect.accept(source.get()));
    }
}
