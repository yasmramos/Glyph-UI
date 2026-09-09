package com.glyphui;

import com.glyphui.core.Application;
import com.glyphui.ui.Button;

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

        // Create a button
        Button button = new Button(300, 250, 200, 50, "Click here!");
        
        // Set click handler
        button.setOnClick(() -> {
            System.out.println("Hello Glyph UI!");
        });

        // Add button to root panel
        app.getRootPanel().add(button);

        try {
            // Run the application
            app.run();
        } finally {
            // Clean up resources
            app.destroy();
        }
    }
}
