package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.RadioGroup;

/**
 * Services a {@link WidgetFactory} may use while building a widget.
 *
 * <p>Creation and attribute application are deliberately separate. A factory
 * builds the widget and reads only the attributes that are intrinsic to it (a
 * button's label, a slider's range); the loader still applies the common
 * attributes ({@code id}, {@code class}, {@code style}, bounds), the layout and
 * the event handlers afterwards.</p>
 *
 * <p>This keeps {@link WidgetRegistry} the single source of truth for the tag
 * vocabulary while letting factories that need a loader service — image
 * resolution, radio grouping, diagnostics — reach it without depending on
 * {@link UiLoader} itself.</p>
 */
public interface WidgetContext {

    /**
     * @param element the element to query
     * @param name    the raw attribute name
     * @return the attribute value, or the empty string when absent or valueless
     */
    String attr(Element element, String name);

    /**
     * @param element the element to query
     * @param name    the raw attribute name
     * @return true when the attribute is present, even without a value
     */
    boolean hasAttr(Element element, String name);

    /**
     * @param element the element whose text is requested
     * @return the element text content (may be empty)
     */
    String textOf(Element element);

    /**
     * @param element      the element to query
     * @param name         the attribute name
     * @param defaultValue the fallback when the attribute is absent or malformed
     * @return the parsed value or the fallback
     */
    float parseFloatAttr(Element element, String name, float defaultValue);

    /**
     * Records a non-fatal problem; the loader collects it for the caller.
     *
     * @param message the diagnostic message
     */
    void warn(String message);

    /**
     * @return the provider used for {@code <img src>}, or null when none is set
     */
    UiLoader.ImageProvider imageProvider();

    /**
     * Returns the radio group for a shared {@code name}, creating it on first
     * use so sibling radios become mutually exclusive.
     *
     * @param name the group name
     * @return the group (never null)
     */
    RadioGroup radioGroup(String name);
}
