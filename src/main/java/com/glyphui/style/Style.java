package com.glyphui.style;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * An immutable, typed map of {@link StyleProperty} values.
 *
 * <p>A {@code Style} is the unit of the cascade: rule styles are merged in
 * specificity order (see {@link StyleSheet}) and the result is attached to a
 * component as its computed style. Values are stored with their parsed Java
 * types:</p>
 * <ul>
 *   <li>colors → {@code Integer} ARGB</li>
 *   <li>lengths/opacity/grow → {@code Float}</li>
 *   <li>keywords and font families → {@code String}</li>
 *   <li>font-weight → {@code Integer}</li>
 * </ul>
 */
public final class Style {

    /** Shared empty style instance. */
    public static final Style EMPTY = new Style(Collections.emptyMap());

    private final Map<StyleProperty, Object> values;

    private Style(Map<StyleProperty, Object> values) {
        this.values = values;
    }

    /**
     * Starts building a new style.
     *
     * @return a fresh builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a style from an existing property map (defensive copy).
     *
     * @param values the property values
     * @return an immutable style
     */
    public static Style of(Map<StyleProperty, Object> values) {
        EnumMap<StyleProperty, Object> copy = new EnumMap<>(StyleProperty.class);
        copy.putAll(values);
        return new Style(Collections.unmodifiableMap(copy));
    }

    /**
     * Parses an HTML {@code style="..."} attribute value into a typed style.
     * Uses the same value pipeline as stylesheet declarations, so
     * {@code var(--name)} references and unsupported properties behave
     * identically (unknown/unsupported declarations are skipped silently in
     * inline styles; use {@link StyleSheet#parse} for logged warnings).
     *
     * @param inlineText semicolon-separated declarations, e.g.
     *                   {@code "color: red; padding: 8px"} (null/blank → empty)
     * @param variables  custom-property map used to resolve {@code var()}
     *                   references (may be null)
     * @return the parsed style, never null
     */
    public static Style parseInline(String inlineText, Map<String, String> variables) {
        if (inlineText == null || inlineText.isBlank()) {
            return EMPTY;
        }
        Builder builder = builder();
        for (String decl : inlineText.split(";")) {
            int colon = decl.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String prop = decl.substring(0, colon).trim().toLowerCase(java.util.Locale.ROOT);
            String raw = decl.substring(colon + 1).trim();
            if (prop.isEmpty() || raw.isEmpty() || prop.startsWith("--")) {
                continue;
            }
            StyleProperty key = StyleSheet.propertyFor(prop);
            if (key == null) {
                continue; // not part of the supported subset
            }
            Object typed = CssValues.parse(prop, raw,
                    variables != null ? variables : Map.of());
            if (typed != null) {
                builder.set(key, typed);
            }
        }
        return builder.build();
    }

    /**
     * Gets the raw value for a property.
     *
     * @param property the property key
     * @return the value or null when unset
     */
    public Object get(StyleProperty property) {
        return values.get(property);
    }

    /**
     * Gets an integer value (colors, weights) with a fallback.
     *
     * @param property   the property key
     * @param defaultVal value returned when unset or wrong type
     * @return the resolved int
     */
    public int getInt(StyleProperty property, int defaultVal) {
        Object v = values.get(property);
        return (v instanceof Integer i) ? i : defaultVal;
    }

    /**
     * Gets a float value (lengths, opacity, grow) with a fallback.
     *
     * @param property   the property key
     * @param defaultVal value returned when unset or wrong type
     * @return the resolved float
     */
    public float getFloat(StyleProperty property, float defaultVal) {
        Object v = values.get(property);
        return (v instanceof Float f) ? f : defaultVal;
    }

    /**
     * Gets a string value (keywords, font family) with a fallback.
     *
     * @param property   the property key
     * @param defaultVal value returned when unset
     * @return the resolved string
     */
    public String getString(StyleProperty property, String defaultVal) {
        Object v = values.get(property);
        return (v instanceof String s) ? s : defaultVal;
    }

    /**
     * Checks whether a property is declared in this style.
     *
     * @param property the property key
     * @return true when present
     */
    public boolean has(StyleProperty property) {
        return values.containsKey(property);
    }

    /**
     * Whether this style declares no properties at all.
     *
     * @return true when empty
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * The set of declared properties.
     *
     * @return unmodifiable property set
     */
    public Set<StyleProperty> properties() {
        return values.keySet();
    }

    /**
     * Returns a new style with every <b>inherited</b> property of {@code parent}
     * that this style does not declare itself. Non-inherited properties never
     * leak through inheritance.
     *
     * @param parent the ancestor computed style (may be null)
     * @return the style with inherited values filled in
     */
    public Style inheritFrom(Style parent) {
        if (parent == null || parent.isEmpty()) {
            return withoutRawValues();
        }
        EnumMap<StyleProperty, Object> merged = new EnumMap<>(StyleProperty.class);
        for (Map.Entry<StyleProperty, Object> e : parent.values.entrySet()) {
            if (e.getKey().isInherited() && e.getKey() != StyleProperty.RAW_VALUES) {
                merged.put(e.getKey(), e.getValue());
            }
        }
        merged.putAll(values);
        merged.remove(StyleProperty.RAW_VALUES);
        return new Style(Collections.unmodifiableMap(merged));
    }

    /**
     * The set of properties this style declares (excluding the synthetic
     * {@link StyleProperty#RAW_VALUES} carrier). Used by the engine to know
     * exactly which keys an inline style overrides.
     *
     * @return the declared property keys, never null
     */
    java.util.Set<StyleProperty> declaredProperties() {
        EnumSet<StyleProperty> set = EnumSet.noneOf(StyleProperty.class);
        for (Map.Entry<StyleProperty, Object> e : values.entrySet()) {
            if (e.getKey() != StyleProperty.RAW_VALUES) {
                set.add(e.getKey());
            }
        }
        return set;
    }

    /**
     * Returns a new style where every entry of {@code other} overrides this
     * style's entries (cascade merge: later/higher-priority wins). The
     * synthetic {@link StyleProperty#RAW_VALUES} carrier is dropped from the
     * result — raw declarations are only meaningful during cascade.
     *
     * @param other the overriding style (null treated as empty)
     * @return the merged style
     */
    public Style overrideWith(Style other) {
        if (other == null || other.isEmpty()) {
            return withoutRawValues();
        }
        EnumMap<StyleProperty, Object> merged = new EnumMap<>(StyleProperty.class);
        merged.putAll(values);
        merged.putAll(other.values);
        merged.remove(StyleProperty.RAW_VALUES);
        return new Style(Collections.unmodifiableMap(merged));
    }

    /**
     * This style with {@link StyleProperty#RAW_VALUES} removed. Used when a
     * rule carries no unresolved declarations so stale raw values never leak
     * into lower-priority merges.
     *
     * @return the sanitized style (same instance when no raw values present)
     */
    public Style withoutRawValues() {
        if (!values.containsKey(StyleProperty.RAW_VALUES)) {
            return this;
        }
        EnumMap<StyleProperty, Object> copy = new EnumMap<>(StyleProperty.class);
        copy.putAll(values);
        copy.remove(StyleProperty.RAW_VALUES);
        return new Style(Collections.unmodifiableMap(copy));
    }

    /**
     * Re-parses this style's unresolved {@code var(--name)} declarations
     * against the given variable map and returns a fully typed style.
     *
     * <p>Produced by {@link StyleSheet#parse}: declarations that referenced
     * variables unknown at parse time (typically theme-derived
     * {@code --bg/--fg/--accent/--border}) are kept as raw text under
     * {@link StyleProperty#RAW_VALUES}. This method runs them through the same
     * value pipeline ({@link CssValues#parse}) with the engine's variables and
     * folds the results into the typed values. On conflict between an existing
     * typed entry and a raw declaration for the same property, the raw
     * declaration wins: it is the most recently parsed form of the rule's own
     * declaration, while the typed entry may derive from an earlier duplicate
     * declaration in the same block (last-declaration-wins, like CSS). For
     * well-formed sheets — one declaration per property per rule, as produced
     * by {@link StyleSheet#parse} — the two sets are disjoint and no conflict
     * arises.</p>
     *
     * @param variables custom-property map for {@code var()} resolution
     *                  (null treated as empty)
     * @return the resolved style, never null
     */
    @SuppressWarnings("unchecked")
    public Style resolveVars(Map<String, String> variables) {
        Object raw = values.get(StyleProperty.RAW_VALUES);
        if (!(raw instanceof Map)) {
            return this;
        }
        Map<StyleProperty, String> rawValues = (Map<StyleProperty, String>) raw;
        Builder builder = builder();
        Map<String, String> vars = variables != null ? variables : Map.of();
        for (Map.Entry<StyleProperty, Object> e : values.entrySet()) {
            if (e.getKey() != StyleProperty.RAW_VALUES) {
                builder.setInternally(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<StyleProperty, String> e : rawValues.entrySet()) {
            // Route through the property's canonical CSS name, which may
            // differ from its kebab-case enum name (BACKGROUND is declared as
            // the "background" shorthand but its cssName() is
            // "background-color"). Deriving the pipeline key from cssName()
            // instead of the declaration text silently misclassifies values:
            // a "#rrggbbaa" color routed under "background-color" hits the
            // heuristic branch of CssValues.parse and comes back rotated to
            // #aarrggbb.
            Object typed = CssValues.parse(pipelineNameFor(e.getKey()), e.getValue(), vars);
            if (typed != null) {
                builder.set(e.getKey(), typed);
            } else {
                System.err.println("[GlyphUI] Warning: could not resolve CSS value \""
                        + e.getValue() + "\" for property \"" + pipelineNameFor(e.getKey()) + "\"");
            }
        }
        return builder.build();
    }

    /**
     * The canonical name under which a property must be routed through
     * {@link CssValues#parse}. Kept in sync with
     * {@link StyleSheet#propertyFor} (its inverse): BACKGROUND is parsed from
     * the "background" shorthand, so re-parsing its raw value must use that
     * key — cssName() returns the longhand "background-color", which the
     * color pipeline only recognizes as a standard name, losing the
     * shorthand-specific branch.
     */
    private static String pipelineNameFor(StyleProperty property) {
        return property == StyleProperty.BACKGROUND ? "background" : property.cssName();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Style{");
        values.forEach((k, v) -> sb.append(k.name().toLowerCase().replace('_', '-'))
                .append('=').append(v).append("; "));
        return sb.append('}').toString();
    }

    /**
     * Mutable builder for {@link Style} instances.
     */
    public static final class Builder {
        private final EnumMap<StyleProperty, Object> values = new EnumMap<>(StyleProperty.class);

        /**
         * Sets a value for a property. The value must match the property's
         * documented type ({@link StyleProperty#expectedType()}); mistyped
         * values are rejected loudly here instead of silently returning the
         * read-time default forever (a {@code String} stored under a Float
         * property is invisible to {@code getFloat}). {@code Integer}/{@code
         * Long} values accepted by an {@code int}-typed property; {@code
         * Float}/{@code Double}/{@code Integer} values by a float-typed one.
         * {@link StyleProperty#RAW_VALUES} is exempt (synthetic carrier).
         *
         * @param property the key
         * @param value    the value (null removes/ignores the entry)
         * @return this builder
         * @throws IllegalArgumentException when the value type does not match
         *                                  the property's expected type
         */
        public Builder set(StyleProperty property, Object value) {
            if (value == null) {
                return this;
            }
            checkType(property, value);
            values.put(property, value);
            return this;
        }

        /**
         * Internal counterpart of {@link #set}: same validation funnel, but
         * stores numeric values in the property's exact boxed type. Used when
         * rebuilding a style whose entries are already typed ({@code
         * resolveVars}): {@link #set} deliberately tolerates widening (Long →
         * Integer slot, Double → Float slot) for programmatic ergonomics, and
         * that tolerance would silently change the stored identity here.
         */
        private void setInternally(StyleProperty property, Object value) {
            if (value == null) {
                return;
            }
            Class<?> expected = property.expectedType();
            if (expected == Integer.class && value instanceof Number n
                    && !(value instanceof Integer)) {
                value = n.intValue();
            } else if (expected == Float.class && value instanceof Number n
                    && !(value instanceof Float)) {
                value = n.floatValue();
            }
            checkType(property, value);
            values.put(property, value);
        }

        private static void checkType(StyleProperty property, Object value) {
            Class<?> expected = property.expectedType();
            if (expected != null && !isCompatible(expected, value)) {
                throw new IllegalArgumentException("Style property " + property.cssName()
                        + " expects " + expected.getSimpleName() + " but got "
                        + value.getClass().getSimpleName() + ": \"" + value + "\"");
            }
        }

        /**
         * Removes a previously set entry from this builder. Used by the
         * engine when overlaying an inline style so that only the properties
         * actually declared inline end up in the result.
         *
         * @param property the key to remove
         * @return this builder
         */
        public Builder clear(StyleProperty property) {
            values.remove(property);
            return this;
        }

        private static boolean isCompatible(Class<?> expected, Object value) {
            if (expected.isInstance(value)) {
                return true;
            }
            // Numeric widening/narrowing tolerated between boxed int/float
            // domains keeps programmatic builders ergonomic without letting
            // strings or wrong-kind objects through.
            if (expected == Integer.class) {
                return value instanceof Integer || value instanceof Long;
            }
            if (expected == Float.class) {
                return value instanceof Float || value instanceof Double
                        || value instanceof Integer;
            }
            return false;
        }

        public Builder color(StyleProperty property, int argb) {
            return set(property, argb);
        }

        public Builder length(StyleProperty property, float pixels) {
            return set(property, pixels);
        }

        public Builder keyword(StyleProperty property, String value) {
            return set(property, value);
        }

        /**
         * Copies every entry of {@code other} into this builder (existing
         * keys are overwritten).
         *
         * @param other the style to absorb
         * @return this builder
         */
        public Builder putAll(Style other) {
            if (other != null) {
                for (Map.Entry<StyleProperty, Object> e : other.values.entrySet()) {
                    // Route through set() so every absorbed entry is type-
                    // validated the same way as a direct one. RAW_VALUES is
                    // exempt (expectedType() == null), everything else must
                    // already be correctly typed — merge paths (inheritFrom,
                    // overrideWith) preserve that invariant.
                    set(e.getKey(), e.getValue());
                }
            }
            return this;
        }

        /**
         * Sets an integer value (colors are stored as ARGB ints). Equivalent
         * to {@code set(property, Integer.valueOf(value))} but without the
         * autoboxing footgun: passing a raw int directly to
         * {@link #set(StyleProperty, Object)} boxes it as {@code Integer},
         * which fails validation for float-typed properties such as
         * {@code opacity}. This method converts explicitly per the property's
         * {@link StyleProperty#expectedType()} (int → float widening allowed,
         * float → int rejected).
         *
         * @param property the key
         * @param value    the int value
         * @return this builder
         * @throws IllegalArgumentException when the property expects String or
         *                                  cannot hold this int losslessly
         */
        public Builder setInt(StyleProperty property, int value) {
            Class<?> expected = property.expectedType();
            if (expected == Integer.class) {
                return set(property, value);
            }
            if (expected == Float.class) {
                return set(property, (float) value);
            }
            throw new IllegalArgumentException("Property " + property.name()
                    + " expects " + (expected == null ? "raw map" : expected.getSimpleName())
                    + ", got int " + value);
        }

        /**
         * Float counterpart of {@link #setInt}: converts a raw float to the
         * property's expected numeric type (float → int only when the value
         * is integral).
         *
         * @param property the key
         * @param value    the float value
         * @return this builder
         * @throws IllegalArgumentException on non-numeric or String properties
         */
        public Builder setFloat(StyleProperty property, float value) {
            Class<?> expected = property.expectedType();
            if (expected == Float.class) {
                return set(property, value);
            }
            if (expected == Integer.class && value == Math.floor(value)
                    && !Float.isInfinite(value)) {
                return set(property, (int) value);
            }
            throw new IllegalArgumentException("Property " + property.name()
                    + " expects " + (expected == null ? "raw map" : expected.getSimpleName())
                    + ", got float " + value);
        }

        /**
         * Builds the immutable style.
         *
         * @return a new {@link Style}
         */
        public Style build() {
            return new Style(Collections.unmodifiableMap(new EnumMap<>(values)));
        }
    }
}
