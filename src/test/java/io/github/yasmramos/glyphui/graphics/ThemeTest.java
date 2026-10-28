package io.github.yasmramos.glyphui.graphics;

import io.github.yasmramos.glyphui.graphics.Theme;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Unit tests for the central {@link Theme}: light/dark palettes, global
 * current-theme switching and builder-based customization.
 */
public class ThemeTest {

    private Theme original;

    @BeforeEach
    public void saveTheme() {
        original = Theme.current();
    }

    @AfterEach
    public void restoreTheme() {
        Theme.setCurrent(original);
    }

    @Test
    public void testLightAndDarkPalettesDiffer() {
        Theme light = Theme.light();
        Theme dark = Theme.dark();

        // Light background must be brighter than the dark one.
        int lightRed = (light.getBackgroundColor() >> 16) & 0xFF;
        int darkRed = (dark.getBackgroundColor() >> 16) & 0xFF;
        assertNotEquals(light.getBackgroundColor(), dark.getBackgroundColor());
        assertEquals(true, lightRed > darkRed, "Light bg should be brighter");

        // Both modes expose an accent color used for focus rings.
        assertNotEquals(0, light.getAccentColor());
        assertNotEquals(0, dark.getAccentColor());
    }

    @Test
    public void testCurrentCanBeSwitched() {
        Theme.setCurrent(Theme.light());
        assertSame(Theme.light(), Theme.current());

        Theme.setCurrent(Theme.dark());
        assertSame(Theme.dark(), Theme.current());
    }

    @Test
    public void testBuilderOverridesColors() {
        int customAccent = 0xFFFF00FF; // opaque yellow
        Theme custom = Theme.builder()
                .mode(Theme.Mode.LIGHT)
                .accentColor(customAccent)
                .build();

        assertEquals(customAccent, custom.getAccentColor());
        // Untouched fields keep the mode defaults.
        assertEquals(Theme.light().getPadding(), custom.getPadding());
    }

    @Test
    public void testFontsAreProvidedPerRole() {
        Theme theme = Theme.current();
        assertNotNull(theme.getFont(Theme.FontRole.BODY));
        assertNotNull(theme.getFont(Theme.FontRole.BUTTON));
        assertNotNull(theme.getFont(Theme.FontRole.HEADING));
        assertEquals(true, theme.getFontSize(Theme.FontRole.HEADING)
                >= theme.getFontSize(Theme.FontRole.BODY));
    }

    // ------------------------------------------------------------------
    // Block 5: value semantics (5.13), builder ordering (5.14),
    // per-role font sizes (5.15)
    // ------------------------------------------------------------------

    @Test
    public void testThemesCompareByValue() {
        // Two independently built themes with identical tokens are equal
        // and hash the same — real value objects, not identity-based.
        Theme a = Theme.builder().mode(Theme.Mode.DARK).accentColor(0xFFFF0000).build();
        Theme b = Theme.builder().mode(Theme.Mode.DARK).accentColor(0xFFFF0000).build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        // Built-in singletons compare by value too.
        assertEquals(Theme.dark(), Theme.builder(Theme.Mode.DARK).build());
        assertNotEquals(Theme.light(), Theme.dark());
        assertNotEquals(a, Theme.dark());
        assertNotEquals(null, a);
        assertNotEquals(a, "not a theme");
    }

    @Test
    public void testBuilderWithModeSeedsPaletteBeforeOverrides() {
        int customAccent = 0xFFFF0000;
        // Theme.builder(Mode) applies the palette first, so custom setters
        // can never be clobbered by a later mode() call (5.14).
        Theme custom = Theme.builder(Theme.Mode.LIGHT)
                .accentColor(customAccent)
                .build();
        assertEquals(customAccent, custom.getAccentColor());
        assertEquals(Theme.light().getBackgroundColor(), custom.getBackgroundColor());
    }

    @Test
    public void testModeAfterOverridesReseedsPalette() {
        int customAccent = 0xFFFF0000;
        // Documented gotcha kept honest: calling mode() last re-seeds every
        // color token, discarding earlier custom colors.
        Theme legacy = Theme.builder().accentColor(customAccent).mode(Theme.Mode.LIGHT).build();
        assertEquals(Theme.light().getAccentColor(), legacy.getAccentColor());
    }

    @Test
    public void testThemeCarriesPerRoleFontSizes() {
        Theme base = Theme.dark();
        float defaultBody = FontManager.getDefaultFontSize(Theme.FontRole.BODY);
        // Without an override the theme defers to FontManager defaults.
        assertEquals(defaultBody, base.getFontSize(Theme.FontRole.BODY), 1e-6);

        Theme scaled = Theme.builder(Theme.Mode.DARK)
                .fontSize(Theme.FontRole.BODY, 20f)
                .build();
        assertEquals(20f, scaled.getFontSize(Theme.FontRole.BODY), 1e-6);
        // Other roles keep falling back to FontManager.
        assertEquals(FontManager.getDefaultFontSize(Theme.FontRole.HEADING),
                scaled.getFontSize(Theme.FontRole.HEADING), 1e-6);

        // Font-size overrides participate in value equality (5.13).
        assertNotEquals(scaled, base);
        Theme sameScale = Theme.builder(Theme.Mode.DARK)
                .fontSize(Theme.FontRole.BODY, 20f)
                .build();
        assertEquals(scaled, sameScale);
        assertEquals(scaled.hashCode(), sameScale.hashCode());

        // toBuilder round-trips the overrides; null clears them.
        Theme derived = scaled.toBuilder().fontSize(Theme.FontRole.BODY, null).build();
        assertEquals(defaultBody, derived.getFontSize(Theme.FontRole.BODY), 1e-6);
        assertEquals(base, derived);
    }
}
