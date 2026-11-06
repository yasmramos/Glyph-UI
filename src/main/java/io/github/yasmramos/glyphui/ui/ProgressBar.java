package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

/**
 * A horizontal progress indicator showing a completion fraction in the range
 * {@code [0, 1]}.
 *
 * <p>The bar is a read-only widget: it never takes focus and never consumes
 * pointer or key events. Applications drive it from their own work loop, for
 * example {@code bar.setValue(done / (float) total)}.</p>
 *
 * <p>An <em>indeterminate</em> bar ({@link #setIndeterminate(boolean)}) hides
 * the completion fraction and paints a moving segment instead, for work whose
 * duration is unknown. The segment position is advanced by
 * {@link #advanceAnimation(float)}: wire that method to
 * {@code Application.setAnimationCallback} so the bar keeps moving. Keeping
 * the clock out of the widget makes the animation deterministic in tests.</p>
 *
 * <p>Like every other widget the native paints are allocated lazily on the
 * first render, so construction and state changes are safe in headless unit
 * tests.</p>
 */
public class ProgressBar extends Component {

    /** Default height of the track, logical units. */
    public static final float DEFAULT_BAR_HEIGHT = 16.0f;

    /** Corner radius of the track and fill, logical units. */
    public static final float BAR_RADIUS = 3.0f;

    /** Width of the moving segment on an indeterminate bar, as a fraction. */
    private static final float INDETERMINATE_SEGMENT = 0.3f;

    /** Cycles per second of the indeterminate segment. */
    private static final float INDETERMINATE_SPEED = 0.6f;

    private float value;
    private boolean indeterminate;
    private boolean showText;
    private float phase;

    private Property<Float> valueProperty;

    private Paint trackPaint;
    private Paint fillPaint;
    private Paint textPaint;

    /**
     * Creates an empty progress bar with no explicit bounds.
     */
    public ProgressBar() {
        super();
        setFocusable(false);
    }

    /**
     * Creates a progress bar with explicit bounds.
     *
     * @param width  the width of the bar
     * @param height the height of the bar
     */
    public ProgressBar(float width, float height) {
        super(width, height);
        setFocusable(false);
    }

    /**
     * Creates a progress bar with explicit bounds.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the bar
     * @param height the height of the bar
     */
    public ProgressBar(float x, float y, float width, float height) {
        super(x, y, width, height);
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "progressbar";
    }

    /**
     * @return the completion fraction in {@code [0, 1]}
     */
    public float getValue() {
        return value;
    }

    /**
     * Returns the observable value property.
     *
     * @return the value property (never null after first access)
     */
    public Property<Float> valueProperty() {
        if (valueProperty == null) {
            valueProperty = new Property<>(propertyOwner(), value, newValue -> {
                this.value = clamp(newValue);
                invalidatePaint();
            });
        }
        return valueProperty;
    }

    /**
     * Sets the completion fraction. Values outside {@code [0, 1]} are clamped
     * and NaN is treated as zero.
     *
     * @param value the new completion fraction
     */
    public void setValue(float value) {
        valueProperty().set(clamp(value));
    }

    /**
     * @return true when the bar shows a moving segment instead of a fraction
     */
    public boolean isIndeterminate() {
        return indeterminate;
    }

    /**
     * Switches between determinate and indeterminate rendering.
     *
     * @param indeterminate true to hide the fraction and animate a segment
     */
    public void setIndeterminate(boolean indeterminate) {
        if (this.indeterminate != indeterminate) {
            this.indeterminate = indeterminate;
            invalidatePaint();
        }
    }

    /**
     * @return true when the percentage caption is painted over the bar
     */
    public boolean isShowText() {
        return showText;
    }

    /**
     * Shows or hides the percentage caption.
     *
     * @param showText true to paint the percentage over the bar
     */
    public void setShowText(boolean showText) {
        if (this.showText != showText) {
            this.showText = showText;
            invalidatePaint();
        }
    }

    /**
     * Advances the indeterminate animation clock.
     *
     * <p>The phase is kept in {@code [0, 1)}; the moving segment wraps around
     * as it passes the right edge. A no-op on a determinate bar so callers can
     * feed it unconditionally from an animation callback.</p>
     *
     * @param seconds elapsed time since the previous frame, in seconds
     */
    public void advanceAnimation(float seconds) {
        if (!indeterminate || !(seconds > 0.0f)) {
            return;
        }
        phase = (phase + seconds * INDETERMINATE_SPEED) % 1.0f;
        invalidatePaint();
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PROGRESS_BAR;
    }

    @Override
    public String getAccessibleName() {
        String override = accessibleNameOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        return "Progress bar";
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return DEFAULT_WIDTH;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return DEFAULT_BAR_HEIGHT;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        if (sizeExplicitlySet) {
            return super.measure(maxWidth, maxHeight);
        }
        return new Dimension(
                Math.min(getPreferredWidth(), sanitize(maxWidth)),
                Math.min(getPreferredHeight(), sanitize(maxHeight)));
    }

    private static float sanitize(float constraint) {
        if (Float.isNaN(constraint) || constraint == Float.POSITIVE_INFINITY) {
            return Float.MAX_VALUE;
        }
        return Math.max(0.0f, constraint);
    }

    private static float clamp(float v) {
        if (Float.isNaN(v)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private void ensurePaints() {
        if (trackPaint == null) {
            trackPaint = new Paint();
            trackPaint.setAntiAlias(true);
        }
        if (fillPaint == null) {
            fillPaint = new Paint();
            fillPaint.setAntiAlias(true);
        }
        if (textPaint == null) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();

        float radius = Math.min(BAR_RADIUS, height / 2.0f);

        trackPaint.setColor(getTheme().getControlDisabledColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, trackPaint);

        fillPaint.setColor(getTheme().getAccentColor());
        if (indeterminate) {
            float segW = width * INDETERMINATE_SEGMENT;
            if (segW > 0.0f && width > 0.0f) {
                // The segment slides from the left edge to fully past the
                // right edge, then wraps; clipping keeps it inside the track.
                float segX = phase * (width + segW) - segW;
                float clippedX = Math.max(0.0f, segX);
                float clippedW = Math.min(width, segX + segW) - clippedX;
                if (clippedW > 0.0f) {
                    canvas.drawRRect(clippedX, 0, clippedW, height,
                            radius, radius, fillPaint);
                }
            }
        } else {
            float fillW = width * value;
            if (fillW > 0.0f) {
                canvas.drawRRect(0, 0, fillW, height, radius, radius, fillPaint);
            }
        }

        if (showText && !indeterminate && height > 0.0f) {
            Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
            FontMetrics metrics = font.getMetrics();
            float line = metrics.getDescent() - metrics.getAscent();
            String caption = Math.round(value * 100.0f) + "%";
            float textWidth = font.measureTextWidth(caption);
            float tx = Math.max(0.0f, (width - textWidth) / 2.0f);
            float baseline = (height - line) / 2.0f - metrics.getAscent();
            textPaint.setColor(getTheme().getControlTextColor());
            canvas.drawString(caption, tx, baseline, textPaint, font);
        }
        // The focus ring is painted by Component.renderInternal() when
        // isFocusVisible() is true — a progress bar never takes focus.
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        // Read-only: never consumes pointer events.
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Read-only: never takes focus or handles keys.
    }

    @Override
    protected void onDispose() {
        close(trackPaint);
        trackPaint = null;
        close(fillPaint);
        fillPaint = null;
        close(textPaint);
        textPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
