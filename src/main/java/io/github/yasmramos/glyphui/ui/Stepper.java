package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphCursor;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A progress indicator for a multi-step flow.
 *
 * <p>A stepper paints a numbered marker and a label for each step, joined by
 * connectors. Steps before {@link #getCurrentStep() current} are completed
 * (accent fill with a check), the current one is highlighted, and the rest are
 * upcoming (muted). Clicking an earlier step, or pressing the arrow keys while
 * focused, moves the current step and fires the
 * {@link #setOnStepChange(IntConsumer) change handler}.</p>
 *
 * <p>By default a stepper only walks back to already-reached steps, the
 * conventional wizard rule; call {@link #setLinear(boolean) setLinear(false)}
 * to allow jumping forward. The current step is observable through
 * {@link #currentStepProperty()}.</p>
 */
public class Stepper extends Component {

    /** Logger for change-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Stepper.class.getName());

    /** Diameter of a step marker, logical units. */
    public static final float MARKER_SIZE = 24.0f;

    /** Horizontal gap between a marker and its label, logical units. */
    public static final float LABEL_GAP = 6.0f;

    /** Horizontal gap between two steps, logical units. */
    public static final float STEP_GAP = 16.0f;

    private final List<String> labels = new ArrayList<>();
    private int currentStep;
    private boolean linear = true;
    private IntConsumer onStepChange;
    private Property<Integer> currentStepProperty;

    private Integer activeColorOverride;
    private Integer inactiveColorOverride;

    private Paint activePaint;
    private Paint inactivePaint;
    private Paint labelPaint;
    private Paint connectorPaint;

    /**
     * Creates an empty stepper with no explicit bounds.
     */
    public Stepper() {
        super();
        setFocusable(true);
    }

    /**
     * Creates a stepper with the supplied step labels.
     *
     * @param labels the step labels (nulls are treated as empty)
     */
    public Stepper(String... labels) {
        this();
        if (labels != null) {
            for (String label : labels) {
                addStep(label);
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "stepper";
    }

    @Override
    protected GlyphCursor defaultCursor() {
        return GlyphCursor.POINTING_HAND;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.STEPPER;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        if (labels.isEmpty()) {
            return "Stepper";
        }
        return "Step " + (currentStep + 1) + " of " + labels.size()
                + ": " + labels.get(currentStep);
    }

    /**
     * Adds a step at the end.
     *
     * @param label the step label (null is treated as empty)
     */
    public void addStep(String label) {
        labels.add(label == null ? "" : label);
        invalidateLayout();
    }

    /**
     * @return the number of steps
     */
    public int getStepCount() {
        return labels.size();
    }

    /**
     * Gets a step label.
     *
     * @param index the step index
     * @return the label, never null
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public String getStepLabel(int index) {
        return labels.get(index);
    }

    /**
     * @return the zero-based index of the current step
     */
    public int getCurrentStep() {
        return currentStep;
    }

    /**
     * Returns the observable current-step property. Setting it from a
     * background thread marshals the change onto the UI thread automatically.
     *
     * @return the current-step property (never null after first access)
     */
    public Property<Integer> currentStepProperty() {
        if (currentStepProperty == null) {
            currentStepProperty = new Property<>(propertyOwner(), currentStep,
                    newIndex -> {
                        this.currentStep = clamp(newIndex == null ? 0 : newIndex);
                        invalidatePaint();
                    });
        }
        return currentStepProperty;
    }

    /**
     * Sets the current step. This does not run the change handler; the handler
     * only fires on an actual click or key press.
     *
     * @param index the new step, clamped into range
     */
    public void setCurrentStep(int index) {
        currentStepProperty().set(index);
    }

    /**
     * @return true when forward navigation is restricted to reached steps
     */
    public boolean isLinear() {
        return linear;
    }

    /**
     * Restricts forward navigation. When true (the default) a click or an
     * arrow key can only move to a step that has already been reached, so a
     * wizard cannot be skipped ahead.
     *
     * @param linear the new policy
     */
    public void setLinear(boolean linear) {
        this.linear = linear;
    }

    /**
     * Registers the change handler, run when the user moves the current step.
     *
     * @param onStepChange the handler, or null to clear it
     */
    public void setOnStepChange(IntConsumer onStepChange) {
        this.onStepChange = onStepChange;
    }

    /**
     * Gets the marker color of a completed or active step (CSS
     * {@code accent-color}, then the override, then the theme accent).
     *
     * @return the active color (ARGB)
     */
    public int getActiveColor() {
        return resolveIntStyle(StyleProperty.ACCENT_COLOR, activeColorOverride,
                getTheme().getAccentColor());
    }

    /**
     * Overrides the active color. Pass null to follow the theme accent.
     *
     * @param activeColor the new color (ARGB) or null
     */
    public void setActiveColor(Integer activeColor) {
        if (java.util.Objects.equals(this.activeColorOverride, activeColor)) {
            return;
        }
        this.activeColorOverride = activeColor;
        invalidatePaint();
    }

    /**
     * Gets the marker color of an upcoming step.
     *
     * @return the inactive color (ARGB)
     */
    public int getInactiveColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, inactiveColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the inactive color. Pass null to follow the theme border.
     *
     * @param inactiveColor the new color (ARGB) or null
     */
    public void setInactiveColor(Integer inactiveColor) {
        if (java.util.Objects.equals(this.inactiveColorOverride, inactiveColor)) {
            return;
        }
        this.inactiveColorOverride = inactiveColor;
        invalidatePaint();
    }

    private int clamp(int index) {
        if (labels.isEmpty()) {
            return 0;
        }
        return Math.max(0, Math.min(index, labels.size() - 1));
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    /** Width of the marker plus the gap and label of one step. */
    private float stepWidth(int index) {
        return MARKER_SIZE + LABEL_GAP + font().measureTextWidth(labels.get(index));
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        if (labels.isEmpty()) {
            return 0.0f;
        }
        float total = 0.0f;
        for (int i = 0; i < labels.size(); i++) {
            total += stepWidth(i);
            if (i < labels.size() - 1) {
                total += STEP_GAP;
            }
        }
        return total;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        FontMetrics metrics = font().getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return Math.max(MARKER_SIZE, line);
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSized = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            return super.measure(maxWidth, maxHeight);
        }
        float w = getPreferredWidth();
        float h = getPreferredHeight();
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
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();
        if (labels.isEmpty()) {
            return;
        }
        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float markerY = (height - MARKER_SIZE) / 2.0f;
        float baseline = (height - line) / 2.0f - metrics.getAscent();

        activePaint.setColor(enabled
                ? getActiveColor()
                : getTheme().getControlTextDisabledColor());
        inactivePaint.setColor(enabled
                ? getInactiveColor()
                : getTheme().getControlDisabledColor());

        float x = 0.0f;
        for (int i = 0; i < labels.size(); i++) {
            boolean reached = i <= currentStep;
            float markerCx = x + MARKER_SIZE / 2.0f;
            float markerCy = markerY + MARKER_SIZE / 2.0f;

            if (reached) {
                activePaint.setMode(PaintMode.FILL);
                canvas.drawCircle(markerCx, markerCy, MARKER_SIZE / 2.0f, activePaint);
                drawMarkerContent(canvas, f, i, markerCx, markerCy, baseline, reached);
            } else {
                inactivePaint.setMode(PaintMode.STROKE);
                inactivePaint.setStrokeWidth(1.0f);
                canvas.drawCircle(markerCx, markerCy, MARKER_SIZE / 2.0f, inactivePaint);
                drawMarkerContent(canvas, f, i, markerCx, markerCy, baseline, reached);
            }

            float labelX = x + MARKER_SIZE + LABEL_GAP;
            labelPaint.setColor(enabled
                    ? (reached ? getTheme().getForegroundColor()
                            : getTheme().getControlTextDisabledColor())
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(labels.get(i), labelX, baseline, labelPaint, f);

            x += stepWidth(i);

            if (i < labels.size() - 1) {
                float connectorY = markerY + MARKER_SIZE / 2.0f;
                connectorPaint.setColor(i < currentStep
                        ? getActiveColor() : getInactiveColor());
                // drawMarkerContent() thickens the paint for the tick; the
                // connector is always hairline, so restore the width here.
                connectorPaint.setStrokeWidth(1.0f);
                canvas.drawLine(markerCx + MARKER_SIZE / 2.0f + 2.0f, connectorY,
                        x + STEP_GAP - 2.0f, connectorY, connectorPaint);
                x += STEP_GAP;
            }
        }
    }

    /**
     * Draws a completed check or the step number inside a marker. A completed
     * step (strictly before the current one) shows a check; the current and
     * upcoming steps show their one-based number.
     */
    private void drawMarkerContent(Canvas canvas, Font f, int index,
            float cx, float cy, float baseline, boolean reached) {
        if (index < currentStep) {
            // Check mark: two strokes forming a tick, centred in the marker.
            connectorPaint.setColor(0xFFFFFFFF);
            connectorPaint.setStrokeWidth(2.0f);
            float arm = MARKER_SIZE * 0.22f;
            canvas.drawLine(cx - arm, cy, cx - arm * 0.2f, cy + arm,
                    connectorPaint);
            canvas.drawLine(cx - arm * 0.2f, cy + arm, cx + arm, cy - arm,
                    connectorPaint);
            return;
        }
        String number = String.valueOf(index + 1);
        float textWidth = f.measureTextWidth(number);
        labelPaint.setColor(reached ? 0xFFFFFFFF : getTheme().getForegroundColor());
        canvas.drawString(number, cx - textWidth / 2.0f, baseline, labelPaint, f);
    }

    private void ensurePaints() {
        if (activePaint == null || activePaint.isClosed()) {
            activePaint = new Paint();
            activePaint.setAntiAlias(true);
        }
        if (inactivePaint == null || inactivePaint.isClosed()) {
            inactivePaint = new Paint();
            inactivePaint.setAntiAlias(true);
        }
        if (labelPaint == null || labelPaint.isClosed()) {
            labelPaint = new Paint();
            labelPaint.setAntiAlias(true);
        }
        if (connectorPaint == null || connectorPaint.isClosed()) {
            connectorPaint = new Paint();
            connectorPaint.setAntiAlias(true);
            connectorPaint.setMode(PaintMode.STROKE);
            connectorPaint.setStrokeWidth(1.0f);
        }
    }

    /** Maps a local x-coordinate to the step index whose region contains it. */
    private int stepAtX(float localX) {
        float x = 0.0f;
        for (int i = 0; i < labels.size(); i++) {
            float width = stepWidth(i);
            if (localX >= x && localX < x + width) {
                return i;
            }
            x += width + STEP_GAP;
        }
        return -1;
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        boolean isInside = contains(event.getX(), event.getY());
        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE -> setState(isInside ? ComponentState.HOVER : ComponentState.IDLE);
            case PRESS -> {
                if (isInside && event.getButton() == MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                    requestFocusFromPointer();
                }
            }
            case RELEASE -> {
                if (isInside && state == ComponentState.PRESSED) {
                    apply(stepAtX(event.getX() - x), true);
                }
                setState(isInside ? ComponentState.HOVER : ComponentState.IDLE);
            }
            default -> {
            }
        }

        if (state != oldState) {
            requestRepaint();
        }
        return isInside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        switch (event.getKeyCode()) {
            case GlyphKeys.LEFT -> apply(currentStep - 1, true);
            case GlyphKeys.RIGHT -> apply(currentStep + 1, true);
            case GlyphKeys.HOME -> apply(0, true);
            case GlyphKeys.END -> apply(labels.size() - 1, true);
            default -> {
            }
        }
    }

    /** Applies a user-driven step, honouring the linear-navigation policy. */
    private void apply(int requested, boolean notify) {
        if (labels.isEmpty()) {
            return;
        }
        int target = clamp(requested);
        if (linear && target > currentStep) {
            return;
        }
        if (target == currentStep) {
            return;
        }
        currentStep = target;
        if (currentStepProperty != null) {
            currentStepProperty.set(currentStep);
        }
        invalidatePaint();
        if (notify && onStepChange != null) {
            try {
                onStepChange.accept(currentStep);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Stepper handler threw", e);
            }
        }
    }

    @Override
    protected void onDispose() {
        if (activePaint != null) {
            activePaint.close();
            activePaint = null;
        }
        if (inactivePaint != null) {
            inactivePaint.close();
            inactivePaint = null;
        }
        if (labelPaint != null) {
            labelPaint.close();
            labelPaint = null;
        }
        if (connectorPaint != null) {
            connectorPaint.close();
            connectorPaint = null;
        }
    }
}
