package com.glyphui.examples;

import com.glyphui.core.Application;
import com.glyphui.ui.Button;
import com.glyphui.layout.FlowLayout;

/**
 * Example application: three buttons laid out with a FlowLayout.
 *
 * <p>Moved out of the core library into the standalone
 * {@code glyph-ui-examples} module so the published toolkit jar stays free
 * of demo entry points.</p>
 */
public class DemoButtons {
    public static void main(String[] args) {
        // Application owns all native resources (surface, GPU context, window);
        // try-with-resources guarantees they are released in the correct order.
        try (Application app = new Application()) {

            // Initialize with window title and size
            if (!app.init("Glyph UI", 800, 600)) {
                System.err.println("Failed to initialize application");
                return;
            }

            // Set FlowLayout on root panel
            app.getRootPanel().setLayoutManager(new FlowLayout());

            // Create buttons. Preferred sizes are derived from the measured text,
            // so no explicit bounds are needed when using FlowLayout.
            Button button1 = new Button("Click here!");
            Button button2 = new Button("Second button");
            Button button3 = new Button(0, 0, 200, 50, "Fixed size button");

            // Set click handler
            button1.setOnClick(() -> {
                System.out.println("Hello Glyph UI!");
            });
            button2.setOnClick(() -> {
                System.out.println("Second button clicked!");
            });
            button3.setOnClick(() -> {
                System.out.println("Third button clicked!");
            });

            // Add buttons to root panel - they will be positioned automatically
            // by FlowLayout. Ownership transfers to the panel: closing the app
            // disposes them in cascade.
            app.getRootPanel().add(button1);
            app.getRootPanel().add(button2);
            app.getRootPanel().add(button3);

            // Run the application
            app.run();
        }
    }
}
