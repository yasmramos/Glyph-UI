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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Pagination}: page-count clamping, navigation via
 * the handler and keyboard, and the observable current page. No Skija native
 * code is touched because the paints are created lazily on the first render.
 */
class PaginationTest extends TestBase {

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
    void defaultsToOnePageAtPageZero() {
        Pagination pager = new Pagination();

        assertEquals(1, pager.getPageCount());
        assertEquals(0, pager.getCurrentPage());
    }

    @Test
    void pageCountBelowOneIsRaisedToOne() {
        Pagination pager = new Pagination();

        pager.setPageCount(0);

        assertEquals(1, pager.getPageCount());
    }

    @Test
    void shrinkingThePageCountClampsTheCurrentPage() {
        Pagination pager = new Pagination();
        pager.setPageCount(10);
        pager.setCurrentPage(8);

        pager.setPageCount(3);

        assertEquals(3, pager.getPageCount());
        assertEquals(2, pager.getCurrentPage());
    }

    @Test
    void currentPageIsClampedIntoRange() {
        Pagination pager = new Pagination();
        pager.setPageCount(5);

        pager.setCurrentPage(99);
        assertEquals(4, pager.getCurrentPage());

        pager.setCurrentPage(-3);
        assertEquals(0, pager.getCurrentPage());
    }

    @Test
    void currentPagePropertyStaysInSync() {
        Pagination pager = new Pagination();
        pager.setPageCount(4);

        pager.currentPageProperty().set(2);

        assertEquals(2, pager.getCurrentPage());
    }

    @Test
    void keyboardStepsAndJumpsPages() {
        Pagination pager = new Pagination();
        pager.setPageCount(6);
        AtomicInteger changed = new AtomicInteger(-1);
        pager.setOnPageChange(changed::set);

        pager.onKeyEvent(key(GlyphKeys.RIGHT));
        assertEquals(1, pager.getCurrentPage());
        assertEquals(1, changed.get());

        pager.onKeyEvent(key(GlyphKeys.END));
        assertEquals(5, pager.getCurrentPage());
        assertEquals(5, changed.get());

        pager.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals(0, pager.getCurrentPage());

        // Already at the first page: no change, so no notification.
        int before = changed.get();
        pager.onKeyEvent(key(GlyphKeys.LEFT));
        assertEquals(0, pager.getCurrentPage());
        assertEquals(before, changed.get());
    }

    @Test
    void clickingTheNextArrowAdvancesOnePage() {
        Pagination pager = new Pagination();
        pager.setPageCount(3);
        AtomicInteger changed = new AtomicInteger(-1);
        pager.setOnPageChange(changed::set);
        // Force a known width so the slots are laid out predictably.
        pager.setWidth(pager.getPreferredWidth());
        pager.setHeight(Pagination.BUTTON_SIZE);

        // The next arrow is the last slot; click inside its left edge.
        float nextX = pager.getWidth() - Pagination.BUTTON_SIZE / 2.0f;
        pager.onMouseEvent(mouse(MouseEventType.PRESS, nextX, 10.0f));
        pager.onMouseEvent(mouse(MouseEventType.RELEASE, nextX, 10.0f));

        assertEquals(1, pager.getCurrentPage());
        assertEquals(1, changed.get());
    }

    @Test
    void settingThePageProgrammaticallyDoesNotNotify() {
        Pagination pager = new Pagination();
        pager.setPageCount(4);
        AtomicInteger changed = new AtomicInteger(-1);
        pager.setOnPageChange(changed::set);

        pager.setCurrentPage(3);

        assertEquals(3, pager.getCurrentPage());
        assertEquals(-1, changed.get());
    }

    @Test
    void reportsItsAccessibleRoleAndName() {
        Pagination pager = new Pagination();
        pager.setPageCount(5);
        pager.setCurrentPage(2);

        assertEquals(AccessibleRole.PAGINATION, pager.getAccessibleRole());
        assertEquals("Page 3 of 5", pager.getAccessibleName());
    }

    @Test
    void aWidePageCountStillMeasuresFiniteWidth() {
        Pagination pager = new Pagination();
        pager.setPageCount(100);
        pager.setCurrentPage(50);

        float width = pager.getPreferredWidth();
        // Two arrows plus at most MAX_SLOTS page slots, each BUTTON_SIZE wide
        // with a GAP between them.
        float maxWidth = (Pagination.MAX_SLOTS + 2)
                * (Pagination.BUTTON_SIZE + Pagination.GAP);

        assertTrue(width > 0.0f);
        assertTrue(width <= maxWidth);
    }

    @Test
    void aThrowingChangeHandlerIsContained() {
        Pagination pager = new Pagination();
        pager.setPageCount(3);
        pager.setOnPageChange(page -> {
            throw new IllegalStateException("boom");
        });

        pager.onKeyEvent(key(GlyphKeys.RIGHT));

        assertEquals(1, pager.getCurrentPage());
    }

    @Test
    void isFocusableForKeyboardNavigation() {
        assertTrue(new Pagination().isFocusable());
        assertFalse(new Pagination().onMouseEvent(
                mouse(MouseEventType.MOVE, -50.0f, -50.0f)));
    }
}
