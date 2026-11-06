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
import io.github.yasmramos.glyphui.graphics.Dimension;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behaviour tests for {@link Dropdown}: building the item list, selection
 * bookkeeping, opening/closing the popup and pointer/keyboard activation.
 * Everything runs headless: no {@code Application}, no window.
 */
public class DropdownTest extends TestBase {

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

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, '\0',
                EnumSet.noneOf(KeyModifier.class));
    }

    @Nested
    @DisplayName("items and selection")
    class ItemsAndSelection {

        @Test
        @DisplayName("a new dropdown is empty with no selection")
        void emptyByDefault() {
            Dropdown dd = new Dropdown();
            assertEquals(0, dd.getItemCount());
            assertEquals(-1, dd.getSelectedIndex());
            assertNull(dd.getSelectedItem());
            assertEquals(AccessibleRole.COMBO_BOX, dd.getAccessibleRole());
            dd.dispose();
        }

        @Test
        @DisplayName("the first item added becomes the selection")
        void firstItemSelected() {
            Dropdown dd = new Dropdown();
            dd.addItem("Red");
            dd.addItem("Green");

            assertEquals(2, dd.getItemCount());
            assertEquals(0, dd.getSelectedIndex());
            assertEquals("Red", dd.getSelectedItem());
            dd.dispose();
        }

        @Test
        @DisplayName("addItems appends in order and returns the dropdown")
        void addItemsChains() {
            Dropdown dd = new Dropdown();
            Dropdown same = dd.addItems("A", "B", "C");

            assertEquals(dd, same);
            assertEquals(java.util.List.of("A", "B", "C"), dd.getItems());
            dd.dispose();
        }

        @Test
        @DisplayName("a null label is stored as an empty string")
        void nullLabelBecomesEmpty() {
            Dropdown dd = new Dropdown();
            dd.addItem(null);
            assertEquals("", dd.getSelectedItem());
            dd.dispose();
        }

        @Test
        @DisplayName("setSelectedIndex clamps out-of-range values to no selection")
        void outOfRangeClearsSelection() {
            Dropdown dd = new Dropdown();
            dd.addItems("A", "B");
            dd.setSelectedIndex(99);
            assertEquals(-1, dd.getSelectedIndex());
            assertNull(dd.getSelectedItem());
            dd.dispose();
        }

        @Test
        @DisplayName("the change listener fires only on a real change")
        void listenerFiresOnChangeOnly() {
            Dropdown dd = new Dropdown();
            dd.addItems("A", "B");

            AtomicInteger calls = new AtomicInteger();
            AtomicReference<Integer> seen = new AtomicReference<>();
            dd.setOnChange(index -> {
                calls.incrementAndGet();
                seen.set(index);
            });

            dd.setSelectedIndex(1);
            assertEquals(1, calls.get());
            assertEquals(1, seen.get());

            // Same index again: no notification.
            dd.setSelectedIndex(1);
            assertEquals(1, calls.get());

            dd.setSelectedIndex(-1);
            assertEquals(2, calls.get());
            assertEquals(-1, (int) seen.get());
            dd.dispose();
        }

        @Test
        @DisplayName("clearItems empties the list and the selection")
        void clearItemsResets() {
            Dropdown dd = new Dropdown();
            dd.addItems("A", "B");
            dd.clearItems();

            assertEquals(0, dd.getItemCount());
            assertEquals(-1, dd.getSelectedIndex());
            assertNull(dd.getSelectedItem());
            dd.dispose();
        }

        @Test
        @DisplayName("getItems returns a defensive copy")
        void getItemsIsACopy() {
            Dropdown dd = new Dropdown();
            dd.addItem("A");
            dd.getItems().add("B");
            assertEquals(1, dd.getItemCount());
            dd.dispose();
        }

        @Test
        @DisplayName("the placeholder defaults to empty and can be set")
        void placeholder() {
            Dropdown dd = new Dropdown();
            assertEquals("", dd.getPlaceholder());
            dd.setPlaceholder("Choose...");
            assertEquals("Choose...", dd.getPlaceholder());
            dd.dispose();
        }

        @Test
        @DisplayName("the accessible name follows the selection, then the placeholder")
        void accessibleName() {
            Dropdown dd = new Dropdown();
            assertEquals("Dropdown", dd.getAccessibleName());

            dd.setPlaceholder("Pick one");
            assertEquals("Pick one", dd.getAccessibleName());

            dd.addItem("Alpha");
            assertEquals("Alpha", dd.getAccessibleName());

            dd.setAccessibleName("Colour");
            assertEquals("Colour", dd.getAccessibleName());
            dd.dispose();
        }
    }

    @Nested
    @DisplayName("popup control")
    class Popup {

        @Test
        @DisplayName("the popup starts closed")
        void startsClosed() {
            Dropdown dd = new Dropdown();
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("open is a no-op while there are no items")
        void openWithNoItemsDoesNothing() {
            Dropdown dd = new Dropdown();
            dd.open();
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("open and close drive the popup")
        void openAndClose() {
            Dropdown dd = new Dropdown();
            dd.addItems("A", "B");

            dd.open();
            assertTrue(dd.isOpen());

            dd.close();
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("toggle flips the popup")
        void toggleFlips() {
            Dropdown dd = new Dropdown();
            dd.addItems("A", "B");

            dd.toggle();
            assertTrue(dd.isOpen());
            dd.toggle();
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("a disabled dropdown refuses to open")
        void disabledRefusesToOpen() {
            Dropdown dd = new Dropdown();
            dd.addItems("A");
            dd.setEnabled(false);

            dd.open();
            assertFalse(dd.isOpen());
            dd.dispose();
        }
    }

    @Nested
    @DisplayName("pointer interaction")
    class Pointer {

        @Test
        @DisplayName("clicking the box toggles the popup")
        void clickToggles() {
            Dropdown dd = new Dropdown(0, 0, 120, 24);
            dd.addItems("A", "B");

            dd.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            dd.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));
            assertTrue(dd.isOpen());

            dd.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            dd.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("hover enters and leaves the hover state")
        void hoverTransitions() {
            Dropdown dd = new Dropdown(0, 0, 120, 24);
            dd.onMouseEvent(mouse(MouseEventType.MOVE, 10, 12));
            assertEquals(ComponentState.HOVER, dd.getState());
            dd.onMouseEvent(mouse(MouseEventType.MOVE, 500, 500));
            assertEquals(ComponentState.IDLE, dd.getState());
            dd.dispose();
        }

        @Test
        @DisplayName("a release outside does not open the popup")
        void releaseOutsideDoesNotOpen() {
            Dropdown dd = new Dropdown(0, 0, 120, 24);
            dd.addItems("A");

            dd.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            dd.onMouseEvent(mouse(MouseEventType.RELEASE, 500, 500));
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("a disabled dropdown ignores clicks")
        void disabledIgnoresClicks() {
            Dropdown dd = new Dropdown(0, 0, 120, 24);
            dd.addItems("A");
            dd.setEnabled(false);

            dd.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12));
            dd.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 12));
            assertFalse(dd.isOpen());
            dd.dispose();
        }
    }

    @Nested
    @DisplayName("keyboard interaction")
    class Keyboard {

        @Test
        @DisplayName("Space, Enter and Down open the popup while focused")
        void openKeys() {
            for (int code : new int[]{GlyphKeys.SPACE, GlyphKeys.ENTER, GlyphKeys.DOWN}) {
                Dropdown dd = new Dropdown();
                dd.addItems("A", "B");
                dd.requestFocus();
                dd.onKeyEvent(key(code));
                assertTrue(dd.isOpen(), "key " + code + " should open the popup");
                dd.dispose();
            }
        }

        @Test
        @DisplayName("keys are ignored while unfocused")
        void unfocusedIgnoresKeys() {
            Dropdown dd = new Dropdown();
            dd.addItems("A");
            dd.onKeyEvent(key(GlyphKeys.SPACE));
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("an unrelated key does not open the popup")
        void unrelatedKeyIgnored() {
            Dropdown dd = new Dropdown();
            dd.addItems("A");
            dd.requestFocus();
            dd.onKeyEvent(key(GlyphKeys.LEFT));
            assertFalse(dd.isOpen());
            dd.dispose();
        }

        @Test
        @DisplayName("a disabled dropdown ignores keys")
        void disabledIgnoresKeys() {
            Dropdown dd = new Dropdown();
            dd.addItems("A");
            dd.requestFocus();
            dd.setEnabled(false);
            dd.onKeyEvent(key(GlyphKeys.SPACE));
            assertFalse(dd.isOpen());
            dd.dispose();
        }
    }

    @Nested
    @DisplayName("sizing")
    class Sizing {

        @Test
        @DisplayName("the preferred width grows with the widest item")
        void preferredWidthGrows() {
            Dropdown dd = new Dropdown();
            dd.addItem("A");
            float narrow = dd.getPreferredWidth();
            dd.addItem("A much longer option");
            float wide = dd.getPreferredWidth();

            assertTrue(wide > narrow, "a longer item should widen the box");
            dd.dispose();
        }

        @Test
        @DisplayName("explicit bounds win over the measured size")
        void explicitBoundsWin() {
            Dropdown dd = new Dropdown(0, 0, 200, 30);
            assertEquals(200.0f, dd.getPreferredWidth(), 1e-6f);
            assertEquals(30.0f, dd.getPreferredHeight(), 1e-6f);
            dd.dispose();
        }

        @Test
        @DisplayName("measure clamps to the supplied constraints")
        void measureClamps() {
            Dropdown dd = new Dropdown();
            dd.addItem("Long label here");
            Dimension d = dd.measure(10.0f, 5.0f);
            assertEquals(10.0f, d.getWidth(), 1e-6f);
            assertEquals(5.0f, d.getHeight(), 1e-6f);
            dd.dispose();
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
        @DisplayName("the chevron is painted in the control text colour")
        void chevronPainted() {
            Dropdown dd = new Dropdown(0, 0, 160, 24);
            dd.addItems("Alpha", "Beta");
            canvas.clear(0xFF0000FF);
            dd.renderInternal(canvas);
            canvas.flush();

            // The chevron sits near the right edge; the control text colour
            // must appear somewhere in that strip.
            assertTrue(anyPixelEquals(Theme.current().getControlTextColor(), 140, 158, 12),
                    "the chevron should be drawn near the right edge");
            dd.dispose();
        }

        @Test
        @DisplayName("a disabled dropdown still renders without errors")
        void disabledRenders() {
            Dropdown dd = new Dropdown(0, 0, 160, 24);
            dd.addItems("Alpha");
            dd.setEnabled(false);
            canvas.clear(0xFF0000FF);
            dd.renderInternal(canvas);
            canvas.flush();
            dd.dispose();
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
