package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A single-row group of mutually exclusive options, the classic segmented
 * button.
 *
 * <p>It reads as one control: adjacent segments share borders, the selected
 * one is filled with the accent colour, and the whole strip behaves like a
 * radio group. The selected index is observable through
 * {@link #selectedIndexProperty()}:</p>
 *
 * <pre>{@code
 * SegmentedControl view = new SegmentedControl("Day", "Week", "Month");
 * view.setOnChange(index -> reload(index));
 * }</pre>
 *
 * <p>Keyboard support mirrors {@link Tabs}: Left/Right move the selection,
 * Home/End jump to the ends, and the focused segment carries the ring.</p>
 */
public class SegmentedControl extends Component {

    /** Height of the strip, logical units. */
    public static final float DEFAULT_HEIGHT = 28.0f;

    /** Horizontal padding inside a segment, logical units. */
    public static final float SEGMENT_PADDING = 12.0f;

    /** Corner radius of the strip ends, logical units. */
    public static final float CORNER_RADIUS = 6.0f;

    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;

    private final List<String> labels = new ArrayList<>();
    private int selectedIndex = -1;
    private int hoveredIndex = -1;

    private Consumer<Integer> onChange;
    private Property<Integer> selectedIndexProperty;

    private Paint bgPaint;
    private Paint selectedPaint;
    private Paint borderPaint;
    private Paint textPaint;
    private Paint selectedTextPaint;

    /**
     * Creates an empty segmented control.
     */
    public SegmentedControl() {
        super();
    }

    /**
     * Creates a segmented control with the supplied labels, selecting the
     * first one.
     *
     * @param labels the segment labels, in order
     */
    public SegmentedControl(String... labels) {
        this();
        if (labels != null) {
            for (String label : labels) {
                addSegment(label);
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "segmentedcontrol";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SEGMENTED_CONTROL;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return selectedIndex >= 0 ? labels.get(selectedIndex) : "Segmented control";
    }

    // ---- Content -----------------------------------------------------------

    /**
     * Appends a segment. The first segment added becomes selected.
     *
     * @param label the segment caption (null is treated as empty)
     * @return this control, for chaining
     */
    public SegmentedControl addSegment(String label) {
        labels.add(label == null ? "" : label);
        if (selectedIndex < 0) {
            selectedIndex = 0;
        }
        invalidateLayout();
        return this;
    }

    /**
     * @return the number of segments
     */
    public int getSegmentCount() {
        return labels.size();
    }

    /**
     * @param index the segment index
     * @return the segment caption
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public String getSegmentLabel(int index) {
        return labels.get(index);
    }

    /**
     * @return the labels, in order (unmodifiable)
     */
    public List<String> getLabels() {
        return Collections.unmodifiableList(labels);
    }

    // ---- Selection ---------------------------------------------------------

    /**
     * @return the selected index, or {@code -1} when there are no segments
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Returns the observable selected-index property. Setting it from a
     * background thread marshals the change onto the UI thread automatically.
     *
     * @return the selected-index property (never null after first access)
     */
    public Property<Integer> selectedIndexProperty() {
        if (selectedIndexProperty == null) {
            selectedIndexProperty = new Property<>(propertyOwner(), selectedIndex,
                    newIndex -> applySelection(newIndex, true));
        }
        return selectedIndexProperty;
    }

    /**
     * Selects a segment. Out-of-range indexes and the current selection are
     * ignored, so this is safe to call with the value just read back.
     *
     * @param index the segment to select
     */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= labels.size() || index == selectedIndex) {
            return;
        }
        applySelection(index, true);
    }

    private void applySelection(int index, boolean notify) {
        if (index < 0 || index >= labels.size() || index == selectedIndex) {
            return;
        }
        selectedIndex = index;
        invalidatePaint();
        if (notify && onChange != null) {
            onChange.accept(index);
        }
    }

    /**
     * Registers the selection-change handler.
     *
     * @param onChange the handler, or null to clear it
     */
    public void setOnChange(Consumer<Integer> onChange) {
        this.onChange = onChange;
    }

    /**
     * @return the selection-change handler, or null when none is set
     */
    public Consumer<Integer> getOnChange() {
        return onChange;
    }

    // ---- Layout ------------------------------------------------------------

    /**
     * Measures every segment against the widest label so the strip keeps a
     * uniform look instead of resizing as the selection moves.
     */
    private float[] segmentWidths(Font f) {
        float[] widths = new float[labels.size()];
        for (int i = 0; i < labels.size(); i++) {
            widths[i] = f.measureTextWidth(labels.get(i)) + 2.0f * SEGMENT_PADDING;
        }
        return widths;
    }

    @Override
    public float getPreferredWidth() {
        if (labels.isEmpty()) {
            return 0.0f;
        }
        float[] widths = segmentWidths(resolveFont(Theme.FontRole.BODY));
        float total = 0.0f;
        for (float w : widths) {
            total += w;
        }
        return total;
    }

    @Override
    public float getPreferredHeight() {
        return sizeExplicitlySet ? height : DEFAULT_HEIGHT;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        return Dimension.of(
                Math.min(getPreferredWidth(), maxWidth),
                Math.min(getPreferredHeight(), maxHeight));
    }

    private int segmentAt(float localX) {
        if (labels.isEmpty()) {
            return -1;
        }
        float[] widths = segmentWidths(resolveFont(Theme.FontRole.BODY));
        float x = 0.0f;
        for (int i = 0; i < widths.length; i++) {
            if (localX >= x && localX < x + widths[i]) {
                return i;
            }
            x += widths[i];
        }
        return -1;
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        Font f = resolveFont(Theme.FontRole.BODY);
        float[] widths = segmentWidths(f);
        int radius = Math.round(CORNER_RADIUS);

        bgPaint.setColor(getBackgroundColor());
        canvas.drawRRect(0, 0, width, height, radius, radius, bgPaint);

        if (selectedIndex >= 0 && selectedIndex < widths.length) {
            float x = 0.0f;
            for (int i = 0; i < selectedIndex; i++) {
                x += widths[i];
            }
            selectedPaint.setColor(getAccentColor());
            canvas.drawRect(x, 0, x + widths[selectedIndex], height, selectedPaint);
        }

        FontMetrics m = f.getMetrics();
        float baseline = (height - (m.getDescent() - m.getAscent())) / 2.0f - m.getAscent();
        float x = 0.0f;
        for (int i = 0; i < labels.size(); i++) {
            String label = labels.get(i);
            float textWidth = f.measureTextWidth(label);
            float textX = x + (widths[i] - textWidth) / 2.0f;
            if (i == selectedIndex) {
                selectedTextPaint.setColor(0xFFFFFFFF);
                canvas.drawString(label, textX, baseline, selectedTextPaint, f);
            } else {
                textPaint.setColor(i == hoveredIndex
                        ? getTheme().getAccentColor() : getTheme().getForegroundColor());
                canvas.drawString(label, textX, baseline, textPaint, f);
            }
            x += widths[i];
        }

        // Segment dividers, skipped for the selected cell so the accent block
        // stays a single uninterrupted shape.
        borderPaint.setColor(getTheme().getBorderColor());
        float dividerX = 0.0f;
        for (int i = 0; i < widths.length - 1; i++) {
            dividerX += widths[i];
            if (i != selectedIndex && i + 1 != selectedIndex) {
                canvas.drawRect(dividerX, 2.0f, dividerX + 1.0f, height - 2.0f, borderPaint);
            }
        }
        canvas.drawRRect(0.5f, 0.5f, width - 1.0f, height - 1.0f, radius, radius, borderPaint);
    }

    /**
     * @return the strip fill (ARGB)
     */
    public int getBackgroundColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, null,
                getTheme().getControlNormalColor());
    }

    /**
     * @return the selected-segment fill (ARGB)
     */
    public int getAccentColor() {
        return resolveIntStyle(StyleProperty.ACCENT_COLOR, null, getTheme().getAccentColor());
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (selectedPaint == null || selectedPaint.isClosed()) {
            selectedPaint = new Paint();
            selectedPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(io.github.humbleui.skija.PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (selectedTextPaint == null || selectedTextPaint.isClosed()) {
            selectedTextPaint = new Paint();
            selectedTextPaint.setAntiAlias(true);
        }
    }

    // ---- Input -------------------------------------------------------------

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        boolean inside = contains(event.getX(), event.getY());
        int index = inside ? segmentAt(event.getX() - x) : -1;

        switch (event.getType()) {
            case MOVE:
                if (hoveredIndex != index) {
                    hoveredIndex = index;
                    requestRepaint();
                }
                break;
            case PRESS:
                if (index >= 0) {
                    requestFocusFromPointer();
                }
                break;
            case RELEASE:
                if (index >= 0 && event.getButton() == io.github.yasmramos.glyphui.events.MouseButton.LEFT) {
                    setSelectedIndex(index);
                }
                break;
            default:
                break;
        }
        return inside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!enabled || event.getType() != KeyEventType.PRESS || labels.isEmpty()) {
            return;
        }
        int key = event.getKeyCode();
        if (key == KEY_LEFT) {
            selectRelative(-1);
        } else if (key == KEY_RIGHT) {
            selectRelative(1);
        } else if (key == KEY_HOME) {
            setSelectedIndex(0);
        } else if (key == KEY_END) {
            setSelectedIndex(labels.size() - 1);
        }
    }

    private void selectRelative(int delta) {
        if (labels.isEmpty()) {
            return;
        }
        int from = selectedIndex < 0 ? 0 : selectedIndex;
        int next = Math.floorMod(from + delta, labels.size());
        setSelectedIndex(next);
    }

    @Override
    protected void onDispose() {
        close(bgPaint);
        bgPaint = null;
        close(selectedPaint);
        selectedPaint = null;
        close(borderPaint);
        borderPaint = null;
        close(textPaint);
        textPaint = null;
        close(selectedTextPaint);
        selectedTextPaint = null;
        super.onDispose();
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) {
            paint.close();
        }
    }
}
