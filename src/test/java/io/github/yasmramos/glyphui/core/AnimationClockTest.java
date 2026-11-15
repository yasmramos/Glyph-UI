package io.github.yasmramos.glyphui.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Timing tests for {@link AnimationClock}: the frame delta, the clamp that
 * keeps a late frame from jumping an animation, and the auto-drop of a
 * one-shot animation that reports itself finished.
 *
 * <p>The clock is driven by an injected time source, so the tests never sleep
 * and never depend on wall-clock timing.</p>
 */
class AnimationClockTest {

    /** A time source the test advances by hand. */
    private static final class FakeTime implements java.util.function.DoubleSupplier {
        double seconds;

        @Override
        public double getAsDouble() {
            return seconds;
        }
    }

    @Test
    void theFirstFrameFeedsNoDelta() {
        FakeTime time = new FakeTime();
        AnimationClock clock = new AnimationClock(time);
        List<Float> deltas = new ArrayList<>();
        clock.add(deltas::add);

        // Seeding call: no delta, so a just-registered animation starts at 0.
        time.seconds = 100.0;
        clock.advance();

        assertTrue(deltas.isEmpty(), "the seeding frame must not advance anything");
    }

    @Test
    void eachFrameFeedsTheElapsedSeconds() {
        FakeTime time = new FakeTime();
        AnimationClock clock = new AnimationClock(time);
        List<Float> deltas = new ArrayList<>();
        clock.add(deltas::add);

        time.seconds = 10.0;
        clock.advance(); // seed
        time.seconds = 10.016;
        clock.advance();
        time.seconds = 10.032;
        clock.advance();

        assertEquals(2, deltas.size());
        assertEquals(0.016f, deltas.get(0), 1e-4f);
        assertEquals(0.016f, deltas.get(1), 1e-4f);
    }

    @Test
    void aLateFrameIsClampedSoTheAnimationNeverJumps() {
        FakeTime time = new FakeTime();
        AnimationClock clock = new AnimationClock(time);
        List<Float> deltas = new ArrayList<>();
        clock.add(deltas::add);

        time.seconds = 0.0;
        clock.advance(); // seed
        // A five-second stall (debugger pause, backgrounded window).
        time.seconds = 5.0;
        clock.advance();

        assertEquals(1, deltas.size());
        assertEquals(AnimationClock.MAX_STEP_SECONDS, deltas.get(0), 1e-4f,
                "a late frame advances by at most one short step");
    }

    @Test
    void aFinishedAnimationIsDroppedAndStopsTheLoop() {
        FakeTime time = new FakeTime();
        AnimationClock clock = new AnimationClock(time);
        int[] ticks = {0};
        clock.add(delta -> ticks[0]++);
        // Second animatable that finishes after its first tick.
        boolean[] finished = {false};
        clock.add(new Animatable() {
            @Override
            public void advance(float deltaSeconds) {
                finished[0] = true;
            }

            @Override
            public boolean isFinished() {
                return finished[0];
            }
        });

        assertTrue(clock.isAnimating());

        time.seconds = 0.0;
        clock.advance(); // seed
        time.seconds = 0.016;
        clock.advance(); // both tick; the second is now finished and dropped

        assertEquals(1, ticks[0]);
        assertEquals(1, clock.size(), "the finished animation is dropped");
        assertTrue(clock.isAnimating(), "the remaining animation keeps the loop alive");
    }

    @Test
    void removingTheLastAnimationStopsTheLoopAndResetsTheClock() {
        FakeTime time = new FakeTime();
        AnimationClock clock = new AnimationClock(time);
        Animatable a = delta -> { };
        clock.add(a);
        assertTrue(clock.isAnimating());

        assertTrue(clock.remove(a));
        assertFalse(clock.isAnimating());
        assertFalse(clock.remove(a), "a second removal is a no-op");

        // Re-registering seeds again: the idle gap is not charged as one frame.
        List<Float> deltas = new ArrayList<>();
        clock.add(deltas::add);
        time.seconds = 1000.0;
        clock.advance();
        assertTrue(deltas.isEmpty(), "the reseeding frame must not advance");
    }

    @Test
    void nullRegistrationIsIgnored() {
        AnimationClock clock = new AnimationClock(new FakeTime());
        assertFalse(clock.add(null));
        assertFalse(clock.isAnimating());
        assertFalse(clock.remove(null));
    }
}
