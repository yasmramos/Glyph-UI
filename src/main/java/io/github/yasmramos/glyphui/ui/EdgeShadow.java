package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Shader;

/**
 * The fade a scrolling surface paints over the edge where content continues,
 * the way a modern web page dims the row hidden under a sticky header or above
 * the footer.
 *
 * <p>The fade is a linear gradient from the shadow colour at the edge to fully
 * transparent a few pixels in, so it reads as depth rather than a hard line. It
 * is drawn on top of the content and never takes layout space.</p>
 *
 * <p>This is package-private toolkit plumbing, like {@link OverlayScrollbar}:
 * it is not part of the frozen public API, and the Skija {@code Paint}/{@code
 * Shader} it holds never leave this package. The caller only ever passes the
 * {@link Canvas} facade, the {@link Theme} and plain floats.</p>
 */
final class EdgeShadow {

    /** How far the fade reaches into the viewport, logical units. */
    static final float DEPTH = 8.0f;

    /** The edge a fade is painted on. */
    enum Edge {
        TOP,
        BOTTOM
    }

    private Paint paint;
    private Shader shader;
    private int shaderArgb;
    private float shaderTop;
    private Edge shaderEdge;

    /**
     * Paints a fade of {@link #DEPTH} pixels at {@code edge} of a viewport.
     *
     * @param canvas  the canvas, in the widget's local coordinate space
     * @param x       the viewport's left edge
     * @param y       the viewport's top edge
     * @param width   the viewport width in logical units
     * @param height  the viewport height in logical units
     * @param edge    the edge to fade
     * @param theme   the active theme (supplies the shadow colour)
     */
    void render(Canvas canvas, float x, float y, float width, float height, Edge edge,
            Theme theme) {
        if (width <= 0.0f || height <= 0.0f || height < DEPTH) {
            return;
        }
        float top = edge == Edge.TOP ? y : y + height - DEPTH;
        ensureShader(edge, top, theme.getShadowColor());
        canvas.drawRect(x, top, x + width, top + DEPTH, paint);
    }

    /**
     * Paints the vertical fades of a scrolled viewport: the top one only once
     * {@code offset} is past zero, the bottom one only while more content
     * remains below the fold. The two conditions are exactly the ones a web
     * scroller uses, so every scrolling widget can share this instead of
     * repeating them.
     *
     * @param canvas    the canvas, in the widget's local coordinate space
     * @param x         the viewport's left edge
     * @param y         the viewport's top edge
     * @param width     the viewport width in logical units
     * @param height    the viewport height in logical units
     * @param offset    the current scroll offset in pixels
     * @param maxOffset the largest scroll offset the widget accepts
     * @param theme     the active theme
     */
    void renderVertical(Canvas canvas, float x, float y, float width, float height,
            float offset, float maxOffset, Theme theme) {
        if (offset > 0.0f) {
            render(canvas, x, y, width, height, Edge.TOP, theme);
        }
        if (offset < maxOffset) {
            render(canvas, x, y, width, height, Edge.BOTTOM, theme);
        }
    }

    /** Releases the reusable paint and shader. */
    void dispose() {
        if (paint != null) {
            paint.close();
            paint = null;
        }
        if (shader != null) {
            shader.close();
            shader = null;
        }
    }

    private void ensureShader(Edge edge, float top, int shadowArgb) {
        if (shader != null && paint != null && !paint.isClosed()
                && edge == shaderEdge && top == shaderTop && shadowArgb == shaderArgb) {
            return;
        }
        if (shader != null) {
            shader.close();
        }
        // Fade from the shadow colour at the edge to transparent DEPTH pixels
        // in. The gradient is anchored to the strip itself (top..top+DEPTH) so
        // it stays aligned whichever edge is being painted.
        int transparent = shadowArgb & 0x00FFFFFF;
        int[] colors = edge == Edge.TOP
                ? new int[]{shadowArgb, transparent}
                : new int[]{transparent, shadowArgb};
        float[] positions = {0.0f, 1.0f};
        shader = Shader.makeLinearGradient(0.0f, top, 0.0f, top + DEPTH, colors, positions);
        shaderEdge = edge;
        shaderTop = top;
        shaderArgb = shadowArgb;
        ensurePaint();
        paint.setShader(shader);
    }

    private void ensurePaint() {
        if (paint == null || paint.isClosed()) {
            paint = new Paint();
            paint.setAntiAlias(true);
        }
    }
}
