package com.glyphui.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SignalTest {

    @BeforeEach
    public void setUp() {
        Reactor.reset();
    }

    @AfterEach
    public void tearDown() {
        Reactor.reset();
    }

    @Test
    public void getReturnsInitialAndLatestValue() {
        Signal<String> signal = new Signal<>("a");
        assertEquals("a", signal.get());
        signal.set("b");
        assertEquals("b", signal.get());
    }

    @Test
    public void notifiesObserversOnChange() {
        Signal<Integer> signal = new Signal<>(1);
        int[] count = {0};
        signal.subscribe(() -> count[0]++);

        signal.set(2);
        signal.set(3);

        assertEquals(2, count[0]);
    }

    @Test
    public void skipsNotificationWhenValueIsEqual() {
        Signal<String> signal = new Signal<>("same");
        int[] count = {0};
        signal.subscribe(() -> count[0]++);

        signal.set(new String("same")); // equal, but a different instance

        assertEquals(0, count[0]);
    }

    @Test
    public void nullIsAValidValueAndEqualNullsAreSkipped() {
        Signal<String> signal = new Signal<>();
        int[] count = {0};
        signal.subscribe(() -> count[0]++);

        signal.set(null);
        assertEquals(0, count[0]);

        signal.set("x");
        signal.set(null);
        assertEquals(2, count[0]);
        assertNull(signal.get());
    }

    @Test
    public void closedSubscriptionIsNotNotifiedAndCloseIsIdempotent() {
        Signal<Integer> signal = new Signal<>(0);
        int[] count = {0};
        Trackable.Subscription subscription = signal.subscribe(() -> count[0]++);

        signal.set(1);
        subscription.close();
        subscription.close();
        signal.set(2);

        assertEquals(1, count[0]);
        assertEquals(0, signal.observerCount());
    }

    @Test
    public void observersAreNotifiedInSubscriptionOrder() {
        Signal<Integer> signal = new Signal<>(0);
        StringBuilder order = new StringBuilder();
        signal.subscribe(() -> order.append('A'));
        signal.subscribe(() -> order.append('B'));
        signal.subscribe(() -> order.append('C'));

        signal.set(1);

        assertEquals("ABC", order.toString());
    }

    @Test
    public void aFailingObserverDoesNotStopTheOthers() {
        Signal<Integer> signal = new Signal<>(0);
        int[] count = {0};
        signal.subscribe(() -> {
            throw new IllegalArgumentException("boom");
        });
        signal.subscribe(() -> count[0]++);

        assertThrows(IllegalArgumentException.class, () -> signal.set(1));

        assertEquals(1, count[0]);
        assertEquals(1, signal.get());
        // The reactor must not be stuck in the notifying state
        assertTrue(!Reactor.isNotifying());
    }

    @Test
    public void reentrantSetOnTheSameSignalThrows() {
        Signal<Integer> signal = new Signal<>(0);
        AtomicReference<Throwable> inner = new AtomicReference<>();
        signal.subscribe(() -> {
            try {
                signal.set(99);
            } catch (IllegalStateException e) {
                inner.set(e);
                throw e;
            }
        });

        assertThrows(IllegalStateException.class, () -> signal.set(1));

        assertTrue(inner.get() instanceof IllegalStateException);
        assertEquals(1, signal.get(), "the outer write stays applied, the re-entrant one is rejected");
    }

    @Test
    public void reentrantSetOnAnotherSignalThrows() {
        Signal<Integer> source = new Signal<>(0);
        Signal<Integer> other = new Signal<>(0);
        source.subscribe(() -> other.set(5));

        assertThrows(IllegalStateException.class, () -> source.set(1));

        assertEquals(0, other.get());
    }

    @Test
    public void canSetAgainAfterAFailedNotification() {
        Signal<Integer> signal = new Signal<>(0);
        Trackable.Subscription bad = signal.subscribe(() -> {
            throw new IllegalStateException("boom");
        });
        assertThrows(IllegalStateException.class, () -> signal.set(1));
        bad.close();

        signal.set(2);

        assertEquals(2, signal.get());
    }

    @Test
    public void setFromAnotherThreadThrowsAClearException() throws InterruptedException {
        Signal<Integer> signal = new Signal<>(0);
        AtomicReference<Throwable> error = new AtomicReference<>();

        Thread worker = new Thread(() -> {
            try {
                signal.set(1);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "worker-1");
        worker.start();
        worker.join();

        assertTrue(error.get() instanceof IllegalStateException);
        assertTrue(error.get().getMessage().contains("UI thread"), error.get().getMessage());
        assertTrue(error.get().getMessage().contains("worker-1"), error.get().getMessage());
        assertEquals(0, signal.get());
    }

    @Test
    public void explicitUiThreadOverridesTheCreatorThread() throws InterruptedException {
        Signal<Integer> signal = new Signal<>(0); // created on the test thread
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread ui = new Thread(() -> {
            try {
                signal.set(7);
            } catch (Throwable t) {
                error.set(t);
            }
        }, "declared-ui");
        Reactor.setUiThread(ui);

        // The creator thread is no longer the UI thread
        assertThrows(IllegalStateException.class, () -> signal.set(1));

        ui.start();
        ui.join();
        assertNull(error.get(), "the declared UI thread may write");
        assertEquals(7, signal.get());
    }

    @Test
    public void declaringTheCurrentThreadAsUiThreadAllowsWrites() {
        Signal<Integer> signal = new Signal<>(0);
        Reactor.setUiThread(Thread.currentThread());

        signal.set(3);

        assertEquals(3, signal.get());
        assertEquals(Thread.currentThread(), Reactor.getUiThread());
    }
}
