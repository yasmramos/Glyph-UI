package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Color;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;

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
    private ObjectFit objectFit = ObjectFit.FILL;
    private int placeholderColor = Color.makeARGB(255, 128, 128, 128);

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
     * Gets how the image is scaled into the view bounds.
     *
     * @return the fit mode (never null)
     */
    public ObjectFit getObjectFit() {
        return objectFit;
    }

    /**
     * Sets how the image is scaled into the view bounds. The default is
     * {@link ObjectFit#FILL}, which stretches the image and may distort it;
     * {@link ObjectFit#CONTAIN} and {@link ObjectFit#COVER} preserve the
     * intrinsic aspect ratio.
     *
     * @param objectFit the fit mode (null keeps the current value)
     */
    public void setObjectFit(ObjectFit objectFit) {
        if (objectFit != null && this.objectFit != objectFit) {
            this.objectFit = objectFit;
            invalidate();
        }
    }

    /**
     * Gets the colour painted when no image is loaded (placeholder).
     *
     * @return an ARGB colour
     */
    public int getPlaceholderColor() {
        return placeholderColor;
    }

    /**
     * Sets the colour painted while the view has no loaded image, so empty or
     * still-loading views do not render as blank areas.
     *
     * @param argb the placeholder colour in ARGB format
     */
    public void setPlaceholderColor(int argb) {
        if (this.placeholderColor != argb) {
            this.placeholderColor = argb;
            invalidate();
        }
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
        // Image intrinsic size feeds measure(): ancestors must relayout.
        invalidateLayout();
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.IMAGE;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        // An explicit CSS width/height (or bounds set through the layout /
        // constructor path) must win over the image's intrinsic pixel size:
        // delegate to the base measurement path, which honours declared
        // sizes including percentages resolved against the parent.
        io.github.yasmramos.glyphui.style.Style s = getComputedStyle();
        boolean cssSized = s != null && (s.has(io.github.yasmramos.glyphui.style.StyleProperty.WIDTH)
                || s.has(io.github.yasmramos.glyphui.style.StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            return super.measure(maxWidth, maxHeight);
        }
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
    protected void render(Canvas canvas) {
        if (image == null || !image.isLoaded()) {
            // Placeholder: empty or still-loading views paint a solid block
            // instead of leaving a blank hole in the layout.
            io.github.humbleui.skija.Paint paint = scratchPaint();
            paint.setColor(placeholderColor);
            canvas.drawRect(0, 0, width, height, paint);
            return;
        }
        float iw = image.getWidth();
        float ih = image.getHeight();
        float dx = 0, dy = 0, dw = width, dh = height;
        if (objectFit != ObjectFit.FILL && iw > 0.0f && ih > 0.0f && width > 0.0f && height > 0.0f) {
            float scale = (objectFit == ObjectFit.CONTAIN)
                    ? Math.min(width / iw, height / ih)
                    : Math.max(width / iw, height / ih);
            dw = iw * scale;
            dh = ih * scale;
            dx = (width - dw) / 2.0f;
            dy = (height - dh) / 2.0f;
            if (objectFit == ObjectFit.COVER) {
                // Clip the overflow so COVER never paints outside the bounds.
                int saved = canvas.save();
                canvas.clipRect(0, 0, width, height);
                canvas.drawImage(image, dx, dy, dw, dh);
                canvas.restoreToCount(saved);
                return;
            }
        }
        canvas.drawImage(image, dx, dy, dw, dh);
    }

    /** Reusable paint for placeholder rendering (no per-frame allocation). */
    private io.github.humbleui.skija.Paint placeholderPaint;

    private io.github.humbleui.skija.Paint scratchPaint() {
        if (placeholderPaint == null) {
            placeholderPaint = new io.github.humbleui.skija.Paint();
        }
        return placeholderPaint;
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        // Images are non-interactive in v0.1
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Images are non-interactive in v0.1
    }
}
