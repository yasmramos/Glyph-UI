package io.github.yasmramos.glyphui.ui;

import io.github.humbleui.skija.Bitmap;
import io.github.humbleui.skija.Surface;
import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.graphics.Canvas;
import io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory;
import io.github.yasmramos.glyphui.graphics.Theme;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the subtree-level theme context (Swing/AWT lesson #8: process-wide
 * statics like {@code UIManager} must not be the only theme source).
 *
 * <p>Glyph-UI resolves a component's effective theme through an explicit
 * chain:</p>
 * <ol>
 *   <li>the component's own override installed with {@link
 *       Component#setTheme(Theme)};</li>
 *   <li>otherwise the nearest ancestor carrying an override, or a {@link
 *       Panel} subclass overriding {@link Panel#resolveTheme()};</li>
 *   <li>otherwise the process-wide {@link Theme#current()} as fallback.</li>
 * </ol>
 *
 * <p>The API-level tests use plain {@link Panel}/{@link TestComponent}
 * instances and need no native rendering. The raster test verifies that a
 * scoped theme actually reaches the painted pixels of a widget that reads
 * colors through {@code getTheme()} at render time.</p>
 */
class ThemeContextTest extends TestBase {

    private Theme previousGlobal;

    @BeforeEach
    void saveGlobalTheme() {
        previousGlobal = Theme.current();
    }

    @AfterEach
    void restoreGlobalTheme() {
        Theme.setCurrent(previousGlobal);
    }

    @Nested
    @DisplayName("Resolution chain")
    class ResolutionChain {

        @Test
        @DisplayName("detached components fall back to Theme.current()")
        void detachedFallsBackToGlobal() {
            TestComponent widget = new TestComponent();
            assertSame(Theme.current(), widget.getEffectiveTheme());
        }

        @Test
        @DisplayName("per-component override wins over everything else")
        void componentOverrideWins() {
            Theme global = Theme.dark();
            Theme scoped = Theme.light();
            Theme.setCurrent(global);

            Panel root = new Panel();
            root.setTheme(scoped);
            TestComponent child = new TestComponent();
            root.add(child);

            // Child inherits the scoped theme from its ancestor...
            assertSame(scoped, child.getEffectiveTheme());

            // ...but its own override takes precedence over the ancestor's.
            Theme own = Theme.dark();
            child.setTheme(own);
            assertSame(own, child.getEffectiveTheme());
        }

        @Test
        @DisplayName("nearest ancestor override wins in deep trees")
        void nearestAncestorWins() {
            Theme outer = Theme.dark();
            Theme inner = Theme.light();

            Panel root = new Panel();
            // Neutralize the default layout: without this, adding a child to
            // an explicit-sized panel triggers auto-fill and rewrites the
            // child's own bounds (see AutoFill-on-add behavior).
            root.setLayoutManager(new io.github.yasmramos.glyphui.layout.AbsoluteLayout());
            root.setTheme(outer);
            Panel middle = new Panel();
            middle.setTheme(inner);
            root.add(middle);
            TestComponent leaf = new TestComponent();
            middle.add(leaf);

            assertSame(inner, leaf.getEffectiveTheme());
            // The container itself resolves to its OWN override, not to the
            // outer context: nearest wins in both directions (a dark toolbar
            // inside a light window must stay dark).
            assertSame(inner, middle.getEffectiveTheme());
        }

        @Test
        @DisplayName("clearing an override restores inheritance")
        void clearingOverrideRestoresInheritance() {
            Theme scoped = Theme.light();
            Theme.setCurrent(Theme.dark());

            Panel root = new Panel();
            root.setTheme(scoped);
            TestComponent child = new TestComponent();
            child.setTheme(Theme.dark());
            root.add(child);

            child.setTheme(null);
            assertSame(scoped, child.getEffectiveTheme());

            root.setTheme(null);
            assertSame(Theme.current(), child.getEffectiveTheme());
            assertFalse(root.hasThemeOverride());
        }

        @Test
        @DisplayName("hasThemeOverride reports only the node that set it")
        void hasThemeOverrideIsLocal() {
            Panel root = new Panel();
            TestComponent child = new TestComponent();
            root.add(child);

            root.setTheme(Theme.light());
            assertTrue(root.hasThemeOverride());
            // Descendants inherit the value but do not report an override.
            assertFalse(child.hasThemeOverride());
        }

        @Test
        @DisplayName("Panel.resolveTheme hook provides a scoped theme")
        void panelResolveThemeHook() {
            final Theme scoped = Theme.light();
            Panel root = new Panel() {
                @Override
                protected Theme resolveTheme() {
                    return scoped;
                }
            };
            TestComponent child = new TestComponent();
            root.add(child);

            assertSame(scoped, child.getEffectiveTheme());
            assertFalse(root.hasThemeOverride());
        }

        @Test
        @DisplayName("component override beats Panel.resolveTheme hook")
        void componentOverrideBeatsHook() {
            final Theme hooked = Theme.light();
            Panel root = new Panel() {
                @Override
                protected Theme resolveTheme() {
                    return hooked;
                }
            };
            TestComponent child = new TestComponent();
            child.setTheme(Theme.dark());
            root.add(child);

            assertSame(Theme.dark(), child.getEffectiveTheme());
        }
    }

    @Nested
    @DisplayName("Rasterized propagation")
    class RasterPropagation {

        /** Reads one N32 premultiplied little-endian ARGB pixel from the surface. */
        private int readPixel(Canvas wrapper, int x, int y) {
            Surface skijaSurface = wrapper.nativeSurfaceForInternalUse();
            int w = skijaSurface.getWidth();
            Bitmap full = new Bitmap();
            try {
                assertTrue(full.allocN32Pixels(w, skijaSurface.getHeight()),
                        "allocN32Pixels failed");
                assertTrue(skijaSurface.readPixels(full, 0, 0), "readPixels failed");
                ByteBuffer buf = full.peekPixels();
                buf.rewind();
                byte[] px = new byte[buf.remaining()];
                buf.get(px);
                int off = (y * w + x) * 4;
                // N32 premultiplied, little-endian: bytes are [B, G, R, A].
                return (px[off] & 0xFF) | ((px[off + 1] & 0xFF) << 8)
                        | ((px[off + 2] & 0xFF) << 16) | ((px[off + 3] & 0xFF) << 24);
            } finally {
                full.close();
            }
        }

        /**
         * Renders {@code root} onto a fresh raster surface and reads the N32
         * premultiplied pixel at (x, y). Encapsulates the render + flush +
         * readPixels dance so several pixel-level contracts can be asserted
         * without duplicating the plumbing.
         */
        private int rasterPixel(Component root, int x, int y) {
            Surface[] holder = new Surface[1];
            Canvas canvas = RasterCanvasTestFactory.create(holder);
            try {
                root.renderInternal(canvas);
                // Flush through the wrapper API. Calling flush() directly on
                // the native surface can hand back a stale snapshot for an
                // on-screen-less raster surface, so the framework's own
                // flush path is used here (same one the toolkit runs).
                canvas.flush();
                return readPixel(canvas, x, y);
            } finally {
                if (holder[0] != null) {
                    holder[0].close();
                }
            }
        }

        /**
         * Builds a 200x100 panel with absolute positioning and an explicit
         * opaque backdrop so themed regions are distinguishable at the pixel
         * level. Callers add their own children afterwards.
         */
        private Panel backdropWindow(int argb) {
            Panel window = new Panel(0, 0, 200, 100);
            // Absolute positioning: the default flex/auto-fill behavior would
            // otherwise rewrite child bounds when they are added.
            window.setLayoutManager(new io.github.yasmramos.glyphui.layout.AbsoluteLayout());
            window.setBackgroundColor(argb);
            return window;
        }

        @Test
        @DisplayName("scoped theme reaches descendant background pixels")
        void scopedThemeReachesPixels() {
            Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                    "Skija native library not loadable on this host; skipping raster tests");

            // Global theme stays dark; a light-themed subtree must paint light
            // descendants without touching any process-wide state.
            Theme.setCurrent(Theme.dark());

            Panel window = backdropWindow(io.github.humbleui.skija.Color.makeARGB(255, 0, 0, 0));

            Panel card = new Panel();
            // Pin the size explicitly: setWidth/setHeight mark the bounds as
            // user-controlled (userSetSize), which stops the container's
            // auto-fill-on-add behavior from rewriting them.
            card.setWidth(100);
            card.setHeight(100);
            // The card declares the scoped theme for its subtree. As the ROOT
            // of that context it keeps its own historical painting contract:
            // no explicit background and no ancestor context means it draws
            // nothing (see the companion assertion below).
            card.setTheme(Theme.light());
            window.add(card);

            // A plain themed child inside the card resolves its background
            // through the ThemeContext chain — never through Theme.current().
            Panel inner = new Panel();
            inner.setWidth(50);
            inner.setHeight(50);
            card.add(inner);

            try {
                // API-level contract first: the themed child's color comes
                // from the scoped light theme, not the global dark one.
                int expected = Theme.light().getBackgroundColor();
                assertEquals(expected, inner.getBackgroundColor(),
                        "descendant background must resolve through the scoped context");

                int pixel = rasterPixel(window, 25, 25);
                assertEquals((expected >>> 16) & 0xFF, (pixel >>> 16) & 0xFF,
                        "R channel inside themed descendant");
                assertEquals(expected & 0xFF, pixel & 0xFF,
                        "B channel inside themed descendant");

                // The card itself (context root, no explicit background, no
                // surrounding ancestor context) must NOT repaint with its own
                // theme: theming a subtree must not silently restyle the node
                // that declares the context. We see the window's black
                // backdrop bleeding through instead.
                assertTrue(!card.shouldPaintBackground(),
                        "context root must keep the historical transparent-container contract");
                int cardArea = rasterPixel(window, 75, 75);
                assertEquals(0, (cardArea >>> 16) & 0xFF,
                        "R channel in card area without descendant coverage");
                assertEquals(0, cardArea & 0xFF,
                        "B channel in card area without descendant coverage");

                // Outside the card we must see the window's explicit black
                // backdrop, NOT the dark theme background: an explicitly set
                // background always wins over the theme. This also proves the
                // scoped light theme did not leak to siblings.
                int outside = rasterPixel(window, 150, 50);
                assertEquals(0, (outside >>> 16) & 0xFF, "R channel outside themed card");
                assertEquals(0, outside & 0xFF, "B channel outside themed card");
            } finally {
                window.dispose();
            }
        }

        @Test
        @DisplayName("ancestor-scoped theme paints descendants under a dark global theme")
        void ancestorScopedThemePaintsDescendants() {
            Assumptions.assumeTrue(RasterCanvasTestFactory.isAvailable(),
                    "Skija native library not loadable on this host; skipping raster tests");

            // Swing/AWT lesson #8 end-to-end: the process-wide static stays
            // dark while an ancestor installs a light context; every themed
            // descendant — including deep ones — rasterizes light colors
            // without any call to Theme.setCurrent().
            Theme.setCurrent(Theme.dark());

            Panel window = backdropWindow(io.github.humbleui.skija.Color.makeARGB(255, 0, 0, 0));

            Panel zone = new Panel();
            zone.setWidth(100);
            zone.setHeight(100);
            // The zone carries the scoped theme via the classic override.
            zone.setTheme(Theme.light());
            window.add(zone);

            Panel leaf = new Panel();
            leaf.setWidth(40);
            leaf.setHeight(40);
            zone.add(leaf);

            Button button = new Button("ok");
            button.setX(0);
            button.setY(50);
            zone.add(button);

            try {
                // API-level: the deep leaf resolves the light context through
                // the nearest ancestor override.
                assertSame(Theme.light(), leaf.getEffectiveTheme());
                int expected = Theme.light().getBackgroundColor();
                assertEquals(expected, leaf.getBackgroundColor());

                int pixel = rasterPixel(window, 20, 20);
                assertEquals((expected >>> 16) & 0xFF, (pixel >>> 16) & 0xFF,
                        "R channel under ancestor-scoped theme");
                assertEquals(expected & 0xFF, pixel & 0xFF,
                        "B channel under ancestor-scoped theme");

                // The zone (context root) itself still does not paint its own
                // theme over its bounds — only descendants inherit it.
                assertFalse(zone.shouldPaintBackground());
            } finally {
                window.dispose();
            }
        }
    }
}
