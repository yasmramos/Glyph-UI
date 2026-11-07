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
import io.github.yasmramos.glyphui.graphics.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link ColorPicker}: palette management, hex parsing,
 * mouse and keyboard selection, and the change handler.
 */
class ColorPickerTest extends TestBase {

    private static final int RED = 0xFFFF0000;
    private static final int GREEN = 0xFF00FF00;
    private static final int BLUE = 0xFF0000FF;

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

    /** A picker over an explicit palette, sized to hold it. */
    private static ColorPicker sizedPicker(int... palette) {
        ColorPicker picker = new ColorPicker(palette);
        picker.setWidth(200.0f);
        picker.setHeight(120.0f);
        return picker;
    }

    @Test
    void defaultPaletteIsNotEmptyAndSelectsTheFirstSwatch() {
        ColorPicker picker = new ColorPicker();

        assertTrue(picker.getSwatchCount() > 0);
        assertEquals(picker.getSwatch(0), picker.getValue());
    }

    @Test
    void explicitPaletteReplacesTheDefault() {
        ColorPicker picker = new ColorPicker(RED, GREEN, BLUE);

        assertEquals(3, picker.getSwatchCount());
        assertEquals(RED, picker.getValue());
        assertEquals(List.of(RED, GREEN, BLUE), picker.getPalette());
    }

    @Test
    void emptyPaletteKeepsTheDefault() {
        ColorPicker picker = new ColorPicker(new int[0]);

        assertTrue(picker.getSwatchCount() > 0);
    }

    @Test
    void addAndRemoveSwatches() {
        ColorPicker picker = new ColorPicker(RED);

        picker.addSwatch(GREEN);
        assertEquals(2, picker.getSwatchCount());

        picker.removeSwatch(0);
        assertEquals(1, picker.getSwatchCount());
        assertEquals(GREEN, picker.getSwatch(0));
    }

    @Test
    void removeSwatchRejectsBadIndex() {
        ColorPicker picker = new ColorPicker(RED);

        assertThrows(IndexOutOfBoundsException.class, () -> picker.removeSwatch(5));
    }

    @Test
    void setPaletteReplacesEverything() {
        ColorPicker picker = new ColorPicker(RED, GREEN);

        picker.setPalette(BLUE);

        assertEquals(1, picker.getSwatchCount());
        assertEquals(BLUE, picker.getSwatch(0));
    }

    @Test
    void columnsAreClampedToAtLeastOne() {
        ColorPicker picker = new ColorPicker(RED, GREEN);

        picker.setColumns(0);

        assertEquals(1, picker.getColumns());
    }

    @Test
    void hexRoundTripsThroughTheValue() {
        ColorPicker picker = sizedPicker(RED, GREEN);

        assertTrue(picker.setHex("#0000FF"));

        assertEquals(BLUE, picker.getValue());
        assertEquals("#0000FF", picker.getHex());
    }

    @Test
    void shortHexExpandsEachDigit() {
        assertEquals(Integer.valueOf(0xFFFF00FF), ColorPicker.parseHex("#F0F"));
    }

    @Test
    void hexAcceptsAnOptionalHash() {
        assertEquals(Integer.valueOf(0xFF00FF00), ColorPicker.parseHex("00ff00"));
    }

    @Test
    void invalidHexIsRejected() {
        assertNull(ColorPicker.parseHex(null));
        assertNull(ColorPicker.parseHex("nope"));
        assertNull(ColorPicker.parseHex("#12345"));

        ColorPicker picker = new ColorPicker(RED);
        assertFalse(picker.setHex("not-a-color"));
        assertEquals(RED, picker.getValue());
    }

    @Test
    void clickingASwatchPicksItsColour() {
        ColorPicker picker = sizedPicker(RED, GREEN, BLUE);
        float secondX = ColorPicker.PADDING + ColorPicker.SWATCH_SIZE
                + ColorPicker.SWATCH_GAP + 1.0f;

        picker.onMouseEvent(mouse(MouseEventType.PRESS, secondX,
                ColorPicker.PADDING + 1.0f));
        picker.onMouseEvent(mouse(MouseEventType.RELEASE, secondX,
                ColorPicker.PADDING + 1.0f));

        assertEquals(GREEN, picker.getValue());
    }

    @Test
    void pickingASwatchFiresTheHandler() {
        ColorPicker picker = sizedPicker(RED, GREEN);
        List<Integer> seen = new ArrayList<>();
        picker.setOnChange(seen::add);
        float secondX = ColorPicker.PADDING + ColorPicker.SWATCH_SIZE
                + ColorPicker.SWATCH_GAP + 1.0f;

        picker.onMouseEvent(mouse(MouseEventType.PRESS, secondX,
                ColorPicker.PADDING + 1.0f));
        picker.onMouseEvent(mouse(MouseEventType.RELEASE, secondX,
                ColorPicker.PADDING + 1.0f));

        assertEquals(List.of(GREEN), seen);
    }

    @Test
    void programmaticValueDoesNotFireTheHandler() {
        ColorPicker picker = sizedPicker(RED, GREEN);
        List<Integer> seen = new ArrayList<>();
        picker.setOnChange(seen::add);

        picker.setValue(GREEN);

        assertTrue(seen.isEmpty());
        assertEquals(GREEN, picker.getValue());
    }

    @Test
    void arrowKeysMoveThroughTheGrid() {
        ColorPicker picker = sizedPicker(RED, GREEN, BLUE);
        picker.setColumns(3);

        picker.onKeyEvent(key(GlyphKeys.RIGHT));
        assertEquals(GREEN, picker.getValue());

        picker.onKeyEvent(key(GlyphKeys.LEFT));
        assertEquals(RED, picker.getValue());
    }

    @Test
    void verticalKeysMoveByARow() {
        ColorPicker picker = sizedPicker(RED, GREEN, BLUE);
        picker.setColumns(1);

        picker.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals(GREEN, picker.getValue());

        picker.onKeyEvent(key(GlyphKeys.UP));
        assertEquals(RED, picker.getValue());
    }

    @Test
    void homeAndEndJumpToTheBounds() {
        ColorPicker picker = sizedPicker(RED, GREEN, BLUE);

        picker.onKeyEvent(key(GlyphKeys.END));
        assertEquals(BLUE, picker.getValue());

        picker.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(RED, picker.getValue());
    }

    @Test
    void keyboardSelectionKeepsTheObservablePropertyInSync() {
        ColorPicker picker = sizedPicker(RED, GREEN, BLUE);
        Property<Integer> value = picker.valueProperty();

        picker.onKeyEvent(key(GlyphKeys.END));

        assertEquals(BLUE, value.get());
        assertEquals(BLUE, picker.getValue());
    }

    @Test
    void valuePropertyDrivesTheState() {
        ColorPicker picker = sizedPicker(RED, GREEN);

        picker.valueProperty().set(GREEN);

        assertEquals(GREEN, picker.getValue());
    }

    @Test
    void handlerFailureIsSwallowed() {
        ColorPicker picker = sizedPicker(RED, GREEN);
        picker.setOnChange(argb -> {
            throw new IllegalStateException("boom");
        });

        picker.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(GREEN, picker.getValue());
    }

    @Test
    void disabledPickerIgnoresInput() {
        ColorPicker picker = sizedPicker(RED, GREEN);
        picker.setEnabled(false);

        picker.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(RED, picker.getValue());
    }

    @Test
    void hexReadOutCanBeHidden() {
        ColorPicker picker = new ColorPicker(RED, GREEN);
        float withHex = picker.getPreferredHeight();

        picker.setShowHex(false);

        assertTrue(picker.getPreferredHeight() < withHex);
    }

    @Test
    void previewStripCanBeHidden() {
        ColorPicker picker = new ColorPicker(RED, GREEN);
        float withPreview = picker.getPreferredHeight();

        picker.setShowPreview(false);

        assertTrue(picker.getPreferredHeight() < withPreview);
    }

    @Test
    void preferredWidthGrowsWithColumns() {
        ColorPicker oneColumn = new ColorPicker(RED, GREEN);
        oneColumn.setColumns(1);
        ColorPicker twoColumns = new ColorPicker(RED, GREEN);
        twoColumns.setColumns(2);

        assertTrue(twoColumns.getPreferredWidth() > oneColumn.getPreferredWidth());
    }

    @Test
    void accessibleNameReportsTheCurrentHex() {
        ColorPicker picker = sizedPicker(RED);

        assertTrue(picker.getAccessibleName().contains("#FF0000"));
        assertEquals(AccessibleRole.COLOR_PICKER, picker.getAccessibleRole());
    }

    @Test
    void settingTheSameValueIsANoOp() {
        ColorPicker picker = sizedPicker(RED, GREEN);
        List<Integer> seen = new ArrayList<>();
        picker.setOnChange(seen::add);

        picker.setValue(RED);

        assertTrue(seen.isEmpty());
        assertEquals(RED, picker.getValue());
        assertNotEquals(0, picker.getValue());
    }
}
