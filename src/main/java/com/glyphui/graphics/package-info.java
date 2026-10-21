/**
 * Drawing primitives, theme and font plumbing for Glyph UI.
 *
 * <p><strong>Native-type boundary (API stability rule 4):</strong> this
 * package wraps the Skija rendering backend ({@code io.github.humbleui.skija})
 * behind toolkit-owned types. The frozen public surface must not use native
 * types in ordinary member signatures; only two kinds of exceptions are
 * allowed, both documented in {@code docs/architecture/api-stability.md}:</p>
 *
 * <ul>
 *   <li><strong>Named escape hatches</strong> — members whose names end in
 *       {@code ForInternalUse()} (e.g. {@code Canvas#nativeCanvasForInternalUse},
 *       {@code Image#nativeImageForInternalUse}). They exist so embedders and
 *       renderers can reach the live native handle when no wrapper API covers
 *       their need, and they may disappear in any release.</li>
 *   <li><strong>Renderer extension points</strong> — {@code protected}
 *       {@code render(...)} / font-resolution hooks on components and
 *       {@code Canvas} draw methods that accept borrowed Skija {@code Paint} /
 *       {@code Font} references. Subclass renderers legitimately need them;
 *       application code should not.</li>
 * </ul>
 *
 * <p>{@link com.glyphui.graphics.FontManager} and
 * com.glyphui.graphics.Fonts are font plumbing: they intentionally remain
 * outside the frozen surface (see {@code docs/architecture/font-resource-model.md})
 * and may change shape at any time, including patch releases.</p>
 *
 * <p>Everything else in this package is ordinary public API: if a signature
 * mentions {@code io.github.humbleui.*} or {@code io.github.humbleui.jwm.*}
 * without one of the exemptions above, it is a leak and
 * {@code ApiSurfaceLeakageTest} will fail the build.</p>
 */
package com.glyphui.graphics;
