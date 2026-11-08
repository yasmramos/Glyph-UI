package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.AppContext;
import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.core.Window;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;

import java.util.ArrayList;
import java.util.List;

/**
 * A custom, undecorated-window title bar — the piece that lets an application
 * put its chrome (a menu bar, window controls, extra widgets) directly in the
 * window's title strip, the way FlatLaf's title pane does.
 *
 * <p>Pair it with an undecorated window
 * ({@code new Window(...).decorated(false)}) so the native title bar is
 * removed and this component becomes the window chrome. The bar
 * owns three window controls (minimize / maximize-restore / close), drawn as
 * crisp vector icons, and supports dragging on its empty area to move the
 * window plus a double-click to toggle maximize.</p>
 *
 * <p><strong>Zone layout.</strong> Following FlatLaf's title pane, the strip is
 * split into three zones rather than a single left-to-right flow:</p>
 *
 * <ul>
 *   <li><b>Leading zone</b> (left) — the embedded {@link MenuBar}, when one is
 *       set with {@link #setMenuBar(MenuBar)}, followed by any widgets added
 *       with {@link #addContent(Component)}. The menus are the leading content
 *       of the strip, exactly as a native menu bar sits at the window's left.</li>
 *   <li><b>Center zone</b> — the window title, centred in the space left
 *       between the leading zone and the controls (and centred within the whole
 *       bar when there is room, so it reads as a window title rather than a
 *       label glued to the menus).</li>
 *   <li><b>Trailing zone</b> (right) — the minimize / maximize / close
 *       controls, pinned to the right edge.</li>
 * </ul>
 *
 * <p>An embedded menu bar is set to {@link MenuBar#setEmbedded(boolean)
 * embedded} mode: its own fill and border are dropped and its triggers become
 * flat, so the menus blend into the title strip. The bar lays its zones out
 * manually ({@link #doLayout()}) to stay self-sizing without depending on
 * nested auto-measuring panels.</p>
 *
 * <p>Use it as the first child of a top-level {@link Panel} whose layout
 * stretches children to the full width (e.g. a {@link FlexLayout} row/column
 * with {@link FlexLayout.Align#STRETCH}); the bar reports a fixed preferred
 * height so it reads as a thin chrome strip above the content. {@link Form}
 * installs and wires it automatically for an undecorated window.</p>
 */
public class TitleBar extends Panel {

    /** Height of the bar (logical units). */
    public static final float BAR_HEIGHT = 40.0f;

    /** Width of each window-control button. */
    public static final float CONTROL_WIDTH = 48.0f;

    /** Left inset before the leading zone. */
    private static final float PAD_LEFT = 12.0f;

    /** Right inset between the title and the control cluster. */
    private static final float PAD_TRAILING = 8.0f;

    /** Default gap between adjacent leading items (menu bar, extra widgets). */
    private static final float DEFAULT_ITEM_GAP = 8.0f;

    /** Two presses closer than this (nanos) count as a double-click. */
    private static final long DOUBLE_CLICK_NANOS = 300_000_000L;

    /** Close-button hover fill (matches the Windows close accent). */
    private static final int CLOSE_HOVER = 0xFFE81123;

    /** Close-button pressed fill (a lighter red, as on Windows). */
    private static final int CLOSE_PRESSED = 0xFFF1707A;

    private final Label titleLabel;
    private final List<Component> content = new ArrayList<>();

    /** Optional embedded menu bar occupying the leading zone; null when none. */
    private MenuBar menuBar;

    /** Gap between adjacent leading items (menu bar, extra widgets). */
    private float itemGap = DEFAULT_ITEM_GAP;
    private final ControlButton minimizeButton;
    private final ControlButton maximizeButton;
    private final ControlButton closeButton;

    private boolean dragging;
    private float grabX;
    private float grabY;
    private long lastPressNanos;

    /**
     * Creates a title bar with the given window title.
     *
     * @param title the window title text (null renders as empty)
     */
    public TitleBar(String title) {
        super(0, 0); // size comes from measure()/doLayout(), not explicit bounds
        titleLabel = new Label(title == null ? "" : title);
        super.add(titleLabel);

        minimizeButton = new ControlButton(ControlKind.MINIMIZE);
        minimizeButton.setAction(() -> {
            Window w = window();
            if (w != null) {
                w.minimize();
            }
        });

        maximizeButton = new ControlButton(ControlKind.MAXIMIZE);
        maximizeButton.setAction(() -> {
            Window w = window();
            if (w != null) {
                w.setMaximized(!w.isMaximized());
                maximizeButton.requestRepaint();
            }
        });

        closeButton = new ControlButton(ControlKind.CLOSE);
        closeButton.setAction(() -> {
            Window w = window();
            if (w != null) {
                w.requestClose();
            }
        });

        super.add(minimizeButton);
        super.add(maximizeButton);
        super.add(closeButton);

        // Seed the chrome color immediately (render() keeps it in sync with
        // the live theme).
        setBackgroundColor(Theme.current().getTitleBarBackgroundColor());
    }

    // ------------------------------------------------------------------
    // Window access
    // ------------------------------------------------------------------
    /**
     * Resolves the native window through an application context: the context
     * of the owning tree when available (ownership wins in multi-app
     * processes), otherwise the documented {@link AppContext} fallback for
     * detached bars (tests, manually assembled trees). Returns null when no
     * application is live.
     */
    private static Window resolveWindow(AppContext ctx) {
        return ctx != null ? ctx.app().getWindow() : null;
    }

    private Window window() {
        // Resolve by ownership first (this bar's tree), then the documented
        // AppContext fallback for detached title bars (tests, manual trees).
        return resolveWindow(context() != null ? context() : AppContext.ofCurrent());
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Updates the window title shown in the bar's centre zone.
     *
     * @param title the new title text (null renders as empty)
     */
    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
        markLayoutDirty();
        invalidate();
    }

    /**
     * The embedded menu bar shown in the leading zone, or {@code null} when
     * none has been installed.
     *
     * @return the embedded menu bar, or null
     */
    public MenuBar getMenuBar() {
        return menuBar;
    }

    /**
     * Installs a menu bar in the bar's leading zone, replacing any previous one
     * (pass {@code null} to remove it). The menu bar is switched to
     * {@link MenuBar#setEmbedded(boolean) embedded} mode so its chrome blends
     * into the title strip, and is stretched to the strip height so its
     * triggers' hover highlight spans the whole bar.
     *
     * @param menuBar the menu bar to embed, or null to remove it
     */
    public void setMenuBar(MenuBar menuBar) {
        if (this.menuBar == menuBar) {
            return;
        }
        if (this.menuBar != null) {
            this.menuBar.setEmbedded(false);
            super.remove(this.menuBar);
        }
        this.menuBar = menuBar;
        if (menuBar != null) {
            menuBar.setEmbedded(true);
            super.add(menuBar);
        }
        markLayoutDirty();
        invalidate();
    }

    /**
     * Adds an application widget into the bar's leading zone, placed after the
     * embedded menu bar and before the centred title. Use it for a leading
     * icon, a back button or any extra chrome; the menu bar itself goes through
     * {@link #setMenuBar(MenuBar)}.
     *
     * @param component the widget to add (ignored when null or already present)
     */
    public void addContent(Component component) {
        if (component == null || content.contains(component)) {
            return;
        }
        content.add(component);
        super.add(component);
        markLayoutDirty();
        invalidate();
    }

    /**
     * Removes a previously added leading-zone widget.
     *
     * @param component the widget to remove (no-op if not present)
     */
    public void removeContent(Component component) {
        if (content.remove(component)) {
            super.remove(component);
            markLayoutDirty();
            invalidate();
        }
    }

    /**
     * Returns the horizontal gap (logical units) between adjacent leading-zone
     * items (the embedded menu bar and the widgets added with
     * {@link #addContent(Component)}).
     *
     * @return the current item gap
     */
    public float getItemGap() {
        return itemGap;
    }

    /**
     * Sets the horizontal gap between adjacent leading-zone items. Use
     * {@code 0} for a native-menubar look where the items sit flush against
     * each other; negative values are clamped to {@code 0}. Takes effect on the
     * next layout pass.
     *
     * @param itemGap the gap in logical units (clamped to &ge; 0)
     */
    public void setItemGap(float itemGap) {
        this.itemGap = Math.max(0.0f, itemGap);
        markLayoutDirty();
        invalidate();
    }

    // ------------------------------------------------------------------
    // Sizing / layout
    // ------------------------------------------------------------------
    @Override
    public float getPreferredWidth() {
        float total = PAD_LEFT + leadingWidth();
        total += titleLabel.getPreferredWidth();
        total += PAD_TRAILING;
        total += CONTROL_WIDTH * 3;
        return Math.max(total, BAR_HEIGHT);
    }

    @Override
    public float getPreferredHeight() {
        return BAR_HEIGHT;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        // Width is normally forced by the parent's STRETCH alignment; height
        // is the fixed chrome strip height.
        float w = Math.min(getPreferredWidth(), sanitizeConstraint(maxWidth));
        return new Dimension(w, BAR_HEIGHT);
    }

    /** Total width of the leading zone (menu bar plus extra widgets). */
    private float leadingWidth() {
        float width = 0.0f;
        if (menuBar != null) {
            width = menuBar.getPreferredWidth();
        }
        for (Component c : content) {
            width += (width > 0.0f ? itemGap : 0.0f) + c.getPreferredWidth();
        }
        return width;
    }

    /**
     * Positions the bar's children in three zones, FlatLaf-style: the menu bar
     * and extra widgets at the leading (left) edge, the title centred, and the
     * three window controls pinned to the trailing (right) edge (close
     * rightmost). Called every frame from render().
     */
    @Override
    public void doLayout() {
        if (!visible) {
            return;
        }
        float barWidth = width;
        float barHeight = height;

        // Trailing zone: controls, right to left (close | maximize | minimize).
        float right = barWidth;
        right -= CONTROL_WIDTH;
        placeControl(closeButton, right, barHeight);
        right -= CONTROL_WIDTH;
        placeControl(maximizeButton, right, barHeight);
        right -= CONTROL_WIDTH;
        placeControl(minimizeButton, right, barHeight);
        float trailingStart = right;

        // Leading zone: menu bar first, then extra widgets, each filling the
        // bar height so a menu trigger's hover highlight spans the full strip.
        float left = PAD_LEFT;
        if (menuBar != null) {
            float menuWidth = menuBar.getPreferredWidth();
            menuBar.setX(left);
            menuBar.setY(0);
            menuBar.setWidth(menuWidth);
            menuBar.setHeight(barHeight);
            left += menuWidth;
        }
        for (Component c : content) {
            Dimension d = c.measure(Float.MAX_VALUE, Float.MAX_VALUE);
            float cw = Math.max(0, d.width);
            if (left > PAD_LEFT) {
                left += itemGap;
            }
            c.setX(left);
            c.setY(0);
            c.setWidth(cw);
            c.setHeight(barHeight);
            left += cw;
        }
        float leadingEnd = left;

        // Centre zone: the title, centred in the space left between the leading
        // zone and the controls. When the whole bar has room, centre it against
        // the full width so it reads as a window title (FlatLaf's behaviour).
        Dimension td = titleLabel.measure(Float.MAX_VALUE, Float.MAX_VALUE);
        float titleW = Math.max(0, td.width);
        float freeStart = leadingEnd + PAD_TRAILING;
        float freeEnd = trailingStart - PAD_TRAILING;
        float freeWidth = Math.max(0.0f, freeEnd - freeStart);

        float titleX;
        float windowCenteredX = (barWidth - titleW) / 2f;
        if (titleW <= freeWidth && windowCenteredX >= freeStart && windowCenteredX + titleW <= freeEnd) {
            titleX = windowCenteredX;
        } else {
            titleX = freeStart + Math.max(0.0f, (freeWidth - titleW) / 2f);
        }
        float clippedW = Math.min(titleW, Math.max(0.0f, freeEnd - titleX));
        titleLabel.setX(titleX);
        titleLabel.setY((barHeight - Math.max(0, td.height)) / 2f);
        titleLabel.setWidth(clippedW);
        titleLabel.setHeight(Math.max(0, td.height));
    }

    private void placeControl(ControlButton button, float left, float barHeight) {
        button.setX(left);
        button.setY(0);
        button.setWidth(CONTROL_WIDTH);
        button.setHeight(barHeight);
    }

    @Override
    protected void render(Canvas canvas) {
        // Keep the chrome colour in step with the active theme (direct field
        // assignment avoids a repaint request every frame). Visibility and
        // canvas isolation are guaranteed by Component.renderInternal().
        this.backgroundColor = getTheme().getTitleBarBackgroundColor();
        super.render(canvas);
    }

    // ------------------------------------------------------------------
    // Interaction: drag-to-move, double-click to maximize
    // ------------------------------------------------------------------
    @Override
    public boolean onMouseEvent(MouseEvent event) {
        // Once a window drag is under way, this bar owns the gesture even if
        // the pointer drifts over the controls or content.
        if (dragging) {
            if (event.getType() == MouseEventType.DRAG) {
                moveWindow(event);
                return true;
            }
            if (event.getType() == MouseEventType.RELEASE) {
                dragging = false;
                return true;
            }
        }

        // Give children (menu bar, title, content, controls) first refusal.
        boolean consumed = super.onMouseEvent(event);
        if (consumed) {
            if (event.getType() == MouseEventType.PRESS) {
                dragging = false;
            }
            return true;
        }

        // Only the bar's own empty chrome is handled below.
        Window win = window();
        if (win == null || !contains(event.getX(), event.getY())) {
            return false;
        }
        if (event.getType() == MouseEventType.PRESS && event.getButton() == MouseButton.LEFT) {
            long now = System.nanoTime();
            boolean doubleClick = (now - lastPressNanos) <= DOUBLE_CLICK_NANOS;
            lastPressNanos = now;
            if (doubleClick) {
                win.setMaximized(!win.isMaximized());
                maximizeButton.requestRepaint();
                return true;
            }
            dragging = true;
            grabX = event.getX();
            grabY = event.getY();
            return true;
        }
        return false;
    }

    /**
     * Moves the window so the grabbed point on the bar stays under the cursor.
     * The cursor's window-relative position shifts as the window moves, so the
     * new position is the current window position plus the delta from the
     * grab point (both in the same logical units).
     */
    private void moveWindow(MouseEvent event) {
        Window win = window();
        if (win == null) {
            return;
        }
        int px = win.getPositionX();
        int py = win.getPositionY();
        win.setPosition(
                Math.round(px + (event.getX() - grabX)),
                Math.round(py + (event.getY() - grabY)));
    }

    private static float sanitizeConstraint(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value) || value <= 0) {
            return Float.MAX_VALUE;
        }
        return value;
    }

    // ------------------------------------------------------------------
    // Window-control button (minimize / maximize-restore / close)
    // ------------------------------------------------------------------
    private enum ControlKind {
        MINIMIZE, MAXIMIZE, CLOSE
    }

    /** A title-bar window control that draws a vector icon instead of text. */
    private static final class ControlButton extends Component {

        private final ControlKind kind;
        private final Paint bgPaint = new Paint();
        private final Paint iconPaint = new Paint();
        private Runnable action;

        ControlButton(ControlKind kind) {
            super(0, 0, CONTROL_WIDTH, BAR_HEIGHT);
            this.kind = kind;
            bgPaint.setAntiAlias(true);
            iconPaint.setMode(PaintMode.STROKE);
            iconPaint.setStrokeWidth(1.4f);
            iconPaint.setAntiAlias(true);
        }

        void setAction(Runnable action) {
            this.action = action;
        }

        @Override
        protected void render(Canvas canvas) {
            // Local coordinates: Component.renderInternal() translated the
            // origin to this button's top-left corner already.
            int bg = backgroundForState();
            if ((bg >>> 24) != 0) {
                bgPaint.setColor(bg);
                canvas.drawRect(0, 0, width, height, bgPaint);
            }
            iconPaint.setColor(iconColor());
            drawIcon(canvas);
        }

        private int backgroundForState() {
            if (state == ComponentState.PRESSED) {
                return kind == ControlKind.CLOSE
                        ? CLOSE_PRESSED : getTheme().getControlPressedColor();
            }
            if (state == ComponentState.HOVER) {
                return kind == ControlKind.CLOSE
                        ? CLOSE_HOVER : getTheme().getControlHoverColor();
            }
            return 0; // transparent normal state
        }

        private int iconColor() {
            if (kind == ControlKind.CLOSE
                    && (state == ComponentState.HOVER || state == ComponentState.PRESSED)) {
                return 0xFFFFFFFF; // white glyph on the red hover fill
            }
            return getTheme().getControlTextColor();
        }

        private void drawIcon(Canvas canvas) {
            float cx = width / 2f;
            float cy = height / 2f;
            switch (kind) {
                case MINIMIZE -> canvas.drawLine(cx - 5, cy, cx + 5, cy, iconPaint);
                case MAXIMIZE -> {
                    AppContext ctx = context() != null ? context() : AppContext.ofCurrent();
                    Window w = resolveWindow(ctx);
                    if (w != null && w.isMaximized()) {
                        // Restore: two overlapping squares.
                        canvas.drawRect(cx - 1, cy - 6, 9, 9, iconPaint);
                        canvas.drawRect(cx - 6, cy - 1, 9, 9, iconPaint);
                    } else {
                        canvas.drawRect(cx - 5, cy - 5, 10, 10, iconPaint);
                    }
                }
                case CLOSE -> {
                    canvas.drawLine(cx - 5, cy - 5, cx + 5, cy + 5, iconPaint);
                    canvas.drawLine(cx + 5, cy - 5, cx - 5, cy + 5, iconPaint);
                }
            }
        }

        @Override
        public boolean onMouseEvent(MouseEvent event) {
            if (!visible || !enabled) {
                return false;
            }
            boolean inside = contains(event.getX(), event.getY());
            ComponentState before = state;
            switch (event.getType()) {
                case MOVE, DRAG ->
                        setState(inside
                                ? (state == ComponentState.PRESSED ? ComponentState.PRESSED
                                        : ComponentState.HOVER)
                                : ComponentState.IDLE);
                case PRESS -> {
                    if (inside && event.getButton() == MouseButton.LEFT) {
                        setState(ComponentState.PRESSED);
                    }
                }
                case RELEASE -> {
                    if (state == ComponentState.PRESSED && inside && action != null) {
                        action.run();
                    }
                    setState(inside ? ComponentState.HOVER : ComponentState.IDLE);
                }
                default -> {
                    // no-op
                }
            }
            if (state != before) {
                requestRepaint();
            }
            return inside;
        }

        @Override
        public void onKeyEvent(KeyEvent event) {
            // Window controls are mouse-only; no keyboard handling.
        }
    }
}
