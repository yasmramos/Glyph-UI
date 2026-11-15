package io.github.yasmramos.glyphui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Boundary tests for {@link ScrollMetrics}, the shared row arithmetic behind the
 * visible-row count and the maximum first-visible row of the row-based scrollers.
 */
class ScrollMetricsTest {

    @Test
    void floorsToWholeRows() {
        assertEquals(4, ScrollMetrics.visibleRows(100.0f, 24.0f, 6));
        assertEquals(1, ScrollMetrics.visibleRows(23.0f, 24.0f, 6));
    }

    @Test
    void alwaysReportsAtLeastOneRow() {
        assertEquals(1, ScrollMetrics.visibleRows(1.0f, 24.0f, 6));
        assertEquals(1, ScrollMetrics.visibleRows(0.0f, 24.0f, 0));
    }

    @Test
    void fallsBackBeforeTheFirstLayoutPass() {
        assertEquals(6, ScrollMetrics.visibleRows(0.0f, 24.0f, 6));
        assertEquals(6, ScrollMetrics.visibleRows(-10.0f, 24.0f, 6));
        assertEquals(6, ScrollMetrics.visibleRows(100.0f, 0.0f, 6));
    }

    @Test
    void maxFirstVisibleKeepsTheLastRowAtTheBottom() {
        // 10 rows, 4 visible: the last window starts at row 6.
        assertEquals(6, ScrollMetrics.maxFirstVisible(10, 100.0f, 24.0f, 6));
    }

    @Test
    void maxFirstVisibleIsZeroWhenEverythingFits() {
        assertEquals(0, ScrollMetrics.maxFirstVisible(3, 100.0f, 24.0f, 6));
        assertEquals(0, ScrollMetrics.maxFirstVisible(0, 100.0f, 24.0f, 6));
    }

    @Test
    void clampFirstVisiblePinsToBothEnds() {
        assertEquals(0, ScrollMetrics.clampFirstVisible(-5, 10, 100.0f, 24.0f, 6));
        assertEquals(6, ScrollMetrics.clampFirstVisible(99, 10, 100.0f, 24.0f, 6));
        assertEquals(3, ScrollMetrics.clampFirstVisible(3, 10, 100.0f, 24.0f, 6));
    }
}
