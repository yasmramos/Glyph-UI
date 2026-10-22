package io.github.yasmramos.glyphui.style;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.SkijaTestSupport;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.ComponentState;
import io.github.yasmramos.glyphui.ui.Panel;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the widget-side style resolution helpers of
 * {@link io.github.yasmramos.glyphui.ui.Component}: {@code resolveIntStyle},
 * {@code resolveFloatStyle} and {@code resolveFont}.
 *
 * <p>Each helper implements the documented cascade: a declaration that
 * reaches the computed style (stylesheet rule, pseudo-class re-cascade or
 * inline style) wins over the per-instance explicit override, which in turn
 * wins over the theme default. These tests pin all four scenarios required
 * by the Phase 1 checklist: empty stylesheet, applicable stylesheet,
 * explicit override and the {@code :hover} pseudo-class.</p>
 */
class StyleResolutionContractTest extends TestBase {

    private static final int CSS_COLOR = 0xFF00AA00;   // stylesheet value (int)
    private static final int OVERRIDE_COLOR = 0xFF0000AA; // programmatic value
    private static final float CSS_LENGTH = 24f;       // stylesheet value (float)
    private static final float OVERRIDE_LENGTH = 7f;   // programmatic value

    /** Concrete Button exposing the protected resolution helpers to tests. */
    private static class ProbingButton extends Button {
        ProbingButton(String text) {
            super(text);
        }

        int probeInt(StyleProperty property, Integer explicitOverride, int themeDefault) {
            return resolveIntStyle(property, explicitOverride, themeDefault);
        }

        float probeFloat(StyleProperty property, Float explicitOverride, float themeDefault) {
            return resolveFloatStyle(property, explicitOverride, themeDefault);
        }

        io.github.humbleui.skija.Font probeFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole role) {
            return resolveFont(role);
        }
    }

    private Panel root;
    private ProbingButton button;
    private StyleSheet sheet;

    @BeforeEach
    void setUp() {
        root = new Panel(0, 0, 400, 300);
        button = new ProbingButton("click");
        root.add(button);
        sheet = StyleSheet.empty();
    }

    /** Re-cascades the current stylesheet against the tree (what Application does per frame). */
    private void applySheet() {
        StyleEngine.apply(root, sheet);
    }

    // ------------------------------------------------------------------
    // resolveIntStyle
    // ------------------------------------------------------------------

    @Test
    void resolveIntStyleWithEmptyStylesheetFallsBackToExplicitOverride() {
        applySheet(); // empty stylesheet: nothing reaches the computed style
        assertFalse(button.getComputedStyle().has(StyleProperty.BACKGROUND));

        assertEquals(OVERRIDE_COLOR,
                button.probeInt(StyleProperty.BACKGROUND, OVERRIDE_COLOR, 0xFF111111),
                "with no stylesheet declaration the explicit override must win over the theme");
    }

    @Test
    void resolveIntStyleWithoutOverrideUsesThemeDefault() {
        applySheet();

        assertEquals(0xFF111111,
                button.probeInt(StyleProperty.BACKGROUND, null, 0xFF111111),
                "a null override with no stylesheet declaration falls through to the theme default");
    }

    @Test
    void resolveIntStyleWithApplicableStylesheetBeatsOverrideAndTheme() {
        sheet = StyleSheet.parse("button { background: #00AA00; }");
        applySheet();

        assertEquals(CSS_COLOR,
                button.probeInt(StyleProperty.BACKGROUND, OVERRIDE_COLOR, 0xFF111111),
                "a matching stylesheet rule must win over both the override and the theme");
    }

    @Test
    void resolveIntStyleHonoursHoverPseudoClassReCascade() {
        sheet = StyleSheet.parse("""
                button { background: #001122; }
                button:hover { background: #00AA00; }
                """);

        button.setState(ComponentState.IDLE);
        applySheet();
        assertEquals(0xFF001122,
                button.probeInt(StyleProperty.BACKGROUND, null, 0xFF111111),
                "idle state must use the base rule");

        button.setState(ComponentState.HOVER);
        applySheet();
        assertEquals(CSS_COLOR,
                button.probeInt(StyleProperty.BACKGROUND, null, 0xFF111111),
                ":hover rule must win after the state change is re-cascaded");

        // The engine only re-evaluates on apply(): a stale cascade must NOT
        // leak the hover colour before the next pass (documents when the
        // contract takes effect — once per frame, like Application does).
    }

    // ------------------------------------------------------------------
    // resolveFloatStyle
    // ------------------------------------------------------------------

    @Test
    void resolveFloatStyleWithEmptyStylesheetFallsBackToExplicitOverride() {
        applySheet();
        assertFalse(button.getComputedStyle().has(StyleProperty.BORDER_RADIUS));

        assertEquals(OVERRIDE_LENGTH,
                button.probeFloat(StyleProperty.BORDER_RADIUS, OVERRIDE_LENGTH, 3f),
                "override beats theme when the stylesheet declares nothing");
    }

    @Test
    void resolveFloatStyleWithoutOverrideUsesThemeDefault() {
        applySheet();

        assertEquals(3f,
                button.probeFloat(StyleProperty.BORDER_RADIUS, null, 3f));
    }

    @Test
    void resolveFloatStyleWithApplicableStylesheetBeatsOverrideAndTheme() {
        sheet = StyleSheet.parse("button { border-radius: 24px; }");
        applySheet();

        assertEquals(CSS_LENGTH,
                button.probeFloat(StyleProperty.BORDER_RADIUS, OVERRIDE_LENGTH, 3f),
                "a matching px rule must win over both the override and the theme");
    }

    @Test
    void resolveFloatStyleHonoursHoverPseudoClassReCascade() {
        sheet = StyleSheet.parse("""
                button { border-radius: 2px; }
                button:hover { border-radius: 24px; }
                """);

        button.setState(ComponentState.IDLE);
        applySheet();
        assertEquals(2f, button.probeFloat(StyleProperty.BORDER_RADIUS, null, 3f));

        button.setState(ComponentState.HOVER);
        applySheet();
        assertEquals(CSS_LENGTH,
                button.probeFloat(StyleProperty.BORDER_RADIUS, null, 3f),
                ":hover border-radius must win after re-cascade");
    }

    // ------------------------------------------------------------------
    // resolveFont
    // ------------------------------------------------------------------

    @Test
    void resolveFontWithoutDeclarationReturnsThemeFont() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to build fonts");
        applySheet();

        io.github.yasmramos.glyphui.graphics.Theme theme = io.github.yasmramos.glyphui.graphics.Theme.current();
        io.github.humbleui.skija.Font font =
                button.probeFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON);

        assertNotNull(font);
        assertSame(theme.getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON), font,
                "with no font-* declarations the theme role font must be returned as-is");
    }

    @Test
    void resolveFontWithDeclaredSizeBuildsLargerFontThanThemeDefault() {
        Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library required to build fonts from CSS sizes");
        sheet = StyleSheet.parse("button { font-size: 32px; }");
        applySheet();

        io.github.humbleui.skija.Font font =
                button.probeFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON);
        assertNotNull(font);

        io.github.yasmramos.glyphui.graphics.Theme theme = io.github.yasmramos.glyphui.graphics.Theme.current();
        float themeSize = theme.getFontSize(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON);
        try (io.github.humbleui.skija.Font probe =
                     new io.github.humbleui.skija.Font(
                             io.github.yasmramos.glyphui.graphics.FontManager.resolveTypeface(null,
                                     io.github.humbleui.skija.FontStyle.NORMAL),
                             32f)) {
            // Same size => same metrics on the same typeface: proves the CSS
            // 32px won over the theme size without touching Skija internals.
            io.github.humbleui.skija.FontMetrics cssMetrics = font.getMetrics();
            io.github.humbleui.skija.FontMetrics expectedMetrics = probe.getMetrics();
            assertEquals(expectedMetrics.getDescent(), cssMetrics.getDescent(), 1e-4f,
                    "descent must match a 32px font (CSS font-size applied)");
            io.github.humbleui.skija.FontMetrics themeMetrics = theme.getFont(
                    io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON).getMetrics();
            assertTrue(cssMetrics.getDescent() > themeMetrics.getDescent() || themeSize == 32f,
                    "the declared size must differ from the theme default unless they coincide");
        }
    }

    @Test
    void resolveFontWithDeclaredFamilyNeverReturnsNullEvenForUnknownFamily() {
        Assumptions.assumeTrue(SkijaTestSupport.isAvailable(),
                "Skija native library required to resolve typefaces");
        sheet = StyleSheet.parse("button { font-family: \"Totally Missing Font\"; font-size: 18px; }");
        applySheet();

        io.github.humbleui.skija.Font font =
                button.probeFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BUTTON);

        assertNotNull(font,
                "an unknown family must fall back to a usable font, never null/NPE");
    }
}
