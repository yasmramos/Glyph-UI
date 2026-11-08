package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Window;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;

import java.util.Objects;

/**
 * The toolkit's high-level top-level window, the analogue of Swing's
 * {@code JFrame} over AWT's {@code Frame}: a {@link Window} that owns the
 * usual application chrome — an optional custom title bar, an optional menu
 * bar, and a content pane — so an application assembles its main window with
 * one object instead of wiring the root panel by hand.
 *
 * <p>A {@code Form} is still a {@link Window}: it is passed to
 * {@code Application.init(Window)} and behaves identically at the native
 * level. What it adds is a structural contract for the application's root
 * panel, installed when the application adopts the form:</p>
 *
 * <ul>
 *   <li><b>Content pane</b> — a {@link Panel} that fills the space left by the
 *       chrome (see {@link #getContentPane()}). Add widgets to it with
 *       {@link #add(Component)}; it grows vertically to absorb the leftover
 *       height, so content stays anchored below the chrome.</li>
 *   <li><b>Menu bar</b> — an optional {@link MenuBar} pinned under the title
 *       bar. Populate it with {@link #getMenuBar()} / {@link #addMenu(String)},
 *       or supply your own via {@link #setMenuBar(MenuBar)}.</li>
 *   <li><b>Custom title bar</b> — when the window is undecorated
 *       ({@code decorated(false)}), a {@link TitleBar} becomes the chrome: it
 *       hosts the window title, the menu strip and the min/max/close controls.
 *       Supply your own via {@link #setTitleBar(TitleBar)}. A decorated window
 *       keeps the native chrome and shows no title bar.</li>
 * </ul>
 *
 * <p>Typical use:</p>
 *
 * <pre>{@code
 * Form form = new Form("My App", 1024, 720).decorated(false);
 * form.addMenu("File").addItem("Quit", () -> form.requestClose());
 * form.add(new Label("Hello, Glyph UI"));
 * try (Application app = new Application()) {
 *     if (!app.init(form)) {
 *         return;
 *     }
 *     app.run();
 * }
 * }</pre>
 *
 * <p><strong>Fluent setters return {@code Form}.</strong> The creation-time
 * setters inherited from {@link Window} are overridden with a covariant return
 * type, so a chain keeps the {@code Form} type and the result can be handed
 * straight to {@code Application.init(Window)}.</p>
 */
public class Form extends Window {

    /** The application content area; never null. */
    private Panel contentPane;

    /** Optional menu bar shown under the title bar; null when absent. */
    private MenuBar menuBar;

    /** Custom title bar for undecorated windows; created on demand. */
    private TitleBar titleBar;

    /** Root panel once the application has adopted this form; null before. */
    private Panel rootPanel;

    /**
     * Creates a form with the given title and logical size, using the default
     * window attributes (visible, resizable, decorated, centered).
     *
     * @param title  the window title
     * @param width  the logical width
     * @param height the logical height
     */
    public Form(String title, int width, int height) {
        super(title, width, height);
        this.contentPane = new Panel();
    }

    // ------------------------------------------------------------------
    // Structure: content pane, menu bar, title bar
    // ------------------------------------------------------------------

    /**
     * The content pane: the panel that holds the application's widgets. It
     * fills the space below the chrome and grows to absorb leftover height.
     *
     * @return the content pane (never null)
     */
    public Panel getContentPane() {
        return contentPane;
    }

    /**
     * Replaces the content pane. Takes effect immediately when the form has
     * already been adopted by an application, otherwise on adoption.
     *
     * @param contentPane the new content pane (must not be null)
     * @return this form
     */
    public Form setContentPane(Panel contentPane) {
        this.contentPane = Objects.requireNonNull(contentPane, "contentPane");
        rebuild();
        return this;
    }

    /**
     * Adds a widget to the content pane. Convenience for
     * {@code getContentPane().add(component)}.
     *
     * @param component the widget to add
     * @return this form
     */
    public Form add(Component component) {
        contentPane.add(component);
        return this;
    }

    /**
     * The menu bar, creating an empty one on first use. The bar is shown under
     * the title bar when the form is mounted.
     *
     * @return the menu bar (never null)
     */
    public MenuBar getMenuBar() {
        if (menuBar == null) {
            menuBar = new MenuBar();
            rebuild();
        }
        return menuBar;
    }

    /**
     * Replaces (or removes, with {@code null}) the menu bar. Takes effect
     * immediately when the form has already been adopted.
     *
     * @param menuBar the menu bar to install, or null to remove it
     * @return this form
     */
    public Form setMenuBar(MenuBar menuBar) {
        this.menuBar = menuBar;
        rebuild();
        return this;
    }

    /**
     * Appends a menu to the menu bar, creating the bar on first use.
     *
     * @param title the menu trigger caption
     * @return the new menu, for adding items
     */
    public Menu addMenu(String title) {
        return getMenuBar().addMenu(title);
    }

    /**
     * The custom title bar used when this form is undecorated, or {@code null}
     * when none has been installed yet. A decorated window never shows it.
     *
     * @return the title bar, or null
     */
    public TitleBar getTitleBar() {
        return titleBar;
    }

    /**
     * Installs a custom title bar for an undecorated window, replacing any
     * previously installed one. Ignored for a decorated window (the native
     * chrome stays). Takes effect immediately when the form is already
     * adopted.
     *
     * @param titleBar the title bar to install, or null to remove it
     * @return this form
     */
    public Form setTitleBar(TitleBar titleBar) {
        this.titleBar = titleBar;
        rebuild();
        return this;
    }

    // ------------------------------------------------------------------
    // Mounting
    // ------------------------------------------------------------------

    /**
     * Builds the application root panel's structure: a full-bleed vertical
     * column holding the optional title bar, the optional menu bar and the
     * content pane, which grows to fill the rest. Called by
     * {@code Application} once the window is adopted.
     *
     * @param rootPanel the application root panel (never null)
     */
    @Override
    protected void onAdopted(Panel rootPanel) {
        this.rootPanel = rootPanel;
        FlexLayout layout = new FlexLayout(FlexLayout.Direction.COLUMN,
                FlexLayout.Justify.FLEX_START, FlexLayout.Align.STRETCH);
        layout.setPadding(0.0f);
        layout.setGap(0.0f);
        rootPanel.setLayoutManager(layout);
        rebuild();
    }

    /**
     * (Re)assembles the root panel from the current chrome and content. Safe
     * to call before adoption (no-op until a root panel exists).
     */
    private void rebuild() {
        if (rootPanel == null) {
            return;
        }
        rootPanel.clear();
        if (!isDecorated()) {
            if (titleBar == null) {
                titleBar = new TitleBar(getTitle());
            }
            rootPanel.add(titleBar);
        }
        if (menuBar != null) {
            rootPanel.add(menuBar);
        }
        // Make the content pane absorb the leftover height so content sits
        // below the chrome. Respect a flex-grow the application already set.
        if (!contentPane.getInlineStyle().has(StyleProperty.FLEX_GROW)) {
            contentPane.setInlineStyle(Style.builder()
                    .setFloat(StyleProperty.FLEX_GROW, 1.0f).build());
        }
        rootPanel.add(contentPane);
    }

    // ------------------------------------------------------------------
    // Covariant fluent setters (keep the Form type while chaining)
    // ------------------------------------------------------------------

    @Override
    public Form title(String title) {
        setTitle(title);
        return this;
    }

    /**
     * Updates the window title and keeps a custom title bar in step.
     *
     * @param title the new title
     */
    @Override
    public void setTitle(String title) {
        super.setTitle(title);
        if (titleBar != null) {
            titleBar.setTitle(title);
        }
    }

    @Override
    public Form size(int width, int height) {
        super.size(width, height);
        return this;
    }

    @Override
    public Form decorated(boolean decorated) {
        super.decorated(decorated);
        return this;
    }

    @Override
    public Form resizable(boolean resizable) {
        super.resizable(resizable);
        return this;
    }

    @Override
    public Form visible(boolean visible) {
        super.visible(visible);
        return this;
    }

    @Override
    public Form floating(boolean floating) {
        super.floating(floating);
        return this;
    }

    @Override
    public Form maximized(boolean maximized) {
        super.maximized(maximized);
        return this;
    }

    @Override
    public Form fullscreen(boolean fullscreen) {
        super.fullscreen(fullscreen);
        return this;
    }

    @Override
    public Form centered(boolean centered) {
        super.centered(centered);
        return this;
    }
}
