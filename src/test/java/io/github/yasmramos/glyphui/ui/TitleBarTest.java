package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link TitleBar} chrome component: fixed sizing, the
 * three-zone layout (leading menu bar / centred title / trailing controls), the
 * embedded menu bar, and the null-window guards (so nothing throws when no
 * {@code Application} is running).
 */
public class TitleBarTest {

    /** Mirrors {@code TitleBar}'s private leading inset. */
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
    public void doLayoutPinsControlsRightAndCentresTitle() {
        TitleBar bar = new TitleBar("Glyph");
        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();

        List<Component> kids = bar.getChildren();
        // Children order: [title, minimize, maximize, close].
        Component title = kids.get(0);
        Component minimize = kids.get(1);
        Component maximize = kids.get(2);
        Component close = kids.get(3);

        assertEquals(400 - TitleBar.CONTROL_WIDTH, close.getX(), 0.001f);
        assertEquals(TitleBar.CONTROL_WIDTH, close.getWidth(), 0.001f);
        assertEquals(TitleBar.BAR_HEIGHT, close.getHeight(), 0.001f);

        assertEquals(400 - TitleBar.CONTROL_WIDTH * 2, maximize.getX(), 0.001f);
        assertEquals(400 - TitleBar.CONTROL_WIDTH * 3, minimize.getX(), 0.001f);

        // With no leading content the title is centred against the whole bar.
        float expected = (400 - title.getWidth()) / 2f;
        assertEquals(expected, title.getX(), 0.5f);
        assertTrue(title.getX() > PAD_LEFT, "centred title sits right of the inset");
    }

    @Test
    public void menuBarOccupiesTheLeadingZone() {
        TitleBar bar = new TitleBar("T");
        MenuBar menu = new MenuBar();
        menu.addMenu("File");
        bar.setMenuBar(menu);

        assertSame(menu, bar.getMenuBar());
        assertTrue(menu.isEmbedded(), "a title-bar menu bar is embedded");

        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();
        // The menu bar starts at the leading inset and fills the strip height.
        assertEquals(PAD_LEFT, menu.getX(), 0.001f);
        assertEquals(0.0f, menu.getY(), 0.001f);
        assertEquals(TitleBar.BAR_HEIGHT, menu.getHeight(), 0.001f);
    }

    @Test
    public void addContentFlowsAfterTheMenuBarAndIsRegistered() {
        TitleBar bar = new TitleBar("T");
        MenuBar menu = new MenuBar();
        menu.addMenu("File");
        bar.setMenuBar(menu);

        Label content = new Label("Status");
        bar.addContent(content);
        assertTrue(bar.getChildren().contains(content));

        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();
        // The extra widget sits to the right of the menu bar.
        assertTrue(content.getX() >= menu.getX() + menu.getWidth());

        bar.removeContent(content);
        assertFalse(bar.getChildren().contains(content));
    }

    @Test
    public void leadingAlignmentPinsTheTitleAfterTheMenuBar() {
        TitleBar bar = new TitleBar("T");
        MenuBar menu = new MenuBar();
        menu.addMenu("File");
        bar.setMenuBar(menu);
        bar.setTitleAlignment(TitleBar.TitleAlignment.LEADING);
        assertSame(TitleBar.TitleAlignment.LEADING, bar.getTitleAlignment());

        bar.setWidth(400);
        bar.setHeight(TitleBar.BAR_HEIGHT);
        bar.doLayout();

        Component title = bar.getChildren().get(0);
        assertTrue(title.getX() >= menu.getX() + menu.getWidth(),
                "a LEADING title sits after the leading zone");
    }

    @Test
    public void nullTitleAlignmentRestoresCentering() {
        TitleBar bar = new TitleBar("T");
        bar.setTitleAlignment(TitleBar.TitleAlignment.LEADING);
        bar.setTitleAlignment(null);
        assertSame(TitleBar.TitleAlignment.CENTER, bar.getTitleAlignment());
    }

    @Test
    public void setMenuBarReplacesThePreviousOne() {
        TitleBar bar = new TitleBar("T");
        MenuBar first = new MenuBar();
        MenuBar second = new MenuBar();
        bar.setMenuBar(first);
        bar.setMenuBar(second);

        assertSame(second, bar.getMenuBar());
        assertTrue(bar.getChildren().contains(second));
        assertFalse(bar.getChildren().contains(first),
                "the previous menu bar is detached");
        assertFalse(first.isEmbedded(), "the detached bar leaves embedded mode");
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

        // x=200 is empty chrome (leading zone is empty, controls start at x>=256).
        boolean consumed = bar.onMouseEvent(
                new MouseEvent(MouseEventType.PRESS, 200, 20, MouseButton.LEFT, 1));
        assertTrue(!consumed);
    }
}
