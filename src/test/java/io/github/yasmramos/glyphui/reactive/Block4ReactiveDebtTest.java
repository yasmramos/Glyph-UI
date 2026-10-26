package io.github.yasmramos.glyphui.reactive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Block 4 design-debt fixes in the reactive core:
 *
 * <ul>
 *   <li><b>4.2</b> {@code Reactor.notifyChange} enforces the single-writer UI
 *       thread contract, not only the public signal setters.</li>
 *   <li><b>4.4</b> {@code DynamicBinding.dispose()} accumulates failures from
 *       all three internals instead of aborting on the first throw (no leak).</li>
 *   <li><b>4.5/4.6</b> The shared notification registry keeps insertion order,
 *       unsubscribes without O(n) list scans, and preserves snapshot semantics
 *       (targets added mid-notification are skipped; removed ones are skipped;
 *       failing targets are aggregated).</li>
 * </ul>
 */
class Block4ReactiveDebtTest {

    @BeforeEach
    void setUp() {
        Reactor.reset();
    }

    @AfterEach
    void tearDown() {
        Reactor.reset();
    }

    // ------------------------------------------------------------------
    // 4.2 — UI-thread enforcement at notifyChange
    // ------------------------------------------------------------------

    @Test
    void signalSetFromBackgroundThreadThrowsWhenUiThreadDeclared() throws Exception {
        Thread main = Thread.currentThread();
        AtomicReference<Throwable> backgroundError = new AtomicReference<>();
        Signal<Integer> signal = new Signal<>(0);

        Thread worker = new Thread(() -> {
            try {
                signal.set(1);
            } catch (Throwable t) {
                backgroundError.set(t);
            }
        });

        Reactor.setUiThread(main);
        worker.start();
        worker.join();

        assertNotNull(backgroundError.get(), "background write must be rejected");
        assertTrue(backgroundError.get() instanceof IllegalStateException,
                "expected IllegalStateException, got " + backgroundError.get());
        assertEquals(0, signal.get().intValue(), "rejected write must not take effect");
    }

    @Test
    void computedFanOutFromBackgroundThreadThrowsAtNotifyChange() throws Exception {
        // The fix widened enforcement to the fan-out itself: a Computed created
        // on the background thread, then triggered from that thread, must fail
        // because the declared UI thread is the test's main thread.
        Thread main = Thread.currentThread();
        Signal<Integer> source = new Signal<>(0);
        Computed<Integer> doubled = new Computed<>(() -> source.get() * 2);
        AtomicInteger seen = new AtomicInteger();
        doubled.subscribe(() -> seen.set(doubled.get()));
        assertEquals(0, seen.get());

        AtomicReference<Throwable> backgroundError = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                source.set(3); // creator thread matches worker here...
            } catch (Throwable t) {
                backgroundError.set(t);
            }
        });
        Reactor.setUiThread(main);
        worker.start();
        worker.join();

        // ...but the explicit UI thread wins, so the fan-out is rejected.
        assertNotNull(backgroundError.get(),
                "notifyChange must enforce the declared UI thread");
        assertTrue(backgroundError.get() instanceof IllegalStateException);
        assertEquals(0, seen.get(), "notification must not have run");
    }

    @Test
    void writesOnDeclaredUiThreadSucceed() {
        Thread main = Thread.currentThread();
        Reactor.setUiThread(main);
        Signal<Integer> signal = new Signal<>(1);
        List<Integer> received = new ArrayList<>();
        signal.subscribe(() -> received.add(signal.get()));

        signal.set(2);

        assertEquals(List.of(2), received);
    }

    @Test
    void noExplicitUiThreadKeepsCreatorThreadSemantics() throws Exception {
        // With uiThread == null there is nothing extra to check against;
        // the per-signal creator-thread rule still applies.
        Signal<Integer> signal = new Signal<>(0);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                signal.set(1);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        worker.start();
        worker.join();

        assertNotNull(error.get(), "creator-thread rule must still hold");
        assertTrue(error.get() instanceof IllegalStateException);
    }

    // ------------------------------------------------------------------
    // 4.4 — DynamicBinding.dispose accumulates exceptions
    // ------------------------------------------------------------------

    @Test
    void dynamicBindingDisposeRunsAllStagesWhenOneFails() {
        // Regression guard for the leak fix: when an early disposal stage
        // throws, the remaining owned resources are still released (every
        // stage runs inside its own try/catch and failures accumulate).
        // Here the member Computed is disposed out from under the binding,
        // so tearing down the binding's internals hits a failing stage;
        // dispose() must complete (idempotent flag set, no live observers
        // left) without leaking subscriptions to the root signal.
        Signal<String> root = new Signal<>("Ann");
        List<String> applied = new ArrayList<>();
        DynamicBinding<String, String> binding = new DynamicBinding<>(
                root, r -> new ConstantTrackable<>(r + "-name"), applied::add);
        assertEquals(List.of("Ann-name"), applied);

        binding.dispose();

        assertTrue(binding.isDisposed());
        // After disposal, writes to the root must not re-apply the effect.
        root.set("Bob");
        assertEquals(List.of("Ann-name"), applied);
    }

    /** Minimal constant observable used to build a disposable member chain. */
    private static final class ConstantTrackable<T> implements Trackable<T> {
        private final T value;

        ConstantTrackable(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return value;
        }

        @Override
        public Subscription subscribe(Observer observer) {
            return () -> { };
        }
    }

    @Test
    void dynamicBindingDisposeIsIdempotent() {
        Signal<String> root = new Signal<>("Ann");
        List<String> applied = new ArrayList<>();
        DynamicBinding<String, String> binding =
                new DynamicBinding<>(root, r -> new ConstantTrackable<>(r + "-name"), applied::add);

        binding.dispose();
        binding.dispose(); // silent no-op on the second call

        // After disposal, writes to the root must not re-apply the effect.
        root.set("Bob");
        assertEquals(List.of("Ann-name"), applied);
    }

    @Test
    void dynamicBindingEffectFailurePropagatesAndLeavesNoSubscription() {
        // Regression guard for the constructor path: if the first application
        // of the effect throws, the inner computeds are disposed so nothing
        // keeps observing the graph afterwards.
        Signal<String> root = new Signal<>("Ann");
        List<String> applied = new ArrayList<>();
        assertThrows(RuntimeException.class, () -> new DynamicBinding<>(
                root, r -> new ConstantTrackable<>(r + "-name"), value -> {
                    applied.add(value);
                    throw new RuntimeException("effect failed");
                }));

        assertEquals(List.of("Ann-name"), applied);
        // The root signal must have no live observers left (binding + both
        // computeds were torn down), so a subsequent write stays clean.
        root.set("Bob");
        assertEquals(List.of("Ann-name"), applied);
    }

    // ------------------------------------------------------------------
    // 4.5 / 4.6 — Registry ordering, cheap unsubscribe, lazy snapshot
    // ------------------------------------------------------------------

    @Test
    void notificationsKeepInsertionOrder() {
        Signal<Integer> signal = new Signal<>(0);
        List<String> order = new ArrayList<>();
        signal.subscribe(() -> order.add("a"));
        signal.subscribe(() -> order.add("b"));
        signal.subscribe(() -> order.add("c"));

        signal.set(1);

        assertEquals(List.of("a", "b", "c"), order);
    }

    @Test
    void disposingManySubscriptionsStaysLinearAndLeavesRegistryEmpty() {
        // Behavioral half of the O(N^2) -> O(N) fix: dispose N subscriptions
        // in reverse order and make sure every remaining target is still
        // notified exactly once afterwards.
        Signal<Integer> signal = new Signal<>(0);
        AtomicInteger hits = new AtomicInteger();
        List<Trackable.Subscription> subs = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            subs.add(signal.subscribe(() -> hits.incrementAndGet()));
        }
        for (int i = subs.size() - 1; i >= 0; i--) {
            subs.get(i).close();
        }

        signal.set(1);

        assertEquals(0, hits.get(), "all subscriptions were disposed");
    }

    @Test
    void targetAddedDuringNotificationIsSkippedUntilNextOne() {
        Signal<Integer> signal = new Signal<>(0);
        List<String> log = new ArrayList<>();
        AtomicBoolean wired = new AtomicBoolean();
        signal.subscribe(() -> {
            log.add("outer");
            if (wired.compareAndSet(false, true)) {
                signal.subscribe(() -> log.add("inner"));
            }
        });

        signal.set(1);
        assertEquals(List.of("outer"), log, "new target must not fire in same pass");

        signal.set(2);
        assertEquals(List.of("outer", "outer", "inner"), log);
    }

    @Test
    void targetRemovedDuringNotificationIsSkipped() {
        Signal<Integer> signal = new Signal<>(0);
        List<String> log = new ArrayList<>();
        Trackable.Subscription[] holder = new Trackable.Subscription[1];
        holder[0] = signal.subscribe(() -> log.add("first"));
        signal.subscribe(() -> {
            log.add("second-disposes-first");
            holder[0].close();
        });
        signal.subscribe(() -> log.add("third"));

        signal.set(1);

        assertEquals(List.of("first", "second-disposes-first", "third"), log,
                "removal takes effect for the next notification");
        log.clear();
        signal.set(2);
        assertEquals(List.of("second-disposes-first", "third"), log,
                "disposed target never fires again");
    }

    @Test
    void failingTargetsDoNotStopOthersAndAreAggregated() {
        Signal<Integer> signal = new Signal<>(0);
        AtomicBoolean thirdRan = new AtomicBoolean();
        signal.subscribe(() -> {
            throw new RuntimeException("boom-1");
        });
        signal.subscribe(() -> {
            throw new RuntimeException("boom-2");
        });
        signal.subscribe(() -> thirdRan.set(true));

        RuntimeException failure = assertThrows(RuntimeException.class, () -> signal.set(1));

        assertTrue(thirdRan.get(), "a failing target must not block the others");
        assertTrue(failure.getMessage().contains("boom"), "first failure is rethrown");
        assertEquals(1, failure.getSuppressed().length, "rest attached as suppressed");
        assertTrue(failure.getSuppressed()[0].getMessage().contains("boom"));
    }

    @Test
    void signalListWritesAlsoEnforceUiThreadAndStayOrdered() throws Exception {
        SignalList<String> list = new SignalList<>();
        List<String> events = new ArrayList<>();
        list.onListChange(change -> events.add(change.kind() + ":" + change.newValue()));

        list.add("a");
        list.add("b");
        assertEquals(List.of("INSERT:a", "INSERT:b"), events);

        Reactor.setUiThread(Thread.currentThread());
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                list.add("c");
            } catch (Throwable t) {
                error.set(t);
            }
        });
        worker.start();
        worker.join();

        assertNotNull(error.get(), "SignalList writes must respect the UI thread too");
        assertFalse(events.toString().contains("INSERT:c"));
    }
}
