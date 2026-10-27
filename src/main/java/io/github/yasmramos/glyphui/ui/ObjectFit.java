package io.github.yasmramos.glyphui.ui;

/**
 * How an {@link ImageView} scales its content into the view bounds.
 *
 * <p>Mirrors the CSS {@code object-fit} property for the subset that makes
 * sense without cropping support in the current canvas backend.</p>
 */
public enum ObjectFit {

    /** Stretch to fill the bounds exactly, ignoring the intrinsic aspect ratio. */
    FILL,

    /** Scale uniformly so the whole image fits inside the bounds (letterboxed). */
    CONTAIN,

    /**
     * Scale uniformly so the bounds are fully covered; overflow is clipped by
     * the view rectangle before drawing.
     */
    COVER
}
