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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Stepper}: step management, the linear-navigation
 * policy, click and keyboard navigation and the change handler.
 */
class StepperTest extends TestBase {

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

    private static Stepper sizedStepper(String... labels) {
        Stepper stepper = new Stepper(labels);
        stepper.setWidth(400.0f);
        stepper.setHeight(40.0f);
        return stepper;
    }

    @Test
    void clickUpdatesTheObservableCurrentStepProperty() {
        Stepper stepper = sizedStepper("One", "Two", "Three");
        stepper.setCurrentStep(2);
        Property<Integer> current = stepper.currentStepProperty();

        stepper.onMouseEvent(mouse(MouseEventType.PRESS, 5, 20));
        stepper.onMouseEvent(mouse(MouseEventType.RELEASE, 5, 20));

        assertEquals(0, stepper.getCurrentStep());
        assertEquals(0, current.get());
    }

    @Test
    void keyboardUpdatesTheObservableCurrentStepProperty() {
        Stepper stepper = sizedStepper("One", "Two", "Three");
        stepper.setLinear(false);
        Property<Integer> current = stepper.currentStepProperty();

        stepper.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(1, stepper.getCurrentStep());
        assertEquals(1, current.get());
    }

    @Test
    void defaultsToNoStepsAndLinear() {
        Stepper stepper = new Stepper();

        assertEquals(0, stepper.getStepCount());
        assertEquals(0, stepper.getCurrentStep());
        assertTrue(stepper.isLinear());
        assertTrue(stepper.isFocusable());
        assertEquals(AccessibleRole.STEPPER, stepper.getAccessibleRole());
    }

    @Test
    void constructorAddsStepsAndNullBecomesEmpty() {
        Stepper stepper = new Stepper("One", null, "Three");

        assertEquals(3, stepper.getStepCount());
        assertEquals("One", stepper.getStepLabel(0));
        assertEquals("", stepper.getStepLabel(1));
        assertEquals("Three", stepper.getStepLabel(2));
    }

    @Test
    void currentStepIsClampedIntoRange() {
        Stepper stepper = sizedStepper("One", "Two", "Three");

        stepper.setCurrentStep(99);
        assertEquals(2, stepper.getCurrentStep());

        stepper.setCurrentStep(-5);
        assertEquals(0, stepper.getCurrentStep());
    }

    @Test
    void linearModeBlocksForwardNavigation() {
        Stepper stepper = sizedStepper("One", "Two", "Three");
        List<Integer> changes = new ArrayList<>();
        stepper.setOnStepChange(changes::add);

        stepper.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(0, stepper.getCurrentStep());
        assertTrue(changes.isEmpty());
    }

    @Test
    void nonLinearModeAllowsJumpingForward() {
        Stepper stepper = sizedStepper("One", "Two", "Three");
        stepper.setLinear(false);
        List<Integer> changes = new ArrayList<>();
        stepper.setOnStepChange(changes::add);

        stepper.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(1, stepper.getCurrentStep());
        assertEquals(List.of(1), changes);
    }

    @Test
    void keyboardHomeAndEndJumpToTheBounds() {
        Stepper stepper = sizedStepper("One", "Two", "Three");
        stepper.setLinear(false);

        stepper.onKeyEvent(key(GlyphKeys.END));
        assertEquals(2, stepper.getCurrentStep());

        stepper.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0, stepper.getCurrentStep());
    }

    @Test
    void clickingAReachedStepMovesBack() {
        Stepper stepper = sizedStepper("One", "Two", "Three");
        stepper.setLinear(false);
        stepper.setCurrentStep(2);

        // Click within the first step's marker region.
        stepper.onMouseEvent(mouse(MouseEventType.PRESS, 5, 20));
        stepper.onMouseEvent(mouse(MouseEventType.RELEASE, 5, 20));

        assertEquals(0, stepper.getCurrentStep());
    }

    @Test
    void handlerFailureIsSwallowed() {
        Stepper stepper = sizedStepper("One", "Two");
        stepper.setLinear(false);
        stepper.setOnStepChange(index -> {
            throw new IllegalStateException("boom");
        });

        stepper.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(1, stepper.getCurrentStep());
    }

    @Test
    void disabledStepperIgnoresInput() {
        Stepper stepper = sizedStepper("One", "Two");
        stepper.setLinear(false);
        stepper.setEnabled(false);

        stepper.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(0, stepper.getCurrentStep());
    }

    @Test
    void currentStepPropertyDrivesTheState() {
        Stepper stepper = sizedStepper("One", "Two", "Three");

        stepper.currentStepProperty().set(2);

        assertEquals(2, stepper.getCurrentStep());
    }

    @Test
    void preferredSizeGrowsWithSteps() {
        Stepper one = new Stepper("One");
        Stepper three = new Stepper("One", "Two", "Three");

        assertTrue(three.getPreferredWidth() > one.getPreferredWidth());
        assertTrue(one.getPreferredHeight() > 0.0f);
    }
}
