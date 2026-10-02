package com.glyphui.reactive;

import java.util.ArrayDeque;
import java.util.function.Supplier;

/**
 * Central coordination point of the reactive core.
 *
 * <h2>Dependency discovery</h2>
 * <p>While a {@code Computed} evaluates, it is installed as the
 * {@linkplain #current() current observer} of the calling thread. Every
 * {@link Trackable#get()} reports itself here and the current observer
 * subscribes to it, so dependencies are discovered dynamically and may change
 * from one evaluation to the next. No reflection is involved.</p>
 *
 * <h2>Threading</h2>
 * <p>Reactive state may only be written on the UI thread. The UI thread is
 * the thread that created the signal, unless {@link #setUiThread(Thread)}
 * installs an explicit one (the application does so when its loop starts).</p>
 *
 * <h2>Notifications and effects</h2>
 * <p>A write fans out invalidations to observers. Effects (bindings) are not
 * run during the fan-out: they are queued and run once it has finished, so
 * they always see a consistent graph (no intermediate "glitch" values in
 * diamond-shaped dependencies). Writing to any signal while a notification or
 * an effect is running is a programming error and throws
 * {@link IllegalStateException}.</p>
 */
public final class Reactor {

    private static final ThreadLocal<Observer> CURRENT = new ThreadLocal<>();
    private static final ArrayDeque<Runnable> EFFECTS = new ArrayDeque<>();
    private static volatile Thread uiThread;
    private static boolean notifying;

    private Reactor() {
    }

    /**
     * Gets the observer collecting dependencies on the calling thread.
     *
     * @return the current observer, or null outside any tracked evaluation
     */
    public static Observer current() {
        return CURRENT.get();
    }

    /**
     * Declares the UI thread. Pass null to go back to "the thread that
     * created each signal".
     *
     * @param thread the UI thread, or null
     */
    public static void setUiThread(Thread thread) {
        uiThread = thread;
    }

    /**
     * Gets the explicitly declared UI thread.
     *
     * @return the UI thread, or null when none was declared
     */
    public static Thread getUiThread() {
        return uiThread;
    }

    /**
     * Tells whether a change notification (or a queued effect) is running.
     *
     * @return true while notifying
     */
    public static boolean isNotifying() {
        return notifying;
    }

    /**
     * Evaluates {@code body} without registering dependencies.
     *
     * @param body the code to run
     * @param <T>  the result type
     * @return the result of {@code body}
     */
    public static <T> T untracked(Supplier<? extends T> body) {
        Observer previous = swapCurrent(null);
        try {
            return body.get();
        } finally {
            swapCurrent(previous);
        }
    }

    /**
     * Runs {@code body} without registering dependencies.
     *
     * @param body the code to run
     */
    public static void runUntracked(Runnable body) {
        untracked(() -> {
            body.run();
            return null;
        });
    }

    // ------------------------------------------------------------------
    // Package-private plumbing
    // ------------------------------------------------------------------

    static Observer swapCurrent(Observer next) {
        Observer previous = CURRENT.get();
        if (next == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(next);
        }
        return previous;
    }

    static void reportRead(Trackable<?> source) {
        Observer observer = CURRENT.get();
        if (observer instanceof AbstractObserver tracking) {
            tracking.observe(source);
        }
    }

    static void checkUiThread(Thread creator, String action) {
        Thread expected = uiThread != null ? uiThread : creator;
        Thread actual = Thread.currentThread();
        if (expected != actual) {
            throw new IllegalStateException(action + " must be called on the UI thread \""
                    + expected.getName() + "\" but was called on \"" + actual.getName() + "\"");
        }
    }

    static void checkWritable(String action) {
        if (notifying) {
            throw new IllegalStateException(action
                    + " is not allowed while a change notification or effect is running"
                    + " (re-entrant write)");
        }
        if (CURRENT.get() != null) {
            throw new IllegalStateException(action
                    + " is not allowed while a Computed is being evaluated");
        }
    }

    /**
     * Runs {@code fanOut} as a notification, then the effects it queued.
     * Failures never leave the reactor stuck in the notifying state.
     */
    static void notifyChange(Runnable fanOut) {
        if (notifying) {
            throw new IllegalStateException("Nested change notification");
        }
        notifying = true;
        RuntimeException failure = null;
        Observer outer = swapCurrent(null);
        try {
            try {
                fanOut.run();
            } catch (RuntimeException e) {
                failure = e;
            }
            Runnable effect;
            while ((effect = EFFECTS.poll()) != null) {
                try {
                    effect.run();
                } catch (RuntimeException e) {
                    failure = merge(failure, e);
                }
            }
        } finally {
            swapCurrent(outer);
            EFFECTS.clear();
            notifying = false;
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Queues an effect for the end of the running notification (or runs it now). */
    static void enqueueEffect(Runnable effect) {
        if (notifying) {
            EFFECTS.add(effect);
        } else {
            notifyChange(() -> EFFECTS.add(effect));
        }
    }

    static RuntimeException merge(RuntimeException first, RuntimeException next) {
        if (first == null) {
            return next;
        }
        if (first != next) {
            first.addSuppressed(next);
        }
        return first;
    }

    /** Restores the pristine state; intended for tests. */
    static void reset() {
        uiThread = null;
        notifying = false;
        EFFECTS.clear();
        CURRENT.remove();
    }
}
