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

import java.util.ArrayList;
import java.util.List;

/**
 * A tabbed container that shows one child panel at a time.
 *
 * <p>Every child added to a {@code Tabs} becomes a tab page. The header strip
 * is painted by the widget itself: each page gets a clickable label, the
 * selected one is highlighted, and the header is drawn above the page content
 * so the two never overlap. The selected index can be observed through
 * {@link #selectedIndexProperty()}.</p>
 *
 * <p>The tab labels come from the child's {@code data-tab} attribute when the
 * page was created from markup, otherwise from the child's accessible name.
 * Pages can also be added programmatically with an explicit label through
 * {@link #addTab(String, Component)}.</p>
 */
public class Tabs extends Panel {

    /** Height of the tab header strip, logical units. */
    public static final float HEADER_HEIGHT = 28.0f;

    /** Horizontal padding inside each tab label, logical units. */
    public static final float TAB_PADDING_HORIZONTAL = 12.0f;

    /** Underline thickness of the selected tab, logical units. */
    public static final float INDICATOR_THICKNESS = 2.0f;

    private final List<String> tabLabels = new ArrayList<>();
    private final List<Component> pages = new ArrayList<>();
    private int selectedIndex = -1;
    private int hoveredIndex = -1;
    private Property<Integer> selectedIndexProperty;

    private Paint headerPaint;
    private Paint borderPaint;
    private Paint indicatorPaint;
    private Paint labelPaint;

    /**
     * Creates an empty tab container with no explicit bounds.
     */
    public Tabs() {
        super();
        // Pages stack in a column below the header strip.
        setLayoutManager(new TabLayout());
        // Tabs are keyboard navigable, so the strip can take focus.
        setFocusable(true);
    }

    /**
     * Column flow that keeps the visible page clear of the header strip. The
     * base pass repositions pages from the panel's top-left on each run, so
     * adding the header offset afterwards is deterministic and cannot
     * accumulate across passes.
     */
    private final class TabLayout extends FlexLayout {

        TabLayout() {
            super(Direction.COLUMN, Justify.FLEX_START, Align.STRETCH);
        }

        @Override
        public void layout(Panel panel) {
            super.layout(panel);
            for (Component page : panel.getChildren()) {
                page.setY(page.getY() + HEADER_HEIGHT);
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "tabs";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TAB_PANEL;
    }

    /**
     * Adds a page with an explicit tab label.
     *
     * @param label the tab label (null is treated as empty)
     * @param page  the page component
     */
    public void addTab(String label, Component page) {
        if (page == null) {
            throw new IllegalArgumentException("Tabs page must not be null");
        }
        pages.add(page);
        tabLabels.add(label == null ? "" : label);
        super.add(page);
        if (selectedIndex < 0) {
            setSelectedIndex(0);
        } else {
            applyVisibility();
            invalidateLayout();
        }
    }

    @Override
    public void add(Component component) {
        if (pages.contains(component)) {
            // Already a known page (added through addTab): only re-attach.
            super.add(component);
            return;
        }
        pages.add(component);
        tabLabels.add(labelOf(component, pages.size() - 1));
        super.add(component);
        if (selectedIndex < 0) {
            setSelectedIndex(0);
        } else {
            applyVisibility();
            invalidateLayout();
        }
    }

    /**
     * Derives a tab label for a child that was not added through
     * {@link #addTab(String, Component)}.
     */
    private String labelOf(Component page, int index) {
        String accessible = page.getAccessibleName();
        if (accessible != null && !accessible.isBlank()
                && !accessible.equals(page.getStyleTag())) {
            return accessible;
        }
        return "Tab " + (index + 1);
    }

    /**
     * @return the number of tab pages
     */
    public int getTabCount() {
        return pages.size();
    }

    /**
     * Gets the tab label at an index.
     *
     * @param index the tab index
     * @return the label, never null
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public String getTabLabel(int index) {
        return tabLabels.get(index);
    }

    /**
     * Gets the selected tab index, or {@code -1} when there are no pages.
     *
     * @return the selected index
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
                    this::onSelectedIndexChanged);
        }
        return selectedIndexProperty;
    }

    /**
     * Selects a tab by index. Out-of-range indices are ignored, so a caller
     * cannot leave the container without a visible page.
     *
     * @param index the tab index
     */
    public void setSelectedIndex(int index) {
        if (index < 0 || index >= pages.size() || index == selectedIndex) {
            return;
        }
        selectedIndexProperty().set(index);
    }

    private void onSelectedIndexChanged(int index) {
        if (index < 0 || index >= pages.size()) {
            return;
        }
        this.selectedIndex = index;
        applyVisibility();
        invalidateLayout();
    }

    /**
     * Shows only the selected page and hides the rest.
     */
    private void applyVisibility() {
        for (int i = 0; i < pages.size(); i++) {
            pages.get(i).setVisible(i == selectedIndex);
        }
    }

    /**
     * Width of a tab label cell, including its horizontal padding.
     */
    private float tabWidth(int index) {
        Font f = resolveFont(Theme.FontRole.BUTTON);
        return f.measureTextWidth(tabLabels.get(index))
                + 2.0f * TAB_PADDING_HORIZONTAL;
    }

    /**
     * @param localX an x-coordinate in the widget's local space
     * @return the tab index at that position, or -1 when none
     */
    private int tabAt(float localX) {
        float cursor = 0.0f;
        for (int i = 0; i < tabLabels.size(); i++) {
            float w = tabWidth(i);
            if (localX >= cursor && localX < cursor + w) {
                return i;
            }
            cursor += w;
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
        Dimension content = super.measure(maxWidth, maxHeight);
        return new Dimension(content.getWidth(), content.getHeight() + HEADER_HEIGHT);
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return super.getPreferredHeight() + HEADER_HEIGHT;
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        doLayout();
        ensurePaints();

        // Header strip background and bottom rule.
        headerPaint.setColor(getTheme().getControlNormalColor());
        canvas.drawRect(0, 0, width, HEADER_HEIGHT, headerPaint);
        borderPaint.setColor(getTheme().getBorderColor());
        canvas.drawRect(0, HEADER_HEIGHT - 1.0f, width, 1.0f, borderPaint);

        Font f = resolveFont(Theme.FontRole.BUTTON);
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (HEADER_HEIGHT - line) / 2.0f - metrics.getAscent();

        float cursor = 0.0f;
        for (int i = 0; i < tabLabels.size(); i++) {
            float w = tabWidth(i);
            if (i == selectedIndex) {
                indicatorPaint.setColor(getTheme().getAccentColor());
                canvas.drawRect(cursor, HEADER_HEIGHT - INDICATOR_THICKNESS,
                        w, INDICATOR_THICKNESS, indicatorPaint);
            }
            int color;
            if (!enabled) {
                color = getTheme().getControlTextDisabledColor();
            } else if (i == selectedIndex) {
                color = getTheme().getAccentColor();
            } else if (i == hoveredIndex) {
                color = getTheme().getForegroundColor();
            } else {
                color = getTheme().getControlTextColor();
            }
            labelPaint.setColor(color);
            float textWidth = canvas.measureText(tabLabels.get(i), f);
            float textX = cursor + (w - textWidth) / 2.0f;
            canvas.drawString(tabLabels.get(i), textX, baseline, labelPaint, f);
            cursor += w;
        }

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            for (Component page : pages) {
                if (page.isVisible() && !isDisplayNone(page)) {
                    page.renderInternal(canvas);
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
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
        }
        if (indicatorPaint == null || indicatorPaint.isClosed()) {
            indicatorPaint = new Paint();
            indicatorPaint.setAntiAlias(true);
        }
        if (labelPaint == null || labelPaint.isClosed()) {
            labelPaint = new Paint();
            labelPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }
        float localX = event.getX() - x;
        float localY = event.getY() - y;
        boolean inHeader = localY >= 0 && localY < HEADER_HEIGHT
                && localX >= 0 && localX < width;

        if (event.getType() == MouseEventType.MOVE) {
            int hover = inHeader ? tabAt(localX) : -1;
            if (hover != hoveredIndex) {
                hoveredIndex = hover;
                invalidatePaint();
            }
            return inHeader;
        }
        if (event.getType() == MouseEventType.PRESS && inHeader
                && event.getButton() == MouseButton.LEFT) {
            requestFocusFromPointer();
            int index = tabAt(localX);
            if (index >= 0) {
                setSelectedIndex(index);
            }
            return true;
        }
        if (inHeader) {
            return true;
        }
        // Outside the header, forward to the visible page through the panel
        // hit-testing path.
        return super.onMouseEvent(event);
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            super.onKeyEvent(event);
            return;
        }
        switch (event.getKeyCode()) {
            case GlyphKeys.LEFT -> setSelectedIndex(selectedIndex - 1);
            case GlyphKeys.RIGHT -> setSelectedIndex(selectedIndex + 1);
            case GlyphKeys.HOME -> setSelectedIndex(0);
            case GlyphKeys.END -> setSelectedIndex(pages.size() - 1);
            default -> super.onKeyEvent(event);
        }
    }

    @Override
    protected void onDispose() {
        if (headerPaint != null) {
            headerPaint.close();
            headerPaint = null;
        }
        if (borderPaint != null) {
            borderPaint.close();
            borderPaint = null;
        }
        if (indicatorPaint != null) {
            indicatorPaint.close();
            indicatorPaint = null;
        }
        if (labelPaint != null) {
            labelPaint.close();
            labelPaint = null;
        }
    }
}
