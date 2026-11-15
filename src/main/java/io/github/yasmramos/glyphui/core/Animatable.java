package io.github.yasmramos.glyphui.core;

/**
 * A widget or helper that changes a little on every frame — a spinner sweep, a
 * toast countdown, a tooltip appear-delay.
 *
 * <p>This is the toolkit's single animation seam. Instead of each animatable
 * widget inventing its own clock and the application wiring a lambda that
 * computes the elapsed time by hand, an animatable is registered once with
 * {@link Application#addAnimatable(Animatable)} and the frame loop calls
 * {@link #advance(float)} with the elapsed seconds of every frame. While at
 * least one animatable is registered the loop keeps repainting, so the motion
 * stays smooth; unregistering the last one returns the application to
 * on-demand rendering.</p>
 *
 * <p>The delta is clamped by the application before it reaches the animatable,
 * so a frame that arrives late (a stall, a debugger pause, the window coming
 * back from the background) advances the animation by at most one short step
 * instead of jumping to its end. Implementations must be no-ops for a
 * non-positive delta and must not assume the delta is exactly one frame.</p>
 *
 * @see Application#addAnimatable(Animatable)
 * @see Application#removeAnimatable(Animatable)
 */
@FunctionalInterface
public interface Animatable {

    /**
     * Advances the animation by the elapsed time of one frame.
     *
     * @param deltaSeconds seconds since the previous frame, never negative and
     *                     never larger than the application's frame-step clamp
     */
    void advance(float deltaSeconds);

    /**
     * Whether the animation has nothing left to do, so the application can
     * drop it and return to on-demand rendering. Most animations run until the
     * caller unregisters them and keep the default {@code false}; a one-shot
     * animation (a fade that has reached its resting state, a delay that has
     * elapsed) overrides this to stop the loop once it is done.
     *
     * @return true when the application may unregister this animatable
     */
    default boolean isFinished() {
        return false;
    }
}
