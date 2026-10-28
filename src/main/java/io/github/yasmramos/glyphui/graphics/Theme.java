package io.github.yasmramos.glyphui.graphics;

import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Font;

/**
 * Central visual theme: colors, per-role fonts, corner radii and paddings.
 *
 * <p>Widgets read their appearance from {@link #current()} at render and
 * measure time instead of hard-coding colors. Two built-in palettes ship with
 * the toolkit — {@link #light()} and {@link #dark()} — and the dark one is
 * the process default (matching the historical Glyph UI look).</p>
 *
 * <p>Instances are immutable value objects; use {@link #toBuilder()} to derive
 * customized themes and {@link #setCurrent(Theme)} to install one globally
 * (typically once, during application startup).</p>
 */
public final class Theme {

    // ------------------------------------------------------------------
    // Roles used by FontManager / Theme font lookup
    // ------------------------------------------------------------------

    /** Semantic font roles available in a theme. */
    public enum FontRole {
        /** Regular body text (labels, text fields). */
        BODY,
        /** Control captions (buttons). */
        BUTTON,
        /** Larger headings. */
        HEADING
    }

    /** The shared light palette instance. */
    private static final Theme LIGHT = builder().mode(Mode.LIGHT).build();

    /** The shared dark palette instance (process default). */
    private static final Theme DARK = new Theme(Mode.DARK);

    /** Currently installed theme. Volatile: swapped from the UI thread. */
    private static volatile Theme current = DARK;

    /** Color/theme mode. */
    public enum Mode {
        /** Light background with dark foreground. */
        LIGHT,
        /** Dark background with light foreground. */
        DARK
    }

    private final Mode mode;
    private final int backgroundColor;
    private final int foregroundColor;
    private final int accentColor;
    private final int controlNormalColor;
    private final int controlHoverColor;
    private final int controlPressedColor;
    private final int controlDisabledColor;
    private final int controlTextColor;
    private final int controlTextDisabledColor;
    private final int borderColor;
    private final int inputBackgroundColor;
    private final int titleBarBackgroundColor;
    private final float controlCornerRadius;
    private final float focusRingRadius;
    private final float padding;
    private final float buttonPaddingHorizontal;
    private final float buttonPaddingVertical;
    /**
     * Per-role font-size overrides owned by this theme (5.15). Empty when the
     * theme defers entirely to {@link FontManager} defaults. Immutable copy;
     * read through {@link #getFontSize(FontRole)}.
     */
    private final java.util.Map<FontRole, Float> fontSizes;

    /**
     * Creates a theme with all values supplied explicitly.
     */
    private Theme(Builder b) {
        this.mode = b.mode;
        // Snapshot only the roles the builder actually overrode so themes
        // that never touch font sizes compare equal to legacy ones and keep
        // an empty map in equals/hashCode/toString.
        this.fontSizes = b.fontSizes.isEmpty()
                ? java.util.Map.of()
                : java.util.Map.copyOf(b.fontSizes);
        this.backgroundColor = b.backgroundColor;
        this.foregroundColor = b.foregroundColor;
        this.accentColor = b.accentColor;
        this.controlNormalColor = b.controlNormalColor;
        this.controlHoverColor = b.controlHoverColor;
        this.controlPressedColor = b.controlPressedColor;
        this.controlDisabledColor = b.controlDisabledColor;
        this.controlTextColor = b.controlTextColor;
        this.controlTextDisabledColor = b.controlTextDisabledColor;
        this.borderColor = b.borderColor;
        this.inputBackgroundColor = b.inputBackgroundColor;
        this.titleBarBackgroundColor = b.titleBarBackgroundColor;
        this.controlCornerRadius = b.controlCornerRadius;
        this.focusRingRadius = b.focusRingRadius;
        this.padding = b.padding;
        this.buttonPaddingHorizontal = b.buttonPaddingHorizontal;
        this.buttonPaddingVertical = b.buttonPaddingVertical;
    }

    /**
     * Full constructor used internally for the built-in palettes.
     *
     * @param mode light or dark
     */
    private Theme(Mode mode) {
        this(builder().mode(mode));
    }

    /**
     * Gets the currently installed global theme.
     *
     * <p><b>Design note (Block 4 item 4.1):</b> this is a process-wide
     * singleton kept as a compatibility fallback. Multi-app scenarios, theme
     * previews and embeddings should not mutate it; instead resolve the theme
     * from the owning context — an {@code Application} instance carries its
     * own installed theme, and widgets reached through a component tree
     * should read it via their app rather than calling {@code current()}.
     * New code that needs per-context theming must thread an explicit
     * theme/app context through the tree and use this method only when no
     * context is available.</p>
     *
     * @return the current theme (never null)
     */
    public static Theme current() {
        return current;
    }

    /**
     * Installs a theme as the process-wide current theme. Prefer installing a
     * theme on a specific {@code Application} when multiple apps or preview
     * surfaces coexist in one JVM (see {@link #current()} for the Block 4
     * design note); this global setter exists so single-app processes keep a
     * simple entry point.
     *
     * @param theme the theme to install (null restores the dark default)
     */
    public static void setCurrent(Theme theme) {
        current = (theme != null) ? theme : DARK;
        // Theme-derived var() values (--bg, --accent, ...) feed the cascade;
        // swapping the theme must trigger a re-cascade on the next frame.
        io.github.yasmramos.glyphui.ui.Component.invalidateStyle();
    }

    /**
     * Gets the shared light theme.
     *
     * @return the light palette
     */
    public static Theme light() {
        return LIGHT;
    }

    /**
     * Gets the shared dark theme.
     *
     * @return the dark palette
     */
    public static Theme dark() {
        return DARK;
    }

    /**
     * Starts building a customized theme. The builder starts from the dark
     * palette when {@link Builder#mode(Mode)} is DARK and from the light
     * palette otherwise; individual values can then be overridden.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder(Mode.DARK);
    }

    /**
     * Starts building a customized theme on top of a base palette.
     *
     * <p><strong>Ordering note (5.14):</strong> {@link Builder#mode(Mode)}
     * reseeds <em>every</em> color token from the chosen palette, so calling
     * it after custom setters silently discards them. When the base palette
     * is known up front — the common case — prefer this factory over
     * {@code Theme.builder().mode(...)}: the mode is applied before any
     * custom setter runs and cannot overwrite anything.</p>
     *
     * <pre>{@code
     * // Correct: red accent survives because the palette is seeded first.
     * Theme t = Theme.builder(Theme.Mode.LIGHT).accentColor(red).build();
     *
     * // Wrong order: mode(LIGHT) re-seeds all colors and drops the red.
     * Theme bad = Theme.builder().accentColor(red).mode(Theme.Mode.LIGHT).build();
     * }</pre>
     *
     * @param mode the base palette to seed the builder from
     * @return a new builder pre-populated with the palette's colors
     */
    public static Builder builder(Mode mode) {
        return new Builder(mode);
    }

    /**
     * Creates a builder pre-populated with this theme's values.
     *
     * @return a new builder copying this theme
     */
    public Builder toBuilder() {
        Builder b = new Builder(mode);
        b.backgroundColor = backgroundColor;
        b.foregroundColor = foregroundColor;
        b.accentColor = accentColor;
        b.controlNormalColor = controlNormalColor;
        b.controlHoverColor = controlHoverColor;
        b.controlPressedColor = controlPressedColor;
        b.controlDisabledColor = controlDisabledColor;
        b.controlTextColor = controlTextColor;
        b.controlTextDisabledColor = controlTextDisabledColor;
        b.borderColor = borderColor;
        b.inputBackgroundColor = inputBackgroundColor;
        b.titleBarBackgroundColor = titleBarBackgroundColor;
        b.controlCornerRadius = controlCornerRadius;
        b.focusRingRadius = focusRingRadius;
        b.padding = padding;
        b.buttonPaddingHorizontal = buttonPaddingHorizontal;
        b.buttonPaddingVertical = buttonPaddingVertical;
        b.fontSizes.putAll(fontSizes);
        return b;
    }

    // ------------------------------------------------------------------
    // Accessors
    // ------------------------------------------------------------------

    /** @return the theme mode (LIGHT/DARK) */
    public Mode getMode() {
        return mode;
    }

    /** @return window/panel background color (ARGB) */
    public int getBackgroundColor() {
        return backgroundColor;
    }

    /** @return primary foreground/text color (ARGB) */
    public int getForegroundColor() {
        return foregroundColor;
    }

    /** @return accent color used for focus rings and highlights (ARGB) */
    public int getAccentColor() {
        return accentColor;
    }

    /** @return normal control background color (ARGB) */
    public int getControlNormalColor() {
        return controlNormalColor;
    }

    /** @return hovered control background color (ARGB) */
    public int getControlHoverColor() {
        return controlHoverColor;
    }

    /** @return pressed control background color (ARGB) */
    public int getControlPressedColor() {
        return controlPressedColor;
    }

    /** @return disabled control background color (ARGB) */
    public int getControlDisabledColor() {
        return controlDisabledColor;
    }

    /** @return control text color (ARGB) */
    public int getControlTextColor() {
        return controlTextColor;
    }

    /** @return disabled control text color (ARGB) */
    public int getControlTextDisabledColor() {
        return controlTextDisabledColor;
    }

    /** @return border color for controls (ARGB) */
    public int getBorderColor() {
        return borderColor;
    }

    /**
     * Gets the fill color of text-entry surfaces (text fields).
     *
     * <p>Entries need their own surface token: painting them with the page
     * background makes the field invisible except for its border, so the
     * editable area cannot be seen at all.</p>
     *
     * @return text-entry background color (ARGB)
     */
    public int getInputBackgroundColor() {
        return inputBackgroundColor;
    }

    /**
     * Gets the fill color of the title-bar strip in a window decorated by the
     * toolkit (custom title bar). It is a separate token so the bar can read
     * as chrome distinct from the page background beneath it.
     *
     * @return title-bar background color (ARGB)
     */
    public int getTitleBarBackgroundColor() {
        return titleBarBackgroundColor;
    }

    /** @return corner radius for controls, logical units */
    public float getControlCornerRadius() {
        return controlCornerRadius;
    }

    /** @return corner radius for the focus ring, logical units */
    public float getFocusRingRadius() {
        return focusRingRadius;
    }

    /** @return general content padding, logical units */
    public float getPadding() {
        return padding;
    }

    /** @return horizontal inner padding for buttons, logical units */
    public float getButtonPaddingHorizontal() {
        return buttonPaddingHorizontal;
    }

    /** @return vertical inner padding for buttons, logical units */
    public float getButtonPaddingVertical() {
        return buttonPaddingVertical;
    }

    /**
     * Gets the font registered for a semantic role.
     *
     * <p><strong>Not part of the frozen public API:</strong> this accessor is
     * reserved for toolkit internals (widget font resolution) and unit tests.
     * It returns a Skija {@code Font} borrowed from the internal manager —
     * application code must not depend on it, since native types are kept out
     * of the supported surface (see {@code docs/architecture/api-stability.md}).
     * The public way to influence fonts is the style system
     * ({@code font-family} / {@code font-size}).</p>
     *
     * @param role the font role
     * @return the font (owned by {@link FontManager}; do not close)
     */
    public Font getFont(FontRole role) {
        return FontManager.getFont(role);
    }

    /**
     * Gets the font registered for a semantic role at an explicit size.
     *
     * <p><strong>Not part of the frozen public API</strong> — see
     * {@link #getFont(FontRole)} for the rationale.</p>
     *
     * @param role the font role
     * @param size the font size in logical units
     * @return the font (owned by {@link FontManager}; do not close)
     */
    public Font getFont(FontRole role, float size) {
        return FontManager.getFont(role, size);
    }

    /**
     * Gets the default font size for a semantic role in logical units.
     *
     * <p><strong>Not part of the frozen public API:</strong> reserved for
     * toolkit internals and tests (see {@link #getFont(FontRole)}).</p>
     *
     * @param role the font role
     * @return the role's default size
     */
    public float getFontSize(FontRole role) {
        Float override = fontSizes.get(role);
        return override != null ? override : FontManager.getDefaultFontSize(role);
    }

    // ------------------------------------------------------------------
    // Value semantics
    // ------------------------------------------------------------------

    /**
     * Two themes are equal when every visual token they carry is identical:
     * mode, colors, metrics and per-role font-size overrides. Themes are
     * immutable value objects, so equality by value (not identity) is safe
     * and makes multi-theme assertions in tests straightforward.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Theme other)) return false;
        return backgroundColor == other.backgroundColor
                && foregroundColor == other.foregroundColor
                && accentColor == other.accentColor
                && controlNormalColor == other.controlNormalColor
                && controlHoverColor == other.controlHoverColor
                && controlPressedColor == other.controlPressedColor
                && controlDisabledColor == other.controlDisabledColor
                && controlTextColor == other.controlTextColor
                && controlTextDisabledColor == other.controlTextDisabledColor
                && borderColor == other.borderColor
                && inputBackgroundColor == other.inputBackgroundColor
                && titleBarBackgroundColor == other.titleBarBackgroundColor
                && Float.compare(controlCornerRadius, other.controlCornerRadius) == 0
                && Float.compare(focusRingRadius, other.focusRingRadius) == 0
                && Float.compare(padding, other.padding) == 0
                && Float.compare(buttonPaddingHorizontal, other.buttonPaddingHorizontal) == 0
                && Float.compare(buttonPaddingVertical, other.buttonPaddingVertical) == 0
                && mode == other.mode
                && fontSizes.equals(other.fontSizes);
    }

    @Override
    public int hashCode() {
        int result = mode.hashCode();
        result = 31 * result + backgroundColor;
        result = 31 * result + foregroundColor;
        result = 31 * result + accentColor;
        result = 31 * result + controlNormalColor;
        result = 31 * result + controlHoverColor;
        result = 31 * result + controlPressedColor;
        result = 31 * result + controlDisabledColor;
        result = 31 * result + controlTextColor;
        result = 31 * result + controlTextDisabledColor;
        result = 31 * result + borderColor;
        result = 31 * result + inputBackgroundColor;
        result = 31 * result + titleBarBackgroundColor;
        result = 31 * result + Float.floatToIntBits(controlCornerRadius);
        result = 31 * result + Float.floatToIntBits(focusRingRadius);
        result = 31 * result + Float.floatToIntBits(padding);
        result = 31 * result + Float.floatToIntBits(buttonPaddingHorizontal);
        result = 31 * result + Float.floatToIntBits(buttonPaddingVertical);
        result = 31 * result + fontSizes.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Theme[mode=" + mode
                + ", bg=" + Integer.toHexString(backgroundColor)
                + ", fg=" + Integer.toHexString(foregroundColor)
                + ", accent=" + Integer.toHexString(accentColor)
                + (fontSizes.isEmpty() ? "" : ", fontSizes=" + fontSizes)
                + "]";
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    /**
     * Mutable builder for {@link Theme}.
     */
    public static final class Builder {
        private Mode mode;
        private int backgroundColor;
        private int foregroundColor;
        private int accentColor;
        private int controlNormalColor;
        private int controlHoverColor;
        private int controlPressedColor;
        private int controlDisabledColor;
        private int controlTextColor;
        private int controlTextDisabledColor;
        private int borderColor;
        private int inputBackgroundColor;
        private int titleBarBackgroundColor;
        private float controlCornerRadius;
        private float focusRingRadius;
        private float padding;
        private float buttonPaddingHorizontal;
        private float buttonPaddingVertical;
        /**
         * Per-role font-size overrides (5.15). Kept in the builder so a theme
         * can carry its own type scale instead of relying solely on the
         * process-wide {@link FontManager} defaults.
         */
        private final java.util.EnumMap<FontRole, Float> fontSizes =
                new java.util.EnumMap<>(FontRole.class);

        private Builder(Mode mode) {
            mode(mode);
            // Shared metrics across palettes
            this.controlCornerRadius = 8.0f;
            this.focusRingRadius = 6.0f;
            this.padding = 8.0f;
            this.buttonPaddingHorizontal = 12.0f;
            this.buttonPaddingVertical = 6.0f;
        }

        /**
         * Selects the base palette (light or dark). Individual setters may
         * still override specific values afterwards.
         *
         * <p><strong>Order matters (5.14):</strong> this method reseeds every
         * color token from the chosen palette, silently discarding custom
         * colors set earlier. Call it first — or use
         * {@link Theme#builder(Mode)} — then apply individual overrides.</p>
         *
         * @param mode the palette mode
         * @return this builder
         */
        public Builder mode(Mode mode) {
            this.mode = mode;
            if (mode == Mode.LIGHT) {
                this.backgroundColor = Color.makeARGB(255, 245, 245, 245);
                this.foregroundColor = Color.makeARGB(255, 25, 25, 25);
                this.accentColor = Color.makeARGB(255, 0, 120, 215);
                this.controlNormalColor = Color.makeARGB(255, 225, 225, 225);
                this.controlHoverColor = Color.makeARGB(255, 205, 205, 205);
                this.controlPressedColor = Color.makeARGB(255, 185, 185, 185);
                this.controlDisabledColor = Color.makeARGB(255, 235, 235, 235);
                this.controlTextColor = Color.makeARGB(255, 25, 25, 25);
                this.controlTextDisabledColor = Color.makeARGB(255, 160, 160, 160);
                this.borderColor = Color.makeARGB(255, 150, 150, 150);
                // Text-entry surface: a clean white well so the field reads as
                // an editable area rather than a hole in the background.
                this.inputBackgroundColor = Color.makeARGB(255, 255, 255, 255);
                // Title-bar chrome: a hair darker than the page so the bar
                // reads as a distinct strip above the content.
                this.titleBarBackgroundColor = Color.makeARGB(255, 235, 235, 235);
            } else {
                this.backgroundColor = Color.makeARGB(255, 30, 30, 30);
                this.foregroundColor = Color.makeARGB(255, 240, 240, 240);
                this.accentColor = Color.makeARGB(255, 0, 122, 255);
                this.controlNormalColor = Color.makeARGB(255, 60, 60, 60);
                this.controlHoverColor = Color.makeARGB(255, 80, 80, 80);
                this.controlPressedColor = Color.makeARGB(255, 100, 100, 100);
                this.controlDisabledColor = Color.makeARGB(255, 50, 50, 50);
                this.controlTextColor = Color.makeARGB(255, 255, 255, 255);
                this.controlTextDisabledColor = Color.makeARGB(255, 150, 150, 150);
                this.borderColor = Color.makeARGB(255, 120, 120, 120);
                // Text-entry surface: slightly above the page background so the
                // field reads as an editable well instead of vanishing into it.
                this.inputBackgroundColor = Color.makeARGB(255, 42, 42, 42);
                // Title-bar chrome: a hair darker than the page so the bar
                // reads as a distinct strip above the content.
                this.titleBarBackgroundColor = Color.makeARGB(255, 24, 24, 24);
            }
            return this;
        }

        public Builder backgroundColor(int argb) { this.backgroundColor = argb; return this; }
        public Builder foregroundColor(int argb) { this.foregroundColor = argb; return this; }
        public Builder accentColor(int argb) { this.accentColor = argb; return this; }
        public Builder controlNormalColor(int argb) { this.controlNormalColor = argb; return this; }
        public Builder controlHoverColor(int argb) { this.controlHoverColor = argb; return this; }
        public Builder controlPressedColor(int argb) { this.controlPressedColor = argb; return this; }
        public Builder controlDisabledColor(int argb) { this.controlDisabledColor = argb; return this; }
        public Builder controlTextColor(int argb) { this.controlTextColor = argb; return this; }
        public Builder controlTextDisabledColor(int argb) { this.controlTextDisabledColor = argb; return this; }
        public Builder borderColor(int argb) { this.borderColor = argb; return this; }
        public Builder inputBackgroundColor(int argb) { this.inputBackgroundColor = argb; return this; }
        public Builder titleBarBackgroundColor(int argb) { this.titleBarBackgroundColor = argb; return this; }
        public Builder controlCornerRadius(float r) { this.controlCornerRadius = r; return this; }
        public Builder focusRingRadius(float r) { this.focusRingRadius = r; return this; }
        public Builder padding(float p) { this.padding = p; return this; }
        public Builder buttonPaddingHorizontal(float p) { this.buttonPaddingHorizontal = p; return this; }
        public Builder buttonPaddingVertical(float p) { this.buttonPaddingVertical = p; return this; }

        /**
         * Overrides the default font size for a semantic role in this theme
         * (5.15). Widgets that resolve their size through
         * {@link Theme#getFontSize(FontRole)} pick up the override; passing
         * {@code null} removes it and falls back to the
         * {@link FontManager} default.
         *
         * @param role the font role (non-null)
         * @param size the size in logical units, or null to clear
         * @return this builder
         */
        public Builder fontSize(FontRole role, Float size) {
            java.util.Objects.requireNonNull(role, "role");
            if (size == null) {
                fontSizes.remove(role);
            } else {
                fontSizes.put(role, size);
            }
            return this;
        }

        /**
         * Builds the immutable theme.
         *
         * @return a new theme
         */
        public Theme build() {
            return new Theme(this);
        }
    }
}
