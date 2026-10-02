package com.glyphui.reactive;

import java.util.ArrayList;
import java.util.List;
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
 */
final class Registry<E> {

    private static final class Entry<E> {
        final E target;
        boolean active = true;

        Entry(E target) {
            this.target = target;
        }
    }

    private final List<Entry<E>> entries = new ArrayList<>();

    Trackable.Subscription add(E target) {
        Objects.requireNonNull(target, "target");
        Entry<E> entry = new Entry<>(target);
        entries.add(entry);
        return () -> {
            if (entry.active) {
                entry.active = false;
                entries.remove(entry);
            }
        };
    }

    int size() {
        return entries.size();
    }

    void clear() {
        for (Entry<E> entry : entries) {
            entry.active = false;
        }
        entries.clear();
    }

    void forEach(Consumer<? super E> action) {
        if (entries.isEmpty()) {
            return;
        }
        List<Entry<E>> snapshot = new ArrayList<>(entries);
        RuntimeException failure = null;
        for (Entry<E> entry : snapshot) {
            if (!entry.active) {
                continue;
            }
            try {
                action.accept(entry.target);
            } catch (RuntimeException e) {
                failure = Reactor.merge(failure, e);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }
}
