package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Animatable;

/**
 * A single float that eases towards a target over a fixed duration, the value
 * behind a "the knob slides" or "the indicator glides" motion.
 *
 * <p>The value starts at its target, so a widget that is never animated (a
 * headless test, a screenshot) renders its resting state. {@link #animateTo}
 * starts a new run from the current value; {@link #advance(float)} moves it
 * along and {@link #isFinished()} reports when it has settled, so the frame
 * loop can drop it. The easing is a smooth-step curve: no overshoot, zero
 * velocity at both ends, which reads as a modern UI transition rather than a
 * linear slide.</p>
 *
 * <p>Package-private toolkit plumbing, not part of the frozen API.</p>
 */
final class AnimatedValue implements Animatable {

    /** Default transition length, seconds. */
    static final float DEFAULT_DURATION_SECONDS = 0.18f;

    private final float durationSeconds;

    private float from;
    private float to;
    private float value;
    /** Elapsed seconds of the current run; equal to the duration when idle. */
    private float elapsed;

    /**
     * Creates a value resting at {@code initial}.
     *
     * @param initial the resting value
     */
    AnimatedValue(float initial) {
        this(initial, DEFAULT_DURATION_SECONDS);
    }

    /**
     * Creates a value resting at {@code initial} with an explicit duration.
     *
     * @param initial         the resting value
     * @param durationSeconds the transition length, in seconds
     */
    AnimatedValue(float initial, float durationSeconds) {
        this.durationSeconds = Math.max(0.0f, durationSeconds);
        this.from = initial;
        this.to = initial;
        this.value = initial;
        this.elapsed = this.durationSeconds;
    }

    /**
     * @return the current value
     */
    float get() {
        return value;
    }

    /**
     * @return the value the current run is heading to
     */
    float target() {
        return to;
    }

    /**
     * Starts easing towards {@code target}. A no-op when already there and at
     * rest; otherwise the run restarts from the current value, so retargeting
     * mid-flight stays continuous.
     *
     * @param target the new target value
     */
    void animateTo(float target) {
        if (target == to && isFinished()) {
            return;
        }
        if (durationSeconds <= 0.0f) {
            snapTo(target);
            return;
        }
        from = value;
        to = target;
        elapsed = 0.0f;
    }

    /**
     * Snaps to {@code target} with no animation, cancelling any run in flight.
     *
     * @param target the new value
     */
    void snapTo(float target) {
        from = target;
        to = target;
        value = target;
        elapsed = durationSeconds;
    }

    /**
     * Moves the value along the current run.
     *
     * @param deltaSeconds seconds elapsed since the previous frame
     */
    @Override
    public void advance(float deltaSeconds) {
        if (isFinished() || deltaSeconds <= 0.0f) {
            return;
        }
        elapsed += deltaSeconds;
        if (elapsed >= durationSeconds) {
            value = to;
            return;
        }
        value = from + (to - from) * ease(elapsed / durationSeconds);
    }

    /**
     * @return true when the value has settled at its target
     */
    @Override
    public boolean isFinished() {
        return elapsed >= durationSeconds;
    }

    /** Smooth-step easing on {@code t} in {@code [0, 1]}. */
    private static float ease(float t) {
        return t * t * (3.0f - 2.0f * t);
    }
}
