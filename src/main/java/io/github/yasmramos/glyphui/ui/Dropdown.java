package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A single-choice drop-down list.
 *
 * <p>Shows the current selection in a control-sized box with a chevron. Clicking
 * the box (or pressing Space, Enter or Down while it is focused) opens a popup
 * list; picking a row sets the selection, updates the caption and notifies the
 * optional change listener.</p>
 *
 * <p>The popup is a {@link Menu}, so the dropdown inherits the whole popup
 * behaviour for free: positioning below the trigger, flipping above when there
 * is no room, dismissal on an outside press or Escape, and keyboard navigation
 * (arrows move the highlight, Enter/Space activate, letters jump by initial).
 * The menu is registered as this widget's trigger, which is what lets the
 * {@link PopupLayer} place it correctly.</p>
 *
 * <p>Like every other widget the native paints are allocated lazily on the
 * first render, so constructing a dropdown and driving its state machine is
 * safe in headless unit tests. Without a running {@code Application} the menu
 * still opens, sizes and positions itself; nothing paints it.</p>
 */
public class Dropdown extends Component {

    /** Horizontal padding between the box edge and its content. */
    public static final float H_PADDING = 10.0f;

    /** Width of the chevron indicator. */
    public static final float CHEVRON_WIDTH = 9.0f;

    /** Height of the chevron indicator. */
    public static final float CHEVRON_HEIGHT = 5.0f;

    /** Gap between the caption and the chevron. */
    public static final float CHEVRON_GAP = 8.0f;

    /** Stroke width of the chevron. */
    private static final float CHEVRON_STROKE = 1.6f;

    /** Alpha applied to the theme border colour for the box hairline. */
    private static final int BORDER_ALPHA = 160;

    /** Popup list backing this dropdown. */
    private final Menu menu = new Menu();

    /** Item labels, in display order. */
    private final List<String> items = new ArrayList<>();

    /** Rows mirroring {@link #items}, so a pick can resolve its index. */
    private final List<MenuItem> rows = new ArrayList<>();

    private int selectedIndex = -1;
    private String placeholder = "";
    private Consumer<Integer> onChange;

    private Paint boxPaint;
    private Paint borderPaint;
    private Paint textPaint;
    private Paint chevronPaint;

    /**
     * Creates an empty dropdown with no explicit bounds.
     */
    public Dropdown() {
        super();
        init();
    }

    /**
     * Creates an empty dropdown with explicit bounds.
     *
     * @param x      the x-coordinate
     * @param y      the y-coordinate
     * @param width  the width of the box
     * @param height the height of the box
     */
    public Dropdown(float x, float y, float width, float height) {
        super(x, y, width, height);
        init();
    }

    private void init() {
        menu.setTrigger(this);
    }

    @Override
    protected String defaultStyleTag() {
        return "select";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.COMBO_BOX;
    }

    @Override
    public String getAccessibleName() {
        String override = accessibleNameOverride();
        if (override != null && !override.isEmpty()) {
            return override;
        }
        String selected = getSelectedItem();
        if (selected != null && !selected.isEmpty()) {
            return selected;
        }
        if (placeholder != null && !placeholder.isEmpty()) {
            return placeholder;
        }
        return "Dropdown";
    }

    // ------------------------------------------------------------------
    // Building
    // ------------------------------------------------------------------

    /**
     * Appends an item and returns this dropdown for chaining.
     *
     * @param text the item label
     * @return this dropdown
     */
    public Dropdown addItem(String text) {
        String label = text == null ? "" : text;
        items.add(label);

        int index = items.size() - 1;
        MenuItem row = menu.addItem(label);
        row.setCheckable(true);
        row.setOnClick(() -> setSelectedIndex(index));
        rows.add(row);

        // A first item arriving with nothing selected becomes the selection,
        // matching the native select element's default behaviour.
        if (selectedIndex < 0) {
            setSelectedIndex(index);
        } else {
            refreshChecks();
        }
        invalidateLayout();
        return this;
    }

    /**
     * Appends several items in one call.
     *
     * @param texts the item labels
     * @return this dropdown
     */
    public Dropdown addItems(String... texts) {
        if (texts != null) {
            for (String text : texts) {
                addItem(text);
            }
        }
        return this;
    }

    /**
     * Removes every item and clears the selection.
     */
    public void clearItems() {
        items.clear();
        rows.clear();
        menu.clear();
        selectedIndex = -1;
        invalidateLayout();
        requestRepaint();
    }

    /**
     * Gets the item labels.
     *
     * @return a copy of the labels, in display order
     */
    public List<String> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Gets the number of items.
     *
     * @return the item count
     */
    public int getItemCount() {
        return items.size();
    }

    // ------------------------------------------------------------------
    // Selection
    // ------------------------------------------------------------------

    /**
     * Gets the selected index.
     *
     * @return the index, or -1 when nothing is selected
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Gets the selected item's label.
     *
     * @return the label, or null when nothing is selected
     */
    public String getSelectedItem() {
        return selectedIndex >= 0 && selectedIndex < items.size()
                ? items.get(selectedIndex) : null;
    }

    /**
     * Selects an item by index, notifying the change listener only when the
     * selection actually changes. An out-of-range index (or -1) clears the
     * selection.
     *
     * @param index the index to select
     */
    public void setSelectedIndex(int index) {
        int next = index >= 0 && index < items.size() ? index : -1;
        if (next == selectedIndex) {
            refreshChecks();
            return;
        }
        selectedIndex = next;
        refreshChecks();
        invalidateLayout();
        Consumer<Integer> listener = onChange;
        if (listener != null) {
            listener.accept(selectedIndex);
        }
    }

    /**
     * Registers a listener invoked after the selection changes.
     *
     * @param onChange the listener, or null to clear it
     */
    public void setOnChange(Consumer<Integer> onChange) {
        this.onChange = onChange;
    }

    /**
     * Gets the placeholder shown while nothing is selected.
     *
     * @return the placeholder (never null)
     */
    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * Sets the placeholder shown while nothing is selected.
     *
     * @param placeholder the placeholder text
     */
    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder == null ? "" : placeholder;
        invalidateLayout();
        requestRepaint();
    }

    /** Marks only the selected row as checked. */
    private void refreshChecks() {
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setChecked(i == selectedIndex);
        }
    }

    // ------------------------------------------------------------------
    // Popup control
    // ------------------------------------------------------------------

    /**
     * Whether the popup list is showing.
     *
     * @return true while open
     */
    public boolean isOpen() {
        return menu.isOpen();
    }

    /**
     * Opens the popup list. A no-op when the dropdown is disabled or has no
     * items.
     */
    public void open() {
        if (!enabled || items.isEmpty() || menu.isOpen()) {
            return;
        }
        menu.open();
    }

    /**
     * Closes the popup list (no-op when already closed).
     */
    public void close() {
        menu.close();
    }

    /**
     * Opens the popup list when closed, closes it when open.
     */
    public void toggle() {
        if (menu.isOpen()) {
            close();
        } else {
            open();
        }
    }

    // ------------------------------------------------------------------
    // Sizing
    // ------------------------------------------------------------------

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        float widest = font.measureTextWidth(currentCaption());
        for (String item : items) {
            widest = Math.max(widest, font.measureTextWidth(item));
        }
        return 2.0f * H_PADDING + widest + CHEVRON_GAP + CHEVRON_WIDTH;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        FontMetrics metrics = font.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        return line + 2.0f * getTheme().getPadding();
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

    /** The caption to paint: the selection, or the muted placeholder. */
    private String currentCaption() {
        String selected = getSelectedItem();
        if (selected != null) {
            return selected;
        }
        return placeholder == null ? "" : placeholder;
    }

    private void ensurePaints() {
        if (boxPaint == null) {
            boxPaint = new Paint();
            boxPaint.setAntiAlias(true);
        }
        if (borderPaint == null) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        if (textPaint == null) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (chevronPaint == null) {
            chevronPaint = new Paint();
            chevronPaint.setAntiAlias(true);
            chevronPaint.setMode(PaintMode.STROKE);
            chevronPaint.setStrokeWidth(CHEVRON_STROKE);
            chevronPaint.setStrokeCap(io.github.humbleui.skija.PaintStrokeCap.ROUND);
        }
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();

        int fill;
        if (!enabled) {
            fill = getTheme().getControlDisabledColor();
        } else if (state == ComponentState.HOVER || state == ComponentState.PRESSED) {
            fill = getTheme().getControlHoverColor();
        } else {
            fill = getTheme().getControlNormalColor();
        }
        boxPaint.setColor(fill);

        float radius = getTheme().getControlCornerRadius();
        canvas.drawRRect(0, 0, width, height, radius, radius, boxPaint);

        int border = getTheme().getBorderColor();
        borderPaint.setColor((BORDER_ALPHA << 24) | (border & 0x00FFFFFF));
        canvas.drawRRect(0.5f, 0.5f, width - 1.0f, height - 1.0f,
                radius, radius, borderPaint);

        Font font = getTheme().getFont(io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        FontMetrics metrics = font.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (height - line) / 2.0f - metrics.getAscent();

        boolean hasSelection = getSelectedItem() != null;
        if (!enabled) {
            textPaint.setColor(getTheme().getControlTextDisabledColor());
        } else if (hasSelection) {
            textPaint.setColor(getTheme().getControlTextColor());
        } else {
            textPaint.setColor(getTheme().getControlTextDisabledColor());
        }
        canvas.drawString(currentCaption(), H_PADDING, baseline, textPaint, font);

        float chevronX = width - H_PADDING - CHEVRON_WIDTH;
        float centerY = height / 2.0f;
        float half = CHEVRON_HEIGHT / 2.0f;
        chevronPaint.setColor(enabled
                ? getTheme().getControlTextColor()
                : getTheme().getControlTextDisabledColor());
        canvas.drawLine(chevronX, centerY - half,
                chevronX + CHEVRON_WIDTH / 2.0f, centerY + half, chevronPaint);
        canvas.drawLine(chevronX + CHEVRON_WIDTH / 2.0f, centerY + half,
                chevronX + CHEVRON_WIDTH, centerY - half, chevronPaint);
        // The focus ring is painted by Component.renderInternal() when
        // isFocusVisible() is true — widgets must not draw it themselves.
    }

    // ------------------------------------------------------------------
    // Interaction
    // ------------------------------------------------------------------

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        if (!visible || !enabled) {
            return false;
        }

        boolean inside = contains(event.getX(), event.getY());
        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE:
                if (!menu.isOpen()) {
                    setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                }
                break;

            case PRESS:
                if (inside && event.getButton() == MouseButton.LEFT) {
                    setState(ComponentState.PRESSED);
                    requestFocusFromPointer();
                }
                break;

            case RELEASE:
                boolean activate = state == ComponentState.PRESSED && inside;
                setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                if (activate) {
                    toggle();
                }
                break;

            default:
                break;
        }

        if (state != oldState) {
            requestRepaint();
        }
        return inside;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!enabled || !isFocused() || event.getType() != KeyEventType.PRESS) {
            return;
        }
        int key = event.getKeyCode();
        // Down opens and moves into the list, matching the platform combo-box
        // convention; Space/Enter open without changing the selection.
        if (key == GlyphKeys.DOWN || key == GlyphKeys.SPACE || key == GlyphKeys.ENTER) {
            open();
        }
    }

    @Override
    protected void onDispose() {
        menu.dispose();
        close(boxPaint);
        boxPaint = null;
        close(borderPaint);
        borderPaint = null;
        close(textPaint);
        textPaint = null;
        close(chevronPaint);
        chevronPaint = null;
    }

    private static void close(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
