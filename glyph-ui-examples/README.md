# Glyph UI Examples

Standalone Maven project containing runnable example applications for the
[Glyph UI](../README.md) toolkit. Keeping the demos out of the library jar
means the published `com.glyphui:glyph-ui` artifact stays clean (no main
class, no demo resources).

## Contents

| Example | Entry point | Description |
|---------|-------------|-------------|
| Buttons demo | `com.glyphui.examples.DemoButtons` | Three buttons in a `FlowLayout`, click handlers |
| Declarative markup demo | `src/main/resources/demo/ui.html` + `app.css` | HTML-like markup + CSS subset loaded by `UiLoader` |

## Requirements

- Java 17+
- Maven 3.6+
- The `glyph-ui` library installed in your local repository

## Running

From the repository root, install the library first:

```bash
mvn install -DskipTests
```

Then run an example from this directory:

```bash
cd glyph-ui-examples
mvn exec:java            # runs com.glyphui.examples.DemoButtons
```

On macOS add the required JVM argument:

```bash
mvn exec:java -Dexec.vmArgs="-XstartOnFirstThread"
```

## Adding new examples

1. Create a class under `src/main/java/com/glyphui/examples/`.
2. Put any markup/CSS assets under `src/main/resources/`.
3. All code, comments and documentation in this project must be written in
   English.
