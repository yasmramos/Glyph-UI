package io.github.yasmramos.glyphui.examples;

import io.github.yasmramos.glyphui.core.Application;
import io.github.yasmramos.glyphui.core.WindowConfig;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.TextField;
import io.github.yasmramos.glyphui.ui.TitleBar;

import java.util.Map;

/**
 * Example application exercising the main widgets: labels, buttons, a text
 * field and nested panels driven by {@link FlexLayout}.
 *
 * <p>The window uses a custom {@link TitleBar} (FlatLaf-style): the native
 * decoration is switched off and our own chrome strip hosts the window title,
 * menu content and the min/max/close controls. Below the bar sits a vertical
 * column of labelled sections, each a horizontal row of controls.</p>
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

            // Undecorated window: the native title bar is removed so our own
            // TitleBar becomes the chrome (and can host a menu in the strip).
            WindowConfig config = WindowConfig.create().decorated(false);
            if (!app.init("Glyph UI — widgets tour", 800, 600, config)) {
                System.err.println("Failed to initialize application");
                return;
            }

            // Root is a full-bleed column: the title bar sits flush at the top
            // edge and the content panel fills the rest of the window.
            FlexLayout rootLayout = new FlexLayout(FlexLayout.Direction.COLUMN,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.STRETCH);
            rootLayout.setPadding(0.0f);
            rootLayout.setGap(0.0f);
            Panel root = app.getRootPanel();
            root.setLayoutManager(rootLayout);

            // ---- Custom title bar with menu content -------------------------
            TitleBar titleBar = new TitleBar("Glyph UI");
            // Native-menubar look: the menus sit flush against each other, so
            // the gap between items is zero (each button keeps only its own
            // small padding, see menuButton below).
            titleBar.setItemGap(0.0f);
            titleBar.addContent(menuButton("Archivo"));
            titleBar.addContent(menuButton("Editar"));
            titleBar.addContent(menuButton("Ver"));
            root.add(titleBar);

            // ---- Content column (grows to fill the space below the bar) ----
            FlexLayout column = new FlexLayout(FlexLayout.Direction.COLUMN,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.FLEX_START);
            column.setPadding(24.0f);
            column.setGap(8.0f);
            Panel content = new Panel(0, 0);
            content.setLayoutManager(column);
            // flex-grow:1 makes the panel absorb the height left over after the
            // fixed-height title bar, keeping its content top-aligned.
            content.setInlineStyle(
                    Style.builder().setFloat(StyleProperty.FLEX_GROW, 1.0f).build());
            root.add(content);

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

    /** A small menu-strip button placed inside the title bar. */
    private static Button menuButton(String text) {
        Button button = new Button(text);
        // Flat menubar look: no chrome in the normal state (transparent fill and
        // border, square corners) so it blends with the title bar; hovering still
        // shows the theme's control hover/pressed highlight.
        button.setNormalColor(0x00000000);
        button.setBorderColor(0x00000000);
        button.setBorderRadius(0f);
        // Tight padding (the theme default is 12px) so neighbouring menus stay
        // close together, like a native menu bar.
        button.setInlineStyle(Style.parseInline("padding: 6px", Map.of()));
        button.setOnClick(() -> System.out.println("menu: " + text));
        return button;
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
