package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Link}: caption, target, visited styling and
 * activation through the pointer and the keyboard.
 */
class LinkTest extends TestBase {

    @BeforeEach
    void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ' ',
                EnumSet.noneOf(KeyModifier.class));
    }

    private static Link sizedLink(String text) {
        Link link = new Link(text);
        link.setWidth(120.0f);
        link.setHeight(20.0f);
        return link;
    }

    @Test
    void defaultsToEmptyCaptionAndUnvisited() {
        Link link = new Link();

        assertEquals("", link.getText());
        assertEquals("", link.getTarget());
        assertFalse(link.isVisited());
        assertFalse(link.isAlwaysUnderline());
        assertTrue(link.isFocusable());
        assertEquals(AccessibleRole.LINK, link.getAccessibleRole());
    }

    @Test
    void nullTextAndTargetBecomeEmpty() {
        Link link = new Link(null);
        link.setTarget(null);

        assertEquals("", link.getText());
        assertEquals("", link.getTarget());
    }

    @Test
    void textPropertyDrivesTheCaption() {
        Link link = new Link("Docs");
        link.textProperty().set("Guides");

        assertEquals("Guides", link.getText());
        link.setText(null);
        assertEquals("", link.getText());
    }

    @Test
    void clickFiresTheHandlerOnce() {
        Link link = sizedLink("Open");
        AtomicInteger clicks = new AtomicInteger();
        link.setOnClick(clicks::incrementAndGet);

        link.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10));
        link.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 10));

        assertEquals(1, clicks.get());
    }

    @Test
    void releaseOutsideDoesNotFire() {
        Link link = sizedLink("Open");
        AtomicInteger clicks = new AtomicInteger();
        link.setOnClick(clicks::incrementAndGet);

        link.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10));
        link.onMouseEvent(mouse(MouseEventType.RELEASE, 500, 500));

        assertEquals(0, clicks.get());
    }

    @Test
    void enterAndSpaceActivateFromTheKeyboard() {
        Link link = sizedLink("Open");
        AtomicInteger clicks = new AtomicInteger();
        link.setOnClick(clicks::incrementAndGet);

        link.onKeyEvent(key(GlyphKeys.ENTER));
        link.onKeyEvent(key(GlyphKeys.SPACE));
        link.onKeyEvent(key(GlyphKeys.LEFT));

        assertEquals(2, clicks.get());
    }

    @Test
    void disabledLinkIgnoresInput() {
        Link link = sizedLink("Open");
        AtomicInteger clicks = new AtomicInteger();
        link.setOnClick(clicks::incrementAndGet);
        link.setEnabled(false);

        assertFalse(link.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10)));
        link.onKeyEvent(key(GlyphKeys.ENTER));

        assertEquals(0, clicks.get());
    }

    @Test
    void handlerFailureIsSwallowed() {
        Link link = sizedLink("Open");
        link.setOnClick(() -> {
            throw new IllegalStateException("boom");
        });

        link.onKeyEvent(key(GlyphKeys.ENTER));
    }

    @Test
    void visitedAndUnderlineTogglesAreSticky() {
        Link link = sizedLink("Open");

        link.setVisited(true);
        link.setAlwaysUnderline(true);

        assertTrue(link.isVisited());
        assertTrue(link.isAlwaysUnderline());
    }
}
