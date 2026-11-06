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
 * Behaviour tests for {@link Slider}: range/step clamping, pointer dragging,
 * keyboard nudging and the change listener. No Skija native code is touched
 * because the paints are created lazily on the first render.
 */
class SliderTest extends TestBase {

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
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT,
                type == MouseEventType.DRAG ? 0 : 1);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ' ',
                EnumSet.noneOf(KeyModifier.class));
    }

    @Nested
    @DisplayName("value and range")
    class ValueAndRange {

        @Test
        @DisplayName("a fresh slider spans [0, 1] and starts at 0")
        void defaults() {
            Slider slider = new Slider();
            assertEquals(0.0f, slider.getMin(), 1e-6f);
            assertEquals(1.0f, slider.getMax(), 1e-6f);
            assertEquals(0.0f, slider.getValue(), 1e-6f);
            assertEquals(0.0f, slider.getStep(), 1e-6f);
            assertTrue(slider.isFocusable());
            assertEquals(AccessibleRole.SLIDER, slider.getAccessibleRole());
            slider.dispose();
        }

        @Test
        @DisplayName("setValue clamps into the range")
        void clampsValue() {
            Slider slider = new Slider();
            slider.setValue(5.0f);
            assertEquals(1.0f, slider.getValue(), 1e-6f);
            slider.setValue(-5.0f);
            assertEquals(0.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("setRange normalises swapped bounds and re-clamps the value")
        void normalisesRange() {
            Slider slider = new Slider();
            slider.setValue(0.5f);
            slider.setRange(10.0f, 2.0f);
            assertEquals(2.0f, slider.getMin(), 1e-6f);
            assertEquals(10.0f, slider.getMax(), 1e-6f);
            assertEquals(2.0f, slider.getValue(), 1e-6f, "value re-clamped into the new range");
            slider.dispose();
        }

        @Test
        @DisplayName("a positive step snaps values to the grid")
        void snapsToStep() {
            Slider slider = new Slider();
            slider.setRange(0.0f, 10.0f);
            slider.setStep(2.0f);
            slider.setValue(3.1f);
            assertEquals(4.0f, slider.getValue(), 1e-6f);
            slider.setValue(2.9f);
            assertEquals(2.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("a negative or NaN step is treated as continuous")
        void nonPositiveStepIsContinuous() {
            Slider slider = new Slider();
            slider.setStep(-1.0f);
            assertEquals(0.0f, slider.getStep(), 1e-6f);
            slider.setValue(0.37f);
            assertEquals(0.37f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("the change listener fires only on real changes")
        void changeListener() {
            Slider slider = new Slider();
            AtomicReference<Float> last = new AtomicReference<>();
            int[] calls = {0};
            slider.setOnChange(v -> {
                last.set(v);
                calls[0]++;
            });

            slider.setValue(0.5f);
            slider.setValue(0.5f); // no change
            assertEquals(1, calls[0], "duplicate values must not notify");
            assertEquals(0.5f, last.get(), 1e-6f);
            slider.dispose();
        }
    }

    @Nested
    @DisplayName("pointer interaction")
    class Pointer {

        @Test
        @DisplayName("pressing on the track jumps the thumb to that position")
        void pressJumpsToPosition() {
            Slider slider = new Slider(0, 0, 200, 24);
            // Mid-track press should land near the middle of the range.
            slider.onMouseEvent(mouse(MouseEventType.PRESS, 100, 12));
            assertEquals(0.5f, slider.getValue(), 0.05f);
            slider.dispose();
        }

        @Test
        @DisplayName("dragging keeps updating the value")
        void dragUpdatesValue() {
            Slider slider = new Slider(0, 0, 200, 24);
            slider.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            float afterPress = slider.getValue();
            slider.onMouseEvent(mouse(MouseEventType.DRAG, 190, 12));
            assertTrue(slider.getValue() > afterPress,
                    "dragging right must increase the value");
            assertEquals(1.0f, slider.getValue(), 0.05f);
            slider.dispose();
        }

        @Test
        @DisplayName("a drag with no prior press is ignored")
        void dragWithoutPressIsIgnored() {
            Slider slider = new Slider(0, 0, 200, 24);
            slider.setValue(0.25f);
            boolean consumed = slider.onMouseEvent(mouse(MouseEventType.DRAG, 190, 12));
            assertFalse(consumed, "no press means no drag session");
            assertEquals(0.25f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("a disabled slider ignores pointer input")
        void disabledIgnoresPointer() {
            Slider slider = new Slider(0, 0, 200, 24);
            slider.setEnabled(false);
            slider.onMouseEvent(mouse(MouseEventType.PRESS, 190, 12));
            assertEquals(0.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("hover enters and leaves the hover state")
        void hoverTransitions() {
            Slider slider = new Slider(0, 0, 200, 24);
            slider.onMouseEvent(mouse(MouseEventType.MOVE, 100, 12));
            assertEquals(ComponentState.HOVER, slider.getState());
            slider.onMouseEvent(mouse(MouseEventType.MOVE, 500, 500));
            assertEquals(ComponentState.IDLE, slider.getState());
            slider.dispose();
        }

        @Test
        @DisplayName("a release ends the drag session")
        void releaseEndsDrag() {
            Slider slider = new Slider(0, 0, 200, 24);
            slider.onMouseEvent(mouse(MouseEventType.PRESS, 100, 12));
            slider.onMouseEvent(mouse(MouseEventType.RELEASE, 100, 12));
            // A later drag must be ignored now that the session ended.
            float before = slider.getValue();
            boolean consumed = slider.onMouseEvent(mouse(MouseEventType.DRAG, 10, 12));
            assertFalse(consumed);
            assertEquals(before, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("NaN value falls back to the minimum")
        void nanValueFallsBack() {
            Slider slider = new Slider();
            slider.setValue(Float.NaN);
            assertEquals(0.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }
    }

    @Nested
    @DisplayName("keyboard interaction")
    class Keyboard {

        @Test
        @DisplayName("arrow keys nudge by one step")
        void arrowsNudge() {
            Slider slider = new Slider();
            slider.setRange(0.0f, 10.0f);
            slider.setStep(1.0f);
            slider.setValue(5.0f);
            slider.requestFocus();

            slider.onKeyEvent(key(GlyphKeys.RIGHT));
            assertEquals(6.0f, slider.getValue(), 1e-6f);
            slider.onKeyEvent(key(GlyphKeys.LEFT));
            slider.onKeyEvent(key(GlyphKeys.LEFT));
            assertEquals(4.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("Home and End jump to the bounds")
        void homeEnd() {
            Slider slider = new Slider();
            slider.setRange(0.0f, 10.0f);
            slider.requestFocus();

            slider.onKeyEvent(key(GlyphKeys.END));
            assertEquals(10.0f, slider.getValue(), 1e-6f);
            slider.onKeyEvent(key(GlyphKeys.HOME));
            assertEquals(0.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("Page Up/Down move by ten steps")
        void pageUpDown() {
            Slider slider = new Slider();
            slider.setRange(0.0f, 100.0f);
            slider.setStep(1.0f);
            slider.setValue(50.0f);
            slider.requestFocus();

            slider.onKeyEvent(key(GlyphKeys.PAGE_UP));
            assertEquals(60.0f, slider.getValue(), 1e-6f);
            slider.onKeyEvent(key(GlyphKeys.PAGE_DOWN));
            assertEquals(50.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("keys are ignored while unfocused")
        void unfocusedIgnoresKeys() {
            Slider slider = new Slider();
            slider.setRange(0.0f, 10.0f);
            slider.setStep(1.0f);
            slider.setValue(5.0f);
            slider.onKeyEvent(key(GlyphKeys.RIGHT));
            assertEquals(5.0f, slider.getValue(), 1e-6f);
            slider.dispose();
        }

        @Test
        @DisplayName("an unknown key is ignored")
        void unknownKeyIgnored() {
            Slider slider = new Slider();
            slider.setValue(0.5f);
            slider.requestFocus();
            slider.onKeyEvent(key('x'));
            assertEquals(0.5f, slider.getValue(), 1e-6f);
            slider.dispose();
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
        @DisplayName("the fill grows into the right half as the value grows")
        void fillGrowsWithValue() {
            int accent = Theme.current().getAccentColor();

            Slider low = new Slider(0, 0, 200, 24);
            low.setValue(0.0f);
            canvas.clear(0xFF0000FF);
            low.renderInternal(canvas);
            canvas.flush();
            boolean lowRight = anyAccentInRange(accent, 120, 180, 12);
            low.dispose();

            Slider high = new Slider(0, 0, 200, 24);
            high.setValue(1.0f);
            canvas.clear(0xFF0000FF);
            high.renderInternal(canvas);
            canvas.flush();
            boolean highRight = anyAccentInRange(accent, 120, 180, 12);
            high.dispose();

            assertFalse(lowRight, "an empty slider must not fill the right half");
            assertTrue(highRight, "a full slider fills the right half");
        }

        @Test
        @DisplayName("a disabled slider still renders without errors")
        void disabledRenders() {
            Slider slider = new Slider(0, 0, 200, 24);
            slider.setValue(0.5f);
            slider.setEnabled(false);
            canvas.clear(0xFF0000FF);
            slider.renderInternal(canvas);
            canvas.flush();
            slider.dispose();
        }

        private boolean anyAccentInRange(int color, int x0, int x1, int y) {
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
