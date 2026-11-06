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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behaviour tests for {@link RadioButton} and {@link RadioGroup}: mutual
 * exclusivity, pointer/keyboard selection and the change listeners.
 */
class RadioButtonTest extends TestBase {

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
    @DisplayName("standalone button")
    class Standalone {

        @Test
        @DisplayName("a fresh button is unselected and focusable")
        void defaults() {
            RadioButton rb = new RadioButton();
            assertFalse(rb.isSelected());
            assertNull(rb.getGroup());
            assertTrue(rb.isFocusable());
            assertEquals(AccessibleRole.RADIO_BUTTON, rb.getAccessibleRole());
            rb.dispose();
        }

        @Test
        @DisplayName("setSelected toggles freely outside a group")
        void freeToggling() {
            RadioButton rb = new RadioButton();
            rb.setSelected(true);
            assertTrue(rb.isSelected());
            rb.setSelected(false);
            assertFalse(rb.isSelected());
            rb.dispose();
        }

        @Test
        @DisplayName("clicking selects and notifies the listener")
        void clickSelects() {
            RadioButton rb = new RadioButton(0, 0, 100, 24);
            AtomicReference<Boolean> last = new AtomicReference<>();
            rb.setOnChange(last::set);

            rb.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            rb.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));

            assertTrue(rb.isSelected());
            assertEquals(Boolean.TRUE, last.get());
            rb.dispose();
        }

        @Test
        @DisplayName("Space selects a focused button")
        void spaceSelects() {
            RadioButton rb = new RadioButton();
            rb.requestFocus();
            rb.onKeyEvent(key(KeyEventType.PRESS, GlyphKeys.SPACE));
            rb.onKeyEvent(key(KeyEventType.RELEASE, GlyphKeys.SPACE));
            assertTrue(rb.isSelected());
            rb.dispose();
        }

        @Test
        @DisplayName("hover enters and leaves the hover state")
        void hoverTransitions() {
            RadioButton rb = new RadioButton(0, 0, 100, 24);
            rb.onMouseEvent(mouse(MouseEventType.MOVE, 10, 12));
            assertEquals(ComponentState.HOVER, rb.getState());
            rb.onMouseEvent(mouse(MouseEventType.MOVE, 500, 500));
            assertEquals(ComponentState.IDLE, rb.getState());
            rb.dispose();
        }

        @Test
        @DisplayName("a release outside does not select")
        void releaseOutsideDoesNotSelect() {
            RadioButton rb = new RadioButton(0, 0, 100, 24);
            rb.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            rb.onMouseEvent(mouse(MouseEventType.RELEASE, 500, 500));
            assertFalse(rb.isSelected());
            rb.dispose();
        }

        @Test
        @DisplayName("a disabled button ignores clicks")
        void disabledIgnoresClick() {
            RadioButton rb = new RadioButton(0, 0, 100, 24);
            rb.setEnabled(false);
            rb.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            rb.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));
            assertFalse(rb.isSelected());
            rb.dispose();
        }
    }

    @Nested
    @DisplayName("group exclusivity")
    class Groups {

        @Test
        @DisplayName("selecting a member deselects the others")
        void exclusivity() {
            RadioGroup group = new RadioGroup();
            RadioButton a = new RadioButton("A");
            RadioButton b = new RadioButton("B");
            RadioButton c = new RadioButton("C");
            group.add(a);
            group.add(b);
            group.add(c);

            assertEquals(3, group.size());

            a.setSelected(true);
            assertTrue(a.isSelected());
            assertFalse(b.isSelected());
            assertFalse(c.isSelected());
            assertSame(a, group.getSelected());

            c.setSelected(true);
            assertFalse(a.isSelected());
            assertTrue(c.isSelected());
            assertSame(c, group.getSelected());

            a.dispose();
            b.dispose();
            c.dispose();
        }

        @Test
        @DisplayName("the group listener fires on selection changes")
        void groupListener() {
            RadioGroup group = new RadioGroup();
            RadioButton a = new RadioButton("A");
            RadioButton b = new RadioButton("B");
            group.add(a);
            group.add(b);

            AtomicReference<RadioButton> last = new AtomicReference<>();
            group.setOnChange(last::set);

            b.setSelected(true);
            assertSame(b, last.get());

            a.setSelected(true);
            assertSame(a, last.get());

            a.dispose();
            b.dispose();
        }

        @Test
        @DisplayName("a button cannot be deselected directly inside a group")
        void groupedButtonStaysSelected() {
            RadioGroup group = new RadioGroup();
            RadioButton a = new RadioButton("A");
            group.add(a);

            a.setSelected(true);
            a.setSelected(false);

            assertTrue(a.isSelected(),
                    "the group keeps exactly one selection once made");
            a.dispose();
        }

        @Test
        @DisplayName("adding an already-selected button keeps exclusivity")
        void addingSelectedButtonDeselectsOthers() {
            RadioGroup group = new RadioGroup();
            RadioButton a = new RadioButton("A");
            RadioButton b = new RadioButton("B");
            group.add(a);
            a.setSelected(true);

            b.setSelected(true);
            group.add(b);

            assertFalse(a.isSelected());
            assertTrue(b.isSelected());

            a.dispose();
            b.dispose();
        }

        @Test
        @DisplayName("clearSelection empties the group")
        void clearSelection() {
            RadioGroup group = new RadioGroup();
            RadioButton a = new RadioButton("A");
            group.add(a);
            a.setSelected(true);

            group.clearSelection();
            assertNull(group.getSelected());
            assertFalse(a.isSelected());

            a.dispose();
        }

        @Test
        @DisplayName("remove detaches a button from the group")
        void removeDetaches() {
            RadioGroup group = new RadioGroup();
            RadioButton a = new RadioButton("A");
            group.add(a);
            group.remove(a);

            assertNull(a.getGroup());
            assertEquals(0, group.size());
            // Detached: it can now toggle freely.
            a.setSelected(true);
            a.setSelected(false);
            assertFalse(a.isSelected());
            a.dispose();
        }

        @Test
        @DisplayName("selecting a foreign button is rejected")
        void foreignButtonRejected() {
            RadioGroup group = new RadioGroup();
            RadioButton foreign = new RadioButton("X");
            assertThrows(IllegalArgumentException.class, () -> group.select(foreign));
            foreign.dispose();
        }

        @Test
        @DisplayName("adding a null button is rejected")
        void nullButtonRejected() {
            RadioGroup group = new RadioGroup();
            assertThrows(IllegalArgumentException.class, () -> group.add(null));
        }

        @Test
        @DisplayName("adding the same button twice is a no-op")
        void duplicateAddIsNoOp() {
            RadioGroup group = new RadioGroup();
            RadioButton a = new RadioButton("A");
            group.add(a);
            group.add(a);
            assertEquals(1, group.size());
            a.dispose();
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
        @DisplayName("a selected button paints the accent indicator")
        void selectedPaintsAccent() {
            int accent = Theme.current().getAccentColor();

            RadioButton off = new RadioButton(0, 0, 120, 24);
            canvas.clear(0xFF0000FF);
            off.renderInternal(canvas);
            canvas.flush();
            boolean offAccent = anyPixelEquals(accent, 0, 20, 12);
            off.dispose();

            RadioButton on = new RadioButton(0, 0, 120, 24);
            on.setSelected(true);
            canvas.clear(0xFF0000FF);
            on.renderInternal(canvas);
            canvas.flush();
            boolean onAccent = anyPixelEquals(accent, 0, 20, 12);
            on.dispose();

            assertFalse(offAccent, "an unselected radio must not paint the accent");
            assertTrue(onAccent, "a selected radio paints the accent indicator");
        }

        @Test
        @DisplayName("a disabled button still renders without errors")
        void disabledRenders() {
            RadioButton rb = new RadioButton(0, 0, 120, 24);
            rb.setSelected(true);
            rb.setEnabled(false);
            canvas.clear(0xFF0000FF);
            rb.renderInternal(canvas);
            canvas.flush();
            rb.dispose();
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
