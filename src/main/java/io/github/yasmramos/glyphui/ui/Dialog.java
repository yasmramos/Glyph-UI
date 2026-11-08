package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.AppContext;
import io.github.yasmramos.glyphui.core.Application;
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
import io.github.humbleui.skija.PaintMode;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A modal message box with a title, a wrapped message and a row of actions.
 *
 * <p>A dialog lives in the application's {@link PopupLayer} (see
 * {@link Application#getPopupLayer()}), where it expands to cover the whole
 * window: the full-bounds area is painted as a translucent scrim and the
 * surface itself is centred on top of it. That is what makes the rest of the
 * page unreachable while the dialog is showing, and what makes "click outside
 * to dismiss" fall out for free. {@link #show()} attaches the dialog to the
 * layer on first use, so typical wiring is just:</p>
 *
 * <pre>{@code
 * Dialog dialog = new Dialog("Delete file?");
 * dialog.setMessage("This cannot be undone.");
 * dialog.addAction("Cancel");
 * dialog.addAction("Delete", () -> delete()).setDefaultAction(true);
 * dialog.setOnClose(() -> System.out.println("dismissed"));
 * dialog.show();
 * }</pre>
 *
 * <p>Escape dismisses the dialog through the layer's own ESC handling, and
 * Enter activates the default action. A {@link #setDismissible(boolean)
 * non-dismissible} dialog (the conventional choice for a destructive
 * confirmation) ignores both the escape key and clicks on the scrim, so the
 * user has to pick an action. Every dismissal path runs
 * {@link #setOnClose(ClickListener) the close handler} exactly once, whether
 * the dialog was closed by an action, by the scrim, or by the layer.</p>
 */
public class Dialog extends Panel {

    /** Logger for action and close-handler failures (never swallowed). */
    private static final Logger LOG = Logger.getLogger(Dialog.class.getName());

    /** Inner padding between the surface edge and its content, logical units. */
    public static final float CONTENT_PADDING = 16.0f;

    /** Gap between the title block and the message block, logical units. */
    public static final float TITLE_GAP = 12.0f;

    /** Gap between the message block and the action row, logical units. */
    public static final float ACTION_GAP = 20.0f;

    /** Height of an action button, logical units. */
    public static final float BUTTON_HEIGHT = 30.0f;

    /** Horizontal padding inside an action button, logical units. */
    public static final float BUTTON_PADDING = 14.0f;

    /** Gap between two action buttons, logical units. */
    public static final float BUTTON_GAP = 8.0f;

    /** Default surface width when nothing forces a wider one, logical units. */
    public static final float DEFAULT_WIDTH = 340.0f;

    /** Minimum surface width, logical units. */
    public static final float MIN_WIDTH = 160.0f;

    /** Alpha of the scrim painted over the page behind the dialog. */
    private static final int SCRIM_COLOR = 0x8C000000;

    /**
     * One footer button: a label, an enabled flag and its handler.
     */
    public static final class Action {

        private final String label;
        private ClickListener onClick;
        private boolean enabled = true;
        private boolean isDefault;

        Action(String label) {
            this.label = label == null ? "" : label;
        }

        /**
         * @return the button label, never null
         */
        public String getLabel() {
            return label;
        }

        /**
         * @return the click handler, or null when none is set
         */
        public ClickListener getOnClick() {
            return onClick;
        }

        /**
         * Sets the handler invoked when the button is pressed.
         *
         * @param onClick the handler, or null to clear it
         * @return this action, for chaining
         */
        public Action setOnClick(ClickListener onClick) {
            this.onClick = onClick;
            return this;
        }

        /**
         * @return true when the button accepts input
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the button. A disabled button is painted muted
         * and ignores clicks.
         *
         * @param enabled the new flag
         * @return this action, for chaining
         */
        public Action setEnabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * @return true when Enter activates this action
         */
        public boolean isDefault() {
            return isDefault;
        }

        /**
         * Marks this action as the default one, which Enter activates. Only
         * one action is the default at a time; marking another clears the
         * previous one.
         *
         * @param isDefault the new flag
         * @return this action, for chaining
         */
        public Action setDefaultAction(boolean isDefault) {
            this.isDefault = isDefault;
            return this;
        }
    }

    private String title;
    private String message;
    private final List<Action> actions = new ArrayList<>();
    private final List<float[]> actionRects = new ArrayList<>();

    private boolean dismissible = true;
    private ClickListener onClose;
    private Property<String> titleProperty;
    private Property<String> messageProperty;

    private Integer scrimColorOverride;
    private Integer surfaceColorOverride;
    private Integer borderColorOverride;
    private Integer textColorOverride;

    private Paint scrimPaint;
    private Paint surfacePaint;
    private Paint borderPaint;
    private Paint titlePaint;
    private Paint messagePaint;

    /** Local x of the surface's left edge, refreshed by {@link #layoutSurface()}. */
    private float surfaceX;
    /** Local y of the surface's top edge, refreshed by {@link #layoutSurface()}. */
    private float surfaceY;
    /** Width of the surface, refreshed by {@link #layoutSurface()}. */
    private float surfaceW;
    /** Height of the surface, refreshed by {@link #layoutSurface()}. */
    private float surfaceH;

    /** Index of the action under the pointer, or {@code -1}. */
    private int hoveredAction = -1;
    /** Index of the action held down by the pointer, or {@code -1}. */
    private int pressedAction = -1;

    /**
     * Creates a dialog with no title and no message.
     */
    public Dialog() {
        this("", "");
    }

    /**
     * Creates a dialog with the supplied title.
     *
     * @param title the title (null is treated as empty, meaning "no header")
     */
    public Dialog(String title) {
        this(title, "");
    }

    /**
     * Creates a dialog with the supplied title and message.
     *
     * @param title   the title (null is treated as empty)
     * @param message the message (null is treated as empty)
     */
    public Dialog(String title, String message) {
        super();
        this.title = title == null ? "" : title;
        this.message = message == null ? "" : message;
        setFocusable(true);
        // A dialog is created hidden and shown explicitly, like a Menu: it is
        // added to the popup layer by show(), not by the constructor.
        setVisible(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "dialog";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.DIALOG;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return title.isEmpty() ? "Dialog" : "Dialog: " + title;
    }

    // ---- Content -----------------------------------------------------------

    /**
     * @return the title, never null (empty means "no header")
     */
    public String getTitle() {
        return title;
    }

    /**
     * Returns the observable title property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the title property (never null after first access)
     */
    public Property<String> titleProperty() {
        if (titleProperty == null) {
            titleProperty = new Property<>(propertyOwner(), title, newTitle -> {
                this.title = newTitle == null ? "" : newTitle;
                invalidateLayout();
                invalidatePaint();
            });
        }
        return titleProperty;
    }

    /**
     * Sets the title. This does not run the close handler.
     *
     * @param title the new title (null is treated as empty)
     */
    public void setTitle(String title) {
        titleProperty().set(title == null ? "" : title);
    }

    /**
     * @return the message, never null
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the observable message property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the message property (never null after first access)
     */
    public Property<String> messageProperty() {
        if (messageProperty == null) {
            messageProperty = new Property<>(propertyOwner(), message, newMessage -> {
                this.message = newMessage == null ? "" : newMessage;
                invalidateLayout();
                invalidatePaint();
            });
        }
        return messageProperty;
    }

    /**
     * Sets the message. Explicit newlines start a new line; long lines are
     * word-wrapped to the surface width when painted.
     *
     * @param message the new message (null is treated as empty)
     */
    public void setMessage(String message) {
        messageProperty().set(message == null ? "" : message);
    }

    // ---- Actions -----------------------------------------------------------

    /**
     * Appends an action button with no handler.
     *
     * @param label the button label (null is treated as empty)
     * @return the new action, for further configuration
     */
    public Action addAction(String label) {
        Action action = new Action(label);
        actions.add(action);
        invalidateLayout();
        return action;
    }

    /**
     * Appends an action button and registers its handler.
     *
     * @param label   the button label (null is treated as empty)
     * @param onClick the handler, or null for none
     * @return the new action, for further configuration
     */
    public Action addAction(String label, ClickListener onClick) {
        return addAction(label).setOnClick(onClick);
    }

    /**
     * Removes every action button.
     */
    public void clearActions() {
        actions.clear();
        hoveredAction = -1;
        pressedAction = -1;
        invalidateLayout();
    }

    /**
     * @return the action buttons, in insertion order
     */
    public List<Action> getActions() {
        return java.util.Collections.unmodifiableList(actions);
    }

    /**
     * @return the index of the default action, or {@code -1} when none is set
     */
    public int getDefaultActionIndex() {
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).isDefault()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Makes one action the default, so Enter activates it. Any previously
     * default action is cleared.
     *
     * @param index the zero-based action index, or a value outside the range
     *              to leave every action non-default
     */
    public void setDefaultActionIndex(int index) {
        for (int i = 0; i < actions.size(); i++) {
            actions.get(i).isDefault = i == index;
        }
        invalidatePaint();
    }

    // ---- Lifecycle ---------------------------------------------------------

    /**
     * @return true while the dialog is on screen
     */
    public boolean isOpen() {
        return isVisible();
    }

    /**
     * Shows the dialog, attaching it to the application's popup layer when it
     * is not attached yet. The dialog covers the whole layer and centres its
     * surface on top of the scrim.
     */
    public void show() {
        AppContext ctx = context() != null ? context() : AppContext.ofCurrent();
        Application app = ctx != null ? ctx.app() : null;
        if (getParent() == null && app != null) {
            app.getPopupLayer().add(this);
        }
        if (getParent() instanceof PopupLayer layer) {
            setWidth(layer.getWidth());
            setHeight(layer.getHeight());
        } else if (getWidth() <= 0.0f || getHeight() <= 0.0f) {
            // Headless (no window): fall back to the preferred surface size so
            // the dialog is still measurable and hit-testable.
            setWidth(Math.max(MIN_WIDTH, surfaceWidth()));
            setHeight(surfaceHeight());
        }
        hoveredAction = -1;
        pressedAction = -1;
        setVisible(true);
        requestFocus();
        invalidateLayout();
        requestRepaint();
    }

    /**
     * Hides the dialog and runs the close handler (once).
     */
    public void close() {
        if (isVisible()) {
            setVisible(false);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        boolean was = isVisible();
        super.setVisible(visible);
        if (was && !visible) {
            hoveredAction = -1;
            pressedAction = -1;
            fireClose();
        }
    }

    /**
     * @return true when the escape key and clicks on the scrim dismiss the
     *         dialog
     */
    public boolean isDismissible() {
        return dismissible;
    }

    /**
     * Controls whether the escape key and clicks on the scrim dismiss the
     * dialog. Turn this off for destructive confirmations that must be
     * answered explicitly.
     *
     * @param dismissible the new flag
     */
    public void setDismissible(boolean dismissible) {
        this.dismissible = dismissible;
    }

    /**
     * Registers the handler run once when the dialog is dismissed, whatever
     * the path that dismissed it.
     *
     * @param onClose the handler, or null to clear it
     */
    public void setOnClose(ClickListener onClose) {
        this.onClose = onClose;
    }

    private void fireClose() {
        if (onClose == null) {
            return;
        }
        try {
            onClose.onClick();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Dialog close handler threw", e);
        }
    }

    // ---- Colors ------------------------------------------------------------

    /**
     * Gets the scrim fill (CSS {@code background}, then the override, then the
     * default translucent black).
     *
     * @return the scrim color (ARGB)
     */
    public int getScrimColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, scrimColorOverride,
                SCRIM_COLOR);
    }

    /**
     * Overrides the scrim fill. Pass null to use the default translucent black.
     *
     * @param scrim the new color (ARGB) or null
     */
    public void setScrimColor(Integer scrim) {
        if (java.util.Objects.equals(this.scrimColorOverride, scrim)) {
            return;
        }
        this.scrimColorOverride = scrim;
        invalidatePaint();
    }

    /**
     * Gets the surface fill (CSS {@code background}, then the override, then
     * the theme control surface).
     *
     * @return the surface color (ARGB)
     */
    public int getSurfaceColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, surfaceColorOverride,
                getTheme().getControlNormalColor());
    }

    /**
     * Overrides the surface fill. Pass null to follow the theme.
     *
     * @param surface the new color (ARGB) or null
     */
    public void setSurfaceColor(Integer surface) {
        if (java.util.Objects.equals(this.surfaceColorOverride, surface)) {
            return;
        }
        this.surfaceColorOverride = surface;
        invalidatePaint();
    }

    /**
     * Gets the surface border color (CSS {@code border-color}, then the
     * override, then the theme border).
     *
     * @return the border color (ARGB)
     */
    public int getBorderColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, borderColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the surface border color. Pass null to follow the theme.
     *
     * @param border the new color (ARGB) or null
     */
    public void setBorderColor(Integer border) {
        if (java.util.Objects.equals(this.borderColorOverride, border)) {
            return;
        }
        this.borderColorOverride = border;
        invalidatePaint();
    }

    /**
     * Gets the text color (CSS {@code color}, then the override, then the
     * theme foreground).
     *
     * @return the text color (ARGB)
     */
    public int getTextColor() {
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride,
                getTheme().getForegroundColor());
    }

    /**
     * Overrides the text color. Pass null to follow the theme.
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

    private Font titleFont() {
        return resolveFont(Theme.FontRole.HEADING);
    }

    private Font bodyFont() {
        return resolveFont(Theme.FontRole.BODY);
    }

    private Font buttonFont() {
        return resolveFont(Theme.FontRole.BUTTON);
    }

    /**
     * The surface width: the default, widened when the title, a message line
     * or the action row needs more room.
     */
    private float surfaceWidth() {
        Font titleF = titleFont();
        Font bodyF = bodyFont();
        float needed = DEFAULT_WIDTH;
        if (!title.isEmpty()) {
            needed = Math.max(needed,
                    titleF.measureTextWidth(title) + 2.0f * CONTENT_PADDING);
        }
        for (String line : message.split("\n", -1)) {
            needed = Math.max(needed,
                    bodyF.measureTextWidth(line) + 2.0f * CONTENT_PADDING);
        }
        needed = Math.max(needed, actionRowWidth() + 2.0f * CONTENT_PADDING);
        return Math.max(MIN_WIDTH, needed);
    }

    private float actionRowWidth() {
        Font f = buttonFont();
        float total = 0.0f;
        for (int i = 0; i < actions.size(); i++) {
            total += actionWidth(i, f);
            if (i < actions.size() - 1) {
                total += BUTTON_GAP;
            }
        }
        return total;
    }

    private float actionWidth(int index, Font f) {
        return f.measureTextWidth(actions.get(index).label) + 2.0f * BUTTON_PADDING;
    }

    /**
     * Wraps the message to the surface width. Explicit newlines always break;
     * a word longer than the available width is left on its own line.
     */
    private List<String> wrapMessage(Font f, float maxWidth) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : message.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String word : paragraph.split(" ", -1)) {
                String candidate = current.length() == 0 ? word
                        : current + " " + word;
                if (current.length() > 0
                        && f.measureTextWidth(candidate) > maxWidth) {
                    lines.add(current.toString());
                    current = new StringBuilder(word);
                } else {
                    current = new StringBuilder(candidate);
                }
            }
            lines.add(current.toString());
        }
        return lines;
    }

    private float messageLineHeight(Font f) {
        FontMetrics m = f.getMetrics();
        return m.getDescent() - m.getAscent();
    }

    /**
     * The surface height for the current bounds: title block, wrapped message
     * block and the action row, plus the surrounding padding.
     */
    private float surfaceHeight() {
        float available = surfaceWidth() - 2.0f * CONTENT_PADDING;
        Font titleF = titleFont();
        Font bodyF = bodyFont();
        float height = 2.0f * CONTENT_PADDING;
        if (!title.isEmpty()) {
            height += messageLineHeight(titleF);
            if (!message.isEmpty()) {
                height += TITLE_GAP;
            }
        }
        if (!message.isEmpty()) {
            List<String> lines = wrapMessage(bodyF, available);
            height += lines.size() * messageLineHeight(bodyF);
        }
        if (!actions.isEmpty()) {
            height += ACTION_GAP + BUTTON_HEIGHT;
        }
        return height;
    }

    /**
     * Recomputes the centred surface rectangle and the action button rects.
     * Called at the top of every paint pass so hit-testing always agrees with
     * what was drawn.
     */
    private void layoutSurface() {
        surfaceW = Math.min(surfaceWidth(), Math.max(MIN_WIDTH, width));
        surfaceH = surfaceHeight();
        surfaceX = (width - surfaceW) / 2.0f;
        surfaceY = (height - surfaceH) / 2.0f;
        surfaceX = Math.max(0.0f, surfaceX);
        surfaceY = Math.max(0.0f, surfaceY);

        actionRects.clear();
        Font f = buttonFont();
        float x = surfaceX + surfaceW - CONTENT_PADDING;
        float y = surfaceY + surfaceH - CONTENT_PADDING - BUTTON_HEIGHT;
        for (int i = actions.size() - 1; i >= 0; i--) {
            float w = actionWidth(i, f);
            x -= w;
            actionRects.add(0, new float[] {x, y, w, BUTTON_HEIGHT});
            x -= BUTTON_GAP;
        }
    }

    /** The index of the action under a local point, or {@code -1}. */
    private int actionAt(float localX, float localY) {
        for (int i = 0; i < actionRects.size(); i++) {
            float[] r = actionRects.get(i);
            if (localX >= r[0] && localX <= r[0] + r[2]
                    && localY >= r[1] && localY <= r[1] + r[3]) {
                return i;
            }
        }
        return -1;
    }

    /** Whether a local point falls inside the dialog surface. */
    private boolean isOnSurface(float localX, float localY) {
        return localX >= surfaceX && localX <= surfaceX + surfaceW
                && localY >= surfaceY && localY <= surfaceY + surfaceH;
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return Math.max(MIN_WIDTH, surfaceWidth());
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return surfaceHeight();
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
        float localX = event.getX() - x;
        float localY = event.getY() - y;
        boolean inside = contains(event.getX(), event.getY());
        ComponentState oldState = state;

        switch (event.getType()) {
            case MOVE -> {
                hoveredAction = actionAt(localX, localY);
                setState(hoveredAction >= 0 && actions.get(hoveredAction).isEnabled()
                        ? ComponentState.HOVER : ComponentState.IDLE);
            }
            case PRESS -> {
                if (inside && event.getButton() == MouseButton.LEFT) {
                    pressedAction = actionAt(localX, localY);
                    if (pressedAction >= 0
                            && actions.get(pressedAction).isEnabled()) {
                        setState(ComponentState.PRESSED);
                        requestFocusFromPointer();
                    }
                }
            }
            case RELEASE -> {
                if (inside && state == ComponentState.PRESSED) {
                    int released = actionAt(localX, localY);
                    if (released >= 0 && released == pressedAction
                            && actions.get(released).isEnabled()) {
                        activate(released);
                    }
                } else if (inside && event.getButton() == MouseButton.LEFT
                        && pressedAction < 0 && dismissible
                        && !isOnSurface(localX, localY)) {
                    // A press that landed on the scrim dismisses the dialog.
                    close();
                }
                pressedAction = -1;
                hoveredAction = actionAt(localX, localY);
                setState(hoveredAction >= 0 ? ComponentState.HOVER : ComponentState.IDLE);
            }
            default -> {
            }
        }

        if (state != oldState) {
            requestRepaint();
        }
        // A dialog is modal: it swallows every pointer event that reaches it,
        // so nothing on the page behind reacts to a click through the scrim.
        return inside || isOnSurface(localX, localY);
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        switch (event.getKeyCode()) {
            case GlyphKeys.ESCAPE -> {
                if (dismissible) {
                    close();
                }
            }
            case GlyphKeys.ENTER -> {
                int index = getDefaultActionIndex();
                if (index < 0 && !actions.isEmpty()) {
                    index = 0;
                }
                if (index >= 0 && actions.get(index).isEnabled()) {
                    activate(index);
                }
            }
            default -> {
            }
        }
    }

    /** Runs an action's handler, then closes the dialog. */
    private void activate(int index) {
        Action action = actions.get(index);
        ClickListener handler = action.getOnClick();
        if (handler != null) {
            try {
                handler.onClick();
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Dialog action handler threw", e);
            }
        }
        close();
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        layoutSurface();

        int scrimColor = getScrimColor();
        if (scrimColor != 0) {
            scrimPaint.setColor(scrimColor);
            canvas.drawRect(0, 0, width, height, scrimPaint);
        }

        float radius = getTheme().getControlCornerRadius();
        // Lift the surface off the scrim with the shared elevation language,
        // so a modal reads as the topmost layer.
        canvas.drawShadow(surfaceX, surfaceY, surfaceW, surfaceH, radius,
                Canvas.Elevation.HIGH);
        surfacePaint.setColor(enabled ? getSurfaceColor()
                : getTheme().getControlDisabledColor());
        canvas.drawRRect(surfaceX, surfaceY, surfaceX + surfaceW,
                surfaceY + surfaceH, radius, radius, surfacePaint);
        borderPaint.setColor(getBorderColor());
        canvas.drawRRect(surfaceX + 0.5f, surfaceY + 0.5f,
                surfaceX + surfaceW - 1.0f, surfaceY + surfaceH - 1.0f,
                radius, radius, borderPaint);

        float contentX = surfaceX + CONTENT_PADDING;
        float contentW = surfaceW - 2.0f * CONTENT_PADDING;
        float y = surfaceY + CONTENT_PADDING;

        int textColor = enabled ? getTextColor()
                : getTheme().getControlTextDisabledColor();

        if (!title.isEmpty()) {
            Font f = titleFont();
            FontMetrics m = f.getMetrics();
            titlePaint.setColor(textColor);
            canvas.drawString(title, contentX, y - m.getAscent(), titlePaint, f);
            y += messageLineHeight(f);
            if (!message.isEmpty()) {
                y += TITLE_GAP;
            }
        }

        if (!message.isEmpty()) {
            Font f = bodyFont();
            messagePaint.setColor(textColor);
            for (String line : wrapMessage(f, contentW)) {
                FontMetrics m = f.getMetrics();
                canvas.drawString(line, contentX, y - m.getAscent(), messagePaint, f);
                y += messageLineHeight(f);
            }
        }

        drawActions(canvas);

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            for (Component child : children) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    private void drawActions(Canvas canvas) {
        Font f = buttonFont();
        FontMetrics m = f.getMetrics();
        int accent = getTheme().getAccentColor();
        int normal = getTheme().getControlNormalColor();
        int hover = getTheme().getControlHoverColor();
        int pressed = getTheme().getControlPressedColor();
        int disabled = getTheme().getControlDisabledColor();
        float radius = getTheme().getControlCornerRadius();

        for (int i = 0; i < actionRects.size() && i < actions.size(); i++) {
            Action action = actions.get(i);
            float[] r = actionRects.get(i);
            int fill;
            if (!action.enabled) {
                fill = disabled;
            } else if (i == pressedAction && i == hoveredAction) {
                fill = pressed;
            } else if (i == hoveredAction) {
                fill = hover;
            } else if (action.isDefault()) {
                fill = accent;
            } else {
                fill = normal;
            }
            surfacePaint.setColor(fill);
            canvas.drawRRect(r[0], r[1], r[0] + r[2], r[1] + r[3],
                    radius, radius, surfacePaint);

            int label = !action.enabled
                    ? getTheme().getControlTextDisabledColor()
                    : (action.isDefault() && i != hoveredAction && i != pressedAction
                            ? 0xFFFFFFFF : getTextColor());
            titlePaint.setColor(label);
            float textWidth = f.measureTextWidth(action.label);
            float textX = r[0] + (r[2] - textWidth) / 2.0f;
            float textY = r[1] + (r[3] - (m.getDescent() - m.getAscent())) / 2.0f
                    - m.getAscent();
            canvas.drawString(action.label, textX, textY, titlePaint, f);
        }
    }

    private void ensurePaints() {
        if (scrimPaint == null || scrimPaint.isClosed()) {
            scrimPaint = new Paint();
            scrimPaint.setAntiAlias(true);
        }
        if (surfacePaint == null || surfacePaint.isClosed()) {
            surfacePaint = new Paint();
            surfacePaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        if (titlePaint == null || titlePaint.isClosed()) {
            titlePaint = new Paint();
            titlePaint.setAntiAlias(true);
        }
        if (messagePaint == null || messagePaint.isClosed()) {
            messagePaint = new Paint();
            messagePaint.setAntiAlias(true);
        }
    }

    @Override
    protected void onDispose() {
        closeQuietly(scrimPaint);
        scrimPaint = null;
        closeQuietly(surfacePaint);
        surfacePaint = null;
        closeQuietly(borderPaint);
        borderPaint = null;
        closeQuietly(titlePaint);
        titlePaint = null;
        closeQuietly(messagePaint);
        messagePaint = null;
    }

    private static void closeQuietly(Paint paint) {
        if (paint != null) {
            paint.close();
        }
    }
}
