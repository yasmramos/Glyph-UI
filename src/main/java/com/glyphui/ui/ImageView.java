package com.glyphui.ui;

import com.glyphui.graphics.Canvas;
import com.glyphui.graphics.Dimension;
import com.glyphui.graphics.Image;
import com.glyphui.events.KeyEvent;
import com.glyphui.events.MouseEvent;

/**
 * Optional widget that displays an {@link Image}.
 *
 * <p>The image is referenced, not owned: keep it alive (typically via an
 * {@code ImageCache}) for the lifetime of the view. {@link #measure(float, float)}
 * derives its preferred size from the image's pixel dimensions, clamped to
 * the supplied constraints.</p>
 */
public class ImageView extends Component {

    @Override
    protected String defaultStyleTag() {
        return "img";
    }

    private Image image;

    /**
     * Creates an image view bound to an image.
     *
     * @param image the image to display (may be null / closed later)
     */
    public ImageView(Image image) {
        super();
        this.image = image;
        // Decorative by default: images do not take keyboard focus
        setFocusable(false);
    }

    /**
     * Creates an empty image view.
     */
    public ImageView() {
        this(null);
    }

    /**
     * Gets the displayed image.
     *
     * @return the image, or null
     */
    public Image getImage() {
        return image;
    }

    /**
     * Sets the displayed image.
     *
     * @param image the new image (not owned by this view)
     */
    public void setImage(Image image) {
        this.image = image;
        invalidate();
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.IMAGE;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        float w = (image != null && image.isLoaded()) ? image.getWidth() : 0.0f;
        float h = (image != null && image.isLoaded()) ? image.getHeight() : 0.0f;
        if (Float.isNaN(maxWidth) || maxWidth == Float.POSITIVE_INFINITY) {
            maxWidth = Float.MAX_VALUE;
        }
        if (Float.isNaN(maxHeight) || maxHeight == Float.POSITIVE_INFINITY) {
            maxHeight = Float.MAX_VALUE;
        }
        return new Dimension(Math.min(w, Math.max(0.0f, maxWidth)),
                Math.min(h, Math.max(0.0f, maxHeight)));
    }

    @Override
    public void render(Canvas canvas) {
        if (image != null && image.isLoaded()) {
            canvas.drawImage(image, x, y, width, height);
        }
    }

    @Override
    public void onMouseEvent(MouseEvent event) {
        // Images are non-interactive in v0.1
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Images are non-interactive in v0.1
    }
}
