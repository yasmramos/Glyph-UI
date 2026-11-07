package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the passive chrome and placeholder widgets:
 * {@link Skeleton}, {@link Toolbar} and {@link GroupBox}.
 */
class ChromeWidgetsTest extends TestBase {

    @Test
    void skeletonDefaultsToATextLine() {
        Skeleton skeleton = new Skeleton();

        assertEquals(Skeleton.Shape.TEXT, skeleton.getShape());
        assertTrue(skeleton.isAnimated());
        assertEquals(0.0f, skeleton.getPhase());
        assertFalse(skeleton.isFocusable());
        assertEquals(AccessibleRole.SKELETON, skeleton.getAccessibleRole());
    }

    @Test
    void skeletonShapeDrivesThePreferredHeight() {
        Skeleton text = new Skeleton(Skeleton.Shape.TEXT);
        Skeleton circle = new Skeleton(Skeleton.Shape.CIRCLE);
        Skeleton rect = new Skeleton(Skeleton.Shape.RECT);

        assertEquals(Skeleton.TEXT_HEIGHT, text.getPreferredHeight(), 1e-6);
        assertEquals(Skeleton.CIRCLE_SIZE, circle.getPreferredHeight(), 1e-6);
        assertTrue(rect.getPreferredHeight() > text.getPreferredHeight());
    }

    @Test
    void skeletonPhaseWrapsAndAnimationCanBeDisabled() {
        Skeleton skeleton = new Skeleton();

        skeleton.setPhase(1.25f);
        assertEquals(0.25f, skeleton.getPhase(), 1e-6);

        skeleton.setAnimated(false);
        assertFalse(skeleton.isAnimated());
    }

    @Test
    void skeletonNeverConsumesInput() {
        Skeleton skeleton = new Skeleton();

        assertFalse(skeleton.onMouseEvent(new MouseEvent(
                MouseEventType.PRESS, 0, 0, MouseButton.LEFT, 1)));
    }

    @Test
    void toolbarUsesARowFlowAndNeverTakesFocus() {
        Toolbar toolbar = new Toolbar();

        assertFalse(toolbar.isFocusable());
        assertEquals(AccessibleRole.TOOLBAR, toolbar.getAccessibleRole());
        assertEquals("toolbar", toolbar.getStyleTag());
    }

    @Test
    void toolbarPreferredHeightFollowsItsTallestChild() {
        Toolbar toolbar = new Toolbar();
        Button button = new Button("Run");
        button.setWidth(60.0f);
        button.setHeight(28.0f);
        toolbar.add(button);

        assertTrue(toolbar.getPreferredHeight() >= 28.0f);
    }

    @Test
    void groupBoxStoresItsTitle() {
        GroupBox box = new GroupBox("Account");

        assertEquals("Account", box.getTitle());
        assertEquals("Account", box.getAccessibleName());
        assertEquals(AccessibleRole.GROUP_BOX, box.getAccessibleRole());
        assertFalse(box.isFocusable());
    }

    @Test
    void groupBoxTitlePropertyAndNullHandling() {
        GroupBox box = new GroupBox();

        assertEquals("", box.getTitle());
        box.titleProperty().set("Settings");
        assertEquals("Settings", box.getTitle());

        box.setTitle(null);
        assertEquals("", box.getTitle());
    }
}
