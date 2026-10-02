package com.glyphui.style;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Focused unit tests for the {@link Style} value object and its {@link Style.Builder}:
 * typed accessors, cascade merge semantics (inheritFrom / overrideWith), raw-value
 * handling (resolveVars / withoutRawValues) and builder type validation.
 */
public class StyleValueTest {

    private static Style sample() {
        return Style.builder()
                .color(StyleProperty.COLOR, 0xFF112233)
                .length(StyleProperty.FONT_SIZE, 14.0f)
                .keyword(StyleProperty.FONT_FAMILY, "Inter")
                .build();
    }

    // ------------------------------------------------------------------
    // Typed accessors
    // ------------------------------------------------------------------

    @Test
    public void gettersReturnTypedValuesAndDefaults() {
        Style s = sample();
        assertEquals(0xFF112233, s.getInt(StyleProperty.COLOR, -1));
        assertEquals(14.0f, s.getFloat(StyleProperty.FONT_SIZE, 0f), 1e-6);
        assertEquals("Inter", s.getString(StyleProperty.FONT_FAMILY, "?"));

        // Unset properties fall back to defaults.
        assertEquals(-7, s.getInt(StyleProperty.BACKGROUND, -7));
        assertEquals(3.5f, s.getFloat(StyleProperty.PADDING, 3.5f), 1e-6);
        assertEquals("dflt", s.getString(StyleProperty.DISPLAY, "dflt"));

        // Wrong-type reads return the default instead of throwing.
        assertEquals(0, s.getInt(StyleProperty.FONT_SIZE, 0));
        assertEquals(0f, s.getFloat(StyleProperty.COLOR, 0f), 1e-6);
        assertEquals(null, s.get(StyleProperty.MARGIN));
    }

    @Test
    public void hasIsEmptyAndProperties() {
        assertTrue(Style.EMPTY.isEmpty());
        assertFalse(sample().isEmpty());
        assertTrue(sample().has(StyleProperty.COLOR));
        assertFalse(sample().has(StyleProperty.GAP));
        assertEquals(3, sample().properties().size());
        assertTrue(sample().properties().contains(StyleProperty.FONT_FAMILY));
    }

    @Test
    public void getReturnsRawObjectOrNull() {
        Style s = sample();
        assertEquals(0xFF112233, s.get(StyleProperty.COLOR));
        assertEquals(null, s.get(StyleProperty.HEIGHT));
    }

    // ------------------------------------------------------------------
    // parseInline
    // ------------------------------------------------------------------

    @Test
    public void parseInlineHandlesNullBlankAndJunk() {
        assertSame(Style.EMPTY, Style.parseInline(null, Map.of()));
        assertSame(Style.EMPTY, Style.parseInline("   ", Map.of()));
        assertSame(Style.EMPTY, Style.parseInline("", null));

        // Declarations without a colon or with unknown/variable names are skipped.
        Style junk = Style.parseInline("noColon; --var: 1; color: ; unknown-prop: red", Map.of());
        assertTrue(junk.isEmpty(), "only supported, well-formed declarations survive");
    }

    @Test
    public void parseInlineResolvesKnownDeclarationsAndVariables() {
        Style s = Style.parseInline("color: #ff0000; font-size: 20px; padding: 8px", Map.of());
        assertEquals(0xFFFF0000, s.getInt(StyleProperty.COLOR, 0));
        assertEquals(20.0f, s.getFloat(StyleProperty.FONT_SIZE, 0f), 1e-6);
        assertEquals(8.0f, s.getFloat(StyleProperty.PADDING, 0f), 1e-6);

        Style withVars = Style.parseInline("color: var(--brand)",
                Map.of("--brand", "#00ff00"));
        assertEquals(0xFF00FF00, withVars.getInt(StyleProperty.COLOR, 0));
    }

    // ------------------------------------------------------------------
    // Cascade merges
    // ------------------------------------------------------------------

    @Test
    public void inheritFromCopiesOnlyInheritedProperties() {
        Style parent = Style.builder()
                .color(StyleProperty.COLOR, 0xFFAAAAAA)
                .length(StyleProperty.FONT_SIZE, 18f)
                .color(StyleProperty.BACKGROUND, 0xFFBBBBBB) // non-inherited
                .build();
        Style child = Style.builder()
                .length(StyleProperty.FONT_SIZE, 12f) // child overrides
                .build();

        Style merged = child.inheritFrom(parent);
        assertEquals(0xFFAAAAAA, merged.getInt(StyleProperty.COLOR, 0));
        assertEquals(12.0f, merged.getFloat(StyleProperty.FONT_SIZE, 0f), 1e-6,
                "child declaration must win over inherited value");
        assertFalse(merged.has(StyleProperty.BACKGROUND),
                "non-inherited properties never leak through inheritance");
    }

    @Test
    public void inheritFromNullOrEmptyReturnsSelfWithoutRawValues() {
        Style s = sample();
        assertSame(s, s.inheritFrom(null));
        assertSame(s, s.inheritFrom(Style.EMPTY));
    }

    @Test
    public void overrideWithMergesLaterWins() {
        Style base = Style.builder()
                .color(StyleProperty.COLOR, 0xFF111111)
                .length(StyleProperty.PADDING, 4f)
                .build();
        Style over = Style.builder()
                .color(StyleProperty.COLOR, 0xFF222222)
                .build();

        Style merged = base.overrideWith(over);
        assertEquals(0xFF222222, merged.getInt(StyleProperty.COLOR, 0));
        assertEquals(4.0f, merged.getFloat(StyleProperty.PADDING, 0f), 1e-6);

        assertSame(base, base.overrideWith(null));
        assertSame(base, base.overrideWith(Style.EMPTY));
    }

    @Test
    public void withoutRawValuesIsIdentityWhenNoCarrier() {
        Style s = sample();
        assertSame(s, s.withoutRawValues());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void resolveVarsFoldsRawDeclarationsIntoTypedValues() {
        Map<StyleProperty, Object> raw = new EnumMap<>(StyleProperty.class);
        raw.put(StyleProperty.RAW_VALUES,
                Map.of(StyleProperty.BACKGROUND, "var(--accent)"));
        Style withRaw = Style.of(raw);
        assertFalse(withRaw.withoutRawValues() == withRaw,
                "carrier present → withoutRawValues returns a copy");

        Style resolved = withRaw.resolveVars(Map.of("--accent", "#112233"));
        assertEquals(0xFF112233, resolved.getInt(StyleProperty.BACKGROUND, 0));
        assertFalse(resolved.has(StyleProperty.RAW_VALUES));

        // Styles without a raw carrier are returned unchanged (same values,
        // rebuilt through the Builder — Style has no equals(), so identity of
        // the entries is verified property by property).
        assertUnchanged(sample(), sample().resolveVars(Map.of()));
        assertUnchanged(sample(), sample().resolveVars(null));
    }

    private static void assertUnchanged(Style reference, Style result) {
        // Values are compared with equals(): resolveVars rebuilds the style
        // through the Builder, so boxed primitives may be re-boxed and fail
        // identity even when numerically identical.
        assertEquals(reference.properties(), result.properties());
        for (StyleProperty p : reference.properties()) {
            assertEquals(reference.get(p), result.get(p), "value differs for " + p);
        }
    }

    @Test
    public void toStringListsLowercasedPropertyNames() {
        String str = sample().toString();
        assertTrue(str.startsWith("Style{"), str);
        assertTrue(str.contains("color="), str);
        assertTrue(str.contains("font-size="), str);
        assertTrue(str.contains("font-family="), str);
    }

    // ------------------------------------------------------------------
    // Builder type validation
    // ------------------------------------------------------------------

    @Test
    public void setRejectsMismatchedTypesAndAcceptsWidening() {
        Style.Builder b = Style.builder();
        assertThrows(IllegalArgumentException.class,
                () -> b.set(StyleProperty.COLOR, "red"));     // String into Integer slot
        assertThrows(IllegalArgumentException.class,
                () -> b.set(StyleProperty.DISPLAY, 42));      // int into String slot

        b.set(StyleProperty.FONT_SIZE, 12);                   // Integer tolerated for Float props
        // Builder.set stores the value as-is (widening is only validated,
        // not normalized); normalization happens in setInternally. The
        // strict typed getters therefore return the default here.
        assertEquals(0f, b.build().getFloat(StyleProperty.FONT_SIZE, 0f), 1e-6);
        assertEquals(12, b.build().get(StyleProperty.FONT_SIZE));

        // Long is accepted for Integer props, but stored as-is (Builder.set
        // only validates the type domain; normalization to Integer happens in
        // setInternally). getInt's strict instanceof Integer then returns the
        // default — documented behavior of the typed getters. Use a fresh
        // builder: Builder keeps one mutable map, so reusing `b` here would
        // let the later FONT_SIZE set below overwrite this key in the same map.
        Style.Builder colors = Style.builder();
        colors.set(StyleProperty.COLOR, 12L);                 // Long accepted for Integer props
        assertEquals(0, colors.build().getInt(StyleProperty.COLOR, 0));
        assertEquals(12L, colors.build().get(StyleProperty.COLOR));

        // Double accepted for Float props — stored as-is, same as above.
        Style.Builder doubles = Style.builder();
        doubles.set(StyleProperty.FONT_SIZE, 20.0);
        assertEquals(0f, doubles.build().getFloat(StyleProperty.FONT_SIZE, 0f), 1e-6);
        assertEquals(20.0, doubles.build().get(StyleProperty.FONT_SIZE));

        // Null values are silently ignored.
        b.set(StyleProperty.PADDING, null);
        assertFalse(b.build().has(StyleProperty.PADDING));
    }

    @Test
    public void clearRemovesEntries() {
        Style.Builder b = Style.builder();
        b.set(StyleProperty.PADDING, 6f);
        assertTrue(b.build().has(StyleProperty.PADDING));
        b.clear(StyleProperty.PADDING);
        assertFalse(b.build().has(StyleProperty.PADDING));
    }

    @Test
    public void putAllAbsorbsAndOverwrites() {
        Style a = Style.builder().length(StyleProperty.PADDING, 1f).build();
        Style b = Style.builder().length(StyleProperty.PADDING, 2f)
                .color(StyleProperty.COLOR, 0xFF0000FF).build();
        Style merged = Style.builder().putAll(a).putAll(b).build();
        assertEquals(2.0f, merged.getFloat(StyleProperty.PADDING, 0f), 1e-6);
        assertEquals(0xFF0000FF, merged.getInt(StyleProperty.COLOR, 0));

        // Null argument is a no-op.
        assertNotSame(null, Style.builder().putAll(null).build());
    }

    @Test
    public void setIntAndSetFloatConvertPerExpectedType() {
        Style.Builder b = Style.builder();
        b.setInt(StyleProperty.FONT_WEIGHT, 700);           // Integer prop
        b.setInt(StyleProperty.FONT_SIZE, 16);              // int widened to float prop
        b.setFloat(StyleProperty.PADDING, 8.0f);            // Float prop
        assertEquals(700, b.build().getInt(StyleProperty.FONT_WEIGHT, 0));
        // Builder is a single mutable map: assert the narrowed result BEFORE
        // overwriting FONT_WEIGHT with the next call (last write wins).
        b.setFloat(StyleProperty.FONT_WEIGHT, 400.0f);      // integral float narrowed to int prop
        Style s = b.build();
        assertEquals(16.0f, s.getFloat(StyleProperty.FONT_SIZE, 0f), 1e-6);
        assertEquals(8.0f, s.getFloat(StyleProperty.PADDING, 0f), 1e-6);
        assertEquals(400, s.getInt(StyleProperty.FONT_WEIGHT, 0));

        Style.Builder bad = Style.builder();
        assertThrows(IllegalArgumentException.class,
                () -> bad.setInt(StyleProperty.DISPLAY, 3));       // String prop
        assertThrows(IllegalArgumentException.class,
                () -> bad.setFloat(StyleProperty.DISPLAY, 3.0f));  // String prop
        assertThrows(IllegalArgumentException.class,
                () -> bad.setFloat(StyleProperty.FONT_WEIGHT, 3.5f)); // fractional into int prop
    }

    @Test
    public void ofWrapsProvidedMap() {
        Map<StyleProperty, Object> map = new EnumMap<>(StyleProperty.class);
        map.put(StyleProperty.OPACITY, 0.5f);
        Style s = Style.of(map);
        assertEquals(0.5f, s.getFloat(StyleProperty.OPACITY, 0f), 1e-6);
    }
}
