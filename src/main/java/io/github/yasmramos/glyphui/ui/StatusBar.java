package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

import java.util.ArrayList;
import java.util.List;

/**
 * A thin information strip along the bottom edge of a window, the classic
 * status bar.
 *
 * <p>It holds a left-to-right list of segments, each a piece of text that can
 * also carry an icon. The strip is read-only chrome: it never takes focus and
 * never consumes input, so it can sit under any content without interfering
 * with it.</p>
 *
 * <pre>{@code
 * StatusBar status = new StatusBar();
 * status.addSegment("Ready");
 * status.addSegment("Ln 1, Col 1");
 * status.setSegmentText(0, "Modified");
 * }</pre>
 */
public class StatusBar extends Component {

    /** Default height, logical units. */
    public static final float DEFAULT_HEIGHT = 24.0f;

    /** Horizontal padding at the strip edges, logical units. */
    public static final float PADDING_HORIZONTAL = 10.0f;

    /** Gap between two adjacent segments, logical units. */
    public static final float SEGMENT_GAP = 16.0f;

    /** One entry of the bar. */
    private static final class Segment {
        String text;
        int color;

        Segment(String text, int color) {
            this.text = text == null ? "" : text;
            this.color = color;
        }
    }

    private final List<Segment> segments = new ArrayList<>();

    private Integer textColorOverride;

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint textPaint;

    /**
     * Creates an empty status bar.
     */
    public StatusBar() {
        super();
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "statusbar";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.STATUS_BAR;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        StringBuilder sb = new StringBuilder();
        for (Segment segment : segments) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(segment.text);
        }
        return sb.toString();
    }

    // ---- Segments ----------------------------------------------------------

    /**
     * Appends a segment with the default text colour.
     *
     * @param text the segment text (null is treated as empty)
     * @return this bar, for chaining
     */
    public StatusBar addSegment(String text) {
        segments.add(new Segment(text, 0));
        invalidateLayout();
        return this;
    }

    /**
     * Appends a segment with an explicit colour.
     *
     * @param text  the segment text (null is treated as empty)
     * @param color the text colour (ARGB)
     * @return this bar, for chaining
     */
    public StatusBar addSegment(String text, int color) {
        segments.add(new Segment(text, color));
        invalidateLayout();
        return this;
    }

    /**
     * @return the number of segments
     */
    public int getSegmentCount() {
        return segments.size();
    }

    /**
     * Replaces the text of an existing segment.
     *
     * @param index the segment index
     * @param text  the new text (null is treated as empty)
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public void setSegmentText(int index, String text) {
        segments.get(index).text = text == null ? "" : text;
        invalidateLayout();
    }

    /**
     * @param index the segment index
     * @return the segment text
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public String getSegmentText(int index) {
        return segments.get(index).text;
    }

    /**
     * Removes every segment.
     */
    public void clear() {
        segments.clear();
        invalidateLayout();
    }

    /**
     * @return the text colour used when a segment does not override it
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride,
                getTheme().getForegroundColor());
    }

    /**
     * Overrides the default segment text colour. Pass null to follow the
     * theme.
     *
     * @param textColor the new colour (ARGB) or null
     */
    public void setTextColor(Integer textColor) {
        this.textColorOverride = textColor;
        invalidatePaint();
    }

    // ---- Geometry ----------------------------------------------------------

    @Override
    public float getPreferredHeight() {
        return sizeExplicitlySet ? height : DEFAULT_HEIGHT;
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font f = resolveFont(Theme.FontRole.BODY);
        float total = 2.0f * PADDING_HORIZONTAL;
        for (int i = 0; i < segments.size(); i++) {
            total += f.measureTextWidth(segments.get(i).text);
            if (i > 0) {
                total += SEGMENT_GAP;
            }
        }
        return total;
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
        bgPaint.setColor(getTheme().getControlNormalColor());
        canvas.drawRect(0, 0, width, height, bgPaint);

        borderPaint.setColor(getTheme().getBorderColor());
        canvas.drawRect(0, 0, width, 1.0f, borderPaint);

        Font f = resolveFont(Theme.FontRole.BODY);
        FontMetrics m = f.getMetrics();
        float baseline = (height - (m.getDescent() - m.getAscent())) / 2.0f - m.getAscent();
        float x = PADDING_HORIZONTAL;
        for (Segment segment : segments) {
            textPaint.setColor(segment.color == 0 ? getTextColor() : segment.color);
            canvas.drawString(segment.text, x, baseline, textPaint, f);
            x += f.measureTextWidth(segment.text) + SEGMENT_GAP;
        }
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
        // Chrome: the status bar never consumes input.
        return false;
    }

    @Override
    public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
        // Not focusable; hook exists to satisfy the Component contract.
    }

    @Override
    protected void onDispose() {
        close(bgPaint);
        bgPaint = null;
        close(borderPaint);
        borderPaint = null;
        close(textPaint);
        textPaint = null;
        super.onDispose();
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) {
            paint.close();
        }
    }
}
