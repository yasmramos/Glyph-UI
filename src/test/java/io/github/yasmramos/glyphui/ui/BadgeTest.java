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
 * Behaviour tests for {@link Badge}: caption, sizing and inert input. No Skija
 * native code is touched because the paints are created lazily on the first
 * render.
 */
class BadgeTest extends TestBase {

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    @Test
    void defaultsToAnEmptyCaption() {
        Badge badge = new Badge();

        assertEquals("", badge.getText());
    }

    @Test
    void nullCaptionIsNormalisedToEmpty() {
        Badge badge = new Badge(null);

        assertEquals("", badge.getText());
    }

    @Test
    void captionDrivesTheIntrinsicWidth() {
        Badge badge = new Badge("1");

        badge.setText("1234567890");

        assertTrue(badge.getPreferredWidth() > 2.0f * Badge.PADDING_HORIZONTAL);
    }

    @Test
    void textPropertyKeepsTextInSync() {
        Badge badge = new Badge("a");

        badge.textProperty().set("b");

        assertEquals("b", badge.getText());
    }

    @Test
    void usesTheAccentBackgroundByDefault() {
        Badge badge = new Badge("x");

        assertEquals(badge.getTheme().getAccentColor(), badge.getBackgroundColor());
    }

    @Test
    void anExplicitBackgroundOverridesTheTheme() {
        Badge badge = new Badge("x");
        badge.setBackgroundColor(0xFF102030);

        assertEquals(0xFF102030, badge.getBackgroundColor());

        badge.setBackgroundColor(null);
        assertEquals(badge.getTheme().getAccentColor(), badge.getBackgroundColor());
    }

    @Test
    void reportsItsAccessibleRole() {
        Badge badge = new Badge("x");

        assertEquals(AccessibleRole.BADGE, badge.getAccessibleRole());
    }

    @Test
    void accessibleNameIsTheCaption() {
        Badge badge = new Badge("NEW");

        assertEquals("NEW", badge.getAccessibleName());
    }

    @Test
    void isInertToPointerAndKeyboardInput() {
        Badge badge = new Badge("x");
        badge.setWidth(40.0f);
        badge.setHeight(20.0f);

        assertFalse(badge.onMouseEvent(mouse(MouseEventType.PRESS, 10.0f, 10.0f)));
        assertFalse(badge.isFocusable());
    }

    @Test
    void explicitSizeWinsOverTheCaptionSize() {
        Badge badge = new Badge("a very long caption");
        badge.setWidth(30.0f);

        assertEquals(30.0f, badge.getPreferredWidth());
    }

    @Test
    void disabledBadgeDiffersFromEnabledOne() {
        Badge badge = new Badge("x");

        assertNotEquals(badge.getTheme().getControlTextDisabledColor(),
                badge.getTextColor());
    }
}
