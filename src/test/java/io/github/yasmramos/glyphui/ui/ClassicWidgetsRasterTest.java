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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rasterization smoke tests for the classic widgets added after the first
 * vocabulary pass. Each widget is rendered onto a real in-memory Skia surface
 * so the paint paths run for real; the assertions only check that the widget
 * changed pixels inside its own bounds, never a specific anti-aliased value.
 *
 * <p>Tests are skipped with an assumption when the Skija native library cannot
 * be loaded on the host.</p>
 */
class ClassicWidgetsRasterTest extends TestBase {

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

    private int pixelAt(int x, int y) {
        try (Pixmap pixmap = new Pixmap()) {
            assertTrue(surfaceHolder[0].peekPixels(pixmap), "peekPixels should succeed");
            return pixmap.getColor(x, y);
        }
    }

    @Test
    @DisplayName("Link paints its caption")
    void linkRenders() {
        canvas.clear(OPAQUE_BLUE);
        Link link = new Link("Home");
        link.setX(10);
        link.setY(10);
        link.setWidth(120);
        link.setHeight(24);
        link.setTextColor(OPAQUE_RED);
        try {
            link.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 10, 10, 130, 34),
                    "link caption pixels expected");
        } finally {
            link.dispose();
        }
    }

    @Test
    @DisplayName("Rating paints filled and empty stars")
    void ratingRenders() {
        canvas.clear(OPAQUE_BLUE);
        Rating rating = new Rating(5);
        rating.setX(10);
        rating.setY(10);
        rating.setWidth(140);
        rating.setHeight(24);
        rating.setValue(3);
        rating.setStarColor(OPAQUE_RED);
        rating.setEmptyColor(OPAQUE_RED);
        try {
            rating.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 10, 10, 150, 40),
                    "star pixels expected");
        } finally {
            rating.dispose();
        }
    }

    @Test
    @DisplayName("Skeleton paints a block")
    void skeletonRenders() {
        canvas.clear(OPAQUE_BLUE);
        Skeleton skeleton = new Skeleton(Skeleton.Shape.RECT);
        skeleton.setX(10);
        skeleton.setY(10);
        skeleton.setWidth(120);
        skeleton.setHeight(60);
        skeleton.setAnimated(false);
        skeleton.setBaseColor(OPAQUE_RED);
        try {
            skeleton.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 10, 10, 130, 70),
                    "skeleton block pixels expected");
        } finally {
            skeleton.dispose();
        }
    }

    @Test
    @DisplayName("Stepper paints its markers and labels")
    void stepperRenders() {
        canvas.clear(OPAQUE_BLUE);
        Stepper stepper = new Stepper("One", "Two", "Three");
        stepper.setX(10);
        stepper.setY(10);
        stepper.setWidth(400);
        stepper.setHeight(40);
        stepper.setCurrentStep(2);
        stepper.setActiveColor(OPAQUE_RED);
        stepper.setInactiveColor(OPAQUE_RED);
        try {
            stepper.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 10, 10, 410, 50),
                    "stepper marker pixels expected");
        } finally {
            stepper.dispose();
        }
    }

    @Test
    @DisplayName("Toolbar paints its strip and children")
    void toolbarRenders() {
        canvas.clear(OPAQUE_BLUE);
        Toolbar toolbar = new Toolbar();
        toolbar.setX(0);
        toolbar.setY(0);
        toolbar.setWidth(200);
        toolbar.setHeight(40);
        toolbar.setBackgroundColor(OPAQUE_RED);
        Button button = new Button("Run");
        button.setWidth(60);
        button.setHeight(28);
        toolbar.add(button);
        try {
            toolbar.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 200, 40),
                    "toolbar strip pixels expected");
        } finally {
            toolbar.dispose();
        }
    }

    @Test
    @DisplayName("GroupBox paints its outline and title")
    void groupBoxRenders() {
        canvas.clear(OPAQUE_BLUE);
        GroupBox box = new GroupBox("Account");
        box.setX(0);
        box.setY(0);
        box.setWidth(200);
        box.setHeight(80);
        box.setBorderColor(OPAQUE_RED);
        try {
            box.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 200, 80),
                    "group box outline pixels expected");
        } finally {
            box.dispose();
        }
    }

    @Test
    @DisplayName("ListBox paints its rows and selection")
    void listBoxRenders() {
        canvas.clear(OPAQUE_BLUE);
        ListBox list = new ListBox("Alpha", "Beta", "Gamma");
        list.setX(0);
        list.setY(0);
        list.setWidth(160);
        list.setHeight(72);
        list.setSelectedIndex(1);
        list.setSelectedColor(OPAQUE_RED);
        try {
            list.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 160, 72),
                    "list selection pixels expected");
        } finally {
            list.dispose();
        }
    }

    @Test
    @DisplayName("SplitPane paints the divider between its panes")
    void splitPaneRenders() {
        canvas.clear(OPAQUE_BLUE);
        SplitPane pane = new SplitPane(SplitPane.Orientation.HORIZONTAL,
                new Panel(), new Panel());
        pane.setX(0);
        pane.setY(0);
        pane.setWidth(200);
        pane.setHeight(100);
        try {
            pane.renderInternal(canvas);
            canvas.flush();
            // The divider strip sits at the middle; its grip is drawn in the
            // theme border colour, so at least the strip must differ from the
            // cleared blue background.
            boolean painted = false;
            for (int y = 40; y < 60 && !painted; y++) {
                for (int x = 0; x < 200; x++) {
                    if (pixelAt(x, y) != OPAQUE_BLUE) {
                        painted = true;
                        break;
                    }
                }
            }
            assertTrue(painted, "split pane divider pixels expected");
        } finally {
            pane.dispose();
        }
    }

    @Test
    @DisplayName("Table paints its header and grid")
    void tableRenders() {
        canvas.clear(OPAQUE_BLUE);
        Table table = new Table("Name", "Value");
        table.addRow("Alpha", "1");
        table.addRow("Beta", "2");
        table.setX(0);
        table.setY(0);
        table.setWidth(200);
        table.setHeight(120);
        table.setHeaderColor(OPAQUE_RED);
        try {
            table.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 200, 30),
                    "table header pixels expected");
        } finally {
            table.dispose();
        }
    }

    @Test
    @DisplayName("TreeView paints its rows and selection")
    void treeViewRenders() {
        canvas.clear(OPAQUE_BLUE);
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("Child");
        tree.refresh();
        tree.setX(0);
        tree.setY(0);
        tree.setWidth(180);
        tree.setHeight(120);
        tree.setSelectedColor(OPAQUE_RED);
        tree.setSelectedNode(root);
        try {
            tree.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 180, 40),
                    "tree selection pixels expected");
        } finally {
            tree.dispose();
        }
    }

    @Test
    @DisplayName("Dialog paints its scrim and surface")
    void dialogRenders() {
        canvas.clear(OPAQUE_BLUE);
        Dialog dialog = new Dialog("Title", "Message");
        dialog.setVisible(true);
        dialog.setX(0);
        dialog.setY(0);
        dialog.setWidth(240);
        dialog.setHeight(160);
        dialog.setScrimColor(OPAQUE_RED);
        dialog.setSurfaceColor(OPAQUE_RED);
        try {
            dialog.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 240, 160),
                    "dialog scrim/surface pixels expected");
        } finally {
            dialog.dispose();
        }
    }

    @Test
    @DisplayName("ColorPicker paints its swatches")
    void colorPickerRenders() {
        canvas.clear(OPAQUE_BLUE);
        ColorPicker picker = new ColorPicker(OPAQUE_RED, OPAQUE_RED);
        picker.setX(0);
        picker.setY(0);
        picker.setWidth(200);
        picker.setHeight(120);
        try {
            picker.renderInternal(canvas);
            canvas.flush();
            assertTrue(anyPixelInRegion(OPAQUE_RED, 0, 0, 200, 120),
                    "color picker swatch pixels expected");
        } finally {
            picker.dispose();
        }
    }

    @Test
    @DisplayName("invisible classic widgets paint nothing")
    void invisibleWidgetsAreNoOps() {
        canvas.clear(OPAQUE_BLUE);
        Skeleton skeleton = new Skeleton(Skeleton.Shape.RECT);
        skeleton.setX(10);
        skeleton.setY(10);
        skeleton.setWidth(100);
        skeleton.setHeight(50);
        skeleton.setBaseColor(OPAQUE_RED);
        skeleton.setVisible(false);
        try {
            skeleton.renderInternal(canvas);
            canvas.flush();
            assertFalse(anyPixelInRegion(OPAQUE_RED, 10, 10, 110, 60),
                    "nothing should be painted for an invisible skeleton");
        } finally {
            skeleton.dispose();
        }
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
}
