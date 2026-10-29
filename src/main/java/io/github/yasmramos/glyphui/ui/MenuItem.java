package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.graphics.Theme;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A single selectable row inside a {@link Menu}.
 *
 * <p>A menu item renders, left to right: a leading gutter (a check mark when
 * the item is {@linkplain #setCheckable(boolean) checkable} and checked,
 * otherwise the {@linkplain #setIcon(Image) icon} when one is set), the label,
 * and an optional right-aligned accelerator text (e.g. {@code Ctrl+S}). Rows
 * keep their text at a fixed offset so mixed icon/non-icon rows stay aligned,
 * the way native menu bars do.</p>
 *
 * <p>Interaction mirrors {@link Button}: hovering highlights the row, pressing
 * and releasing inside it activates the {@link ClickListener}. Activation
 * toggles the checked state of a checkable item first, then runs the handler,
 * and finally closes the owning {@link Menu}. Disabled rows ignore the
 * pointer entirely.</p>
 *
 * <p>Like every toolkit widget, no native resources are allocated in the
 * constructor: paints are created lazily on the first
 * {@link #render(Canvas)} call so the widget stays unit-testable without
 * Skija.</p>
 */
public class MenuItem extends Component {

    private static final Logger LOG = Logger.getLogger(MenuItem.class.getName());

    /** Left/right inset of the row content. */
    static final float H_PADDING = 10.0f;

    /** Width of the leading gutter (check mark or icon). */
    static final float GUTTER = 16.0f;

    /** Gap between the gutter and the label. */
    static final float GUTTER_GAP = 8.0f;

    /** Vertical inset above and below the row text. */
    static final float V_PADDING = 5.0f;

    /** Minimum gap between the label and the accelerator text. */
    static final float ACCEL_GAP = 24.0f;

    /** Stroke width of the check mark. */
    private static final float CHECK_STROKE = 1.8f;

    private String text;
    private Image icon;
    private String acceleratorText;
    private boolean checkable;
    private boolean checked;
    private ClickListener onClick;

    // Reusable paints, created lazily on first render (never in the ctor).
    private Paint rowPaint;
    private Paint textPaint;
    private Paint checkPaint;

    /**
     * Creates a menu item with the given label.
     *
     * @param text the row label (null renders as empty)
     */
    public MenuItem(String text) {
        this(text, null);
    }

    /**
     * Creates a menu item with the given label and leading icon.
     *
     * @param text the row label (null renders as empty)
     * @param icon the leading icon, or null for none
     */
    public MenuItem(String text, Image icon) {
        // No explicit bounds: the row sizes itself from its content, like
        // Button(String) does, so the menu can measure its rows.
        super();
        this.text = text;
        this.icon = icon;
        // Menu rows never take the keyboard focus (there is no keyboard
        // navigation in this iteration), so Tab traversal skips them.
        setFocusable(false);
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.MENU_ITEM;
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    /**
     * Gets the row label.
     *
     * @return the label (may be null)
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the row label.
     *
     * @param text the new label (null renders as empty)
     */
    public void setText(String text) {
        this.text = text;
        requestRepaint();
    }

    /**
     * Gets the leading icon.
     *
     * @return the icon, or null when the row has none
     */
    public Image getIcon() {
        return icon;
    }

    /**
     * Sets the leading icon drawn in the row's gutter. Has no visual effect
     * on a checked checkable row, which shows its check mark instead.
     *
     * @param icon the icon, or null to clear it
     */
    public void setIcon(Image icon) {
        this.icon = icon;
        requestRepaint();
    }

    /**
     * Gets the right-aligned accelerator text.
     *
     * @return the accelerator text (may be null)
     */
    public String getAcceleratorText() {
        return acceleratorText;
    }

    /**
     * Sets the right-aligned accelerator text, e.g. {@code "Ctrl+S"}.
     *
     * @param acceleratorText the accelerator text (null hides it)
     */
    public void setAcceleratorText(String acceleratorText) {
        this.acceleratorText = acceleratorText;
        requestRepaint();
    }

    /**
     * Whether this row shows a check mark when selected (a checkbox-style
     * menu option such as "Show status bar").
     *
     * @return true when the row is checkable
     */
    public boolean isCheckable() {
        return checkable;
    }

    /**
     * Makes this row checkable. A checkable row toggles its checked state on
     * every activation, before the click handler runs.
     *
     * @param checkable true to show/track a check mark
     */
    public void setCheckable(boolean checkable) {
        this.checkable = checkable;
        requestRepaint();
    }

    /**
     * Whether a checkable row is currently checked.
     *
     * @return true when checked (always false for non-checkable rows)
     */
    public boolean isChecked() {
        return checked;
    }

    /**
     * Sets the checked state of a checkable row.
     *
     * @param checked the new state
     */
    public void setChecked(boolean checked) {
        if (this.checked != checked) {
            this.checked = checked;
            requestRepaint();
        }
    }

    /**
     * Gets the activation handler.
     *
     * @return the click listener, or null when none is registered
     */
    public ClickListener getOnClick() {
        return onClick;
    }

    /**
     * Sets the handler invoked when the row is activated. The owning menu
     * closes after the handler runs (even when the handler throws).
     *
     * @param onClick the click listener (null clears it)
     */
    public void setOnClick(ClickListener onClick) {
        this.onClick = onClick;
    }

    // ------------------------------------------------------------------
    // Sizing
    // ------------------------------------------------------------------

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).getWidth();
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY).getHeight();
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        // Explicit bounds (bounds constructor / setWidth) win over the
        // text-based intrinsic size, mirroring Button.measure().
        if (sizeExplicitlySet) {
            return super.measure(maxWidth, maxHeight);
        }

        io.github.humbleui.skija.Font f = resolveFont(Theme.FontRole.BODY);
        float textWidth = f.measureTextWidth(text == null ? "" : text);
        float accelWidth = f.measureTextWidth(acceleratorText == null ? "" : acceleratorText);

        float width = H_PADDING + GUTTER + GUTTER_GAP + textWidth;
        if (acceleratorText != null && !acceleratorText.isEmpty()) {
            width += ACCEL_GAP + accelWidth;
        }
        width += H_PADDING;

        float lineHeight = f.getMetrics().getDescent() - f.getMetrics().getAscent();
        float height = lineHeight + 2.0f * V_PADDING;

        return new Dimension(Math.min(width, sanitizeConstraint(maxWidth)),
                Math.min(height, sanitizeConstraint(maxHeight)));
    }

    /**
     * Treats NaN/infinite constraints as "unbounded" so the clamp above still
     * yields the preferred size (same contract as the other widgets).
     *
     * @param value the raw constraint
     * @return a finite upper bound
     */
    private static float sanitizeConstraint(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return Float.MAX_VALUE;
        }
        return Math.max(0.0f, value);
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    /** Ensures the reusable paint objects exist (lazy, render-time creation). */
    private void ensurePaints() {
        if (rowPaint == null) {
            rowPaint = new Paint();
            rowPaint.setAntiAlias(true);
        }
        if (textPaint == null) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (checkPaint == null) {
            checkPaint = new Paint();
            checkPaint.setAntiAlias(true);
            checkPaint.setMode(PaintMode.STROKE);
            checkPaint.setStrokeWidth(CHECK_STROKE);
            checkPaint.setStrokeCap(io.github.humbleui.skija.PaintStrokeCap.ROUND);
        }
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();

        Theme theme = getTheme();

        // Row highlight: only enabled rows react to the pointer, and a
        // pressed row is a shade darker than a hovered one.
        if (enabled && (state == ComponentState.HOVER || state == ComponentState.PRESSED)) {
            rowPaint.setColor(state == ComponentState.PRESSED
                    ? theme.getControlPressedColor() : theme.getControlHoverColor());
            canvas.drawRect(0, 0, width, height, rowPaint);
        }

        io.github.humbleui.skija.Font f = resolveFont(Theme.FontRole.BODY);
        io.github.humbleui.skija.FontMetrics metrics = f.getMetrics();
        float textHeight = metrics.getDescent() - metrics.getAscent();
        // drawString takes a baseline; center the glyph box in the row.
        float baseline = (height - textHeight) / 2.0f - metrics.getAscent();

        // Leading gutter: the check mark wins over the icon so a checked
        // option never shows two markers at once.
        float gutterX = H_PADDING;
        float centerY = height / 2.0f;
        if (checkable && checked) {
            checkPaint.setColor(theme.getAccentColor());
            canvas.drawLine(gutterX + 2.5f, centerY,
                    gutterX + 6.5f, centerY + 4.0f, checkPaint);
            canvas.drawLine(gutterX + 6.5f, centerY + 4.0f,
                    gutterX + 13.5f, centerY - 4.0f, checkPaint);
        } else if (icon != null) {
            float iconY = (height - GUTTER) / 2.0f;
            canvas.drawImage(icon, gutterX, iconY, GUTTER, GUTTER);
        }

        // Label. Disabled rows use the theme's muted control text colour.
        textPaint.setColor(enabled
                ? theme.getControlTextColor() : theme.getControlTextDisabledColor());
        float textX = H_PADDING + GUTTER + GUTTER_GAP;
        canvas.drawString(text == null ? "" : text, textX, baseline, textPaint, f);

        // Accelerator, right-aligned and always muted.
        if (acceleratorText != null && !acceleratorText.isEmpty()) {
            float accelWidth = canvas.measureText(acceleratorText, f);
            textPaint.setColor(theme.getControlTextDisabledColor());
            canvas.drawString(acceleratorText,
                    width - H_PADDING - accelWidth, baseline, textPaint, f);
        }
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
        if (event.getType() == MouseEventType.MOVE
                || event.getType() == MouseEventType.DRAG) {
            ComponentState next = inside ? ComponentState.HOVER : ComponentState.IDLE;
            if (state != next && state != ComponentState.PRESSED) {
                setState(next);
                requestRepaint();
            }
        } else if (event.getType() == MouseEventType.PRESS) {
            if (inside && event.getButton() == MouseButton.LEFT) {
                setState(ComponentState.PRESSED);
                requestRepaint();
            }
        } else if (event.getType() == MouseEventType.RELEASE) {
            if (inside && state == ComponentState.PRESSED) {
                setState(ComponentState.HOVER);
                requestRepaint();
                runClick();
            }
        }
        return inside;
    }

    /**
     * Activates this row the same way a pointer pick does: toggles the
     * checked state (checkable rows only), runs the click handler and closes
     * the owning menu. Used by keyboard navigation ({@link Menu} Enter/Space)
     * and safe to call directly.
     *
     * @return true when the row was activated; false when it is disabled
     */
    public boolean fire() {
        if (!enabled) {
            return false;
        }
        runClick();
        return true;
    }

    /**
     * Runs the activation sequence: toggle the checked state (checkable rows
     * only), invoke the handler, then close the owning menu. A throwing
     * handler is logged and never prevents the menu from closing, so the UI
     * cannot be left with a stale open popup.
     */
    private void runClick() {
        if (checkable) {
            setChecked(!checked);
        }
        if (onClick != null) {
            try {
                onClick.onClick();
            } catch (RuntimeException | Error e) {
                LOG.log(Level.WARNING, "Menu item handler threw; menu still closes", e);
            }
        }
        Menu menu = findMenu();
        if (menu != null) {
            menu.close();
        }
    }

    /**
     * Walks up the parent chain looking for the menu that owns this row.
     *
     * @return the owning menu, or null when the row is not inside one
     */
    private Menu findMenu() {
        for (Component c = parent; c != null; c = c.getParent()) {
            if (c instanceof Menu menu) {
                return menu;
            }
        }
        return null;
    }

    @Override
    public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
        // Rows are not focusable in this iteration, so no key ever reaches
        // them; the hook exists to satisfy the Component contract.
    }
}
