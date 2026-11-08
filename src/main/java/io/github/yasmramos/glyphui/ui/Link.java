package io.github.yasmramos.glyphui.ui;

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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A text hyperlink: a caption that navigates when activated.
 *
 * <p>Unlike {@link Label}, which is static, a link is a control. It paints its
 * caption in the accent color, underlines it while hovered or focused, and runs
 * the {@link #setOnClick(ClickListener) click handler} on a click or on
 * Enter/Space while focused. Following the HTML convention, a link that has
 * already been visited paints in the muted visited color.</p>
 *
 * <p>The widget never opens anything by itself: it reports the activation and
 * lets the application decide where the {@link #getTarget() target} leads.
 * Colors resolve like every other widget: a CSS declaration wins, then a
 * per-instance override, then the theme.</p>
 */
public class Link extends Component implements Textual {

    /** Logger for click-handler failures (never swallowed silently). */
    private static final Logger LOG = Logger.getLogger(Link.class.getName());

    /** Horizontal padding kept around the caption, logical units. */
    public static final float PADDING_HORIZONTAL = 1.0f;

    private String text;

    /** Application-defined destination reported to the click handler. */
    private String target = "";

    private boolean visited;
    private boolean alwaysUnderline;
    private ClickListener onClick;
    private Property<String> textProperty;

    private Integer textColorOverride;
    private Integer visitedColorOverride;

    private Paint textPaint;
    private Paint underlinePaint;

    /**
     * Creates an empty link with no explicit bounds.
     */
    public Link() {
        this("");
    }

    /**
     * Creates a link showing the supplied caption.
     *
     * @param text the caption (null is treated as empty)
     */
    public Link(String text) {
        super();
        this.text = text == null ? "" : text;
        // A link is a control, so it is keyboard reachable.
        setFocusable(true);
    }

    @Override
    protected String defaultStyleTag() {
        return "link";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.LINK;
    }

    @Override
    public String getAccessibleName() {
        return accessibleNameOverride() != null ? accessibleNameOverride() : text;
    }

    /**
     * Gets the link caption.
     *
     * @return the caption, never null
     */
    public String getText() {
        return text;
    }

    /**
     * Returns the observable caption property. Setting it from a background
     * thread marshals the change onto the UI thread automatically.
     *
     * @return the caption property (never null after first access)
     */
    public Property<String> textProperty() {
        if (textProperty == null) {
            textProperty = new Property<>(propertyOwner(), text, newText -> {
                this.text = newText == null ? "" : newText;
                invalidateLayout();
            });
        }
        return textProperty;
    }

    /**
     * Sets the link caption.
     *
     * @param text the new caption (null is treated as empty)
     */
    public void setText(String text) {
        textProperty().set(text);
    }

    /**
     * Gets the application-defined destination. The toolkit never interprets
     * it; it is carried so a shared click handler can tell links apart.
     *
     * @return the target, never null (empty when unset)
     */
    public String getTarget() {
        return target;
    }

    /**
     * Sets the application-defined destination reported alongside an
     * activation.
     *
     * @param target the target (null is treated as empty)
     */
    public void setTarget(String target) {
        this.target = target == null ? "" : target;
    }

    /**
     * @return true when the link paints in the visited color
     */
    public boolean isVisited() {
        return visited;
    }

    /**
     * Sets the visited state. The widget does not track visits on its own:
     * the application decides when a destination counts as seen.
     *
     * @param visited the new state
     */
    public void setVisited(boolean visited) {
        if (this.visited == visited) {
            return;
        }
        this.visited = visited;
        invalidatePaint();
    }

    /**
     * @return true when the caption is always underlined
     */
    public boolean isAlwaysUnderline() {
        return alwaysUnderline;
    }

    /**
     * Controls the underline policy. When false (the default) the caption is
     * underlined only while hovered or focused, the modern web convention.
     *
     * @param alwaysUnderline true to underline unconditionally
     */
    public void setAlwaysUnderline(boolean alwaysUnderline) {
        if (this.alwaysUnderline == alwaysUnderline) {
            return;
        }
        this.alwaysUnderline = alwaysUnderline;
        invalidatePaint();
    }

    /**
     * Registers the activation handler.
     *
     * @param onClick the handler, or null to clear it
     */
    public void setOnClick(ClickListener onClick) {
        this.onClick = onClick;
    }

    /**
     * Gets the caption color: the visited color once visited, the accent
     * color otherwise.
     *
     * @return the caption color (ARGB)
     */
    public int getTextColor() {
        if (visited) {
            return resolveIntStyle(StyleProperty.COLOR, visitedColorOverride,
                    mutedAccent());
        }
        return resolveIntStyle(StyleProperty.COLOR, textColorOverride,
                getTheme().getAccentColor());
    }

    /**
     * Overrides the unvisited caption color. Pass null to follow the accent.
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

    /**
     * Overrides the visited caption color. Pass null to follow the muted
     * accent blend.
     *
     * @param visitedColor the new color (ARGB) or null
     */
    public void setVisitedColor(Integer visitedColor) {
        if (java.util.Objects.equals(this.visitedColorOverride, visitedColor)) {
            return;
        }
        this.visitedColorOverride = visitedColor;
        invalidatePaint();
    }

    /**
     * The visited color when nothing overrides it: the accent blended halfway
     * toward the foreground, which reads as "seen" without leaving the theme.
     */
    private int mutedAccent() {
        return blend(getTheme().getAccentColor(), getTheme().getForegroundColor(), 0.5f);
    }

    private static int blend(int from, int to, float ratio) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - ratio) + ((to >> 16) & 0xFF) * ratio);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - ratio) + ((to >> 8) & 0xFF) * ratio);
        int b = Math.round((from & 0xFF) * (1 - ratio) + (to & 0xFF) * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private Font font() {
        return resolveFont(Theme.FontRole.BODY);
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return font().measureTextWidth(text) + 2.0f * PADDING_HORIZONTAL;
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        FontMetrics metrics = font().getMetrics();
        return metrics.getDescent() - metrics.getAscent();
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

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        ensurePaints();
        Font f = font();
        FontMetrics metrics = f.getMetrics();
        float line = metrics.getDescent() - metrics.getAscent();
        float baseline = (height - line) / 2.0f - metrics.getAscent();
        float textWidth = canvas.measureText(text, f);

        textPaint.setColor(enabled
                ? getTextColor()
                : getTheme().getControlTextDisabledColor());
        canvas.drawString(text, PADDING_HORIZONTAL, baseline, textPaint, f);

        boolean underlined = enabled
                && (alwaysUnderline || state == ComponentState.HOVER || isFocused());
        if (underlined) {
            underlinePaint.setColor(textPaint.getColor());
            float underlineY = baseline - metrics.getAscent() + 1.0f;
            canvas.drawLine(PADDING_HORIZONTAL, underlineY,
                    PADDING_HORIZONTAL + textWidth, underlineY, underlinePaint);
        }
    }

    private void ensurePaints() {
        if (textPaint == null || textPaint.isClosed()) {
            textPaint = new Paint();
            textPaint.setAntiAlias(true);
        }
        if (underlinePaint == null || underlinePaint.isClosed()) {
            underlinePaint = new Paint();
            underlinePaint.setAntiAlias(true);
            underlinePaint.setMode(PaintMode.STROKE);
            underlinePaint.setStrokeWidth(1.0f);
        }
    }

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
                    runHandler();
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
    public void onKeyEvent(KeyEvent event) {
        if (!visible || !enabled || event.getType() != KeyEventType.PRESS) {
            return;
        }
        if (event.getKeyCode() == GlyphKeys.ENTER || event.getKeyCode() == GlyphKeys.SPACE) {
            runHandler();
        }
    }

    /** Runs the activation handler, catching failures like every other control. */
    private void runHandler() {
        if (onClick == null) {
            return;
        }
        try {
            onClick.onClick();
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Link handler threw", e);
        }
    }

    @Override
    protected void onDispose() {
        if (textPaint != null) {
            textPaint.close();
            textPaint = null;
        }
        if (underlinePaint != null) {
            underlinePaint.close();
            underlinePaint = null;
        }
    }
}
