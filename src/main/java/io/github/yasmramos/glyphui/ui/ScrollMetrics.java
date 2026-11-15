package io.github.yasmramos.glyphui.ui;

/**
 * Row metrics shared by the scrollers that map rows to pixels, so the list, the
 * table, the tree and the text area all answer "how many rows fit?" and "how far
 * can I scroll?" the same way.
 *
 * <p>A widget passes its viewport height and row height in pixels; the helper
 * floors to whole rows and never reports less than one, so a viewport shorter
 * than a single row still scrolls by a row rather than stalling. The row-based
 * widgets (list, table, tree) derive both their visible-row count and their
 * maximum first visible row from the same numbers, which keeps Page Up/Down and
 * the overlay scrollbar in agreement.</p>
 *
 * <p>This is package-private toolkit plumbing, not part of the frozen API.</p>
 */
final class ScrollMetrics {

    private ScrollMetrics() {
    }

    /**
     * Whole rows that fit in a viewport, at least one.
     *
     * @param viewportHeight the visible height in pixels
     * @param rowHeight      the height of one row in pixels
     * @param fallbackRows   the count to use when the viewport height is not
     *                       known yet (zero or negative), e.g. before the first
     *                       layout pass
     * @return the number of fully visible rows, never below one
     */
    static int visibleRows(float viewportHeight, float rowHeight, int fallbackRows) {
        if (viewportHeight <= 0.0f || rowHeight <= 0.0f) {
            return Math.max(1, fallbackRows);
        }
        return Math.max(1, (int) Math.floor(viewportHeight / rowHeight));
    }

    /**
     * The largest first-visible row for a list of {@code rowCount} rows, so the
     * last row lands at the bottom of the viewport instead of overscrolling.
     *
     * @param rowCount       the total number of rows
     * @param viewportHeight the visible height in pixels
     * @param rowHeight      the height of one row in pixels
     * @param fallbackRows   the visible-row fallback (see {@link #visibleRows})
     * @return the maximum first-visible row, never negative
     */
    static int maxFirstVisible(int rowCount, float viewportHeight, float rowHeight,
            int fallbackRows) {
        return Math.max(0, rowCount - visibleRows(viewportHeight, rowHeight, fallbackRows));
    }

    /**
     * Clamps a first-visible row into {@code [0, maxFirstVisible]}.
     *
     * @param firstVisible the requested first-visible row
     * @param rowCount     the total number of rows
     * @param viewportHeight the visible height in pixels
     * @param rowHeight    the height of one row in pixels
     * @param fallbackRows the visible-row fallback (see {@link #visibleRows})
     * @return the clamped first-visible row
     */
    static int clampFirstVisible(int firstVisible, int rowCount, float viewportHeight,
            float rowHeight, int fallbackRows) {
        int max = maxFirstVisible(rowCount, viewportHeight, rowHeight, fallbackRows);
        return Math.max(0, Math.min(firstVisible, max));
    }
}
