package com.glyphui.graphics;

/**
 * An immutable 2D size in logical units.
 *
 * <p>This is Glyph UI's own dimension type so the toolkit does not depend on
 * AWT (which would force a headless-unsafe transitive dependency and mixes
 * integer-only fields with the float-based layout system).</p>
 */
public final class Dimension {
    /** Width component. */
    public final float width;

    /** Height component. */
    public final float height;

    /**
     * Creates a new dimension.
     *
     * @param width  the width (clamped to be non-negative)
     * @param height the height (clamped to be non-negative)
     */
    public Dimension(float width, float height) {
        this.width = Math.max(0.0f, width);
        this.height = Math.max(0.0f, height);
    }

    /**
     * Factory method for creating a dimension.
     *
     * @param width  the width
     * @param height the height
     * @return a new {@link Dimension}
     */
    public static Dimension of(float width, float height) {
        return new Dimension(width, height);
    }

    /**
     * Gets the width.
     *
     * @return the width
     */
    public float getWidth() {
        return width;
    }

    /**
     * Gets the height.
     *
     * @return the height
     */
    public float getHeight() {
        return height;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Dimension)) {
            return false;
        }
        Dimension d = (Dimension) other;
        return Float.compare(d.width, width) == 0 && Float.compare(d.height, height) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Float.hashCode(width) + Float.hashCode(height);
    }

    @Override
    public String toString() {
        return "Dimension(" + width + " x " + height + ")";
    }
}
