package com.glyphui.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class BindingTest {

    @BeforeEach
    public void setUp() {
        Reactor.reset();
    }

    @AfterEach
    public void tearDown() {
        Reactor.reset();
    }

    @Test
    public void appliesTheInitialValueImmediately() {
        Signal<String> source = new Signal<>("hello");
        List<String> applied = new ArrayList<>();

        new Binding<>(source, applied::add);

        assertEquals(List.of("hello"), applied);
    }

    @Test
    public void reappliesOnEveryChange() {
        Signal<Integer> source = new Signal<>(1);
        List<Integer> applied = new ArrayList<>();
        new Binding<>(source, applied::add);

        source.set(2);
        source.set(3);

        assertEquals(List.of(1, 2, 3), applied);
    }

    @Test
    public void doesNotReapplyWhenTheSignalSkipsAnEqualValue() {
        Signal<Integer> source = new Signal<>(1);
        List<Integer> applied = new ArrayList<>();
        new Binding<>(source, applied::add);

        source.set(1);

        assertEquals(List.of(1), applied);
    }

    @Test
    public void followsAComputedSource() {
        Signal<Integer> source = new Signal<>(2);
        Computed<Integer> squared = new Computed<>(() -> source.get() * source.get());
        List<Integer> applied = new ArrayList<>();
        new Binding<>(squared, applied::add);

        source.set(3);

        assertEquals(List.of(4, 9), applied);
    }

    @Test
    public void followsAList() {
        SignalList<String> list = new SignalList<>(List.of("a"));
        List<Integer> sizes = new ArrayList<>();
        new Binding<>(list, l -> sizes.add(l.snapshot().size()));

        list.add("b");

        assertEquals(List.of(1, 2), sizes);
    }

    @Test
    public void diamondDependenciesApplyOnceWithAConsistentValue() {
        Signal<Integer> source = new Signal<>(1);
        Computed<Integer> single = new Computed<>(source::get);
        Computed<Integer> twice = new Computed<>(() -> source.get() * 2);
        Computed<Integer> sum = new Computed<>(() -> single.get() + twice.get());
        List<Integer> applied = new ArrayList<>();
        new Binding<>(sum, applied::add);

        source.set(10);

        assertEquals(List.of(3, 30), applied,
                "exactly one re-application, never an intermediate value like 12 or 21");
    }

    @Test
    public void closeStopsApplicationsAndReleasesTheSubscription() {
        Signal<Integer> source = new Signal<>(1);
        List<Integer> applied = new ArrayList<>();
        Binding<Integer> binding = new Binding<>(source, applied::add);

        binding.close();
        source.set(2);

        assertEquals(List.of(1), applied);
        assertTrue(binding.isDisposed());
        assertEquals(0, source.observerCount());
    }

    @Test
    public void disposeIsIdempotent() {
        Signal<Integer> source = new Signal<>(1);
        Binding<Integer> binding = new Binding<>(source, v -> { });

        binding.dispose();
        binding.dispose();
        binding.close();

        assertEquals(0, source.observerCount());
    }

    @Test
    public void aBindingDisposedDuringANotificationIsNotApplied() {
        Signal<Integer> source = new Signal<>(1);
        List<Integer> applied = new ArrayList<>();
        Binding<Integer> victim = new Binding<>(source, applied::add);
        // Subscribed after the victim: runs inside the same notification
        source.subscribe(victim::dispose);

        source.set(2);

        assertEquals(List.of(1), applied);
    }

    @Test
    public void aFailingInitialApplyDoesNotLeaveASubscription() {
        Signal<Integer> source = new Signal<>(1);

        assertThrows(IllegalArgumentException.class, () -> new Binding<>(source, v -> {
            throw new IllegalArgumentException("boom");
        }));

        assertEquals(0, source.observerCount());
    }

    @Test
    public void aFailingEffectDoesNotBlockOtherBindingsNorTheReactor() {
        Signal<Integer> source = new Signal<>(0);
        List<Integer> applied = new ArrayList<>();
        boolean[] armed = {false};
        new Binding<>(source, v -> {
            if (armed[0]) {
                throw new IllegalArgumentException("boom");
            }
        });
        new Binding<>(source, applied::add);
        armed[0] = true;

        assertThrows(IllegalArgumentException.class, () -> source.set(1));

        assertEquals(List.of(0, 1), applied);
        armed[0] = false;
        source.set(2);
        assertEquals(List.of(0, 1, 2), applied);
    }

    @Test
    public void anEffectMayNotWriteToSignals() {
        Signal<Integer> source = new Signal<>(0);
        Signal<Integer> other = new Signal<>(0);
        boolean[] armed = {false};
        new Binding<>(source, v -> {
            if (armed[0]) {
                other.set(v);
            }
        });
        armed[0] = true;

        assertThrows(IllegalStateException.class, () -> source.set(1));

        assertEquals(0, other.get());
    }

    @Test
    public void effectReadsAreNotTrackedAsDependenciesOfOtherObservers() {
        Signal<Integer> source = new Signal<>(0);
        Signal<Integer> read = new Signal<>(5);
        new Binding<>(source, v -> read.get());

        assertEquals(0, read.observerCount());
    }

    @Test
    public void impactDefaultsToPaintAndCanBeChosen() {
        Signal<Integer> source = new Signal<>(0);

        assertEquals(Binding.Impact.PAINT, new Binding<>(source, v -> { }).impact());
        assertEquals(Binding.Impact.LAYOUT,
                new Binding<>(source, v -> { }, Binding.Impact.LAYOUT).impact());
    }
}
