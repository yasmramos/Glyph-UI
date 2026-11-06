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
 * Behaviour tests for {@link Rating}: value clamping, click mapping, half
 * stars, keyboard control and the change handler.
 */
class RatingTest extends TestBase {

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

    private static Rating sizedRating(int max) {
        Rating rating = new Rating(max);
        rating.setWidth(200.0f);
        rating.setHeight(24.0f);
        return rating;
    }

    /** Clicks the star at the given zero-based index. */
    private static void clickStar(Rating rating, int index) {
        float slot = Rating.STAR_SIZE + Rating.STAR_GAP;
        float x = index * slot + Rating.STAR_SIZE / 2.0f;
        rating.onMouseEvent(mouse(MouseEventType.PRESS, x, 12));
        rating.onMouseEvent(mouse(MouseEventType.RELEASE, x, 12));
    }

    @Test
    void defaultsToFiveStarsAndNoRating() {
        Rating rating = new Rating();

        assertEquals(Rating.DEFAULT_MAX, rating.getMax());
        assertEquals(0.0f, rating.getValue());
        assertFalse(rating.isAllowHalf());
        assertFalse(rating.isReadOnly());
        assertEquals(AccessibleRole.RATING, rating.getAccessibleRole());
    }

    @Test
    void maxIsClampedToAtLeastOne() {
        Rating rating = new Rating(-3);

        assertEquals(1, rating.getMax());
    }

    @Test
    void setValueClampsIntoRange() {
        Rating rating = sizedRating(5);

        rating.setValue(99.0f);
        assertEquals(5.0f, rating.getValue());

        rating.setValue(-4.0f);
        assertEquals(0.0f, rating.getValue());
    }

    @Test
    void clickingAStarSelectsThatCount() {
        Rating rating = sizedRating(5);

        clickStar(rating, 2);

        assertEquals(3.0f, rating.getValue());
    }

    @Test
    void clickingTheSameStarClearsTheRating() {
        Rating rating = sizedRating(5);
        clickStar(rating, 2);

        clickStar(rating, 2);

        assertEquals(0.0f, rating.getValue());
    }

    @Test
    void halfStarsRoundToTheLowerHalf() {
        Rating rating = sizedRating(5);
        rating.setAllowHalf(true);

        // Left half of the third star -> 2.5
        float slot = Rating.STAR_SIZE + Rating.STAR_GAP;
        float x = 2 * slot + 2.0f;
        rating.onMouseEvent(mouse(MouseEventType.PRESS, x, 12));
        rating.onMouseEvent(mouse(MouseEventType.RELEASE, x, 12));

        assertEquals(2.5f, rating.getValue());
    }

    @Test
    void keyboardArrowsMoveTheValue() {
        Rating rating = sizedRating(5);

        rating.onKeyEvent(key(GlyphKeys.RIGHT));
        rating.onKeyEvent(key(GlyphKeys.RIGHT));
        assertEquals(2.0f, rating.getValue());

        rating.onKeyEvent(key(GlyphKeys.LEFT));
        assertEquals(1.0f, rating.getValue());

        rating.onKeyEvent(key(GlyphKeys.END));
        assertEquals(5.0f, rating.getValue());

        rating.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0.0f, rating.getValue());
    }

    @Test
    void changeHandlerReportsTheRoundedValue() {
        Rating rating = sizedRating(5);
        List<Integer> changes = new ArrayList<>();
        rating.setOnChange(changes::add);

        clickStar(rating, 3);

        assertEquals(List.of(4), changes);
    }

    @Test
    void readOnlyIgnoresPointerAndKeyboard() {
        Rating rating = sizedRating(5);
        rating.setValue(3.0f);
        rating.setReadOnly(true);

        assertFalse(rating.onMouseEvent(mouse(MouseEventType.PRESS, 10, 12)));
        rating.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(3.0f, rating.getValue());
    }

    @Test
    void handlerFailureIsSwallowed() {
        Rating rating = sizedRating(5);
        rating.setOnChange(value -> {
            throw new IllegalStateException("boom");
        });

        clickStar(rating, 1);

        assertEquals(2.0f, rating.getValue());
    }
}
