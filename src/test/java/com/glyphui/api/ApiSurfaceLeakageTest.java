package com.glyphui.api;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for {@code docs/architecture/api-stability.md} rule 4:
 * no native backend type ({@code io.github.humbleui.*}, i.e. Skija/JWM) may
 * leak through the frozen public API surface.
 *
 * <p>Scans every compiled class in the supported packages and asserts that
 * public/protected methods and constructors never mention a forbidden type in
 * their generic signature. Excluded from the scan:</p>
 *
 * <ul>
 *   <li>{@code com.glyphui.core.internal.**} — internal seams are allowed to
 *       carry native handles (e.g. {@code ImeTextFieldBridge}).</li>
 *   <li>{@code com.glyphui.core.backend.**} — SPI, not application-facing.</li>
 *   <li>graphics primitives themselves ({@code Canvas}, {@code Image},
 *       {@code FontManager}, {@code Fonts}, {@code Theme}) keep documented
 *       escape hatches ({@code *ForInternalUse()}, borrowed-font getters) that
 *       the ADR marks as non-API; they are listed explicitly below so new
 *       leaks elsewhere still fail the build.</li>
 * </ul>
 */
public class ApiSurfaceLeakageTest {

    /** Packages whose public members must stay free of native types. */
    private static final String[] SUPPORTED_PREFIXES = {
            "com/glyphui/core/",
            "com/glyphui/ui/",
            "com/glyphui/events/",
            "com/glyphui/style/",
            "com/glyphui/layout/",
            "com/glyphui/graphics/",
    };

    /** Sub-packages that are explicitly NOT part of the frozen surface. */
    private static final String[] EXCLUDED_PACKAGE_PREFIXES = {
            "com/glyphui/core/internal/",
            "com/glyphui/core/backend/",
    };

    /**
     * Documented escape hatches per the ADR ("Not part of the frozen public
     * API" Javadoc marker). Keep this list minimal; adding an entry here is a
     * deliberate API-hygiene decision, not a convenience.
     */
    private static final String[] ALLOWED_LEAK_MEMBERS = {
            // Borrowed-font accessors, marked non-API in Javadoc.
            "com.glyphui.graphics.Theme#getFont",
            "com.glyphui.graphics.FontManager#", // whole class: font plumbing
            "com.glyphui.graphics.Fonts#",        // whole class: font plumbing
            // Native-handle escape hatches with *ForInternalUse naming.
            "com.glyphui.graphics.Canvas#nativeCanvasForInternalUse",
            "com.glyphui.graphics.Canvas#nativeSurfaceForInternalUse",
            "com.glyphui.graphics.Canvas#bindNativeSurface",
            "com.glyphui.graphics.Image#nativeImageForInternalUse",
            // Canvas is the drawing-primitive facade: its constructors and
            // draw methods take borrowed Skija Paint/Font/Image references by
            // design (renderer extension points). See the native-type-boundary
            // section of docs/architecture/api-stability.md and
            // com.glyphui.graphics.package-info for the full rationale.
            "com.glyphui.graphics.Canvas#<init>",
            "com.glyphui.graphics.Canvas#drawRect",
            "com.glyphui.graphics.Canvas#drawRRect",
            "com.glyphui.graphics.Canvas#drawCircle",
            "com.glyphui.graphics.Canvas#drawLine",
            "com.glyphui.graphics.Canvas#drawString",
            "com.glyphui.graphics.Canvas#measureText",
            "com.glyphui.graphics.Canvas#getTextHeight",
            "com.glyphui.graphics.Canvas#drawImage",
            // Widget render extension points (protected, renderer-facing).
            "com.glyphui.ui.Component#resolveFont",
            "com.glyphui.ui.Component#render",
            "com.glyphui.ui.Button#render",
            "com.glyphui.ui.Label#render",
            "com.glyphui.ui.Panel#render",
            "com.glyphui.ui.TextField#render",
            "com.glyphui.ui.TextField#getFontForIme",
            // IME seam implementation on TextField.
            "com.glyphui.core.Window#present",
    };

    @Test
    public void publicApiMustNotExposeNativeBackendTypes() throws Exception {
        Path classesRoot = Paths.get(System.getProperty("user.dir"),
                "target", "classes");
        assertTrue(Files.isDirectory(classesRoot),
                "Compiled classes missing; run `mvn compile` first");

        List<String> violations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(classesRoot)) {
            List<Path> classFiles = new ArrayList<>();
            walk.filter(p -> p.toString().endsWith(".class"))
                    .forEach(classFiles::add);
            for (Path classFile : classFiles) {
                String internalName = classesRoot
                        .relativize(classFile).toString()
                        .replace(File.separatorChar, '/')
                        .replaceAll("\\.class$", "");
                if (!isSupported(internalName) || isExcluded(internalName)) {
                    continue;
                }
                Class<?> type = Class.forName(
                        internalName.replace('/', '.'), false,
                        getClass().getClassLoader());
                collectViolations(type, violations);
            }
        }

        if (!violations.isEmpty()) {
            throw new AssertionError("Public API leaks native backend types "
                    + "(io.github.humbleui / jwm). See "
                    + "docs/architecture/api-stability.md rule 4:\n  "
                    + String.join("\n  ", violations));
        }
    }

    private static boolean isSupported(String internalName) {
        for (String prefix : SUPPORTED_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExcluded(String internalName) {
        for (String prefix : EXCLUDED_PACKAGE_PREFIXES) {
            if (internalName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static void collectViolations(Class<?> type, List<String> out) {
        for (Method m : type.getDeclaredMethods()) {
            int mods = m.getModifiers();
            if (!java.lang.reflect.Modifier.isPublic(mods)
                    && !java.lang.reflect.Modifier.isProtected(mods)) {
                continue;
            }
            if (isAllowed(type, m.getName())) {
                continue;
            }
            String signature = m.getGenericReturnType() + " "
                    + java.util.Arrays.toString(m.getParameterTypes());
            if (mentionsNative(signature)) {
                out.add(type.getName() + "#" + m.getName()
                        + " : " + signature);
            }
        }
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            int mods = c.getModifiers();
            if (!java.lang.reflect.Modifier.isPublic(mods)
                    && !java.lang.reflect.Modifier.isProtected(mods)) {
                continue;
            }
            if (isAllowed(type, "<init>")) {
                continue;
            }
            String signature = java.util.Arrays
                    .toString(c.getParameterTypes());
            if (mentionsNative(signature)) {
                out.add(type.getName() + "#<init> : " + signature);
            }
        }
    }

    private static boolean isAllowed(Class<?> type, String member) {
        String fq = type.getName() + "#" + member;
        for (String allowed : ALLOWED_LEAK_MEMBERS) {
            if (fq.equals(allowed) || fq.startsWith(allowed)) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsNative(String signature) {
        return signature.contains("io.github.humbleui")
                || signature.contains("humbleui.jwm");
    }

    /**
     * Sanity check: the source of truth lists exist so the contract stays
     * discoverable from the codebase itself.
     */
    @Test
    public void architectureDocsExist() throws IOException {
        Path base = Paths.get(System.getProperty("user.dir"),
                "docs", "architecture");
        for (String doc : new String[] {"api-stability.md",
                "internal-boundary.md", "font-resource-model.md"}) {
            Path file = base.resolve(doc);
            assertTrue(Files.isRegularFile(file),
                    "Missing architecture contract: " + file);
            String content = new String(
                    Files.readAllBytes(file), StandardCharsets.UTF_8);
            assertTrue(content.length() > 200,
                    "Architecture contract looks truncated: " + doc);
        }
    }
}
