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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link SplitPane}: pane geometry, divider dragging,
 * keyboard resizing, clamping and the resize handler.
 */
class SplitPaneTest extends TestBase {

    @BeforeEach
    void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ' ',
                EnumSet.noneOf(KeyModifier.class));
    }

    private static SplitPane sizedPane() {
        SplitPane pane = new SplitPane(SplitPane.Orientation.HORIZONTAL,
                new Panel(), new Panel());
        pane.setWidth(200.0f);
        pane.setHeight(100.0f);
        return pane;
    }

    @Test
    void defaultsToHorizontalHalfSplit() {
        SplitPane pane = new SplitPane();

        assertEquals(SplitPane.Orientation.HORIZONTAL, pane.getOrientation());
        assertEquals(0.5f, pane.getRatio(), 1e-6);
        assertEquals(SplitPane.MIN_PANE, pane.getMinSize(), 1e-6);
        assertEquals(AccessibleRole.SPLIT_PANE, pane.getAccessibleRole());
    }

    @Test
    void constructorStoresOrientationAndPanes() {
        SplitPane pane = sizedPane();

        assertEquals(2, pane.getChildren().size());
    }

    @Test
    void ratioIsClampedIntoUnitRange() {
        SplitPane pane = sizedPane();

        pane.setRatio(3.0f);
        assertEquals(1.0f, pane.getRatio(), 1e-6);

        pane.setRatio(-2.0f);
        assertEquals(0.0f, pane.getRatio(), 1e-6);
    }

    @Test
    void orientationRejectsNull() {
        SplitPane pane = sizedPane();

        assertThrows(IllegalArgumentException.class, () -> pane.setOrientation(null));
    }

    @Test
    void minSizeRejectsNegative() {
        SplitPane pane = sizedPane();

        assertThrows(IllegalArgumentException.class, () -> pane.setMinSize(-1.0f));
    }

    @Test
    void draggingTheDividerMovesTheSplit() {
        SplitPane pane = sizedPane();
        List<Float> resizes = new ArrayList<>();
        pane.setOnResize(resizes::add);

        // The divider starts at 200 * 0.5 = 100 (minus half the divider).
        pane.onMouseEvent(mouse(MouseEventType.PRESS, 99, 50));
        pane.onMouseEvent(mouse(MouseEventType.DRAG, 150, 50));
        pane.onMouseEvent(mouse(MouseEventType.RELEASE, 150, 50));

        assertTrue(pane.getRatio() > 0.5f);
        assertFalse(resizes.isEmpty());
    }

    @Test
    void keyboardResizesByAFixedStep() {
        SplitPane pane = sizedPane();
        List<Float> resizes = new ArrayList<>();
        pane.setOnResize(resizes::add);

        pane.onKeyEvent(key(GlyphKeys.RIGHT));
        assertTrue(pane.getRatio() > 0.5f);

        float afterRight = pane.getRatio();
        pane.onKeyEvent(key(GlyphKeys.LEFT));
        assertTrue(pane.getRatio() < afterRight);

        pane.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0.0f, pane.getRatio(), 1e-6);

        pane.onKeyEvent(key(GlyphKeys.END));
        assertEquals(1.0f, pane.getRatio(), 1e-6);
    }

    @Test
    void draggingOutsideTheDividerDoesNotResize() {
        SplitPane pane = sizedPane();

        // Press far away from the divider: the panes get the event instead.
        pane.onMouseEvent(mouse(MouseEventType.PRESS, 10, 50));
        pane.onMouseEvent(mouse(MouseEventType.DRAG, 190, 50));

        assertEquals(0.5f, pane.getRatio(), 1e-6);
    }

    @Test
    void disabledPaneIgnoresInput() {
        SplitPane pane = sizedPane();
        pane.setEnabled(false);

        assertFalse(pane.onMouseEvent(mouse(MouseEventType.PRESS, 99, 50)));
        pane.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(0.5f, pane.getRatio(), 1e-6);
    }

    @Test
    void handlerFailureIsSwallowed() {
        SplitPane pane = sizedPane();
        pane.setOnResize(ratio -> {
            throw new IllegalStateException("boom");
        });

        pane.onKeyEvent(key(GlyphKeys.RIGHT));

        assertTrue(pane.getRatio() > 0.5f);
    }
}
