package io.github.yasmramos.glyphui.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.DoubleSupplier;

/**
 * Owns the animation registration and per-frame timing of one {@link
 * Application} instance.
 *
 * <p>Extracted so the frame loop and the widgets talk to a small, dedicated
 * collaborator instead of the application computing deltas inline. It keeps a
 * monotonic clock, computes the seconds elapsed since the previous frame, and
 * feeds every registered {@link Animatable} that delta.</p>
 *
 * <p>Two properties matter for smooth motion and testability:</p>
 *
 * <ul>
 *   <li><b>The delta is clamped.</b> A frame that arrives after a stall (a
 *       debugger pause, a window returning from the background, a long GC)
 *       would otherwise advance an animation by whole seconds and make it jump.
 *       The clamp keeps every step short, so a slow frame is a slow animation,
 *       never a teleport.</li>
 *   <li><b>The first frame has no delta.</b> The clock is seeded on the first
 *       {@link #advance()} call and reports zero, so an animation registered
 *       while the app is idle does not consume an arbitrary amount of time on
 *       its very first tick.</li>
 * </ul>
 *
 * <p>Iteration order is registration order ({@link LinkedHashSet}), so a
 * deterministic sequence is preserved for tests and for anything that depends
 * on relative update order. All mutation happens on the UI thread.</p>
 */
final class AnimationClock {

    /** Longest single step fed to an animatable, seconds (about 4 frames at 60Hz). */
    static final float MAX_STEP_SECONDS = 0.064f;

    private final Set<Animatable> animatables = new LinkedHashSet<>();
    private final DoubleSupplier clock;
    private double lastTime;
    private boolean started;

    /** Creates a clock reading the real monotonic system time. */
    AnimationClock() {
        this(AnimationClock::nowSeconds);
    }

    /**
     * Creates a clock reading time from {@code clock}. Package-private seam so
     * tests can drive frames deterministically instead of sleeping.
     *
     * @param clock seconds from an arbitrary fixed origin, monotonically rising
     */
    AnimationClock(DoubleSupplier clock) {
        this.clock = clock;
    }

    /**
     * Registers an animatable. While at least one is registered the frame loop
     * keeps repainting; registering the first one must schedule a repaint so
     * the motion starts without waiting for unrelated input.
     *
     * @param animatable the animatable to register (null is ignored)
     * @return true when this call added a new animatable (i.e. the set grew)
     */
    boolean add(Animatable animatable) {
        if (animatable == null) {
            return false;
        }
        return animatables.add(animatable);
    }

    /**
     * Unregisters an animatable.
     *
     * @param animatable the animatable to remove (null is ignored)
     * @return true when the animatable was registered
     */
    boolean remove(Animatable animatable) {
        if (animatable == null) {
            return false;
        }
        boolean removed = animatables.remove(animatable);
        if (removed && animatables.isEmpty()) {
            // Forget the clock origin so a later re-registration does not see
            // the whole idle gap as one frame.
            reset();
        }
        return removed;
    }

    /**
     * @return true while at least one animatable is registered, i.e. the loop
     *         must keep painting every frame
     */
    boolean isAnimating() {
        return !animatables.isEmpty();
    }

    /**
     * Advances every registered animatable by the elapsed seconds of one
     * frame, clamped to {@link #MAX_STEP_SECONDS}. The first call after
     * registration only seeds the clock and feeds a zero delta. An animatable
     * that reports {@link Animatable#isFinished()} is dropped once advanced, so
     * a one-shot animation stops the loop by itself.
     */
    void advance() {
        double now = clock.getAsDouble();
        if (!started) {
            started = true;
            lastTime = now;
            return;
        }
        float delta = (float) (now - lastTime);
        lastTime = now;
        if (delta <= 0.0f) {
            return;
        }
        if (delta > MAX_STEP_SECONDS) {
            delta = MAX_STEP_SECONDS;
        }
        for (Animatable animatable : animatables) {
            animatable.advance(delta);
        }
        if (animatables.removeIf(Animatable::isFinished) && animatables.isEmpty()) {
            reset();
        }
    }

    /**
     * Forgets the clock origin so the next {@link #advance()} seeds again and
     * feeds a zero delta. Called when the last animatable leaves so a later
     * re-registration does not see the whole idle gap as one frame.
     */
    void reset() {
        started = false;
    }

    /**
     * @return how many animatables are registered (mainly for tests)
     */
    int size() {
        return animatables.size();
    }

    private static double nowSeconds() {
        return System.nanoTime() / 1_000_000_000.0;
    }
}
