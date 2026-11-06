package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A horizontal navigation trail of clickable segments.
 *
 * <p>Each segment is a link to a level of the hierarchy, separated by a
 * configurable glyph (a slash by default). The last segment is the current
 * page and is not clickable; activating any earlier segment fires the
 * {@link #setOnNavigate(IntConsumer) navigation handler} with that segment's
 * index. Segments can be added programmatically with
 * {@link #addSegment(String)}.</p>
 *
 * <p>Colors resolve like every other widget: a CSS declaration wins, then the
 * theme. Clickable segments use the accent color on hover, the current
 * (last) segment uses the theme foreground, and the separators are muted.</p>
 */
public class Breadcrumb extends Component {

    /** Logger for navigation-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Breadcrumb.class.getName());

    /** Horizontal gap between a segment and its separator, logical units. */
    public static final float SEPARATOR_GAP = 6.0f;

    /** Vertical inner padding, logical units. */
    public static final float PADDING_VERTICAL = 4.0f;

    private final List<String> segments = new ArrayList<>();
    private String separator = "/";
    private IntConsumer onNavigate;
    private int hoveredIndex = -1;
    private int focusedIndex = -1;

    private Paint textPaint;
    private Paint separatorPaint;

    /**
     * Creates an empty breadcrumb with no explicit bounds.
     */
    public Breadcrumb() {
        super();
        setFocusable(true);
    }

    @Override
    protected String defaultStyleTag() {
        return "breadcrumb";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.BREADCRUMB;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return String.join(" " + separator + " ", segments);
    }

    /**
     * Appends a segment to the trail.
     *
     * @param text the segment caption (null is treated as empty)
     */
    public void addSegment(String text) {
        segments.add(text == null ? "" : text);
        invalidateLayout();
    }

    /**
     * Replaces every segment.
     *
     * @param labels the new segment captions (null clears the trail)
     */
    public void setSegments(List<String> labels) {
        segments.clear();
        if (labels != null) {
            for (String label : labels) {
                segments.add(label == null ? "" : label);
            }
        }
        hoveredIndex = -1;
        focusedIndex = -1;
        invalidateLayout();
    }

    /**
     * @return the number of segments
     */
    public int getSegmentCount() {
        return segments.size();
    }

    /**
     * Gets a segment caption.
     *
     * @param index the segment index
     * @return the caption, never null
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public String getSegment(int index) {
        return segments.get(index);
    }

    /**
     * @return the separator glyph placed between segments, never null
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Sets the separator glyph placed between segments.
     *
     * @param separator the new glyph (null or empty means no separator)
     */
    public void setSeparator(String separator) {
        this.separator = separator == null ? "" : separator;
        invalidateLayout();
    }

    /**
     * Registers the navigation handler, run with the activated segment index.
     *
     * @param onNavigate the handler, or null to clear it
     */
    public void setOnNavigate(IntConsumer onNavigate) {
        this.onNavigate = onNavigate;
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    private float separatorWidth() {
        return separator.isEmpty() ? 0.0f : font().measureTextWidth(separator);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font f = font();
        float w = 0.0f;
        for (int i = 0; i < segments.size(); i++) {
            w += f.measureTextWidth(segments.get(i));
            if (i < segments.size() - 1) {
                w += 2.0f * SEPARATOR_GAP + separatorWidth();
            }
        }
        return w;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        FontMetrics metrics = font().getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return line + 2.0f * PADDING_VERTICAL;
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

    /**
     * @param localX an x-coordinate in the widget's local space
     * @return the index of the segment containing that position, or -1
     */
    private int segmentAt(float localX) {
        Font f = font();
        float x = 0.0f;
        for (int i = 0; i < segments.size(); i++) {
            float w = f.measureTextWidth(segments.get(i));
            if (localX >= x && localX < x + w) {
                return i;
            }
            x += w;
            if (i < segments.size() - 1) {
                x += 2.0f * SEPARATOR_GAP + separatorWidth();
            }
        }
        return -1;
    }

    /** A clickable segment is any segment except the last (the current page). */
    private boolean isClickable(int index) {
        return index >= 0 && index < segments.size() - 1;
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();
        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (height - line) / 2.0f - metrics.getAscent();

        float x = 0.0f;
        for (int i = 0; i < segments.size(); i++) {
            String label = segments.get(i);
            if (i == segments.size() - 1) {
                textPaint.setColor(enabled
                        ? getTheme().getForegroundColor()
                        : getTheme().getControlTextDisabledColor());
            } else if (i == focusedIndex || i == hoveredIndex) {
                textPaint.setColor(getTheme().getAccentColor());
            } else {
                textPaint.setColor(enabled
                        ? getTheme().getControlTextColor()
                        : getTheme().getControlTextDisabledColor());
            }
            canvas.drawString(label, x, baseline, textPaint, f);
            x += canvas.measureText(label, f);

            if (i < segments.size() - 1) {
                x += SEPARATOR_GAP;
                separatorPaint.setColor(getTheme().getControlTextDisabledColor());
                canvas.drawString(separator, x, baseline, separatorPaint, f);
                x += separatorWidth() + SEPARATOR_GAP;
            }
        }
    }

    private void ensurePaints() {
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (separatorPaint == null || separatorPaint.isClosed()) {
            separatorPaint = new Paint();
            separatorPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        boolean isInside = contains(event.getX(), event.getY());
        float localX = event.getX() - x;

        switch (event.getType()) {
            case MOVE -> {
                int index = isInside ? segmentAt(localX) : -1;
                if (index != hoveredIndex) {
                    hoveredIndex = isClickable(index) ? index : -1;
                    requestRepaint();
                }
            }
            case PRESS -> {
                if (isInside && event.getButton() == MouseButton.LEFT) {
                    requestFocusFromPointer();
                }
            }
            case RELEASE -> {
                if (isInside && event.getButton() == MouseButton.LEFT) {
                    int index = segmentAt(localX);
                    if (isClickable(index)) {
                        runNavigate(index);
                    }
                }
            }
            default -> {
            }
        }
        return isInside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        // Only the clickable prefix participates in keyboard navigation.
        int lastClickable = segments.size() - 2;
        switch (event.getKeyCode()) {
            case GlyphKeys.RIGHT -> {
                if (lastClickable >= 0) {
                    focusedIndex = focusedIndex < 0 ? 0
                            : Math.min(lastClickable, focusedIndex + 1);
                    requestRepaint();
                }
            }
            case GlyphKeys.LEFT -> {
                if (lastClickable >= 0) {
                    focusedIndex = focusedIndex < 0 ? lastClickable
                            : Math.max(0, focusedIndex - 1);
                    requestRepaint();
                }
            }
            case GlyphKeys.ENTER, GlyphKeys.SPACE -> {
                if (isClickable(focusedIndex)) {
                    runNavigate(focusedIndex);
                }
            }
            default -> {
            }
        }
    }

    /**
     * Runs the navigation handler, catching runtime exceptions so a failing
     * callback can never break event dispatch.
     */
    private void runNavigate(int index) {
        if (onNavigate == null) {
            return;
        }
        try {
            onNavigate.accept(index);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Breadcrumb navigation handler threw", e);
        }
    }

    @Override
    protected void onDispose() {
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
        if (separatorPaint != null) {
            separatorPaint.close();
            separatorPaint = null;
        }
    }
}
