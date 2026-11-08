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

## Subclassing the text inputs

`PasswordField` and `TextArea` both extend `TextField`. `TextField` exposes one
extension point for subclasses: `protected String displayText()` returns the
string actually painted and measured, while the buffer (and the caret index)
stays unmasked — that is how `PasswordField` masks without breaking editing.
`caret`/`selectionAnchor` are `protected` so a subclass can reimplement caret
movement.

Do NOT widen `TextField.getFont()`/`getFieldPadding()` (or other helpers that
return native Skija types): a `protected` method returning `skija.Font` leaks a
native type through the frozen API and fails `ApiSurfaceLeakageTest`. Subclasses
that need a font resolve it themselves via `resolveFont(Theme.FontRole.BODY)` /
`getTheme().getFont(...)`.

`TextField` handles Ctrl+A/C/X/V with `event.getKeyChar()` lower-cased, so tests
must pass the real key char (e.g. `'a'`), not `'\0'`. Ctrl+C/X/V run through the
static `TextField.setClipboardHooks` installed by `Application`; unit tests
without that wiring must not rely on them. `TextArea` deliberately keeps Enter
(inserts `\n`, never submits) and makes Home/End line-relative; every other key
falls through to `super.onKeyEvent`.

## Top-level windows: `Window` vs `Form`

`core.Window` is the low-level window (the AWT `Frame` analogue); `ui.Form`
extends it as the high-level top-level window (the Swing `JFrame` analogue).
`Form` is purely additive and owns the root-panel assembly: a content pane
(`getContentPane()`), an optional `MenuBar` (`addMenu`), and — for an
undecorated form — a `TitleBar` used as custom chrome. Its creation-time fluent
setters are covariant overrides so a chain keeps the `Form` type.

**Two entry points, one window.** For Swing-style standalone use, build the
form and show it — `new Form(...)` then `form.setVisible(true)`. `setVisible`
creates an internal `Application` when the form has none and runs the event
loop on the calling (main) thread until the window closes (it blocks: the JWM
backend requires the main thread and owns its native message loop, so there is
no separate EDT). For the explicit path, **start applications with
`Application#createForm(...)`**: the factory creates the form and initializes
the application in one call, and throws `IllegalStateException` on failure (a
form that cannot be shown is exceptional, unlike the boolean `init`). In that
path `setVisible(true)` only presents the window (the owner drives the loop);
`Form#getApplication()` returns the owner. The old `init(Window)` path stays
for plain `Window`s and for callers that need the boolean. `Form` re-mounts
its chrome when `decorated(...)` flips after adoption, so
`app.createForm(...).decorated(false)` works even though the native window is
created lazily.

Keep `Window` public. A public class cannot extend a package-private superclass
from another package (javac rejects it), so `Form` extends the public `Window`
instead of hiding it; `Window` already crosses the public surface
(`Application.init(Window)`, the `SurfaceFactory` SPI).

Mounting goes through `Window#onAdopted(Panel)`, a protected no-op hook that
`Application.init` calls after the root panel is sized and bound to the owning
`AppContext`; `Form` overrides it to install its chrome (so children added
during mounting inherit the application by ownership). To unit-test a form's
structure without an `Application`, call `onAdopted(new Panel())` directly
(same-package tests can reach the `protected` hook).

`TitleBar` is laid out in three zones (FlatLaf's `FlatTitlePane` model): the
menu bar and extra widgets at the leading edge, the window title centred, and
the min/max/close controls pinned trailing. A menu bar put into the strip goes
through `TitleBar#setMenuBar`, which sets `MenuBar#setEmbedded(true)` — the bar
drops its fill/border and flattens its triggers so the menus blend in. Keep
standalone menu bars non-embedded (the default). `Form` wires this up for an
undecorated window automatically and keeps the menu bar as its own row when the
window is decorated.

## Environment

The sandbox can be reset between turns, losing the JDK, Maven and the local
`~/.m2` cache. Recover with
`apt-get update && apt-get install -y openjdk-21-jdk-headless maven libegl1 libgles2`,
then run the first Maven command online so the dependencies re-download before
switching back to `-o`.
