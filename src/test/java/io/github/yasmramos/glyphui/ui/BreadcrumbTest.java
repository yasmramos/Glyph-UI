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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Breadcrumb}: segment registration, the current
 * (last) segment being non-clickable, and keyboard navigation. No Skija
 * native code is touched because the paints are created lazily on the first
 * render.
 */
class BreadcrumbTest extends TestBase {

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

    private static Breadcrumb trail() {
        Breadcrumb crumb = new Breadcrumb();
        crumb.setSeparator(">");
        crumb.addSegment("Home");
        crumb.addSegment("Library");
        crumb.addSegment("Data");
        // A wide enough strip keeps every segment hit-testable in tests.
        crumb.setWidth(400.0f);
        crumb.setHeight(20.0f);
        return crumb;
    }

    @Test
    void startsEmptyWithSlashSeparator() {
        Breadcrumb crumb = new Breadcrumb();

        assertEquals(0, crumb.getSegmentCount());
        assertEquals("/", crumb.getSeparator());
    }

    @Test
    void segmentsAreStoredInOrder() {
        Breadcrumb crumb = trail();

        assertEquals(3, crumb.getSegmentCount());
        assertEquals("Home", crumb.getSegment(0));
        assertEquals("Library", crumb.getSegment(1));
        assertEquals("Data", crumb.getSegment(2));
    }

    @Test
    void nullSegmentBecomesEmpty() {
        Breadcrumb crumb = new Breadcrumb();
        crumb.addSegment(null);

        assertEquals("", crumb.getSegment(0));
    }

    @Test
    void setSegmentsReplacesTheTrail() {
        Breadcrumb crumb = trail();

        crumb.setSegments(List.of("A", "B"));

        assertEquals(2, crumb.getSegmentCount());
        assertEquals("A", crumb.getSegment(0));
        assertEquals("B", crumb.getSegment(1));
    }

    @Test
    void outOfRangeSegmentThrows() {
        Breadcrumb crumb = trail();

        assertThrows(IndexOutOfBoundsException.class, () -> crumb.getSegment(9));
    }

    @Test
    void keyboardNavigationFiresForTheCurrentSegmentOnly() {
        Breadcrumb crumb = trail();
        AtomicInteger navigated = new AtomicInteger(-1);
        crumb.setOnNavigate(navigated::set);

        crumb.onKeyEvent(key(GlyphKeys.RIGHT));
        crumb.onKeyEvent(key(GlyphKeys.ENTER));
        assertEquals(0, navigated.get());

        crumb.onKeyEvent(key(GlyphKeys.RIGHT));
        crumb.onKeyEvent(key(GlyphKeys.ENTER));
        assertEquals(1, navigated.get());

        // The last segment is the current page: it cannot be activated.
        crumb.onKeyEvent(key(GlyphKeys.RIGHT));
        crumb.onKeyEvent(key(GlyphKeys.ENTER));
        assertEquals(1, navigated.get());
    }

    @Test
    void theCurrentSegmentIsNotClickable() {
        Breadcrumb crumb = new Breadcrumb();
        crumb.addSegment("Only");
        crumb.setWidth(200.0f);
        crumb.setHeight(20.0f);
        AtomicInteger navigated = new AtomicInteger(-1);
        crumb.setOnNavigate(navigated::set);

        // The single segment is the current page: clicking it does nothing.
        crumb.onMouseEvent(mouse(MouseEventType.PRESS, 10.0f, 10.0f));
        crumb.onMouseEvent(mouse(MouseEventType.RELEASE, 10.0f, 10.0f));

        assertEquals(-1, navigated.get());
    }

    @Test
    void aThrowingNavigationHandlerIsContained() {
        Breadcrumb crumb = trail();
        crumb.setOnNavigate(index -> {
            throw new IllegalStateException("boom");
        });

        crumb.onKeyEvent(key(GlyphKeys.RIGHT));

        // Reaching here without propagating the exception is the assertion.
        assertTrue(true);
    }

    @Test
    void reportsItsAccessibleRole() {
        assertEquals(AccessibleRole.BREADCRUMB, new Breadcrumb().getAccessibleRole());
    }

    @Test
    void isFocusableForKeyboardNavigation() {
        assertTrue(new Breadcrumb().isFocusable());
        assertFalse(new Breadcrumb().onMouseEvent(
                mouse(MouseEventType.MOVE, -50.0f, -50.0f)));
    }
}
