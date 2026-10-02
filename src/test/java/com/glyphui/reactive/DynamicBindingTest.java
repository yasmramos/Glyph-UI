package com.glyphui.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class DynamicBindingTest {

    /** Minimal model with an observable member. */
    private static final class Person {
        final Signal<String> name;

        Person(String name) {
            this.name = new Signal<>(name);
        }
    }

    private final List<String> applied = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        Reactor.reset();
        applied.clear();
    }

    @AfterEach
    public void tearDown() {
        Reactor.reset();
    }

    @Test
    public void appliesTheInitialMemberValue() {
        Signal<Person> root = new Signal<>(new Person("Ann"));

        new DynamicBinding<>(root, p -> p.name, applied::add);

        assertEquals(List.of("Ann"), applied);
    }

    @Test
    public void reappliesWhenTheMemberChanges() {
        Person ann = new Person("Ann");
        Signal<Person> root = new Signal<>(ann);
        new DynamicBinding<>(root, p -> p.name, applied::add);

        ann.name.set("Anna");

        assertEquals(List.of("Ann", "Anna"), applied);
    }

    @Test
    public void resubscribesToTheNewMemberWhenTheRootChanges() {
        Person ann = new Person("Ann");
        Person bob = new Person("Bob");
        Signal<Person> root = new Signal<>(ann);
        new DynamicBinding<>(root, p -> p.name, applied::add);
        assertEquals(1, ann.name.observerCount());

        root.set(bob);

        assertEquals(List.of("Ann", "Bob"), applied);
        assertEquals(0, ann.name.observerCount(), "the old member is unsubscribed");
        assertEquals(1, bob.name.observerCount(), "the new member is subscribed");

        ann.name.set("Ann 2");
        assertEquals(List.of("Ann", "Bob"), applied, "the old member no longer drives the effect");

        bob.name.set("Bobby");
        assertEquals(List.of("Ann", "Bob", "Bobby"), applied);
    }

    @Test
    public void aNullMemberDeliversNull() {
        Signal<Person> root = new Signal<>(new Person("Ann"));
        new DynamicBinding<>(root, p -> p == null ? null : p.name, applied::add);

        root.set(null);

        assertEquals(2, applied.size());
        assertEquals("Ann", applied.get(0));
        assertEquals(null, applied.get(1));
    }

    @Test
    public void disposeReleasesEverySubscriptionWithoutLeaks() {
        Person ann = new Person("Ann");
        Signal<Person> root = new Signal<>(ann);
        DynamicBinding<Person, String> binding =
                new DynamicBinding<>(root, p -> p.name, applied::add);

        binding.dispose();

        assertTrue(binding.isDisposed());
        assertEquals(0, root.observerCount());
        assertEquals(0, ann.name.observerCount());
        root.set(new Person("Bob"));
        ann.name.set("changed");
        assertEquals(List.of("Ann"), applied);
    }

    @Test
    public void disposeIsIdempotent() {
        Signal<Person> root = new Signal<>(new Person("Ann"));
        DynamicBinding<Person, String> binding =
                new DynamicBinding<>(root, p -> p.name, applied::add);

        binding.dispose();
        binding.dispose();
        binding.close();

        assertEquals(0, root.observerCount());
    }

    @Test
    public void aFailingInitialApplyDoesNotLeakSubscriptions() {
        Person ann = new Person("Ann");
        Signal<Person> root = new Signal<>(ann);

        assertThrows(IllegalArgumentException.class, () -> new DynamicBinding<>(root, p -> p.name, v -> {
            throw new IllegalArgumentException("boom");
        }));

        assertEquals(0, root.observerCount());
        assertEquals(0, ann.name.observerCount());
    }

    @Test
    public void manyRootSwapsNeverAccumulateSubscriptions() {
        Person first = new Person("p0");
        Signal<Person> root = new Signal<>(first);
        DynamicBinding<Person, String> binding =
                new DynamicBinding<>(root, p -> p.name, applied::add);
        Person last = first;
        for (int i = 1; i <= 50; i++) {
            last = new Person("p" + i);
            root.set(last);
        }

        assertEquals(1, root.observerCount());
        assertEquals(1, last.name.observerCount());
        assertEquals(0, first.name.observerCount());
        assertEquals(51, applied.size());
        binding.dispose();
        assertEquals(0, last.name.observerCount());
        assertEquals(0, root.observerCount());
    }
}
