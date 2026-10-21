# ADR: Public API Stability Contract (frozen at 0.1)

- Status: accepted
- Date: 2026-10-21
- Deciders: toolkit maintainers

## Context

Glyph UI is approaching its first usable release. Early adopters need a
predictable upgrade path, and the toolkit needs room to keep tightening its
internals (test hooks, backend seams, native-type isolation) without being
locked into every accidental public member that shipped in the 0.x line.

Before this ADR, anything `public` was implicitly "API". That made hygiene
impossible: FocusManager entry points, IME helpers, invalidate counters and
Skija-native accessors all sat on the same footing as `Button.setText()`.

## Decision

### 1. Versioning contract

- From **0.1** onward, the *supported public API surface* defined below is
  frozen for the **1.x** line: no breaking changes (removed/renamed members,
  changed signatures, changed semantics) are allowed in any 1.x minor or
  patch release. Breaking changes may only land in 2.0.
- During 0.x the surface may still evolve; each breaking change must carry a
  `BREAKING CHANGE:` footer in the conventional commit and a note here.
- Additive changes (new methods/classes on the supported surface) are allowed
  in 1.x minors and are never breaking.

### 2. What counts as the supported public API

A member is part of the frozen surface only if ALL of these hold:

1. It is `public` (or `protected` for subclass extension points).
2. Its package is one of: `com.glyphui.core`, `com.glyphui.ui`,
   `com.glyphui.graphics`, `com.glyphui.events`, `com.glyphui.style`,
   `com.glyphui.layout`.
3. Its package is NOT `*.internal` (see `internal-boundary.md`).
4. Its Javadoc does not say **"Not part of the frozen public API"** —
   members kept `public` only for cross-package toolkit plumbing
   (`Application.getWindow()`, `Component.applyFocusChange(boolean)`,
   `Theme.getFont(FontRole*)`) are explicitly excluded by their Javadoc.
5. It does not accept or return types from `io.github.humbleui.*` (Skija) or
   the JWM window backend. Native handles reachable for diagnostics use the
   `*ForInternalUse()` naming convention and are excluded from the surface.

Everything else — `com.glyphui.core.internal.**`, `com.glyphui.core.backend.**`,
package-private members, and members excluded under rules 4/5 — is
implementation detail and may change in any release, including patches.

### 3. Test/diagnostic hooks

All test-only entry points live behind `com.glyphui.core.internal.TestHooks`
(reflective facade): `recreateRasterSurfaceForTesting`, `drainUiTasksForTests`,
`getInvalidateCount`, `resetInvalidateCount`, `disposeStyleFonts`,
`renderFrame`, `isPaintDirty`. The backing methods on production classes are
package-private; tests must go through `TestHooks`. New hooks are added to
`TestHooks`, never as public members.

Cross-package plumbing that reflection cannot reasonably replace
(`Component.applyFocusChange`, the `ImeTextFieldBridge` seam) stays public but
carries the rule-4 exclusion marker in its Javadoc.

### 4. No native-type leakage

Public API uses toolkit-owned value types (`Color`, `Dimension`, `Insets`,
`KeyEvent`, ...). Skija/JWM types appear only:

- inside `com.glyphui.core.backend.**` (SPI, not application-facing),
- inside `*.internal` seams (`ImeTextFieldBridge`),
- behind `*ForInternalUse()` accessors documented as non-API,
- in the `Canvas` drawing-primitive facade (constructors, surface rebinding and
  draw methods that take borrowed Skija `Paint`/`Font`/`Image` references).
  `Canvas` is renderer-facing by design: subclass renderers legitimately pass
  native paint/font objects through it, so wrapping every primitive would
  duplicate Skija rather than isolate it. Application code should prefer
  widget/theme APIs over raw `Canvas` calls. The rationale lives in
  `com.glyphui.graphics.package-info`.

Regression guard: `ApiSurfaceLeakageTest` scans public signatures of the
packages in rule 2 (minus exclusions) for `io.github.humbleui` / JWM types.

## Consequences

- Audits become mechanical: apply the five rules, move violations to
  package-private or `*.internal`, mark unavoidable cross-package seams.
- IDE autocomplete will still show rule-4 members; the Javadoc warning is the
  contract signal until modules/jep-378 enforcement is feasible.
- `STYLE_FONTS` and font distribution are addressed separately in
  `font-resource-model.md`.
