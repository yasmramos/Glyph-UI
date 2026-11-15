package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Animatable;

/**
 * The eased rectangle behind a "selection glides between cells" motion, the
 * left edge and width a header underline or a segmented block travels with.
 *
 * <p>It rests at its target, so a widget that is never animated (a headless
 * test, a screenshot) paints the indicator in place. The first {@link #glideTo}
 * has no previous position to travel from, so it appears in place; every later
 * target starts a run and registers the owning widget with its application
 * through {@link AnimationSupport}. When no application is live nothing drives
 * the run, so the value stays put until a caller calls {@link #advance(float)} —
 * it never snaps, or {@code isFinished()} would lie to the frame loop.</p>
 *
 * <p>Package-private toolkit plumbing, not part of the frozen API.</p>
 */
final class SlidingIndicator implements Animatable {

    private final AnimatedValue left = new AnimatedValue(0.0f);
    private final AnimatedValue width = new AnimatedValue(0.0f);
    /** False until the first target places the indicator. */
    private boolean placed;

    /**
     * Eases the indicator towards {@code targetLeft} / {@code targetWidth}. The
     * first call places it without animating; a call that repeats the current
     * target is a no-op, so a widget may call this every frame while painting.
     *
     * @param owner        the widget the animation belongs to (resolves the app)
     * @param targetLeft   the target left edge
     * @param targetWidth  the target width
     */
    void glideTo(Component owner, float targetLeft, float targetWidth) {
        if (placed && targetLeft == left.target() && targetWidth == width.target()) {
            return;
        }
        if (!placed) {
            left.snapTo(targetLeft);
            width.snapTo(targetWidth);
            placed = true;
            return;
        }
        left.animateTo(targetLeft);
        width.animateTo(targetWidth);
        AnimationSupport.start(owner, this);
    }

    /**
     * @return the current left edge
     */
    float left() {
        return left.get();
    }

    /**
     * @return the current width
     */
    float width() {
        return width.get();
    }

    /**
     * Moves the glide along by one frame. {@link Animatable} implementation: the
     * application drives it while the transition runs.
     *
     * @param deltaSeconds seconds elapsed since the previous frame
     */
    @Override
    public void advance(float deltaSeconds) {
        left.advance(deltaSeconds);
        width.advance(deltaSeconds);
    }

    /**
     * @return true when the indicator has settled at its target
     */
    @Override
    public boolean isFinished() {
        return left.isFinished() && width.isFinished();
    }
}
