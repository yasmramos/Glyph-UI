package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.humbleui.skija.Paint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A horizontal strip of drop-down menus, the classic application menu bar.
 *
 * <p>A menu bar is a {@link Panel} that lays its triggers out in a single row
 * and paints the same chrome as a {@link Toolbar}. Each entry is a
 * {@link MenuItem}-less trigger button owned by the bar, so the usual wiring
 * is one call per menu:</p>
 *
 * <pre>{@code
 * MenuBar bar = new MenuBar();
 * Menu file = bar.addMenu("File");
 * file.addItem("New", () -> newDocument());
 * file.addItem("Open…", () -> open());
 * }</pre>
 *
 * <p>The bar positions each menu against its trigger and hands the trigger to
 * {@link Menu#setTrigger(Component)}, which is what gives the toolkit's menu
 * bar its native behaviour for free: once a menu is open, sliding the pointer
 * onto a sibling trigger switches to that menu without another click (see
 * {@link PopupLayer}).</p>
 */
public class MenuBar extends Panel {

    /** Horizontal padding inside the strip, logical units. */
    public static final float CONTENT_PADDING = 4.0f;

    /** Gap between two triggers, logical units. */
    public static final float ITEM_GAP = 2.0f;

    /** The trigger buttons, in insertion order. */
    private final List<Button> triggers = new ArrayList<>();

    /** The menus owned by this bar, in insertion order. */
    private final List<Menu> menus = new ArrayList<>();

    private Integer borderColorOverride;

    private Paint bgPaint;
    private Paint borderPaint;

    /**
     * Creates an empty menu bar with no explicit bounds.
     */
    public MenuBar() {
        super();
        setLayoutManager(new MenuBarLayout());
        // The bar is chrome: it never takes focus itself, only the menus do.
        setFocusable(false);
    }

    @Override
    protected String defaultStyleTag() {
        return "menubar";
    }

    @Override
    public AccessibleRole getAccessibleRole() {
        return AccessibleRole.MENU_BAR;
    }

    /**
     * Appends a menu with a trigger button carrying the supplied title.
     *
     * @param title the trigger caption (null is treated as empty)
     * @return the new menu, for adding items
     */
    public Menu addMenu(String title) {
        String label = title == null ? "" : title;
        Menu menu = new Menu();
        Button trigger = new Button(label);
        trigger.setStyleTag("menubar-item");
        trigger.setOnClick(menu::toggle);
        menu.setTrigger(trigger);
        add(trigger);
        triggers.add(trigger);
        menus.add(menu);
        invalidateLayout();
        return menu;
    }

    /**
     * @return the menus owned by this bar, in insertion order
     */
    public List<Menu> getMenus() {
        return Collections.unmodifiableList(menus);
    }

    /**
     * @return the trigger buttons, in insertion order
     */
    public List<Button> getTriggers() {
        return Collections.unmodifiableList(triggers);
    }

    /**
     * Gets the strip fill. Resolution order: a CSS {@code background}
     * declaration, then an explicit {@link Panel#setBackgroundColor(int)
     * override}, then the theme control surface.
     *
     * @return the fill color (ARGB)
     */
    @Override
    public int getBackgroundColor() {
        Style s = getComputedStyle();
        if (s != null && s.has(StyleProperty.BACKGROUND)) {
            return s.getInt(StyleProperty.BACKGROUND, getTheme().getControlNormalColor());
        }
        return super.getBackgroundColor();
    }

    /**
     * Gets the bottom border color.
     *
     * @return the border color (ARGB)
     */
    public int getBorderColor() {
        return resolveIntStyle(StyleProperty.BORDER_COLOR, borderColorOverride,
                getTheme().getBorderColor());
    }

    /**
     * Overrides the bottom border color. Pass null to follow the theme.
     *
     * @param borderColor the new color (ARGB) or null
     */
    public void setBorderColor(Integer borderColor) {
        this.borderColorOverride = borderColor;
        invalidatePaint();
    }

    @Override
    public float getPreferredHeight() {
        float tallest = 0.0f;
        for (Component child : getChildren()) {
            tallest = Math.max(tallest, child.getPreferredHeight());
        }
        return tallest + 2.0f * CONTENT_PADDING;
    }

    @Override
    protected void render(Canvas canvas) {
        doLayout();
        ensurePaints();
        bgPaint.setColor(getBackgroundColor());
        canvas.drawRect(0, 0, width, height, bgPaint);
        borderPaint.setColor(getBorderColor());
        canvas.drawRect(0, height - 1.0f, width, 1.0f, borderPaint);

        int saveCount = canvas.save();
        canvas.clipRect(0, 0, width, height);
        try {
            for (Component child : getChildren()) {
                if (child.isVisible() && !isDisplayNone(child)) {
                    child.renderInternal(canvas);
                }
            }
        } finally {
            canvas.restoreToCount(saveCount);
        }
    }

    private void ensurePaints() {
        if (bgPaint == null || bgPaint.isClosed()) {
            bgPaint = new Paint();
            bgPaint.setAntiAlias(true);
        }
        if (borderPaint == null || borderPaint.isClosed()) {
            borderPaint = new Paint();
            borderPaint.setAntiAlias(true);
        }
    }

    /** Row flow with the bar's padding and gap baked in. */
    private static final class MenuBarLayout extends FlexLayout {
        MenuBarLayout() {
            super(Direction.ROW, Justify.FLEX_START, Align.CENTER);
            setGap(ITEM_GAP);
            setPadding(CONTENT_PADDING);
        }
    }

    @Override
    protected void onDispose() {
        close(bgPaint);
        bgPaint = null;
        close(borderPaint);
        borderPaint = null;
        super.onDispose();
    }

    private static void close(Paint paint) {
        if (paint != null && !paint.isClosed()) {
            paint.close();
        }
    }
}
