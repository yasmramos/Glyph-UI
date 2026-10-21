package com.glyphui.ui;

import com.glyphui.TestBase;
import com.glyphui.core.FocusManager;
import com.glyphui.events.GlyphKeys;
import com.glyphui.events.KeyEvent;
import com.glyphui.events.KeyEventType;
import com.glyphui.events.MouseButton;
import com.glyphui.events.MouseEvent;
import com.glyphui.events.MouseEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior tests for {@link Button} that deliberately avoid any Skija native
 * initialization: since the paint objects are now created lazily on the first
 * render(), construction and the whole mouse/keyboard state machine can be
 * exercised headlessly.
 */
public class ButtonBehaviorTest extends TestBase {

    private FocusManager focusManager;

    @BeforeEach
    public void installFocusManager() {
        focusManager = new FocusManager(() -> { /* repaint no-op in tests */ });
        FocusManager.setGlobalFocusManager(focusManager);
    }

    @AfterEach
    public void clearFocusManager() {
        FocusManager.setGlobalFocusManager(null);
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static KeyEvent key(KeyEventType type, int keyCode) {
        return new KeyEvent(type, keyCode, ' ', EnumSet.noneOf(com.glyphui.events.KeyModifier.class));
    }

    // ------------------------------------------------------------------
    // Phase 1 bug regression tests (test-first rule: each test below was
    // written to reproduce a real defect before the fix landed).
    // ------------------------------------------------------------------

    @Test
    public void stateColorGettersReturnTheirOwnStateRegardlessOfButtonState() {
        // Bug: getNormalColor()/getHoverColor()/getPressedColor() used to
        // resolve colors against the button's *current* state, so asking for
        // the normal color while the button hovered returned the hover color.
        Button button = new Button(0, 0, 100, 40, "Colors");
        int normal = 0xFF112233;
        int hover = 0xFF445566;
        int pressed = 0xFF778899;
        button.setNormalColor(normal);
        button.setHoverColor(hover);
        button.setPressedColor(pressed);

        // While idle, all three getters must agree with their names.
        assertEquals(ComponentState.IDLE, button.getState());
        assertEquals(normal, button.getNormalColor());
        assertEquals(hover, button.getHoverColor());
        assertEquals(pressed, button.getPressedColor());

        // The regression: hovering must not change what getNormalColor()
        // reports — it promises the NORMAL-state color, not the live one.
        button.setState(ComponentState.HOVER);
        assertEquals(ComponentState.HOVER, button.getState(), "test setup");
        assertEquals(normal, button.getNormalColor(),
                "getNormalColor() must return the normal color even while HOVERED");
        assertEquals(hover, button.getHoverColor());
        assertEquals(pressed, button.getPressedColor());

        // Same contract while PRESSED.
        button.setState(ComponentState.PRESSED);
        assertEquals(normal, button.getNormalColor());
        assertEquals(hover, button.getHoverColor());
        assertEquals(pressed, button.getPressedColor());
    }

    @Test
    public void widthPropertySetAndSetWidthBothMarkSizeExplicitlySet() {
        // Contract decision: widthProperty().set() and setWidth() are two
        // paths onto the SAME mutation — both mark bounds as explicitly
        // user-set so layout managers must respect them. setWidth() delegates
        // to the property internally, and the property's onChange hook
        // (applyWidthExplicit) sets the flag.
        Button contentSized = new Button("Auto");
        assertFalse(contentSized.isSizeExplicitlySet(),
                "content-sized constructor must leave layout free to size the button");

        contentSized.widthProperty().set(150f);
        assertTrue(contentSized.isSizeExplicitlySet(),
                "widthProperty().set() must mark sizeExplicitlySet like setWidth() does");
        assertEquals(150f, contentSized.getWidth());

        Button setterPath = new Button("Auto");
        setterPath.setWidth(150f);
        assertTrue(setterPath.isSizeExplicitlySet(),
                "setWidth() must mark sizeExplicitlySet");
        assertEquals(150f, setterPath.getWidth());

        // Height follows the same contract.
        Button heightPath = new Button("Auto");
        heightPath.heightProperty().set(60f);
        assertTrue(heightPath.isSizeExplicitlySet(),
                "heightProperty().set() must mark sizeExplicitlySet too");
        assertEquals(60f, heightPath.getHeight());
    }

    /** Attaches the button to a panel and gives it keyboard focus. */
    private Button focusedButton() {
        Panel root = new Panel(0, 0, 200, 100);
        focusManager.setRoot(root);
        Button button = new Button(10, 10, 100, 40, "Go");
        root.add(button);
        button.requestFocus();
        assertTrue(button.isFocused(), "test setup: button should hold focus");
        return button;
    }

    // ------------------------------------------------------------------
    // Lazy paints: no Skija touched until render()
    // ------------------------------------------------------------------

    @Test
    public void testConstructionAndEventsWithoutSkija() {
        // If paints were still created in the constructor this would blow up
        // with UnsatisfiedLinkError/ExceptionInInitializerError on hosts
        // without native libraries; here everything must just work.
        Button button = new Button(10, 10, 100, 40, "Click me");
        assertEquals("Click me", button.getText());

        AtomicInteger calls = new AtomicInteger();
        button.setOnClick(calls::incrementAndGet);

        button.onMouseEvent(mouse(MouseEventType.PRESS, 50, 30));
        assertEquals(ComponentState.PRESSED, button.getState());
        button.onMouseEvent(mouse(MouseEventType.RELEASE, 50, 30));
        assertEquals(1, calls.get());
        assertEquals(ComponentState.HOVER, button.getState());
    }

    @Test
    public void testTextBoundariesConstructorStillWorks() {
        Button button = new Button();
        assertEquals("", button.getText());
        Button textOnly = new Button("Hi");
        assertEquals("Hi", textOnly.getText());
    }

    // ------------------------------------------------------------------
    // Exception-safe click handling
    // ------------------------------------------------------------------

    @Test
    public void testThrowingHandlerDoesNotStuckPressedState() {
        Button button = new Button(10, 10, 100, 40, "Boom");
        button.setOnClick(() -> {
            throw new IllegalStateException("handler failure");
        });

        button.onMouseEvent(mouse(MouseEventType.PRESS, 50, 30));
        assertEquals(ComponentState.PRESSED, button.getState());

        // The release must not propagate the exception...
        assertDoesNotThrow(() -> button.onMouseEvent(mouse(MouseEventType.RELEASE, 50, 30)));
        // ...and the button must leave PRESSED regardless.
        assertEquals(ComponentState.HOVER, button.getState(),
                "A throwing handler must never leave the button stuck in PRESSED");

        // performClick() is equally defensive.
        assertDoesNotThrow(button::performClick);
    }

    @Test
    public void testPerformClickReturnsFalseWithoutHandler() {
        Button button = new Button("Noop");
        assertFalse(button.performClick());
        AtomicInteger calls = new AtomicInteger();
        button.setOnClick(calls::incrementAndGet);
        assertTrue(button.performClick());
        assertEquals(1, calls.get());
    }

    /**
     * Full performClick() contract (Phase 1 checklist): the return value
     * reports whether a handler was present and invoked, the handler runs
     * exactly once per call, and clearing the handler returns the button to
     * the "false" branch. Also pins that performClick() never mutates the
     * visual state machine — it is a programmatic trigger, not a press.
     */
    @Test
    public void performClickContractRunExactlyOnceAndBooleanResult() {
        Button button = new Button("Go");
        AtomicInteger calls = new AtomicInteger();

        // No handler: false, nothing happens.
        assertFalse(button.performClick(),
                "performClick without a handler must report false");
        assertEquals(0, calls.get());

        // With a handler: true, invoked exactly once per call.
        button.setOnClick(calls::incrementAndGet);
        assertTrue(button.performClick(),
                "performClick with a handler must report true");
        assertTrue(button.performClick());
        assertTrue(button.performClick());
        assertEquals(3, calls.get(), "one invocation per performClick() call");

        // State machine untouched by programmatic clicks.
        org.junit.jupiter.api.Assertions.assertEquals(ComponentState.IDLE,
                button.getState(),
                "performClick must not leave the button in PRESSED");

        // Clearing the handler restores the no-handler branch.
        button.clearOnClick();
        assertFalse(button.performClick(),
                "after clearOnClick() the button has no handler again");
        assertEquals(3, calls.get(), "no extra invocation after clearing");
    }

    // ------------------------------------------------------------------
    // Keyboard activation via GlyphKeys constants
    // ------------------------------------------------------------------

    @Test
    public void testEnterPressActivatesFocusedButton() {
        Button button = focusedButton();
        AtomicInteger calls = new AtomicInteger();
        button.setOnClick(calls::incrementAndGet);

        button.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.ENTER));
        assertEquals(1, calls.get());

        // Other keys must not activate the button.
        button.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.SPACE + 1));
        assertEquals(1, calls.get());
    }

    @Test
    public void testSpacePressDepressesAndReleaseActivates() {
        Button button = focusedButton();
        AtomicInteger calls = new AtomicInteger();
        button.setOnClick(calls::incrementAndGet);

        button.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.SPACE));
        assertEquals(ComponentState.PRESSED, button.getState(),
                "Space press should visually depress the button");
        assertEquals(0, calls.get(), "Space must activate on release, not press");

        button.onKeyEvent(key(KeyEventType.RELEASE, GlyphKeys.SPACE));
        assertEquals(1, calls.get());
        assertEquals(ComponentState.IDLE, button.getState());
    }

    @Test
    public void testKeyEventsIgnoredWhenUnfocusedOrDisabled() {
        Button button = focusedButton();
        AtomicInteger calls = new AtomicInteger();
        button.setOnClick(calls::incrementAndGet);

        focusManager.clearFocus(button);
        button.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.ENTER));
        assertEquals(0, calls.get(), "Unfocused buttons ignore Enter");

        button.requestFocus();
        button.setEnabled(false);
        button.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.ENTER));
        assertEquals(0, calls.get(), "Disabled buttons ignore Enter");
    }

    // ------------------------------------------------------------------
    // Accessible name fallbacks
    // ------------------------------------------------------------------

    @Test
    public void testAccessibleNameNeverNull() {
        Button nullText = new Button((String) null);
        assertNotNull(nullText.getAccessibleName());
        assertEquals("Button", nullText.getAccessibleName());

        Button emptyText = new Button("");
        assertEquals("Button", emptyText.getAccessibleName());

        Button captioned = new Button("Save");
        assertEquals("Save", captioned.getAccessibleName());
    }

    // ------------------------------------------------------------------
    // ClickListener unification
    // ------------------------------------------------------------------

    @Test
    public void testOnClickIsClickListener() {
        Button button = new Button("X");
        assertNull(button.getOnClick());

        ClickListener listener = () -> { };
        button.setOnClick(listener);
        assertSame(listener, button.getOnClick());

        // A legacy Runnable must be wrapped explicitly at the call site:
        // ClickListener and Runnable share the same abstract-method shape,
        // so no setOnClick(Runnable) overload exists (it would make every
        // lambda call site ambiguous).
        Runnable legacy = () -> { };
        button.setOnClick(legacy::run);
        assertNotNull(button.getOnClick());
        assertNotSame(listener, button.getOnClick());

        button.clearOnClick();
        assertNull(button.getOnClick());
    }
}
