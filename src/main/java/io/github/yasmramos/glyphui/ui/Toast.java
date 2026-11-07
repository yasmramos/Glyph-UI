package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.AppContext;
import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Property;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontMetrics;
import io.github.humbleui.skija.Paint;

/**
 * A transient notification that slides in at the edge of the window and
 * dismisses itself.
 *
 * <p>A toast is a {@link PassiveOverlay}: it floats above the page (in the
 * application's {@link PopupLayer}) but never takes the pointer, the keyboard
 * or the ESC gesture, so it can never block the control it covers. A single
 * call publishes one:</p>
 *
 * <pre>{@code
 * Toast.show("File saved", Toast.Level.SUCCESS);
 * }</pre>
 *
 * <p>The {@link #show(String, Level) static helper} stacks toasts in the
 * application's toast host (created on first use), so several notifications
 * pile up instead of overlapping. The auto-dismiss clock is advanced by
 * {@link #advance(float)} — wire it to
 * {@code Application.setAnimationCallback} — which keeps the widget
 * deterministic in tests and lets an application keep its own frame cadence.</p>
 */
public class Toast extends Panel implements PassiveOverlay {

    /** Severity of a toast, which selects its accent colour. */
    public enum Level {
        /** Neutral information. */
        INFO,
        /** A successful outcome. */
        SUCCESS,
        /** A recoverable problem. */
        WARNING,
        /** A failed operation. */
        ERROR
    }

    /** Horizontal inner padding, logical units. */
    public static final float PADDING_HORIZONTAL = 14.0f;

    /** Vertical inner padding, logical units. */
    public static final float PADDING_VERTICAL = 10.0f;

    /** Corner radius of the toast surface, logical units. */
    public static final float CORNER_RADIUS = 6.0f;

    /** Width of the accent strip on the leading edge, logical units. */
    public static final float ACCENT_STRIP_WIDTH = 4.0f;

    /** Default duration a toast stays on screen, seconds. */
    public static final float DEFAULT_DURATION = 3.0f;

    /** Default toast width, logical units. */
    public static final float DEFAULT_WIDTH = 280.0f;

    private String text;
    private Level level;
    private float durationSeconds = DEFAULT_DURATION;
    private float elapsed;
    private boolean paused;
    private Integer accentColorOverride;

    private Property<String> textProperty;

    private Paint bgPaint;
    private Paint accentPaint;
    private Paint textPaint;

    /**
     * Creates an informational toast with no message.
     */
    public Toast() {
        this(Level.INFO, "");
    }

    /**
     * Creates a toast with the supplied level and message.
     *
     * @param level the severity (null defaults to {@link Level#INFO})
     * @param text  the message (null is treated as empty)
     */
    public Toast(Level level, String text) {
        super();
        this.level = level == null ? Level.INFO : level;
        this.text = text == null ? "" : text;
        setFocusable(false);
        // Toasts start hidden and are shown explicitly, like a Dialog.
        setVisible(false);
    }

    /**
     * Publishes a toast in the running application's toast host, creating the
     * host on first use. With no application running (headless tests) the
     * toast is returned unattached so callers can still drive it manually.
     *
     * @param text  the message
     * @param level the severity
     * @return the created toast
     */
    public static Toast show(String text, Level level) {
        Toast toast = new Toast(level, text);
        ToastHost host = ToastHost.ofCurrent();
        if (host != null) {
            host.push(toast);
        } else {
            toast.setWidth(toast.getPreferredWidth());
            toast.setHeight(toast.getPreferredHeight());
            toast.setVisible(true);
        }
        return toast;
    }

    @Override
    protected String defaultStyleTag() {
        return "toast";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TOAST;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null ? accessibleNameOverride() : text;
    }

    // ---- Content -----------------------------------------------------------

    /**
     * @return the toast message, never null
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the toast message. Setting it from a background thread marshals the
     * change onto the UI thread automatically.
     *
     * @param text the new message (null is treated as empty)
     */
    public void setText(String text) {
        if (textProperty == null) {
            textProperty = new Property<>(propertyOwner(), this.text, newText -> {
                this.text = newText == null ? "" : newText;
                invalidateLayout();
                invalidatePaint();
            });
        }
        textProperty.set(text == null ? "" : text);
    }

    /**
     * @return the severity level
     */
    public Level getLevel() {
        return level;
    }

    /**
     * Sets the severity level, which selects the accent colour.
     *
     * @param level the new level (null defaults to {@link Level#INFO})
     */
    public void setLevel(Level level) {
        this.level = level == null ? Level.INFO : level;
        invalidatePaint();
    }

    /**
     * @return how long the toast stays on screen, in seconds
     */
    public float getDurationSeconds() {
        return durationSeconds;
    }

    /**
     * Sets how long the toast stays on screen. A non-positive duration keeps
     * the toast up until it is dismissed programmatically.
     *
     * @param durationSeconds the duration in seconds
     */
    public void setDurationSeconds(float durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    /**
     * Pauses or resumes the auto-dismiss clock, so a toast under the pointer
     * does not vanish while the user reads it.
     *
     * @param paused true to freeze the countdown
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * @return true while the countdown is frozen
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Advances the auto-dismiss clock by one frame, dismissing the toast when
     * its duration elapses. Wire it to
     * {@code Application.setAnimationCallback}.
     *
     * @param deltaSeconds seconds elapsed since the previous frame
     */
    public void advance(float deltaSeconds) {
        if (deltaSeconds <= 0.0f || paused || !isVisible()) {
            return;
        }
        elapsed += deltaSeconds;
        if (durationSeconds > 0.0f && elapsed >= durationSeconds) {
            dismiss();
        }
    }

    /**
     * Dismisses the toast, removing it from its host when attached.
     */
    public void dismiss() {
        if (getParent() instanceof ToastHost host) {
            host.remove(this);
        }
        setVisible(false);
        requestRepaint();
    }

    // ---- Colors ------------------------------------------------------------

    /**
     * Gets the accent colour for the current level. Resolution order: a CSS
     * {@code accent-color} declaration, then an explicit override, then the
     * level default.
     *
     * @return the accent colour (ARGB)
     */
    public int getAccentColor() {
        int fallback = switch (level) {
            case SUCCESS -> 0xFF2E7D32;
            case WARNING -> 0xFFED6C02;
            case ERROR -> 0xFFD32F2F;
            default -> getTheme().getAccentColor();
        };
        return resolveIntStyle(StyleProperty.ACCENT_COLOR, accentColorOverride, fallback);
    }

    /**
     * Overrides the accent colour. Pass null to follow the level default.
     *
     * @param accentColor the new colour (ARGB) or null
     */
    public void setAccentColor(Integer accentColor) {
        this.accentColorOverride = accentColor;
        invalidatePaint();
    }

    // ---- Geometry ----------------------------------------------------------

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    private float lineHeight(Font f) {
        FontMetrics m = f.getMetrics();
        return m.getDescent() - m.getAscent();
    }

    @Override
    public float getPreferredWidth() {
        float textWidth = text.isEmpty() ? 0.0f : font().measureTextWidth(text);
        return Math.max(DEFAULT_WIDTH,
                ACCENT_STRIP_WIDTH + 2.0f * PADDING_HORIZONTAL + textWidth);
    }

    @Override
    public float getPreferredHeight() {
        return lineHeight(font()) + 2.0f * PADDING_VERTICAL;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        return Dimension.of(
                Math.min(getPreferredWidth(), maxWidth),
                Math.min(getPreferredHeight(), maxHeight));
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        bgPaint.setColor(getTheme().getControlNormalColor());
        canvas.drawRRect(0, 0, width, height, CORNER_RADIUS, CORNER_RADIUS, bgPaint);

        accentPaint.setColor(getAccentColor());
        canvas.drawRect(0, 0, ACCENT_STRIP_WIDTH, height, accentPaint);

        if (!text.isEmpty()) {
            Font f = font();
            textPaint.setColor(getTheme().getForegroundColor());
            FontMetrics m = f.getMetrics();
            float baseline = (height - (m.getDescent() - m.getAscent())) / 2.0f - m.getAscent();
            canvas.drawString(text, ACCENT_STRIP_WIDTH + PADDING_HORIZONTAL, baseline,
                    textPaint, f);
        }
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (accentPaint == null || accentPaint.isClosed()) {
            accentPaint = new Paint();
            accentPaint.setAntiAlias(true);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
        // A toast is decoration: it never consumes input.
        return false;
    }

    @Override
    public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
        // Not focusable; hook exists to satisfy the Component contract.
    }

    @Override
    protected void onDispose() {
        close(bgPaint);
        bgPaint = null;
        close(accentPaint);
        accentPaint = null;
        close(textPaint);
        textPaint = null;
        super.onDispose();
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) {
            paint.close();
        }
    }

    /**
     * The popup-layer child that stacks toasts at the bottom-right corner.
     *
     * <p>Kept package-private: applications reach it only through
     * {@link Toast#show(String, Level)}, which finds the running host or
     * creates one. A host is a passive overlay itself, so neither the stack
     * nor its toasts take input from the page.</p>
     */
    static final class ToastHost extends Panel implements PassiveOverlay {

        /** Gap between stacked toasts, logical units. */
        static final float GAP = 8.0f;

        /** Margin between the stack and the window edges, logical units. */
        static final float MARGIN = 16.0f;

        ToastHost() {
            super(0, 0, 0, 0);
            setFocusable(false);
            setVisible(true);
        }

        /**
         * Returns the host attached to the running application, creating and
         * attaching one on first use. The host is found by scanning the popup
         * layer, so no process-wide static retains it past {@code close()}.
         *
         * @return the host, or null when no application is running
         */
        static ToastHost ofCurrent() {
            AppContext ctx = AppContext.ofCurrent();
            Application app = ctx != null ? ctx.app() : null;
            if (app == null) {
                return null;
            }
            PopupLayer layer = app.getPopupLayer();
            for (Component child : layer.getChildren()) {
                if (child instanceof ToastHost host) {
                    return host;
                }
            }
            ToastHost host = new ToastHost();
            layer.add(host);
            return host;
        }

        /**
         * Adds a toast to the top of the stack and shows it.
         *
         * @param toast the toast to push
         */
        void push(Toast toast) {
            add(toast);
            toast.setWidth(toast.getPreferredWidth());
            toast.setHeight(toast.getPreferredHeight());
            toast.setVisible(true);
            relayoutStack();
            requestRepaint();
        }

        /**
         * Removes a toast from the stack.
         *
         * @param toast the toast to remove
         */
        void remove(Toast toast) {
            remove((Component) toast);
            relayoutStack();
            requestRepaint();
        }

        /** Stacks the toasts bottom-up against the bottom-right corner. */
        void relayoutStack() {
            float layerWidth = (getParent() != null && getParent().getWidth() > 0.0f)
                    ? getParent().getWidth() : getWidth();
            float layerHeight = (getParent() != null && getParent().getHeight() > 0.0f)
                    ? getParent().getHeight() : getHeight();
            setWidth(layerWidth);
            setHeight(layerHeight);

            float y = layerHeight - MARGIN;
            for (int i = getChildren().size() - 1; i >= 0; i--) {
                Component child = getChildren().get(i);
                y -= child.getHeight();
                child.setX(layerWidth - MARGIN - child.getWidth());
                child.setY(Math.max(MARGIN, y));
                y -= GAP;
            }
        }

        @Override
        public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
            // The stack is transparent to input; only a toast that wanted a
            // click would need to consume it, and none does today.
            return false;
        }

        @Override
        public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
            // Not focusable.
        }
    }
}
