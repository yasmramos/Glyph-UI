package com.glyphui.graphics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ImageCache} LRU semantics. Uses stub images so no
 * Skija native decoding is required: the cache only tracks keys, eviction
 * order and close calls.
 */
public class ImageCacheTest {

    /** Minimal in-memory image stub — no native resources involved. */
    private static final class StubImage extends Image {
        private boolean closed;
        private final String id;

        StubImage(String id) {
            super(null); // no native image needed for cache bookkeeping
            this.id = id;
        }

        @Override
        public void close() {
            closed = true;
        }

        boolean isClosed() {
            return closed;
        }

        String getId() {
            return id;
        }
    }

    @Test
    public void testGetOrLoadCallsLoaderOnce() {
        try (ImageCache cache = new ImageCache(4)) {
            int[] loads = {0};
            Image first = cache.getOrLoad("k", () -> {
                loads[0]++;
                return new StubImage("a");
            });
            Image second = cache.getOrLoad("k", () -> {
                loads[0]++;
                return new StubImage("b");
            });

            assertEquals(1, loads[0], "Loader must run only on a miss");
            assertSame(first, second);
            assertEquals("a", ((StubImage) first).getId());
        }
    }

    @Test
    public void testLruEvictionClosesLeastRecentlyUsed() {
        ImageCache cache = new ImageCache(2);

        StubImage a = new StubImage("a");
        StubImage b = new StubImage("b");
        StubImage c = new StubImage("c");

        cache.put("a", a);
        cache.put("b", b);

        // Touch "a" so "b" becomes the least recently used entry.
        assertSame(a, cache.getOrLoad("a", () -> b));

        cache.put("c", c); // capacity exceeded -> evict LRU ("b")

        assertFalse(a.isClosed(), "Recently used entry must survive");
        assertTrue(b.isClosed(), "LRU entry must be evicted and closed");
        assertFalse(c.isClosed());
        assertEquals(2, cache.size());
        assertFalse(cache.containsKey("b"));
    }

    @Test
    public void testRemoveEvictsAndCloses() {
        try (ImageCache cache = new ImageCache(4)) {
            StubImage a = new StubImage("a");
            cache.put("a", a);

            assertTrue(cache.remove("a"));
            assertTrue(a.isClosed());
            assertNull(cache.getIfPresent("a"));

            // Removing an absent key is a no-op.
            assertFalse(cache.remove("missing"));
        }
    }

    @Test
    public void testCloseDropsAllEntries() {
        ImageCache cache = new ImageCache(4);
        StubImage a = new StubImage("a");
        StubImage b = new StubImage("b");
        cache.put("a", a);
        cache.put("b", b);

        cache.close();

        assertTrue(a.isClosed());
        assertTrue(b.isClosed());
        assertEquals(0, cache.size());
        assertNotNull(cache);
    }
}
