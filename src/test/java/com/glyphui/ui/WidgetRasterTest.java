package com.glyphui.ui;

import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Color;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for {@link Label} and {@link Button} executed against a real in-memory
 * Skia raster surface (no GPU / windowing required). Tests are skipped with an
 * assumption if the Skija native library cannot be loaded on the host.
 */
class WidgetRasterTest {

    private static final int OPAQUE_BLUE = 0xFF0000FF;
    private static final int OPAQUE_RED = 0xFFFF0000;

    @BeforeAll
    static void checkSkijaAvailable() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping raster tests");
    }

    private Surface[] surfaceHolder;
    private Canvas canvas;

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

    // ---- Label: pure state tests (still valid without GPU) -----------------

    @Nested
    @DisplayName("Label state")
    class LabelState {

        @Test
        @DisplayName("Constructor stores text and defaults")
        void constructorDefaults() {
            Label label = new Label(5, 10, 200, 30, "Hello");
            assertEquals("Hello", label.getText());
            assertEquals(Color.makeARGB(255, 255, 255, 255), label.getTextColor());
            assertEquals(Label.TextAlignment.LEFT, label.getAlignment());
            assertEquals(14.0f, label.getFontSize(), 1e-6);
            label.dispose();
        }

        @Test
        @DisplayName("setText/getText round-trip")
        void setTextRoundTrip() {
            Label label = new Label(0, 0, 100, 20, "a");
            label.setText("changed");
            assertEquals("changed", label.getText());
            label.dispose();
        }

        @Test
        @DisplayName("setTextColor/getTextColor round-trip")
        void textColorRoundTrip() {
            Label label = new Label(0, 0, 100, 20, "a");
            label.setTextColor(OPAQUE_RED);
            assertEquals(OPAQUE_RED, label.getTextColor());
            label.dispose();
        }

        @Test
        @DisplayName("setAlignment/getAlignment round-trip for all values")
        void alignmentRoundTrip() {
            Label label = new Label(0, 0, 100, 20, "a");
            for (Label.TextAlignment a : Label.TextAlignment.values()) {
                label.setAlignment(a);
                assertSame(a, label.getAlignment());
            }
            label.dispose();
        }

        @Test
        @DisplayName("setFontSize updates preferred height accordingly")
        void fontSizeAffectsPreferredHeight() {
            Label label = new Label(0, 0, 100, 20, "a");
            float h16 = label.getPreferredHeight();
            label.setFontSize(30.0f);
            assertEquals(30.0f, label.getFontSize(), 1e-6);
            assertTrue(label.getPreferredHeight() > h16,
                    "preferred height should grow with font size");
            label.dispose();
        }
    }

    // ---- Label: rendering tests --------------------------------------------

    @Nested
    @DisplayName("Label rendering")
    class LabelRendering {

        @Test
        @DisplayName("render of visible label draws pixels onto the canvas")
        void renderDrawsVisibleText() {
            canvas.clear(OPAQUE_BLUE);
            Label label = new Label(10, 40, 200, 60, "Rendered!");
            label.setTextColor(OPAQUE_RED);
            label.render(canvas);
            canvas.flush();

            assertTrue(anyPixelInRegionEquals(OPAQUE_RED, 10, 20, 220, 100),
                    "red text pixels expected where the label was drawn");
            label.dispose();
        }

        @Test
        @DisplayName("invisible label renders nothing")
        void invisibleLabelIsNoOp() {
            canvas.clear(OPAQUE_BLUE);
            Label label = new Label(10, 40, 200, 60, "Hidden");
            label.setVisible(false);
            label.render(canvas);
            canvas.flush();

            assertEquals(OPAQUE_BLUE, pixelAt(50, 60),
                    "nothing should have been painted for an invisible label");
            label.dispose();
        }

        @Test
        @DisplayName("alignment shifts text horizontally")
        void alignmentChangesHorizontalPosition() {
            Label left = new Label(10, 40, 200, 60, "WWW");
            Label right = new Label(10, 40, 200, 60, "WWW");
            try {
                left.setTextColor(OPAQUE_RED);
                right.setTextColor(OPAQUE_RED);
                left.setAlignment(Label.TextAlignment.LEFT);
                right.setAlignment(Label.TextAlignment.RIGHT);

                canvas.clear(OPAQUE_BLUE);
                left.render(canvas);
                canvas.flush();
                boolean redNearLeft = anyPixelInRegionEquals(OPAQUE_RED, 10, 65, 90, 80);

                canvas.clear(OPAQUE_BLUE);
                right.render(canvas);
                canvas.flush();
                // surface is RasterCanvasTestFactory.SIZE px wide; label spans x=[10,210]
                int size = RasterCanvasTestFactory.SIZE;
                boolean redNearRight = anyPixelInRegionEquals(OPAQUE_RED, size - 80, 65, size - 1, 80);

                assertTrue(redNearLeft, "LEFT alignment should paint near x=10");
                assertTrue(redNearRight, "RIGHT alignment should paint near the right edge");
            } finally {
                left.dispose();
                right.dispose();
            }
        }
    }

    // ---- Button: state + interaction ---------------------------------------

    @Nested
    @DisplayName("Button state and interaction")
    class ButtonState {

        @Test
        @DisplayName("Constructor stores text, defaults and no click handler")
        void constructorDefaults() {
            Button button = new Button(0, 0, 120, 40, "Click me");
            assertEquals("Click me", button.getText());
            assertEquals(null, button.getOnClick());
            assertEquals(8.0f, button.getBorderRadius(), 1e-6);
            assertEquals(ComponentState.IDLE, button.getState());
            button.dispose();
        }

        @Test
        @DisplayName("Hover appears when mouse moves inside bounds")
        void hoverOnMoveInside() {
            Button button = new Button(10, 10, 100, 40, "B");
            button.onMouseEvent(mouse(com.glyphui.events.MouseEventType.MOVE, 50, 20));
            assertEquals(ComponentState.HOVER, button.getState());

            button.onMouseEvent(mouse(com.glyphui.events.MouseEventType.MOVE, 500, 500));
            assertEquals(ComponentState.IDLE, button.getState());
            button.dispose();
        }

        @Test
        @DisplayName("Full press/release cycle invokes onClick exactly once")
        void clickInvokesHandlerOnce() {
            Button button = new Button(10, 10, 100, 40, "B");
            int[] calls = {0};
            button.setOnClick(() -> calls[0]++);

            button.onMouseEvent(mouse(com.glyphui.events.MouseEventType.PRESS, 50, 20));
            assertEquals(ComponentState.PRESSED, button.getState());

            button.onMouseEvent(mouse(com.glyphui.events.MouseEventType.RELEASE, 50, 20));
            assertEquals(1, calls[0], "onClick must run once per click");
            assertEquals(ComponentState.HOVER, button.getState(),
                    "after release inside bounds button returns to HOVER");
            button.dispose();
        }

        @Test
        @DisplayName("Release outside bounds does not invoke onClick")
        void releaseOutsideDoesNotFire() {
            Button button = new Button(10, 10, 100, 40, "B");
            int[] calls = {0};
            button.setOnClick(() -> calls[0]++);

            button.onMouseEvent(mouse(com.glyphui.events.MouseEventType.PRESS, 50, 20));
            button.onMouseEvent(mouse(com.glyphui.events.MouseEventType.RELEASE, 500, 20));

            assertEquals(0, calls[0]);
            assertEquals(ComponentState.IDLE, button.getState());
            button.dispose();
        }

        @Test
        @DisplayName("Disabled or hidden buttons ignore mouse events")
        void disabledOrHiddenIgnoreEvents() {
            Button disabled = new Button(0, 0, 50, 20, "D");
            disabled.setEnabled(false);
            disabled.onMouseEvent(mouse(com.glyphui.events.MouseEventType.MOVE, 10, 10));
            assertEquals(ComponentState.IDLE, disabled.getState());
            disabled.dispose();

            Button hidden = new Button(0, 0, 50, 20, "H");
            hidden.setVisible(false);
            hidden.onMouseEvent(mouse(com.glyphui.events.MouseEventType.MOVE, 10, 10));
            assertEquals(ComponentState.IDLE, hidden.getState());
            hidden.dispose();
        }

        @Test
        @DisplayName("Color setters round-trip through getters")
        void colorSettersRoundTrip() {
            Button button = new Button(0, 0, 50, 20, "C");
            button.setNormalColor(0xFF112233);
            button.setHoverColor(0xFF445566);
            button.setPressedColor(0xFF778899);
            button.setTextColor(0xFFAABBCC);
            button.setBorderColor(0xFFDDEEFF);
            button.setBorderRadius(12.0f);

            assertEquals(0xFF112233, button.getNormalColor());
            assertEquals(0xFF445566, button.getHoverColor());
            assertEquals(0xFF778899, button.getPressedColor());
            assertEquals(0xFFAABBCC, button.getTextColor());
            assertEquals(0xFFDDEEFF, button.getBorderColor());
            assertEquals(12.0f, button.getBorderRadius(), 1e-6);
            button.dispose();
        }

        @Test
        @DisplayName("render paints rounded background covering button bounds")
        void renderPaintsBackground() {
            canvas.clear(OPAQUE_BLUE);
            Button button = new Button(20, 20, 100, 40, "OK");
            button.setNormalColor(OPAQUE_RED);
            button.render(canvas);
            canvas.flush();

            assertEquals(OPAQUE_RED, pixelAt(70, 40),
                    "center of button should show its background color");
            assertNotEquals(OPAQUE_RED, pixelAt(2, 2),
                    "outside the button the clear color must remain");
            button.dispose();
        }

        @Test
        @DisplayName("invisible button renders nothing")
        void invisibleButtonRendersNothing() {
            canvas.clear(OPAQUE_BLUE);
            Button button = new Button(20, 20, 100, 40, "OK");
            button.setVisible(false);
            button.render(canvas);
            canvas.flush();

            assertEquals(OPAQUE_BLUE, pixelAt(70, 40));
            button.dispose();
        }

        @Test
        @DisplayName("dispose releases resources and is idempotent-safe for state checks")
        void disposeKeepsComponentUsableForQueries() {
            Button button = new Button(0, 0, 50, 20, "X");
            button.dispose();
            // After dispose the object still answers basic queries without crashing
            assertEquals("X", button.getText());
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static com.glyphui.events.MouseEvent mouse(
            com.glyphui.events.MouseEventType type, int x, int y) {
        return new com.glyphui.events.MouseEvent(type, x, y,
                com.glyphui.events.MouseButton.LEFT, 1);
    }

    private int pixelAt(int x, int y) {
        try (Pixmap pixmap = new Pixmap()) {
            assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
            return pixmap.getColor(x, y);
        }
    }

    private boolean anyPixelInRegionEquals(int expected, int x0, int y0, int x1, int y1) {
        try (Pixmap pixmap = new Pixmap()) {
            if (!surfaceHolder[0].peekPixels(pixmap)) {
                return false;
            }
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if (pixmap.getColor(x, y) == expected) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
