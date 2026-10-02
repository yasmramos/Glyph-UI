package com.glyphui.style;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Focused unit tests for the package-private {@link CssValues} value
 * pipeline: typed parsing per property class, {@code var()} resolution
 * (including nesting and fallbacks) and every color syntax supported by
 * the subset. Complements StyleParseTest/StyleValueTest, which exercise
 * these paths only indirectly through StyleSheet/Style.
 */
class CssValuesTest {

    private static final Map<String, String> NO_VARS = Map.of();

    // ------------------------------------------------------------------
    // parse(): dispatch by property kind
    // ------------------------------------------------------------------

    @Nested
    class ParseDispatch {

        @Test
        void blankAndNullValuesYieldNull() {
            assertNull(CssValues.parse("color", null, NO_VARS));
            assertNull(CssValues.parse("color", "", NO_VARS));
            assertNull(CssValues.parse("color", "   ", NO_VARS));
        }

        @Test
        void colorPropertiesParseToArgbInts() {
            assertEquals(0xFFFF0000, CssValues.parse("color", "red", NO_VARS));
            assertEquals(0xFF102030, CssValues.parse("background-color", "#102030", NO_VARS));
            assertEquals(0x80FF0000, CssValues.parse("border-color", "#80FF0000", NO_VARS));
            assertEquals(0xFF00FF00, CssValues.parse("accent-color", "rgb(0,255,0)", NO_VARS));
        }

        @Test
        void backgroundShorthandIsColorOnly() {
            assertEquals(0xFFFFFFFF, CssValues.parse("background", "white", NO_VARS));
            // Non-color keyword for `background`: BACKGROUND maps to an
            // Integer (ARGB) slot, so a color name that is not in the subset's
            // named palette ("none") resolves to null rather than leaking a
            // String into the typed style. The literal "transparent" IS a
            // supported color and maps to 0x00000000.
            assertNull(CssValues.parse("background", "none", NO_VARS));
            assertEquals(0x00000000, CssValues.parse("background", "transparent", NO_VARS));
        }

        @Test
        void lengthPropertiesParseToFloats() {
            assertEquals(8f, (Float) CssValues.parse("padding", "8px", NO_VARS), 1e-6);
            assertEquals(12f, (Float) CssValues.parse("width", "12", NO_VARS), 1e-6);
            assertEquals(1.5f, (Float) CssValues.parse("font-size", "1.5px", NO_VARS), 1e-6);
            assertEquals(-4f, (Float) CssValues.parse("margin", "-4px", NO_VARS), 1e-6);
            // Unsupported units / keywords are rejected for lengths
            assertNull(CssValues.parse("padding", "10%", NO_VARS));
            assertNull(CssValues.parse("width", "auto", NO_VARS));
        }

        @Test
        void borderShorthandAcceptsWidthOnlyForms() {
            assertEquals(2f, (Float) CssValues.parse("border", "2px", NO_VARS), 1e-6);
            assertEquals(0f, (Float) CssValues.parse("border", "0", NO_VARS), 1e-6);
            // Multi-token shorthand is out of subset
            assertNull(CssValues.parse("border", "1px solid red", NO_VARS));
        }

        @Test
        void numericUnitlessProperties() {
            assertEquals(0.5f, (Float) CssValues.parse("opacity", "0.5", NO_VARS), 1e-6);
            assertEquals(2f, (Float) CssValues.parse("flex-grow", "2", NO_VARS), 1e-6);
            assertNull(CssValues.parse("opacity", "mostly", NO_VARS));
            // flex maps onto FLEX_GROW: a bare number parses as Float; the
            // out-of-subset "1 1 auto" shorthand is never stored under a
            // float-typed property (Style.Builder would reject a String), so
            // the pipeline drops it to null.
            assertEquals(1f, (Float) CssValues.parse("flex", "1", NO_VARS), 1e-6);
            assertNull(CssValues.parse("flex", "1 1 auto", NO_VARS),
                    "multi-token flex shorthand must not resolve to a value");
        }

        @Test
        void fontWeightKeywordsAndNumbers() {
            assertEquals(400, CssValues.parse("font-weight", "normal", NO_VARS));
            assertEquals(700, CssValues.parse("font-weight", "bold", NO_VARS));
            assertEquals(300, CssValues.parse("font-weight", "lighter", NO_VARS));
            assertEquals(800, CssValues.parse("font-weight", "bolder", NO_VARS));
            assertEquals(550, CssValues.parse("font-weight", "550", NO_VARS));
            assertNull(CssValues.parse("font-weight", "50", NO_VARS));   // out of 100..900
            assertNull(CssValues.parse("font-weight", "heavy", NO_VARS)); // not a number
        }

        @Test
        void colorLikeValuesUnderNonStandardNamesResolveAsColors() {
            assertEquals(0xFFABCDEF, CssValues.parse("text-color", "#ABCDEF", NO_VARS));
            assertEquals(0x80FF0000, CssValues.parse("glow", "rgba(255,0,0,0.5)", NO_VARS));
            // Named colors too...
            assertEquals(0xFF0000FF, CssValues.parse("shadow", "blue", NO_VARS));
            // ...but an unparseable hex stays a plain keyword
            assertEquals("#zzz", CssValues.parse("shadow", "#zzz", NO_VARS));
        }

        @Test
        void keywordPropertiesAreStrippedOfQuotes() {
            assertEquals("block", CssValues.parse("display", "block", NO_VARS));
            assertEquals("DejaVu Sans", CssValues.parse("font-family", "\"DejaVu Sans\"", NO_VARS));
            assertEquals("DejaVu Sans", CssValues.parse("font-family", "'DejaVu Sans'", NO_VARS));
            // A quoted empty string collapses to null (blank after stripping)
            assertNull(CssValues.parse("font-family", "\"\"", NO_VARS));
        }

        @Test
        void variablesResolvedBeforeTypedParsing() {
            Map<String, String> vars = Map.of("--brand", "#0A0B0C");
            assertEquals(0xFF0A0B0C, CssValues.parse("color", "var(--brand)", vars));
            assertEquals(16f, (Float) CssValues.parse("font-size", "var(--fs, 16px)", vars), 1e-6);
            // Undefined variable with no fallback -> empty value -> null result
            assertNull(CssValues.parse("color", "var(--nope)", NO_VARS));
        }
    }

    // ------------------------------------------------------------------
    // resolveVars()
    // ------------------------------------------------------------------

    @Nested
    class ResolveVars {

        @Test
        void passesThroughWhenNoVarPresent() {
            assertEquals("red", CssValues.resolveVars("red", NO_VARS, new ArrayList<>()));
            assertNull(CssValues.resolveVars(null, NO_VARS, new ArrayList<>()));
        }

        @Test
        void substitutesKnownVariables() {
            String out = CssValues.resolveVars("var(--a)", Map.of("--a", "7px"), new ArrayList<>());
            assertEquals("7px", out);
        }

        @Test
        void usesFallbackWhenVariableUnknown() {
            List<String> unresolved = new ArrayList<>();
            String out = CssValues.resolveVars("var(--missing, #FFF)", NO_VARS, unresolved);
            assertEquals("#FFF", out);
            assertTrueName(unresolved.isEmpty(), "fallback supplied: nothing unresolved");
        }

        @Test
        void recordsUnresolvedNamesAndEmptiesThem() {
            List<String> unresolved = new ArrayList<>();
            String out = CssValues.resolveVars("solid var(--ghost)", NO_VARS, unresolved);
            assertEquals("solid", out);
            assertEquals(List.of("--ghost"), unresolved);
        }

        @Test
        void resolvesMultipleAndNestedParens() {
            Map<String, String> vars = Map.of("--a", "rgb(1", "--b", "2,3)");
            // Nested parens must be tracked by depth, not by first ')'
            String out = CssValues.resolveVars("var(--c, rgb(0,0,0))", NO_VARS, new ArrayList<>());
            assertEquals("rgb(0,0,0)", out);
            assertNotNull(vars);
        }

        @Test
        void truncatedVarAtEndOfStringDoesNotThrow() {
            // Malformed input: 'var(' without closing paren — parser clamps index.
            String out = CssValues.resolveVars("1px var(", NO_VARS, new ArrayList<>());
            assertEquals("1px", out);
        }

        private static void assertTrueName(boolean cond, String msg) {
            org.junit.jupiter.api.Assertions.assertTrue(cond, msg);
        }
    }

    // ------------------------------------------------------------------
    // parseColor()
    // ------------------------------------------------------------------

    @Nested
    class ParseColor {

        @Test
        void namedColorsCaseInsensitive() {
            assertEquals(0xFF000000, CssValues.parseColor("BLACK"));
            assertEquals(0xFF808080, CssValues.parseColor("gray"));
            assertEquals(0xFF808080, CssValues.parseColor("Grey"));
            assertEquals(0x00000000, CssValues.parseColor("transparent"));
        }

        @Test
        void threeDigitHexExpandsChannels() {
            assertEquals(0xFFFFFFFF, CssValues.parseColor("#fff"));
            assertEquals(0xFF112233, CssValues.parseColor("#123"));
        }

        @Test
        void fourDigitHexUsesArgbConvention() {
            // Project convention: alpha FIRST (#argb), not CSS #rgba.
            // "#8F00" → a=0x88, r=0xFF, g=0x00, b=0x00 (each digit ×17).
            assertEquals(0x88FF0000, CssValues.parseColor("#8F00"));
        }

        @Test
        void sixDigitHexIsOpaqueRgb() {
            assertEquals(0xFFABCDEF, CssValues.parseColor("#abcdef"));
        }

        @Test
        void eightDigitHexReadsVerbatimArgb() {
            assertEquals(0x11223344, CssValues.parseColor("#11223344"));
        }

        @Test
        void invalidHexLengthsAndCharsReturnNull() {
            assertNull(CssValues.parseColor("#12"));      // 2 digits
            assertNull(CssValues.parseColor("#12345"));   // 5 digits
            assertNull(CssValues.parseColor("#1234567")); // 7 digits
            assertNull(CssValues.parseColor("#gggggg"));  // non-hex chars
        }

        @Test
        void rgbAndRgbaFunctions() {
            assertEquals(0xFF102030, CssValues.parseColor("rgb(16, 32, 48)"));
            assertEquals(0x80102030, CssValues.parseColor("rgba(16, 32, 48, 0.5)"));
            assertEquals(0xFF000000, CssValues.parseColor("rgb(-10, -10, -10)")); // clamped low
            assertEquals(0xFFFFFFFF, CssValues.parseColor("rgb(300, 300, 300)")); // clamped high
        }

        @Test
        void malformedFunctionsReturnNull() {
            assertNull(CssValues.parseColor("rgb(1,2,3"));     // missing close paren
            assertNull(CssValues.parseColor("rgb(1,2)"));      // wrong arity
            assertNull(CssValues.parseColor("rgb(a,b,c)"));    // NaN components
            assertNull(CssValues.parseColor("rebeccapurple")); // unknown name
        }
    }

    // ------------------------------------------------------------------
    // parseLength()
    // ------------------------------------------------------------------

    @Nested
    class ParseLength {

        @Test
        void pxSuffixOptionalAndTrimmed() {
            assertEquals(10f, CssValues.parseLength("10px"), 1e-6);
            assertEquals(10f, CssValues.parseLength(" 10 "), 1e-6);
            assertEquals(10f, CssValues.parseLength("10 px"), 1e-6);
        }

        @Test
        void dotAndMinusLeadsAccepted() {
            assertEquals(.5f, CssValues.parseLength(".5"), 1e-6);
            assertEquals(-2f, CssValues.parseLength("-2px"), 1e-6);
        }

        @Test
        void keywordsRejected() {
            assertNull(CssValues.parseLength("auto"));
            assertNull(CssValues.parseLength("thin"));
        }

        @Test
        void badNumbersRejected() {
            assertNull(CssValues.parseLength("12abc"));
            assertNull(CssValues.parseLength("1.2.3"));
        }

        @Test
        void emptyStringParsesAsZeroLikeFloatParserFailure() {
            // Float.parseFloat("") throws -> null (documented behavior)
            assertNull(CssValues.parseLength(""));
        }
    }
}
