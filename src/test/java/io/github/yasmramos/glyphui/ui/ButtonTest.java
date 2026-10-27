package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ComponentState;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the real {@link Button} widget: geometry, hit-testing and
 * the full click state machine (hover / pressed / release-inside /
 * release-outside / disabled).
 *
 * <p>These tests use the production {@code Button} directly. Since paints
 * are created lazily on the first {@code render()}, construction and event
 * handling run without any Skija native libraries, so no assumptions are
 * needed here.</p>
 *
 * <p>Historical note: this file previously tested a hand-written
 * {@code MockButton} that re-implemented the state machine inside the test.
 * That mock could drift from the real widget and pass while the actual
 * {@code Button} was broken — the tests were asserting the mock's copy of
 * the logic, not the product's. The mock has been deleted and every case
 * now exercises {@code Button} itself.</p>
 */
public class ButtonTest extends TestBase {

    /**
     * Installs a fresh global focus manager so keyboard-activation tests can
     * give buttons real focus. {@link TestBase} clears it after each test.
     */
    @BeforeEach
    public void installFocusManager() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { /* repaint no-op */ }));
    }

    private static MouseEvent mouse(MouseEventType type, int x, int y) {
        return new MouseEvent(type, x, y, MouseButton.LEFT, 1);
    }

    /** Builds a button with an explicit size so hit-testing is deterministic. */
    private Button buttonWithClick(AtomicInteger clicks) {
        Button button = new Button(10, 10, 100, 40, "Click me");
        button.setOnClick(() -> clicks.incrementAndGet());
        return button;
    }

    @Test
    public void testButtonInitialState() {
        Button button = new Button(10, 10, 100, 40, "Hello");

        assertEquals(10f, button.getX());
        assertEquals(10f, button.getY());
        assertEquals(100f, button.getWidth());
        assertEquals(40f, button.getHeight());
        assertTrue(button.isVisible());
        assertTrue(button.isEnabled());
        assertEquals(ComponentState.IDLE, button.getState());
        assertEquals("Hello", button.getText());
    }

    @Test
    public void testButtonContainsPoint() {
        Button button = new Button(10, 10, 100, 40, "Hit test");

        // Point inside button
        assertTrue(button.contains(50, 30), "Point (50,30) should be inside button");
        assertTrue(button.contains(10, 10), "Top-left corner should be inside");
        assertTrue(button.contains(109, 49), "Point just inside the bottom-right edge should be inside");

        // Point outside button
        assertFalse(button.contains(5, 30), "Point left of button should be outside");
        assertFalse(button.contains(50, 5), "Point above button should be outside");
        assertFalse(button.contains(115, 30), "Point right of button should be outside");
        assertFalse(button.contains(50, 55), "Point below button should be outside");
    }

    @Test
    public void testButtonClickStateMachinePressInsideReleaseInside() {
        AtomicInteger clicks = new AtomicInteger();
        Button button = buttonWithClick(clicks);

        // Press inside button
        button.onMouseEvent(mouse(MouseEventType.PRESS, 50, 30));

        assertEquals(ComponentState.PRESSED, button.getState(),
                "Button should be PRESSED after press inside");
        assertEquals(0, clicks.get(), "onClick must not fire on press");

        // Release inside button
        button.onMouseEvent(mouse(MouseEventType.RELEASE, 50, 30));

        assertEquals(1, clicks.get(), "onClick should fire when released inside after press");
        assertEquals(ComponentState.HOVER, button.getState(),
                "Button should be HOVER after release inside (pointer still over it)");
    }

    @Test
    public void testButtonClickStateMachinePressInsideReleaseOutside() {
        AtomicInteger clicks = new AtomicInteger();
        Button button = buttonWithClick(clicks);

        button.onMouseEvent(mouse(MouseEventType.PRESS, 50, 30));
        assertEquals(ComponentState.PRESSED, button.getState());

        // Move outside and release there
        button.onMouseEvent(mouse(MouseEventType.MOVE, 200, 200));
        button.onMouseEvent(mouse(MouseEventType.RELEASE, 200, 200));

        assertEquals(0, clicks.get(), "onClick must NOT fire when released outside");
        assertEquals(ComponentState.IDLE, button.getState(),
                "Button should return to IDLE after release outside");
    }

    @Test
    public void testButtonHoverState() {
        Button button = new Button(10, 10, 100, 40, "Hover");

        button.onMouseEvent(mouse(MouseEventType.MOVE, 50, 30));

        assertEquals(ComponentState.HOVER, button.getState(),
                "Button should be HOVER when the mouse moves over it");
    }

    @Test
    public void testButtonDisabledDoesNotHandleEvents() {
        AtomicInteger clicks = new AtomicInteger();
        Button button = buttonWithClick(clicks);
        button.setEnabled(false);

        boolean consumed = button.onMouseEvent(mouse(MouseEventType.PRESS, 50, 30));

        assertFalse(consumed, "Disabled button must not consume events");
        assertEquals(ComponentState.IDLE, button.getState(),
                "Disabled button should remain IDLE");
        assertEquals(0, clicks.get(), "Disabled button should not execute onClick");
    }

    /**
     * Gives the button real keyboard focus through the global FocusManager
     * (installed by {@link #focusButton}). The button's key handler is a
     * no-op unless {@code isFocused()} is true, exactly like a real app.
     */
    private Button focusButton(Button button) {
        Panel root = new Panel(0, 0, 200, 100);
        root.add(button);
        FocusManager fm = FocusManager.getGlobalFocusManager();
        assertNotNull(fm, "test setup: global focus manager must be installed");
        assertTrue(fm.requestFocus(button), "test setup: button should accept focus");
        assertTrue(button.isFocused(), "test setup: button should hold focus");
        return button;
    }

    @Test
    public void testEnterKeyTriggersClickWhenFocused() {
        AtomicInteger clicks = new AtomicInteger();
        Button button = focusButton(buttonWithClick(clicks));

        KeyEvent enter = new KeyEvent(KeyEventType.PRESS, GlyphKeys.ENTER, ' ',
                EnumSet.noneOf(io.github.yasmramos.glyphui.events.KeyModifier.class));
        button.onKeyEvent(enter);

        assertEquals(1, clicks.get(), "Enter must activate a focused button");
    }

    @Test
    public void testUnfocusedButtonIgnoresActivationKeys() {
        AtomicInteger clicks = new AtomicInteger();
        Button button = buttonWithClick(clicks);
        // Deliberately NOT focused: keys routed elsewhere must not click it.
        assertFalse(button.isFocused(), "test setup: button must not hold focus");

        button.onKeyEvent(new KeyEvent(KeyEventType.PRESS, GlyphKeys.ENTER, ' ',
                EnumSet.noneOf(io.github.yasmramos.glyphui.events.KeyModifier.class)));
        button.onKeyEvent(new KeyEvent(KeyEventType.PRESS, GlyphKeys.SPACE, ' ',
                EnumSet.noneOf(io.github.yasmramos.glyphui.events.KeyModifier.class)));

        assertEquals(0, clicks.get(), "Unfocused button must ignore Enter/Space");
        assertEquals(ComponentState.IDLE, button.getState(),
                "Unfocused button must not depress on keys");
    }

    @Test
    public void testMousePressFocusesWithoutShowingTheFocusRing() {
        Button button = buttonWithClick(new AtomicInteger());

        button.onMouseEvent(mouse(MouseEventType.PRESS, 50, 30));

        assertTrue(button.isFocused(), "a press must still focus the button");
        assertFalse(button.isFocusVisible(),
                "a mouse click must not leave a dashed focus ring");
    }

    @Test
    public void testProgrammaticFocusShowsTheFocusRing() {
        Button button = focusButton(buttonWithClick(new AtomicInteger()));

        assertTrue(button.isFocusVisible(),
                "keyboard/programmatic focus must show where it is");
    }

    @Test
    public void testSpaceKeyReleasesAndTriggersClick() {
        AtomicInteger clicks = new AtomicInteger();
        Button button = focusButton(buttonWithClick(clicks));

        KeyEvent spacePress = new KeyEvent(KeyEventType.PRESS, GlyphKeys.SPACE, ' ',
                EnumSet.noneOf(io.github.yasmramos.glyphui.events.KeyModifier.class));
        KeyEvent spaceRelease = new KeyEvent(KeyEventType.RELEASE, GlyphKeys.SPACE, ' ',
                EnumSet.noneOf(io.github.yasmramos.glyphui.events.KeyModifier.class));

        button.onKeyEvent(spacePress);
        assertEquals(ComponentState.PRESSED, button.getState(),
                "Space press should visually depress the button");
        assertEquals(0, clicks.get(), "Space must not fire onClick on press");

        button.onKeyEvent(spaceRelease);
        assertEquals(1, clicks.get(), "Space release must fire onClick");
    }

    @Test
    public void testOnClickExceptionDoesNotLeaveButtonPressed() {
        Button button = new Button(10, 10, 100, 40, "Boom");
        button.setOnClick(() -> {
            throw new IllegalStateException("handler failure");
        });

        button.onMouseEvent(mouse(MouseEventType.PRESS, 50, 30));
        assertEquals(ComponentState.PRESSED, button.getState());

        assertDoesNotThrow(() -> button.onMouseEvent(mouse(MouseEventType.RELEASE, 50, 30)),
                "Handler exceptions must not propagate through the event pipeline");

        assertNotEquals(ComponentState.PRESSED, button.getState(),
                "A throwing handler must never leave the button stuck in PRESSED");
    }
}
