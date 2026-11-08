package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.AccessibleRole;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.PasswordField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Markup tests for the {@code password} input tag and its {@code passwordfield}
 * Compose-style alias: the tag maps to {@link PasswordField} and reads its
 * {@code value}, {@code placeholder} and {@code mask} attributes. The masking
 * itself (the protected display form) is covered from inside the {@code ui}
 * package by {@code PasswordFieldTest}.
 */
class PasswordFieldMarkupTest {

    @Test
    void passwordTagBuildsAMaskedField() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><password id=\"pw\" value=\"hunter2\"/></body>");

        PasswordField field = (PasswordField) UiLoader.findById(root, "pw");
        assertNotNull(field, "<password> must map to PasswordField");
        assertEquals("hunter2", field.getText());
        assertEquals(AccessibleRole.PASSWORD_FIELD, field.getAccessibleRole());
    }

    @Test
    void passwordTagReadsPlaceholderAndCustomMask() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><password id=\"pw\" placeholder=\"Secret\" mask=\"*\" value=\"ab\"/></body>");

        PasswordField field = (PasswordField) UiLoader.findById(root, "pw");
        assertEquals("Secret", field.getPlaceholder());
        assertEquals('*', field.getMaskChar());
    }

    @Test
    void passwordfieldAliasIsAccepted() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><passwordfield id=\"pw\" value=\"x\"/></body>");

        assertNotNull(UiLoader.findById(root, "pw"));
    }
}
