package com.glyphui.examples;

import com.glyphui.core.Application;
import com.glyphui.graphics.Theme;
import com.glyphui.layout.FlexLayout;
import com.glyphui.ui.Button;
import com.glyphui.ui.Label;
import com.glyphui.ui.Panel;
import com.glyphui.ui.TextField;

/**
 * Example application exercising the main widgets: labels, buttons, a text
 * field and nested panels driven by {@link FlexLayout}.
 *
 * <p>The window is a vertical column of labelled sections, each section being a
 * horizontal row of controls. {@link FlexLayout} (unlike {@code FlowLayout})
 * aligns children on a common grid, which is what keeps a mixed set of widgets
 * looking tidy.</p>
 *
 * <p>Moved out of the core library into the standalone
 * {@code glyph-ui-examples} module so the published toolkit jar stays free
 * of demo entry points.</p>
 */
public class DemoButtons {

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

            // Initialize with window title and size
            if (!app.init("Glyph UI — widgets tour", 800, 600)) {
                System.err.println("Failed to initialize application");
                return;
            }

            // The window is a single column of sections, top-aligned, with a
            // consistent gap and page margin.
            FlexLayout column = new FlexLayout(FlexLayout.Direction.COLUMN,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.FLEX_START);
            column.setPadding(24.0f);
            column.setGap(8.0f);
            Panel root = app.getRootPanel();
            root.setLayoutManager(column);

            // ---- Header -----------------------------------------------------
            Label title = new Label("Glyph UI");
            title.setFontSize(28.0f);
            root.add(title);

            Label subtitle = new Label("Buttons, text fields and nested layouts");
            subtitle.setTextColor(MUTED);
            root.add(subtitle);

            // ---- Interactive counter ----------------------------------------
            root.add(caption("Counter (nested panels + FlexLayout)"));

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
            root.add(counterRow);

            // ---- Text field wired to a label --------------------------------
            root.add(caption("TextField (placeholder + live echo)"));

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
            root.add(fieldRow);
            root.add(echo);

            // ---- Buttons: intrinsic vs. fixed size --------------------------
            root.add(caption("Buttons (auto-sized and fixed)"));

            // Preferred sizes come from the measured text, so no bounds are
            // needed under a layout manager; an explicit size wins over it.
            Button primary = new Button("Click here!");
            Button secondary = new Button("Second button");
            Button fixed = new Button(0, 0, 200, ROW, "Fixed size button");

            primary.setOnClick(() -> System.out.println("Hello Glyph UI!"));
            secondary.setOnClick(() -> System.out.println("Second button clicked!"));
            fixed.setOnClick(() -> System.out.println("Third button clicked!"));

            Panel buttonsRow = row();
            buttonsRow.setLayoutManager(new FlexLayout(FlexLayout.Direction.ROW,
                    FlexLayout.Justify.FLEX_START, FlexLayout.Align.CENTER));
            buttonsRow.add(primary);
            buttonsRow.add(secondary);
            buttonsRow.add(fixed);
            root.add(buttonsRow);

            // ---- Labels: multi-line captions -------------------------------
            root.add(caption("Label (multi-line, one line per \\n)"));

            Label verse = new Label("Labels hold any text.\nSecond line, same box.\nThird line too.");
            verse.setTextColor(Theme.current().getForegroundColor());
            root.add(verse);

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

    /**
     * A full-column control row. Transparent so it inherits the page look; its
     * only job is to give FlexLayout a box to align its children in.
     */
    private static Panel row() {
        Panel panel = new Panel(0, 0, COLUMN, ROW);
        panel.setBackgroundColor(Theme.current().getBackgroundColor());
        return panel;
    }
}
