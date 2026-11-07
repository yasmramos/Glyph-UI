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
