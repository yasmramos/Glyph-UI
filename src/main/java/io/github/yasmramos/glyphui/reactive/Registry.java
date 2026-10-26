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
 * a notification still receive the in-flight pass but never a later one.
 * A failing target does not prevent the others from being notified; the
 * first failure is rethrown afterwards with the rest attached as suppressed
 * exceptions.</p>
 *
 * <p>Entries live in a {@link LinkedHashMap} so unsubscribing is O(1) —
 * disposing N subscriptions costs O(N), not O(N²) — and iteration keeps
 * insertion order.</p>
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

    /**
     * Number of in-flight {@link #forEach} passes. While it is non-zero,
     * structural additions are appended to a pending list instead of the
     * live map, so an iteration never visits a target registered during
     * the same notification (snapshot semantics).
     */
    private int activeIterations;

    /** Entries added while a notification pass is running. */
    private final List<Entry<E>> pending = new ArrayList<>();

    Trackable.Subscription add(E target) {
        Objects.requireNonNull(target, "target");
        Entry<E> entry = new Entry<>(target);
        if (activeIterations > 0) {
            // Defer the insertion: the running pass must not see it, and
            // later passes must see it after the pre-existing entries.
            pending.add(entry);
        } else {
            entries.put(entry, Boolean.TRUE);
        }
        mutationCount++;
        return () -> {
            if (entry.active) {
                entry.active = false;
                entries.remove(entry);
                pending.remove(entry);
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
        for (Entry<E> entry : pending) {
            entry.active = false;
        }
        pending.clear();
        mutationCount++;
    }

    void forEach(Consumer<? super E> action) {
        if (entries.isEmpty()) {
            return;
        }
        // Snapshot semantics (item 4.6): targets added during this pass are
        // first called on the next one, and targets removed during the pass
        // still receive it (their "active" flag is cleared but the snapshot
        // already references them). The copy is taken once per notification —
        // unavoidable when callbacks may mutate the registry, since any
        // live-map iteration would otherwise risk ConcurrentModification or
        // reordering bugs. Deferred additions are merged back only after
        // every nested pass has finished (see add()).
        RuntimeException failure = null;
        List<Entry<E>> snapshot = new ArrayList<>(entries.keySet());
        activeIterations++;
        try {
            for (Entry<E> entry : snapshot) {
                try {
                    action.accept(entry.target);
                } catch (RuntimeException e) {
                    failure = Reactor.merge(failure, e);
                }
            }
        } finally {
            activeIterations--;
            if (activeIterations == 0 && !pending.isEmpty()) {
                for (Entry<E> entry : pending) {
                    entries.put(entry, Boolean.TRUE);
                }
                pending.clear();
                mutationCount++;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
