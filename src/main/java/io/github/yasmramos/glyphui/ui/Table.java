package io.github.yasmramos.glyphui.ui;

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
 * A grid of text rows and columns with a header strip.
 *
 * <p>The table owns its columns ({@link #addColumn(String)}) and its rows
 * ({@link #addRow(String...)}), and paints a fixed number of rows into its
 * bounds, scrolling the visible window to keep the selected row on screen.
 * Column widths come from the header caption unless the column is given an
 * explicit width with {@link #setColumnWidth(int, float)}, in which case the
 * remaining space is shared between the flexible columns.</p>
 *
 * <p>The selection follows the mouse (a click selects the row under the
 * pointer) and the keyboard: the arrow keys move it, Page Up/Down jump a
 * screen, and Home/End go to the bounds. Moving the selection fires the
 * {@link #setOnSelect(IntConsumer) selection handler} and updates
 * {@link #selectedIndexProperty()}. A value of {@code -1} means "nothing
 * selected".</p>
 */
public class Table extends Component {

    /** Logger for selection-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Table.class.getName());

    /** Default height of one row, logical units. */
    public static final float ROW_HEIGHT = 24.0f;

    /** Default height of the header strip, logical units. */
    public static final float HEADER_HEIGHT = 28.0f;

    /** Horizontal padding inside a cell, logical units. */
    public static final float CELL_PADDING = 8.0f;

    /** Minimum width a column may be given, logical units. */
    public static final float MIN_COLUMN_WIDTH = 24.0f;

    /** Default number of body rows shown when the table has no explicit height. */
    public static final int DEFAULT_VISIBLE_ROWS = 6;

    /** One column: a caption and an optional explicit width. */
    private static final class Column {
        private final String title;
        private Float width;

        Column(String title) {
            this.title = title;
        }
    }

    private final List<Column> columns = new ArrayList<>();
    private final List<List<String>> rows = new ArrayList<>();

    private int selectedIndex = -1;

    /** Index of the top-most visible body row. */
    private int firstVisible;

    private IntConsumer onSelect;
    private Property<Integer> selectedIndexProperty;

    private Integer backgroundOverride;
    private Integer headerColorOverride;
    private Integer selectedColorOverride;
    private Integer gridColorOverride;
    private Integer textColorOverride;

    private Paint bgPaint;
    private Paint headerPaint;
    private Paint selectedPaint;
    private Paint textPaint;
    private Paint gridPaint;
    private Paint borderPaint;

    /**
     * Creates an empty table with no explicit bounds.
     */
    public Table() {
        super();
        setFocusable(true);
    }

    /**
     * Creates a table with the supplied column captions.
     *
     * @param columnTitles the column captions (nulls are treated as empty)
     */
    public Table(String... columnTitles) {
        this();
        if (columnTitles != null) {
            for (String title : columnTitles) {
                addColumn(title);
            }
        }
    }

    @Override
    protected String defaultStyleTag() {
        return "table";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TABLE;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return "Table, " + rows.size() + " rows, " + columns.size() + " columns";
    }

    // ---- Columns -----------------------------------------------------------

    /**
     * Appends a column with an auto width derived from its caption and cells.
     *
     * @param title the column caption (null is treated as empty)
     */
    public void addColumn(String title) {
        columns.add(new Column(title == null ? "" : title));
        invalidateLayout();
    }

    /**
     * Removes a column and its cells from every row.
     *
     * @param index the zero-based column index
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public void removeColumn(int index) {
        if (index < 0 || index >= columns.size()) {
            throw new IndexOutOfBoundsException("Column index: " + index);
        }
        columns.remove(index);
        for (List<String> row : rows) {
            if (index < row.size()) {
                row.remove(index);
            }
        }
        invalidateLayout();
    }

    /**
     * @return the number of columns
     */
    public int getColumnCount() {
        return columns.size();
    }

    /**
     * @param index the zero-based column index
     * @return the column caption
     */
    public String getColumnTitle(int index) {
        return columns.get(index).title;
    }

    /**
     * Gets a column's width.
     *
     * @param index the zero-based column index
     * @return the explicit width in logical units, or {@code -1} when the
     *         column is auto-sized
     */
    public float getColumnWidth(int index) {
        Float width = columns.get(index).width;
        return width == null ? -1.0f : width;
    }

    /**
     * Pins a column to a fixed width. The remaining space is shared between
     * the flexible columns. Passing a non-positive width restores auto sizing.
     *
     * @param index the zero-based column index
     * @param width the width in logical units, or a non-positive value to
     *              restore auto sizing
     */
    public void setColumnWidth(int index, float width) {
        Column column = columns.get(index);
        Float next = width > 0.0f ? Math.max(MIN_COLUMN_WIDTH, width) : null;
        if (java.util.Objects.equals(column.width, next)) {
            return;
        }
        column.width = next;
        invalidateLayout();
    }

    // ---- Rows --------------------------------------------------------------

    /**
     * Appends a row. Missing cells are treated as empty and extra cells beyond
     * the column count are ignored when painting.
     *
     * @param cells the cell texts (null is treated as empty)
     */
    public void addRow(String... cells) {
        List<String> row = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String cell = cells != null && i < cells.length ? cells[i] : null;
            row.add(cell == null ? "" : cell);
        }
        rows.add(row);
        invalidateLayout();
    }

    /**
     * Replaces the whole data set, keeping the columns.
     *
     * @param newRows the new rows, each an array of cell texts
     */
    public void setRows(List<String[]> newRows) {
        rows.clear();
        if (newRows != null) {
            for (String[] row : newRows) {
                addRow(row);
            }
        }
        selectedIndex = clampIndex(selectedIndex);
        if (selectedIndexProperty != null) {
            selectedIndexProperty.set(selectedIndex);
        }
        firstVisible = Math.max(0, Math.min(firstVisible, maxFirstVisible()));
        invalidateLayout();
    }

    /**
     * Removes a row.
     *
     * @param index the zero-based row index
     * @throws IndexOutOfBoundsException when the index is out of range
     */
    public void removeRow(int index) {
        if (index < 0 || index >= rows.size()) {
            throw new IndexOutOfBoundsException("Row index: " + index);
        }
        rows.remove(index);
        if (selectedIndex > index) {
            selectedIndex--;
        } else if (selectedIndex == index) {
            selectedIndex = rows.isEmpty() ? -1
                    : Math.min(index, rows.size() - 1);
        }
        if (selectedIndexProperty != null) {
            selectedIndexProperty.set(selectedIndex);
        }
        ensureSelectionVisible();
        invalidateLayout();
    }

    /**
     * @return the number of body rows
     */
    public int getRowCount() {
        return rows.size();
    }

    /**
     * @param row    the zero-based row index
     * @param column the zero-based column index
     * @return the cell text (empty when the cell is outside the row)
     */
    public String getCell(int row, int column) {
        List<String> cells = rows.get(row);
        return column >= 0 && column < cells.size() ? cells.get(column) : "";
    }

    // ---- Selection ---------------------------------------------------------

    /**
     * @return the selected row index, or {@code -1} when nothing is selected
     */
    public int getSelectedIndex() {
        return selectedIndex;
    }

    /**
     * Selects a row without firing the selection handler.
     *
     * @param index the zero-based row index, or a value outside the range to
     *              clear the selection
     */
    public void setSelectedIndex(int index) {
        apply(index, false);
    }

    /**
     * @return the selected row's cells, or null when nothing is selected
     */
    public List<String> getSelectedRow() {
        return selectedIndex < 0 ? null : Collections.unmodifiableList(rows.get(selectedIndex));
    }

    /**
     * @return the selected row index as an observable property
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
     * Registers the selection handler, run when the user moves the selection.
     *
     * @param onSelect the handler, or null to clear it
     */
    public void setOnSelect(IntConsumer onSelect) {
        this.onSelect = onSelect;
    }

    // ---- Colors ------------------------------------------------------------

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
     * Gets the header strip fill (CSS {@code background}, then the override,
     * then the theme control surface).
     *
     * @return the header color (ARGB)
     */
    public int getHeaderColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, headerColorOverride,
                getTheme().getControlNormalColor());
    }

    /**
     * Overrides the header strip fill. Pass null to follow the theme.
     *
     * @param header the new color (ARGB) or null
     */
    public void setHeaderColor(Integer header) {
        if (java.util.Objects.equals(this.headerColorOverride, header)) {
            return;
        }
        this.headerColorOverride = header;
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
     * @param selected the new color (ARGB) or null
     */
    public void setSelectedColor(Integer selected) {
        if (java.util.Objects.equals(this.selectedColorOverride, selected)) {
            return;
        }
        this.selectedColorOverride = selected;
        invalidatePaint();
    }

    /**
     * Gets the grid line color (CSS {@code border-color}, then the override,
     * then the theme border).
     *
     * @return the grid line color (ARGB)
     */
    public int getGridColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, gridColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the grid line color. Pass null to follow the theme.
     *
     * @param grid the new color (ARGB) or null
     */
    public void setGridColor(Integer grid) {
        if (java.util.Objects.equals(this.gridColorOverride, grid)) {
            return;
        }
        this.gridColorOverride = grid;
        invalidatePaint();
    }

    /**
     * Gets the cell text color (CSS {@code color}, then the override, then the
     * theme foreground).
     *
     * @return the text color (ARGB)
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride,
                getTheme().getForegroundColor());
    }

    /**
     * Overrides the cell text color. Pass null to follow the theme.
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

    // ---- Geometry ----------------------------------------------------------

    private int visibleRows() {
        float body = height - HEADER_HEIGHT;
        if (body <= 0.0f) {
            return DEFAULT_VISIBLE_ROWS;
        }
        return Math.max(1, (int) Math.floor(body / ROW_HEIGHT));
    }

    private int maxFirstVisible() {
        return Math.max(0, rows.size() - visibleRows());
    }

    /** Scrolls the window so the selected row is inside it. */
    private void ensureSelectionVisible() {
        if (selectedIndex < 0) {
            return;
        }
        int visible = visibleRows();
        if (selectedIndex < firstVisible) {
            firstVisible = selectedIndex;
        } else if (selectedIndex >= firstVisible + visible) {
            firstVisible = selectedIndex - visible + 1;
        }
        firstVisible = Math.max(0, Math.min(firstVisible, maxFirstVisible()));
    }

    /**
     * Computes the horizontal extent of every column. Fixed columns keep their
     * width; the rest share what is left proportionally to their content
     * width, and the last flexible column absorbs the rounding remainder so
     * the grid always spans the full width.
     */
    private float[] columnWidths() {
        int count = columns.size();
        float[] widths = new float[count];
        if (count == 0) {
            return widths;
        }
        Font f = font();
        float[] content = new float[count];
        float fixedTotal = 0.0f;
        float contentTotal = 0.0f;
        int flexible = 0;
        for (int i = 0; i < count; i++) {
            content[i] = contentWidth(i, f);
            Float fixed = columns.get(i).width;
            if (fixed != null) {
                widths[i] = fixed;
                fixedTotal += fixed;
            } else {
                flexible++;
                contentTotal += content[i];
            }
        }
        float available = Math.max(0.0f, width - fixedTotal);
        if (flexible > 0) {
            float share = contentTotal > 0.0f ? available / contentTotal : 0.0f;
            float used = 0.0f;
            int lastFlexible = -1;
            for (int i = 0; i < count; i++) {
                if (columns.get(i).width != null) {
                    continue;
                }
                lastFlexible = i;
            }
            for (int i = 0; i < count; i++) {
                if (columns.get(i).width != null) {
                    continue;
                }
                if (i == lastFlexible) {
                    widths[i] = Math.max(0.0f, available - used);
                } else {
                    widths[i] = content[i] * share;
                    used += widths[i];
                }
            }
        }
        return widths;
    }

    private float contentWidth(int column, Font f) {
        float widest = f.measureTextWidth(columns.get(column).title);
        for (List<String> row : rows) {
            if (column < row.size()) {
                widest = Math.max(widest, f.measureTextWidth(row.get(column)));
            }
        }
        return widest + 2.0f * CELL_PADDING;
    }

    private int clampIndex(int index) {
        if (rows.isEmpty()) {
            return -1;
        }
        if (index < 0) {
            return -1;
        }
        return Math.min(index, rows.size() - 1);
    }

    /** Maps a local y-coordinate to a body row index, or {@code -1}. */
    private int rowAtY(float localY) {
        float bodyY = localY - HEADER_HEIGHT;
        if (bodyY < 0.0f) {
            return -1;
        }
        int row = (int) Math.floor(bodyY / ROW_HEIGHT);
        int index = firstVisible + row;
        return index < rows.size() ? index : -1;
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
        float total = 0.0f;
        for (int i = 0; i < columns.size(); i++) {
            Float fixed = columns.get(i).width;
            total += fixed != null ? fixed : contentWidth(i, f);
        }
        return total;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        int visible = rows.isEmpty() ? DEFAULT_VISIBLE_ROWS
                : Math.min(rows.size(), DEFAULT_VISIBLE_ROWS);
        return HEADER_HEIGHT + visible * ROW_HEIGHT;
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

    // ---- Input -------------------------------------------------------------

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
        int visible = visibleRows();
        switch (event.getKeyCode()) {
            case GlyphKeys.UP -> apply(selectedIndex - 1, true);
            case GlyphKeys.DOWN -> apply(selectedIndex + 1, true);
            case GlyphKeys.PAGE_UP -> apply(selectedIndex - visible, true);
            case GlyphKeys.PAGE_DOWN -> apply(selectedIndex + visible, true);
            case GlyphKeys.HOME -> apply(0, true);
            case GlyphKeys.END -> apply(rows.size() - 1, true);
            default -> {
            }
        }
    }

    /** Applies a user-driven selection and reports the change. */
    private void apply(int index, boolean notify) {
        int target = clampIndex(index);
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
                LOG.log(Level.SEVERE, "Table handler threw", e);
            }
        }
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baselineInRow = (ROW_HEIGHT - line) / 2.0f - metrics.getAscent();
        float headerBaseline = (HEADER_HEIGHT - line) / 2.0f - metrics.getAscent();

        bgPaint.setColor(enabled ? getBackgroundColor()
                : getTheme().getControlDisabledColor());
        canvas.drawRect(0, 0, width, height, bgPaint);

        float[] widths = columnWidths();

        headerPaint.setColor(getHeaderColor());
        canvas.drawRect(0, 0, width, HEADER_HEIGHT, headerPaint);
        textPaint.setColor(enabled ? getTheme().getForegroundColor()
                : getTheme().getControlTextDisabledColor());
        float cellX = 0.0f;
        for (int i = 0; i < columns.size(); i++) {
            canvas.drawString(columns.get(i).title, cellX + CELL_PADDING,
                    headerBaseline, textPaint, f);
            cellX += widths[i];
        }

        int saveCount = canvas.save();
        canvas.clipRect(0, HEADER_HEIGHT, width, height);
        try {
            int visible = visibleRows();
            for (int row = 0; row < visible; row++) {
                int index = firstVisible + row;
                if (index >= rows.size()) {
                    break;
                }
                float rowY = HEADER_HEIGHT + row * ROW_HEIGHT;
                boolean selected = index == selectedIndex;
                if (selected) {
                    selectedPaint.setColor(enabled ? getSelectedColor()
                            : getTheme().getControlDisabledColor());
                    canvas.drawRect(0, rowY, width, ROW_HEIGHT, selectedPaint);
                }
                int color = selected ? 0xFFFFFFFF
                        : (enabled ? getTextColor()
                                : getTheme().getControlTextDisabledColor());
                textPaint.setColor(color);
                List<String> cells = rows.get(index);
                float x = 0.0f;
                for (int col = 0; col < columns.size(); col++) {
                    String text = col < cells.size() ? cells.get(col) : "";
                    canvas.drawString(text, x + CELL_PADDING,
                            rowY + baselineInRow, textPaint, f);
                    x += widths[col];
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }

        gridPaint.setColor(getGridColor());
        canvas.drawLine(0.0f, HEADER_HEIGHT - 0.5f, width, HEADER_HEIGHT - 0.5f, gridPaint);
        float x = 0.0f;
        for (int i = 0; i < columns.size() - 1; i++) {
            x += widths[i];
            canvas.drawLine(x, 0.0f, x, height, gridPaint);
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
        if (headerPaint == null || headerPaint.isClosed()) {
            headerPaint = new Paint();
            headerPaint.setAntiAlias(true);
        }
        if (selectedPaint == null || selectedPaint.isClosed()) {
            selectedPaint = new Paint();
            selectedPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (gridPaint == null || gridPaint.isClosed()) {
            gridPaint = new Paint();
            gridPaint.setAntiAlias(true);
            gridPaint.setStrokeWidth(1.0f);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(io.github.humbleui.skija.PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
    }

    @Override
    protected void onDispose() {
        closeQuietly(bgPaint);
        bgPaint = null;
        closeQuietly(headerPaint);
        headerPaint = null;
        closeQuietly(selectedPaint);
        selectedPaint = null;
        closeQuietly(textPaint);
        textPaint = null;
        closeQuietly(gridPaint);
        gridPaint = null;
        closeQuietly(borderPaint);
        borderPaint = null;
    }

    private static void closeQuietly(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
