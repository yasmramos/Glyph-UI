package com.glyphui.graphics;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

/**
 * Canvas wrapper that provides drawing methods using Skija.
 */
public class Canvas {
    private final io.github.humbleui.skija.Canvas canvas;
    private final Surface surface;
    private int width;
    private int height;

    /**
     * Creates a new Canvas wrapper.
     *
     * @param canvas the Skija canvas
     * @param surface the Skija surface
     * @param width the canvas width
     * @param height the canvas height
     */
    public Canvas(io.github.humbleui.skija.Canvas canvas, Surface surface, int width, int height) {
        this.canvas = canvas;
        this.surface = surface;
        this.width = width;
        this.height = height;
    }

    /**
     * Gets the underlying Skija canvas.
     *
     * @return the Skija canvas
     */
    public io.github.humbleui.skija.Canvas getNativeCanvas() {
        return canvas;
    }

    /**
     * Gets the underlying Skija surface.
     *
     * @return the Skija surface
     */
    public Surface getSurface() {
        return surface;
    }

    /**
     * Updates the canvas dimensions.
     *
     * @param width the new width
     * @param height the new height
     */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Gets the canvas width.
     *
     * @return the width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Gets the canvas height.
     *
     * @return the height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Clears the canvas with the specified color.
     *
     * @param color the color to fill the canvas with (as ARGB int)
     */
    public void clear(int color) {
        canvas.clear(color);
    }

    /**
     * Saves the current canvas matrix/clip state. Pair with
     * {@link #restoreToCount(int)}.
     *
     * @return a save count to pass to {@link #restoreToCount(int)}
     */
    public int save() {
        return canvas.save();
    }

    /**
     * Restores the canvas state to the given save count.
     *
     * @param saveCount the count returned by {@link #save()}
     */
    public void restoreToCount(int saveCount) {
        canvas.restoreToCount(saveCount);
    }

    /**
     * Scales the canvas CTM. Used for HiDPI rendering: the surface covers
     * the physical framebuffer, so the logical-coordinate UI tree is drawn
     * under a {@code contentScale} transform.
     *
     * @param sx the horizontal scale factor
     * @param sy the vertical scale factor
     */
    public void scale(float sx, float sy) {
        canvas.scale(sx, sy);
    }

    /**
     * Translates the canvas CTM.
     *
     * @param dx the horizontal translation
     * @param dy the vertical translation
     */
    public void translate(float dx, float dy) {
        canvas.translate(dx, dy);
    }

    /**
     * Gets the total device matrix of the canvas (useful to verify that the
     * HiDPI content scale was applied: scaleX/scaleY will equal the content
     * scale after {@link #scale(float, float)}).
     *
     * @return the current canvas matrix as a 9-element array [a,b,c, d,e,f, g,h,i]
     */
    public float[] getMatrixArray() {
        io.github.humbleui.skija.Matrix33 m = canvas.getLocalToDeviceAsMatrix33();
        return m.getMat();
    }

    /**
     * Draws a rectangle.
     *
     * @param x      the x-coordinate of the top-left corner
     * @param y      the y-coordinate of the top-left corner
     * @param width  the width of the rectangle
     * @param height the height of the rectangle
     * @param paint  the paint to use for drawing
     */
    public void drawRect(float x, float y, float width, float height, Paint paint) {
        Rect rect = Rect.makeXYWH(x, y, width, height);
        canvas.drawRect(rect, paint);
    }

    /**
     * Draws a rounded rectangle.
     *
     * @param x          the x-coordinate of the top-left corner
     * @param y          the y-coordinate of the top-left corner
     * @param width      the width of the rectangle
     * @param height     the height of the rectangle
     * @param radiusX    the x-radius of the rounded corners
     * @param radiusY    the y-radius of the rounded corners
     * @param paint      the paint to use for drawing
     */
    public void drawRRect(float x, float y, float width, float height, float radiusX, float radiusY, Paint paint) {
        RRect rrect = RRect.makeXYWH(x, y, width, height, radiusX, radiusY);
        canvas.drawRRect(rrect, paint);
    }

    /**
     * Draws a circle.
     *
     * @param centerX the x-coordinate of the center
     * @param centerY the y-coordinate of the center
     * @param radius  the radius of the circle
     * @param paint   the paint to use for drawing
     */
    public void drawCircle(float centerX, float centerY, float radius, Paint paint) {
        canvas.drawCircle(centerX, centerY, radius, paint);
    }

    /**
     * Draws a line.
     *
     * @param x1    the x-coordinate of the start point
     * @param y1    the y-coordinate of the start point
     * @param x2    the x-coordinate of the end point
     * @param y2    the y-coordinate of the end point
     * @param paint the paint to use for drawing
     */
    public void drawLine(float x1, float y1, float x2, float y2, Paint paint) {
        canvas.drawLine(x1, y1, x2, y2, paint);
    }

    /**
     * Draws a string of text.
     *
     * @param text   the text to draw
     * @param x      the x-coordinate of the starting position
     * @param y      the y-coordinate of the starting position
     * @param paint  the paint to use for drawing
     * @param font   the font to use
     */
    public void drawString(String text, float x, float y, Paint paint, Font font) {
        canvas.drawString(text, x, y, font, paint);
    }

    /**
     * Measures the width of a text string.
     *
     * @param text the text to measure
     * @param font the font to use
     * @return the width of the text
     */
    public float measureText(String text, Font font) {
        return font.measureTextWidth(text);
    }

    /**
     * Gets the height of text for a given font.
     *
     * @param font the font to use
     * @return the text height
     */
    public float getTextHeight(Font font) {
        return font.getMetrics().getDescent() - font.getMetrics().getAscent();
    }

    /**
     * Draws a raw Skija image at the specified position (native pixels).
     *
     * @param image the image to draw (not closed by this call)
     * @param x     the x-coordinate
     * @param y     the y-coordinate
     */
    public void drawImage(io.github.humbleui.skija.Image image, float x, float y) {
        canvas.drawImage(image, x, y);
    }

    /**
     * Draws a Glyph UI {@link Image} wrapper at the specified position,
     * scaled to the given destination size.
     *
     * @param image the image wrapper (not consumed by this call)
     * @param x     the destination x-coordinate
     * @param y     the destination y-coordinate
     * @param w     the destination width
     * @param h     the destination height
     */
    public void drawImage(Image image, float x, float y, float w, float h) {
        if (image == null || !image.isLoaded()) {
            return;
        }
        io.github.humbleui.types.Rect dst = io.github.humbleui.types.Rect.makeXYWH(x, y, w, h);
        io.github.humbleui.types.Rect src = io.github.humbleui.types.Rect.makeXYWH(
                0, 0, image.getWidth(), image.getHeight());
        canvas.drawImageRect(image.getNativeImage(), src, dst, null, true);
    }

    /**
     * Flushes and submits the canvas drawing operations.
     */
    public void flush() {
        surface.flushAndSubmit();
    }
}
