package io.github.yasmramos.glyphui.api;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Machine-checked guard for {@code docs/architecture/internal-boundary.md}:
 * a {@code public} top-level type may only live in a whitelisted package, or
 * under an {@code internal} segment. Everything else is an accidental API
 * leak, because any {@code public} type in the published jar is reachable by
 * application authors whether we intend it or not.
 *
 * <p>This is the runnable half of the ADR's enforcement plan (follow-up 3).
 * japicmp/revapi still need a released baseline to diff against; this test
 * enforces the *boundary* itself on every build, with no baseline required.</p>
 *
 * <p>Nested and synthetic classes are skipped: they are governed by their
 * top-level enclosing type, which is checked here.</p>
 */
public class ApiBoundaryTest {

    /** Packages whose public types are part of the supported surface. */
    private static final Set<String> WHITELISTED_PACKAGES = Set.of(
            "io/github/yasmramos/glyphui/core",
            "io/github/yasmramos/glyphui/core/backend",
            "io/github/yasmramos/glyphui/core/logging",
            "io/github/yasmramos/glyphui/ui",
            "io/github/yasmramos/glyphui/graphics",
            "io/github/yasmramos/glyphui/events",
            "io/github/yasmramos/glyphui/style",
            "io/github/yasmramos/glyphui/layout",
            "io/github/yasmramos/glyphui/reactive",
            "io/github/yasmramos/glyphui/markup");

    @Test
    public void publicTypesMustLiveInWhitelistedPackages() throws Exception {
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
                // Nested/synthetic types follow their top-level enclosing type.
                if (internalName.contains("$")) {
                    continue;
                }
                String packageName = packageOf(internalName);
                if (isWhitelisted(packageName)) {
                    continue;
                }
                Class<?> type = Class.forName(internalName.replace('/', '.'),
                        false, getClass().getClassLoader());
                if (Modifier.isPublic(type.getModifiers())) {
                    violations.add(internalName.replace('/', '.'));
                }
            }
        }

        if (!violations.isEmpty()) {
            throw new AssertionError("Public type(s) outside the whitelisted "
                    + "packages. Move the type under an `internal` package or "
                    + "add its package to the whitelist in "
                    + "docs/architecture/internal-boundary.md:\n  "
                    + String.join("\n  ", violations));
        }
    }

    private static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash < 0 ? "" : internalName.substring(0, slash);
    }

    private static boolean isWhitelisted(String packageName) {
        if (WHITELISTED_PACKAGES.contains(packageName)) {
            return true;
        }
        // Any package carrying an `internal` segment is explicitly out of API.
        return packageName.equals("io/github/yasmramos/glyphui/internal")
                || packageName.startsWith("io/github/yasmramos/glyphui/internal/")
                || packageName.contains("/internal/")
                || packageName.endsWith("/internal");
    }
}
