package com.glyphui.integration;

import com.glyphui.core.Application;
import com.glyphui.ui.Button;
import com.glyphui.ui.Label;
import com.glyphui.ui.Panel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that captures a screenshot of all UI components.
 * This test requires GLFW and will be skipped in headless environments.
 */
public class AllComponentsScreenshotIT {

    private Application app;
    private static final String SCREENSHOT_DIR = "screenshots";
    private static final String SCREENSHOT_FILE = "all-components.png";

    @BeforeEach
    public void setUp() {
        app = new Application();
        // Initialize with invisible window for automated testing
        boolean initialized = app.init("Glyph UI Integration Test", 800, 600);
        // Skip test if GLFW cannot be initialized (headless environment)
        Assumptions.assumeTrue(initialized, "GLFW not available (headless environment); skipping test");
    }

    @AfterEach
    public void tearDown() {
        if (app != null) {
            app.destroy();
        }
    }

    @Test
    public void testAllComponentsRendering() throws Exception {
        // Get root panel and set a layout manager
        Panel rootPanel = app.getRootPanel();
        
        // Create and add a Label
        Label label = new Label(20, 20, 300, 30, "Sample Label Text");
        rootPanel.add(label);
        
        // Create and add a Button
        Button button = new Button(20, 80, 200, 50, "Click Me!");
        button.setOnClick(() -> System.out.println("Button clicked!"));
        rootPanel.add(button);
        
        // Create a nested Panel with its own children
        Panel nestedPanel = new Panel(20, 160, 400, 300);
        nestedPanel.setBackgroundColor(io.github.humbleui.skija.Color.makeARGB(255, 50, 50, 70));
        
        // Add a Label inside the nested panel
        Label nestedLabel = new Label(20, 20, 200, 30, "Nested Label");
        nestedPanel.add(nestedLabel);
        
        // Add a Button inside the nested panel
        Button nestedButton = new Button(20, 80, 180, 45, "Nested Button");
        nestedButton.setOnClick(() -> System.out.println("Nested button clicked!"));
        nestedPanel.add(nestedButton);
        
        rootPanel.add(nestedPanel);
        
        // Define output file path - use absolute path to ensure it's in project root
        File outputFile = new File(System.getProperty("user.dir"), SCREENSHOT_DIR + File.separator + SCREENSHOT_FILE);
        
        // Capture screenshot
        app.captureToPng(outputFile);
        
        // Verify the file was created and is not empty
        assertTrue(outputFile.exists(), "Screenshot file should exist at: " + outputFile.getAbsolutePath());
        assertTrue(outputFile.length() > 0, "Screenshot file should not be empty");
        
        // Optional: Validate PNG header (first 8 bytes: 137 80 78 71 13 10 26 10)
        byte[] pngHeader = new byte[8];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(outputFile)) {
            int bytesRead = fis.read(pngHeader);
            assertEquals(8, bytesRead, "Should read 8 bytes for PNG header");
            
            // PNG magic number: 89 50 4E 47 0D 0A 1A 0A
            assertEquals((byte) 0x89, pngHeader[0]);
            assertEquals((byte) 0x50, pngHeader[1]); // 'P'
            assertEquals((byte) 0x4E, pngHeader[2]); // 'N'
            assertEquals((byte) 0x47, pngHeader[3]); // 'G'
            assertEquals((byte) 0x0D, pngHeader[4]);
            assertEquals((byte) 0x0A, pngHeader[5]);
            assertEquals((byte) 0x1A, pngHeader[6]);
            assertEquals((byte) 0x0A, pngHeader[7]);
        }
    }
}
