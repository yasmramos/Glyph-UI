package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Animatable;

/**
 * An ARGB colour that eases towards a target, the value behind a control's
 * hover and press fade.
 *
 * <p>It rests at its target, so a widget that is never animated (a headless
 * test, a screenshot) paints its resting colour. {@link #animateTo(int)} starts
 * a run from the current colour; {@link #advance(float)} moves it along and
 * {@link #isFinished()} reports when it has settled, so the frame loop can drop
 * it. The interpolation reuses {@link ColorBlend}, so a settled transition
 * lands exactly on the same colour an instantaneous swap would produce.</p>
 *
 * <p>Package-private toolkit plumbing, not part of the frozen API.</p>
 */
final class AnimatedColor implements Animatable {

    /** Progress of the current run, eased in {@code [0, 1]}. */
    private final AnimatedValue progress = new AnimatedValue(1.0f);

    private int from;
    private int to;
    private int value;
    /** False until the first {@link #sync} seeds the colour from the live state. */
    private boolean initialized;

    /**
     * Creates a colour resting at {@code initial}.
     *
     * @param initial the resting ARGB colour
     */
    AnimatedColor(int initial) {
        this.from = initial;
        this.to = initial;
        this.value = initial;
    }

    /**
     * @return the current ARGB colour
     */
    int get() {
        return value;
    }

    /**
     * @return the colour the current run is heading to
     */
    int target() {
        return to;
    }

    /**
     * Returns the colour to paint for {@code target}, easing from the previous
     * value. The first call seeds the colour with {@code target} without
     * animating, so a widget that is never animated — or is painted for the
     * first time — rests exactly at its target and never fades in on
     * appearance.
     *
     * <p>Afterwards a change of target starts a transition and registers
     * {@code driver} with the application that owns {@code owner}. When no
     * application is live (a headless test, a screenshot) nothing drives the
     * transition, so the value stays put until a caller calls
     * {@code advance(...)} — never snap it here, or {@code isFinished()} would
     * lie to the frame loop.</p>
     *
     * @param owner  the widget the animation belongs to (resolves the app)
     * @param driver the animatable the frame loop advances
     * @param target the ARGB colour the widget's current state wants
     * @return the ARGB colour to paint this frame
     */
    int sync(Component owner, Animatable driver, int target) {
        if (!initialized) {
            initialized = true;
            snapTo(target);
            return target;
        }
        if (target != to) {
            animateTo(target);
            AnimationSupport.start(owner, driver);
            return value;
        }
        return value;
    }

    /**
     * Starts easing towards {@code target}. A no-op when already there and at
     * rest; otherwise the run restarts from the current colour, so retargeting
     * mid-flight stays continuous.
     *
     * @param target the new ARGB colour
     */
    void animateTo(int target) {
        if (target == to && progress.isFinished()) {
            return;
        }
        from = value;
        to = target;
        progress.snapTo(0.0f);
        progress.animateTo(1.0f);
    }

    /**
     * Snaps to {@code target} with no animation, cancelling any run in flight.
     *
     * @param target the new ARGB colour
     */
    void snapTo(int target) {
        from = target;
        to = target;
        value = target;
        progress.snapTo(1.0f);
    }

    /**
     * Moves the colour along the current run.
     *
     * @param deltaSeconds seconds elapsed since the previous frame
     */
    @Override
    public void advance(float deltaSeconds) {
        if (isFinished() || deltaSeconds <= 0.0f) {
            return;
        }
        progress.advance(deltaSeconds);
        value = ColorBlend.lerp(from, to, progress.get());
    }

    /**
     * @return true when the colour has settled at its target
     */
    @Override
    public boolean isFinished() {
        return progress.isFinished();
    }
}
