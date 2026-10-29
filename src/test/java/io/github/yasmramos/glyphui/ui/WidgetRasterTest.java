package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.ui.TextField;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.ComponentState;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
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
class WidgetRasterTest extends io.github.yasmramos.glyphui.TestBase {

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
            assertEquals(io.github.yasmramos.glyphui.graphics.Theme.current().getForegroundColor(), label.getTextColor(),
                    "default text color follows the current theme");
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
            // No explicit bounds: preferred height must follow the font size.
            Label label = new Label("a");
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
            label.renderInternal(canvas);
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
            label.renderInternal(canvas);
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
                left.renderInternal(canvas);
                canvas.flush();
                boolean redNearLeft = anyPixelInRegionEquals(OPAQUE_RED, 10, 65, 90, 80);

                canvas.clear(OPAQUE_BLUE);
                right.renderInternal(canvas);
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

        @Test
        @DisplayName("multi-line caption draws every line and centers the block")
        void renderDrawsEveryLine() {
            canvas.clear(OPAQUE_BLUE);
            Label one = new Label(10, 0, 300, 120, "AAA");
            Label two = new Label(10, 0, 300, 120, "AAA\nAAA");
            try {
                one.setTextColor(OPAQUE_RED);
                two.setTextColor(OPAQUE_RED);

                one.renderInternal(canvas);
                canvas.flush();
                int[] single = redRowSpan(10, 0, 310, 120);

                canvas.clear(OPAQUE_BLUE);
                two.renderInternal(canvas);
                canvas.flush();
                int[] block = redRowSpan(10, 0, 310, 120);

                assertTrue(block[1] - block[0] > single[1] - single[0],
                        "two lines must occupy more rows than one: "
                                + (block[1] - block[0]) + " vs " + (single[1] - single[0]));
                // Two identical lines are symmetric about the block centre, and the
                // block is centred in the box, so the ink midpoint is the box centre.
                double mid = (block[0] + block[1]) / 2.0;
                assertEquals(60.0, mid, 4.0, "the block of lines must be vertically centered");
            } finally {
                one.dispose();
                two.dispose();
            }
        }

        @Test
        @DisplayName("a trailing CRLF still yields two lines")
        void renderToleratesWindowsLineEndings() {
            canvas.clear(OPAQUE_BLUE);
            Label lf = new Label(10, 0, 300, 120, "AAA\nAAA");
            Label crlf = new Label(10, 0, 300, 120, "AAA\r\nAAA");
            try {
                lf.setTextColor(OPAQUE_RED);
                crlf.setTextColor(OPAQUE_RED);

                lf.renderInternal(canvas);
                canvas.flush();
                int[] unix = redRowSpan(10, 0, 310, 120);

                canvas.clear(OPAQUE_BLUE);
                crlf.renderInternal(canvas);
                canvas.flush();
                int[] windows = redRowSpan(10, 0, 310, 120);

                assertEquals(unix[0], windows[0], "\"\\r\\n\" must not open a third line");
                assertEquals(unix[1], windows[1], "\"\\r\\n\" must not open a third line");
            } finally {
                lf.dispose();
                crlf.dispose();
            }
        }

        @Test
        @DisplayName("height grows by one line step per line break")
        void preferredHeightAddsOneLinePerBreak() {
            Label one = new Label("A");
            Label two = new Label("A\nB");
            Label three = new Label("A\nB\nC");
            try {
                assertTrue(two.getPreferredHeight() > one.getPreferredHeight(),
                        "a second line must add height");
                assertEquals(two.getPreferredHeight() - one.getPreferredHeight(),
                        three.getPreferredHeight() - two.getPreferredHeight(), 0.01f,
                        "every line break must add the same line step");
            } finally {
                one.dispose();
                two.dispose();
                three.dispose();
            }
        }

        @Test
        @DisplayName("width measures the widest line, not the joined string")
        void preferredWidthUsesWidestLine() {
            Label single = new Label("MMMM");
            Label twoLines = new Label("MMMM\nMMMM");
            try {
                assertEquals(single.getPreferredWidth(), twoLines.getPreferredWidth(), 1.0f,
                        "stacking lines must not widen the label");
            } finally {
                single.dispose();
                twoLines.dispose();
            }
        }

        /**
         * Scans a region for red text ink and returns the first and last row
         * that contain any, or fails if nothing was painted.
         */
        private int[] redRowSpan(int x0, int y0, int x1, int y1) {
            int first = Integer.MAX_VALUE;
            int last = Integer.MIN_VALUE;
            long ink = 0;
            try (Pixmap pixmap = new Pixmap()) {
                assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
                for (int py = y0; py < y1; py++) {
                    for (int px = x0; px < x1; px++) {
                        int c = pixmap.getColor(px, py);
                        int r = (c >> 16) & 0xFF;
                        int g = (c >> 8) & 0xFF;
                        int b = c & 0xFF;
                        if (r > 200 && g < 80 && b < 80) {
                            ink++;
                            first = Math.min(first, py);
                            last = Math.max(last, py);
                        }
                    }
                }
            }
            assertTrue(ink > 0, "expected red text ink inside the region");
            return new int[] { first, last };
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
            button.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.MOVE, 50, 20));
            assertEquals(ComponentState.HOVER, button.getState());

            button.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.MOVE, 500, 500));
            assertEquals(ComponentState.IDLE, button.getState());
            button.dispose();
        }

        @Test
        @DisplayName("Full press/release cycle invokes onClick exactly once")
        void clickInvokesHandlerOnce() {
            Button button = new Button(10, 10, 100, 40, "B");
            int[] calls = {0};
            button.setOnClick(() -> calls[0]++);

            button.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.PRESS, 50, 20));
            assertEquals(ComponentState.PRESSED, button.getState());

            button.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.RELEASE, 50, 20));
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

            button.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.PRESS, 50, 20));
            button.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.RELEASE, 500, 20));

            assertEquals(0, calls[0]);
            assertEquals(ComponentState.IDLE, button.getState());
            button.dispose();
        }

        @Test
        @DisplayName("Disabled or hidden buttons ignore mouse events")
        void disabledOrHiddenIgnoreEvents() {
            Button disabled = new Button(0, 0, 50, 20, "D");
            disabled.setEnabled(false);
            disabled.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.MOVE, 10, 10));
            assertEquals(ComponentState.IDLE, disabled.getState());
            disabled.dispose();

            Button hidden = new Button(0, 0, 50, 20, "H");
            hidden.setVisible(false);
            hidden.onMouseEvent(mouse(io.github.yasmramos.glyphui.events.MouseEventType.MOVE, 10, 10));
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
            // Sample the upper-left interior: the caption is centered, so the
            // very middle of the button belongs to the glyphs.
            Button button = new Button(20, 20, 100, 40, "OK");
            button.setNormalColor(OPAQUE_RED);
            button.renderInternal(canvas);
            canvas.flush();

            assertEquals(OPAQUE_RED, pixelAt(30, 26),
                    "interior of button should show its background color");
            assertNotEquals(OPAQUE_RED, pixelAt(2, 2),
                    "outside the button the clear color must remain");
            button.dispose();
        }

        @Test
        @DisplayName("caption is centered horizontally and vertically inside the button")
        void renderCentersCaption() {
            // Render the same caption in three geometries and compare where the
            // ink lands. If the text is centered, growing the button by N moves
            // the caption by exactly N/2 — an invariant that depends on neither
            // the font metrics nor the ink distribution of the specific glyphs
            // (a plain centroid comparison fails here: "Hello" is optically
            // heavier on the left than its advance-width center).
            double[] small = renderCaptionInk(0, 0, 100, 20);
            double[] wide  = renderCaptionInk(0, 0, 200, 20);
            double[] tall  = renderCaptionInk(0, 0, 100, 60);

            assertTrue(small[2] > 50, "the caption must actually be rendered (ink="
                    + (long) small[2] + ")");
            assertEquals(50.0, wide[0] - small[0], 0.5,
                    "width 100 -> 200 must shift the caption 50px right");
            assertEquals(20.0, tall[1] - small[1], 0.5,
                    "height 20 -> 60 must shift the caption 20px down");
        }

        /**
         * Renders a white button with a black caption at the given size and
         * returns the ink centroid as {@code {x, y, inkCount}}.
         */
        private double[] renderCaptionInk(float bx, float by, float bw, float bh) {
            final int white = 0xFFFFFFFF;
            final int black = 0xFF000000;
            canvas.clear(white);

            Button button = new Button(bx, by, bw, bh, "Hello");
            button.setNormalColor(white);
            button.setTextColor(black);
            button.setBorderColor(white);
            button.renderInternal(canvas);
            canvas.flush();

            long ink = 0, sumX = 0, sumY = 0;
            try (Pixmap pixmap = new Pixmap()) {
                assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
                for (int py = (int) by; py < (int) (by + bh); py++) {
                    for (int px = (int) bx; px < (int) (bx + bw); px++) {
                        int c = pixmap.getColor(px, py);
                        int lum = ((c >> 16) & 0xFF) + ((c >> 8) & 0xFF) + (c & 0xFF);
                        if (lum < 3 * 128) {
                            ink++;
                            sumX += px;
                            sumY += py;
                        }
                    }
                }
            }
            button.dispose();
            return new double[] { sumX / (double) ink + 0.5, sumY / (double) ink + 0.5, ink };
        }

        @Test
        @DisplayName("invisible button renders nothing")
        void invisibleButtonRendersNothing() {
            canvas.clear(OPAQUE_BLUE);
            Button button = new Button(20, 20, 100, 40, "OK");
            button.setVisible(false);
            button.renderInternal(canvas);
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

    // ---- TextField: rendering tests ----------------------------------------

    @Nested
    @DisplayName("TextField rendering")
    class TextFieldRendering {

        @Test
        @DisplayName("field is filled with the entry surface, not the page background")
        void renderUsesEntrySurfaceFill() {
            canvas.clear(0xFFFF0000);
            TextField field = new TextField(0, 0, 200, 40);
            field.renderInternal(canvas);
            canvas.flush();

            int fill = pixelAt(100, 20);
            assertNotEquals(0xFFFF0000, fill, "the field must paint its own background");
            assertNotEquals(io.github.yasmramos.glyphui.graphics.Theme.current().getBackgroundColor(), fill,
                    "an entry filled with the page color is invisible but for its border");
            field.dispose();
        }

        @Test
        @DisplayName("text is centered vertically inside the field")
        void renderCentersTextVertically() {
            // Taller-than-needed field: a top-anchored baseline leaves the text
            // riding high, which is how it used to render (ink at rows 13..22 of
            // 40 instead of a centered 16..25).
            canvas.clear(0xFFFF0000);
            TextField field = new TextField(0, 0, 200, 40);
            field.setPlaceholder("Your name");
            field.renderInternal(canvas);
            canvas.flush();

            int fill = pixelAt(100, 20);
            int top = -1, bottom = -1;
            for (int y = 4; y < 36; y++) {          // skip the border stroke
                for (int x = 4; x < 196; x++) {
                    if (pixelAt(x, y) != fill) {
                        if (top < 0) {
                            top = y;
                        }
                        bottom = y;
                    }
                }
            }
            assertTrue(top >= 0, "the placeholder must actually be rendered");
            double inkCenter = (top + bottom) / 2.0 + 0.5;
            // Glyph shapes make the optical center approximate, so allow a
            // couple of pixels; the old code was off by ~2.5px and the fix is
            // a full baseline re-derivation.
            assertEquals(20.0, inkCenter, 2.0, "placeholder vertical center");
            field.dispose();
        }
    }

    // ---- Menu: rendering tests ---------------------------------------------

    @Nested
    @DisplayName("Menu rendering")
    class MenuRendering {

        @Test
        @DisplayName("popup surface is opaque and hides the page behind it")
        void menuPaintsOpaqueSurface() {
            canvas.clear(OPAQUE_BLUE);

            Menu menu = new Menu();
            menu.addItem("Guardar");
            menu.open();
            menu.renderInternal(canvas);
            canvas.flush();

            // Sample the top-left padding: inside the popup, away from the
            // hairline border and from any row text.
            int surface = io.github.yasmramos.glyphui.graphics.Theme.current()
                    .getControlNormalColor();
            assertEquals(surface, pixelAt(3, 3),
                    "the popup must paint its own surface instead of letting "
                    + "the page show through the rows");
            menu.dispose();
        }
    }

    // ---- renderInternal() template guarantees ------------------------------

    @Nested
    @DisplayName("Render template guarantees")
    class RenderTemplate {

        /**
         * A deliberately ill-behaved widget: it translates and clips the
         * canvas without saving or restoring anything. In Swing, forgetting
         * {@code super.paintComponent} (or leaking canvas state) corrupts
         * sibling painting; here the framework template must absorb the
         * damage instead.
         */
        private static final class LeakyWidget extends Component {
            @Override
            protected void render(io.github.yasmramos.glyphui.graphics.Canvas canvas) {
                canvas.translate(1000.0f, 1000.0f);
                canvas.clipRect(0.0f, 0.0f, 1.0f, 1.0f);
            }

            @Override
            public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
                return false;
            }

            @Override
            public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
                // No key handling in this test widget.
            }
        }

        @Test
        @DisplayName("a custom widget that leaks canvas state cannot corrupt its siblings")
        void leakedCanvasStateIsRestoredByTemplate() {
            canvas.clear(OPAQUE_BLUE);

            Label label = new Label(10, 0, 300, 120, "AAA");
            LeakyWidget leaky = new LeakyWidget();
            try {
                label.setTextColor(OPAQUE_RED);
                leaky.setX(0.0f);
                leaky.setY(0.0f);
                leaky.setWidth(50.0f);
                leaky.setHeight(50.0f);

                // The leaker runs first; whatever it does to the transform or
                // clip stack, the template must undo before the next widget.
                leaky.renderInternal(canvas);
                label.renderInternal(canvas);
                canvas.flush();

                assertTrue(anyPixelInRegionEquals(OPAQUE_RED, 10, 20, 220, 100),
                        "the label after a leaking sibling must still paint at"
                        + " its own position with an unclipped canvas");
            } finally {
                label.dispose();
                leaky.dispose();
            }
        }

        @Test
        @DisplayName("renderInternal skips invisible components")
        void templateSkipsInvisibleComponents() {
            canvas.clear(OPAQUE_BLUE);

            Label label = new Label(10, 0, 300, 120, "AAA");
            try {
                label.setTextColor(OPAQUE_RED);
                label.setVisible(false);
                label.renderInternal(canvas);
                canvas.flush();

                assertEquals(OPAQUE_BLUE, pixelAt(50, 60),
                        "an invisible component must not paint anything");
            } finally {
                label.dispose();
            }
        }

        @Test
        @DisplayName("renderInternal translates the origin to the component's x/y")
        void templateTranslatesToComponentOrigin() {
            canvas.clear(OPAQUE_BLUE);

            Label label = new Label(0, 0, 300, 120, "AAA");
            try {
                label.setTextColor(OPAQUE_RED);
                // The widget itself draws in local coordinates; only the
                // template knows where the widget lives inside its parent.
                label.setX(10.0f);
                label.setY(20.0f);
                label.renderInternal(canvas);
                canvas.flush();

                assertTrue(anyPixelInRegionEquals(OPAQUE_RED, 10, 20, 220, 120),
                        "ink must appear offset by the component's x/y");
                assertEquals(OPAQUE_BLUE, pixelAt(2, 2),
                        "the area above-left of the component must stay untouched");
            } finally {
                label.dispose();
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    private static io.github.yasmramos.glyphui.events.MouseEvent mouse(
            io.github.yasmramos.glyphui.events.MouseEventType type, int x, int y) {
        return new io.github.yasmramos.glyphui.events.MouseEvent(type, x, y,
                io.github.yasmramos.glyphui.events.MouseButton.LEFT, 1);
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
