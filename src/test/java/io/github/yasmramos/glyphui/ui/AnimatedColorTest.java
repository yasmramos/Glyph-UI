package io.github.yasmramos.glyphui.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link AnimatedColor}: the eased ARGB value behind a control's
 * hover/press fade.
 */
class AnimatedColorTest {

    @Test
    @DisplayName("a fresh colour rests at its initial value and is finished")
    void freshColourRests() {
        AnimatedColor c = new AnimatedColor(0xFF112233);

        assertEquals(0xFF112233, c.get());
        assertEquals(0xFF112233, c.target());
        assertTrue(c.isFinished(), "a colour that never animated has settled");
    }

    @Test
    @DisplayName("animateTo eases across frames and settles exactly at the target")
    void animateToSettlesAtTarget() {
        AnimatedColor c = new AnimatedColor(0xFF000000);

        c.animateTo(0xFFFFFFFF);
        assertFalse(c.isFinished(), "starting a run leaves it unfinished");

        c.advance(1.0f);
        assertTrue(c.isFinished());
        assertEquals(0xFFFFFFFF, c.get(),
                "a settled transition lands on the exact target colour");
    }

    @Test
    @DisplayName("the run is continuous: a mid-flight retarget starts from the current colour")
    void retargetMidFlightIsContinuous() {
        AnimatedColor c = new AnimatedColor(0xFF000000);
        c.animateTo(0xFFFFFFFF);
        c.advance(0.09f);
        int midway = c.get();
        assertFalse(midway == 0xFF000000 || midway == 0xFFFFFFFF,
                "halfway through the run the colour is strictly between the ends");

        c.animateTo(0xFF000000);
        assertFalse(c.isFinished());

        c.advance(1.0f);
        assertTrue(c.isFinished());
        assertEquals(0xFF000000, c.get());
    }

    @Test
    @DisplayName("advancing a settled colour or a non-positive delta is a no-op")
    void advanceIsNoOpWhenSettled() {
        AnimatedColor c = new AnimatedColor(0xFF203040);

        c.advance(0.5f);
        assertEquals(0xFF203040, c.get());
        c.advance(0.0f);
        assertEquals(0xFF203040, c.get());
        assertTrue(c.isFinished());
    }

    @Test
    @DisplayName("snapTo jumps to the target and cancels the run in flight")
    void snapToCancelsRun() {
        AnimatedColor c = new AnimatedColor(0xFF000000);
        c.animateTo(0xFFFFFFFF);

        c.snapTo(0xFF123456);

        assertEquals(0xFF123456, c.get());
        assertEquals(0xFF123456, c.target());
        assertTrue(c.isFinished());
    }

    @Nested
    @DisplayName("sync policy")
    class Sync {

        @Test
        @DisplayName("the first sync seeds the colour without animating")
        void firstSyncSeeds() {
            AnimatedColor c = new AnimatedColor(0);

            int painted = c.sync(null, null, 0xFF112233);

            assertEquals(0xFF112233, painted, "the first paint must be the resting colour");
            assertTrue(c.isFinished(), "a widget must not fade in on first appearance");
        }

        @Test
        @DisplayName("a detached colour leaves the transition to the caller instead of snapping")
        void detachedSyncLeavesRunToCaller() {
            AnimatedColor c = new AnimatedColor(0);
            c.sync(null, null, 0xFF112233);

            // No owner/driver: nothing drives the frame loop, so the run starts
            // but the value stays where it was until advance(...) is called.
            int painted = c.sync(null, null, 0xFF445566);

            assertEquals(0xFF112233, painted,
                    "a detached colour must not jump to the new target");
            assertFalse(c.isFinished(), "the transition is pending, not settled");

            c.advance(1.0f);
            assertTrue(c.isFinished());
            assertEquals(0xFF445566, c.get());
        }

        @Test
        @DisplayName("re-syncing the same target returns the settled colour")
        void repeatedSyncIsStable() {
            AnimatedColor c = new AnimatedColor(0);
            c.sync(null, null, 0xFF112233);

            assertEquals(0xFF112233, c.sync(null, null, 0xFF112233));
        }
    }
}
