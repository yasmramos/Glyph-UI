package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link TitleBar} chrome component: fixed sizing, the
 * left-title / right-controls layout, the content slot, and the null-window
 * guards (so nothing throws when no {@code Application} is running).
 */
public class TitleBarTest {

    /** Mirrors {@code TitleBar}'s private left inset. */
    private static final float PAD_LEFT = 12.0f;

    @Test
    public void preferredHeightIsAFixedChromeStrip() {
        TitleBar bar = new TitleBar("Title");
        assertEquals(TitleBar.BAR_HEIGHT, bar.getPreferredHeight(), 0.001f);
        assertEquals(TitleBar.BAR_HEIGHT, bar.measure(1000, 1000).height, 0.001f);
        // Width at least covers the three controls plus the title inset.
        assertTrue(bar.getPreferredWidth() >= TitleBar.CONTROL_WIDTH * 3);
    }

    @Test
    public void setTitleUpdatesTheTitleLabel() {
        TitleBar bar = new TitleBar("First");
        bar.setTitle("Second");
        Component title = bar.getChildren().get(0);
        assertInstanceOf(Label.class, title);
        assertEquals("Second", ((Label) title).getText());
    }

    @Test
    public void doLayoutPinsControlsRightAndTitleLeft() {
        TitleBar bar = new TitleBar("Glyph");
        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();

        List<Component> kids = bar.getChildren();
        // Children order: [title, minimize, maximize, close, ...content].
        Component title = kids.get(0);
        Component minimize = kids.get(1);
        Component maximize = kids.get(2);
        Component close = kids.get(3);

        assertEquals(PAD_LEFT, title.getX(), 0.001f);

        assertEquals(400 - TitleBar.CONTROL_WIDTH, close.getX(), 0.001f);
        assertEquals(TitleBar.CONTROL_WIDTH, close.getWidth(), 0.001f);
        assertEquals(TitleBar.BAR_HEIGHT, close.getHeight(), 0.001f);

        assertEquals(400 - TitleBar.CONTROL_WIDTH * 2, maximize.getX(), 0.001f);
        assertEquals(400 - TitleBar.CONTROL_WIDTH * 3, minimize.getX(), 0.001f);
    }

    @Test
    public void addContentFlowsAfterTheTitleAndIsRegistered() {
        TitleBar bar = new TitleBar("T");
        Label content = new Label("Menu");
        bar.addContent(content);
        assertTrue(bar.getChildren().contains(content));

        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();
        // The content widget sits to the right of the title's left inset.
        assertTrue(content.getX() > PAD_LEFT);
    }

    @Test
    public void controlClickWithoutAWindowDoesNotThrow() {
        // No Application is running, so window() is null; the control actions
        // must guard that instead of throwing. A press/release over the
        // minimize control is still consumed by the control itself.
        TitleBar bar = new TitleBar("T");
        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();

        Component minimize = bar.getChildren().get(1);
        int cx = (int) minimize.getX() + 1;
        int cy = (int) minimize.getY() + 1;
        assertDoesNotThrow(() -> {
            assertTrue(minimize.onMouseEvent(
                    new MouseEvent(MouseEventType.PRESS, cx, cy, MouseButton.LEFT, 1)));
            assertTrue(minimize.onMouseEvent(
                    new MouseEvent(MouseEventType.RELEASE, cx, cy, MouseButton.LEFT, 1)));
        });
    }

    @Test
    public void barDragRequiresAWindowSoPressIsNotConsumed() {
        // Without a live window there is nothing to drag; the empty-chrome
        // press must be ignored (not consumed) rather than throwing.
        TitleBar bar = new TitleBar("T");
        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();

        // x=120 is empty chrome (title ends near x=20, controls start at x>=256).
        boolean consumed = bar.onMouseEvent(
                new MouseEvent(MouseEventType.PRESS, 120, 20, MouseButton.LEFT, 1));
        assertTrue(!consumed);
    }
}
