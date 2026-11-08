package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.AccessibleRole;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.TextArea;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Markup tests for the {@code textarea} tag: it maps to the multi-line
 * {@link TextArea} (not the single-line {@code TextField}) and reads its
 * {@code value}/{@code placeholder} attributes.
 */
class TextAreaMarkupTest {

    @Test
    void textareaTagBuildsAMultiLineArea() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><textarea id=\"notes\">first\nsecond</textarea></body>");

        TextArea area = (TextArea) UiLoader.findById(root, "notes");
        assertNotNull(area, "<textarea> must map to TextArea");
        assertEquals(AccessibleRole.TEXT_AREA, area.getAccessibleRole());
    }

    @Test
    void textareaTagReadsItsPlaceholder() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><textarea id=\"notes\" placeholder=\"Write here\"/></body>");

        TextArea area = (TextArea) UiLoader.findById(root, "notes");
        assertEquals("Write here", area.getPlaceholder());
    }
}
