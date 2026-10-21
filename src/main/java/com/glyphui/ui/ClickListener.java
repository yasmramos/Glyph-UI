package com.glyphui.ui;

/**
 * Functional callback invoked when a widget is activated by a click (or an
 * equivalent keyboard gesture such as Enter/Space on a focused button).
 *
 * <p>This is the canonical handler type for {@link Button} clicks. It is a
 * {@code @FunctionalInterface} so lambdas and method references work exactly
 * as they did with the historical {@link Runnable}-based API:</p>
 *
 * <pre>{@code
 * button.setOnClick(() -> System.out.println("clicked"));
 * }</pre>
 *
 * <h2>Design decision: ClickListener instead of Runnable</h2>
 * <p>{@code Runnable} described no contract at all — any {@code Runnable}
 * could be passed anywhere, and the name gave no hint that the callback runs
 * on the UI thread during event dispatch. {@code ClickListener} names the
 * role explicitly, keeps lambda ergonomics (single abstract method, no
 * parameters) and gives the toolkit a typed seam to document and later extend
 * (e.g. richer click events) without breaking unrelated {@code Runnable}
 * usages.</p>
 *
 * <h2>Threading and exception safety</h2>
 * <ul>
 *   <li>Handlers always run on the UI thread, inside the backend event
 *       dispatch ({@code Application.run()}); widget mutations need no
 *       synchronization.</li>
 *   <li>The widget catches any exception thrown from {@link #onClick()}, so a
 *       failing handler can never leave internal state (for example a stuck
 *       pressed visual state) inconsistent. Exceptions are reported through
 *       {@link java.util.logging.Logger} rather than swallowed silently.</li>
 * </ul>
 */
@FunctionalInterface
public interface ClickListener {

    /**
     * Invoked when the widget is clicked/activated. Implementations should
     * keep the work short; long operations belong on a background thread
     * with results marshalled back via
     * {@link com.glyphui.core.Application#invokeLater(Runnable)}.
     */
    void onClick();
}
