package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
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
 * Rasterization smoke tests for the menu, overlay and form widgets added with
 * the classic vocabulary. Each widget is rendered onto a real in-memory Skia
 * surface so its paint path runs for real; the assertion only checks that the
 * widget changed pixels inside its own bounds, never a specific anti-aliased
 * value.
 *
 * <p>Tests are skipped with an assumption when the Skija native library cannot
 * be loaded on the host.</p>
 */
class NewWidgetsRasterTest extends TestBase {

    private static final int OPAQUE_BLUE = 0xFF0000FF;

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

    @Test
    @DisplayName("Tooltip paints its bubble and caption")
    void tooltipRenders() {
        canvas.clear(OPAQUE_BLUE);
        Tooltip tooltip = new Tooltip("Save the file");
        tooltip.setX(10);
        tooltip.setY(10);
        tooltip.setWidth(120);
        tooltip.setHeight(24);
        tooltip.setVisible(true);
        try {
            tooltip.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 10, 10, 130, 34),
                    "tooltip bubble pixels expected");
        } finally {
            tooltip.dispose();
        }
    }

    @Test
    @DisplayName("Toast paints its surface and accent strip")
    void toastRenders() {
        canvas.clear(OPAQUE_BLUE);
        Toast toast = new Toast(Toast.Level.SUCCESS, "Saved");
        toast.setX(0);
        toast.setY(0);
        toast.setWidth(200);
        toast.setHeight(44);
        toast.setVisible(true);
        try {
            toast.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 0, 0, 200, 44),
                    "toast surface pixels expected");
        } finally {
            toast.dispose();
        }
    }

    @Test
    @DisplayName("MenuBar paints its strip and trigger captions")
    void menuBarRenders() {
        canvas.clear(OPAQUE_BLUE);
        MenuBar bar = new MenuBar();
        bar.setX(0);
        bar.setY(0);
        bar.setWidth(200);
        bar.setHeight(28);
        bar.addMenu("File");
        bar.addMenu("Edit");
        try {
            bar.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 0, 0, 200, 28),
                    "menu bar strip pixels expected");
        } finally {
            bar.dispose();
        }
    }

    @Test
    @DisplayName("ContextMenu paints its rows once opened")
    void contextMenuRenders() {
        canvas.clear(OPAQUE_BLUE);
        ContextMenu menu = new ContextMenu();
        menu.addItem("Cut");
        menu.addItem("Copy");
        menu.addSeparator();
        menu.addItem("Paste");
        menu.setX(0);
        menu.setY(0);
        menu.open();
        try {
            menu.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 0, 0,
                            (int) menu.getWidth(), (int) menu.getHeight()),
                    "context menu row pixels expected");
        } finally {
            menu.dispose();
        }
    }

    @Test
    @DisplayName("ProgressRing paints its track and progress arc")
    void progressRingRenders() {
        canvas.clear(OPAQUE_BLUE);
        ProgressRing ring = new ProgressRing();
        ring.setX(10);
        ring.setY(10);
        ring.setWidth(48);
        ring.setHeight(48);
        ring.setValue(0.6f);
        try {
            ring.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 10, 10, 58, 58),
                    "progress ring stroke pixels expected");
        } finally {
            ring.dispose();
        }
    }

    @Test
    @DisplayName("SegmentedControl paints its strip and selected segment")
    void segmentedControlRenders() {
        canvas.clear(OPAQUE_BLUE);
        SegmentedControl control = new SegmentedControl("Day", "Week");
        control.setX(0);
        control.setY(0);
        control.setWidth(160);
        control.setHeight(28);
        try {
            control.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 0, 0, 160, 28),
                    "segmented control strip pixels expected");
        } finally {
            control.dispose();
        }
    }

    @Test
    @DisplayName("RangeSlider paints its track and two thumbs")
    void rangeSliderRenders() {
        canvas.clear(OPAQUE_BLUE);
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        slider.setX(0);
        slider.setY(0);
        slider.setWidth(200);
        slider.setHeight(24);
        slider.setValues(25.0f, 75.0f);
        try {
            slider.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 0, 0, 200, 24),
                    "range slider track and thumb pixels expected");
        } finally {
            slider.dispose();
        }
    }

    @Test
    @DisplayName("ToggleButton paints its caption and frame")
    void toggleButtonRenders() {
        canvas.clear(OPAQUE_BLUE);
        ToggleButton toggle = new ToggleButton("Bold");
        toggle.setX(0);
        toggle.setY(0);
        toggle.setWidth(90);
        toggle.setHeight(28);
        toggle.setOn(true);
        try {
            toggle.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 0, 0, 90, 28),
                    "toggle button frame pixels expected");
        } finally {
            toggle.dispose();
        }
    }

    @Test
    @DisplayName("StatusBar paints its strip and segment captions")
    void statusBarRenders() {
        canvas.clear(OPAQUE_BLUE);
        StatusBar bar = new StatusBar();
        bar.setX(0);
        bar.setY(0);
        bar.setWidth(240);
        bar.setHeight(24);
        bar.addSegment("Ready");
        bar.addSegment("Ln 1, Col 1");
        try {
            bar.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelNotIn(OPAQUE_BLUE, 0, 0, 240, 24),
                    "status bar strip pixels expected");
        } finally {
            bar.dispose();
        }
    }

    private boolean anyPixelNotIn(int excluded, int x0, int y0, int x1, int y1) {
        try (Pixmap pixmap = new Pixmap()) {
            if (!surfaceHolder[0].peekPixels(pixmap)) {
                return false;
            }
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    if (pixmap.getColor(x, y) != excluded) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
