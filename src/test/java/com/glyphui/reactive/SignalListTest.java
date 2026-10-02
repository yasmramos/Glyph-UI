package com.glyphui.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SignalListTest {

    private final List<SignalList.Change<String>> changes = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        Reactor.reset();
        changes.clear();
    }

    @AfterEach
    public void tearDown() {
        Reactor.reset();
    }

    private SignalList<String> listOf(String... initial) {
        return new SignalList<>(List.of(initial));
    }

    private static void assertChange(SignalList.Change<String> change,
                                     SignalList.Change.Kind kind, int index,
                                     String oldValue, String newValue) {
        assertEquals(kind, change.kind());
        assertEquals(index, change.index());
        assertEquals(oldValue, change.oldValue());
        assertEquals(newValue, change.newValue());
    }

    @Test
    public void appendEmitsInsertAtTheEnd() {
        SignalList<String> list = listOf("a", "b");
        list.onListChange(changes::add);

        list.add("c");

        assertEquals(1, changes.size());
        assertChange(changes.get(0), SignalList.Change.Kind.INSERT, 2, null, "c");
        assertEquals(List.of("a", "b", "c"), list.snapshot());
    }

    @Test
    public void insertInTheMiddleReportsTheIndex() {
        SignalList<String> list = listOf("a", "c");
        list.onListChange(changes::add);

        list.add(1, "b");

        assertChange(changes.get(0), SignalList.Change.Kind.INSERT, 1, null, "b");
        assertEquals(List.of("a", "b", "c"), list.snapshot());
    }

    @Test
    public void removeEmitsTheIndexAndTheRemovedElement() {
        SignalList<String> list = listOf("a", "b", "c");
        list.onListChange(changes::add);

        String removed = list.remove(1);

        assertEquals("b", removed);
        assertChange(changes.get(0), SignalList.Change.Kind.REMOVE, 1, "b", null);
        assertEquals(List.of("a", "c"), list.snapshot());
    }

    @Test
    public void setEmitsReplaceWithOldAndNew() {
        SignalList<String> list = listOf("a", "b");
        list.onListChange(changes::add);

        String previous = list.set(0, "z");

        assertEquals("a", previous);
        assertChange(changes.get(0), SignalList.Change.Kind.REPLACE, 0, "a", "z");
        assertEquals(List.of("z", "b"), list.snapshot());
    }

    @Test
    public void replacingWithAnEqualElementEmitsNothing() {
        SignalList<String> list = listOf("a");
        list.onListChange(changes::add);

        list.set(0, new String("a"));

        assertTrue(changes.isEmpty());
    }

    @Test
    public void addAllEmitsOneInsertPerElementWithGrowingIndexes() {
        SignalList<String> list = listOf("a");
        list.onListChange(changes::add);

        list.addAll(List.of("b", "c"));

        assertEquals(2, changes.size());
        assertChange(changes.get(0), SignalList.Change.Kind.INSERT, 1, null, "b");
        assertChange(changes.get(1), SignalList.Change.Kind.INSERT, 2, null, "c");
    }

    @Test
    public void clearEmitsRemovesFromTheLastToTheFirst() {
        SignalList<String> list = listOf("a", "b", "c");
        list.onListChange(changes::add);

        list.clear();

        assertEquals(3, changes.size());
        assertChange(changes.get(0), SignalList.Change.Kind.REMOVE, 2, "c", null);
        assertChange(changes.get(1), SignalList.Change.Kind.REMOVE, 1, "b", null);
        assertChange(changes.get(2), SignalList.Change.Kind.REMOVE, 0, "a", null);
        assertTrue(list.isEmpty());
    }

    @Test
    public void closingTheSubscriptionRemovesTheListener() {
        SignalList<String> list = listOf();
        Trackable.Subscription subscription = list.onListChange(changes::add);

        list.add("a");
        subscription.close();
        subscription.close();
        list.add("b");

        assertEquals(1, changes.size());
        assertEquals(0, list.subscriberCount());
    }

    @Test
    public void everyListenerReceivesEveryChange() {
        SignalList<String> list = listOf();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        list.onListChange(c -> first.add(c.newValue()));
        list.onListChange(c -> second.add(c.newValue()));

        list.add("x");

        assertEquals(List.of("x"), first);
        assertEquals(List.of("x"), second);
    }

    @Test
    public void invalidIndexThrowsWithoutEmittingOrMutating() {
        SignalList<String> list = listOf("a");
        list.onListChange(changes::add);

        assertThrows(IndexOutOfBoundsException.class, () -> list.add(5, "x"));
        assertThrows(IndexOutOfBoundsException.class, () -> list.remove(3));
        assertThrows(IndexOutOfBoundsException.class, () -> list.set(2, "x"));

        assertTrue(changes.isEmpty());
        assertEquals(List.of("a"), list.snapshot());
        assertTrue(!Reactor.isNotifying());
    }

    @Test
    public void modifyingTheListFromAListenerThrows() {
        SignalList<String> list = listOf();
        list.onListChange(c -> list.add("again"));

        assertThrows(IllegalStateException.class, () -> list.add("a"));

        assertEquals(List.of("a"), list.snapshot());
    }

    @Test
    public void writingFromAnotherThreadThrows() throws InterruptedException {
        SignalList<String> list = listOf();
        Throwable[] error = new Throwable[1];
        Thread worker = new Thread(() -> {
            try {
                list.add("x");
            } catch (Throwable t) {
                error[0] = t;
            }
        });
        worker.start();
        worker.join();

        assertTrue(error[0] instanceof IllegalStateException);
        assertTrue(list.isEmpty());
    }

    @Test
    public void snapshotIsADetachedUnmodifiableCopy() {
        SignalList<String> list = listOf("a");
        List<String> snapshot = list.snapshot();

        list.add("b");

        assertEquals(List.of("a"), snapshot);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add("c"));
    }

    @Test
    public void computedValuesCanDependOnTheList() {
        SignalList<String> list = listOf("a");
        int[] runs = {0};
        Computed<Integer> size = new Computed<>(() -> {
            runs[0]++;
            return list.size();
        });
        assertEquals(1, size.get());

        list.add("b");
        assertEquals(2, size.get());
        list.remove(0);
        assertEquals(1, size.get());
        assertEquals(3, runs[0]);

        size.dispose();
        assertEquals(0, list.subscriberCount());
    }

    @Test
    public void aFailingListenerDoesNotStopTheOthersNorTheObservers() {
        SignalList<String> list = listOf();
        int[] observed = {0};
        List<String> received = new ArrayList<>();
        list.onListChange(c -> {
            throw new IllegalArgumentException("boom");
        });
        list.onListChange(c -> received.add(c.newValue()));
        list.subscribe(() -> observed[0]++);

        assertThrows(IllegalArgumentException.class, () -> list.add("a"));

        assertEquals(List.of("a"), received);
        assertEquals(1, observed[0]);
    }
}
