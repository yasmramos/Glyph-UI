package io.github.yasmramos.glyphui.ui;

/**
 * Accessibility roles for UI components (v0.1 API stub).
 *
 * <p>Glyph UI v0.1 exposes the accessibility surface of every component so
 * that a future platform bridge (AT-SPI on Linux, UIA on Windows, NSAccessibility
 * on macOS) does not require retrofitting the widget API. The windowing layer itself cannot
 * host such a bridge, so no screen-reader integration exists yet — only the
 * role/name/description contract on {@link Component}.</p>
 */
public enum AccessibleRole {
    /** Unspecified / custom component. */
    GENERIC,

    /** A push button ({@link Button}). */
    BUTTON,

    /** A static text label ({@link Label}). */
    LABEL,

    /** A container panel ({@link Panel}). */
    PANEL,

    /** A single-line editable text field ({@link TextField}). */
    TEXT_FIELD,

    /** An image display ({@link ImageView}). */
    IMAGE,

    /** A drop-down menu panel ({@link Menu}). */
    MENU,

    /** A selectable row inside a menu ({@link MenuItem}). */
    MENU_ITEM,

    /** A non-interactive divider between menu rows ({@link MenuSeparator}). */
    MENU_SEPARATOR
}
