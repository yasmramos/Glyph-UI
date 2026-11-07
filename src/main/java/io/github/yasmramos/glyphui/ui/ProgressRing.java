package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

/**
 * A circular progress indicator showing a completion fraction in the range
 * {@code [0, 1]}.
 *
 * <p>The ring is the compact sibling of {@link ProgressBar}: it fits where a
 * wide bar would not (a toolbar button, a card corner, a file row). Like the
 * bar it is read-only — it never takes focus and never consumes pointer or
 * key events — and applications drive it from their own work loop with
 * {@link #setValue(float)}.</p>
 *
 * <p>An <em>indeterminate</em> ring ({@link #setIndeterminate(boolean)}) hides
 * the fraction and sweeps a moving arc instead, for work of unknown duration.
 * The sweep is advanced by {@link #advanceAnimation(float)}; wire that to
 * {@code Application.setAnimationCallback} so the ring keeps spinning, which
 * keeps the clock out of the widget and the animation deterministic in
 * tests.</p>
 */
public class ProgressRing extends Component {

    /** Default diameter of the ring, logical units. */
    public static final float DEFAULT_SIZE = 36.0f;

    /** Thickness of the ring stroke, logical units. */
    public static final float STROKE_WIDTH = 4.0f;

    /** Sweep of the indeterminate arc, as a fraction of the full circle. */
    private static final float INDETERMINATE_SWEEP = 0.25f;

    /** Cycles per second of the indeterminate arc. */
    private static final float INDETERMINATE_SPEED = 0.8f;

    /** Number of line segments used to approximate an arc. */
    private static final int ARC_SEGMENTS = 48;

    private float value;
    private boolean indeterminate;
    private boolean showText;
    private float phase;

    private Property<Float> valueProperty;

    private Paint trackPaint;
    private Paint fillPaint;
    private Paint textPaint;

    /**
     * Creates an empty ring with no explicit bounds.
     */
    public ProgressRing() {
        super();
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "progressring";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PROGRESS_RING;
    }

    // ---- Value -------------------------------------------------------------

    /**
     * @return the completion fraction in {@code [0, 1]}
     */
    public float getValue() {
        return value;
    }

    /**
     * Returns the observable value property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the value property (never null after first access)
     */
    public Property<Float> valueProperty() {
        if (valueProperty == null) {
            valueProperty = new Property<>(propertyOwner(), value, newValue -> {
                this.value = clamp01(newValue);
                invalidatePaint();
            });
        }
        return valueProperty;
    }

    /**
     * Sets the completion fraction. Values outside {@code [0, 1]} are clamped.
     *
     * @param value the new fraction
     */
    public void setValue(float value) {
        valueProperty().set(value);
    }

    /**
     * @return true when the ring sweeps instead of showing a fraction
     */
    public boolean isIndeterminate() {
        return indeterminate;
    }

    /**
     * Chooses between determinate (fraction) and indeterminate (sweep) modes.
     *
     * @param indeterminate true to sweep
     */
    public void setIndeterminate(boolean indeterminate) {
        if (this.indeterminate != indeterminate) {
            this.indeterminate = indeterminate;
            invalidatePaint();
        }
    }

    /**
     * @return true when the percentage is painted in the middle of the ring
     */
    public boolean isShowText() {
        return showText;
    }

    /**
     * Shows or hides the percentage label in the middle of the ring. Ignored
     * while the ring is indeterminate.
     *
     * @param showText true to paint the label
     */
    public void setShowText(boolean showText) {
        if (this.showText != showText) {
            this.showText = showText;
            invalidatePaint();
        }
    }

    /**
     * Advances the indeterminate sweep by one frame. A no-op for a
     * determinate ring. Wire it to {@code Application.setAnimationCallback}.
     *
     * @param deltaSeconds seconds elapsed since the previous frame
     */
    public void advanceAnimation(float deltaSeconds) {
        if (!indeterminate || deltaSeconds <= 0.0f) {
            return;
        }
        phase = (phase + deltaSeconds * INDETERMINATE_SPEED) % 1.0f;
        invalidatePaint();
    }

    // ---- Geometry ----------------------------------------------------------

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
        return Dimension.of(
                Math.min(getPreferredWidth(), maxWidth),
                Math.min(getPreferredHeight(), maxHeight));
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        float cx = width / 2.0f;
        float cy = height / 2.0f;
        float radius = Math.max(0.0f, Math.min(cx, cy) - STROKE_WIDTH / 2.0f);

        trackPaint.setColor(getTrackColor());
        canvas.drawCircle(cx, cy, radius, trackPaint);

        float start = indeterminate ? phase : 0.0f;
        float sweep = indeterminate ? INDETERMINATE_SWEEP : clamp01(value);
        if (sweep > 0.0f) {
            fillPaint.setColor(getProgressColor());
            drawArc(canvas, cx, cy, radius, start, sweep);
        }

        if (showText && !indeterminate) {
            String label = Math.round(clamp01(value) * 100.0f) + "%";
            Font f = resolveFont(Theme.FontRole.BODY);
            FontMetrics m = f.getMetrics();
            float textWidth = f.measureTextWidth(label);
            textPaint.setColor(getTheme().getForegroundColor());
            float baseline = cy - (m.getAscent() + m.getDescent()) / 2.0f;
            canvas.drawString(label, cx - textWidth / 2.0f, baseline, textPaint, f);
        }
    }

    /**
     * Approximates an arc with short chords: the wrapper's Canvas exposes
     * lines, not Skia's {@code drawArc}, and a fine-grained chord chain is
     * indistinguishable at widget sizes while staying inside the frozen API.
     */
    private void drawArc(Canvas canvas, float cx, float cy, float radius,
            float startFraction, float sweepFraction) {
        float startAngle = -90.0f + startFraction * 360.0f;
        float sweepAngle = sweepFraction * 360.0f;
        int segments = Math.max(2, Math.round(ARC_SEGMENTS * sweepFraction));
        float prevX = 0.0f;
        float prevY = 0.0f;
        for (int i = 0; i <= segments; i++) {
            double angle = Math.toRadians(startAngle + sweepAngle * i / segments);
            float x = cx + (float) (Math.cos(angle) * radius);
            float y = cy + (float) (Math.sin(angle) * radius);
            if (i > 0) {
                canvas.drawLine(prevX, prevY, x, y, fillPaint);
            }
            prevX = x;
            prevY = y;
        }
    }

    /**
     * @return the ring track color (ARGB)
     */
    public int getTrackColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, null,
                getTheme().getControlNormalColor());
    }

    /**
     * @return the progress arc color (ARGB)
     */
    public int getProgressColor() {
        return resolveIntStyle(StyleProperty.ACCENT_COLOR, null,
                getTheme().getAccentColor());
    }

    private void ensurePaints() {
        if (trackPaint == null || trackPaint.isClosed()) {
            trackPaint = new Paint();
            trackPaint.setAntiAlias(true);
            trackPaint.setMode(PaintMode.STROKE);
            trackPaint.setStrokeWidth(STROKE_WIDTH);
        }
        if (fillPaint == null || fillPaint.isClosed()) {
            fillPaint = new Paint();
            fillPaint.setAntiAlias(true);
            fillPaint.setMode(PaintMode.STROKE);
            fillPaint.setStrokeWidth(STROKE_WIDTH);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
        // Read-only: the ring never consumes input.
        return false;
    }

    @Override
    public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
        // Not focusable; hook exists to satisfy the Component contract.
    }

    @Override
    protected void onDispose() {
        close(trackPaint);
        trackPaint = null;
        close(fillPaint);
        fillPaint = null;
        close(textPaint);
        textPaint = null;
        super.onDispose();
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) {
            paint.close();
        }
    }

    private static float clamp01(float v) {
        if (Float.isNaN(v)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, v));
    }
}
