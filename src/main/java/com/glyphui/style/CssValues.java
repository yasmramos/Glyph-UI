package com.glyphui.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts raw CSS declaration values into the typed values stored in a
 * {@link Style}, resolving {@code var(--name)} references against a variable
 * map (populated from {@code :root} rules, optionally seeded with
 * theme-derived variables).
 */
final class CssValues {

    /** Named colors supported by the subset. */
    private static final Map<String, Integer> NAMED_COLORS = buildNamedColors();

    private static Map<String, Integer> buildNamedColors() {
        Map<String, Integer> m = new java.util.HashMap<>();
        m.put("black", 0xFF000000);
        m.put("white", 0xFFFFFFFF);
        m.put("red", 0xFFFF0000);
        m.put("green", 0xFF008000);
        m.put("blue", 0xFF0000FF);
        m.put("yellow", 0xFFFFFF00);
        m.put("cyan", 0xFF00FFFF);
        m.put("magenta", 0xFFFF00FF);
        m.put("gray", 0xFF808080);
        m.put("grey", 0xFF808080);
        m.put("silver", 0xFFC0C0C0);
        m.put("maroon", 0xFF800000);
        m.put("olive", 0xFF808000);
        m.put("lime", 0xFF00FF00);
        m.put("aqua", 0xFF00FFFF);
        m.put("teal", 0xFF008080);
        m.put("navy", 0xFF000080);
        m.put("fuchsia", 0xFFFF00FF);
        m.put("purple", 0xFF800080);
        m.put("orange", 0xFFFFA500);
        m.put("transparent", 0x00000000);
        return Map.copyOf(m);
    }

    /** Properties whose value is an ARGB color. */
    private static boolean isColorProperty(String prop) {
        return switch (prop) {
            case "color", "background-color", "accent-color", "border-color" -> true;
            // Note: plain CSS `background` is a multi-part shorthand that this
            // subset only supports in its color-only form; it is intentionally
            // absent here so StyleProperty.BACKGROUND can be handled below.
            default -> false;
        };
    }

    /** Properties whose value is a bare numeric length (px). */
    private static boolean isLengthProperty(String prop) {
        return switch (prop) {
            case "padding", "margin", "width", "height", "border-width",
                 "border-radius", "font-size", "gap" -> true;
            default -> false;
        };
    }

    private CssValues() {
    }

    /**
     * Parses a declaration for a property, first expanding any
     * {@code var(--name, fallback)} references.
     *
     * @param property  the CSS property name (lower-case)
     * @param rawValue  the raw declaration text (may contain {@code var(...)})
     * @param variables known custom properties for {@code var()} resolution
     * @return the typed value ({@code Integer}, {@code Float} or
     *         {@code String}), or null when unsupported
     */
    static Object parse(String property, String rawValue, Map<String, String> variables) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        List<String> unresolved = new ArrayList<>();
        String v = resolveVars(rawValue, variables, unresolved).trim();
        for (String name : unresolved) {
            System.err.println("[GlyphUI] Warning: undefined CSS variable \"" + name + "\"");
        }
        if (v.isEmpty()) {
            return null;
        }
        // Shorthands that need more than one token are out of subset.
        if (property.equals("border") && !v.matches("-?\\d+(\\.\\d+)?px")) {
            return null; // e.g. "1px solid red" not supported yet
        }
        if (property.equals("flex") && !v.matches("\\d+(\\.\\d+)?")) {
            return null; // "1 1 auto" style shorthand not supported
        }
        if (isColorProperty(property)) {
            return parseColor(v);
        }
        // The enum's cssName for BACKGROUND is "background"; keep it working as
        // a color-only form of the CSS shorthand even though it is not listed
        // in isColorProperty (which keys off the standard longhand names).
        if (property.equals("background")) {
            return parseColor(v);
        }
        if (isLengthProperty(property)) {
            return parseLength(v);
        }
        if (property.equals("opacity") || property.equals("flex-grow")) {
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (property.equals("font-weight")) {
            return parseFontWeight(v);
        }
        // Some widgets store color-like properties under non-standard names
        // (e.g. "text-color"); still resolve them as colors when the raw
        // value looks like a color token.
        if (v.startsWith("#") || v.startsWith("rgb(") || v.startsWith("rgba(")
                || NAMED_COLORS.containsKey(v)) {
            Integer c = parseColor(v);
            if (c != null) {
                return c;
            }
        }
        // Keyword / identifier properties: display, font-family, flex-*, etc.
        return stripQuotes(v);
    }

    /**
     * Resolves {@code var(--name, fallback)} occurrences inside a raw value.
     *
     * @param value      the raw declaration text
     * @param variables  known custom properties (without {@code var()} wrapper)
     * @param unresolved collected names of unknown variables (for warnings)
     * @return the value with every resolvable {@code var()} substituted
     */
    static String resolveVars(String value, Map<String, String> variables,
                              List<String> unresolved) {
        if (value == null || !value.contains("var(")) {
            return value;
        }
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < value.length()) {
            int start = value.indexOf("var(", i);
            if (start < 0) {
                out.append(value, i, value.length());
                break;
            }
            out.append(value, i, start);
            int depth = 1;
            int j = start + 4;
            while (j < value.length() && depth > 0) {
                char c = value.charAt(j);
                if (c == '(') depth++;
                else if (c == ')') depth--;
                if (depth > 0) j++;
            }
            String inner = value.substring(start + 4, Math.min(j, value.length()));
            int comma = inner.indexOf(',');
            String name = (comma >= 0 ? inner.substring(0, comma) : inner).trim();
            String fallback = comma >= 0 ? inner.substring(comma + 1).trim() : null;
            String resolved = variables.get(name);
            if (resolved == null && fallback != null) {
                resolved = fallback;
            }
            if (resolved == null) {
                unresolved.add(name);
                resolved = "";
            }
            out.append(resolved);
            i = Math.min(j + 1, value.length());
        }
        return out.toString().trim();
    }

    /**
     * Parses a CSS color into an ARGB int.
     *
     * <p>Hex forms are interpreted per the CSS Color 4 specification:
     * {@code #rgba} and {@code #rrggbbaa} carry the alpha channel in the
     * <em>last</em> digits (RGBA ordering), which is converted to the internal
     * ARGB representation here.</p>
     *
     * @param value one of {@code #rgb}, {@code #rgba}, {@code #rrggbb},
     *              {@code #rrggbbaa}, {@code rgb(r,g,b)},
     *              {@code rgba(r,g,b,a)} or a named color
     * @return ARGB int, or null when unparsable
     */
    static Integer parseColor(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        Integer named = NAMED_COLORS.get(v);
        if (named != null) {
            return named;
        }
        if (v.startsWith("#")) {
            String hex = v.substring(1);
            try {
                switch (hex.length()) {
                    case 3: {
                        int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                        int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                        int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                        return 0xFF000000 | (r << 16) | (g << 8) | b;
                    }
                    case 4: {
                        // #rgba — CSS Color 4, alpha in the last digit.
                        int r = Integer.parseInt(hex.substring(0, 1), 16) * 17;
                        int g = Integer.parseInt(hex.substring(1, 2), 16) * 17;
                        int b = Integer.parseInt(hex.substring(2, 3), 16) * 17;
                        int a = Integer.parseInt(hex.substring(3, 4), 16) * 17;
                        return (a << 24) | (r << 16) | (g << 8) | b;
                    }
                    case 6: {
                        long rgb = Long.parseLong(hex, 16);
                        return 0xFF000000 | (int) rgb;
                    }
                    case 8: {
                        // #rrggbbaa — CSS Color 4, alpha in the last byte.
                        long rgba = Long.parseLong(hex, 16);
                        long r = (rgba >> 24) & 0xFF;
                        long g = (rgba >> 16) & 0xFF;
                        long b = (rgba >> 8) & 0xFF;
                        long a = rgba & 0xFF;
                        return (int) ((a << 24) | (r << 16) | (g << 8) | b);
                    }
                    default:
                        return null;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (v.startsWith("rgb(") || v.startsWith("rgba(")) {
            int open = v.indexOf('(');
            int close = v.lastIndexOf(')');
            if (close < 0) return null;
            String[] parts = v.substring(open + 1, close).split("\\s*,\\s*");
            try {
                if (parts.length == 3 || parts.length == 4) {
                    int r = clamp255(Double.parseDouble(parts[0]));
                    int g = clamp255(Double.parseDouble(parts[1]));
                    int b = clamp255(Double.parseDouble(parts[2]));
                    int a = parts.length == 4
                            ? clamp255((int) Math.round(Double.parseDouble(parts[3]) * 255))
                            : 255;
                    return (a << 24) | (r << 16) | (g << 8) | b;
                }
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static int clamp255(double d) {
        return (int) Math.max(0, Math.min(255, Math.round(d)));
    }

    /**
     * Parses a length into logical pixels. Only {@code px} and unitless
     * numbers are accepted (no %, em, rem… in this subset).
     *
     * @param value the raw length text
     * @return float pixels or null
     */
    static Float parseLength(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (v.endsWith("px")) {
            v = v.substring(0, v.length() - 2).trim();
        } else if (!v.isEmpty() && !Character.isDigit(v.charAt(0)) && v.charAt(0) != '.'
                && v.charAt(0) != '-') {
            return null; // keywords like "auto" unsupported for lengths
        }
        try {
            return Float.parseFloat(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer parseFontWeight(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        switch (v) {
            case "normal": return 400;
            case "bold": return 700;
            case "lighter": return 300;
            case "bolder": return 800;
            default:
                try {
                    int n = Integer.parseInt(v);
                    return (n >= 100 && n <= 900) ? n : null;
                } catch (NumberFormatException e) {
                    return null;
                }
        }
    }

    private static String stripQuotes(String value) {
        String v = value.trim();
        if (v.length() >= 2 && ((v.startsWith("\"") && v.endsWith("\""))
                || (v.startsWith("'") && v.endsWith("'")))) {
            v = v.substring(1, v.length() - 1);
        }
        return v.isEmpty() ? null : v;
    }
}
