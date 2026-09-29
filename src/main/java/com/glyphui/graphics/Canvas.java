package com.glyphui.graphics;

import io.github.humbleui.skija.*;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;

/**
 * Canvas wrapper that provides drawing methods using Skija.
 */
public class Canvas {
    private io.github.humbleui.skija.Canvas canvas;
    private Surface surface;
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
     * Replaces the underlying native canvas and surface.
     * Used when the application recreates its surface (e.g. after a window
     * resize on the raster backend) so wrappers keep drawing to the new target.
     *
     * @param newCanvas the new Skija canvas
     * @param newSurface the new Skija surface owning the canvas
     */
    public void setNativeCanvas(io.github.humbleui.skija.Canvas newCanvas, Surface newSurface) {
        this.canvas = newCanvas;
        if (newSurface != null) {
            this.surface = newSurface;
        }
    }

    /**
     * Replaces the underlying native canvas, keeping the current surface reference.
     *
     * @param newCanvas the new Skija canvas
     */
    public void setNativeCanvas(io.github.humbleui.skija.Canvas newCanvas) {
        setNativeCanvas(newCanvas, null);
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
     * Flushes and submits the canvas drawing operations.
     */
    public void flush() {
        surface.flushAndSubmit();
    }
}
