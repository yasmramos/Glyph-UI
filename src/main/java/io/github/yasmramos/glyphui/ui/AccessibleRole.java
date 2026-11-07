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

    /** A button that latches on and off ({@link ToggleButton}). */
    TOGGLE_BUTTON,

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
    MENU_SEPARATOR,

    /** A check box with a boolean state ({@link CheckBox}). */
    CHECKBOX,

    /** A read-only progress indicator ({@link ProgressBar}). */
    PROGRESS_BAR,

    /** A continuous value selector ({@link Slider}). */
    SLIDER,

    /** A two-state on/off switch ({@link ToggleSwitch}). */
    SWITCH,

    /** A mutually-exclusive choice ({@link RadioButton}). */
    RADIO_BUTTON,

    /** A single-choice drop-down list ({@link Dropdown}). */
    COMBO_BOX,

    /** A non-interactive divider between content regions ({@link Separator}). */
    SEPARATOR,

    /** A scrollable container ({@link ScrollPane}). */
    SCROLL_PANE,

    /** A numeric stepper over a bounded range ({@link Spinner}). */
    SPINNER,

    /** A compact pill-shaped label ({@link Badge}). */
    BADGE,

    /** A titled surface that groups related content ({@link Card}). */
    CARD,

    /** A tabbed container showing one panel at a time ({@link Tabs}). */
    TAB_PANEL,

    /** An inline status message with a severity level ({@link Alert}). */
    ALERT,

    /** A circular user image or initials ({@link Avatar}). */
    AVATAR,

    /** A stack of collapsible sections ({@link Accordion}). */
    ACCORDION,

    /** A compact interactive tag or filter ({@link Chip}). */
    CHIP,

    /** A navigation trail of clickable segments ({@link Breadcrumb}). */
    BREADCRUMB,

    /** A pager over a page-split result set ({@link Pagination}). */
    PAGINATION,

    /** A text hyperlink ({@link Link}). */
    LINK,

    /** A star rating control ({@link Rating}). */
    RATING,

    /** A loading placeholder ({@link Skeleton}). */
    SKELETON,

    /** A multi-step flow indicator ({@link Stepper}). */
    STEPPER,

    /** A strip of controls ({@link Toolbar}). */
    TOOLBAR,

    /** A titled group of controls ({@link GroupBox}). */
    GROUP_BOX,

    /** A selectable list of items ({@link ListBox}). */
    LIST_BOX,

    /** A resizable two-pane splitter ({@link SplitPane}). */
    SPLIT_PANE,

    /** A grid of rows and columns ({@link Table}). */
    TABLE,

    /** A hierarchical list of expandable nodes ({@link TreeView}). */
    TREE,

    /** A modal surface over the rest of the UI ({@link Dialog}). */
    DIALOG,

    /** A color chooser ({@link ColorPicker}). */
    COLOR_PICKER,

    /** A transient text bubble ({@link Tooltip}). */
    TOOLTIP,

    /** A transient notification ({@link Toast}). */
    TOAST,

    /** A horizontal strip of drop-down menus ({@link MenuBar}). */
    MENU_BAR,

    /** A menu opened at an arbitrary point ({@link ContextMenu}). */
    CONTEXT_MENU,

    /** A circular progress indicator ({@link ProgressRing}). */
    PROGRESS_RING,

    /** A row of mutually exclusive options ({@link SegmentedControl}). */
    SEGMENTED_CONTROL,

    /** A two-thumb slider selecting a range ({@link RangeSlider}). */
    RANGE_SLIDER,

    /** A thin information strip along a window edge ({@link StatusBar}). */
    STATUS_BAR
}
