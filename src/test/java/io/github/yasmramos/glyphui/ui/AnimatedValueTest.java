package io.github.yasmramos.glyphui.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AnimatedValue}: the easing value behind a widget's own
 * motion (the sliding switch knob, the gliding tab indicator). The value owns
 * no clock, so the tests drive it with explicit deltas instead of sleeping.
 */
class AnimatedValueTest {

    @Test
    @DisplayName("a fresh value rests at its initial value and is finished")
    void restsAtInitial() {
        AnimatedValue v = new AnimatedValue(0.5f);

        assertEquals(0.5f, v.get(), 1e-6f);
        assertTrue(v.isFinished(), "a value that never moved has nothing to animate");
    }

    @Test
    @DisplayName("advancing a resting value does not move it")
    void restingValueIgnoresAdvance() {
        AnimatedValue v = new AnimatedValue(0.5f);

        v.advance(1.0f);

        assertEquals(0.5f, v.get(), 1e-6f);
    }

    @Test
    @DisplayName("animateTo eases from the current value to the target")
    void easesTowardsTarget() {
        AnimatedValue v = new AnimatedValue(0.0f, 1.0f);

        v.animateTo(1.0f);
        assertFalse(v.isFinished(), "the run has just started");

        v.advance(0.5f);
        assertTrue(v.get() > 0.0f && v.get() < 1.0f,
                "halfway through the run the value is between the ends, got " + v.get());
        assertFalse(v.isFinished(), "halfway is not the end");
    }

    @Test
    @DisplayName("the run settles exactly on the target when the duration elapses")
    void settlesOnTarget() {
        AnimatedValue v = new AnimatedValue(0.0f, 1.0f);
        v.animateTo(1.0f);

        v.advance(1.0f);

        assertEquals(1.0f, v.get(), 1e-6f);
        assertTrue(v.isFinished());
    }

    @Test
    @DisplayName("a non-positive delta leaves the value untouched")
    void nonPositiveDeltaIsNoop() {
        AnimatedValue v = new AnimatedValue(0.0f, 1.0f);
        v.animateTo(1.0f);

        v.advance(0.0f);
        assertEquals(0.0f, v.get(), 1e-6f);
        v.advance(-0.5f);
        assertEquals(0.0f, v.get(), 1e-6f);
    }

    @Test
    @DisplayName("retargeting mid-flight continues from the current value")
    void retargetIsContinuous() {
        AnimatedValue v = new AnimatedValue(0.0f, 1.0f);
        v.animateTo(1.0f);
        v.advance(0.5f);
        float mid = v.get();

        v.animateTo(0.0f);

        assertEquals(mid, v.get(), 1e-6f, "retargeting must not jump the value");
        assertFalse(v.isFinished());
        v.advance(1.0f);
        assertEquals(0.0f, v.get(), 1e-6f);
    }

    @Test
    @DisplayName("animateTo the current resting target is a no-op")
    void sameTargetIsNoop() {
        AnimatedValue v = new AnimatedValue(0.0f, 1.0f);
        v.animateTo(1.0f);
        v.advance(1.0f);
        assertTrue(v.isFinished());

        v.animateTo(1.0f);

        assertTrue(v.isFinished(), "asking for the value it already holds starts no run");
    }

    @Test
    @DisplayName("snapTo jumps to the target and cancels the run")
    void snapJumpsAndFinishes() {
        AnimatedValue v = new AnimatedValue(0.0f, 1.0f);
        v.animateTo(1.0f);

        v.snapTo(0.25f);

        assertEquals(0.25f, v.get(), 1e-6f);
        assertTrue(v.isFinished());
    }
}
