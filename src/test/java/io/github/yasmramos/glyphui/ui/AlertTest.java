package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Alert}: level, message, sizing and inert input.
 * No Skija native code is touched because the paints are created lazily on the
 * first render.
 */
class AlertTest extends TestBase {

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    @Test
    void defaultsToInfoLevelAndEmptyMessage() {
        Alert alert = new Alert();

        assertEquals(Alert.Level.INFO, alert.getLevel());
        assertEquals("", alert.getText());
    }

    @Test
    void nullLevelFallsBackToInfoAndNullTextToEmpty() {
        Alert alert = new Alert(null, null);

        assertEquals(Alert.Level.INFO, alert.getLevel());
        assertEquals("", alert.getText());
    }

    @Test
    void eachLevelHasItsOwnAccentColor() {
        Alert info = new Alert(Alert.Level.INFO, "i");
        Alert success = new Alert(Alert.Level.SUCCESS, "s");
        Alert warning = new Alert(Alert.Level.WARNING, "w");
        Alert error = new Alert(Alert.Level.ERROR, "e");

        assertNotEquals(success.getAccentColor(), warning.getAccentColor());
        assertNotEquals(warning.getAccentColor(), error.getAccentColor());
        assertNotEquals(info.getAccentColor(), error.getAccentColor());
    }

    @Test
    void anExplicitAccentOverridesTheLevelColor() {
        Alert alert = new Alert(Alert.Level.ERROR, "e");
        alert.setAccentColor(0xFF102030);

        assertEquals(0xFF102030, alert.getAccentColor());

        alert.setAccentColor(null);
        assertEquals(new Alert(Alert.Level.ERROR, "e").getAccentColor(),
                alert.getAccentColor());
    }

    @Test
    void textPropertyKeepsTextInSync() {
        Alert alert = new Alert(Alert.Level.INFO, "a");

        alert.textProperty().set("b");

        assertEquals("b", alert.getText());
    }

    @Test
    void messageDrivesTheIntrinsicWidth() {
        Alert alert = new Alert(Alert.Level.INFO, "ok");

        alert.setText("a much longer message");

        assertTrue(alert.getPreferredWidth() > 2.0f * Alert.PADDING_HORIZONTAL);
    }

    @Test
    void explicitSizeWinsOverTheMessageSize() {
        Alert alert = new Alert(Alert.Level.INFO, "a very long message");
        alert.setWidth(50.0f);

        assertEquals(50.0f, alert.getPreferredWidth());
    }

    @Test
    void reportsItsAccessibleRoleAndName() {
        Alert alert = new Alert(Alert.Level.WARNING, "Careful");

        assertEquals(AccessibleRole.ALERT, alert.getAccessibleRole());
        assertEquals("Careful", alert.getAccessibleName());
    }

    @Test
    void isInertToPointerAndKeyboardInput() {
        Alert alert = new Alert(Alert.Level.INFO, "x");
        alert.setWidth(80.0f);
        alert.setHeight(24.0f);

        assertFalse(alert.onMouseEvent(mouse(MouseEventType.PRESS, 10.0f, 10.0f)));
        assertFalse(alert.isFocusable());
    }
}
