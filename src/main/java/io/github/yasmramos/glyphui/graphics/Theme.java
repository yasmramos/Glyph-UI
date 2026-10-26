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
     * Creates a theme with all values supplied explicitly.
     */
    private Theme(Builder b) {
        this.mode = b.mode;
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
     * Gets the default body font size in logical units.
     *
     * @return the body font size
     */
    public float getFontSize(FontRole role) {
        return FontManager.getDefaultFontSize(role);
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
         * Builds the immutable theme.
         *
         * @return a new theme
         */
        public Theme build() {
            return new Theme(this);
        }
    }
}
