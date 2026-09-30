# Glyph UI

A modern UI toolkit built on Skija and LWJGL for Java 17+.

## Features

- Modern rendering engine powered by Skija (Skia for Java)
- Window management and event handling via LWJGL/GLFW
- Component-based UI architecture
- Support for rounded corners, custom colors, and text rendering
- Event system for mouse and keyboard input
- Layout managers for automatic component positioning
- Cross-platform support (Windows, Linux, macOS)

## Requirements

- Java 17 or higher
- Maven 3.6+
- OpenGL 3.3+ compatible graphics card

## Platform-Specific Setup

### macOS

On macOS, you need to add the `-XstartOnFirstThread` JVM argument:

```bash
mvn exec:java -Dexec.mainClass="com.glyphui.Main" -Dexec.vmArgs="-XstartOnFirstThread"
```

Or configure your IDE to include this VM argument when running the application.

## Building

```bash
mvn clean compile
```

## Running

```bash
mvn exec:java -Dexec.mainClass="com.glyphui.Main"
```

On macOS:
```bash
mvn exec:java -Dexec.mainClass="com.glyphui.Main" -Dexec.vmArgs="-XstartOnFirstThread"
```

## Project Structure

```
src/main/java/com/glyphui/
├── Main.java                 # Example application entry point
├── core/
│   ├── Application.java      # Main application class with event loop
│   └── Window.java           # Window abstraction using GLFW
├── graphics/
│   └── Canvas.java           # Skija drawing wrapper
├── ui/
│   ├── Component.java        # Base class for all UI components
│   ├── Panel.java            # Container for components
│   ├── Button.java           # Clickable button component
│   ├── Label.java            # Text label component
│   └── ComponentState.java   # Component state enum
├── events/
│   ├── MouseEvent.java       # Mouse event class
│   ├── KeyEvent.java         # Key event class
│   ├── MouseEventType.java   # Mouse event type enum
│   ├── MouseButton.java      # Mouse button enum
│   ├── KeyEventType.java     # Key event type enum
│   └── KeyModifier.java      # Key modifier enum
└── layout/
    ├── LayoutManager.java    # Base class for layout managers
    └── FlowLayout.java       # Flow layout implementation
```

## Usage Example

```java
import com.glyphui.core.Application;
import com.glyphui.ui.Button;

public class MyApp {
    public static void main(String[] args) {
        Application app = new Application();
        
        if (!app.init("My App", 800, 600)) {
            System.err.println("Failed to initialize");
            return;
        }

        // Create a button
        Button button = new Button(100, 100, 150, 40, "Click Me!");
        button.setOnClick(() -> {
            System.out.println("Button clicked!");
        });

        // Add to root panel
        app.getRootPanel().add(button);

        try {
            app.run();
        } finally {
            app.destroy();
        }
    }
}
```

## Dependencies

- **Skija** (io.github.humbleui:skija-*) - Skia graphics library for Java
- **LWJGL 3** - Lightweight Java Game Library for windowing and input
  - lwjgl
  - lwjgl-glfw
  - lwjgl-opengl

## License

This project is open source. See the LICENSE file for details.

## Testing

### Running Tests

```bash
mvn test
```

### Integration Tests

The project includes integration tests that create actual GLFW windows and capture screenshots. These tests require a display server:

- **Linux**: Install Xvfb (`sudo apt-get install xvfb`) and run with `xvfb-run -a mvn test`
- **macOS/Windows**: A physical display is required, or use virtual display software

**Note on Monocle**: Monocle is specific to JavaFX and does NOT apply to this toolkit, which uses LWJGL/GLFW + Skija for rendering.

### Raster mode (`useRasterSurface = true`)

`Application.init(title, width, height, true)` creates an off-screen raster surface instead of a GPU-backed one. **A raster surface is never presented to the GLFW window** (no GL framebuffer is involved); it exists exclusively for headless/testing scenarios such as screenshot capture via `captureToPng`. For visible on-screen output always use the GPU backend (`useRasterSurface = false`, the default).

Screenshot artifacts from CI builds can be downloaded from GitHub Actions workflow runs.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.