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
import io.github.humbleui.skija.PaintMode;

/**
 * A small text bubble shown next to the control the pointer rests on.
 *
 * <p>A tooltip is a {@link PassiveOverlay}: it lives in the application's
 * {@link PopupLayer} so it paints above page content, but it never takes the
 * pointer, the keyboard or the ESC gesture from the widget underneath. The
 * usual wiring attaches one to a target:</p>
 *
 * <pre>{@code
 * Tooltip.attach(saveButton, "Save the current file");
 * }</pre>
 *
 * <p>Attaching installs pointer-enter/pointer-exit callbacks on the target,
 * so the bubble appears while the pointer is over the control and disappears
 * when it leaves. A {@link #setDelaySeconds(float) delay} keeps a quick
 * sweep across a toolbar from flashing every bubble; the delay is driven by
 * {@link #advance(float)} (wire it to
 * {@code Application.setAnimationCallback}) so the widget stays deterministic
 * in tests. With no application running (headless unit tests) the tooltip
 * still computes its size and text, it is simply never painted.</p>
 */
public class Tooltip extends Panel implements PassiveOverlay {

    /** Inner padding between the bubble border and its text, logical units. */
    public static final float CONTENT_PADDING = 6.0f;

    /** Corner radius of the bubble, logical units. */
    public static final float CORNER_RADIUS = 4.0f;

    /** Gap between the anchor and the bubble, logical units. */
    public static final float ANCHOR_GAP = 6.0f;

    /** Default delay before the bubble appears, seconds. */
    public static final float DEFAULT_DELAY = 0.4f;

    /** Alpha applied to the theme border colour for the bubble hairline. */
    private static final int BORDER_ALPHA = 160;

    private String text = "";
    private float delaySeconds = DEFAULT_DELAY;
    private boolean delayElapsed;
    private float elapsed;

    private Component anchor;
    private Integer backgroundOverride;
    private Integer textColorOverride;

    private Property<String> textProperty;

    private Paint bgPaint;
    private Paint borderPaint;
    private Paint textPaint;

    /**
     * Creates an empty, hidden tooltip.
     */
    public Tooltip() {
        this("");
    }

    /**
     * Creates a hidden tooltip with the supplied text.
     *
     * @param text the bubble text (null is treated as empty)
     */
    public Tooltip(String text) {
        super(0, 0, 0, 0);
        this.text = text == null ? "" : text;
        setFocusable(false);
        // Overlays start hidden: an invisible tooltip is skipped by the
        // layer's hit-testing and rendering.
        setVisible(false);
    }

    /**
     * Creates a tooltip and attaches it to a target in one call.
     *
     * @param anchor the component the bubble describes
     * @param text   the bubble text
     * @return the new tooltip
     */
    public static Tooltip attach(Component anchor, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setAnchor(anchor);
        return tooltip;
    }

    @Override
    protected String defaultStyleTag() {
        return "tooltip";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.TOOLTIP;
    }

    @Override
    public String getAccessibleName() {
        if (accessibleNameOverride() != null) {
            return accessibleNameOverride();
        }
        return text.isEmpty() ? "Tooltip" : text;
    }

    // ---- Content -----------------------------------------------------------

    /**
     * @return the bubble text, never null
     */
    public String getText() {
        return text;
    }

    /**
     * Sets the bubble text. Setting it from a background thread marshals the
     * change onto the UI thread automatically.
     *
     * @param text the new text (null is treated as empty)
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
     * @return the delay before the bubble appears, in seconds
     */
    public float getDelaySeconds() {
        return delaySeconds;
    }

    /**
     * Sets the delay before the bubble appears. A non-positive delay shows it
     * immediately on pointer enter.
     *
     * @param delaySeconds the delay in seconds (values below zero are clamped)
     */
    public void setDelaySeconds(float delaySeconds) {
        this.delaySeconds = Math.max(0.0f, delaySeconds);
    }

    // ---- Anchor ------------------------------------------------------------

    /**
     * @return the component the bubble describes, or null when none is set
     */
    public Component getAnchor() {
        return anchor;
    }

    /**
     * Attaches the tooltip to a target: installs the pointer-enter/exit
     * callbacks that show and hide it. Passing null detaches the previous
     * target.
     *
     * @param anchor the component the bubble describes, or null
     */
    public void setAnchor(Component anchor) {
        if (this.anchor == anchor) {
            return;
        }
        if (this.anchor != null) {
            this.anchor.setOnPointerEnter(null);
            this.anchor.setOnPointerExit(null);
            hide();
        }
        this.anchor = anchor;
        if (anchor != null) {
            anchor.setOnPointerEnter(this::onAnchorEnter);
            anchor.setOnPointerExit(this::onAnchorExit);
        }
    }

    private void onAnchorEnter() {
        elapsed = 0.0f;
        delayElapsed = delaySeconds <= 0.0f;
        if (delayElapsed) {
            show();
        }
    }

    private void onAnchorExit() {
        elapsed = 0.0f;
        delayElapsed = false;
        hide();
    }

    /**
     * Advances the appear-delay clock by one frame. Wire it to
     * {@code Application.setAnimationCallback} so the delay is honoured; with
     * no animation callback the tooltip appears as soon as it is hovered.
     *
     * @param deltaSeconds seconds elapsed since the previous frame
     */
    public void advance(float deltaSeconds) {
        if (deltaSeconds <= 0.0f || isVisible() || anchor == null) {
            return;
        }
        elapsed += deltaSeconds;
        if (!delayElapsed && elapsed >= delaySeconds) {
            delayElapsed = true;
            show();
        }
    }

    // ---- Show / hide -------------------------------------------------------

    /**
     * @return true while the bubble is on screen
     */
    public boolean isShowing() {
        return isVisible();
    }

    /**
     * Shows the bubble next to its anchor, attaching it to the popup layer on
     * first use.
     */
    public void show() {
        if (anchor == null) {
            return;
        }
        AppContext ctx = context() != null ? context() : AppContext.ofCurrent();
        Application app = ctx != null ? ctx.app() : null;
        if (getParent() == null && app != null) {
            app.getPopupLayer().add(this);
        }
        setWidth(getPreferredWidth());
        setHeight(getPreferredHeight());
        positionNearAnchor();
        setVisible(true);
        requestRepaint();
    }

    /**
     * Hides the bubble (no-op when it is already hidden).
     */
    public void hide() {
        if (isVisible()) {
            setVisible(false);
            requestRepaint();
        }
    }

    /**
     * Places the bubble centred below the anchor, flipping above it when the
     * layer has no room below.
     */
    private void positionNearAnchor() {
        if (anchor == null) {
            return;
        }
        float anchorX = 0.0f;
        float anchorY = 0.0f;
        for (Component c = anchor; c != null; c = c.getParent()) {
            anchorX += c.getX();
            anchorY += c.getY();
        }
        Panel layer = getParent();
        float layerWidth = (layer != null && layer.getWidth() > 0.0f)
                ? layer.getWidth() : Float.MAX_VALUE;
        float layerHeight = (layer != null && layer.getHeight() > 0.0f)
                ? layer.getHeight() : Float.MAX_VALUE;

        float x = anchorX + (anchor.getWidth() - getWidth()) / 2.0f;
        x = Math.max(0.0f, Math.min(x, layerWidth - getWidth()));

        float belowY = anchorY + anchor.getHeight() + ANCHOR_GAP;
        float aboveY = anchorY - getHeight() - ANCHOR_GAP;
        float y = (belowY + getHeight() <= layerHeight) ? belowY : Math.max(0.0f, aboveY);

        setX(x);
        setY(y);
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
        if (text.isEmpty()) {
            return 2.0f * CONTENT_PADDING;
        }
        return font().measureTextWidth(text) + 2.0f * CONTENT_PADDING;
    }

    @Override
    public float getPreferredHeight() {
        return lineHeight(font()) + 2.0f * CONTENT_PADDING;
    }

    @Override
    public Dimension measure(float maxWidth, float maxHeight) {
        return Dimension.of(
                Math.min(getPreferredWidth(), maxWidth),
                Math.min(getPreferredHeight(), maxHeight));
    }

    // ---- Colors ------------------------------------------------------------

    /**
     * @return the bubble fill (ARGB)
     */
    public int getBackgroundColor() {
        return resolveIntStyle(StyleProperty.BACKGROUND, backgroundOverride,
                getTheme().getControlNormalColor());
    }

    /**
     * Overrides the bubble fill. Pass null to follow the theme.
     *
     * @param background the new color (ARGB) or null
     */
    public void setBackgroundColor(Integer background) {
        this.backgroundOverride = background;
        invalidatePaint();
    }

    /**
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
        this.textColorOverride = textColor;
        invalidatePaint();
    }

    // ---- Rendering ---------------------------------------------------------

    @Override
    protected void render(Canvas canvas) {
        ensurePaints();
        bgPaint.setColor(getBackgroundColor());
        canvas.drawRRect(0, 0, width, height, CORNER_RADIUS, CORNER_RADIUS, bgPaint);

        if (!text.isEmpty()) {
            Font f = font();
            textPaint.setColor(getTextColor());
            FontMetrics m = f.getMetrics();
            float baseline = CONTENT_PADDING - m.getAscent();
            canvas.drawString(text, CONTENT_PADDING, baseline, textPaint, f);
        }

        borderPaint.setColor((BORDER_ALPHA << 24) | (getTheme().getBorderColor() & 0x00FFFFFF));
        canvas.drawRRect(0.5f, 0.5f, width - 1.0f, height - 1.0f,
                CORNER_RADIUS, CORNER_RADIUS, borderPaint);
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
            borderPaint.setMode(PaintMode.STROKE);
            borderPaint.setStrokeWidth(1.0f);
        }
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
    }

    @Override
    public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
        // A tooltip is decoration: it never consumes input, so clicks pass
        // through to the control it covers.
        return false;
    }

    @Override
    public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
        // Not focusable; hook exists to satisfy the Component contract.
    }

    @Override
    protected void onDispose() {
        if (anchor != null) {
            anchor.setOnPointerEnter(null);
            anchor.setOnPointerExit(null);
            anchor = null;
        }
        close(bgPaint);
        bgPaint = null;
        close(borderPaint);
        borderPaint = null;
        close(textPaint);
        textPaint = null;
        super.onDispose();
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) {
            paint.close();
        }
    }
}
