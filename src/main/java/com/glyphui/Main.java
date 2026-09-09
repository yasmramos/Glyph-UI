package com.glyphui;

import com.glyphui.core.Application;
import com.glyphui.ui.Button;
import com.glyphui.layout.FlowLayout;

/**
 * Main entry point for the Glyph UI example application.
 */
public class Main {
    public static void main(String[] args) {
        // Create application instance
        Application app = new Application();

        // Initialize with window title and size
        if (!app.init("Glyph UI", 800, 600)) {
            System.err.println("Failed to initialize application");
            return;
        }

        // Set FlowLayout on root panel
        app.getRootPanel().setLayoutManager(new FlowLayout());

        // Create buttons
        Button button1 = new Button(0, 0, 200, 50, "Click here!");
        Button button2 = new Button(0, 0, 200, 50, "Second button");
        Button button3 = new Button(0, 0, 200, 50, "Third button");
        
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

        // Add buttons to root panel - they will be positioned automatically by FlowLayout
        app.getRootPanel().add(button1);
        app.getRootPanel().add(button2);
        app.getRootPanel().add(button3);

        try {
            // Run the application
            app.run();
        } finally {
            // Clean up resources
            app.destroy();
        }
    }
}
