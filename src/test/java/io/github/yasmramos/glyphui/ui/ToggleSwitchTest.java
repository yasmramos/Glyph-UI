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
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behaviour tests for {@link ToggleSwitch}. The paints are lazy, so the whole
 * mouse/keyboard state machine runs headlessly.
 */
class ToggleSwitchTest extends TestBase {

    private FocusManager focusManager;

    @BeforeEach
    void installFocusManager() {
        focusManager = new FocusManager(() -> { });
        FocusManager.setGlobalFocusManager(focusManager);
    }

    @AfterEach
    void clearFocusManager() {
        FocusManager.setGlobalFocusManager(null);
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static KeyEvent key(KeyEventType type, int keyCode) {
        return new KeyEvent(type, keyCode, ' ', EnumSet.noneOf(KeyModifier.class));
    }

    @Nested
    @DisplayName("state contract")
    class State {

        @Test
        @DisplayName("a fresh switch is off")
        void defaults() {
            ToggleSwitch sw = new ToggleSwitch();
            assertFalse(sw.isOn());
            assertTrue(sw.isFocusable());
            assertEquals(AccessibleRole.SWITCH, sw.getAccessibleRole());
            sw.dispose();
        }

        @Test
        @DisplayName("setOn notifies the listener only on real changes")
        void changeListener() {
            ToggleSwitch sw = new ToggleSwitch();
            AtomicReference<Boolean> last = new AtomicReference<>();
            int[] calls = {0};
            sw.setOnChange(v -> {
                last.set(v);
                calls[0]++;
            });

            sw.setOn(true);
            sw.setOn(true); // no change
            assertEquals(1, calls[0]);
            assertTrue(last.get());
            sw.dispose();
        }

        @Test
        @DisplayName("toggle flips the state")
        void toggleFlips() {
            ToggleSwitch sw = new ToggleSwitch();
            sw.toggle();
            assertTrue(sw.isOn());
            sw.toggle();
            assertFalse(sw.isOn());
            sw.dispose();
        }

        @Test
        @DisplayName("the caption is exposed as the accessible name")
        void accessibleName() {
            ToggleSwitch sw = new ToggleSwitch("Wi-Fi");
            assertEquals("Wi-Fi", sw.getAccessibleName());
            sw.dispose();
        }
    }

    @Nested
    @DisplayName("pointer interaction")
    class Pointer {

        @Test
        @DisplayName("a press+release inside toggles the switch")
        void clickToggles() {
            ToggleSwitch sw = new ToggleSwitch(0, 0, 100, 24);
            sw.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            sw.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));
            assertTrue(sw.isOn());
            sw.dispose();
        }

        @Test
        @DisplayName("a release outside does not toggle")
        void releaseOutsideDoesNotToggle() {
            ToggleSwitch sw = new ToggleSwitch(0, 0, 100, 24);
            sw.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            sw.onMouseEvent(mouse(MouseEventType.RELEASE, 500, 500));
            assertFalse(sw.isOn());
            sw.dispose();
        }

        @Test
        @DisplayName("a disabled switch ignores clicks")
        void disabledIgnoresClick() {
            ToggleSwitch sw = new ToggleSwitch(0, 0, 100, 24);
            sw.setEnabled(false);
            sw.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            sw.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));
            assertFalse(sw.isOn());
            sw.dispose();
        }
    }

    @Nested
    @DisplayName("keyboard interaction")
    class Keyboard {

        @Test
        @DisplayName("Space toggles a focused switch")
        void spaceToggles() {
            ToggleSwitch sw = new ToggleSwitch();
            sw.requestFocus();
            sw.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.SPACE));
            sw.onKeyEvent(key(KeyEventType.RELEASE, GlyphKeys.SPACE));
            assertTrue(sw.isOn());
            sw.dispose();
        }

        @Test
        @DisplayName("keys are ignored while unfocused")
        void unfocusedIgnoresKeys() {
            ToggleSwitch sw = new ToggleSwitch();
            sw.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.SPACE));
            sw.onKeyEvent(key(KeyEventType.RELEASE, GlyphKeys.SPACE));
            assertFalse(sw.isOn());
            sw.dispose();
        }

        @Test
        @DisplayName("an unrelated key does not toggle")
        void unrelatedKeyIgnored() {
            ToggleSwitch sw = new ToggleSwitch();
            sw.requestFocus();
            sw.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.ENTER));
            sw.onKeyEvent(key(KeyEventType.RELEASE, GlyphKeys.ENTER));
            assertFalse(sw.isOn());
            sw.dispose();
        }

        @Test
        @DisplayName("hover enters and leaves the hover state")
        void hoverTransitions() {
            ToggleSwitch sw = new ToggleSwitch(0, 0, 120, 24);
            sw.onMouseEvent(mouse(MouseEventType.MOVE, 10, 12));
            assertEquals(ComponentState.HOVER, sw.getState());
            sw.onMouseEvent(mouse(MouseEventType.MOVE, 500, 500));
            assertEquals(ComponentState.IDLE, sw.getState());
            sw.dispose();
        }

        @Test
        @DisplayName("a release without a matching press does not toggle")
        void releaseWithoutPressDoesNotToggle() {
            ToggleSwitch sw = new ToggleSwitch(0, 0, 120, 24);
            sw.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));
            assertFalse(sw.isOn());
            sw.dispose();
        }
    }

    @Nested
    @DisplayName("raster rendering")
    class Raster {

        private Surface[] surfaceHolder;
        private Canvas canvas;

        @BeforeAll
        static void checkSkija() {
            assumeTrue(RasterCanvasTestFactory.isAvailable(),
                    "Skija native library not loadable on this host; skipping raster tests");
        }

        @BeforeEach
        void setUp() {
            surfaceHolder = new Surface[1];
            canvas = RasterCanvasTestFactory.create(surfaceHolder);
        }

        @AfterEach
        void tearDown() {
            if (surfaceHolder[0] != null) {
                surfaceHolder[0].close();
            }
        }

        @Test
        @DisplayName("an on switch paints the accent track")
        void onPaintsAccent() {
            int accent = Theme.current().getAccentColor();

            ToggleSwitch off = new ToggleSwitch(0, 0, 120, 24);
            canvas.clear(0xFF0000FF);
            off.renderInternal(canvas);
            canvas.flush();
            boolean offAccent = anyPixelEquals(accent, 0, 36, 12);
            off.dispose();

            ToggleSwitch on = new ToggleSwitch(0, 0, 120, 24);
            on.setOn(true);
            canvas.clear(0xFF0000FF);
            on.renderInternal(canvas);
            canvas.flush();
            boolean onAccent = anyPixelEquals(accent, 0, 36, 12);
            on.dispose();

            assertFalse(offAccent, "an off switch must not paint the accent track");
            assertTrue(onAccent, "an on switch paints the accent track");
        }

        @Test
        @DisplayName("a disabled switch still renders without errors")
        void disabledRenders() {
            ToggleSwitch sw = new ToggleSwitch(0, 0, 120, 24);
            sw.setOn(true);
            sw.setEnabled(false);
            canvas.clear(0xFF0000FF);
            sw.renderInternal(canvas);
            canvas.flush();
            sw.dispose();
        }

        private boolean anyPixelEquals(int color, int x0, int x1, int y) {
            try (Pixmap pixmap = new Pixmap()) {
                assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
                for (int x = x0; x < x1; x++) {
                    if (pixmap.getColor(x, y) == color) {
                        return true;
                    }
                }
                return false;
            }
        }
    }
}
