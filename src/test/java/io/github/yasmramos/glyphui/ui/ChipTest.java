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
 * Behaviour tests for {@link Chip}: caption, selected state, click/remove
 * handlers and keyboard activation. No Skija native code is touched because
 * the paints are created lazily on the first render.
 */
class ChipTest extends TestBase {

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

    private static Chip sizedChip(String text) {
        Chip chip = new Chip(text);
        chip.setWidth(80.0f);
        chip.setHeight(24.0f);
        return chip;
    }

    @Test
    void defaultsToEmptyCaptionAndUnselected() {
        Chip chip = new Chip();

        assertEquals("", chip.getText());
        assertFalse(chip.isSelected());
        assertFalse(chip.isRemovable());
    }

    @Test
    void nullTextBecomesEmpty() {
        Chip chip = new Chip(null);

        assertEquals("", chip.getText());
    }

    @Test
    void textPropertyKeepsCaptionInSync() {
        Chip chip = new Chip("a");

        chip.textProperty().set("b");

        assertEquals("b", chip.getText());
    }

    @Test
    void clickTogglesSelectionAndRunsTheHandler() {
        Chip chip = sizedChip("Filter");
        AtomicInteger clicks = new AtomicInteger();
        chip.setOnClick(clicks::incrementAndGet);

        chip.onMouseEvent(mouse(MouseEventType.PRESS, 10.0f, 10.0f));
        chip.onMouseEvent(mouse(MouseEventType.RELEASE, 10.0f, 10.0f));

        assertTrue(chip.isSelected());
        assertEquals(1, clicks.get());

        chip.onMouseEvent(mouse(MouseEventType.PRESS, 10.0f, 10.0f));
        chip.onMouseEvent(mouse(MouseEventType.RELEASE, 10.0f, 10.0f));

        assertFalse(chip.isSelected());
        assertEquals(2, clicks.get());
    }

    @Test
    void removableChipFiresRemoveOnTheCloseAffordance() {
        Chip chip = sizedChip("Tag");
        chip.setRemovable(true);
        AtomicInteger removes = new AtomicInteger();
        AtomicInteger clicks = new AtomicInteger();
        chip.setOnRemove(removes::incrementAndGet);
        chip.setOnClick(clicks::incrementAndGet);

        float closeX = chip.getWidth() - Chip.PADDING_HORIZONTAL - 4.0f;
        chip.onMouseEvent(mouse(MouseEventType.PRESS, closeX, 10.0f));
        chip.onMouseEvent(mouse(MouseEventType.RELEASE, closeX, 10.0f));

        assertEquals(1, removes.get());
        assertEquals(0, clicks.get());
        assertFalse(chip.isSelected());
    }

    @Test
    void pressingOutsideDoesNotActivate() {
        Chip chip = sizedChip("Tag");
        AtomicInteger clicks = new AtomicInteger();
        chip.setOnClick(clicks::incrementAndGet);

        assertFalse(chip.onMouseEvent(mouse(MouseEventType.PRESS, 200.0f, 10.0f)));
        assertEquals(0, clicks.get());
    }

    @Test
    void keyboardActivatesTheChip() {
        Chip chip = sizedChip("Tag");
        AtomicInteger clicks = new AtomicInteger();
        chip.setOnClick(clicks::incrementAndGet);

        chip.onKeyEvent(key(GlyphKeys.SPACE));
        chip.onKeyEvent(key(GlyphKeys.ENTER));

        assertFalse(chip.isSelected());
        assertEquals(2, clicks.get());
    }

    @Test
    void captionDrivesTheIntrinsicWidth() {
        Chip chip = new Chip("ok");
        float shortWidth = chip.getPreferredWidth();

        chip.setText("a much longer caption");

        assertTrue(chip.getPreferredWidth() > shortWidth);
    }

    @Test
    void reportsItsAccessibleRoleAndName() {
        Chip chip = new Chip("Beta");

        assertEquals(AccessibleRole.CHIP, chip.getAccessibleRole());
        assertEquals("Beta", chip.getAccessibleName());
    }

    @Test
    void aThrowingHandlerLeavesTheStateConsistent() {
        Chip chip = sizedChip("Tag");
        chip.setOnClick(() -> {
            throw new IllegalStateException("boom");
        });

        chip.onMouseEvent(mouse(MouseEventType.PRESS, 10.0f, 10.0f));
        chip.onMouseEvent(mouse(MouseEventType.RELEASE, 10.0f, 10.0f));

        assertTrue(chip.isSelected());
    }
}
