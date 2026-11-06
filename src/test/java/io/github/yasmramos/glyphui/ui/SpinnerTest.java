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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Spinner}: range/step clamping, the change
 * listener, keyboard nudging and the pointer steppers. No Skija native code is
 * touched because the paints are created lazily on the first render.
 */
class SpinnerTest extends TestBase {

    private FocusManager focusManager;

    @BeforeEach
    void setUp() {
        focusManager = new FocusManager(() -> { });
        FocusManager.setGlobalFocusManager(focusManager);
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ' ',
                EnumSet.noneOf(KeyModifier.class));
    }

    @Test
    void defaultsToTheDocumentedRange() {
        Spinner spinner = new Spinner();

        assertEquals(0.0f, spinner.getMin());
        assertEquals(100.0f, spinner.getMax());
        assertEquals(1.0f, spinner.getStep());
        assertEquals(0.0f, spinner.getValue());
    }

    @Test
    void setValueClampsToTheRange() {
        Spinner spinner = new Spinner();
        spinner.setRange(0.0f, 10.0f);

        spinner.setValue(-5.0f);
        assertEquals(0.0f, spinner.getValue());

        spinner.setValue(42.0f);
        assertEquals(10.0f, spinner.getValue());
    }

    @Test
    void setRangeNormalisesReversedBounds() {
        Spinner spinner = new Spinner();
        spinner.setRange(10.0f, 0.0f);

        assertEquals(0.0f, spinner.getMin());
        assertEquals(10.0f, spinner.getMax());
    }

    @Test
    void valueSnapsToTheStep() {
        Spinner spinner = new Spinner();
        spinner.setRange(0.0f, 10.0f);
        spinner.setStep(2.0f);

        spinner.setValue(5.0f);
        assertEquals(6.0f, spinner.getValue());
    }

    @Test
    void aNonPositiveStepFallsBackToOne() {
        Spinner spinner = new Spinner();

        spinner.setStep(0.0f);
        assertEquals(1.0f, spinner.getStep());

        spinner.setStep(-3.0f);
        assertEquals(1.0f, spinner.getStep());
    }

    @Test
    void onChangeFiresOnlyOnRealChanges() {
        Spinner spinner = new Spinner();
        List<Float> changes = new ArrayList<>();
        spinner.setOnChange(changes::add);

        spinner.setValue(1.0f);
        spinner.setValue(1.0f);
        spinner.setValue(2.0f);

        assertEquals(List.of(1.0f, 2.0f), changes);
    }

    @Test
    void stepMovesByOneStepInEitherDirection() {
        Spinner spinner = new Spinner();
        spinner.setStep(5.0f);

        spinner.step(1);
        assertEquals(5.0f, spinner.getValue());

        spinner.step(-1);
        assertEquals(0.0f, spinner.getValue());
    }

    @Test
    void arrowKeysNudgeAndHomeEndJumpToTheBounds() {
        Spinner spinner = new Spinner();
        spinner.setWidth(80.0f);
        spinner.setHeight(24.0f);
        spinner.requestFocus();

        spinner.onKeyEvent(key(GlyphKeys.UP));
        assertEquals(1.0f, spinner.getValue());

        spinner.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals(0.0f, spinner.getValue());

        spinner.onKeyEvent(key(GlyphKeys.END));
        assertEquals(100.0f, spinner.getValue());

        spinner.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0.0f, spinner.getValue());
    }

    @Test
    void pageKeysMoveByTenSteps() {
        Spinner spinner = new Spinner();
        spinner.setWidth(80.0f);
        spinner.setHeight(24.0f);
        spinner.requestFocus();

        spinner.onKeyEvent(key(GlyphKeys.PAGE_UP));
        assertEquals(10.0f, spinner.getValue());
    }

    @Test
    void keysAreIgnoredWhenNotFocused() {
        Spinner spinner = new Spinner();
        spinner.setWidth(80.0f);
        spinner.setHeight(24.0f);

        spinner.onKeyEvent(key(GlyphKeys.UP));
        assertEquals(0.0f, spinner.getValue());
    }

    @Test
    void clickingTheRightHalfStepsUpAndTheLeftHalfStepsDown() {
        Spinner spinner = new Spinner(0.0f, 0.0f, 80.0f, 24.0f);
        spinner.setValue(10.0f);

        spinner.onMouseEvent(mouse(MouseEventType.PRESS, 70.0f, 12.0f));
        assertEquals(11.0f, spinner.getValue());

        spinner.onMouseEvent(mouse(MouseEventType.PRESS, 10.0f, 12.0f));
        assertEquals(10.0f, spinner.getValue());
    }

    @Test
    void aDisabledSpinnerIgnoresInput() {
        Spinner spinner = new Spinner(0.0f, 0.0f, 80.0f, 24.0f);
        spinner.setEnabled(false);

        assertFalse(spinner.onMouseEvent(mouse(MouseEventType.PRESS, 70.0f, 12.0f)));
        assertEquals(0.0f, spinner.getValue());
    }

    @Test
    void reportsItsAccessibleRoleAndSize() {
        Spinner spinner = new Spinner();

        assertEquals(AccessibleRole.SPINNER, spinner.getAccessibleRole());
        assertEquals(Spinner.DEFAULT_SPINNER_WIDTH, spinner.getPreferredWidth());
        assertEquals(Spinner.DEFAULT_SPINNER_HEIGHT, spinner.getPreferredHeight());
        assertTrue(spinner.isFocusable());
    }
}
