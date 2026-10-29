package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.humbleui.skija.Paint;

/**
 * A one-pixel divider between content regions.
 *
 * <p>The separator is decorative: it never takes focus and its preferred size
 * is one logical pixel on the cross axis. The main axis is left to the layout
 * manager (a horizontal separator stretches to the container width).</p>
 */
public class Separator extends Component {

    /**
     * Divider orientation.
     */
    public enum Orientation {
        /** A horizontal line, one pixel high. */
        HORIZONTAL,
        /** A vertical line, one pixel wide. */
        VERTICAL
    }

    /** Thickness of the painted line. */
    public static final float THICKNESS = 1.0f;

    private Orientation orientation = Orientation.HORIZONTAL;
    private Paint paint;

    /**
     * Creates a horizontal separator.
     */
    public Separator() {
        super();
        setFocusable(false);
    }

    /**
     * Creates a separator with the given orientation.
     *
     * @param orientation the orientation, never null
     */
    public Separator(Orientation orientation) {
        this();
        setOrientation(orientation);
    }

    @Override
    protected String defaultStyleTag() {
        return "separator";
    }

    /**
     * @return the orientation
     */
    public Orientation getOrientation() {
        return orientation;
    }

    /**
     * Sets the orientation.
     *
     * @param orientation the orientation, never null
     * @throws IllegalArgumentException when orientation is null
     */
    public void setOrientation(Orientation orientation) {
        if (orientation == null) {
            throw new IllegalArgumentException("Separator orientation must not be null");
        }
        if (this.orientation != orientation) {
            this.orientation = orientation;
            invalidateLayout();
        }
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.SEPARATOR;
    }

    @Override
    public float getPreferredWidth() {
        if (sizeExplicitlySet) {
            return width;
        }
        return orientation == Orientation.VERTICAL ? THICKNESS : super.getPreferredWidth();
    }

    @Override
    public float getPreferredHeight() {
        if (sizeExplicitlySet) {
            return height;
        }
        return orientation == Orientation.HORIZONTAL ? THICKNESS : super.getPreferredHeight();
    }

    @Override
    protected void render(Canvas canvas) {
        // Local coordinates: Component.renderInternal() filtered visibility
        // and translated the origin to (x, y) before calling this method.
        if (paint == null) {
            paint = new Paint();
            paint.setAntiAlias(false);
        }
        paint.setColor(getTheme().getBorderColor());
        if (orientation == Orientation.HORIZONTAL) {
            float lineY = (height - THICKNESS) / 2.0f;
            canvas.drawRect(0, lineY, width, THICKNESS, paint);
        } else {
            float lineX = (width - THICKNESS) / 2.0f;
            canvas.drawRect(lineX, 0, THICKNESS, height, paint);
        }
    }

    @Override
    public boolean onMouseEvent(io.github.yasmramos.glyphui.events.MouseEvent event) {
        // Decorative: never consumes pointer events.
        return false;
    }

    @Override
    public void onKeyEvent(io.github.yasmramos.glyphui.events.KeyEvent event) {
        // Decorative: separators never take focus or handle keys.
    }

    @Override
    protected void onDispose() {
        if (paint != null) {
            paint.close();
            paint = null;
        }
    }
}
