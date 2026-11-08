package io.github.yasmramos.glyphui.graphics;

import io.github.yasmramos.glyphui.graphics.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the built-in modern-web palettes. They pin the
 * properties that make the look read as "modern" rather than the exact ARGB
 * values, so the palette can be tuned without rewriting the tests while the
 * design intent stays enforced:
 *
 * <ul>
 *   <li>neutral ramps (cool grey, only a slight blue tint),</li>
 *   <li>a saturated accent that stands out from the neutrals,</li>
 *   <li>borders that read as hairlines rather than the old heavy grey,</li>
 *   <li>a surface ramp that keeps controls distinguishable from the page,</li>
 *   <li>readable text contrast in both modes.</li>
 * </ul>
 */
class ModernPaletteTest {

    /**
     * A cool neutral ramp (Tailwind's "slate") carries a slight blue tint, so
     * a small non-zero chroma is expected; a saturated brand colour is not.
     */
    private static final int NEUTRAL_CHROMA = 45;

    @Test
    void neutralsAreCoolAndLowChromaInBothModes() {
        for (Theme t : new Theme[] {Theme.light(), Theme.dark()}) {
            assertLowChroma(t.getBackgroundColor(), "background");
            assertLowChroma(t.getForegroundColor(), "foreground");
            assertLowChroma(t.getControlNormalColor(), "control surface");
            assertLowChroma(t.getBorderColor(), "border");
        }
    }

    @Test
    void accentIsSaturatedAndDistinctFromTheNeutrals() {
        for (Theme t : new Theme[] {Theme.light(), Theme.dark()}) {
            int accent = t.getAccentColor();
            assertTrue(chroma(accent) >= 100,
                    "the accent must be clearly saturated, got " + hex(accent));
            assertNotEquals(accent, t.getBackgroundColor());
            assertNotEquals(accent, t.getControlNormalColor());
        }
    }

    @Test
    void bordersAreHairlinesNotHeavyGrey() {
        // The old palettes used mid-grey borders (#969696 light / #787878
        // dark) that sat ~225 channel-steps from the surface they outlined.
        // A modern hairline sits much closer to that surface.
        for (Theme t : new Theme[] {Theme.light(), Theme.dark()}) {
            int border = t.getBorderColor();
            int surface = t.getControlNormalColor();
            assertTrue(channelDistance(border, surface) < 130,
                    "border " + hex(border) + " should hug the surface "
                            + hex(surface) + " as a hairline, not a heavy grey");
            assertTrue(chroma(border) <= NEUTRAL_CHROMA,
                    "border " + hex(border) + " should stay neutral");
        }
    }

    @Test
    void controlSurfacesStayDistinguishableFromThePage() {
        for (Theme t : new Theme[] {Theme.light(), Theme.dark()}) {
            assertNotEquals(t.getControlNormalColor(), t.getBackgroundColor(),
                    "a control surface must not vanish into the page");
        }
    }

    @Test
    void textHasReadableContrastAgainstItsSurface() {
        for (Theme t : new Theme[] {Theme.light(), Theme.dark()}) {
            double page = contrastRatio(t.getForegroundColor(), t.getBackgroundColor());
            assertTrue(page >= 7.0,
                    "foreground on page should reach AAA, got " + page);
            double control = contrastRatio(t.getControlTextColor(), t.getControlNormalColor());
            assertTrue(control >= 4.5,
                    "control text should reach AA, got " + control);
        }
    }

    @Test
    void disabledTextIsDimmerButStillLegible() {
        for (Theme t : new Theme[] {Theme.light(), Theme.dark()}) {
            double enabled = contrastRatio(t.getControlTextColor(), t.getControlNormalColor());
            double disabled = contrastRatio(t.getControlTextDisabledColor(),
                    t.getControlNormalColor());
            assertTrue(disabled < enabled, "disabled text must read as dimmer");
            assertTrue(disabled >= 1.6, "disabled text must remain visible");
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static int red(int argb) { return (argb >> 16) & 0xFF; }
    private static int green(int argb) { return (argb >> 8) & 0xFF; }
    private static int blue(int argb) { return argb & 0xFF; }

    /** Max channel minus min channel: 0 = grey, 255 = fully saturated. */
    private static int chroma(int argb) {
        int max = Math.max(red(argb), Math.max(green(argb), blue(argb)));
        int min = Math.min(red(argb), Math.min(green(argb), blue(argb)));
        return max - min;
    }

    private static void assertLowChroma(int argb, String what) {
        assertTrue(chroma(argb) <= NEUTRAL_CHROMA,
                what + " " + hex(argb) + " should be a neutral, not a tinted colour");
    }

    private static int channelDistance(int a, int b) {
        return Math.abs(red(a) - red(b))
                + Math.abs(green(a) - green(b))
                + Math.abs(blue(a) - blue(b));
    }

    /** WCAG relative-luminance contrast ratio (1.0 … 21.0). */
    private static double contrastRatio(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double lighter = Math.max(la, lb);
        double darker = Math.min(la, lb);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double luminance(int argb) {
        double r = linear(red(argb) / 255.0);
        double g = linear(green(argb) / 255.0);
        double b = linear(blue(argb) / 255.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linear(double c) {
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static String hex(int argb) {
        return String.format("#%08X", argb);
    }
}
