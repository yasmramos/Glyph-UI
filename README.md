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

`Application` implements `AutoCloseable`, so the recommended pattern is
try-with-resources. Closing the application disposes the whole component tree
in cascade (each widget releases its native paints/fonts), then frees the
shared font cache, the Skija surface, the GPU context and finally the GLFW
window - always in the correct dependency order:

```java
import com.glyphui.core.Application;
import com.glyphui.ui.Button;

public class MyApp {
    public static void main(String[] args) {
        try (Application app = new Application()) {

            if (!app.init("My App", 800, 600)) {
                System.err.println("Failed to initialize");
                return;
            }

            // Create a button
            Button button = new Button(100, 100, 150, 40, "Click Me!");
            button.setOnClick(() -> {
                System.out.println("Button clicked!");
            });

            // Add to root panel (ownership transfers to the panel)
            app.getRootPanel().add(button);

            app.run();
        }
        // app.close() ran automatically: components, fonts, surface,
        // GPU context and window were released in order.
    }
}
```

Components (`Button`, `Label`, `Panel`, ...) also implement `AutoCloseable`;
`close()` delegates to `dispose()` and `Panel` closes all of its children
recursively, so standalone widgets can be managed with try-with-resources too:

```java
try (Button button = new Button("Click Me!")) {
    // use the button...
}   // native paints and font released here
```

For backward compatibility, `app.destroy()` still exists as an alias for
`app.close()`.

## Threading

Glyph UI runs all widget state changes, layout and rendering on a single UI
thread - the thread that calls `Application.run()`. All user callbacks
(`Button.setOnClick(...)`, key/mouse listeners dispatched from the event loop)
therefore always execute **on the UI thread by construction**; you never need
to synchronize inside them.

**Mutating widgets from any other thread is safe via properties.** Each widget
exposes observable properties (`button.textProperty().set(...)`,
`label.textProperty()`, and on every component: `xProperty()`, `yProperty()`,
`widthProperty()`, `heightProperty()`, `visibleProperty()`, `enabledProperty()`).
When `Property.set(...)` is called from a background thread, the framework
marshals the change onto the UI thread automatically (queue +
`glfwPostEmptyEvent` wakeup), marks the frame dirty and notifies listeners on
the UI thread. The classic setters (`setText`, `setVisible`, ...) delegate to
these properties, so they are equally thread-safe:

```java
// From a worker thread - no synchronization needed:
Thread.ofVirtual().start(() -> {
    button.textProperty().set("Loaded!");   // marshalled to the UI thread
});
```

Properties also support listeners and one-way bindings:

```java
Property<String> model = Property.of("");
label.textProperty().bind(model);           // label mirrors the model
model.addListener((p, oldV, newV) -> System.out.println(oldV + " -> " + newV));
```

Use `Application.invokeLater(Runnable)` only for **composite operations** that
cannot be expressed as a single property change (e.g. add several children and
re-layout atomically). It is also the escape hatch used internally by
properties. `Application.isUiThread()` / `checkThread()` let you assert or
detect the current thread when writing custom components.

The event loop blocks in `glfwWaitEvents()` while idle (GPU backend), so
cross-thread updates applied through properties wake it up immediately instead
of waiting for the next poll tick.

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

Screenshot artifacts from CI builds can be downloaded from GitHub Actions workflow runs.

## Contributing

Contributions are welcome! Please feel free to submit issues and pull requests.