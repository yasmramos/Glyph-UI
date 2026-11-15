package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Animatable;
import io.github.yasmramos.glyphui.core.AppContext;
import io.github.yasmramos.glyphui.core.Application;

/**
 * Bridges a widget's own animation to the application's frame loop.
 *
 * <p>A widget that animates itself (a sliding knob, a tab indicator) does not
 * hold an {@link Application} reference: it resolves the owning application
 * through its component tree at the moment it needs to start animating, then
 * registers itself as an {@link Animatable}. Detached widgets — headless tests,
 * components built before any application exists — simply get no application
 * and skip registration, staying deterministic.</p>
 *
 * <p>This is package-private toolkit plumbing, not part of the frozen API.</p>
 */
final class AnimationSupport {

    private AnimationSupport() {
    }

    /**
     * Registers {@code owner} with the application that owns it, so the frame
     * loop drives {@code owner.advance(...)} until it reports itself finished.
     * A no-op when the widget is detached or no application is live (headless
     * tests, a component built before any window exists), which keeps such a
     * widget deterministic: it simply never advances until a caller drives it.
     *
     * @param owner the component that owns the animation
     * @param animatable the animation to drive
     */
    static void start(Component owner, Animatable animatable) {
        if (owner == null || animatable == null) {
            return;
        }
        AppContext ctx = owner.context();
        if (ctx == null) {
            ctx = AppContext.ofCurrent();
        }
        Application app = ctx != null ? ctx.app() : null;
        if (app != null) {
            app.addAnimatable(animatable);
        }
    }
}
