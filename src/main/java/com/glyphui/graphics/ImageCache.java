package com.glyphui.graphics;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded LRU cache of decoded {@link Image} objects keyed by an arbitrary
 * string (typically a path or resource name).
 *
 * <p>Entries evicted because the cache exceeds its capacity — and every
 * entry dropped by {@link #close()} from {@code Application.destroy()} —
 * have their native resources released.</p>
 */
public class ImageCache implements AutoCloseable {

    private final int capacity;
    private final LinkedHashMap<String, Image> entries;

    /**
     * Creates a cache holding at most {@code capacity} images.
     *
     * @param capacity maximum number of cached images (must be positive)
     */
    public ImageCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.entries = new LinkedHashMap<String, Image>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                if (size() > ImageCache.this.capacity) {
                    eldest.getValue().close(); // release evicted native image
                    return true;
                }
                return false;
            }
        };
    }

    /**
     * Creates a cache with the default capacity of 32 images.
     */
    public ImageCache() {
        this(32);
    }

    /**
     * Gets a cached image, loading it through the supplied loader on a miss.
     *
     * @param key    cache key (e.g. a resource name)
     * @param loader factory invoked only when the key is absent
     * @return the cached image (owned by the cache — do NOT close directly)
     */
    public Image getOrLoad(String key, ImageLoader loader) {
        Image image = entries.get(key);
        if (image != null) {
            return image;
        }
        image = loader.load();
        entries.put(key, image);
        return image;
    }

    /**
     * Puts an already-loaded image into the cache, replacing any previous
     * entry under the same key (the replaced image is closed).
     *
     * @param key   cache key
     * @param image the image (ownership transfers to the cache)
     */
    public void put(String key, Image image) {
        Image previous = entries.put(key, image);
        if (previous != null && previous != image) {
            previous.close();
        }
    }

    /**
     * Checks whether a key is present.
     *
     * @param key cache key
     * @return true if cached
     */
    public boolean containsKey(String key) {
        return entries.containsKey(key);
    }

    /**
     * Gets the current number of cached images.
     *
     * @return the cache size
     */
    public int size() {
        return entries.size();
    }

    /**
     * Gets the configured capacity.
     *
     * @return maximum number of entries
     */
    public int getCapacity() {
        return capacity;
    }

    /**
     * Removes one entry and closes its native image.
     *
     * @param key cache key
     * @return true if an entry was removed
     */
    public boolean remove(String key) {
        Image image = entries.remove(key);
        if (image != null) {
            image.close();
            return true;
        }
        return false;
    }

    /**
     * Closes and removes every cached image. Called from
     * {@code Application.destroy()}.
     */
    @Override
    public void close() {
        Iterator<Map.Entry<String, Image>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            it.next().getValue().close();
            it.remove();
        }
    }

    /**
     * Returns the cached image for the given key without triggering a load,
     * or {@code null} if no entry exists.
     */
    public Image getIfPresent(String key) {
        return entries.get(key);
    }

    /**
     * Produces a freshly loaded image on a cache miss.
     */
    @FunctionalInterface
    public interface ImageLoader {
        /**
         * @return a freshly loaded image
         */
        Image load();
    }
}
