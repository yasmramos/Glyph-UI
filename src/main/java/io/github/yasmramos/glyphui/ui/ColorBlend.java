package io.github.yasmramos.glyphui.ui;

/**
 * ARGB blending shared by the widgets that tint or fade a colour.
 *
 * <p>Interpolation is per channel in sRGB, matching the flat blends the widgets
 * already use to derive their resting colours, so a transition lands exactly on
 * the same value as an instantaneous swap.</p>
 *
 * <p>Package-private toolkit plumbing, not part of the frozen API.</p>
 */
final class ColorBlend {

    private ColorBlend() {
    }

    /**
     * Linear blend of {@code from} towards {@code to} by {@code t}, all four
     * channels interpolated (alpha included) so a translucent colour fades
     * instead of snapping to opaque.
     *
     * @param from the colour at {@code t == 0} (ARGB)
     * @param to   the colour at {@code t == 1} (ARGB)
     * @param t    the position in {@code [0, 1]}; values outside are clamped
     * @return the blended ARGB colour
     */
    static int lerp(int from, int to, float t) {
        if (t <= 0.0f) {
            return from;
        }
        if (t >= 1.0f) {
            return to;
        }
        int a = channel(from, to, t, 24);
        int r = channel(from, to, t, 16);
        int g = channel(from, to, t, 8);
        int b = channel(from, to, t, 0);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int channel(int from, int to, float t, int shift) {
        int start = (from >>> shift) & 0xFF;
        int end = (to >>> shift) & 0xFF;
        return Math.round(start + (end - start) * t);
    }

    /**
     * Linear blend of two byte-scale values (an alpha byte, a channel already
     * masked out) by {@code t}.
     *
     * @param from the value at {@code t == 0}
     * @param to   the value at {@code t == 1}
     * @param t    the position in {@code [0, 1]}; values outside are clamped
     * @return the blended value
     */
    static int lerpByte(int from, int to, float t) {
        if (t <= 0.0f) {
            return from;
        }
        if (t >= 1.0f) {
            return to;
        }
        return Math.round(from + (to - from) * t);
    }
}
