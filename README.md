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
cd glyph-ui-examples && mvn exec:java -Dexec.vmArgs="-XstartOnFirstThread"
```

Or configure your IDE to include this VM argument when running the application.

## Building

```bash
mvn clean compile
```

## Running the examples

Runnable demos live in the separate [`glyph-ui-examples`](glyph-ui-examples)
Maven project, which depends on the toolkit jar. Install the library locally
first, then run any example from the examples directory:

```bash
mvn install -DskipTests          # from the repository root (installs glyph-ui)
cd glyph-ui-examples
mvn exec:java                    # runs com.glyphui.examples.DemoButtons
```

On macOS:
```bash
mvn exec:java -Dexec.vmArgs="-XstartOnFirstThread"
```

### Choosing a window backend

The native window backend is resolved at runtime by `BackendFactory`:

| Value | Backend |
|-------|---------|
| *(unset)* | JWM when `io.github.humbleui:jwm` is on the classpath, otherwise GLFW |
| `glfw` | Legacy LWJGL/GLFW backend (OpenGL rendering) |
| `jwm` | JWM backend: native IME/clipboard/per-monitor DPI, raster rendering |
| `<fqcn>` | Any `WindowBackend` implementation with a `(String, int, int, WindowConfig)` constructor |

```bash
java -Dglyphui.backend=glfw -cp target/classes:<deps> com.glyphui.examples.DemoButtons
```

JWM owns the process UI thread: its native message loop is started by
`Application.run()` (through `WindowBackend.enterEventLoop`), not by
`Application.init()`, which stays fully headless until then. Frames are
blitted from the CPU surface onto the window layer via
`WindowBackend.present(...)`.

## Project Structure

```
src/main/java/com/glyphui/
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
├── markup/
│   ├── Tokenizer.java        # .glyph lexer (three-mode scanner)
│   ├── Parser.java           # .glyph recursive-descent parser -> AST
│   └── UiLoader.java         # AST -> widget tree mapper
└── layout/
    ├── LayoutManager.java    # Base class for layout managers
    └── FlowLayout.java       # Flow layout implementation

glyph-ui-examples/            # Separate Maven project with runnable demos
├── pom.xml                   # Depends on the glyph-ui artifact
└── src/main/
    ├── java/com/glyphui/examples/DemoButtons.java
    └── resources/demo/       # ui.glyph + app.css declarative markup demo
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

## Declarative UI: `.glyph` markup + CSS subset

Glyph-UI lets you declare interfaces in a `.glyph` markup file and style them
with a CSS subset, resolved against the live widget tree. `.glyph` is scanned
by the built-in `Tokenizer`/`Parser` (no third-party HTML parser) into a raw
AST that `UiLoader` maps onto widgets.

> **This is NOT a browser.** There is no JavaScript engine, no DOM, and only a
> documented subset of CSS is supported. Unknown properties/selectors are
> ignored with a warning.

### Markup (`UiLoader`)

```java
UiLoader loader = new UiLoader();
Panel root = (Panel) loader.load("/demo/ui.glyph", new MyController());

// A <link rel="stylesheet" href="app.css"/> found while parsing is exposed here:
StyleSheet sheet = loader.getLastStyleSheet();
if (sheet != null) {
    StyleEngine.apply(root, sheet);
}
```

`.glyph` syntax is XML-like:

```glyph
<!-- comments use the HTML form -->
<link rel="stylesheet" href="app.css"/>
<body>
  <div id="card" class="card" layout="flex">
    <label class="title">Hello</label>
    <button id="ok" onclick="onOk">OK</button>
  </div>
</body>
```

- Elements use `<Tag attr="value">children</Tag>` or the self-closing
  `<Tag/>` form; unquoted values (`layout=flex`) are accepted.
- Content may contain `{path.to.value}` interpolations and the literal-brace
  escapes `{{` / `}}`. The parser captures interpolations as `BindingNode`s,
  but wiring them to reactive properties is not implemented yet, so
  `UiLoader` reports them as warnings.
- Inline `<style>` blocks are **not** supported (the `{` delimiter collides
  with interpolations). Link an external sheet with
  `<link rel="stylesheet" href="app.css"/>` instead.

Supported tags (unknown tags become a generic `Panel` with a warning):

| Tag        | Widget      | Notes                                        |
|------------|-------------|----------------------------------------------|
| `div`      | `Panel`     | container; supports `layout="flex"/"flow"`   |
| `button`   | `Button`    | text from tag body; `onclick="methodName"`   |
| `label`, `p` | `Label`   | text from tag body                           |
| `input`    | `TextField` | `value` attribute; `onchange="methodName"`   |
| `img`      | `ImageView` | `src` resolved through the loader's `ImageProvider` |

Attributes: `class`, `id`, `style="..."` (inline CSS), `layout="flex"` /
`layout="flow"` on containers, and `onclick`/`onchange` which are resolved by
name against a registered controller object via reflection (no-arg or
component-arg public methods). `head`, `title`, `meta`, `link`, `style`,
`script` and `base` are document metadata and never become widgets; a `body`
element, when present, becomes the root container.

### CSS subset

Selectors supported: type (`button`), class (`.primary`), id (`#header`),
descendant (`panel .item`), and pseudo-classes `:hover`, `:focus`,
`:disabled`, `:active` — mapped to the widget's runtime `ComponentState`.
Cascade order: inline > id > class > type. Textual properties (`font-*`,
`color`) inherit down the tree. Custom variables `var(--name)` resolve against
the active `Theme` (`--bg`, `--fg`, `--accent`, `--border`) plus any custom
properties declared in the sheet.

Supported properties:

- `color`, `background` / `background-color` (hex #rgb/#rrggbb, rgb(), named)
- `opacity`
- `padding`, `margin` (single value or shorthand)
- `border-width`, `border-color`, `border-radius`
- `width`, `height`
- `font-family`, `font-size`, `font-weight`
- Flexbox layout subset on `layout="flex"` containers: `flex-direction`,
  `justify-content`, `align-items`, `gap`, `flex-grow`

Not supported (ignored with warning): positioning (`position`, `top/left`),
`grid`, animations/transitions, `float`, media queries, pseudo-elements,
shorthand `border`/`font` composites beyond the listed longhands.

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