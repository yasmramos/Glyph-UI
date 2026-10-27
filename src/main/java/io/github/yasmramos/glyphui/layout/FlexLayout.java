package io.github.yasmramos.glyphui.layout;

import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Panel;

import java.util.ArrayList;
import java.util.List;

/**
 * A single-axis flexbox layout (subset of CSS Flexbox) that sizes children
 * through {@link Component#measure(float, float)}.
 *
 * <p>
 * Supported properties, read from each panel's computed {@link Style}
 * (overridable programmatically via the setters):</p>
 * <ul>
 * <li>{@code flex-direction}: {@code row} (default) or {@code column}</li>
 * <li>{@code justify-content}: {@code flex-start} (default), {@code center},
 * {@code flex-end}, {@code space-between}, {@code space-around}</li>
 * <li>{@code align-items}: {@code stretch} (default), {@code flex-start},
 * {@code center}, {@code flex-end}, {@code baseline}</li>
 * <li>{@code flex-wrap}: {@code nowrap} (default) or {@code wrap}</li>
 * <li>{@code gap}: spacing between items</li>
 * <li>{@code padding}: inner spacing (from the container style)</li>
 * </ul>
 *
 * <p>
 * Child {@code flex-grow} is honoured: leftover main-axis space is shared
 * proportionally among growing items. Child {@code flex-shrink} shrinks
 * items that overflow the main axis, proportionally to
 * {@code shrink x base size}. Child margins are respected as extra spacing
 * around each item.</p>
 *
 * <p>
 * Baseline alignment uses a text-baseline approximation: for components
 * exposing a baseline offset (labels and text fields) the distance from the
 * top of the item's border box to its first text line; other components are
 * treated as having their baseline at the bottom of their content box plus
 * margin, matching CSS behavior for replaced elements.</p>
 */
public class FlexLayout extends LayoutManager {

    /**
     * Main axis orientation.
     */
    public enum Direction {
        ROW, COLUMN
    }

    /**
     * Main-axis distribution of items.
     */
    public enum Justify {
        FLEX_START, CENTER, FLEX_END, SPACE_BETWEEN, SPACE_AROUND
    }

    /**
     * Cross-axis alignment of items. BASELINE aligns items along the text
     * baseline approximation (Block 5 item 5.10).
     */
    public enum Align {
        STRETCH, FLEX_START, CENTER, FLEX_END, BASELINE
    }

    /**
     * Multi-line behavior (Block 5 item 5.9). WRAP moves items that do not
     * fit on the main axis into additional lines.
     */
    public enum Wrap {
        NOWRAP, WRAP
    }

    private Direction direction;
    private Justify justify;
    private Align alignItems;
    private Wrap wrap;
    private Float gapOverride;
    private Float paddingOverride;

    /**
     * Creates a flex layout with the given main-axis direction.
     *
     * @param direction
     */
    public FlexLayout(Direction direction) {
        this(direction, Justify.FLEX_START, Align.STRETCH);
    }

    /**
     * Creates a row/flex-start/stretch flex layout with style-driven gaps.
     */
    public FlexLayout() {
        this(Direction.ROW, Justify.FLEX_START, Align.STRETCH);
    }

    /**
     * Creates a flex layout with explicit defaults; the computed style of the
     * panel still wins over these when it declares the property.
     *
     * @param direction main axis orientation
     * @param justify main-axis distribution
     * @param alignItems cross-axis alignment
     */
    public FlexLayout(Direction direction, Justify justify, Align alignItems) {
        this.direction = direction;
        this.justify = justify;
        this.alignItems = alignItems;
        this.wrap = Wrap.NOWRAP;
    }

    /**
     * Creates a flex layout with explicit defaults including wrap behavior.
     *
     * @param direction main axis orientation
     * @param justify main-axis distribution
     * @param alignItems cross-axis alignment
     * @param wrap multi-line behavior (Block 5 item 5.9)
     */
    public FlexLayout(Direction direction, Justify justify, Align alignItems, Wrap wrap) {
        this.direction = direction;
        this.justify = justify;
        this.alignItems = alignItems;
        this.wrap = wrap;
    }

    public Direction getDirection() {
        return direction;
    }

    public Wrap getWrap() {
        return wrap;
    }

    public void setWrap(Wrap wrap) {
        this.wrap = wrap;
        notifyOwner();
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
        // Configuration change: ask the container to re-run its layout so
        // the new axis is visible on the next frame (3.5).
        notifyOwner();
    }

    public Justify getJustify() {
        return justify;
    }

    public void setJustify(Justify justify) {
        this.justify = justify;
        notifyOwner();
    }

    public Align getAlignItems() {
        return alignItems;
    }

    public void setAlignItems(Align alignItems) {
        this.alignItems = alignItems;
        notifyOwner();
    }

    public void setGap(float gap) {
        this.gapOverride = gap;
        notifyOwner();
    }

    public void setPadding(float padding) {
        this.paddingOverride = padding;
        notifyOwner();
    }


    /**
     * Measures the flex content: the bounding box of the items along both
     * axes plus padding, mirroring the first pass of {@link #layout}. The
     * main-axis total includes gaps; the cross-axis size is the largest item
     * (stretching does not grow the container's intrinsic size).
     */
    @Override // NOPMD - concrete signature required by the LayoutManager contract
    public Dimension measure(Panel panel, float maxWidth, float maxHeight) {
        Style style = panel.getComputedStyle();
        Direction dir = style != null && style.has(StyleProperty.FLEX_DIRECTION)
                ? parseDirection(style.getString(StyleProperty.FLEX_DIRECTION, null), direction)
                : direction;
        float gap = gapOverride != null ? gapOverride
                : (style != null ? style.getFloat(StyleProperty.GAP, 10f) : 10f);
        float pad = paddingOverride != null ? paddingOverride
                : (style != null ? style.getFloat(StyleProperty.PADDING, 0f) : 0f);

        boolean row = dir == Direction.ROW;
        // Generous constraints for intrinsic sizing: measure against the
        // declared bounds minus padding, unbounded when infinite.
        float availMain = clampAvail(row ? maxWidth : maxHeight, row ? panel.getWidth() : panel.getHeight(), pad);
        float availCross = clampAvail(row ? maxHeight : maxWidth, row ? panel.getHeight() : panel.getWidth(), pad);

        float totalMain = 0f;
        float maxCross = 0f;
        int count = 0;
        for (Component c : panel.getChildren()) {
            if (!c.isVisible() || "none".equals(displayOf(c))) {
                continue;
            }
            Style cs = c.getComputedStyle();
            float m = cs != null ? cs.getFloat(StyleProperty.MARGIN, 0) : 0f;
            Dimension measured = c.measure(Math.max(0, availMain), Math.max(0, availCross));
            float mainSize = row ? measured.width : measured.height;
            float crossSize = row ? measured.height : measured.width;
            totalMain += mainSize + 2 * m;
            maxCross = Math.max(maxCross, crossSize + 2 * m);
            count++;
        }
        if (count > 0) {
            totalMain += gap * (count - 1);
        }
        float w = row ? totalMain + 2 * pad : maxCross + 2 * pad;
        float h = row ? maxCross + 2 * pad : totalMain + 2 * pad;
        return new Dimension(Math.max(0f, w), Math.max(0f, h));
    }

    /**
     * Converts a measurement constraint into available content space: uses
     * the supplied maximum when finite, otherwise falls back to the panel's
     * current size on that axis; always subtracts twice the padding.
     */
    private static float clampAvail(float max, float panelSize, float pad) {
        float base = Float.isInfinite(max) ? panelSize : max;
        return Math.max(0f, base - 2 * pad);
    }

    // Priority 3 item 8: reusable per-pass scratch buffers. Guarded by the
    // UI-thread contract of the layout pipeline (layout() only runs from
    // Panel.doLayout on the UI thread), so no synchronization is needed.
    private Dimension[] scratchMeasured;
    private float[] scratchMarginStart;
    private float[] scratchMarginEnd;
    private Dimension[] scratchFinalSizes;

    public void layout(Panel panel) {
        Style style = panel.getComputedStyle();
        Direction dir = style != null && style.has(StyleProperty.FLEX_DIRECTION)
                ? parseDirection(style.getString(StyleProperty.FLEX_DIRECTION, null), direction)
                : direction;
        Justify just = style != null && style.has(StyleProperty.JUSTIFY_CONTENT)
                ? parseJustify(style.getString(StyleProperty.JUSTIFY_CONTENT, null), justify)
                : justify;
        Align align = style != null && style.has(StyleProperty.ALIGN_ITEMS)
                ? parseAlign(style.getString(StyleProperty.ALIGN_ITEMS, null), alignItems)
                : alignItems;
        // The container's own computed style is authoritative for layout
        // properties (flex-wrap, gap, padding): StyleEngine writes resolved
        // CSS into it, so a stylesheet declaration like "flex-wrap: wrap"
        // must win over the layout manager's field default.
        Wrap wrapMode = parseWrap(
                style != null ? style.getString(StyleProperty.FLEX_WRAP, null) : null, wrap);
        // Padding defaults to zero: a flex container that was never padded
        // must not silently eat pixels off every child's measured size. That
        // implicit inset capped a 44px row's cross space at 24px, which made
        // TextField.measure clamp a 34.62px field down to 24px and left the
        // control looking like bare text. Opt in via setPadding or a style
        // declaration.
        float gap = gapOverride != null ? gapOverride
                : (style != null ? style.getFloat(StyleProperty.GAP, 10f) : 10f);
        float pad = paddingOverride != null ? paddingOverride
                : (style != null ? style.getFloat(StyleProperty.PADDING, 0f) : 0f);

        boolean row = dir == Direction.ROW;

        List<Component> items = new ArrayList<>();
        for (Component child : panel.getChildren()) {
            if (child.isVisible() && !"none".equals(displayOf(child))) {
                items.add(child);
            }
        }
        if (items.isEmpty()) {
            return;
        }


        // Pass 1: measure each item against the container's full cross space.
        // The main axis is left unconstrained on purpose: measuring with the
        // remaining main space would clamp explicit CSS widths down to what
        // currently fits, which makes overflow detection (flex-wrap) and
        // shrink accounting (flex-shrink) impossible because every item
        // would always "fit". Cross-axis clamping stays so wrapping lines
        // never exceed the container height.
        //
        // Children are measured BEFORE the panel receives its final layout
        // size: sizing a child from a stale panel box (e.g. the content-sized
        // default of an empty flex container) produced wrong wrap decisions.
        // The available space here reflects only padding insets.
        float contentMain = (row ? panel.getWidth() : panel.getHeight()) - 2 * pad;
        float contentCross = (row ? panel.getHeight() : panel.getWidth()) - 2 * pad;
        // Snapshot the panel's layout-assigned box before measuring children:
        // an unconstrained child measure pass can resize the panel through
        // preferred-size side effects, and wrap/shrink accounting must run
        // against the authoritative box captured up front.
        float assignedW = panel.getWidth();
        float assignedH = panel.getHeight();
        final int n = items.size();
        // Priority 3 item 8: the per-pass scratch buffers are reused across
        // layout calls on this instance whenever the visible-item count is
        // unchanged, so steady-state relayouts (60fps animations) stop
        // allocating a fresh Dimension[] + float[] triplet every pass. The
        // arrays are only scratch space — measured/final values never escape
        // into children (sizes are applied through applyLayoutSize), so
        // stale entries from previous passes are harmless once overwritten.
        if (scratchMeasured == null || scratchMeasured.length != n) {
            scratchMeasured = new Dimension[n];
            scratchMarginStart = new float[n];
            scratchMarginEnd = new float[n];
            scratchFinalSizes = new Dimension[n];
        }
        final Dimension[] measured = scratchMeasured;
        final float[] marginStart = scratchMarginStart;
        final float[] marginEnd = scratchMarginEnd;
        final Dimension[] finalSizes = scratchFinalSizes;
        for (int i = 0; i < n; i++) {
            Component c = items.get(i);
            Style cs = c.getComputedStyle();
            float m = cs != null ? cs.getFloat(StyleProperty.MARGIN, 0) : 0f;
            marginStart[i] = m;
            marginEnd[i] = m;
            float crossLimit = row ? contentCross : contentMain;
            measured[i] = c.measure(Float.MAX_VALUE, Math.max(0, crossLimit));
        }

        // Restore the snapshot box after measurement: a child measure pass
        // may have resized the panel through preferred-size side effects;
        // wrap/shrink accounting must run against the authoritative box.
        if (panel.getWidth() != assignedW || panel.getHeight() != assignedH) {
            panel.applyLayoutSize(assignedW, assignedH);
        }
        float availableMain = contentMain;
        float availableCross = contentCross;
        float contentX = pad;
        float contentY = pad;

        // Pass 2: group items into lines honoring flex-wrap (5.9). With
        // NOWRAP every item shares a single line; with WRAP an item that
        // would overflow the main axis (after shrinking is considered it
        // still does not fit at its shrink floor) starts a new line.
        List<List<Integer>> lines = new ArrayList<>();
        List<Float> lineMainTotals = new ArrayList<>();
        if (wrapMode == Wrap.NOWRAP) {
            List<Integer> single = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                single.add(i);
            }
            lines.add(single);
            lineMainTotals.add(totalLineMain(row, measured, marginStart, marginEnd, gap, single));
        } else {
            List<Integer> current = new ArrayList<>();
            float lineMain = 0f;
            for (int i = 0; i < n; i++) {
                float itemMain = mainSizeOf(row, measured[i]) + marginStart[i] + marginEnd[i];
                float needed = current.isEmpty() ? itemMain : gap + itemMain;
                if (!current.isEmpty() && lineMain + needed > availableMain) {
                    lines.add(current);
                    lineMainTotals.add(lineMain);
                    current = new ArrayList<>();
                    lineMain = 0f;
                    needed = itemMain;
                }
                current.add(i);
                lineMain += needed;
            }
            if (!current.isEmpty()) {
                lines.add(current);
                lineMainTotals.add(lineMain);
            }
        }

        // Pass 3: per-line grow/shrink resolution. Overflow shrinks items
        // proportionally to flex-shrink x base size, floored at zero;
        // leftover space grows items proportionally to flex-grow.
        for (int li = 0; li < lines.size(); li++) {
            List<Integer> line = lines.get(li);
            float lineMainTotal = lineMainTotals.isEmpty() ? totalLineMain(row, measured,
                    marginStart, marginEnd, gap, line) : lineMainTotals.get(li);
            float slack = availableMain - lineMainTotal;
            for (int idx : line) {
                finalSizes[idx] = measured[idx];
            }
            if (slack < 0) {
                // Shrink pass (flex-shrink, default 1 like CSS). Explicitly
                // sized items (constructor bounds or setWidth/setHeight) are
                // treated as flex-shrink: 0 — CSS "box-sizing" intuition:
                // a declared box must not silently collapse because siblings
                // overflow. Opt-in shrinking for those is possible via the
                // FLEX_SHRINK computed-style property.
                float weightedShrink = 0f;
                for (int idx : line) {
                    weightedShrink += effectiveShrink(items.get(idx))
                            * mainSizeOf(row, measured[idx]);
                }
                if (weightedShrink > 0) {
                    float deficit = -slack;
                    for (int idx : line) {
                        float base = mainSizeOf(row, measured[idx]);
                        float shrink = effectiveShrink(items.get(idx));
                        float reduce = deficit * (shrink * base) / weightedShrink;
                        float newSize = Math.max(0f, base - reduce);
                        finalSizes[idx] = withMain(row, measured[idx], newSize);
                    }
                }
            } else if (slack > 0) {
                float totalGrow = 0f;
                for (int idx : line) {
                    totalGrow += itemGrow(items.get(idx));
                }
                if (totalGrow > 0) {
                    for (int idx : line) {
                        float grow = itemGrow(items.get(idx));
                        if (grow > 0) {
                            finalSizes[idx] = withMain(row, measured[idx],
                                    mainSizeOf(row, measured[idx]) + slack * grow / totalGrow);
                        }
                    }
                }
            }
        }

        // Pass 4: place lines along the cross axis and items along the main
        // axis, applying justify-content per line and align-items per item.
        // Single-line (NOWRAP) containers keep the historical behavior of
        // distributing/stretching against the full container cross size;
        // multi-line containers stack each line at its own cross extent.
        boolean singleLine = wrapMode == Wrap.NOWRAP || lines.size() == 1;
        float lineCrossCursor = contentCross(dir, contentX, contentY);
        for (int li = 0; li < lines.size(); li++) {
            List<Integer> line = lines.get(li);
            // Line cross size is the tallest/widest item on the line.
            float lineCross = 0f;
            for (int idx : line) {
                float cSize = crossSizeOf(row, finalSizes[idx]) + marginStart[idx] + marginEnd[idx];
                lineCross = Math.max(lineCross, cSize);
            }
            // The space this line may distribute across: the whole container
            // cross axis when there is only one line, otherwise the line's
            // own measured extent.
            float lineSpace = singleLine ? availableCross : lineCross;
            float lineMainTotal = totalLineMain(row, finalSizes, marginStart, marginEnd, gap, line);
            float freeSpace = Math.max(0, availableMain - lineMainTotal);
            float cursor = contentMain(dir, contentX, contentY)
                    + leadingOffset(just, freeSpace, line.size(), gap);
            float betweenExtra = just == Justify.SPACE_BETWEEN && line.size() > 1
                    ? freeSpace / (line.size() - 1)
                    : (just == Justify.SPACE_AROUND && !line.isEmpty() ? freeSpace / line.size() : 0);
            if (just == Justify.SPACE_AROUND) {
                cursor += betweenExtra / 2;
            }

            // Baseline alignment (5.10): compute the max baseline offset of
            // the line so all items share one text baseline.
            float maxBaseline = Float.NEGATIVE_INFINITY;
            if (align == Align.BASELINE) {
                for (int idx : line) {
                    float b = baselineOf(items.get(idx), row, finalSizes[idx]);
                    maxBaseline = Math.max(maxBaseline, b);
                }
            }

            for (int idx : line) {
                Component c = items.get(idx);
                float mainSize = mainSizeOf(row, finalSizes[idx]);
                float crossSize = crossSizeOf(row, finalSizes[idx]);
                cursor += marginStart[idx];

                float mainPos = cursor;
                float crossPos;
                Align effAlign = effectiveAlign(c, align);
                switch (effAlign) {
                    case CENTER -> crossPos = lineCrossCursor + (lineSpace - crossSize) / 2;
                    case FLEX_END -> crossPos = lineCrossCursor + lineSpace - crossSize - marginEnd[idx];
                    case STRETCH -> {
                        crossPos = lineCrossCursor + marginStart[idx];
                        crossSize = Math.max(0, lineSpace - marginStart[idx] - marginEnd[idx]);
                    }
                    case BASELINE -> crossPos = lineCrossCursor
                            + maxBaseline - baselineOf(c, row, finalSizes[idx]);
                    default -> // FLEX_START
                        crossPos = lineCrossCursor + marginStart[idx];
                }

                // Layout-assigned sizes must NOT mark the component as
                // explicitly sized: setWidth/setHeight would pin sizeExplicitlySet
                // = true and override the inline style, so the child could never
                // be re-measured again (e.g. after Label.setText changes its
                // preferred size). applyLayoutSize keeps the component
                // re-measurable on subsequent layout passes.
                if (row) {
                    c.setX(mainPos);
                    c.setY(crossPos);
                    c.applyLayoutSize(mainSize, crossSize);
                } else {
                    c.setX(crossPos);
                    c.setY(mainPos);
                    c.applyLayoutSize(crossSize, mainSize);
                }
                cursor += mainSize + marginEnd[idx] + gap + betweenExtra;
            }
            lineCrossCursor += lineCross + (li < lines.size() - 1 ? gap : 0);
        }
    }

    /**
     * Returns the per-line main-axis total including margins and gaps.
     */
    private static float totalLineMain(boolean row, Dimension[] sizes, float[] ms, float[] me,
            float gap, List<Integer> line) {
        float total = 0f;
        for (int idx : line) {
            total += mainSizeOf(row, sizes[idx]) + ms[idx] + me[idx];
        }
        if (!line.isEmpty()) {
            total += gap * (line.size() - 1);
        }
        return total;
    }

    private static float mainSizeOf(boolean row, Dimension d) {
        return row ? d.width : d.height;
    }

    private static float crossSizeOf(boolean row, Dimension d) {
        return row ? d.height : d.width;
    }

    private static Dimension withMain(boolean row, Dimension d, float newMain) {
        return row ? new Dimension(newMain, d.height) : new Dimension(d.width, newMain);
    }

    /**
     * Resolves the alignment actually used for one item: {@code align-self}
     * overrides {@code align-items}; stretch is demoted to flex-start when
     * the item already has an explicit cross size so intrinsic sizing is not
     * destroyed by stretching.
     */
    private static Align effectiveAlign(Component c, Align containerAlign) {
        Style s = c.getComputedStyle();
        Align a = containerAlign;
        if (s != null && s.has(StyleProperty.ALIGN_SELF)) {
            a = parseAlign(s.getString(StyleProperty.ALIGN_SELF, null), containerAlign);
        }
        return a;
    }

    /**
     * Baseline offset of an item measured from the top of its border box.
     * Text-bearing components report their first-line baseline; everything
     * else falls back to the bottom of the box (CSS replaced-element rule).
     */
    private static float baselineOf(Component c, boolean row, Dimension size) {
        float h = row ? size.height : size.width;
        Style cs = c.getComputedStyle();
        if (cs != null && cs.has(StyleProperty.FONT_SIZE)) {
            // Approximate the first-line baseline as the line box center:
            // half a font-size down from the top of the content box. This is
            // enough to align labels and inputs of different sizes on one
            // shared text line without exposing Skija metrics here.
            float fontSize = cs.getFloat(StyleProperty.FONT_SIZE, 0f);
            if (fontSize > 0f) {
                return Math.min(h, h / 2f + fontSize / 4f);
            }
        }
        return h;
    }

    private static float contentMain(Direction dir, float x, float y) {
        return dir == Direction.ROW ? x : y;
    }

    private static float contentCross(Direction dir, float x, float y) {
        return dir == Direction.ROW ? y : x;
    }

    private static float leadingOffset(Justify j, float freeSpace, int n, float gap) {
        return switch (j) {
            case CENTER -> freeSpace / 2;
            case FLEX_END -> freeSpace;
            case SPACE_AROUND -> n > 0 ? freeSpace / n / 2 : 0;
            default -> 0;
        };
    }

    private static float itemGrow(Component c) {
        Style s = c.getComputedStyle();
        return s != null ? s.getFloat(StyleProperty.FLEX_GROW, 0) : 0;
    }

    private static String displayOf(Component c) {
        Style s = c.getComputedStyle();
        return s != null ? s.getString(StyleProperty.DISPLAY, null) : null;
    }

    private static Direction parseDirection(String raw, Direction fallback) {
        if (raw == null) {
            return fallback;
        }
        String v = raw.trim().toLowerCase();
        if (v.startsWith("column")) {
            return Direction.COLUMN;
        }
        if (v.startsWith("row")) {
            return Direction.ROW;
        }
        return fallback;
    }

    private static Justify parseJustify(String raw, Justify fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "center" -> Justify.CENTER;
            case "flex-end", "end" -> Justify.FLEX_END;
            case "space-between" -> Justify.SPACE_BETWEEN;
            case "space-around" -> Justify.SPACE_AROUND;
            case "flex-start", "start" -> Justify.FLEX_START;
            default -> fallback;
        };
    }

    private static Align parseAlign(String raw, Align fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "center" -> Align.CENTER;
            case "flex-end", "end" -> Align.FLEX_END;
            case "flex-start", "start" -> Align.FLEX_START;
            case "stretch" -> Align.STRETCH;
            case "baseline" -> Align.BASELINE;
            default -> fallback;
        };
    }

    private static Wrap parseWrap(String raw, Wrap fallback) {
        if (raw == null) {
            return fallback;
        }
        return switch (raw.trim().toLowerCase()) {
            case "wrap" -> Wrap.WRAP;
            case "nowrap" -> Wrap.NOWRAP;
            default -> fallback;
        };
    }

    private static float itemShrink(Component c) {
        Style s = c.getComputedStyle();
        // CSS default is flex-shrink: 1; items shrink proportionally unless
        // the style opts out with flex-shrink: 0.
        return s != null ? s.getFloat(StyleProperty.FLEX_SHRINK, 1f) : 1f;
    }

    /**
     * Effective main-axis shrink factor for an item. A declared FLEX_SHRINK
     * in the computed style always wins; otherwise items that were given an
     * explicit size by the user (bounds constructor or setWidth/setHeight)
     * are pinned at 0 so overflow never silently collapses a box the caller
     * asked for. Content-sized items keep the CSS default of 1.
     */
    private static float effectiveShrink(Component c) {
        Style s = c.getComputedStyle();
        if (s != null && s.has(StyleProperty.FLEX_SHRINK)) {
            return s.getFloat(StyleProperty.FLEX_SHRINK, 1f);
        }
        return c.isSizeExplicitlySet() ? 0f : 1f;
    }
}
