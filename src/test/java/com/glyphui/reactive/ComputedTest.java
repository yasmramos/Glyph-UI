package com.glyphui.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ComputedTest {

    @BeforeEach
    public void setUp() {
        Reactor.reset();
    }

    @AfterEach
    public void tearDown() {
        Reactor.reset();
    }

    @Test
    public void isLazyAndCaches() {
        Signal<Integer> source = new Signal<>(1);
        int[] runs = {0};
        Computed<Integer> doubled = new Computed<>(() -> {
            runs[0]++;
            return source.get() * 2;
        });

        assertEquals(0, runs[0], "nothing runs before the first get()");

        assertEquals(2, doubled.get());
        assertEquals(2, doubled.get());
        assertEquals(1, runs[0], "a clean value is served from the cache");

        source.set(5);
        assertEquals(1, runs[0], "a change only marks dirty, it does not recompute");

        assertEquals(10, doubled.get());
        assertEquals(2, runs[0]);
    }

    @Test
    public void manyChangesRecomputeOnlyOnce() {
        Signal<Integer> source = new Signal<>(0);
        int[] runs = {0};
        Computed<Integer> value = new Computed<>(() -> {
            runs[0]++;
            return source.get();
        });
        value.get();

        source.set(1);
        source.set(2);
        source.set(3);

        assertEquals(3, value.get());
        assertEquals(2, runs[0]);
    }

    @Test
    public void discoversDependenciesDynamically() {
        Signal<Boolean> useA = new Signal<>(true);
        Signal<String> a = new Signal<>("a1");
        Signal<String> b = new Signal<>("b1");
        int[] runs = {0};
        Computed<String> picked = new Computed<>(() -> {
            runs[0]++;
            return useA.get() ? a.get() : b.get();
        });

        assertEquals("a1", picked.get());
        assertEquals(1, a.observerCount());
        assertEquals(0, b.observerCount(), "the untaken branch is not a dependency");

        useA.set(false);
        assertEquals("b1", picked.get());
        assertEquals(0, a.observerCount(), "a is unsubscribed once it is no longer read");
        assertEquals(1, b.observerCount());
        assertEquals(1, useA.observerCount(), "the condition itself stays subscribed");

        int before = runs[0];
        a.set("a2"); // no longer a dependency
        assertEquals("b1", picked.get());
        assertEquals(before, runs[0], "changing a former dependency must not recompute");

        b.set("b2");
        assertEquals("b2", picked.get());
        assertEquals(before + 1, runs[0]);
    }

    @Test
    public void keepsSubscriptionsOfSourcesReadAgain() {
        Signal<Integer> source = new Signal<>(1);
        Computed<Integer> value = new Computed<>(source::get);
        value.get();
        source.set(2);
        value.get();
        source.set(3);
        value.get();

        assertEquals(1, source.observerCount(), "re-evaluation must not pile up subscriptions");
    }

    @Test
    public void invalidationPropagatesThroughChainsToObservers() {
        Signal<Integer> source = new Signal<>(1);
        Computed<Integer> doubled = new Computed<>(() -> source.get() * 2);
        Computed<Integer> plusOne = new Computed<>(() -> doubled.get() + 1);
        int[] notified = {0};
        plusOne.subscribe(() -> notified[0]++);

        assertEquals(3, plusOne.get());
        source.set(10);

        assertEquals(1, notified[0], "the observer of the last link hears about the change");
        assertEquals(21, plusOne.get());
        assertEquals(20, doubled.get());
    }

    @Test
    public void observersOfAFailedEvaluationAreStillNotifiedLater() {
        Signal<Integer> source = new Signal<>(-1);
        Computed<Integer> value = new Computed<>(() -> {
            int v = source.get();
            if (v < 0) {
                throw new IllegalArgumentException("negative");
            }
            return v;
        });
        int[] notified = {0};
        value.subscribe(() -> notified[0]++);

        assertThrows(IllegalArgumentException.class, value::get);
        source.set(-2);
        assertEquals(1, notified[0], "still dirty, yet observers must keep hearing about changes");

        source.set(4);
        assertEquals(2, notified[0]);
        assertEquals(4, value.get(), "recovers as soon as the function succeeds");
    }

    @Test
    public void disposeStopsUpdatesAndReleasesSubscriptions() {
        Signal<Integer> source = new Signal<>(1);
        int[] runs = {0};
        Computed<Integer> value = new Computed<>(() -> {
            runs[0]++;
            return source.get();
        });
        value.get();
        assertEquals(1, source.observerCount());

        value.dispose();

        assertTrue(value.isDisposed());
        assertEquals(0, source.observerCount());
        source.set(2);
        assertEquals(1, runs[0], "a disposed computed never runs again");
        assertThrows(IllegalStateException.class, value::get);
        assertThrows(IllegalStateException.class, () -> value.subscribe(() -> { }));
    }

    @Test
    public void disposeIsIdempotentAndDropsObservers() {
        Signal<Integer> source = new Signal<>(1);
        Computed<Integer> value = new Computed<>(source::get);
        value.get();
        int[] notified = {0};
        value.subscribe(() -> notified[0]++);

        value.dispose();
        value.dispose();

        assertEquals(0, value.observerCount());
        source.set(2);
        assertEquals(0, notified[0]);
    }

    @Test
    public void disposingBeforeTheFirstGetIsHarmless() {
        Computed<Integer> value = new Computed<>(() -> 1);
        value.dispose();
        assertTrue(value.isDisposed());
        assertThrows(IllegalStateException.class, value::get);
    }

    @Test
    public void cyclicDependencyIsDetected() {
        @SuppressWarnings("unchecked")
        Computed<Integer>[] self = new Computed[1];
        self[0] = new Computed<>(() -> self[0].get() + 1);

        assertThrows(IllegalStateException.class, () -> self[0].get());
        assertFalse(Reactor.isNotifying());
        assertEquals(null, Reactor.current(), "the tracking scope must be restored after a failure");
    }

    @Test
    public void writingToASignalFromTheFunctionIsRejected() {
        Signal<Integer> target = new Signal<>(0);
        Computed<Integer> impure = new Computed<>(() -> {
            target.set(1);
            return 0;
        });

        assertThrows(IllegalStateException.class, impure::get);
        assertEquals(0, target.get());
    }

    @Test
    public void readingOutsideAnEvaluationRegistersNothing() {
        Signal<Integer> source = new Signal<>(1);
        source.get();
        assertEquals(0, source.observerCount());
    }

    @Test
    public void untrackedReadsAreNotDependencies() {
        Signal<Integer> tracked = new Signal<>(1);
        Signal<Integer> ignored = new Signal<>(10);
        int[] runs = {0};
        Computed<Integer> value = new Computed<>(() -> {
            runs[0]++;
            return tracked.get() + Reactor.untracked(ignored::get);
        });
        assertEquals(11, value.get());
        assertEquals(0, ignored.observerCount());

        ignored.set(20);
        assertEquals(11, value.get(), "an untracked source does not invalidate the value");
        assertEquals(1, runs[0]);
    }
}
