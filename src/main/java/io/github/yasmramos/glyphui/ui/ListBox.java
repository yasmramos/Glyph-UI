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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A scrollable list of selectable text items.
 *
 * <p>The widget paints a fixed number of rows into its bounds and scrolls the
 * window to keep the selected row visible, so the list itself never grows with
 * its content. The selection follows the mouse (a click selects the row under
 * the pointer) and the keyboard: the arrow keys move it, Page Up/Down jump a
 * screen, and Home/End go to the bounds. Moving the selection fires the
 * {@link #setOnSelect(IntConsumer) selection handler}.</p>
 *
 * <p>The selected index is observable through {@link #selectedIndexProperty()}.
 * A value of {@code -1} means "nothing selected".</p>
 */
public class ListBox extends Component {

    /** Logger for selection-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(ListBox.class.getName());

    /** Default height of one row, logical units. */
    public static final float ROW_HEIGHT = 24.0f;

    /** Horizontal padding inside a row, logical units. */
    public static final float ROW_PADDING = 8.0f;

    /** Default number of rows shown when the list has no explicit height. */
    public static final int DEFAULT_VISIBLE_ROWS = 6;

    private final List<String> items = new ArrayList<>();
    private int selectedIndex = -1;

    /** Index of the top-most visible row. */
    private int firstVisible;

    private IntConsumer onSelect;
    private Property<Integer> selectedIndexProperty;

    private Integer backgroundOverride;
    private Integer selectedColorOverride;
    private Integer textColorOverride;

    private Paint bgPaint;
    private Paint selectedPaint;
    private Paint textPaint;
    private Paint borderPaint;

    /**
     * Creates an empty list with no explicit bounds.
     */
    public ListBox() {
        super();
        setFocusable(true);
    }

    /**
     * Creates a list with the supplied items.
     *
     * @param items the initial items (nulls are treated as empty)
     */
    public ListBox(String... items) {
        this();
        if (items != null) {
            for (String item : items) {
                addItem(item);
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "listbox";
    }

    @Override
    protected GlyphCursor defaultCursor() {
        return GlyphCursor.POINTING_HAND;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.LIST_BOX;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        String selected = getSelectedItem();
        return selected == null ? "List" : "List, selected: " + selected;
    }

    /**
     * Appends an item.
     *
     * @param item the item text (null is treated as empty)
     */
    public void addItem(String item) {
        items.add(item == null ? "" : item);
        invalidateLayout();
    }

    /**
     * Removes an item and fixes up the selection.
     *
     * @param index the item index
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public void removeItem(int index) {
        items.remove(index);
        if (selectedIndex >= items.size()) {
            selectedIndex = items.size() - 1;
        }
        firstVisible = Math.max(0, Math.min(firstVisible, maxFirstVisible()));
        invalidateLayout();
    }

    /**
     * @return the number of items
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Gets an item.
     *
     * @param index the item index
     * @return the item text, never null
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public String getItem(int index) {
        return items.get(index);
    }

    /**
     * @return an unmodifiable view of the items, in order
     */
    public List<String> getItems() {
        return Collections.unmodifiableList(items);
    }

    /**
     * @return the zero-based index of the selected row, or {@code -1}
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Gets the selected item.
     *
     * @return the selected text, or null when nothing is selected
     */
    public String getSelectedItem() {
        return selectedIndex >= 0 && selectedIndex < items.size()
                ? items.get(selectedIndex) : null;
    }

    /**
     * Returns the observable selection property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the selection property (never null after first access)
     */
    public Property<Integer> selectedIndexProperty() {
        if (selectedIndexProperty == null) {
            selectedIndexProperty = new Property<>(propertyOwner(), selectedIndex,
                    newIndex -> {
                        this.selectedIndex = clampIndex(newIndex == null ? -1 : newIndex);
                        ensureSelectionVisible();
                        invalidatePaint();
                    });
        }
        return selectedIndexProperty;
    }

    /**
     * Sets the selected index. This does not run the selection handler; the
     * handler only fires on an actual click or key press.
     *
     * @param index the new index, clamped into range ({@code -1} clears it)
     */
    public void setSelectedIndex(int index) {
        selectedIndexProperty().set(index);
    }

    /**
     * Registers the selection handler, run when the user moves the selection.
     *
     * @param onSelect the handler, or null to clear it
     */
    public void setOnSelect(IntConsumer onSelect) {
        this.onSelect = onSelect;
    }

    /**
     * Gets the row fill (CSS {@code background}, then the override, then the
     * theme input surface).
     *
     * @return the fill color (ARGB)
     */
    public int getBackgroundColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, backgroundOverride,
                getTheme().getInputBackgroundColor());
    }

    /**
     * Overrides the row fill. Pass null to follow the theme.
     *
     * @param background the new color (ARGB) or null
     */
    public void setBackgroundColor(Integer background) {
        if (java.util.Objects.equals(this.backgroundOverride, background)) {
            return;
        }
        this.backgroundOverride = background;
        invalidatePaint();
    }

    /**
     * Gets the selected-row fill (CSS {@code accent-color}, then the override,
     * then the theme accent).
     *
     * @return the selection color (ARGB)
     */
    public int getSelectedColor() {
        return resolveIntStyle(StyleProperty.ACCENT_COLOR, selectedColorOverride,
                getTheme().getAccentColor());
    }

    /**
     * Overrides the selected-row fill. Pass null to follow the theme accent.
     *
     * @param selectedColor the new color (ARGB) or null
     */
    public void setSelectedColor(Integer selectedColor) {
        if (java.util.Objects.equals(this.selectedColorOverride, selectedColor)) {
            return;
        }
        this.selectedColorOverride = selectedColor;
        invalidatePaint();
    }

    /**
     * Overrides the row text color. Pass null to follow the theme.
     *
     * @param textColor the new color (ARGB) or null
     */
    public void setTextColor(Integer textColor) {
        if (java.util.Objects.equals(this.textColorOverride, textColor)) {
            return;
        }
        this.textColorOverride = textColor;
        invalidatePaint();
    }

    private int clampIndex(int index) {
        if (items.isEmpty()) {
            return -1;
        }
        if (index < 0) {
            return -1;
        }
        return Math.min(index, items.size() - 1);
    }

    /** Number of rows that fit in the current height. */
    private int visibleRows() {
        if (height <= 0.0f) {
            return DEFAULT_VISIBLE_ROWS;
        }
        return Math.max(1, (int) Math.floor(height / ROW_HEIGHT));
    }

    private int maxFirstVisible() {
        return Math.max(0, items.size() - visibleRows());
    }

    /** Scrolls the window so the selected row is inside it. */
    private void ensureSelectionVisible() {
        if (selectedIndex < 0) {
            return;
        }
        int rows = visibleRows();
        if (selectedIndex < firstVisible) {
            firstVisible = selectedIndex;
        } else if (selectedIndex >= firstVisible + rows) {
            firstVisible = selectedIndex - rows + 1;
        }
        firstVisible = Math.max(0, Math.min(firstVisible, maxFirstVisible()));
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        Font f = font();
        float widest = 0.0f;
        for (String item : items) {
            widest = Math.max(widest, f.measureTextWidth(item));
        }
        return widest + 2.0f * ROW_PADDING;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        int rows = items.isEmpty() ? DEFAULT_VISIBLE_ROWS
                : Math.min(items.size(), DEFAULT_VISIBLE_ROWS);
        return rows * ROW_HEIGHT;
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
        bgPaint.setColor(getBackgroundColor());
        canvas.drawRect(0, 0, width, height, bgPaint);

        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baselineInRow = (ROW_HEIGHT - line) / 2.0f - metrics.getAscent();

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            int rows = visibleRows();
            for (int row = 0; row < rows; row++) {
                int index = firstVisible + row;
                if (index >= items.size()) {
                    break;
                }
                float rowY = row * ROW_HEIGHT;
                boolean selected = index == selectedIndex;
                if (selected) {
                    selectedPaint.setColor(enabled
                            ? getSelectedColor()
                            : getTheme().getControlDisabledColor());
                    canvas.drawRect(0, rowY, width, ROW_HEIGHT, selectedPaint);
                }
                int color = selected ? 0xFFFFFFFF
                        : (enabled ? getTheme().getForegroundColor()
                                : getTheme().getControlTextDisabledColor());
                if (textColorOverride != null && !selected) {
                    color = textColorOverride;
                }
                textPaint.setColor(color);
                canvas.drawString(items.get(index), ROW_PADDING,
                        rowY + baselineInRow, textPaint, f);
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }

        borderPaint.setColor(getTheme().getBorderColor());
        canvas.drawRect(0.5f, 0.5f, Math.max(0.0f, width - 1.0f),
                Math.max(0.0f, height - 1.0f), borderPaint);
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
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(io.github.humbleui.skija.PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
    }

    /** Maps a local y-coordinate to the row index under it, or {@code -1}. */
    private int rowAtY(float localY) {
        int row = (int) Math.floor(localY / ROW_HEIGHT);
        if (row < 0) {
            return -1;
        }
        int index = firstVisible + row;
        return index < items.size() ? index : -1;
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
                    apply(rowAtY(event.getY() - y), true);
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
        int rows = visibleRows();
        switch (event.getKeyCode()) {
            case GlyphKeys.UP -> apply(selectedIndex - 1, true);
            case GlyphKeys.DOWN -> apply(selectedIndex + 1, true);
            case GlyphKeys.PAGE_UP -> apply(selectedIndex - rows, true);
            case GlyphKeys.PAGE_DOWN -> apply(selectedIndex + rows, true);
            case GlyphKeys.HOME -> apply(0, true);
            case GlyphKeys.END -> apply(items.size() - 1, true);
            default -> {
            }
        }
    }

    /** Applies a user-driven selection and reports the change. */
    private void apply(int index, boolean notify) {
        if (items.isEmpty()) {
            return;
        }
        int target = clampIndex(index);
        if (target < 0) {
            return;
        }
        if (target == selectedIndex) {
            return;
        }
        selectedIndex = target;
        if (selectedIndexProperty != null) {
            selectedIndexProperty.set(selectedIndex);
        }
        ensureSelectionVisible();
        invalidatePaint();
        if (notify && onSelect != null) {
            try {
                onSelect.accept(selectedIndex);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "ListBox handler threw", e);
            }
        }
    }

    @Override
    protected void onDispose() {
        if (bgPaint != null) {
            bgPaint.close();
            bgPaint = null;
        }
        if (selectedPaint != null) {
            selectedPaint.close();
            selectedPaint = null;
        }
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
        if (borderPaint != null) {
            borderPaint.close();
            borderPaint = null;
        }
    }
}
