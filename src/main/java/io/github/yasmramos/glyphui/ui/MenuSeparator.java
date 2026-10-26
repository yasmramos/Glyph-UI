package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.PaintMode;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.Theme;

/**
 * A non-interactive divider drawn between groups of {@link MenuItem}s inside
 * a {@link Menu}, the horizontal rule of menu bars ("---" in a menu builder).
 *
 * <p>The separator has no intrinsic width: {@link Menu} stretches it to the
 * menu's inner width and reserves a fixed row height for it, so inserting a
 * divider never changes how wide the menu turns out to be.</p>
 */
public class MenuSeparator extends Component {

    /** Row height reserved for the divider (line plus breathing room). */
    static final float SEPARATOR_HEIGHT = 9.0f;

    /** Distance from each row edge to the start of the line. */
    private static final float INSET = 6.0f;

    /** Alpha applied to the theme border colour for the hairline. */
    private static final int LINE_ALPHA = 110;

    private Paint linePaint;

    /**
     * Creates a separator row.
     */
    public MenuSeparator() {
        super(0, 0, 0, SEPARATOR_HEIGHT);
        // A divider is decoration: it is never hit-tested as an interactive
        // target and never joins the focus traversal.
        setEnabled(false);
        setFocusable(false);
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.MENU_SEPARATOR;
    }

    @Override
    public float getPreferredWidth() {
        // A divider is always stretched to the menu's inner width, so it must
        // never influence how wide the menu measures itself.
        return 0.0f;
    }

    @Override
    public float getPreferredHeight() {
        return SEPARATOR_HEIGHT;
    }

    @Override
    protected void render(Canvas canvas) {
        if (!visible) {
            return;
        }
        if (linePaint == null) {
            linePaint = new Paint();
            linePaint.setAntiAlias(true);
            linePaint.setMode(PaintMode.STROKE);
            linePaint.setStrokeWidth(1.0f);
        }
        Theme theme = getTheme();
        int color = theme.getBorderColor();
        linePaint.setColor((LINE_ALPHA << 24) | (color & 0x00FFFFFF));

        float lineY = y + height / 2.0f;
        canvas.drawLine(x + INSET, lineY, x + width - INSET, lineY, linePaint);
    }

    @Override
    public boolean onMouseEvent(MouseEvent event) {
        // Decoration only: a divider never consumes pointer input, so a
        // press landing on it falls through to the menu chrome beneath.
        return false;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        // Not focusable; nothing to do.
    }
}
