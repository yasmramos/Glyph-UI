package com.glyphui.style;

/**
 * Typed set of CSS-like style properties supported by Glyph UI.
 *
 * <p>Each enum constant represents one declaration property. Properties are
 * divided into two groups:</p>
 * <ul>
 *   <li><b>Inherited</b> (textual) properties — font family/size/weight, text
 *       color and opacity — propagate from a parent component to its
 *       children, mirroring CSS inheritance.</li>
 *   <li><b>Non-inherited</b> properties — box model (width/height/padding/
 *       margin/border), background and display — apply only to the element
 *       they are declared on.</li>
 * </ul>
 */
public enum StyleProperty {

    // ------------------------------------------------------------------
    // Inherited / textual properties
    // ------------------------------------------------------------------

    /** Text foreground color (ARGB int). Inherited. */
    COLOR(true),

    /** Font family name (String). Inherited. */
    FONT_FAMILY(true),

    /** Font size in logical pixels (Float). Inherited. */
    FONT_SIZE(true),

    /** Font weight 100..900 (Integer). Inherited. */
    FONT_WEIGHT(true),

    /** Element opacity 0..1 (Float). Inherited. */
    OPACITY(true),

    // ------------------------------------------------------------------
    // Non-inherited / box properties
    // ------------------------------------------------------------------

    /** Background color (ARGB int). */
    BACKGROUND(false),

    /** Accent color used by focus rings and highlights (ARGB int). */
    ACCENT_COLOR(false),

    /** Border color (ARGB int). */
    BORDER_COLOR(false),

    /** Uniform border width in logical pixels (Float). */
    BORDER_WIDTH(false),

    /** Corner radius of the border box in logical pixels (Float). */
    BORDER_RADIUS(false),

    /**
     * Raw (unresolved) declaration values attached to a parsed rule, stored
     * under this synthetic key by {@link StyleSheet#parse}. Values are
     * {@code Map<StyleProperty, String>} mapping each supported property to
     * its original declaration text (which may still contain
     * {@code var(--name)} references). {@link StyleEngine} re-parses these
     * with its own variable map (stylesheet custom properties + theme-derived
     * {@code --bg/--fg/--accent/--border}) because those variables are not
     * known at parse time. Never set programmatically; exempt from the value
     * type validation performed by {@link Builder#set}.
     */
    RAW_VALUES(false),

    /** Uniform margin outside the border box (Float). */
    MARGIN(false),

    /** Uniform padding inside the border box (Float). */
    PADDING(false),

    /** Explicit width in logical pixels (Float). */
    WIDTH(false),

    /** Explicit height in logical pixels (Float). */
    HEIGHT(false),

    /** Display mode: "flex", "flow" or "none" (String). */
    DISPLAY(false),

    /** Flex main axis: "row" or "column" (String). */
    FLEX_DIRECTION(false),

    /** Main-axis distribution: flex-start|center|flex-end|space-between|space-around (String). */
    JUSTIFY_CONTENT(false),

    /** Cross-axis alignment: flex-start|center|flex-end|stretch (String). */
    ALIGN_ITEMS(false),

    /** Gap between flex items in logical pixels (Float). */
    GAP(false),

    /** Flex grow factor (Float). */
    FLEX_GROW(false);

    private final boolean inherited;

    StyleProperty(boolean inherited) {
        this.inherited = inherited;
    }

    /**
     * The Java type expected by this property's values, as documented on each
     * enum constant: {@code Integer} (colors, font-weight), {@code Float}
     * (lengths, opacity, flex-grow), {@code String} (keywords, font-family).
     * {@link Style.Builder#set} uses this to reject mistyped values instead of
     * letting them silently fall back to defaults at read time.
     *
     * @return the expected value type, or null for properties without a
     *         fixed type ({@link #RAW_VALUES})
     */
    public Class<?> expectedType() {
        return switch (this) {
            case COLOR, BACKGROUND, ACCENT_COLOR, BORDER_COLOR, FONT_WEIGHT -> Integer.class;
            case BORDER_WIDTH, BORDER_RADIUS, PADDING, MARGIN, WIDTH, HEIGHT,
                 OPACITY, FONT_SIZE, GAP, FLEX_GROW -> Float.class;
            case FONT_FAMILY, DISPLAY, FLEX_DIRECTION, JUSTIFY_CONTENT,
                 ALIGN_ITEMS -> String.class;
            case RAW_VALUES -> null; // synthetic carrier, exempt from validation
        };
    }

    /**
     * Whether this property is inherited by child components (CSS-style
     * textual inheritance).
     *
     * @return true when children inherit the value
     */
    public boolean isInherited() {
        return inherited;
    }

    /**
     * The canonical CSS declaration name accepted by the value pipeline
     * ({@link CssValues#parse}). It is normally the enum constant lowercased
     * with underscores replaced by hyphens — e.g. {@code BORDER_COLOR} →
     * {@code "border-color"} — but a few constants override it because the
     * engine's internal names diverge from standard CSS: {@code BACKGROUND}
     * maps to the shorthand {@code "background"} and {@code COLOR} must NOT
     * become {@code "colour"}, or the color branch of the value pipeline
     * silently misclassifies the value as a keyword string.
     *
     * <p>Used to re-run raw values through the value pipeline
     * (see {@link Style#resolveVars}); any caller routing a property through
     * {@link CssValues#parse} must use this name, not an ad-hoc derivation.</p>
     *
     * @return the canonical CSS property name, never null
     */
    public String cssName() {
        return switch (this) {
            case COLOR -> "color";
            case BACKGROUND -> "background";
            default -> name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
        };
    }
}
