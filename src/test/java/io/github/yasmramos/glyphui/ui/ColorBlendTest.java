package io.github.yasmramos.glyphui.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ColorBlend}: the per-channel ARGB interpolation the
 * interactive widgets share.
 */
class ColorBlendTest {

    @Test
    @DisplayName("t = 0 returns the source colour unchanged")
    void zeroReturnsSource() {
        assertEquals(0xFF112233, ColorBlend.lerp(0xFF112233, 0xFFAABBCC, 0.0f));
    }

    @Test
    @DisplayName("t = 1 returns the destination colour unchanged")
    void oneReturnsDestination() {
        assertEquals(0xFFAABBCC, ColorBlend.lerp(0xFF112233, 0xFFAABBCC, 1.0f));
    }

    @Test
    @DisplayName("t out of range clamps to the endpoints")
    void outOfRangeClamps() {
        assertEquals(0xFF112233, ColorBlend.lerp(0xFF112233, 0xFFAABBCC, -1.0f));
        assertEquals(0xFFAABBCC, ColorBlend.lerp(0xFF112233, 0xFFAABBCC, 2.0f));
    }

    @Test
    @DisplayName("every channel, alpha included, interpolates linearly")
    void interpolatesAllChannels() {
        // 0x00 -> 0xFF in every channel: the midpoint is 0x80 after rounding,
        // alpha included.
        assertEquals(0x80808080, ColorBlend.lerp(0x00000000, 0xFFFFFFFF, 0.5f));
        assertEquals(0x80402010, ColorBlend.lerp(0x00000000, 0xFF804020, 0.5f));
    }

    @Test
    @DisplayName("an identical pair blends to itself at any t")
    void identicalPairIsStable() {
        assertEquals(0xFF203040, ColorBlend.lerp(0xFF203040, 0xFF203040, 0.37f));
    }

    @Test
    @DisplayName("lerpByte interpolates a single byte-scale value")
    void byteLerpInterpolates() {
        assertEquals(100, ColorBlend.lerpByte(100, 200, 0.0f));
        assertEquals(200, ColorBlend.lerpByte(100, 200, 1.0f));
        assertEquals(150, ColorBlend.lerpByte(100, 200, 0.5f));
        assertEquals(100, ColorBlend.lerpByte(100, 200, -1.0f));
        assertEquals(200, ColorBlend.lerpByte(100, 200, 2.0f));
    }
}
