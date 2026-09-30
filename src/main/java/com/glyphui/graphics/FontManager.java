package com.glyphui.graphics;

import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Central font registry: bundled fonts, system fallback chain and per-role
 * {@link Font} instances.
 *
 * <p><b>Scope note (v0.1):</b> Glyph UI renders Latin text through Skija's
 * {@code Canvas.drawString}, which does basic glyph placement but no complex
 * script shaping. HarfBuzz-based shaping (via the optional Skija {@code shaper}
 * artifact) is intentionally out of scope for v0.1; scripts that require
 * ligatures/RTL reordering are not yet supported.</p>
 *
 * <p>Resolution order when a family is requested:</p>
 * <ol>
 *   <li>Families explicitly registered from a file or classpath resource
 *       ({@link #registerFromFile(String)} / {@link #registerFromResource(String, String)}),
 *       backed by {@code Typeface.makeFromFile} semantics (bundled files are
 *       extracted to a temp path because Skija has no byte-array typeface API).</li>
 *   <li>The system fallback chain configured via {@link #setFallbackChain(String...)}
 *       (default: DejaVu Sans → Liberation Sans → Helvetica → Arial), resolved
 *       with {@code FontMgr.matchFamilyStyle}.</li>
 *   <li>Skia's own default typeface as a last resort.</li>
 * </ol>
 */
public final class FontManager {

    /** Default system fallback chain (most portable first on Linux). */
    private static final String[] DEFAULT_FALLBACKS = {
        "DejaVu Sans", "Liberation Sans", "Noto Sans", "Helvetica", "Arial"
    };

    private static String[] fallbackChain = DEFAULT_FALLBACKS.clone();

    /** Registered families: lower-cased family name -> primary typeface. */
    private static final Map<String, Typeface> registered = new LinkedHashMap<>();

    /** Cached role fonts: role -> [size -> Font]. */
    private static final Map<Theme.FontRole, Map<Float, Font>> roleFonts =
            new EnumMap<>(Theme.FontRole.class);

    /** Default sizes per role (logical units). */
    private static final Map<Theme.FontRole, Float> defaultSizes = new EnumMap<>(Theme.FontRole.class);

    static {
        defaultSizes.put(Theme.FontRole.BODY, 14.0f);
        defaultSizes.put(Theme.FontRole.BUTTON, 16.0f);
        defaultSizes.put(Theme.FontRole.HEADING, 20.0f);
        for (Theme.FontRole role : Theme.FontRole.values()) {
            roleFonts.put(role, new LinkedHashMap<>());
        }
    }

    private FontManager() {
    }

    // ------------------------------------------------------------------
    // Configuration
    // ------------------------------------------------------------------

    /**
     * Sets the explicit system fallback chain used when a family is not
     * registered locally. The first family that resolves wins.
     *
     * @param families family names in priority order
     */
    public static void setFallbackChain(String... families) {
        if (families == null || families.length == 0) {
            fallbackChain = DEFAULT_FALLBACKS.clone();
        } else {
            fallbackChain = families.clone();
        }
        clearCaches();
    }

    /**
     * Gets the current fallback chain.
     *
     * @return the fallback family names
     */
    public static String[] getFallbackChain() {
        return fallbackChain.clone();
    }

    /**
     * Registers a font family from a file on disk
     * ({@code Typeface.makeFromFile}).
     *
     * @param filePath absolute or relative path to a .ttf/.otf file
     * @return the resulting typeface
     * @throws IllegalArgumentException if the file cannot be loaded
     */
    public static Typeface registerFromFile(String filePath) {
        Path path = Path.of(filePath);
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Font file not readable: " + filePath);
        }
        Typeface typeface = Typeface.makeFromFile(path.toString());
        if (typeface == null) {
            throw new IllegalArgumentException("Failed to load typeface from " + filePath);
        }
        String key = familyKey(typeface.getFamilyName());
        registered.put(key, typeface);
        clearCaches();
        return typeface;
    }

    /**
     * Registers a font family bundled as a classpath resource. Skija only
     * exposes {@code Typeface.makeFromFile}, so the resource bytes are
     * extracted to a temporary file first.
     *
     * @param resourceName classpath resource path (e.g. {@code /fonts/MyFont.ttf})
     * @param familyHint   logical family name to register under (may be null
     *                     to use the embedded family name)
     * @return the resulting typeface
     * @throws IllegalArgumentException if the resource is missing or unloadable
     */
    public static Typeface registerFromResource(String resourceName, String familyHint) {
        try (InputStream in = FontManager.class.getResourceAsStream(resourceName)) {
            if (in == null) {
                throw new IllegalArgumentException("Font resource not found: " + resourceName);
            }
            Path tmp = Files.createTempFile("glyph-font-", ".ttf");
            tmp.toFile().deleteOnExit();
            Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
            Typeface typeface = Typeface.makeFromFile(tmp.toString());
            if (typeface == null) {
                throw new IllegalArgumentException("Failed to load typeface from resource " + resourceName);
            }
            String family = (familyHint != null && !familyHint.isEmpty())
                    ? familyHint : typeface.getFamilyName();
            registered.put(familyKey(family), typeface);
            clearCaches();
            return typeface;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not extract font resource " + resourceName, e);
        }
    }

    // ------------------------------------------------------------------
    // Resolution
    // ------------------------------------------------------------------

    /**
     * Resolves a typeface for a family name through the documented fallback
     * chain. Passing {@code null} returns the first resolvable fallback.
     *
     * @param family  preferred family name (or null for the default)
     * @param style   the font style
     * @return a typeface (never null; may be Skia's default as last resort)
     */
    public static Typeface resolveTypeface(String family, FontStyle style) {
        List<String> candidates = new ArrayList<>();
        if (family != null && !family.isEmpty()) {
            candidates.add(family);
        }
        for (String f : fallbackChain) {
            if (!candidates.contains(f)) {
                candidates.add(f);
            }
        }

        for (String candidate : candidates) {
            Typeface t = registered.get(familyKey(candidate));
            if (t != null) {
                return t;
            }
        }

        // System lookup via Skia's font manager
        for (String candidate : candidates) {
            try {
                Typeface t = Typeface.makeFromName(candidate, style);
                if (t != null) {
                    return t;
                }
            } catch (RuntimeException ignored) {
                // Try next candidate
            }
        }

        // Last resort: Skia default typeface
        Typeface def = Typeface.makeFromName(null, style);
        if (def == null) {
            throw new IllegalStateException("No typeface could be resolved");
        }
        return def;
    }

    /**
     * Gets (and caches) a {@link Font} for a semantic theme role at the
     * role's default size.
     *
     * @param role the font role
     * @return the cached font — owned by the manager, do NOT close
     */
    public static Font getFont(Theme.FontRole role) {
        return getFont(role, getDefaultFontSize(role));
    }

    /**
     * Gets (and caches) a {@link Font} for a semantic theme role at an
     * explicit size.
     *
     * @param role the font role
     * @param size the font size in logical units
     * @return the cached font — owned by the manager, do NOT close
     */
    public static Font getFont(Theme.FontRole role, float size) {
        Map<Float, Font> bySize = roleFonts.get(role);
        Font font = bySize.get(size);
        if (font == null) {
            Typeface typeface = resolveTypeface(null, FontStyle.NORMAL);
            font = new Font(typeface, size);
            bySize.put(size, font);
        }
        return font;
    }

    /**
     * Gets the default size configured for a role.
     *
     * @param role the font role
     * @return the size in logical units
     */
    public static float getDefaultFontSize(Theme.FontRole role) {
        return defaultSizes.get(role);
    }

    /**
     * Overrides the default size for a role and drops cached fonts for it.
     *
     * @param role the font role
     * @param size the new default size
     */
    public static void setDefaultFontSize(Theme.FontRole role, float size) {
        defaultSizes.put(role, size);
        roleFonts.get(role).clear();
    }

    /**
     * Releases all cached fonts and registered typefaces. Called from
     * {@code Application.destroy()} so native Skija objects do not leak.
     */
    public static void dispose() {
        clearCaches();
        for (Typeface typeface : registered.values()) {
            typeface.close();
        }
        registered.clear();
    }

    /**
     * Clears cached role fonts (call after changing the fallback chain).
     */
    public static void clearCaches() {
        for (Map<Float, Font> bySize : roleFonts.values()) {
            for (Font font : bySize.values()) {
                font.close();
            }
            bySize.clear();
        }
    }

    private static String familyKey(String family) {
        return family == null ? "" : family.toLowerCase(Locale.ROOT);
    }
}
