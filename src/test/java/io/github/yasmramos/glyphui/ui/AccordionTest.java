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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Accordion}: section registration, the
 * single-open rule, keyboard toggling and inert input outside the headers. No
 * Skija native code is touched because the paints are created lazily on the
 * first render.
 */
class AccordionTest extends TestBase {

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

    private static Accordion threeSections() {
        Accordion accordion = new Accordion();
        accordion.addSection("One", new Label("one"));
        accordion.addSection("Two", new Label("two"));
        accordion.addSection("Three", new Label("three"));
        return accordion;
    }

    @Test
    void startsEmpty() {
        Accordion accordion = new Accordion();

        assertEquals(0, accordion.getSectionCount());
        assertEquals(List.of(), accordion.getOpenIndices());
    }

    @Test
    void firstSectionOpensByDefault() {
        Accordion accordion = threeSections();

        assertEquals(3, accordion.getSectionCount());
        assertEquals("One", accordion.getSectionTitle(0));
        assertEquals(List.of(0), accordion.getOpenIndices());
        assertTrue(accordion.getChildren().get(0).isVisible());
        assertFalse(accordion.getChildren().get(1).isVisible());
    }

    @Test
    void rejectsANullBody() {
        Accordion accordion = new Accordion();

        assertThrows(IllegalArgumentException.class,
                () -> accordion.addSection("One", null));
    }

    @Test
    void openingASectionClosesThePreviousOne() {
        Accordion accordion = threeSections();

        accordion.setSectionOpen(1, true);

        assertEquals(List.of(1), accordion.getOpenIndices());
        assertFalse(accordion.getChildren().get(0).isVisible());
        assertTrue(accordion.getChildren().get(1).isVisible());
    }

    @Test
    void multipleSectionsStayOpenWhenAllowed() {
        Accordion accordion = threeSections();
        accordion.setAllowMultiple(true);

        accordion.setSectionOpen(1, true);
        accordion.setSectionOpen(2, true);

        assertEquals(List.of(0, 1, 2), accordion.getOpenIndices());
        assertTrue(accordion.isAllowMultiple());
    }

    @Test
    void turningOffMultipleClosesAllButTheFirstOpenSection() {
        Accordion accordion = threeSections();
        accordion.setAllowMultiple(true);
        accordion.setSectionOpen(1, true);
        accordion.setSectionOpen(2, true);

        accordion.setAllowMultiple(false);

        assertEquals(List.of(0), accordion.getOpenIndices());
    }

    @Test
    void togglingClosesAnOpenSection() {
        Accordion accordion = threeSections();

        accordion.toggleSection(0);

        assertEquals(List.of(), accordion.getOpenIndices());
    }

    @Test
    void openIndicesPropertyDrivesTheSections() {
        Accordion accordion = threeSections();
        accordion.setAllowMultiple(true);

        accordion.openIndicesProperty().set(List.of(2));

        assertEquals(List.of(2), accordion.getOpenIndices());
        assertTrue(accordion.getChildren().get(2).isVisible());
        assertFalse(accordion.getChildren().get(0).isVisible());
    }

    @Test
    void outOfRangeIndicesAreIgnored() {
        Accordion accordion = threeSections();

        accordion.setSectionOpen(9, true);
        accordion.setSectionOpen(-1, true);
        accordion.toggleSection(9);

        assertEquals(List.of(0), accordion.getOpenIndices());
    }

    @Test
    void clickingAHeaderTogglesItsSection() {
        Accordion accordion = threeSections();
        accordion.setWidth(200.0f);
        accordion.setHeight(200.0f);

        // Second header sits at y = HEADER_HEIGHT + SECTION_GAP.
        float headerTwoY = Accordion.HEADER_HEIGHT + Accordion.SECTION_GAP + 1.0f;
        assertTrue(accordion.onMouseEvent(
                mouse(MouseEventType.PRESS, 10.0f, headerTwoY)));

        assertEquals(List.of(1), accordion.getOpenIndices());
    }

    @Test
    void clickingOutsideTheWidgetIsNotConsumed() {
        Accordion accordion = threeSections();
        accordion.setWidth(100.0f);
        accordion.setHeight(100.0f);

        assertFalse(accordion.onMouseEvent(
                mouse(MouseEventType.PRESS, 500.0f, 500.0f)));
    }

    @Test
    void keyboardTogglesTheFocusedSection() {
        Accordion accordion = threeSections();

        accordion.onKeyEvent(key(GlyphKeys.DOWN));
        accordion.onKeyEvent(key(GlyphKeys.ENTER));

        assertEquals(List.of(1), accordion.getOpenIndices());
    }

    @Test
    void reportsItsAccessibleRole() {
        Accordion accordion = threeSections();

        assertEquals(AccessibleRole.ACCORDION, accordion.getAccessibleRole());
    }
}
