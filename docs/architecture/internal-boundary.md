# Architecture Decision: the internal boundary

**Status:** accepted
**Date:** 2026-10-21
**Deciders:** core maintainers
**Scope:** `com.glyphui` package layout, test hooks, non-public API classes

## Context

Glyph UI ships a single Maven artifact (`com.glyphui:glyph-ui`) with no JPMS
`module-info.java`. Today every type in `src/main/java` is `public`, so the
published surface includes things that were never meant to be consumed by
applications: windowing backends, surface factories and even methods whose own
Javadoc says "for testing". Without a defined boundary:

- any refactor of a backend or test shim is technically a breaking change;
- users can (and will) bind to internals we do not want to support;
- new contributors have no rule to decide where a new class goes.

The project needs one explicit answer to: **which packages are public API, and
where do test hooks and non-API classes live?**

## Decision

Define the boundary as a **package-name convention**, enforced by tooling, not
by access modifiers alone.

### 1. Everything under `com.glyphui..*internal*` is private

A segment named exactly `internal` anywhere inside `com.glyphui` marks the
package as **not part of the public API**. Internal types may be `public` at
the Java-language level (see rationale below), but they carry **no stability
promise**: they can change or disappear in any release, including patch
releases.

### 2. The internal host package is `com.glyphui.core.internal`

This package — not `com.glyphui.core.backend` — hosts:

- **Test hooks / shims** previously exposed on production classes
  (`Application.recreateRasterSurfaceForTesting()`,
  `Application.drainUiTasksForTests()`, and future equivalents). They move into
  static entry points on `com.glyphui.core.internal.TestHooks`, which reaches
  the rest of the toolkit through package-private seams exercised only from
  the test sourceset.
- **Non-public implementation classes** that must remain reachable across
  packages but should not be advertised: e.g. `ImeBridge`-style adapters,
  global-hook registries, and plumbing that `Application`/`Window` delegate to.

### 3. `com.glyphui.core.backend` stays public, but frozen as SPI-lite

The backend layer (`WindowBackend`, `AbstractWindowBackend`,
`JwmWindowBackend`, `SurfaceFactory`, `GlSurfaceFactory`,
`RasterSurfaceFactory`, `SurfaceResult`) is currently referenced from the
public API (`Window.getBackend()`, `Window.present(SurfaceResult)`), so it
**cannot** be moved under `internal` without a breaking change. It therefore
stays where it is, reclassified as a **supported extension point (SPI)**:

- adding a new backend is allowed and encouraged;
- the interface signatures are stable; implementations may gain members in
  minor releases (default methods);
- it is *not* where ad-hoc test code goes. Newer, purely internal machinery
  goes to `com.glyphui.core.internal` instead.

### 4. Public API surface (whitelist)

These packages form the supported API for application developers:

| Package                 | Role                                             |
|-------------------------|--------------------------------------------------|
| `com.glyphui.core`      | Application lifecycle, Window, WindowConfig, FocusManager, RepaintRequester |
| `com.glyphui.ui`        | Components (Button, Label, Panel, TextField, ImageView, Component) |
| `com.glyphui.graphics`  | Canvas, Image, Theme, Fonts, Property            |
| `com.glyphui.layout`    | LayoutManager, FlowLayout, FlexLayout            |
| `com.glyphui.events`    | Event records/enums and key/modifier vocabularies |
| `com.glyphui.reactive`  | Signal, Computed, Binding, Reactor, Disposable   |
| `com.glyphui.style`     | StyleSheet, Style, StyleEngine                   |
| `com.glyphui.markup`    | Tokenizer, Parser, UiLoader (`.glyph` markup)    |

Anything not listed above, and anything under an `internal` segment, is out.

### 5. Enforcement

- **`package-info.java` per internal package.** Each `…​internal…` package gets
  a Javadoc header starting with `@apiNote This package is NOT part of the
  public API.` (mirrors the existing `markup/package-info.java` practice).
- **japicmp / revapi** against the last released version, configured with the
  whitelist above, fails CI if a signature outside the whitelist changes
  incompatibly — or if a new public type appears outside a whitelisted package.
- **Code review rule** (added to `.github/CONTRIBUTING.md`): *"If it isn't
  needed by an application author, it goes in `com.glyphui.core.internal`."*

## Why not the alternatives

| Alternative | Why rejected |
|---|---|
| **JPMS `module-info.java` now** (`exports`/`opens`) | Cleanest enforcement, but the library targets Java 17 classpath users too; adding a module today forces every consumer onto the module path or requires automatic-module hacks. Deferred: once the boundary exists as packages, adding `module-info.java` later is mechanical (`exports com.glyphui.*` minus `com.glyphui.core.internal`). |
| **Make internals package-private** | Impossible without moving them: `WindowBackend` is referenced from `com.glyphui.core` (different package), and tests intentionally live in mirrored packages. Package-private would force large file moves and break the existing test suite for zero extra safety over the naming convention. |
| **Annotation-only (`@ApiStatus.Internal`)** | Requires a new dependency (JetBrains annotations) and is advisory only — nothing stops a user from calling an annotated method, and no tool fails the build when the API grows. We keep the *option* of layering these annotations on top of the package rule later; the annotation alone is not the boundary. |
| **Separate artifact (`glyph-ui-internal`)** | Two artifacts double release/versioning overhead for a pre-1.0 single-team project and complicate the examples module's dependency graph. |

## Consequences

**Positive**

- One-sentence rule for contributors; unambiguous answers in code review.
- Test hooks stop polluting user-facing classes (`Application`, `Component`).
- japicmp/revapi can guard the boundary mechanically from now on.
- Future JPMS migration becomes a packaging task, not an architecture task.

**Negative / costs**

- `com.glyphui.core.internal` adds one more package to navigate.
- Moving existing `*ForTesting`/`*ForTests` methods out of `Application`
  touches ~10 test files (all in-repo; acceptable pre-1.0).
- `core.backend` remains public despite being implementation-heavy; this is a
  documented compromise until a `1.0` breaking-change window lets us either
  fold `Window.getBackend()` behind an opaque handle or re-export it as SPI.

## Follow-ups (tracked, not part of this decision)

1. Create `com.glyphui.core.internal` with `package-info.java` + `TestHooks`.
2. Relocate `Application.recreateRasterSurfaceForTesting()`,
   `drainUiTasksForTests()` and similar shims; update tests.
3. Add japicmp to the `verify` phase with the package whitelist.
4. Amend `.github/CONTRIBUTING.md` with the placement rule.
