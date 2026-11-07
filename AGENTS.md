# AGENTS.md

Repository notes for automated agents working on Glyph-UI.

## Project

Java 17+ UI toolkit. Sources under `src/main/java/io/github/yasmramos/glyphui/`.
The public API surface is limited to the `core`, `ui`, `graphics`, `layout`,
`style` and `events` packages; keep new public types inside them.

## Build and test

- Test only: `mvn -o test -Djacoco.skip=true`
- Full gate as CI runs it: `mvn -B verify`

`verify` runs the JaCoCo `check` goal (bound to the `verify` phase), which
enforces a bundle LINE ratio of 0.70 and a BRANCH ratio of 0.55. Skipping
JaCoCo locally (`-Djacoco.skip=true`) hides those failures, so run `verify`
before opening a pull request. Large additions of mostly-untested widget code
can drag the bundle ratio below the gate.

The Surefire reports in `target/surefire-reports/*.xml` are the quickest way to
aggregate results without reading the Maven log.

## Conventions

- All code, comments, Javadoc and docs in English.
- Conventional Commits.
- Commit author: `yasmramos <yasmramos95@gmail.com>`, no co-author trailer.
- Widget tests extend `TestBase`; rasterization smoke tests use
  `RasterCanvasTestFactory` with a real Skija surface.

## Declarative front-ends

`.glyph` documents have two front-ends that both produce the same
`markup.Document` AST, so `UiLoader` and everything downstream is shared:

- `markup.Parser` — XML-style markup (`<Tag attr="value">children</Tag>`).
- `markup.GlyphDslParser` — Compose-style DSL (`Component(args) { children }`).

`UiLoader.loadSource` routes by the first significant character (`<` = XML,
anything else = DSL); `GlyphDslParser.looksLikeDsl` is the single detector. The
DSL keeps its own scanner (it does not use `Tokenizer`) because the token
grammar is different. The demo `glyph-ui-examples/src/main/resources/demo/ui.glyph`
uses the DSL. When adding a widget tag, register the alias in
`WidgetRegistry.defaults()` and extend `WidgetRegistryTest`.

Tag names are matched case-insensitively (`WidgetRegistry.key` lower-cases via
`Locale.ROOT`), so the Compose-style PascalCase spellings (`Text`, `Column`,
`ImageView`, `ProgressBar`, `Dropdown`, `RadioButton`, `ToggleSwitch`,
`Hyperlink`, ...) resolve to the same factories as their HTML spellings.

## Reactive bindings and control flow

`UiLoader` wires `{path}` interpolations (in element content, mixed with static
text) and the `bind:text` / `bind-text` attribute to a widget's caption through
the `ui.Textual` interface (implemented by `Label`, `Button`, `TextField`,
`CheckBox`, `RadioButton`, `Link`, `ToggleButton`, `Badge`, `Chip`,
`ToggleSwitch`, `Alert`). Paths are resolved against `markup.Bindings`
(register with `UiLoader.registerBinding(path, trackable)`); unknown paths warn
once per load. Values are pushed through the widget's `Property<String>` via
`reactive.Binding`, which is tracked on the component so disposal unsubscribes.

Control flow lives in `UiLoader.addBuiltChildren`:

- `for-each="items"` expands the element once per list element. Each copy is
  built under a child `Bindings` scope exposing `item` and `index` (and
  `item.<key>` for map entries). Lists are read from `SignalList`, `List` or any
  `Iterable`; other values warn.
- `if` / `elif` / `else` build one chain; each member's visibility is driven by
  a chain of `reactive.Computed` flags, so branches toggle without a rebuild.

Both directives work in the XML and DSL front-ends. The DSL also splits `{...}`
inside string literals (`Text("Hi {name}")`) into `TextNode` + `BindingNode`
children, matching the XML content behaviour.

