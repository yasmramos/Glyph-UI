package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Animatable;
import io.github.yasmramos.glyphui.core.Application;
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
 * hidden when the content fits. It asserts itself while the user is moving the
 * content — a wheel notch, a key, a track page, a drag — and fades back to its
 * resting tint a moment after the movement stops, the way a modern web page
 * does. The fade is driven through the {@link Animatable} seam: the widget
 * calls {@link #consumeScrollActivity()} after {@link #render} and registers
 * the bar with the application when it returns true; {@link #advance(float)}
 * then decays the highlight and {@link #isFinished()} stops the loop once the
 * bar is back at rest, so an idle widget paints nothing.</p>
 *
 * <p>This is not part of the frozen public API: it is package-private toolkit
 * plumbing.</p>
 */
final class OverlayScrollbar implements Animatable {

    /** Width of the bar, logical units. */
    static final float WIDTH = 8.0f;

    /** Inset between the bar and the viewport edges, logical units. */
    static final float MARGIN = 2.0f;

    /** Smallest thumb height, so the thumb stays grabbable, logical units. */
    static final float MIN_THUMB = 24.0f;

    /** How long the highlight lingers after the last movement, seconds. */
    static final float FADE_DELAY_SECONDS = 0.7f;

    /** How long the highlight takes to fade out once the delay elapses. */
    static final float FADE_DURATION_SECONDS = 0.25f;

    private static final float RADIUS = WIDTH / 2.0f;

    private static final int TRACK_REST = 0x1A;
    private static final int TRACK_ACTIVE = 0x40;
    private static final int THUMB_REST = 0x66;
    private static final int THUMB_ACTIVE = 0xB0;
    private static final int THUMB_HOVER = 0xA0;
    private static final int THUMB_DRAG = 0xC0;

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

    /**
     * True while the bar is highlighted because the content moved recently.
     * Cleared once the highlight has fully faded, so the bar settles back to
     * its resting tint.
     */
    private boolean active;
    /** Seconds of stillness accumulated since the last movement. */
    private float idleSeconds;
    /** True when activity happened but the widget has not registered us yet. */
    private boolean pendingRegistration;
    /** Last scroll offset seen by {@link #render}, to spot movement. */
    private float lastScrollY = Float.NaN;

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
            // The bar is gone from view: drop the highlight so a later
            // reappearance starts at rest.
            active = false;
            lastScrollY = Float.NaN;
            return;
        }
        // Movement since the previous paint highlights the bar. This catches
        // every source of scrolling (wheel, keys, track page, drag) without
        // each widget having to report it, because they all repaint when the
        // offset changes. The very first paint only records the offset.
        if (Float.isNaN(lastScrollY)) {
            lastScrollY = scrollY;
        } else if (scrollY != lastScrollY) {
            lastScrollY = scrollY;
            active = true;
            idleSeconds = 0.0f;
            pendingRegistration = true;
        }

        ensurePaints();
        float trackX = trackX(viewportWidth);
        float top = trackTop(viewportY);

        float highlight = highlightIntensity();
        int trackAlpha = (hover || dragging)
                ? TRACK_ACTIVE
                : blend(TRACK_REST, TRACK_ACTIVE, highlight);
        trackPaint.setColor(withAlpha(theme.getForegroundColor(), trackAlpha));
        canvas.drawRRect(trackX, top, WIDTH, trackHeight(viewportHeight),
                RADIUS, RADIUS, trackPaint);

        int thumbAlpha;
        if (dragging) {
            thumbAlpha = THUMB_DRAG;
        } else if (hover) {
            thumbAlpha = THUMB_HOVER;
        } else {
            thumbAlpha = blend(THUMB_REST, THUMB_ACTIVE, highlight);
        }
        thumbPaint.setColor(withAlpha(theme.getForegroundColor(), thumbAlpha));
        canvas.drawRRect(trackX,
                thumbY(viewportY, viewportHeight, contentHeight, scrollY, maxScroll),
                WIDTH, thumbHeight(viewportHeight, contentHeight),
                RADIUS, RADIUS, thumbPaint);
    }

    /**
     * Reports, once per activity burst, that the bar must be registered with
     * the application so the highlight can fade. The widget calls this right
     * after {@link #render} and, when it returns true, passes the bar to
     * {@link Application#addAnimatable(Animatable)}.
     *
     * @return true when the caller should register the bar as an animatable
     */
    boolean consumeScrollActivity() {
        if (pendingRegistration) {
            pendingRegistration = false;
            return true;
        }
        return false;
    }

    /**
     * Registers {@code bar} with the application owning {@code owner} when a
     * fresh activity burst was detected during its last {@link #render}. This
     * is the one-line hook each scrolling widget calls right after painting its
     * bar; it resolves the application through the component tree so a
     * detached or headless widget simply skips registration.
     *
     * @param bar   the bar that just painted
     * @param owner the widget that owns it
     */
    static void registerActivityWith(OverlayScrollbar bar, Component owner) {
        if (!bar.consumeScrollActivity()) {
            return;
        }
        AnimationSupport.start(owner, bar);
    }

    /**
     * @return true while the bar is highlighted by recent movement
     */
    boolean isActive() {
        return active;
    }

    /**
     * Advances the highlight fade by one frame.
     *
     * @param deltaSeconds seconds elapsed since the previous frame
     */
    @Override
    public void advance(float deltaSeconds) {
        if (!active || deltaSeconds <= 0.0f) {
            return;
        }
        idleSeconds += deltaSeconds;
        if (idleSeconds >= FADE_DELAY_SECONDS + FADE_DURATION_SECONDS) {
            active = false;
            idleSeconds = 0.0f;
        }
    }

    /**
     * @return true once the highlight has faded out, so the application can
     *         stop driving the bar and return to on-demand rendering
     */
    @Override
    public boolean isFinished() {
        return !active;
    }

    /** The highlight strength in {@code [0, 1]}: full, then a linear fade. */
    private float highlightIntensity() {
        if (!active) {
            return 0.0f;
        }
        if (idleSeconds <= FADE_DELAY_SECONDS) {
            return 1.0f;
        }
        float faded = (idleSeconds - FADE_DELAY_SECONDS) / FADE_DURATION_SECONDS;
        return Math.max(0.0f, 1.0f - faded);
    }

    /** Linear blend of two alpha bytes by {@code t} in {@code [0, 1]}. */
    private static int blend(int rest, int active, float t) {
        return Math.round(rest + (active - rest) * t);
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
