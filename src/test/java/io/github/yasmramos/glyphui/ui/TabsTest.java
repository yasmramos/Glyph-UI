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

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Tabs}: page registration, selection, visibility
 * and input. No Skija native code is touched because the paints are created
 * lazily on the first render.
 */
class TabsTest extends TestBase {

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

    @Test
    void startsEmpty() {
        Tabs tabs = new Tabs();

        assertEquals(0, tabs.getTabCount());
        assertEquals(-1, tabs.getSelectedIndex());
    }

    @Test
    void addingAPageSelectsTheFirstOne() {
        Tabs tabs = new Tabs();
        tabs.addTab("One", new Label("one"));

        assertEquals(1, tabs.getTabCount());
        assertEquals(0, tabs.getSelectedIndex());
        assertEquals("One", tabs.getTabLabel(0));
    }

    @Test
    void onlyTheSelectedPageIsVisible() {
        Tabs tabs = new Tabs();
        Panel first = new Panel();
        Panel second = new Panel();
        tabs.addTab("One", first);
        tabs.addTab("Two", second);

        assertTrue(first.isVisible());
        assertFalse(second.isVisible());

        tabs.setSelectedIndex(1);
        assertFalse(first.isVisible());
        assertTrue(second.isVisible());
    }

    @Test
    void selectingAnOutOfRangeIndexIsIgnored() {
        Tabs tabs = new Tabs();
        tabs.addTab("One", new Label("one"));

        tabs.setSelectedIndex(5);
        assertEquals(0, tabs.getSelectedIndex());

        tabs.setSelectedIndex(-1);
        assertEquals(0, tabs.getSelectedIndex());
    }

    @Test
    void selectedIndexPropertyKeepsStateInSync() {
        Tabs tabs = new Tabs();
        tabs.addTab("One", new Label("one"));
        tabs.addTab("Two", new Label("two"));

        tabs.selectedIndexProperty().set(1);

        assertEquals(1, tabs.getSelectedIndex());
    }

    @Test
    void nullPageIsRejected() {
        Tabs tabs = new Tabs();

        assertThrows(IllegalArgumentException.class, () -> tabs.addTab("One", null));
    }

    @Test
    void pagesAreOffsetBelowTheHeaderStrip() {
        Tabs tabs = new Tabs();
        tabs.setWidth(200.0f);
        tabs.setHeight(150.0f);
        Label page = new Label("content");
        tabs.addTab("One", page);

        tabs.doLayout();

        assertTrue(page.getY() >= Tabs.HEADER_HEIGHT,
                "the page must sit below the header strip");
    }

    @Test
    void clickingATabSelectsIt() {
        Tabs tabs = new Tabs();
        tabs.setWidth(200.0f);
        tabs.setHeight(150.0f);
        tabs.addTab("A", new Label("a"));
        tabs.addTab("B", new Label("b"));

        // The first cell starts at x=0; click well inside it.
        tabs.onMouseEvent(mouse(MouseEventType.PRESS, 4.0f, 10.0f));
        assertEquals(0, tabs.getSelectedIndex());

        // The second cell begins after the first; its centre is a safe hit.
        float secondCentre = tabs.getTabLabel(0).length() * 8.0f
                + 2.0f * Tabs.TAB_PADDING_HORIZONTAL + 4.0f;
        tabs.onMouseEvent(mouse(MouseEventType.PRESS, secondCentre, 10.0f));
        assertEquals(1, tabs.getSelectedIndex());
    }

    @Test
    void arrowKeysMoveTheSelection() {
        Tabs tabs = new Tabs();
        tabs.setWidth(200.0f);
        tabs.setHeight(150.0f);
        tabs.addTab("A", new Label("a"));
        tabs.addTab("B", new Label("b"));
        tabs.addTab("C", new Label("c"));

        tabs.onKeyEvent(key(GlyphKeys.RIGHT));
        assertEquals(1, tabs.getSelectedIndex());

        tabs.onKeyEvent(key(GlyphKeys.END));
        assertEquals(2, tabs.getSelectedIndex());

        tabs.onKeyEvent(key(GlyphKeys.LEFT));
        assertEquals(1, tabs.getSelectedIndex());

        tabs.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0, tabs.getSelectedIndex());
    }

    @Test
    void reportsItsAccessibleRole() {
        Tabs tabs = new Tabs();

        assertEquals(AccessibleRole.TAB_PANEL, tabs.getAccessibleRole());
    }

    @Test
    void aPageAddedThroughAddDerivesItsLabelFromTheWidget() {
        Tabs tabs = new Tabs();
        tabs.add(new Label("plain"));

        assertEquals(1, tabs.getTabCount());
        assertEquals("plain", tabs.getTabLabel(0));
    }

    @Test
    void aPageWithNoCaptionGetsANumberedLabel() {
        Tabs tabs = new Tabs();
        tabs.add(new Panel());

        assertEquals(1, tabs.getTabCount());
        assertEquals("Tab 1", tabs.getTabLabel(0));
    }
}
