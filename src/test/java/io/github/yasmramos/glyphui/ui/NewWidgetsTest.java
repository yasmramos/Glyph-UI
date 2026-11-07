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
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Behaviour tests for the widgets added with the menu, overlay and form
 * additions: {@link Tooltip}, {@link Toast}, {@link MenuBar},
 * {@link ContextMenu}, {@link ProgressRing}, {@link SegmentedControl},
 * {@link RangeSlider} and {@link StatusBar}.
 *
 * <p>Logic that needs no font measurement runs everywhere; the few paths that
 * must measure text (showing an overlay, opening a menu, hit-testing a
 * segmented control) are guarded with a Skija availability assumption, the
 * same way the raster suite skips itself on hosts without the native
 * library.</p>
 */
class NewWidgetsTest extends TestBase {

    private FocusManager focusManager;

    @BeforeEach
    void installFocusManager() {
        focusManager = new FocusManager(() -> { });
        FocusManager.setGlobalFocusManager(focusManager);
    }

    @AfterEach
    void clearFocusManager() {
        FocusManager.setGlobalFocusManager(null);
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT,
                type == MouseEventType.DRAG ? 0 : 1);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, (char) 0,
                EnumSet.noneOf(KeyModifier.class));
    }

    private static void requireSkija() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not loadable on this host; skipping font-dependent checks");
    }

    // ------------------------------------------------------------------
    // Tooltip
    // ------------------------------------------------------------------

    @Test
    void tooltipStartsHiddenAndEmpty() {
        Tooltip tooltip = new Tooltip();

        assertFalse(tooltip.isShowing());
        assertEquals("", tooltip.getText());
        assertEquals(AccessibleRole.TOOLTIP, tooltip.getAccessibleRole());
    }

    @Test
    void nullTooltipTextBecomesEmpty() {
        Tooltip tooltip = new Tooltip(null);

        assertEquals("", tooltip.getText());
    }

    @Test
    void tooltipUsesItsTextAsTheAccessibleName() {
        Tooltip tooltip = new Tooltip("Save the file");

        assertEquals("Save the file", tooltip.getAccessibleName());
    }

    @Test
    void emptyTooltipFallsBackToAGenericAccessibleName() {
        Tooltip tooltip = new Tooltip();

        assertEquals("Tooltip", tooltip.getAccessibleName());
    }

    @Test
    void tooltipDelayIsClampedAtZero() {
        Tooltip tooltip = new Tooltip("x");

        tooltip.setDelaySeconds(-3.0f);
        assertEquals(0.0f, tooltip.getDelaySeconds());

        tooltip.setDelaySeconds(0.5f);
        assertEquals(0.5f, tooltip.getDelaySeconds());
    }

    @Test
    void detachingATooltipClearsTheAnchorHooks() {
        Component anchor = new Panel(0, 0, 40, 20);
        Tooltip tooltip = Tooltip.attach(anchor, "hello");
        tooltip.setDelaySeconds(0.0f);

        tooltip.setAnchor(null);
        assertNull(tooltip.getAnchor());

        anchor.firePointerEnter();
        assertFalse(tooltip.isShowing(), "a detached tooltip must not react to its old anchor");
    }

    @Test
    void tooltipNeverConsumesPointerInput() {
        Tooltip tooltip = new Tooltip("x");
        tooltip.setWidth(50);
        tooltip.setHeight(20);

        assertFalse(tooltip.onMouseEvent(mouse(MouseEventType.PRESS, 5, 5)));
    }

    @Test
    void zeroDelayTooltipShowsOnAnchorEnter() {
        requireSkija();
        Component anchor = new Panel(0, 0, 40, 20);
        Tooltip tooltip = Tooltip.attach(anchor, "hello");
        tooltip.setDelaySeconds(0.0f);

        anchor.firePointerEnter();
        assertTrue(tooltip.isShowing(), "a zero-delay tooltip appears immediately");

        anchor.firePointerExit();
        assertFalse(tooltip.isShowing(), "leaving the anchor hides it again");
    }

    @Test
    void delayedTooltipWaitsForTheClockToAdvance() {
        requireSkija();
        Component anchor = new Panel(0, 0, 40, 20);
        Tooltip tooltip = Tooltip.attach(anchor, "hello");
        tooltip.setDelaySeconds(1.0f);

        anchor.firePointerEnter();
        assertFalse(tooltip.isShowing(), "the delay has not elapsed yet");

        tooltip.advance(0.4f);
        assertFalse(tooltip.isShowing(), "still below the delay");

        tooltip.advance(0.7f);
        assertTrue(tooltip.isShowing(), "the accumulated time passed the delay");
    }

    // ------------------------------------------------------------------
    // Toast
    // ------------------------------------------------------------------

    @Test
    void toastDefaultsToInfoAndEmptyText() {
        Toast toast = new Toast();

        assertEquals(Toast.Level.INFO, toast.getLevel());
        assertEquals("", toast.getText());
        assertFalse(toast.isVisible(), "toasts start hidden");
        assertEquals(AccessibleRole.TOAST, toast.getAccessibleRole());
    }

    @Test
    void nullToastLevelFallsBackToInfo() {
        Toast toast = new Toast(null, null);

        assertEquals(Toast.Level.INFO, toast.getLevel());
        assertEquals("", toast.getText());
    }

    @Test
    void eachToastLevelHasItsOwnAccentColor() {
        Toast info = new Toast(Toast.Level.INFO, "i");
        Toast success = new Toast(Toast.Level.SUCCESS, "s");
        Toast warning = new Toast(Toast.Level.WARNING, "w");
        Toast error = new Toast(Toast.Level.ERROR, "e");

        assertNotEquals(success.getAccentColor(), warning.getAccentColor());
        assertNotEquals(warning.getAccentColor(), error.getAccentColor());
        assertNotEquals(info.getAccentColor(), error.getAccentColor());
    }

    @Test
    void explicitToastAccentOverridesTheLevelColor() {
        Toast toast = new Toast(Toast.Level.ERROR, "e");
        toast.setAccentColor(0xFF102030);

        assertEquals(0xFF102030, toast.getAccentColor());

        toast.setAccentColor(null);
        assertNotEquals(0xFF102030, toast.getAccentColor());
    }

    @Test
    void toastAutoDismissesWhenItsDurationElapses() {
        Toast toast = new Toast(Toast.Level.INFO, "bye");
        toast.setVisible(true);
        toast.setDurationSeconds(1.0f);

        toast.advance(0.5f);
        assertTrue(toast.isVisible(), "half the duration is not enough");

        toast.advance(0.6f);
        assertFalse(toast.isVisible(), "the toast dismisses itself");
    }

    @Test
    void pausedToastDoesNotCountDown() {
        Toast toast = new Toast(Toast.Level.INFO, "hold");
        toast.setVisible(true);
        toast.setDurationSeconds(1.0f);
        toast.setPaused(true);

        toast.advance(5.0f);

        assertTrue(toast.isVisible());
        assertTrue(toast.isPaused());
    }

    @Test
    void nonPositiveDurationKeepsTheToastUp() {
        Toast toast = new Toast(Toast.Level.INFO, "sticky");
        toast.setVisible(true);
        toast.setDurationSeconds(0.0f);

        toast.advance(100.0f);

        assertTrue(toast.isVisible());
    }

    @Test
    void hiddenToastIgnoresTheClock() {
        Toast toast = new Toast(Toast.Level.INFO, "hidden");
        toast.setDurationSeconds(1.0f);

        toast.advance(100.0f);

        assertFalse(toast.isVisible());
    }

    @Test
    void toastNeverConsumesPointerInput() {
        Toast toast = new Toast(Toast.Level.INFO, "x");
        toast.setWidth(200);
        toast.setHeight(40);

        assertFalse(toast.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10)));
    }

    @Test
    void toastAccessibleNameIsItsText() {
        Toast toast = new Toast(Toast.Level.SUCCESS, "Saved");

        assertEquals("Saved", toast.getAccessibleName());
    }

    // ------------------------------------------------------------------
    // MenuBar
    // ------------------------------------------------------------------

    @Test
    void menuBarStartsEmpty() {
        MenuBar bar = new MenuBar();

        assertEquals(0, bar.getMenus().size());
        assertEquals(0, bar.getTriggers().size());
        assertEquals(AccessibleRole.MENU_BAR, bar.getAccessibleRole());
    }

    @Test
    void addingAMenuCreatesATriggerAndKeepsBothInOrder() {
        MenuBar bar = new MenuBar();

        Menu file = bar.addMenu("File");
        Menu edit = bar.addMenu("Edit");

        assertEquals(2, bar.getMenus().size());
        assertEquals(2, bar.getTriggers().size());
        assertEquals(file, bar.getMenus().get(0));
        assertEquals(edit, bar.getMenus().get(1));
        assertEquals("File", bar.getTriggers().get(0).getText());
        assertEquals("Edit", bar.getTriggers().get(1).getText());
    }

    @Test
    void nullMenuTitleBecomesAnEmptyCaption() {
        MenuBar bar = new MenuBar();
        bar.addMenu(null);

        assertEquals("", bar.getTriggers().get(0).getText());
    }

    @Test
    void clickingATriggerTogglesItsMenu() {
        requireSkija();
        MenuBar bar = new MenuBar();
        Menu file = bar.addMenu("File");
        file.addItem("New");

        Button trigger = bar.getTriggers().get(0);
        trigger.setWidth(60);
        trigger.setHeight(24);

        trigger.onMouseEvent(mouse(MouseEventType.PRESS, 5, 5));
        trigger.onMouseEvent(mouse(MouseEventType.RELEASE, 5, 5));
        assertTrue(file.isOpen(), "the first click opens the drop-down");

        trigger.onMouseEvent(mouse(MouseEventType.PRESS, 5, 5));
        trigger.onMouseEvent(mouse(MouseEventType.RELEASE, 5, 5));
        assertFalse(file.isOpen(), "a second click closes it again");
    }

    // ------------------------------------------------------------------
    // ContextMenu
    // ------------------------------------------------------------------

    @Test
    void contextMenuStartsClosedAndUnattached() {
        ContextMenu menu = new ContextMenu();

        assertFalse(menu.isOpen());
        assertNull(menu.getTarget());
        assertEquals(AccessibleRole.CONTEXT_MENU, menu.getAccessibleRole());
    }

    @Test
    void attachToInstallsARightClickHandlerOnTheTarget() {
        ContextMenu menu = new ContextMenu();
        Panel target = new Panel(0, 0, 100, 100);

        menu.attachTo(target);

        assertEquals(target, menu.getTarget());
        assertNotNull(target.getOnContextMenuRequested());

        menu.attachTo(null);
        assertNull(target.getOnContextMenuRequested(), "detaching clears the handler");
    }

    @Test
    void rightClickOnAnUnattachedComponentIsNotConsumed() {
        Panel target = new Panel(0, 0, 100, 100);

        assertFalse(target.fireContextMenuRequested(1.0f, 1.0f));
    }

    @Test
    void rightClickOnTheTargetOpensTheMenuAtThePoint() {
        requireSkija();
        ContextMenu menu = new ContextMenu();
        menu.addItem("Cut");
        Panel target = new Panel(0, 0, 100, 100);
        menu.attachTo(target);

        assertTrue(target.fireContextMenuRequested(30.0f, 40.0f));

        assertTrue(menu.isOpen());
        assertEquals(30.0f, menu.getX());
        assertEquals(40.0f, menu.getY());
    }

    @Test
    void contextMenuRowsActivateLikeMenuRows() {
        requireSkija();
        ContextMenu menu = new ContextMenu();
        MenuItem item = menu.addItem("Copy");
        List<Boolean> fired = new ArrayList<>();
        item.setOnClick(() -> fired.add(true));
        Panel target = new Panel(0, 0, 100, 100);
        menu.attachTo(target);

        target.fireContextMenuRequested(10.0f, 10.0f);

        int x = (int) item.getX() + 3;
        int y = (int) item.getY() + 3;
        item.onMouseEvent(mouse(MouseEventType.PRESS, x, y));
        item.onMouseEvent(mouse(MouseEventType.RELEASE, x, y));

        assertEquals(1, fired.size(), "the row runs its handler");
        assertFalse(menu.isOpen(), "picking a row closes the menu");
    }

    // ------------------------------------------------------------------
    // ProgressRing
    // ------------------------------------------------------------------

    @Test
    void progressRingStartsAtZeroAndClamps() {
        ProgressRing ring = new ProgressRing();

        assertEquals(0.0f, ring.getValue());

        ring.setValue(1.5f);
        assertEquals(1.0f, ring.getValue(), "values above one are clamped");

        ring.setValue(-2.0f);
        assertEquals(0.0f, ring.getValue(), "values below zero are clamped");
    }

    @Test
    void progressRingIsDeterminateByDefault() {
        ProgressRing ring = new ProgressRing();

        assertFalse(ring.isIndeterminate());
        assertFalse(ring.isShowText());
        assertEquals(AccessibleRole.PROGRESS_RING, ring.getAccessibleRole());
    }

    @Test
    void advanceAnimationIsANoOpWhenDeterminate() {
        ProgressRing ring = new ProgressRing();

        ring.advanceAnimation(1.0f);

        assertEquals(0.0f, ring.getValue());
    }

    @Test
    void progressRingValuePropertyDrivesTheValue() {
        ProgressRing ring = new ProgressRing();

        ring.valueProperty().set(0.25f);

        assertEquals(0.25f, ring.getValue());
    }

    @Test
    void progressRingNeverConsumesPointerInput() {
        ProgressRing ring = new ProgressRing();
        ring.setWidth(40);
        ring.setHeight(40);

        assertFalse(ring.onMouseEvent(mouse(MouseEventType.PRESS, 20, 20)));
    }

    // ------------------------------------------------------------------
    // SegmentedControl
    // ------------------------------------------------------------------

    @Test
    void segmentedControlSelectsTheFirstSegmentByDefault() {
        SegmentedControl control = new SegmentedControl("Day", "Week", "Month");

        assertEquals(3, control.getSegmentCount());
        assertEquals(0, control.getSelectedIndex());
        assertEquals("Day", control.getSegmentLabel(0));
        assertEquals(AccessibleRole.SEGMENTED_CONTROL, control.getAccessibleRole());
    }

    @Test
    void addingSegmentsAfterConstructionKeepsTheFirstSelected() {
        SegmentedControl control = new SegmentedControl();
        assertEquals(-1, control.getSelectedIndex(), "an empty control has no selection");

        control.addSegment("A").addSegment("B");

        assertEquals(0, control.getSelectedIndex());
        assertEquals(List.of("A", "B"), control.getLabels());
    }

    @Test
    void nullSegmentLabelBecomesEmpty() {
        SegmentedControl control = new SegmentedControl();
        control.addSegment(null);

        assertEquals("", control.getSegmentLabel(0));
    }

    @Test
    void selectingASegmentNotifiesTheHandler() {
        SegmentedControl control = new SegmentedControl("A", "B", "C");
        List<Integer> seen = new ArrayList<>();
        control.setOnChange(seen::add);

        control.setSelectedIndex(2);

        assertEquals(2, control.getSelectedIndex());
        assertEquals(List.of(2), seen);
    }

    @Test
    void reselectingTheSameSegmentDoesNotNotify() {
        SegmentedControl control = new SegmentedControl("A", "B");
        List<Integer> seen = new ArrayList<>();
        control.setOnChange(seen::add);

        control.setSelectedIndex(0);

        assertTrue(seen.isEmpty(), "no change means no notification");
    }

    @Test
    void outOfRangeSelectionIsIgnored() {
        SegmentedControl control = new SegmentedControl("A", "B");

        control.setSelectedIndex(5);
        control.setSelectedIndex(-1);

        assertEquals(0, control.getSelectedIndex());
    }

    @Test
    void arrowKeysMoveTheSelectionAndHomeEndJump() {
        SegmentedControl control = new SegmentedControl("A", "B", "C");

        control.onKeyEvent(key(GlyphKeys.RIGHT));
        assertEquals(1, control.getSelectedIndex());

        control.onKeyEvent(key(GlyphKeys.LEFT));
        assertEquals(0, control.getSelectedIndex());

        control.onKeyEvent(key(GlyphKeys.END));
        assertEquals(2, control.getSelectedIndex());

        control.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0, control.getSelectedIndex());
    }

    @Test
    void arrowKeysWrapAround() {
        SegmentedControl control = new SegmentedControl("A", "B");

        control.onKeyEvent(key(GlyphKeys.LEFT));

        assertEquals(1, control.getSelectedIndex(), "left wraps to the last segment");
    }

    @Test
    void selectingASegmentDrivesItsProperty() {
        SegmentedControl control = new SegmentedControl("A", "B");

        control.selectedIndexProperty().set(1);

        assertEquals(1, control.getSelectedIndex());
    }

    @Test
    void clickingASegmentSelectsIt() {
        requireSkija();
        SegmentedControl control = new SegmentedControl("A", "B");
        control.setX(0);
        control.setY(0);
        float total = control.getPreferredWidth();
        control.setWidth(total);
        control.setHeight(28);

        // Both captions are one character wide, so the two segments are the
        // same size and three quarters along the strip lands in the second.
        control.onMouseEvent(mouse(MouseEventType.PRESS, total * 0.75f, 14));
        control.onMouseEvent(mouse(MouseEventType.RELEASE, total * 0.75f, 14));

        assertEquals(1, control.getSelectedIndex());
    }

    // ------------------------------------------------------------------
    // RangeSlider
    // ------------------------------------------------------------------

    @Test
    void rangeSliderStartsAtItsMinimum() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);

        assertEquals(0.0f, slider.getMin());
        assertEquals(100.0f, slider.getMax());
        assertEquals(0.0f, slider.getLowerValue());
        assertEquals(0.0f, slider.getUpperValue());
        assertEquals(AccessibleRole.RANGE_SLIDER, slider.getAccessibleRole());
    }

    @Test
    void setRangeSwapsInvertedBounds() {
        RangeSlider slider = new RangeSlider(0.0f, 1.0f);

        slider.setRange(10.0f, 2.0f);

        assertEquals(2.0f, slider.getMin());
        assertEquals(10.0f, slider.getMax());
    }

    @Test
    void degenerateRangeIsWidenedSoTheSliderStaysUsable() {
        RangeSlider slider = new RangeSlider(5.0f, 5.0f);

        assertTrue(slider.getMax() > slider.getMin());
    }

    @Test
    void setValuesOrdersThePairAndClampsIt() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);

        slider.setValues(80.0f, 20.0f);

        assertEquals(20.0f, slider.getLowerValue());
        assertEquals(80.0f, slider.getUpperValue());

        slider.setValues(-50.0f, 500.0f);
        assertEquals(0.0f, slider.getLowerValue());
        assertEquals(100.0f, slider.getUpperValue());
    }

    @Test
    void valuesSnapToTheStep() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        slider.setStep(10.0f);

        slider.setValues(23.0f, 77.0f);

        assertEquals(20.0f, slider.getLowerValue());
        assertEquals(80.0f, slider.getUpperValue());
    }

    @Test
    void rangeChangeNotifiesWithBothValues() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        List<float[]> seen = new ArrayList<>();
        slider.setOnChange((lo, hi) -> seen.add(new float[]{lo, hi}));

        slider.setValues(10.0f, 90.0f);

        assertEquals(1, seen.size());
        assertEquals(10.0f, seen.get(0)[0]);
        assertEquals(90.0f, seen.get(0)[1]);
    }

    @Test
    void settingTheSameRangeDoesNotNotify() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        List<float[]> seen = new ArrayList<>();
        slider.setOnChange((lo, hi) -> seen.add(new float[]{lo, hi}));

        slider.setValues(0.0f, 0.0f);

        assertTrue(seen.isEmpty());
    }

    @Test
    void draggingTheLowerThumbPastTheUpperCarriesIt() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        slider.setX(0);
        slider.setY(0);
        slider.setWidth(200);
        slider.setHeight(24);
        slider.setValues(20.0f, 40.0f);

        slider.onMouseEvent(mouse(MouseEventType.PRESS, 40, 12));
        slider.onMouseEvent(mouse(MouseEventType.DRAG, 200, 12));

        assertEquals(slider.getUpperValue(), slider.getLowerValue(),
                "the lower thumb cannot pass the upper one");
        assertEquals(100.0f, slider.getUpperValue());
    }

    @Test
    void draggingTheUpperThumbPastTheLowerCarriesIt() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        slider.setX(0);
        slider.setY(0);
        slider.setWidth(200);
        slider.setHeight(24);
        slider.setValues(60.0f, 80.0f);

        slider.onMouseEvent(mouse(MouseEventType.PRESS, 170, 12));
        slider.onMouseEvent(mouse(MouseEventType.DRAG, 0, 12));

        assertEquals(slider.getLowerValue(), slider.getUpperValue());
        assertEquals(0.0f, slider.getLowerValue());
    }

    @Test
    void arrowKeysMoveTheFocusedThumb() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        slider.setStep(1.0f);
        slider.setValues(10.0f, 90.0f);
        slider.requestFocus();

        slider.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(11.0f, slider.getLowerValue());
        assertEquals(90.0f, slider.getUpperValue());
    }

    @Test
    void tabSwitchesTheActiveThumb() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        slider.setStep(1.0f);
        slider.setValues(10.0f, 90.0f);
        slider.requestFocus();

        slider.onKeyEvent(key(GlyphKeys.TAB));
        slider.onKeyEvent(key(GlyphKeys.LEFT));

        assertEquals(10.0f, slider.getLowerValue());
        assertEquals(89.0f, slider.getUpperValue(), "the upper thumb is now the active one");
    }

    @Test
    void valuePropertiesDriveTheValues() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);

        // Raise the upper thumb first so the lower set is not reordered.
        slider.upperValueProperty().set(70.0f);
        slider.lowerValueProperty().set(30.0f);

        assertEquals(30.0f, slider.getLowerValue());
        assertEquals(70.0f, slider.getUpperValue());
    }

    @Test
    void rangeSliderNeverConsumesInputWhenDisabled() {
        RangeSlider slider = new RangeSlider(0.0f, 100.0f);
        slider.setEnabled(false);
        slider.setWidth(200);
        slider.setHeight(24);

        assertFalse(slider.onMouseEvent(mouse(MouseEventType.PRESS, 50, 12)));
    }

    // ------------------------------------------------------------------
    // ToggleButton
    // ------------------------------------------------------------------

    @Test
    void toggleButtonStartsOffWithAnEmptyCaption() {
        ToggleButton toggle = new ToggleButton();

        assertFalse(toggle.isOn());
        assertEquals("", toggle.getText());
        assertEquals(AccessibleRole.TOGGLE_BUTTON, toggle.getAccessibleRole());
    }

    @Test
    void nullToggleButtonCaptionBecomesEmpty() {
        ToggleButton toggle = new ToggleButton(null);

        assertEquals("", toggle.getText());
    }

    @Test
    void toggleButtonAccessibleNameFallsBackWhenEmpty() {
        assertEquals("Toggle button", new ToggleButton().getAccessibleName());
        assertEquals("Bold", new ToggleButton("Bold").getAccessibleName());
    }

    @Test
    void setOnNotifiesOnlyOnRealChanges() {
        ToggleButton toggle = new ToggleButton("Bold");
        List<Boolean> seen = new ArrayList<>();
        toggle.setOnChange(seen::add);

        toggle.setOn(true);
        toggle.setOn(true);

        assertEquals(List.of(true), seen);
        assertTrue(toggle.isOn());
    }

    @Test
    void toggleFlipsTheState() {
        ToggleButton toggle = new ToggleButton("Bold");

        toggle.toggle();
        assertTrue(toggle.isOn());

        toggle.toggle();
        assertFalse(toggle.isOn());
    }

    @Test
    void clickingAToggleButtonFlipsIt() {
        ToggleButton toggle = new ToggleButton("Bold");
        toggle.setWidth(80);
        toggle.setHeight(28);

        toggle.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10));
        toggle.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 10));
        assertTrue(toggle.isOn());

        toggle.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10));
        toggle.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 10));
        assertFalse(toggle.isOn());
    }

    @Test
    void releasingOutsideAToggleButtonDoesNotFlipIt() {
        ToggleButton toggle = new ToggleButton("Bold");
        toggle.setWidth(80);
        toggle.setHeight(28);

        toggle.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10));
        toggle.onMouseEvent(mouse(MouseEventType.RELEASE, 500, 500));

        assertFalse(toggle.isOn());
    }

    @Test
    void spaceTogglesAFocusedToggleButton() {
        ToggleButton toggle = new ToggleButton("Bold");
        toggle.requestFocus();

        toggle.onKeyEvent(new KeyEvent(KeyEventType.PRESS, GlyphKeys.SPACE, ' ',
                EnumSet.noneOf(KeyModifier.class)));
        assertFalse(toggle.isOn(), "space flips on release, not on press");

        toggle.onKeyEvent(new KeyEvent(KeyEventType.RELEASE, GlyphKeys.SPACE, ' ',
                EnumSet.noneOf(KeyModifier.class)));
        assertTrue(toggle.isOn());
    }

    @Test
    void unfocusedToggleButtonIgnoresSpace() {
        ToggleButton toggle = new ToggleButton("Bold");

        toggle.onKeyEvent(new KeyEvent(KeyEventType.PRESS, GlyphKeys.SPACE, ' ',
                EnumSet.noneOf(KeyModifier.class)));
        toggle.onKeyEvent(new KeyEvent(KeyEventType.RELEASE, GlyphKeys.SPACE, ' ',
                EnumSet.noneOf(KeyModifier.class)));

        assertFalse(toggle.isOn());
    }

    @Test
    void disabledToggleButtonIgnoresPointerInput() {
        ToggleButton toggle = new ToggleButton("Bold");
        toggle.setEnabled(false);
        toggle.setWidth(80);
        toggle.setHeight(28);

        assertFalse(toggle.onMouseEvent(mouse(MouseEventType.PRESS, 10, 10)));
        assertFalse(toggle.isOn());
    }

    @Test
    void toggleButtonTextPropertyDrivesTheCaption() {
        ToggleButton toggle = new ToggleButton("Bold");

        toggle.textProperty().set("Italic");

        assertEquals("Italic", toggle.getText());
    }

    // ------------------------------------------------------------------
    // StatusBar
    // ------------------------------------------------------------------

    @Test
    void statusBarStartsEmpty() {
        StatusBar bar = new StatusBar();

        assertEquals(0, bar.getSegmentCount());
        assertEquals(AccessibleRole.STATUS_BAR, bar.getAccessibleRole());
    }

    @Test
    void segmentsCanBeAddedAndUpdated() {
        StatusBar bar = new StatusBar();

        bar.addSegment("Ready").addSegment("Ln 1", 0xFF00FF00);

        assertEquals(2, bar.getSegmentCount());
        assertEquals("Ready", bar.getSegmentText(0));

        bar.setSegmentText(0, "Modified");
        assertEquals("Modified", bar.getSegmentText(0));
    }

    @Test
    void nullSegmentTextBecomesEmpty() {
        StatusBar bar = new StatusBar();
        bar.addSegment(null);

        assertEquals("", bar.getSegmentText(0));
    }

    @Test
    void clearRemovesEverySegment() {
        StatusBar bar = new StatusBar();
        bar.addSegment("a");
        bar.addSegment("b");

        bar.clear();

        assertEquals(0, bar.getSegmentCount());
    }

    @Test
    void statusBarNeverConsumesPointerInput() {
        StatusBar bar = new StatusBar();
        bar.setWidth(300);
        bar.setHeight(24);

        assertFalse(bar.onMouseEvent(mouse(MouseEventType.PRESS, 5, 5)));
    }

    @Test
    void statusBarAccessibleNameJoinsItsSegments() {
        StatusBar bar = new StatusBar();
        bar.addSegment("Ready");
        bar.addSegment("100%");

        assertEquals("Ready, 100%", bar.getAccessibleName());
    }
}
