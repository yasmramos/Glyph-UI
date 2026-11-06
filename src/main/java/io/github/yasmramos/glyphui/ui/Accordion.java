package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.ArrayList;
import java.util.List;

/**
 * A vertical stack of collapsible sections.
 *
 * <p>Each section has a clickable header and a body. Clicking a header (or
 * pressing Enter/Space on a focused header) toggles its body. By default only
 * one section is open at a time, the conventional accordion behaviour; call
 * {@link #setAllowMultiple(boolean)} to keep several open at once.</p>
 *
 * <p>The header strip is painted by the widget itself and the bodies are
 * positioned below their headers, so content never overlaps a header. The
 * open-section state is observable through {@link #openIndicesProperty()}.</p>
 */
public class Accordion extends Panel {

    /** Height of each section header, logical units. */
    public static final float HEADER_HEIGHT = 32.0f;

    /** Horizontal padding inside a header, logical units. */
    public static final float HEADER_PADDING = 12.0f;

    /** Vertical gap between sections, logical units. */
    public static final float SECTION_GAP = 4.0f;

    private final List<String> titles = new ArrayList<>();
    private final List<Component> bodies = new ArrayList<>();
    private final List<Boolean> open = new ArrayList<>();
    private boolean allowMultiple;
    private int focusedSection = -1;
    private Property<List<Integer>> openIndicesProperty;

    private Paint headerPaint;
    private Paint titlePaint;
    private Paint chevronPaint;

    /**
     * Creates an empty accordion with no explicit bounds.
     */
    public Accordion() {
        super();
        setLayoutManager(new AccordionLayout());
        setFocusable(true);
    }

    /**
     * Column flow that stacks headers and open bodies. Bodies are the panel's
     * children; each is placed just below its own header and hidden when its
     * section is closed. The pass is idempotent: every child is repositioned
     * from the top on each run, so no offset accumulates.
     */
    private final class AccordionLayout extends FlexLayout {

        AccordionLayout() {
            super(Direction.COLUMN, Justify.FLEX_START, Align.STRETCH);
        }

        @Override
        public void layout(Panel panel) {
            float width = panel.getWidth();
            float y = 0.0f;
            for (int i = 0; i < bodies.size(); i++) {
                Component body = bodies.get(i);
                boolean expanded = open.get(i);
                body.setVisible(expanded);
                if (expanded) {
                    float available = Math.max(0.0f, width - 2.0f * HEADER_PADDING);
                    Dimension d = body.measure(available, Float.POSITIVE_INFINITY);
                    body.applyLayoutSize(available, d.getHeight());
                    body.setX(HEADER_PADDING);
                    body.setY(y + HEADER_HEIGHT);
                    y += HEADER_HEIGHT + d.getHeight();
                } else {
                    y += HEADER_HEIGHT;
                }
                if (i < bodies.size() - 1) {
                    y += SECTION_GAP;
                }
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "accordion";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.ACCORDION;
    }

    /**
     * Adds a section with the supplied header title and body.
     *
     * @param title the header title (null is treated as empty)
     * @param body  the collapsible body (must not be null)
     * @throws IllegalArgumentException when {@code body} is null
     */
    public void addSection(String title, Component body) {
        if (body == null) {
            throw new IllegalArgumentException("Accordion section body must not be null");
        }
        titles.add(title == null ? "" : title);
        bodies.add(body);
        boolean first = open.isEmpty();
        open.add(first);
        body.setVisible(first);
        super.add(body);
        invalidateLayoutTree();
    }

    /**
     * @return the number of sections
     */
    public int getSectionCount() {
        return bodies.size();
    }

    /**
     * Gets a section title.
     *
     * @param index the section index
     * @return the title, never null
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public String getSectionTitle(int index) {
        return titles.get(index);
    }

    /**
     * @param index the section index
     * @return true when the section's body is visible
     */
    public boolean isSectionOpen(int index) {
        return open.get(index);
    }

    /**
     * Opens or closes a section. When {@link #setAllowMultiple(boolean)} is
     * false, opening a section closes every other one.
     *
     * @param index the section index
     * @param expanded the new state
     */
    public void setSectionOpen(int index, boolean expanded) {
        if (index < 0 || index >= open.size() || open.get(index) == expanded) {
            return;
        }
        if (expanded && !allowMultiple) {
            for (int i = 0; i < open.size(); i++) {
                open.set(i, i == index);
            }
        } else {
            open.set(index, expanded);
        }
        applyVisibility();
        invalidateLayoutTree();
        notifyOpenIndices();
    }

    /** Mirrors the open flags onto each body's visibility. */
    private void applyVisibility() {
        for (int i = 0; i < bodies.size(); i++) {
            bodies.get(i).setVisible(open.get(i));
        }
    }

    /**
     * Toggles a section, applying the same single-open rule as
     * {@link #setSectionOpen(int, boolean)}.
     *
     * @param index the section index
     */
    public void toggleSection(int index) {
        if (index < 0 || index >= open.size()) {
            return;
        }
        setSectionOpen(index, !open.get(index));
    }

    /**
     * @return the indices of the open sections, in order
     */
    public List<Integer> getOpenIndices() {
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < open.size(); i++) {
            if (open.get(i)) {
                result.add(i);
            }
        }
        return result;
    }

    /**
     * Returns the observable open-section indices. Reading gives the current
     * indices; setting replaces the open set. Assigning from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the open-indices property (never null after first access)
     */
    public Property<List<Integer>> openIndicesProperty() {
        if (openIndicesProperty == null) {
            openIndicesProperty = new Property<>(propertyOwner(), getOpenIndices(),
                    this::applyOpenIndices);
        }
        return openIndicesProperty;
    }

    private void applyOpenIndices(List<Integer> indices) {
        for (int i = 0; i < open.size(); i++) {
            open.set(i, false);
        }
        if (indices != null) {
            int applied = 0;
            for (Integer index : indices) {
                if (index == null || index < 0 || index >= open.size()) {
                    continue;
                }
                if (!allowMultiple && applied >= 1) {
                    break;
                }
                open.set(index, true);
                applied++;
            }
        }
        applyVisibility();
        invalidateLayoutTree();
    }

    private void notifyOpenIndices() {
        if (openIndicesProperty != null) {
            // Equal lists are ignored by Property.set, so re-applying the same
            // open set through the change hook is a no-op (no feedback loop).
            openIndicesProperty.set(getOpenIndices());
        }
    }

    /**
     * @return true when several sections may be open at once
     */
    public boolean isAllowMultiple() {
        return allowMultiple;
    }

    /**
     * Controls whether several sections may be open at once. Turning this off
     * closes all but the first open section.
     *
     * @param allowMultiple the new policy
     */
    public void setAllowMultiple(boolean allowMultiple) {
        if (this.allowMultiple == allowMultiple) {
            return;
        }
        this.allowMultiple = allowMultiple;
        if (!allowMultiple) {
            int firstOpen = -1;
            for (int i = 0; i < open.size(); i++) {
                if (open.get(i)) {
                    if (firstOpen < 0) {
                        firstOpen = i;
                    } else {
                        open.set(i, false);
                    }
                }
            }
        }
        applyVisibility();
        invalidateLayoutTree();
        notifyOpenIndices();
    }

    /**
     * @param localY a y-coordinate in the widget's local space
     * @return the section index whose header contains that position, or -1
     */
    private int headerAt(float localY) {
        float y = 0.0f;
        for (int i = 0; i < bodies.size(); i++) {
            if (localY >= y && localY < y + HEADER_HEIGHT) {
                return i;
            }
            y += HEADER_HEIGHT;
            if (open.get(i)) {
                y += bodies.get(i).getHeight();
            }
            if (i < bodies.size() - 1) {
                y += SECTION_GAP;
            }
        }
        return -1;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSize = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (cssSize || isCtorSizeExplicit()) {
            return super.measure(maxWidth, maxHeight);
        }
        float width = 0.0f;
        float height = 0.0f;
        for (int i = 0; i < bodies.size(); i++) {
            Component body = bodies.get(i);
            Dimension d = body.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
            width = Math.max(width, d.getWidth() + 2.0f * HEADER_PADDING);
            height += HEADER_HEIGHT;
            if (open.get(i)) {
                height += d.getHeight();
            }
            if (i < bodies.size() - 1) {
                height += SECTION_GAP;
            }
        }
        return new Dimension(width, height);
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        doLayout();
        ensurePaints();

        Font f = resolveFont(Theme.FontRole.BUTTON);
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (HEADER_HEIGHT - line) / 2.0f - metrics.getAscent();
        float radius = getTheme().getControlCornerRadius();

        float y = 0.0f;
        for (int i = 0; i < bodies.size(); i++) {
            headerPaint.setColor(i == focusedSection
                    ? getTheme().getControlHoverColor()
                    : getTheme().getControlNormalColor());
            canvas.drawRRect(0, y, width, HEADER_HEIGHT, radius, radius, headerPaint);

            titlePaint.setColor(enabled
                    ? getTheme().getForegroundColor()
                    : getTheme().getControlTextDisabledColor());
            canvas.drawString(titles.get(i), HEADER_PADDING, y + baseline, titlePaint, f);

            // Chevron: a downward V when open, a rightward V when closed.
            chevronPaint.setColor(getTheme().getControlTextColor());
            float cx = width - HEADER_PADDING - 5.0f;
            float cy = y + HEADER_HEIGHT / 2.0f;
            if (open.get(i)) {
                canvas.drawLine(cx - 5.0f, cy - 2.5f, cx, cy + 2.5f, chevronPaint);
                canvas.drawLine(cx, cy + 2.5f, cx + 5.0f, cy - 2.5f, chevronPaint);
            } else {
                canvas.drawLine(cx - 2.5f, cy - 5.0f, cx + 2.5f, cy, chevronPaint);
                canvas.drawLine(cx + 2.5f, cy, cx - 2.5f, cy + 5.0f, chevronPaint);
            }

            y += HEADER_HEIGHT;
            if (open.get(i)) {
                y += bodies.get(i).getHeight();
            }
            if (i < bodies.size() - 1) {
                y += SECTION_GAP;
            }
        }

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            for (Component body : bodies) {
                if (body.isVisible() && !isDisplayNone(body)) {
                    body.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    private void ensurePaints() {
        if (headerPaint == null || headerPaint.isClosed()) {
            headerPaint = new Paint();
            headerPaint.setAntiAlias(true);
        }
        if (titlePaint == null || titlePaint.isClosed()) {
            titlePaint = new Paint();
            titlePaint.setAntiAlias(true);
        }
        if (chevronPaint == null || chevronPaint.isClosed()) {
            chevronPaint = new Paint();
            chevronPaint.setAntiAlias(true);
            chevronPaint.setMode(PaintMode.STROKE);
            chevronPaint.setStrokeWidth(1.5f);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        float localY = event.getY() - y;
        float localX = event.getX() - x;
        if (event.getType() == MouseEventType.PRESS && event.getButton() == MouseButton.LEFT
                && localX >= 0 && localX < width && localY >= 0 && localY < height) {
            int index = headerAt(localY);
            if (index >= 0) {
                requestFocusFromPointer();
                toggleSection(index);
                return true;
            }
        }
        // Outside the headers, forward to the bodies through the panel
        // hit-testing path so their own controls stay interactive.
        return super.onMouseEvent(event);
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            super.onKeyEvent(event);
            return;
        }
        if (focusedSection < 0 && !bodies.isEmpty()) {
            focusedSection = 0;
        }
        switch (event.getKeyCode()) {
            case GlyphKeys.UP -> {
                focusedSection = Math.max(0, focusedSection - 1);
                invalidatePaint();
            }
            case GlyphKeys.DOWN -> {
                focusedSection = Math.min(bodies.size() - 1, focusedSection + 1);
                invalidatePaint();
            }
            case GlyphKeys.ENTER, GlyphKeys.SPACE -> {
                if (focusedSection >= 0) {
                    toggleSection(focusedSection);
                }
            }
            default -> super.onKeyEvent(event);
        }
    }

    @Override
    protected void onDispose() {
        if (headerPaint != null) {
            headerPaint.close();
            headerPaint = null;
        }
        if (titlePaint != null) {
            titlePaint.close();
            titlePaint = null;
        }
        if (chevronPaint != null) {
            chevronPaint.close();
            chevronPaint = null;
        }
    }
}
