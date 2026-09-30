package com.glyphui.graphics;

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
}
