package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Image;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Behaviour tests for {@link Avatar}: initials, disc color derivation, sizing
 * and inert input. No Skija native code is touched because the paints are
 * created lazily on the first render.
 */
class AvatarTest extends TestBase {

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static Image tinyImage(int w, int h) throws IOException {
        java.awt.image.BufferedImage awt =
                new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(awt, "png", out);
        return Image.loadBytes(out.toByteArray());
    }

    @Test
    void defaultsToEmptyInitialsAndNoImage() {
        Avatar avatar = new Avatar();

        assertEquals("", avatar.getInitials());
        assertNull(avatar.getImage());
    }

    @Test
    void nullInitialsAreNormalisedToEmpty() {
        Avatar avatar = new Avatar(null);

        assertEquals("", avatar.getInitials());
    }

    @Test
    void defaultsToTheStandardDiameter() {
        Avatar avatar = new Avatar("AB");

        assertEquals(Avatar.DEFAULT_SIZE, avatar.getPreferredWidth());
        assertEquals(Avatar.DEFAULT_SIZE, avatar.getPreferredHeight());
    }

    @Test
    void initialsPropertyKeepsInitialsInSync() {
        Avatar avatar = new Avatar("A");

        avatar.initialsProperty().set("B");

        assertEquals("B", avatar.getInitials());
    }

    @Test
    void theSameInitialsKeepTheSameDiscColor() {
        Avatar one = new Avatar("AB");
        Avatar two = new Avatar("AB");

        assertEquals(one.getDiscColor(), two.getDiscColor());
    }

    @Test
    void differentInitialsUsuallyDifferInDiscColor() {
        Avatar one = new Avatar("AB");
        Avatar two = new Avatar("ZZ");

        assertNotEquals(one.getDiscColor(), two.getDiscColor());
    }

    @Test
    void anExplicitDiscColorOverridesTheDerivedOne() {
        Avatar avatar = new Avatar("AB");
        avatar.setDiscColor(0xFF102030);

        assertEquals(0xFF102030, avatar.getDiscColor());

        avatar.setDiscColor(null);
        assertEquals(new Avatar("AB").getDiscColor(), avatar.getDiscColor());
    }

    @Test
    void imageRoundTrip() throws IOException {
        Avatar avatar = new Avatar("AB");
        try (Image img = tinyImage(8, 8)) {
            avatar.setImage(img);

            assertSame(img, avatar.getImage());
        }
    }

    @Test
    void reportsItsAccessibleRoleAndName() {
        Avatar avatar = new Avatar("JD");

        assertEquals(AccessibleRole.AVATAR, avatar.getAccessibleRole());
        assertEquals("JD", avatar.getAccessibleName());
    }

    @Test
    void isInertToPointerAndKeyboardInput() {
        Avatar avatar = new Avatar("AB");

        assertFalse(avatar.onMouseEvent(mouse(MouseEventType.PRESS, 5.0f, 5.0f)));
        assertFalse(avatar.isFocusable());
    }
}
