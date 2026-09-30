package com.glyphui.style;

import java.util.Collections;
import java.util.EnumMap;
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
            return this;
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
     * folds the results into the typed values (existing typed entries win on
     * conflict, which cannot happen for well-formed sheets).</p>
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
        for (Map.Entry<StyleProperty, Object> e : values.entrySet()) {
            if (e.getKey() != StyleProperty.RAW_VALUES) {
                builder.set(e.getKey(), e.getValue());
            }
        }
        Map<String, String> vars = variables != null ? variables : Map.of();
        for (Map.Entry<StyleProperty, String> e : rawValues.entrySet()) {
            Object typed = CssValues.parse(e.getKey().cssName(), e.getValue(), vars);
            if (typed != null) {
                builder.set(e.getKey(), typed);
            } else {
                System.err.println("[GlyphUI] Warning: could not resolve CSS value \""
                        + e.getValue() + "\" for property \"" + e.getKey().cssName() + "\"");
            }
        }
        return builder.build();
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
         * Sets a raw value (type must match the property's documented type).
         *
         * @param property the key
         * @param value    the value
         * @return this builder
         */
        public Builder set(StyleProperty property, Object value) {
            if (value != null) {
                values.put(property, value);
            }
            return this;
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
                values.putAll(other.values);
            }
            return this;
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
