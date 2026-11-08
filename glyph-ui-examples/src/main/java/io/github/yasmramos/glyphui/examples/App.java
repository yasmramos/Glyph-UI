package io.github.yasmramos.glyphui.examples;

import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.graphics.Image;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Form;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Menu;
import io.github.yasmramos.glyphui.ui.MenuItem;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.PasswordField;
import io.github.yasmramos.glyphui.ui.TextArea;
import io.github.yasmramos.glyphui.ui.TextField;

import java.io.IOException;

/**
 * Example application exercising the main widgets: labels, buttons, a text
 * field and nested panels driven by {@link FlexLayout}.
 *
 * <p>The main window is a {@link Form} — the toolkit's {@code JFrame}-style
 * top-level window. The form is undecorated, so its own
 * {@link io.github.yasmramos.glyphui.ui.TitleBar} becomes the chrome and hosts
 * the menu bar in its leading zone, the window title in its centre and the
 * min/max/close controls at its trailing edge. Below the chrome sits the form's
 * content pane, a vertical column of labelled sections, each a horizontal row
 * of controls.</p>
 *
 * <p>Moved out of the core library into the standalone
 * {@code glyph-ui-examples} module so the published toolkit jar stays free
 * of demo entry points.</p>
 */
public class App {

    /** Neutral grey that reads well on the default theme background. */
    private static final int MUTED = 0xFF9CA3AF;
    /** Content column width, shared by every section so edges line up. */
    private static final float COLUMN = 520.0f;
    /** Height of a control row. */
    private static final float ROW = 44.0f;

    public static void main(String[] args) {
        // Application owns all native resources (surface, GPU context, window);
        // try-with-resources guarantees they are released in the correct order.
        try (Application app = new Application()) {

            // Undecorated Form: the native title bar is removed so the form's
            // own TitleBar becomes the chrome. The menu bar is embedded into the
            // title bar's leading zone, so the menus sit at the window's left and
            // the title stays centred between them and the window controls.
            Form form = new Form("Glyph UI — widgets tour", 800, 600)
                    .decorated(false);
            fillArchivo(form.addMenu("Archivo"));
            fillEditar(form.addMenu("Editar"));
            fillVer(form.addMenu("Ver"));

            if (!app.init(form)) {
                System.err.println("Failed to initialize application");
                return;
            }

            // ---- Content column (grows to fill the space below the bar) ----
            FlexLayout column = new FlexLayout(FlexLayout.Direction.COLUMN,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.FLEX_START);
            column.setPadding(24.0f);
            column.setGap(8.0f);
            Panel content = form.getContentPane();
            content.setLayoutManager(column);

            // ---- Header -----------------------------------------------------
            Label title = new Label("Glyph UI");
            title.setFontSize(28.0f);
            content.add(title);

            Label subtitle = new Label("Buttons, text fields and nested layouts");
            subtitle.setTextColor(MUTED);
            content.add(subtitle);

            // ---- Interactive counter ----------------------------------------
            content.add(caption("Counter (nested panels + FlexLayout)"));

            // A stateful widget: the click handler mutates a label, which is
            // repainted on demand (setText requests the repaint implicitly).
            Label counterValue = new Label("0");
            counterValue.setFontSize(20.0f);

            final int[] count = {0};
            Button decrement = new Button("−");
            Button increment = new Button("+");
            increment.setOnClick(() -> counterValue.setText(String.valueOf(++count[0])));
            decrement.setOnClick(() -> counterValue.setText(String.valueOf(--count[0])));

            Panel counterRow = row();
            counterRow.setLayoutManager(new FlexLayout(FlexLayout.Direction.ROW,
                    FlexLayout.Justify.CENTER, FlexLayout.Align.CENTER));
            counterRow.add(decrement);
            counterRow.add(counterValue);
            counterRow.add(increment);
            content.add(counterRow);

            // ---- Text field wired to a label --------------------------------
            content.add(caption("TextField (placeholder + live echo)"));

            // The handler runs on the UI thread, so mutating other widgets from
            // it needs no synchronization.
            Label echo = new Label("Type above…");
            echo.setTextColor(MUTED);

            TextField field = new TextField();
            field.setPlaceholder("Your name");
            field.setOnTextChanged(() -> {
                String text = field.getText();
                echo.setText(text.isEmpty() ? "Type above…" : "Hello, " + text + "!");
            });

            Panel fieldRow = row();
            fieldRow.setLayoutManager(new FlexLayout(FlexLayout.Direction.ROW,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.CENTER));
            fieldRow.add(field);
            content.add(fieldRow);
            content.add(echo);

            // ---- Masked and multi-line inputs -------------------------------
            content.add(caption("PasswordField (masked) and TextArea (multi-line)"));

            PasswordField password = new PasswordField();
            password.setPlaceholder("Password");
            password.setWidth(COLUMN);

            TextArea notes = new TextArea();
            notes.setPlaceholder("Notes…");
            notes.setWidth(COLUMN);
            notes.setHeight(96.0f);
            notes.setText("First line\nSecond line");

            Panel passwordRow = row();
            passwordRow.setLayoutManager(new FlexLayout(FlexLayout.Direction.ROW,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.CENTER));
            passwordRow.add(password);
            content.add(passwordRow);

            // The multi-line area needs a taller row than the default one.
            Panel notesRow = row();
            notesRow.setHeight(112.0f);
            notesRow.setLayoutManager(new FlexLayout(FlexLayout.Direction.ROW,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.CENTER));
            notesRow.add(notes);
            content.add(notesRow);

            // ---- Buttons: intrinsic vs. fixed size --------------------------
            content.add(caption("Buttons (auto-sized and fixed)"));

            // Preferred sizes come from the measured text, so no bounds are
            // needed under a layout manager; an explicit size wins over it.
            Button primary = new Button("Click here!");
            Button secondary = new Button("Second button");
            Button fixed = new Button(200, ROW, "Fixed size button");

            primary.setOnClick(() -> System.out.println("Hello Glyph UI!"));
            secondary.setOnClick(() -> System.out.println("Second button clicked!"));
            fixed.setOnClick(() -> System.out.println("Third button clicked!"));

            Panel buttonsRow = row();
            buttonsRow.setLayoutManager(new FlexLayout(FlexLayout.Direction.ROW,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.CENTER));
            buttonsRow.add(primary);
            buttonsRow.add(secondary);
            buttonsRow.add(fixed);
            content.add(buttonsRow);

            // ---- Labels: multi-line captions -------------------------------
            content.add(caption("Label (multi-line, one line per \\n)"));

            Label verse = new Label("Labels hold any text.\nSecond line, same box.\nThird line too.");
            verse.setTextColor(Theme.current().getForegroundColor());
            content.add(verse);

            // Run the application
            app.run();
        }
    }

    /** A muted caption label introducing one section. */
    private static Label caption(String text) {
        Label label = new Label(text);
        label.setTextColor(MUTED);
        return label;
    }

    // ------------------------------------------------------------------
    // Title-bar menus
    // ------------------------------------------------------------------

    /** File actions: rows with icons, shortcuts and a trailing separator. */
    private static void fillArchivo(Menu menu) {
        addItem(menu, "Nuevo", "Ctrl+N", icon("new"));
        addItem(menu, "Abrir…", "Ctrl+O", icon("open"));
        addItem(menu, "Guardar", "Ctrl+S", icon("save"));
        menu.addSeparator();
        addItem(menu, "Salir", "Alt+F4", null);
    }

    /** Edit actions: includes a disabled row (nothing to paste yet). */
    private static void fillEditar(Menu menu) {
        addItem(menu, "Deshacer", "Ctrl+Z", null);
        menu.addSeparator();
        addItem(menu, "Cortar", "Ctrl+X", null);
        addItem(menu, "Copiar", "Ctrl+C", null);
        MenuItem paste = addItem(menu, "Pegar", "Ctrl+V", null);
        // Disabled rows stay visible (so the shortcut is discoverable) but
        // ignore the pointer entirely.
        paste.setEnabled(false);
    }

    /** View options: checkable rows toggle their own check mark on click. */
    private static void fillVer(Menu menu) {
        MenuItem toolbar = addItem(menu, "Barra de herramientas", null, null);
        toolbar.setCheckable(true);
        toolbar.setChecked(true);
        toolbar.setOnClick(() -> System.out.println(
                "menu: Barra de herramientas = " + toolbar.isChecked()));
        MenuItem status = addItem(menu, "Barra de estado", null, null);
        status.setCheckable(true);
        status.setOnClick(() -> System.out.println(
                "menu: Barra de estado = " + status.isChecked()));
        menu.addSeparator();
        addItem(menu, "Acercar", "Ctrl++", null);
        addItem(menu, "Alejar", "Ctrl+−", null);
    }

    /** Appends an item with an accelerator and a printer-style action. */
    private static MenuItem addItem(Menu menu, String text, String accelerator, Image icon) {
        MenuItem item = menu.addItem(text, icon);
        item.setAcceleratorText(accelerator);
        item.setOnClick(() -> System.out.println("menu: " + text));
        return item;
    }

    /**
     * Loads a 16px menu icon from the classpath. Icons are decoration: a
     * missing file degrades to a plain row instead of breaking the menu.
     */
    private static Image icon(String name) {
        try {
            return Image.loadResource("/icons/" + name + ".png");
        } catch (IOException e) {
            System.err.println("menu icon missing: " + name + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * A full-column control row. Transparent so it inherits the page look; its
     * only job is to give FlexLayout a box to align its children in.
     */
    private static Panel row() {
        Panel panel = new Panel(COLUMN, ROW);
        panel.setBackgroundColor(Theme.current().getBackgroundColor());
        return panel;
    }
}
