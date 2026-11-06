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
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A pager for stepping through a long, page-split result set.
 *
 * <p>The control paints a previous arrow, a window of page numbers (with
 * ellipses when the page count is large), a next arrow and the current page is
 * highlighted. Activating a page (or an arrow) moves the current page and
 * fires the {@link #setOnPageChange(IntConsumer) change handler}. The current
 * page is observable through {@link #currentPageProperty()}.</p>
 *
 * <p>Pages are zero-based in the API but shown one-based, matching the
 * convention of {@link Tabs} for the index and of a user-facing pager for the
 * label. Colors resolve like every other widget: a CSS declaration wins, then
 * the theme.</p>
 */
public class Pagination extends Component {

    /** Logger for page-change handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Pagination.class.getName());

    /** Side length of a square page/arrow button, logical units. */
    public static final float BUTTON_SIZE = 28.0f;

    /** Horizontal gap between buttons, logical units. */
    public static final float GAP = 4.0f;

    /** Largest number of page slots shown before ellipses kick in. */
    public static final int MAX_SLOTS = 7;

    /** An ellipsis slot in the rendered window. */
    private static final int ELLIPSIS = -1;

    /** The kind of item occupying a slot in the control. */
    private enum Kind { PREV, PAGE, ELLIPSIS, NEXT }

    /** A laid-out slot: its kind, the page it targets and its left edge. */
    private record Slot(Kind kind, int page, float x, float width) {
    }

    private int pageCount = 1;
    private int currentPage;
    private IntConsumer onPageChange;
    private Property<Integer> currentPageProperty;

    private int hoveredSlot = -1;
    private List<Slot> slots = List.of();

    private Paint bgPaint;
    private Paint hoverPaint;
    private Paint textPaint;
    private Paint arrowPaint;

    /**
     * Creates a single-page pager with no explicit bounds.
     */
    public Pagination() {
        super();
        setFocusable(true);
    }

    @Override
    protected String defaultStyleTag() {
        return "pagination";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.PAGINATION;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return "Page " + (currentPage + 1) + " of " + pageCount;
    }

    /**
     * @return the number of pages, never less than one
     */
    public int getPageCount() {
        return pageCount;
    }

    /**
     * Sets the number of pages. The current page is clamped into range.
     *
     * @param pageCount the new page count (values below one are raised to one)
     */
    public void setPageCount(int pageCount) {
        int next = Math.max(1, pageCount);
        if (this.pageCount == next) {
            return;
        }
        this.pageCount = next;
        if (currentPage >= next) {
            setCurrentPage(next - 1);
        } else {
            invalidateLayout();
        }
    }

    /**
     * @return the current page (zero-based)
     */
    public int getCurrentPage() {
        return currentPage;
    }

    /**
     * Returns the observable current page. Setting it from a background thread
     * marshals the change onto the UI thread automatically.
     *
     * @return the current-page property (never null after first access)
     */
    public Property<Integer> currentPageProperty() {
        if (currentPageProperty == null) {
            currentPageProperty = new Property<>(propertyOwner(), currentPage,
                    this::applyCurrentPage);
        }
        return currentPageProperty;
    }

    /**
     * Moves to a page, clamped into range. This does not run the change
     * handler; the handler only fires on an actual activation.
     *
     * @param page the target page (zero-based)
     */
    public void setCurrentPage(int page) {
        currentPageProperty().set(page);
    }

    private void applyCurrentPage(Integer page) {
        int clamped = page == null ? 0 : Math.max(0, Math.min(pageCount - 1, page));
        if (currentPage == clamped) {
            return;
        }
        currentPage = clamped;
        hoveredSlot = -1;
        invalidateLayout();
    }

    /**
     * Registers the change handler, run with the new page index.
     *
     * @param onPageChange the handler, or null to clear it
     */
    public void setOnPageChange(IntConsumer onPageChange) {
        this.onPageChange = onPageChange;
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BUTTON);
    }

    /**
     * Builds the slot window: previous arrow, a bounded run of page numbers
     * (with ellipses when the count exceeds {@link #MAX_SLOTS}) and a next
     * arrow. Positions are assigned left to right.
     */
    private void buildSlots() {
        List<Integer> pages = pageWindow();
        List<Slot> built = new ArrayList<>(pages.size() + 2);
        float x = 0.0f;
        built.add(new Slot(Kind.PREV, currentPage - 1, x, BUTTON_SIZE));
        x += BUTTON_SIZE + GAP;
        for (int page : pages) {
            built.add(new Slot(page == ELLIPSIS ? Kind.ELLIPSIS : Kind.PAGE, page,
                    x, BUTTON_SIZE));
            x += BUTTON_SIZE + GAP;
        }
        built.add(new Slot(Kind.NEXT, currentPage + 1, x, BUTTON_SIZE));
        slots = built;
    }

    /** The page numbers to show, using {@link #ELLIPSIS} for a skipped run. */
    private List<Integer> pageWindow() {
        List<Integer> pages = new ArrayList<>();
        if (pageCount <= MAX_SLOTS) {
            for (int i = 0; i < pageCount; i++) {
                pages.add(i);
            }
            return pages;
        }
        // Always show first and last; keep the current page in a small window.
        int inner = MAX_SLOTS - 4;
        int start = Math.max(1, currentPage - inner / 2);
        int end = Math.min(pageCount - 2, start + inner - 1);
        start = Math.max(1, end - inner + 1);

        pages.add(0);
        if (start > 1) {
            pages.add(ELLIPSIS);
        }
        for (int i = start; i <= end; i++) {
            pages.add(i);
        }
        if (end < pageCount - 2) {
            pages.add(ELLIPSIS);
        }
        pages.add(pageCount - 1);
        return pages;
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        buildSlots();
        if (slots.isEmpty()) {
            return 0.0f;
        }
        Slot last = slots.get(slots.size() - 1);
        return last.x + last.width;
    }

    @Override
    public float getPreferredHeight() {
        return sizeExplicitlySet ? height : BUTTON_SIZE;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        Style s = getComputedStyle();
        boolean cssSized = s != null
                && (s.has(StyleProperty.WIDTH) || s.has(StyleProperty.HEIGHT));
        if (sizeExplicitlySet || cssSized) {
            return super.measure(maxWidth, maxHeight);
        }
        return new Dimension(getPreferredWidth(), getPreferredHeight());
    }

    /**
     * @param localX an x-coordinate in the widget's local space
     * @return the index of the slot containing that position, or -1
     */
    private int slotAt(float localX) {
        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (localX >= slot.x() && localX < slot.x() + slot.width()) {
                return i;
            }
        }
        return -1;
    }

    private boolean isEnabledSlot(int index) {
        if (index < 0 || index >= slots.size()) {
            return false;
        }
        Slot slot = slots.get(index);
        return switch (slot.kind()) {
            case PREV -> currentPage > 0;
            case NEXT -> currentPage < pageCount - 1;
            case PAGE -> true;
            case ELLIPSIS -> false;
        };
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();
        buildSlots();

        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float radius = getTheme().getControlCornerRadius();

        for (int i = 0; i < slots.size(); i++) {
            Slot slot = slots.get(i);
            if (slot.kind() == Kind.ELLIPSIS) {
                textPaint.setColor(getTheme().getControlTextDisabledColor());
                drawCentered(canvas, "…", slot, line, textPaint, f);
                continue;
            }

            boolean active = slot.kind() == Kind.PAGE && slot.page() == currentPage;
            if (active) {
                bgPaint.setColor(getTheme().getAccentColor());
                canvas.drawRRect(slot.x(), 0, slot.width(), BUTTON_SIZE,
                        radius, radius, bgPaint);
            } else if (i == hoveredSlot && isEnabledSlot(i)) {
                hoverPaint.setColor(getTheme().getControlHoverColor());
                canvas.drawRRect(slot.x(), 0, slot.width(), BUTTON_SIZE,
                        radius, radius, hoverPaint);
            }

            int color = active
                    ? 0xFFFFFFFF
                    : (enabled ? getTheme().getControlTextColor()
                            : getTheme().getControlTextDisabledColor());
            if (!isEnabledSlot(i) && !active) {
                color = getTheme().getControlTextDisabledColor();
            }
            if (slot.kind() == Kind.PAGE) {
                textPaint.setColor(color);
                drawCentered(canvas, Integer.toString(slot.page() + 1), slot, line,
                        textPaint, f);
            } else {
                arrowPaint.setColor(color);
                drawArrow(canvas, slot, slot.kind() == Kind.PREV);
            }
        }
    }

    private void drawCentered(Canvas canvas, String label, Slot slot, float line,
            Paint paint, Font f) {
        float textWidth = canvas.measureText(label, f);
        float baseline = (BUTTON_SIZE - line) / 2.0f - f.getMetrics().getAscent();
        canvas.drawString(label, slot.x() + (slot.width() - textWidth) / 2.0f,
                baseline, paint, f);
    }

    private void drawArrow(Canvas canvas, Slot slot, boolean pointsLeft) {
        float cx = slot.x() + slot.width() / 2.0f;
        float cy = BUTTON_SIZE / 2.0f;
        float arm = 3.5f;
        if (pointsLeft) {
            canvas.drawLine(cx + arm, cy - arm, cx - arm, cy, arrowPaint);
            canvas.drawLine(cx - arm, cy, cx + arm, cy + arm, arrowPaint);
        } else {
            canvas.drawLine(cx - arm, cy - arm, cx + arm, cy, arrowPaint);
            canvas.drawLine(cx + arm, cy, cx - arm, cy + arm, arrowPaint);
        }
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (hoverPaint == null || hoverPaint.isClosed()) {
            hoverPaint = new Paint();
            hoverPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (arrowPaint == null || arrowPaint.isClosed()) {
            arrowPaint = new Paint();
            arrowPaint.setAntiAlias(true);
            arrowPaint.setMode(PaintMode.STROKE);
            arrowPaint.setStrokeWidth(1.5f);
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
                int index = isInside ? slotAt(localX) : -1;
                if (index != hoveredSlot) {
                    hoveredSlot = index;
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
                    activate(slotAt(localX));
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
        switch (event.getKeyCode()) {
            case GlyphKeys.LEFT, GlyphKeys.PAGE_UP -> goTo(currentPage - 1);
            case GlyphKeys.RIGHT, GlyphKeys.PAGE_DOWN -> goTo(currentPage + 1);
            case GlyphKeys.HOME -> goTo(0);
            case GlyphKeys.END -> goTo(pageCount - 1);
            default -> {
            }
        }
    }

    /** Activates a slot: an arrow steps one page, a page jumps to it. */
    private void activate(int index) {
        if (!isEnabledSlot(index)) {
            return;
        }
        Slot slot = slots.get(index);
        switch (slot.kind()) {
            case PREV -> goTo(currentPage - 1);
            case NEXT -> goTo(currentPage + 1);
            case PAGE -> goTo(slot.page());
            case ELLIPSIS -> {
            }
        }
    }

    /** Moves to a page, clamped into range, and notifies the handler. */
    private void goTo(int page) {
        int clamped = Math.max(0, Math.min(pageCount - 1, page));
        if (clamped == currentPage) {
            return;
        }
        setCurrentPage(clamped);
        if (onPageChange != null) {
            try {
                onPageChange.accept(clamped);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Pagination change handler threw", e);
            }
        }
    }

    @Override
    protected void onDispose() {
        closeQuietly(bgPaint);
        bgPaint = null;
        closeQuietly(hoverPaint);
        hoverPaint = null;
        closeQuietly(textPaint);
        textPaint = null;
        closeQuietly(arrowPaint);
        arrowPaint = null;
    }

    private static void closeQuietly(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
