# ADR: Font and Resource Distribution Model

- Status: accepted
- Date: 2026-10-21
- Deciders: toolkit maintainers

## Context

Glyph UI renders text through Skija typefaces. Two questions were open:

1. How do applications get fonts into the toolkit (bundled files, classpath
   resources, system fonts)?
2. `Component.STYLE_FONTS` is a process-wide static cache of derived
   `io.github.humbleui.skija.Font` objects keyed by family/size/style. Is that
   global cache part of the contract, or an implementation detail we can swap?

## Decision

### 1. Font registration API (contract)

`com.glyphui.graphics.FontManager` is the single, application-facing entry
point for fonts. Its public surface is:

- `registerFromFile(String path)` → registers a `.ttf/.otf` shipped alongside
  the application (native installers, dev checkout).
- `registerFromResource(String resourceName, String familyHint)` → registers a
  font embedded in the application JAR/classpath (`resources/fonts/...`).
- `setFallbackChain(String... families)` / `getFallbackChain()` → ordered
  family fallback used when a glyph is missing.
- `resolveTypeface(String family, FontStyle style)` → **internal plumbing**:
  kept public only because widget code across packages needs it; excluded from
  the frozen surface per `api-stability.md` rule 4 semantics.
- `dispose()` / `clearCaches()` → lifecycle cleanup.

The toolkit itself bundles **no font binaries**. The default theme resolves to
the platform/system typeface chosen by Skija at startup. Applications that
need a specific look call `registerFromResource(...)` during startup, before
building the widget tree. This keeps the artifact small and avoids licensing
entanglement (e.g. redistributing proprietary fonts inside the toolkit).

Style-sheet authors use `font-family` / `font-size` / `font-weight`; those are
resolved against the registered families + fallback chain. That styling layer —
not `FontManager` internals — is what ordinary applications touch day-to-day.

### 2. STYLE_FONTS is implementation detail (not contract)

`Component.STYLE_FONTS` (static family|size|style → Skija `Font` cache):

- **Not** exposed as public API beyond its current `protected` helper usage.
- Owned by `Component` today; documented here as swappable. A future release
  may move it behind `FontManager` (instance-scoped, per-application) without
  an API break, because nothing outside the toolkit references it.
- Cache identity is stable within a JVM run: `FontManager` never mutates or
  evicts a `Font` it handed out while it is cached in `STYLE_FONTS`, so widget
  measure caches may rely on reference equality of resolved fonts.
- Disposal: the cache is released via `TestHooks.disposeStyleFonts()` on
  test-isolation/JVM-shutdown paths only. Production apps are expected to let
  process exit reclaim native memory (Skija fonts are cheap relative to the
  surface allocations already held for the app's lifetime).

Consequences for widget code: treat every `Font` returned by
`resolveFont(...)` as borrowed, never close it, and rely on reference-equality
invalidation of measure caches (Button does exactly this).

### 3. Other resources

Images follow the same model: `com.glyphui.graphics.Image` loads from file or
classpath resource; the toolkit bundles none. Native handles stay behind
`nativeImageForInternalUse()`.

## Consequences

- Apps must register their own fonts; README documents the two-line startup
  snippet (`registerFromResource` + CSS `font-family`).
- `STYLE_FONTS` may be relocated/reimplemented freely; any change is internal
  and non-breaking.
- Global mutable state remains (FontManager registries, fallback chain). It is
  acceptable for a single-window desktop toolkit and revisited if headless
  multi-app hosting ever becomes a requirement.
