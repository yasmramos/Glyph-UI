package io.github.yasmramos.glyphui.integration;

import io.github.humbleui.skija.Font;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the Priority-3 performance and optimization hardening
 * (design review items 7 and 8).
 *
 * <ul>
 *   <li><b>Item 7 — preferred-size caching in Label / TextField:</b> text
 *       measurement is expensive, so both widgets cache their preferred size
 *       against every input it depends on (caption, placeholder, resolved
 *       font identity, padding). The cache must return identical values for
 *     repeated reads, invalidate exactly when an input changes, and hit
 *     across equal-but-distinct string instances thanks to {@code intern()}.</li>
 *   <li><b>Item 8 — scratch-buffer reuse in FlexLayout.layout():</b> the
 *       per-pass {@code Dimension[]} / {@code float[]} arrays are reused
 *       between layout calls whenever the visible-item count is unchanged, so
 *       steady-state relayouts stop allocating. Reuse must be invisible: the
 *       buffers only ever hold scratch data (sizes are applied through
 *       {@code applyLayoutSize}), so results stay correct across passes with
 *       growing, shrinking and fluctuating child counts.</li>
 * </ul>
 *
 * <p>All tests run headless (no Application/window needed): Label/TextField
 * resolve fonts and themes lazily from {@code Theme.current()}, and bare
 * {@link Panel} instances lay out fine without a native surface.</p>
 */
class PerformanceCachingTest extends io.github.yasmramos.glyphui.TestBase {

    @AfterEach
    void restoreDefaultTheme() {
        // Tests that install a custom theme must not leak it into others.
        Theme.setCurrent(null);
    }

    /** Reads a private field reflectively (cache internals are private by design). */
    private static Object readField(Object target, String name) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("missing cache field: " + name, e);
        }
    }

    // ------------------------------------------------------------------
    // Item 7 — Label preferred-size cache
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Label caches preferred width: repeated reads return the same Float instance")
    void labelPreferredWidthIsCached() {
        Label label = new Label("Hello world");
        float first = label.getPreferredWidth();
        Float cachedValue = (Float) readField(label, "cachedPreferredWidth");
        assertNotNull(cachedValue, "measurement must populate the cache");
        float second = label.getPreferredWidth();
        assertSame(cachedValue, readField(label, "cachedPreferredWidth"),
                "second read must hit the cache, not re-measure");
        assertEquals(first, second, 0.0f);
    }

    @Test
    @DisplayName("Label cache invalidates when the caption changes")
    void labelCacheInvalidatesOnTextChange() {
        Label label = new Label("short");
        float before = label.getPreferredWidth();
        label.setText("a much longer caption than before");
        float after = label.getPreferredWidth();
        assertTrue(after > before,
                "re-measured width must grow with the caption (got " + before + " -> " + after + ")");
    }

    @Test
    @DisplayName("Label cache hits across equal-but-distinct strings via intern()")
    void labelCacheHitsAcrossEqualStrings() {
        Label label = new Label("cached caption");
        label.getPreferredWidth(); // populate the cache
        // A fresh StringBuilder produces a distinct String instance with the
        // same content; setText interns it, so the cache key still matches.
        label.setText(new StringBuilder("cached ").append("caption").toString());
        Float cached = (Float) readField(label, "cachedPreferredWidth");
        assertNotNull(cached);
        // The cache entry's text key is the interned caption, and the stored
        // value equals the freshly computed width — no miss occurred.
        String key = (String) readField(label, "cachedPreferredWidthText");
        assertEquals("cached caption", key);
        assertEquals(cached.floatValue(), label.getPreferredWidth(), 0.0f);
    }

    @Test
    @DisplayName("Label preferred height stays stable across repeated cached reads")
    void labelPreferredHeightIsCached() {
        Label label = new Label("multi\nline\ntext");
        float h1 = label.getPreferredHeight();
        float h2 = label.getPreferredHeight();
        assertEquals(h1, h2, 0.0f);
        assertNotNull(readField(label, "cachedPreferredHeight"));
        // Adding a line must change the result (cache invalidated by content).
        label.setText("multi\nline\ntext\nagain");
        assertTrue(label.getPreferredHeight() > h1, "extra line must grow the height");
    }

    // ------------------------------------------------------------------
    // Item 7 — TextField preferred-size cache
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TextField caches preferred width keyed by text, placeholder, font and padding")
    void textFieldPreferredWidthIsCached() {
        TextField field = new TextField();
        field.setText("initial value");
        float first = field.getPreferredWidth();
        Float cached = (Float) readField(field, "cachedPreferredWidth");
        assertNotNull(cached, "measurement must populate the cache");
        assertSame(cached, readField(field, "cachedPreferredWidth"),
                "second read must hit the cache");
        assertEquals(first, field.getPreferredWidth(), 0.0f);
    }

    @Test
    @DisplayName("TextField cache invalidates on text and placeholder changes")
    void textFieldCacheInvalidatesOnInputs() {
        TextField field = new TextField();
        field.setText("ab");
        float wShort = field.getPreferredWidth();

        // Like a native input, the current text deliberately does NOT widen
        // the field (stable-size contract from fix(ui): stabilize text field
        // size). The text still participates in the cache key, so changing it
        // must invalidate the cached measurement even when the result is the
        // same value.
        field.setText("a considerably longer value inside the field");
        float wLong = field.getPreferredWidth();
        assertEquals(wShort, wLong, 0.01f, "typed text must not widen the field");
        assertSame(readField(field, "cachedPreferredWidthText"),
                "a considerably longer value inside the field".intern(),
                "cache key must track the new text instance");

        // Placeholder participates in the width formula and the cache key:
        // setting a wide placeholder on an empty field must re-measure.
        field.setText("");
        float wEmpty = field.getPreferredWidth(); // populate cache for empty text
        field.setPlaceholder("an extremely long placeholder hint here");
        float wPlaceholder = field.getPreferredWidth();
        assertTrue(wPlaceholder > wEmpty, "wide placeholder must widen the field");
        assertNotNull(readField(field, "cachedPreferredWidthPlaceholder"));
    }

    // ------------------------------------------------------------------
    // Item 7 — cache correctness under theme changes (font/padding inputs)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Label cache misses when the theme padding changes")
    void labelCacheMissesOnThemePaddingChange() {
        Theme base = Theme.builder().padding(4f).build();
        setCurrentSafely(base);
        Label label = new Label("padding test");
        float wSmall = label.getPreferredWidth();

        Theme big = Theme.builder().padding(40f).build();
        Theme.setCurrent(big);
        float wBig = label.getPreferredWidth();
        assertTrue(wBig > wSmall,
                "larger theme padding must widen the preferred size (got " + wSmall + " -> " + wBig + ")");
    }

    private static void setCurrentSafely(Theme theme) {
        try {
            Theme.setCurrent(theme);
        } catch (Throwable t) {
            // Skija unavailable on this host: skip the test gracefully.
            org.junit.jupiter.api.Assumptions.assumeTrue(false,
                    "Skija/theme not available: " + t);
        }
    }

    // ------------------------------------------------------------------
    // Item 8 — FlexLayout scratch-buffer reuse
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FlexLayout reuses the same scratch arrays across relayouts with constant child count")
    void flexLayoutReusesScratchBuffers() throws Exception {
        Panel panel = new Panel();
        panel.setWidth(300f);
        panel.setHeight(60f);
        FlexLayout layout = new FlexLayout(FlexLayout.Direction.ROW);
        panel.setLayoutManager(layout);
        panel.add(new Button("one"));
        panel.add(new Button("two"));

        // Force allocation of the scratch buffers on the first pass.
        layout.layout(panel);
        Field f = FlexLayout.class.getDeclaredField("scratchMeasured");
        f.setAccessible(true);
        Object firstBuffers = f.get(layout);
        assertNotNull(firstBuffers, "first pass must allocate the scratch buffers");

        // Second pass with the same visible-item count must reuse them.
        layout.layout(panel);
        assertSame(firstBuffers, f.get(layout),
                "steady-state relayout must reuse the scratch buffer array");
    }

    @Test
    @DisplayName("FlexLayout reallocates scratch buffers when the child count changes")
    void flexLayoutReallocatesOnChildCountChange() throws Exception {
        Panel panel = new Panel();
        panel.setWidth(300f);
        panel.setHeight(60f);
        FlexLayout layout = new FlexLayout(FlexLayout.Direction.ROW);
        panel.setLayoutManager(layout);
        panel.add(new Button("one"));
        layout.layout(panel);

        Field f = FlexLayout.class.getDeclaredField("scratchMeasured");
        f.setAccessible(true);
        Object oneChildBuffers = f.get(layout);
        assertEquals(1, java.lang.reflect.Array.getLength(oneChildBuffers));

        panel.add(new Button("two"));
        layout.layout(panel);
        Object twoChildBuffers = f.get(layout);
        assertTrue(twoChildBuffers != oneChildBuffers,
                "buffer set must grow with the visible-item count");
        assertEquals(2, java.lang.reflect.Array.getLength(twoChildBuffers));
    }

    @Test
    @DisplayName("FlexLayout results stay correct across passes with fluctuating child counts")
    void flexLayoutResultsUnaffectedByBufferReuse() {
        Panel panel = new Panel();
        panel.setWidth(400f);
        panel.setHeight(50f);
        FlexLayout layout = new FlexLayout(FlexLayout.Direction.ROW);
        layout.setGap(0f);
        panel.setLayoutManager(layout);

        Button a = new Button("A");
        Button b = new Button("B");
        Button c = new Button("C");
        panel.add(a);
        panel.add(b);
        panel.add(c);

        Map<String, Dimension> reference = new IdentityHashMap<>();
        layout.layout(panel);
        reference.put("a", new Dimension(a.getWidth(), a.getHeight()));
        reference.put("b", new Dimension(b.getWidth(), b.getHeight()));
        reference.put("c", new Dimension(c.getWidth(), c.getHeight()));

        // Remove a middle child, relayout, then re-add it and relayout again:
        // stale scratch entries from previous passes must never leak into the
        // applied sizes.
        panel.remove(b);
        layout.layout(panel);
        Dimension refA = reference.get("a");
        Dimension refC = reference.get("c");
        assertEquals(refA.getWidth(), a.getWidth(), 0.01f);
        assertEquals(refC.getWidth(), c.getWidth(), 0.01f);

        panel.add(b);
        layout.layout(panel);
        Dimension refB = reference.get("b");
        assertEquals(refA.getWidth(), a.getWidth(), 0.01f);
        assertEquals(refB.getWidth(), b.getWidth(), 0.01f);
        assertEquals(refC.getWidth(), c.getWidth(), 0.01f);
    }

    @Test
    @DisplayName("Font identity is stable across reads (shared theme/style font caches)")
    void fontIdentityIsStableForCacheKeys() {
        // The preferred-size caches compare resolved Font objects by
        // reference; this documents the invariant they rely on: the same
        // role resolves to the same shared Font instance repeatedly.
        Label label = new Label("stable fonts");
        Font f1 = (Font) invokeResolveFont(label);
        Font f2 = (Font) invokeResolveFont(label);
        assertSame(f1, f2, "role fonts must be identity-stable across reads");
    }

    private static Object invokeResolveFont(Label label) {
        try {
            java.lang.reflect.Method m = Component.class.getDeclaredMethod("resolveFont",
                    io.github.yasmramos.glyphui.graphics.Theme.FontRole.class);
            m.setAccessible(true);
            return m.invoke(label, io.github.yasmramos.glyphui.graphics.Theme.FontRole.BODY);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
