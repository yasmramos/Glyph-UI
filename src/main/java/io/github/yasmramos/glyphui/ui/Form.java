package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.core.Application;
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
 *   <li><b>Menu bar</b> — an optional {@link MenuBar}. Populate it with
 *       {@link #getMenuBar()} / {@link #addMenu(String)}, or supply your own via
 *       {@link #setMenuBar(MenuBar)}. On an undecorated form the menu bar is
 *       embedded into the title bar's leading zone (FlatLaf-style), so the menus
 *       sit at the window's left; on a decorated form it is a separate row under
 *       the native title bar.</li>
 *   <li><b>Custom title bar</b> — when the window is undecorated
 *       ({@code decorated(false)}), a {@link TitleBar} becomes the chrome: it
 *       hosts the menu strip (leading), the window title (centre) and the
 *       min/max/close controls (trailing). Supply your own via
 *       {@link #setTitleBar(TitleBar)}. A decorated window keeps the native
 *       chrome and shows no title bar.</li>
 * </ul>
 *
 * <p>Typical use — the Swing-style standalone form, with no {@code Application}
 * in sight:</p>
 *
 * <pre>{@code
 * public static void main(String[] args) {
 *     Form form = new Form("My App", 1024, 720).decorated(false);
 *     form.addMenu("File").addItem("Quit", () -> form.dispose());
 *     form.add(new Label("Hello, Glyph UI"));
 *     form.setVisible(true);   // shows the window and runs until it closes
 * }
 * }</pre>
 *
 * <p>Or, when an application object is already at hand, the one-call factory
 * keeps the two in step: {@code app.createForm("My App", 1024, 720)}. Both
 * paths produce the same window; the difference is only who owns the
 * application and drives the event loop.</p>
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

    /** The application that owns this form; null until the form is shown. */
    private Application application;

    /** True when this form started its own application (standalone use). */
    private boolean ownsApplication;

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
     * The custom title bar used when this form is undecorated. Created on first
     * use with the current window title. A decorated window never shows it.
     *
     * @return the title bar (never null)
     */
    public TitleBar getTitleBar() {
        if (titleBar == null) {
            titleBar = new TitleBar(getTitle());
        }
        return titleBar;
    }

    /**
     * Installs a custom title bar for an undecorated window, replacing any
     * previously installed one. Ignored for a decorated window (the native
     * chrome stays). Takes effect immediately when the form is already
     * adopted.
     *
     * @param titleBar the title bar to install, or null to revert to the
     *                 lazily created default
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
        // The application bound the root panel before calling this hook, so the
        // context tells us who adopted the form (createForm/init path).
        if (rootPanel.getContext() != null) {
            this.application = rootPanel.getContext().app();
        }
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
     *
     * <p>On an undecorated form the menu bar is embedded into the title bar's
     * leading zone (FlatLaf-style) instead of taking its own row; on a decorated
     * form it stays a separate row under the native title bar.</p>
     */
    private void rebuild() {
        if (rootPanel == null) {
            return;
        }
        boolean undecorated = !isDecorated();
        if (undecorated) {
            // Keep the lazily created default bar in step with the window title.
            TitleBar bar = getTitleBar();
            bar.setTitle(getTitle());
            bar.setMenuBar(menuBar);
        }
        rootPanel.clear();
        if (undecorated) {
            rootPanel.add(titleBar);
        } else if (menuBar != null) {
            menuBar.setEmbedded(false);
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
    // Swing-style lifecycle: show and run without an explicit Application
    // ------------------------------------------------------------------

    /**
     * Shows this form and, when it is not already owned by an application,
     * starts one and runs the event loop on the calling thread until the window
     * closes — the Swing-style entry point that hides {@code Application} from
     * the caller:
     *
     * <pre>{@code
     * Form form = new Form("My App", 1024, 720);
     * form.add(new Label("Hello"));
     * form.setVisible(true);   // returns after the window is closed
     * }</pre>
     *
     * <p><strong>It blocks.</strong> The toolkit's windowing backend (JWM)
     * requires the process main thread and owns its native message loop, so
     * there is no separate event-dispatch thread to hand the loop to (unlike
     * Swing's EDT). {@code setVisible(true)} therefore shows the window and
     * keeps the calling thread inside the loop until {@link #dispose()} (or the
     * window's close button) ends it. Call it last, from {@code main}.</p>
     *
     * <p>When the form is already owned by an application — the
     * {@code Application#createForm} path, or a form passed to
     * {@code Application.init(Window)} — this method only shows the window and
     * returns; that application's {@code run()} drives the loop instead.</p>
     *
     * @param visible true to show and run; false to hide (or, for a self-owned
     *                form, to close it and end the loop)
     */
    public void setVisible(boolean visible) {
        if (visible) {
            if (rootPanel != null) {
                // Already adopted by an application (createForm/init path): the
                // owner drives the loop, so just present the window.
                super.show();
                return;
            }
            showAndRun();
            return;
        }
        if (ownsApplication) {
            // Self-owned loop: asking to hide is asking to end the program.
            requestClose();
            return;
        }
        super.hide();
    }

    /**
     * Closes this form, releasing its native resources. For a standalone form
     * (shown with {@link #setVisible(boolean)}) this ends the event loop and
     * returns control to {@code main}; for a form owned by an application it
     * behaves like {@link Window#close()}.
     */
    public void dispose() {
        if (ownsApplication) {
            requestClose();
            return;
        }
        close();
    }

    /**
     * Creates an application, adopts this form and runs its event loop until
     * the window closes, then releases the application. Called by
     * {@link #setVisible(boolean)} when the form has no owner yet.
     */
    private void showAndRun() {
        Application app = new Application();
        if (!app.init(this)) {
            app.close();
            throw new IllegalStateException("Failed to initialize the Form window");
        }
        application = app;
        ownsApplication = true;
        try {
            app.run();
        } finally {
            app.close();
            // close() disposed the root panel tree; drop the references so a
            // later setVisible(true) starts from a clean slate instead of
            // touching disposed widgets.
            rootPanel = null;
            application = null;
            ownsApplication = false;
        }
    }

    /**
     * The application that owns this form, or {@code null} when it has not been
     * shown yet. For a standalone form this is the application
     * {@link #setVisible(boolean)} created and is cleared again once the loop
     * ends.
     *
     * @return the owning application, or null
     */
    public Application getApplication() {
        return application;
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
        setDecorated(decorated);
        return this;
    }

    /**
     * Toggles the native decoration and re-mounts the chrome so the change is
     * visible immediately. Because a window's native decoration is fixed at
     * creation time by some backends, applications normally set this through
     * the fluent {@link #decorated(boolean)} <em>before</em> the window is
     * created; a change after adoption still re-mounts the root panel (title
     * bar on/off) so the in-app chrome stays consistent with the hint.
     *
     * @param decorated true for the native title bar, false for custom chrome
     */
    @Override
    public void setDecorated(boolean decorated) {
        super.setDecorated(decorated);
        rebuild();
    }

    @Override
    public Form resizable(boolean resizable) {
        super.resizable(resizable);
        return this;
    }

    @Override
    public Form visible(boolean visible) {
        setVisible(visible);
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
