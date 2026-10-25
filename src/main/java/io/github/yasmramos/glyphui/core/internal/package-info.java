/**
 * Internal implementation hooks and test/diagnostics entry points for the
 * Glyph UI toolkit.
 *
 * <p><strong>@apiNote This package is NOT part of the public API.</strong>
 * Types here may be {@code public} at the Java-language level (they must
 * cross package boundaries to reach mirrored test sources), but they carry
 * <b>no stability promise</b>: they can change or disappear in any release,
 * including patch releases. Application code must not depend on them.</p>
 *
 * <p>Per {@code docs/architecture/internal-boundary.md}, this package hosts:</p>
 * <ul>
 *   <li>{@link io.github.yasmramos.glyphui.core.internal.TestHooks} — static entry points for
 *       test-only shims previously exposed on production classes
 *       ({@code Application}, {@code Component}).</li>
 *   <li>Future internal plumbing that {@code Application}/{@code Window}
 *       delegate to but that should never be advertised.</li>
 * </ul>
 *
 * <p>Placement rule for contributors: <em>"If it isn't needed by an
 * application author, it goes in {@code io.github.yasmramos.glyphui.core.internal}."</em></p>
 */
package io.github.yasmramos.glyphui.core.internal;
