package io.github.yasmramos.glyphui.ui;

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
 * put arbitrary content (a menu bar, buttons, status text, …) directly in the
 * window's title strip, the way FlatLaf's title pane does.
 *
 * <p>Pair it with a {@code WindowConfig.decorated(false)} window so the native
 * title bar is removed and this component becomes the window chrome. The bar
 * owns three window controls (minimize / maximize-restore / close), drawn as
 * crisp vector icons, and supports dragging on its empty area to move the
 * window plus a double-click to toggle maximize.</p>
 *
 * <p>Layout is managed manually ({@link #doLayout()}): the window title sits at
 * the left, application content flows immediately after it, and the control
 * cluster is pinned to the right edge. This keeps the bar self-sizing without
 * depending on nested auto-measuring panels.</p>
 *
 * <p>Use it as the first child of a top-level {@link Panel} whose layout
 * stretches children to the full width (e.g. a {@link FlexLayout} row/column
 * with {@link FlexLayout.Align#STRETCH}); the bar reports a fixed preferred
 * height so it reads as a thin chrome strip above the scrollable content.</p>
 */
public class TitleBar extends Panel {

    /** Height of the bar (logical units). */
    public static final float BAR_HEIGHT = 40.0f;

    /** Width of each window-control button. */
    public static final float CONTROL_WIDTH = 48.0f;

    /** Left inset before the title text. */
    private static final float PAD_LEFT = 12.0f;

    /** Gap between adjacent title-bar items (title, content widgets). */
    private static final float ITEM_GAP = 8.0f;

    /** Two presses closer than this (nanos) count as a double-click. */
    private static final long DOUBLE_CLICK_NANOS = 300_000_000L;

    /** Close-button hover fill (matches the Windows close accent). */
    private static final int CLOSE_HOVER = 0xFFE81123;

    /** Close-button pressed fill (a lighter red, as on Windows). */
    private static final int CLOSE_PRESSED = 0xFFF1707A;

    private final Label titleLabel;
    private final List<Component> content = new ArrayList<>();
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
    private static Window window() {
        Application app = Application.getCurrent();
        return app != null ? app.getWindow() : null;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Updates the window title shown at the left of the bar.
     *
     * @param title the new title text (null renders as empty)
     */
    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
        markLayoutDirty();
        invalidate();
    }

    /**
     * Adds an application widget (e.g. a menu-bar button) into the bar's
     * content area, placed after the title and before the window controls.
     *
     * @param component the widget to add (ignored when null or already present)
     */
    public void addContent(Component component) {
        if (component == null || content.contains(component)) {
            return;
        }
        content.add(component);
        super.add(component);
    }

    /**
     * Removes a previously added content widget.
     *
     * @param component the widget to remove (no-op if not present)
     */
    public void removeContent(Component component) {
        if (content.remove(component)) {
            super.remove(component);
        }
    }

    // ------------------------------------------------------------------
    // Sizing / layout
    // ------------------------------------------------------------------
    @Override
    public float getPreferredWidth() {
        float total = PAD_LEFT + titleLabel.getPreferredWidth();
        for (Component c : content) {
            total += c.getPreferredWidth() + ITEM_GAP;
        }
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

    /**
     * Positions the bar's children: the three window controls pinned to the
     * right edge (close rightmost), the title at the left, and the application
     * content flowing after the title. Called every frame from render().
     */
    @Override
    public void doLayout() {
        if (!visible) {
            return;
        }
        float barWidth = width;
        float barHeight = height;

        // Controls, right to left: close | maximize | minimize.
        float right = barWidth;
        right -= CONTROL_WIDTH;
        placeControl(closeButton, right, barHeight);
        right -= CONTROL_WIDTH;
        placeControl(maximizeButton, right, barHeight);
        right -= CONTROL_WIDTH;
        placeControl(minimizeButton, right, barHeight);

        // Title at the left, vertically centred at its measured height.
        Dimension td = titleLabel.measure(Float.MAX_VALUE, Float.MAX_VALUE);
        float titleW = Math.max(0, td.width);
        titleLabel.setX(PAD_LEFT);
        titleLabel.setY((barHeight - Math.max(0, td.height)) / 2f);
        titleLabel.setWidth(titleW);
        titleLabel.setHeight(Math.max(0, td.height));

        // Content widgets flow after the title and fill the bar height so their
        // hover highlight spans the full strip (menubar-style).
        float left = PAD_LEFT + titleW + ITEM_GAP;
        for (Component c : content) {
            Dimension d = c.measure(Float.MAX_VALUE, Float.MAX_VALUE);
            float cw = Math.max(0, d.width);
            c.setX(left);
            c.setY(0);
            c.setWidth(cw);
            c.setHeight(barHeight);
            left += cw + ITEM_GAP;
        }
    }

    private void placeControl(ControlButton button, float left, float barHeight) {
        button.setX(left);
        button.setY(0);
        button.setWidth(CONTROL_WIDTH);
        button.setHeight(barHeight);
    }

    @Override
    public void render(Canvas canvas) {
        // Keep the chrome colour in step with the active theme (direct field
        // assignment avoids a repaint request every frame).
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

        // Give children (title, content, controls) first refusal.
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
        public void render(Canvas canvas) {
            int bg = backgroundForState();
            if ((bg >>> 24) != 0) {
                bgPaint.setColor(bg);
                canvas.drawRect(x, y, width, height, bgPaint);
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
            float cx = x + width / 2f;
            float cy = y + height / 2f;
            switch (kind) {
                case MINIMIZE -> canvas.drawLine(cx - 5, cy, cx + 5, cy, iconPaint);
                case MAXIMIZE -> {
                    Window w = window();
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
