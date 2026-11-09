package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphCursor;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.ScrollEvent;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A hierarchical list of expandable nodes.
 *
 * <p>Nodes are added with {@link #addRoot(String)} (and
 * {@link Node#addChild(String)} below that). The widget flattens the visible
 * tree into rows on every render, so a collapsed branch simply contributes no
 * rows: there is no per-node widget, and the tree scales with the data set
 * rather than with the number of controls.</p>
 *
 * <p>The disclosure triangle toggles a branch; clicking a row selects it.
 * The keyboard mirrors the mouse: Up/Down move the selection, Right expands
 * (or steps into the first child), Left collapses (or walks up to the
 * parent), Home/End jump to the bounds and Enter/Space toggles. Selection
 * changes fire {@link #setOnSelect(Consumer) the selection handler} and update
 * {@link #selectedNodeProperty()}.</p>
 *
 * <p>The mouse wheel scrolls the visible rows one row per notch without moving
 * the selection; when the tree already fits, the gesture is left unconsumed so
 * an enclosing scroll container can take it.</p>
 */
public class TreeView extends Component {

    /** Logger for selection-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(TreeView.class.getName());

    /** Default height of one row, logical units. */
    public static final float ROW_HEIGHT = 24.0f;

    /** Horizontal indent applied per depth level, logical units. */
    public static final float INDENT = 16.0f;

    /** Size of the disclosure triangle's bounding box, logical units. */
    public static final float GLYPH_SIZE = 10.0f;

    /** Default number of rows shown when the tree has no explicit height. */
    public static final int DEFAULT_VISIBLE_ROWS = 8;

    /**
     * A node in the tree. Nodes hold their own children and expanded flag;
     * the {@link TreeView} owns the selection.
     */
    public static final class Node {

        private final String label;
        private final List<Node> children = new ArrayList<>();
        private Node parent;
        private boolean expanded;

        /** Depth in the tree, refreshed by the owner on every flatten. */
        private int depth;

        Node(String label) {
            this.label = label == null ? "" : label;
        }

        /**
         * Appends a child node.
         *
         * @param label the child label (null is treated as empty)
         * @return the new child
         */
        public Node addChild(String label) {
            Node child = new Node(label);
            child.parent = this;
            children.add(child);
            return child;
        }

        /**
         * Appends an existing node as a child.
         *
         * @param child the node to adopt (null is ignored)
         * @return the adopted child, or null when the argument was null
         */
        public Node addChild(Node child) {
            if (child == null) {
                return null;
            }
            child.parent = this;
            children.add(child);
            return child;
        }

        /**
         * @return the node label, never null
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return the children, in insertion order
         */
        public List<Node> getChildren() {
            return Collections.unmodifiableList(children);
        }

        /**
         * @return the parent node, or null for a root
         */
        public Node getParent() {
            return parent;
        }

        /**
         * @return true when this node has at least one child
         */
        public boolean isLeaf() {
            return children.isEmpty();
        }

        /**
         * @return true when the node is expanded
         */
        public boolean isExpanded() {
            return expanded;
        }

        /**
         * Sets the expanded flag. A leaf ignores the value (there is nothing
         * to disclose).
         *
         * @param expanded the new flag
         */
        public void setExpanded(boolean expanded) {
            if (!isLeaf()) {
                this.expanded = expanded;
            }
        }
    }

    private final List<Node> roots = new ArrayList<>();

    /** Flattened visible rows, rebuilt whenever the tree shape changes. */
    private final List<Node> visibleNodes = new ArrayList<>();

    private Node selected;
    private int selectedRow = -1;
    private int firstVisible;

    private Consumer<Node> onSelect;
    private Property<Node> selectedNodeProperty;

    private Integer backgroundOverride;
    private Integer selectedColorOverride;
    private Integer textColorOverride;

    private Paint bgPaint;
    private Paint selectedPaint;
    private Paint textPaint;
    private Paint glyphPaint;
    private Paint borderPaint;

    /**
     * Creates an empty tree with no explicit bounds.
     */
    public TreeView() {
        super();
        setFocusable(true);
        rebuild();
    }

    @Override
    protected String defaultStyleTag() {
        return "treeview";
    }

    @Override
    protected GlyphCursor defaultCursor() {
        return GlyphCursor.POINTING_HAND;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TREE;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return selected == null ? "Tree" : "Tree, selected: " + selected.getLabel();
    }

    // ---- Building ----------------------------------------------------------

    /**
     * Appends a root node.
     *
     * @param label the root label (null is treated as empty)
     * @return the new root
     */
    public Node addRoot(String label) {
        Node root = new Node(label);
        roots.add(root);
        rebuild();
        return root;
    }

    /**
     * Appends an existing node as a root.
     *
     * @param node the node to add (null is ignored)
     * @return the added root, or null when the argument was null
     */
    public Node addRoot(Node node) {
        if (node == null) {
            return null;
        }
        roots.add(node);
        rebuild();
        return node;
    }

    /**
     * @return the root nodes, in insertion order
     */
    public List<Node> getRoots() {
        return Collections.unmodifiableList(roots);
    }

    /**
     * Removes every node and clears the selection.
     */
    public void clear() {
        roots.clear();
        selected = null;
        selectedRow = -1;
        firstVisible = 0;
        if (selectedNodeProperty != null) {
            selectedNodeProperty.set(null);
        }
        rebuild();
    }

    /**
     * Recomputes the flattened visible rows. Call after mutating a node's
     * children directly; the {@code addRoot} / {@code addChild} helpers do it
     * for you.
     */
    public void refresh() {
        rebuild();
    }

    private void rebuild() {
        visibleNodes.clear();
        for (Node root : roots) {
            flatten(root, 0);
        }
        if (selected == null || !visibleNodes.contains(selected)) {
            selectedRow = -1;
        } else {
            selectedRow = visibleNodes.indexOf(selected);
        }
        ensureSelectionVisible();
        invalidateLayout();
    }

    private void flatten(Node node, int depth) {
        node.depth = depth;
        visibleNodes.add(node);
        if (node.isExpanded()) {
            for (Node child : node.children) {
                flatten(child, depth + 1);
            }
        }
    }

    // ---- Selection ---------------------------------------------------------

    /**
     * @return the selected node, or null when nothing is selected
     */
    public Node getSelectedNode() {
        return selected;
    }

    /**
     * @return the selected row index in the flattened view, or {@code -1}
     */
    public int getSelectedRow() {
        return selectedRow;
    }

    /**
     * Selects a node without firing the selection handler.
     *
     * @param node the node to select, or null to clear the selection
     */
    public void setSelectedNode(Node node) {
        applySelection(node, false);
    }

    /**
     * @return the selected node as an observable property
     */
    public Property<Node> selectedNodeProperty() {
        if (selectedNodeProperty == null) {
            selectedNodeProperty = new Property<>(propertyOwner(), selected,
                    node -> {
                        this.selected = node;
                        this.selectedRow = node == null ? -1
                                : visibleNodes.indexOf(node);
                        ensureSelectionVisible();
                        invalidatePaint();
                    });
        }
        return selectedNodeProperty;
    }

    /**
     * Registers the selection handler, run when the user selects a node.
     *
     * @param onSelect the handler, or null to clear it
     */
    public void setOnSelect(Consumer<Node> onSelect) {
        this.onSelect = onSelect;
    }

    private void applySelection(Node node, boolean notify) {
        if (node == selected) {
            return;
        }
        selected = node;
        selectedRow = node == null ? -1 : visibleNodes.indexOf(node);
        if (selectedNodeProperty != null) {
            selectedNodeProperty.set(node);
        }
        ensureSelectionVisible();
        invalidatePaint();
        if (notify && onSelect != null) {
            try {
                onSelect.accept(node);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "TreeView handler threw", e);
            }
        }
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
     * Gets the label color (CSS {@code color}, then the override, then the
     * theme foreground).
     *
     * @return the text color (ARGB)
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride,
                getTheme().getForegroundColor());
    }

    /**
     * Overrides the label color. Pass null to follow the theme.
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
        if (height <= 0.0f) {
            return DEFAULT_VISIBLE_ROWS;
        }
        return Math.max(1, (int) Math.floor(height / ROW_HEIGHT));
    }

    private int maxFirstVisible() {
        return Math.max(0, visibleNodes.size() - visibleRows());
    }

    private void ensureSelectionVisible() {
        if (selectedRow < 0) {
            return;
        }
        int visible = visibleRows();
        if (selectedRow < firstVisible) {
            firstVisible = selectedRow;
        } else if (selectedRow >= firstVisible + visible) {
            firstVisible = selectedRow - visible + 1;
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
        for (Node node : visibleNodes) {
            widest = Math.max(widest, node.depth * INDENT
                    + GLYPH_SIZE + 4.0f + f.measureTextWidth(node.label));
        }
        return widest + 2.0f * 6.0f;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        int rows = visibleNodes.isEmpty() ? DEFAULT_VISIBLE_ROWS
                : Math.min(visibleNodes.size(), DEFAULT_VISIBLE_ROWS);
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

    /** Maps a local y-coordinate to a flattened row index, or {@code -1}. */
    private int rowAtY(float localY) {
        int row = (int) Math.floor(localY / ROW_HEIGHT);
        if (row < 0) {
            return -1;
        }
        int index = firstVisible + row;
        return index < visibleNodes.size() ? index : -1;
    }

    /**
     * @param localX the x-coordinate in widget space
     * @param node   the node of the row under the pointer
     * @return true when the point falls on the row's disclosure triangle
     */
    private boolean isOnGlyph(float localX, Node node) {
        float glyphX = node.depth * INDENT + 6.0f;
        return localX >= glyphX && localX <= glyphX + GLYPH_SIZE;
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
                    int row = rowAtY(event.getY() - y);
                    if (row >= 0) {
                        Node node = visibleNodes.get(row);
                        if (isOnGlyph(event.getX() - x, node) && !node.isLeaf()) {
                            node.setExpanded(!node.isExpanded());
                            rebuild();
                            invalidatePaint();
                        } else {
                            applySelection(node, true);
                        }
                    }
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
    public boolean onScroll(ScrollEvent event) {
        if (!visible || !enabled || event.getDeltaY() == 0f) {
            return false;
        }
        // One notch scrolls one row without moving the selection, so the wheel
        // is a pure view gesture (matching ListBox).
        int max = maxFirstVisible();
        if (max == 0) {
            return false;
        }
        int rows = Math.round(event.getDeltaY());
        int target = Math.max(0, Math.min(firstVisible + rows, max));
        if (target == firstVisible) {
            return false;
        }
        firstVisible = target;
        invalidatePaint();
        return true;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        int visible = visibleRows();
        switch (event.getKeyCode()) {
            case GlyphKeys.UP -> move(-1);
            case GlyphKeys.DOWN -> move(1);
            case GlyphKeys.PAGE_UP -> move(-visible);
            case GlyphKeys.PAGE_DOWN -> move(visible);
            case GlyphKeys.HOME -> selectRow(0);
            case GlyphKeys.END -> selectRow(visibleNodes.size() - 1);
            case GlyphKeys.RIGHT -> expandOrDescend();
            case GlyphKeys.LEFT -> collapseOrAscend();
            case GlyphKeys.ENTER, GlyphKeys.SPACE -> toggleSelected();
            default -> {
            }
        }
    }

    private void move(int delta) {
        if (visibleNodes.isEmpty()) {
            return;
        }
        int target = selectedRow < 0 ? (delta > 0 ? 0 : visibleNodes.size() - 1)
                : selectedRow + delta;
        selectRow(target);
    }

    private void selectRow(int index) {
        if (index < 0 || index >= visibleNodes.size()) {
            return;
        }
        applySelection(visibleNodes.get(index), true);
    }

    private void expandOrDescend() {
        if (selected == null) {
            selectRow(0);
            return;
        }
        if (!selected.isLeaf() && !selected.isExpanded()) {
            selected.setExpanded(true);
            rebuild();
            invalidatePaint();
        } else if (!selected.isLeaf()) {
            selectRow(selectedRow + 1);
        }
    }

    private void collapseOrAscend() {
        if (selected == null) {
            return;
        }
        if (!selected.isLeaf() && selected.isExpanded()) {
            selected.setExpanded(false);
            rebuild();
            invalidatePaint();
        } else if (selected.getParent() != null) {
            applySelection(selected.getParent(), true);
        }
    }

    private void toggleSelected() {
        if (selected == null || selected.isLeaf()) {
            return;
        }
        selected.setExpanded(!selected.isExpanded());
        rebuild();
        invalidatePaint();
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baselineInRow = (ROW_HEIGHT - line) / 2.0f - metrics.getAscent();

        bgPaint.setColor(enabled ? getBackgroundColor()
                : getTheme().getControlDisabledColor());
        canvas.drawRect(0, 0, width, height, bgPaint);

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            int rows = visibleRows();
            for (int row = 0; row < rows; row++) {
                int index = firstVisible + row;
                if (index >= visibleNodes.size()) {
                    break;
                }
                Node node = visibleNodes.get(index);
                float rowY = row * ROW_HEIGHT;
                boolean isSelected = index == selectedRow;
                if (isSelected) {
                    selectedPaint.setColor(enabled ? getSelectedColor()
                            : getTheme().getControlDisabledColor());
                    canvas.drawRect(0, rowY, width, ROW_HEIGHT, selectedPaint);
                }
                int color = isSelected ? 0xFFFFFFFF
                        : (enabled ? getTextColor()
                                : getTheme().getControlTextDisabledColor());

                float glyphX = node.depth * INDENT + 6.0f;
                if (!node.isLeaf()) {
                    glyphPaint.setColor(color);
                    drawTriangle(canvas, glyphX, rowY + (ROW_HEIGHT - GLYPH_SIZE) / 2.0f,
                            node.isExpanded());
                }
                textPaint.setColor(color);
                canvas.drawString(node.label, glyphX + GLYPH_SIZE + 4.0f,
                        rowY + baselineInRow, textPaint, f);
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }

        borderPaint.setColor(getTheme().getBorderColor());
        canvas.drawRect(0.5f, 0.5f, Math.max(0.0f, width - 1.0f),
                Math.max(0.0f, height - 1.0f), borderPaint);
    }

    /** Draws the disclosure triangle: pointing down when expanded, right when not. */
    private void drawTriangle(Canvas canvas, float x, float y, boolean expanded) {
        if (expanded) {
            canvas.drawLine(x, y + GLYPH_SIZE * 0.3f, x + GLYPH_SIZE, y + GLYPH_SIZE * 0.3f,
                    glyphPaint);
            canvas.drawLine(x, y + GLYPH_SIZE * 0.3f, x + GLYPH_SIZE * 0.5f, y + GLYPH_SIZE,
                    glyphPaint);
            canvas.drawLine(x + GLYPH_SIZE * 0.5f, y + GLYPH_SIZE,
                    x + GLYPH_SIZE, y + GLYPH_SIZE * 0.3f, glyphPaint);
        } else {
            canvas.drawLine(x + GLYPH_SIZE * 0.3f, y, x + GLYPH_SIZE * 0.3f, y + GLYPH_SIZE,
                    glyphPaint);
            canvas.drawLine(x + GLYPH_SIZE * 0.3f, y, x + GLYPH_SIZE, y + GLYPH_SIZE * 0.5f,
                    glyphPaint);
            canvas.drawLine(x + GLYPH_SIZE, y + GLYPH_SIZE * 0.5f,
                    x + GLYPH_SIZE * 0.3f, y + GLYPH_SIZE, glyphPaint);
        }
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
        if (glyphPaint == null || glyphPaint.isClosed()) {
            glyphPaint = new Paint();
            glyphPaint.setAntiAlias(true);
            glyphPaint.setStrokeWidth(1.5f);
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
        closeQuietly(selectedPaint);
        selectedPaint = null;
        closeQuietly(textPaint);
        textPaint = null;
        closeQuietly(glyphPaint);
        glyphPaint = null;
        closeQuietly(borderPaint);
        borderPaint = null;
    }

    private static void closeQuietly(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
