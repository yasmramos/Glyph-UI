package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.ClipMode;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.types.RRect;

/**
 * A circular avatar showing a person's initials or a profile image.
 *
 * <p>An avatar is a fixed, square widget painted as a circle. When an image is
 * set it is drawn clipped to the circle; otherwise the avatar falls back to
 * its initials on a colored disc. The disc color is derived from the initials
 * so the same person keeps a stable color, and can be overridden explicitly.
 * The initials can be driven from a background thread through
 * {@link #initialsProperty()}.</p>
 *
 * <p>Avatars are decorative: they never take focus and never consume pointer
 * or key events.</p>
 */
public class Avatar extends Component {

    /** Default diameter, logical units. */
    public static final float DEFAULT_SIZE = 40.0f;

    private String initials;
    private Image image;
    private Integer backgroundColorOverride;
    private Integer textColorOverride;
    private Property<String> initialsProperty;

    private Paint bgPaint;
    private Paint textPaint;

    /**
     * Creates an empty avatar at the default size.
     */
    public Avatar() {
        this("");
    }

    /**
     * Creates an avatar showing the supplied initials at the default size.
     *
     * @param initials the initials (null is treated as empty)
     */
    public Avatar(String initials) {
        super();
        this.initials = initials == null ? "" : initials;
        setWidth(DEFAULT_SIZE);
        setHeight(DEFAULT_SIZE);
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "avatar";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.AVATAR;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null ? accessibleNameOverride() : initials;
    }

    /**
     * Gets the initials shown when no image is set.
     *
     * @return the initials, never null
     */
    public String getInitials() {
        return initials;
    }

    /**
     * Returns the observable initials property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the initials property (never null after first access)
     */
    public Property<String> initialsProperty() {
        if (initialsProperty == null) {
            initialsProperty = new Property<>(propertyOwner(), initials, newInitials -> {
                this.initials = newInitials == null ? "" : newInitials;
                invalidatePaint();
            });
        }
        return initialsProperty;
    }

    /**
     * Sets the initials shown when no image is set.
     *
     * @param initials the new initials (null is treated as empty)
     */
    public void setInitials(String initials) {
        initialsProperty().set(initials);
    }

    /**
     * Gets the profile image, or null when the avatar shows initials.
     *
     * @return the image or null
     */
    public Image getImage() {
        return image;
    }

    /**
     * Sets the profile image. Pass null to fall back to the initials.
     *
     * @param image the new image or null
     */
    public void setImage(Image image) {
        if (this.image == image) {
            return;
        }
        this.image = image;
        invalidatePaint();
    }

    /**
     * Gets the disc color. Resolution order: a CSS {@code background}
     * declaration → a per-instance override → a stable color derived from the
     * initials.
     *
     * @return the disc color (ARGB)
     */
    public int getDiscColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, backgroundColorOverride,
                derivedColor());
    }

    /**
     * Overrides the disc color. Pass null to fall back to the derived color.
     *
     * @param discColor the new disc color (ARGB) or null
     */
    public void setDiscColor(Integer discColor) {
        if (java.util.Objects.equals(this.backgroundColorOverride, discColor)) {
            return;
        }
        this.backgroundColorOverride = discColor;
        invalidatePaint();
    }

    /**
     * Gets the initials color. Resolution order: a CSS {@code color}
     * declaration → a per-instance override → white, which stays legible on
     * the saturated derived discs.
     *
     * @return the initials color (ARGB)
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride, 0xFFFFFFFF);
    }

    /**
     * Overrides the initials color. Pass null to follow the theme.
     *
     * @param textColor the new initials color (ARGB) or null
     */
    public void setTextColor(Integer textColor) {
        if (java.util.Objects.equals(this.textColorOverride, textColor)) {
            return;
        }
        this.textColorOverride = textColor;
        invalidatePaint();
    }

    /**
     * A stable, saturated color derived from the initials, so the same person
     * keeps the same disc color across runs and screens.
     */
    private int derivedColor() {
        if (initials.isEmpty()) {
            return getTheme().getControlNormalColor();
        }
        int hash = 0;
        for (int i = 0; i < initials.length(); i++) {
            hash = 31 * hash + Character.toLowerCase(initials.charAt(i));
        }
        float hue = Math.floorMod(hash, 360);
        return hsvToArgb(hue, 0.55f, 0.75f);
    }

    /** Converts an HSV triple (h in degrees, s and v in [0, 1]) to ARGB. */
    private static int hsvToArgb(float h, float s, float v) {
        float c = v * s;
        float x = c * (1 - Math.abs((h / 60.0f) % 2 - 1));
        float m = v - c;
        float r;
        float g;
        float b;
        if (h < 60) {
            r = c; g = x; b = 0;
        } else if (h < 120) {
            r = x; g = c; b = 0;
        } else if (h < 180) {
            r = 0; g = c; b = x;
        } else if (h < 240) {
            r = 0; g = x; b = c;
        } else if (h < 300) {
            r = x; g = 0; b = c;
        } else {
            r = c; g = 0; b = x;
        }
        int ri = Math.round((r + m) * 255);
        int gi = Math.round((g + m) * 255);
        int bi = Math.round((b + m) * 255);
        return 0xFF000000 | (ri << 16) | (gi << 8) | bi;
    }

    @Override
    public float getPreferredWidth() {
        return sizeExplicitlySet ? width : DEFAULT_SIZE;
    }

    @Override
    public float getPreferredHeight() {
        return sizeExplicitlySet ? height : DEFAULT_SIZE;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSized = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            return super.measure(maxWidth, maxHeight);
        }
        return new Dimension(DEFAULT_SIZE, DEFAULT_SIZE);
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();

        float diameter = Math.min(width, height);
        float radius = diameter / 2.0f;

        if (image != null && image.isLoaded()) {
            // Clip to the disc, then cover it: scale the image so it fills the
            // circle and centre the overflow.
            io.github.humbleui.skija.Canvas nativeCanvas = canvas.nativeCanvasForInternalUse();
            int saveCount = nativeCanvas.save();
            try {
                nativeCanvas.clipRRect(RRect.makeXYWH(0, 0, diameter, diameter, radius),
                        ClipMode.INTERSECT, true);
                float iw = image.getWidth();
                float ih = image.getHeight();
                if (iw > 0 && ih > 0) {
                    float scale = Math.max(diameter / iw, diameter / ih);
                    float dw = iw * scale;
                    float dh = ih * scale;
                    canvas.drawImage(image, (diameter - dw) / 2.0f,
                            (diameter - dh) / 2.0f, dw, dh);
                }
            } finally {
                nativeCanvas.restoreToCount(saveCount);
            }
            return;
        }

        bgPaint.setColor(getDiscColor());
        canvas.drawCircle(radius, radius, radius, bgPaint);

        if (!initials.isEmpty()) {
            Font f = resolveFont(Theme.FontRole.BUTTON);
            FontMetrics metrics = f.getMetrics();
            float line = metrics.getDescent() - metrics.getAscent();
            float baseline = (diameter - line) / 2.0f - metrics.getAscent();
            float textWidth = canvas.measureText(initials, f);
            textPaint.setColor(getTextColor());
            canvas.drawString(initials, (diameter - textWidth) / 2.0f, baseline,
                    textPaint, f);
        }
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        // Decorative: never consumes pointer events.
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Decorative: avatars never take focus or handle keys.
    }

    @Override
    protected void onDispose() {
        if (bgPaint != null) {
            bgPaint.close();
            bgPaint = null;
        }
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
    }
}
