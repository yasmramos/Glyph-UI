package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.humbleui.skija.Paint;

/**
 * The overlay scrollbar shared by the scrolling widgets, so the pane, the list,
 * the table and the tree all show the same bar.
 *
 * <p>It is a pure geometry and state machine over a pixel scroll model: the
 * caller passes the viewport height, the content height and the current scroll
 * offset, and the bar reports what the pointer did and where to paint. Widgets
 * that scroll by row map their window to that model
 * ({@code contentHeight = rows * rowHeight}, {@code scrollY = firstVisible * rowHeight}),
 * so the bar needs no knowledge of rows.</p>
 *
 * <p>The bar is drawn on top of the content, never taking layout space, and is
 * hidden when the content fits. This is not part of the frozen public API: it is
 * package-private toolkit plumbing.</p>
 */
final class OverlayScrollbar {

    /** Width of the bar, logical units. */
    static final float WIDTH = 8.0f;

    /** Inset between the bar and the viewport edges, logical units. */
    static final float MARGIN = 2.0f;

    /** Smallest thumb height, so the thumb stays grabbable, logical units. */
    static final float MIN_THUMB = 24.0f;

    private static final float RADIUS = WIDTH / 2.0f;

    /** What a press on the bar asked the widget to do. */
    enum Press {
        /** The point was not on the bar. */
        NONE,
        /** The press landed on the thumb; a drag is now in progress. */
        THUMB,
        /** The press landed on the track above the thumb. */
        TRACK_BEFORE,
        /** The press landed on the track below the thumb. */
        TRACK_AFTER
    }

    private boolean hover;
    private boolean dragging;
    private boolean pressed;
    private float grabOffset;

    private Paint trackPaint;
    private Paint thumbPaint;

    /**
     * @return true when the content overflows the viewport and the bar shows
     */
    boolean isVisible(float viewportHeight, float contentHeight) {
        return contentHeight > viewportHeight && trackHeight(viewportHeight) > 0.0f;
    }

    boolean isDragging() {
        return dragging;
    }

    /**
     * @return true when the point lies anywhere on the bar, track included
     */
    boolean isOverTrack(float x, float y, float viewportWidth, float viewportHeight,
            float viewportY, float contentHeight) {
        if (!isVisible(viewportHeight, contentHeight)) {
            return false;
        }
        float trackX = trackX(viewportWidth);
        float top = trackTop(viewportY);
        return x >= trackX && x < trackX + WIDTH
                && y >= top && y < top + trackHeight(viewportHeight);
    }

    /**
     * Classifies a press, and starts a thumb drag when it lands on the thumb.
     * A press on the track is left to the widget, which pages in its own units.
     *
     * @param maxScroll the largest scroll offset the widget accepts, so the
     *                  thumb reaches the bottom exactly at the end of the
     *                  content even when the last row is partially visible
     */
    Press press(float x, float y, float viewportWidth, float viewportHeight,
            float viewportY, float contentHeight, float scrollY, float maxScroll) {
        if (!isOverTrack(x, y, viewportWidth, viewportHeight, viewportY, contentHeight)) {
            return Press.NONE;
        }
        pressed = true;
        float thumbY = thumbY(viewportY, viewportHeight, contentHeight, scrollY, maxScroll);
        float thumbHeight = thumbHeight(viewportHeight, contentHeight);
        if (y >= thumbY && y < thumbY + thumbHeight) {
            dragging = true;
            grabOffset = y - thumbY;
            return Press.THUMB;
        }
        return y < thumbY ? Press.TRACK_BEFORE : Press.TRACK_AFTER;
    }

    /**
     * Maps a pointer position to a scroll offset while dragging.
     *
     * @return the new scroll offset, clamped to {@code [0, maxScroll]}, or
     *         {@link Float#NaN} when no drag is in progress
     */
    float drag(float y, float viewportHeight, float viewportY, float contentHeight,
            float maxScroll) {
        if (!dragging) {
            return Float.NaN;
        }
        float travel = travel(viewportHeight, contentHeight);
        if (travel <= 0.0f) {
            return 0.0f;
        }
        float top = trackTop(viewportY);
        float thumbY = Math.max(top, Math.min(y - grabOffset, top + travel));
        return (thumbY - top) / travel * Math.max(0.0f, maxScroll);
    }

    /**
     * Ends any press or drag.
     *
     * @return true when a press or drag was in progress
     */
    boolean release() {
        boolean active = pressed || dragging;
        pressed = false;
        dragging = false;
        return active;
    }

    /**
     * Records whether the pointer is on the bar, so it can assert itself on
     * hover the way a web scrollbar does.
     *
     * @return true when the hover state changed and a repaint is needed
     */
    boolean setHover(boolean over) {
        if (over != hover) {
            hover = over;
            return true;
        }
        return false;
    }

    /** Paints the track and the thumb over the content. */
    void render(Canvas canvas, float viewportWidth, float viewportHeight,
            float viewportY, float contentHeight, float scrollY, float maxScroll,
            Theme theme) {
        if (!isVisible(viewportHeight, contentHeight)) {
            return;
        }
        ensurePaints();
        float trackX = trackX(viewportWidth);
        float top = trackTop(viewportY);

        int trackAlpha = (hover || dragging) ? 0x40 : 0x1A;
        trackPaint.setColor(withAlpha(theme.getForegroundColor(), trackAlpha));
        canvas.drawRRect(trackX, top, WIDTH, trackHeight(viewportHeight),
                RADIUS, RADIUS, trackPaint);

        int thumbAlpha;
        if (dragging) {
            thumbAlpha = 0xC0;
        } else if (hover) {
            thumbAlpha = 0xA0;
        } else {
            thumbAlpha = 0x66;
        }
        thumbPaint.setColor(withAlpha(theme.getForegroundColor(), thumbAlpha));
        canvas.drawRRect(trackX,
                thumbY(viewportY, viewportHeight, contentHeight, scrollY, maxScroll),
                WIDTH, thumbHeight(viewportHeight, contentHeight),
                RADIUS, RADIUS, thumbPaint);
    }

    /** Releases the reusable paints. */
    void dispose() {
        if (trackPaint != null) {
            trackPaint.close();
            trackPaint = null;
        }
        if (thumbPaint != null) {
            thumbPaint.close();
            thumbPaint = null;
        }
    }

    private float trackX(float viewportWidth) {
        return viewportWidth - MARGIN - WIDTH;
    }

    private float trackTop(float viewportY) {
        return viewportY + MARGIN;
    }

    private float trackHeight(float viewportHeight) {
        return Math.max(0.0f, viewportHeight - 2.0f * MARGIN);
    }

    private float thumbHeight(float viewportHeight, float contentHeight) {
        float ratio = contentHeight > 0.0f ? viewportHeight / contentHeight : 1.0f;
        return Math.max(MIN_THUMB, trackHeight(viewportHeight) * ratio);
    }

    private float travel(float viewportHeight, float contentHeight) {
        return trackHeight(viewportHeight) - thumbHeight(viewportHeight, contentHeight);
    }

    private float thumbY(float viewportY, float viewportHeight, float contentHeight,
            float scrollY, float maxScroll) {
        float travel = travel(viewportHeight, contentHeight);
        if (travel <= 0.0f) {
            return trackTop(viewportY);
        }
        float fraction = maxScroll > 0.0f
                ? Math.min(1.0f, Math.max(0.0f, scrollY / maxScroll))
                : 0.0f;
        return trackTop(viewportY) + fraction * travel;
    }

    private void ensurePaints() {
        if (trackPaint == null || trackPaint.isClosed()) {
            trackPaint = new Paint();
            trackPaint.setAntiAlias(true);
        }
        if (thumbPaint == null || thumbPaint.isClosed()) {
            thumbPaint = new Paint();
            thumbPaint.setAntiAlias(true);
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }
}
