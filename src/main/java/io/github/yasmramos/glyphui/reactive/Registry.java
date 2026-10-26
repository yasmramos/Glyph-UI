package io.github.yasmramos.glyphui.reactive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Ordered registry of notification targets shared by {@code Signal},
 * {@code Computed} and {@code SignalList}.
 *
 * <p>Notification iterates over a snapshot: targets added during a
 * notification are first called on the next one, and targets removed during
 * a notification are skipped. A failing target does not prevent the others
 * from being notified; the first failure is rethrown afterwards with the rest
 * attached as suppressed exceptions.</p>
 *
 * <p>Entries live in a {@link LinkedHashMap} so unsubscribing is O(1) —
 * disposing N subscriptions costs O(N), not O(N²) — and iteration keeps
 * insertion order. The snapshot for a notification is copied lazily: when no
 * structural change happens while iterating (the common case), the copy is
 * avoided entirely via an internal cursor over the live map.</p>
 */
final class Registry<E> {

    private static final class Entry<E> {
        final E target;
        boolean active = true;

        Entry(E target) {
            this.target = target;
        }
    }

    /** Live entries keyed by their wrapper; preserves insertion order. */
    private final Map<Entry<E>, Boolean> entries = new LinkedHashMap<>();

    /** Bumped whenever the entry set changes structurally. */
    private int mutationCount;

    Trackable.Subscription add(E target) {
        Objects.requireNonNull(target, "target");
        Entry<E> entry = new Entry<>(target);
        entries.put(entry, Boolean.TRUE);
        mutationCount++;
        return () -> {
            if (entry.active) {
                entry.active = false;
                entries.remove(entry);
                mutationCount++;
            }
        };
    }

    int size() {
        return entries.size();
    }

    void clear() {
        for (Entry<E> entry : entries.keySet()) {
            entry.active = false;
        }
        entries.clear();
        mutationCount++;
    }

    void forEach(Consumer<? super E> action) {
        if (entries.isEmpty()) {
            return;
        }
        // Fast path: iterate the live map with an index cursor. If any
        // callback mutates the registry (subscribe/unsubscribe during
        // notification), fall back to a snapshot copy for the remaining
        // items. This avoids copying the whole list on every notification
        // (60 fps x 100 signals used to mean 6000 copies per second).
        RuntimeException failure = null;
        List<Entry<E>> snapshot = null;
        int cursor = 0;
        int seenMutations = mutationCount;
        while (true) {
            Entry<E> entry;
            if (snapshot != null) {
                if (cursor >= snapshot.size()) {
                    break;
                }
                entry = snapshot.get(cursor++);
            } else {
                if (cursor >= entries.size()) {
                    break;
                }
                entry = nthEntry(cursor++);
            }
            if (!entry.active) {
                continue;
            }
            try {
                action.accept(entry.target);
            } catch (RuntimeException e) {
                failure = Reactor.merge(failure, e);
            }
            if (snapshot == null && mutationCount != seenMutations) {
                // Structure changed mid-iteration: take a snapshot from the
                // current position onward so ordering stays well-defined.
                snapshot = new ArrayList<>(entries.keySet());
                cursor = Math.min(cursor, snapshot.size());
                seenMutations = mutationCount;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    /** Returns the i-th entry of the insertion-ordered map in O(i). */
    private Entry<E> nthEntry(int index) {
        int i = 0;
        for (Entry<E> entry : entries.keySet()) {
            if (i++ == index) {
                return entry;
            }
        }
        throw new IndexOutOfBoundsException(index);
    }
}
