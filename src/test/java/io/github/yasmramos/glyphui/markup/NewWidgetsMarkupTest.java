package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.AccessibleRole;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ContextMenu;
import io.github.yasmramos.glyphui.ui.Menu;
import io.github.yasmramos.glyphui.ui.MenuBar;
import io.github.yasmramos.glyphui.ui.ProgressRing;
import io.github.yasmramos.glyphui.ui.RangeSlider;
import io.github.yasmramos.glyphui.ui.SegmentedControl;
import io.github.yasmramos.glyphui.ui.StatusBar;
import io.github.yasmramos.glyphui.ui.Toast;
import io.github.yasmramos.glyphui.ui.ToggleButton;
import io.github.yasmramos.glyphui.ui.Tooltip;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markup tests for the widgets added with the menu, overlay and form
 * additions: {@code menubar}, {@code statusbar}, {@code contextmenu},
 * {@code segmented}, {@code progressring} and {@code rangeslider}.
 */
class NewWidgetsMarkupTest {

    @Test
    void menubarTagBuildsMenusFromItsChildren() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <menubar id="bar">
                    <menu label="File">
                      <item>New</item>
                      <item>Open</item>
                      <separator/>
                      <item>Exit</item>
                    </menu>
                    <menu label="Help">
                      <item>About</item>
                    </menu>
                  </menubar>
                </body>
                """);

        MenuBar bar = (MenuBar) UiLoader.findById(root, "bar");
        assertNotNull(bar, "<menubar> must map to MenuBar");
        assertEquals(AccessibleRole.MENU_BAR, bar.getAccessibleRole());
        assertEquals(2, bar.getMenus().size());
        assertEquals("File", bar.getTriggers().get(0).getText());
        assertEquals("Help", bar.getTriggers().get(1).getText());

        Menu file = bar.getMenus().get(0);
        assertEquals(4, file.getChildren().size(), "two items, a separator and a third item");
        assertEquals(1, bar.getMenus().get(1).getChildren().size());
    }

    @Test
    void statusbarTagBuildsSegmentsFromItsChildren() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <statusbar id="status">
                    <span>Ready</span>
                    <span>Ln 1, Col 1</span>
                  </statusbar>
                </body>
                """);

        StatusBar bar = (StatusBar) UiLoader.findById(root, "status");
        assertNotNull(bar, "<statusbar> must map to StatusBar");
        assertEquals(2, bar.getSegmentCount());
        assertEquals("Ready", bar.getSegmentText(0));
        assertEquals("Ln 1, Col 1", bar.getSegmentText(1));
        assertEquals(AccessibleRole.STATUS_BAR, bar.getAccessibleRole());
    }

    @Test
    void contextMenuTagBuildsAClosedMenu() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><contextmenu id=\"ctx\"><item>Cut</item><item>Copy</item></contextmenu></body>");

        ContextMenu menu = (ContextMenu) UiLoader.findById(root, "ctx");
        assertNotNull(menu, "<contextmenu> must map to ContextMenu");
        assertEquals(AccessibleRole.CONTEXT_MENU, menu.getAccessibleRole());
        assertFalse(menu.isOpen());
    }

    @Test
    void segmentedTagReadsItsOptions() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><segmented id=\"seg\" options=\"Day, Week, Month\"/></body>");

        SegmentedControl control = (SegmentedControl) UiLoader.findById(root, "seg");
        assertNotNull(control, "<segmented> must map to SegmentedControl");
        assertEquals(3, control.getSegmentCount());
        assertEquals("Week", control.getSegmentLabel(1));
        assertEquals(0, control.getSelectedIndex());
    }

    @Test
    void segmentedControlSpellingIsAlsoAccepted() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><segmentedcontrol id=\"seg2\" options=\"A,B\"/></body>");

        SegmentedControl control = (SegmentedControl) UiLoader.findById(root, "seg2");
        assertNotNull(control);
        assertEquals(2, control.getSegmentCount());
    }

    @Test
    void progressRingTagReadsItsAttributes() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><progressring id=\"ring\" value=\"0.4\" text/></body>");

        ProgressRing ring = (ProgressRing) UiLoader.findById(root, "ring");
        assertNotNull(ring, "<progressring> must map to ProgressRing");
        assertEquals(0.4f, ring.getValue(), 0.001f);
        assertTrue(ring.isShowText());
        assertFalse(ring.isIndeterminate());
    }

    @Test
    void progressRingTagCanStartIndeterminate() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><progressring id=\"spin\" indeterminate/></body>");

        ProgressRing ring = (ProgressRing) UiLoader.findById(root, "spin");
        assertNotNull(ring);
        assertTrue(ring.isIndeterminate());
    }

    @Test
    void rangeSliderTagReadsItsBoundsAndValues() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><rangeslider id=\"price\" min=\"0\" max=\"1000\" step=\"10\""
                        + " lower=\"200\" upper=\"800\"/></body>");

        RangeSlider slider = (RangeSlider) UiLoader.findById(root, "price");
        assertNotNull(slider, "<rangeslider> must map to RangeSlider");
        assertEquals(0.0f, slider.getMin());
        assertEquals(1000.0f, slider.getMax());
        assertEquals(10.0f, slider.getStep());
        assertEquals(200.0f, slider.getLowerValue());
        assertEquals(800.0f, slider.getUpperValue());
    }

    @Test
    void rangeSliderTagDefaultsToAFullSpan() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("<body><rangeslider id=\"r\"/></body>");

        RangeSlider slider = (RangeSlider) UiLoader.findById(root, "r");
        assertNotNull(slider);
        assertEquals(0.0f, slider.getLowerValue());
        assertEquals(100.0f, slider.getUpperValue());
    }

    @Test
    void toggleButtonTagReadsItsInitialState() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><togglebutton id=\"bold\" checked>Bold</togglebutton></body>");

        ToggleButton toggle = (ToggleButton) UiLoader.findById(root, "bold");
        assertNotNull(toggle, "<togglebutton> must map to ToggleButton");
        assertEquals("Bold", toggle.getText());
        assertTrue(toggle.isOn());
    }

    @Test
    void toggleButtonTagDefaultsToOff() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><togglebutton id=\"italic\">Italic</togglebutton></body>");

        ToggleButton toggle = (ToggleButton) UiLoader.findById(root, "italic");
        assertNotNull(toggle);
        assertFalse(toggle.isOn());
    }

    @Test
    void tooltipTagReadsItsTextAndDelay() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><tooltip id=\"tip\" delay=\"0.2\">Save the file</tooltip></body>");

        Tooltip tooltip = (Tooltip) UiLoader.findById(root, "tip");
        assertNotNull(tooltip, "<tooltip> must map to Tooltip");
        assertEquals("Save the file", tooltip.getText());
        assertEquals(0.2f, tooltip.getDelaySeconds(), 0.001f);
    }

    @Test
    void toastTagReadsItsLevelAndDuration() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><toast id=\"t\" level=\"success\" duration=\"5\">Saved</toast></body>");

        Toast toast = (Toast) UiLoader.findById(root, "t");
        assertNotNull(toast, "<toast> must map to Toast");
        assertEquals("Saved", toast.getText());
        assertEquals(Toast.Level.SUCCESS, toast.getLevel());
        assertEquals(5.0f, toast.getDurationSeconds(), 0.001f);
    }

    @Test
    void toastTagFallsBackToInfoForAnUnknownLevel() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><toast id=\"t2\" level=\"nonsense\">Hi</toast></body>");

        Toast toast = (Toast) UiLoader.findById(root, "t2");
        assertNotNull(toast);
        assertEquals(Toast.Level.INFO, toast.getLevel());
    }
}
