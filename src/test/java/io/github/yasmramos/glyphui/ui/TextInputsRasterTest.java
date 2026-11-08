package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Pixmap;
import io.github.humbleui.skija.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rasterization smoke tests for the text inputs. Each widget is rendered onto a
 * real in-memory Skia surface so the paint paths run for real; assertions only
 * check that text pixels land where expected, never a specific anti-aliased
 * value.
 *
 * <p>Skipped with an assumption when the Skija native library cannot be loaded.</p>
 */
class TextInputsRasterTest extends TestBase {

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
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
        surfaceHolder = new Surface[1];
        canvas = RasterCanvasTestFactory.create(surfaceHolder);
    }

    @AfterEach
    void tearDown() {
        if (surfaceHolder[0] != null) {
            surfaceHolder[0].close();
        }
        FocusManager.setGlobalFocusManager(null);
    }

    private static Style redText() {
        return Style.builder().color(StyleProperty.COLOR, OPAQUE_RED).build();
    }

    private boolean anyPixelInRegion(int expected, int x0, int y0, int x1, int y1) {
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

    /** Number of contiguous horizontal bands of rows that contain the colour. */
    private int countColourBands(int expected, int x0, int y0, int x1, int y1) {
        int bands = 0;
        boolean inBand = false;
        try (Pixmap pixmap = new Pixmap()) {
            if (!surfaceHolder[0].peekPixels(pixmap)) {
                return 0;
            }
            for (int y = y0; y < y1; y++) {
                boolean rowHasColour = false;
                for (int x = x0; x < x1; x++) {
                    if (pixmap.getColor(x, y) == expected) {
                        rowHasColour = true;
                        break;
                    }
                }
                if (rowHasColour && !inBand) {
                    bands++;
                    inBand = true;
                } else if (!rowHasColour) {
                    inBand = false;
                }
            }
        }
        return bands;
    }

    @Test
    @DisplayName("PasswordField paints its masked characters")
    void passwordFieldRendersMaskedText() {
        canvas.clear(OPAQUE_BLUE);
        PasswordField field = new PasswordField();
        field.setX(10);
        field.setY(10);
        field.setWidth(180);
        field.setHeight(30);
        field.setInlineStyle(redText());
        field.setText("secret");
        try {
            field.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 10, 10, 190, 40),
                    "masked text pixels expected");
        } finally {
            field.dispose();
        }
    }

    @Test
    @DisplayName("TextArea paints one band of text per line")
    void textAreaRendersEachLineSeparately() {
        canvas.clear(OPAQUE_BLUE);
        TextArea area = new TextArea();
        area.setX(0);
        area.setY(0);
        area.setWidth(200);
        area.setHeight(90);
        area.setInlineStyle(redText());
        area.setText("ab\ncd");
        try {
            area.renderInternal(canvas);
            canvas.flush();
            assertTrue(countColourBands(OPAQUE_RED, 0, 0, 200, 90) >= 2,
                    "two visual lines must paint two separated text bands");
        } finally {
            area.dispose();
        }
    }

    @Test
    @DisplayName("TextArea paints a caret when focused")
    void textAreaRendersCaret() {
        canvas.clear(OPAQUE_BLUE);
        TextArea area = new TextArea();
        area.setX(0);
        area.setY(0);
        area.setWidth(200);
        area.setHeight(60);
        area.setInlineStyle(Style.builder()
                .color(StyleProperty.ACCENT_COLOR, OPAQUE_RED)
                .build());
        area.setVisible(true);
        area.requestFocus();
        try {
            area.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 40, 40),
                    "caret pixels expected near the start of the empty first line");
        } finally {
            area.dispose();
        }
    }
}
