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
 * {@code center}, {@code flex-end}</li>
 * <li>{@code gap}: spacing between items</li>
 * <li>{@code padding}: inner spacing (from the container style)</li>
 * </ul>
 *
 * <p>
 * Child {@code flex-grow} is honoured: leftover main-axis space is shared
 * proportionally among growing items. Child margins are respected as extra
 * spacing around each item.</p>
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
     * Cross-axis alignment of items.
     */
    public enum Align {
        STRETCH, FLEX_START, CENTER, FLEX_END
    }

    private Direction direction;
    private Justify justify;
    private Align alignItems;
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
    }

    public Direction getDirection() {
        return direction;
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

    @Override
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
        float gap = gapOverride != null ? gapOverride
                : (style != null ? style.getFloat(StyleProperty.GAP, 10f) : 10f);
        // Padding defaults to 0, matching CSS flexbox: a container the caller
        // never padded must not silently eat 10px off every child's measured
        // size. That implicit inset capped a 44px row's cross space at 24px,
        // which made TextField.measure clamp a 34.62px field down to 24px and
        // left the control looking like bare text. Opt in via setPadding or a
        // style declaration.
        float pad = paddingOverride != null ? paddingOverride
                : (style != null ? style.getFloat(StyleProperty.PADDING, 0f) : 0f);

        boolean row = dir == Direction.ROW;
        float contentX = pad;
        float contentY = pad;
        float availableMain = (row ? panel.getWidth() : panel.getHeight()) - 2 * pad;
        float availableCross = (row ? panel.getHeight() : panel.getWidth()) - 2 * pad;

        List<Component> items = new ArrayList<>();
        for (Component child : panel.getChildren()) {
            if (child.isVisible() && !"none".equals(displayOf(child))) {
                items.add(child);
            }
        }
        if (items.isEmpty()) {
            return;
        }

        // Pass 1: measure each item with generous constraints.
        int n = items.size();
        Dimension[] measured = new Dimension[n];
        float[] marginStart = new float[n];
        float[] marginEnd = new float[n];
        float totalMain = 0f;
        float totalGrow = 0f;
        for (int i = 0; i < n; i++) {
            Component c = items.get(i);
            Style cs = c.getComputedStyle();
            float m = cs != null ? cs.getFloat(StyleProperty.MARGIN, 0) : 0f;
            marginStart[i] = m;
            marginEnd[i] = m;
            float mx = 2 * m;
            measured[i] = c.measure(Math.max(0, availableMain), Math.max(0, availableCross));
            float mainSize = row ? measured[i].width : measured[i].height;
            totalMain += mainSize + mx;
            totalGrow += cs != null ? cs.getFloat(StyleProperty.FLEX_GROW, 0) : 0;
        }
        totalMain += gap * (n - 1);

        // Pass 2: distribute leftover space to growing items.
        float leftover = Math.max(0, availableMain - totalMain);
        if (leftover > 0 && totalGrow > 0) {
            for (int i = 0; i < n; i++) {
                float grow = itemGrow(items.get(i));
                if (grow > 0) {
                    float add = leftover * grow / totalGrow;
                    if (row) {
                        measured[i] = new Dimension(measured[i].width + add, measured[i].height);
                    } else {
                        measured[i] = new Dimension(measured[i].width, measured[i].height + add);
                    }
                }
            }
            totalMain = availableMain;
        }

        // Pass 3: main-axis offset per justify-content.
        float freeSpace = Math.max(0, availableMain - totalMain);
        float cursor = contentMain(dir, contentX, contentY)
                + leadingOffset(just, freeSpace, n, gap);
        float betweenExtra = just == Justify.SPACE_BETWEEN && n > 1
                ? freeSpace / (n - 1)
                : (just == Justify.SPACE_AROUND && n > 0 ? freeSpace / n : 0);
        if (just == Justify.SPACE_AROUND) {
            cursor += betweenExtra / 2;
        }

        for (int i = 0; i < n; i++) {
            Component c = items.get(i);
            float mainSize = row ? measured[i].width : measured[i].height;
            float crossSize = row ? measured[i].height : measured[i].width;
            cursor += marginStart[i];

            float mainPos = cursor;
            float crossPos;
            switch (align) {
                case CENTER -> crossPos = contentCross(dir, contentX, contentY)
                            + (availableCross - crossSize) / 2;
                case FLEX_END -> crossPos = contentCross(dir, contentX, contentY)
                            + availableCross - crossSize - marginEnd[i];
                case STRETCH -> {
                    crossPos = contentCross(dir, contentX, contentY) + marginStart[i] * 0;
                    crossSize = Math.max(0, availableCross - marginStart[i] - marginEnd[i]);
                }
                default -> // FLEX_START
                    crossPos = contentCross(dir, contentX, contentY) + 0;
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
            cursor += mainSize + marginEnd[i] + gap + betweenExtra;
        }
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
            default -> fallback;
        };
    }
}
