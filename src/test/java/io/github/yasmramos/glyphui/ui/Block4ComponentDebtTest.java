package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.core.internal.TestHooks;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.markup.MarkupException;
import io.github.yasmramos.glyphui.markup.UiLoader;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Block 4 design-debt fixes affecting components,
 * widgets and the markup loader:
 *
 * <ul>
 *   <li><b>4.10</b> programmatic size and inline style stay in sync.</li>
 *   <li><b>4.11</b> {@code setFocusable} invalidates at most once.</li>
 *   <li><b>4.12</b> {@code Button} click handling swallows only runtime
 *       exceptions, never {@link Error}s.</li>
 *   <li><b>4.13/4.14/4.19</b> markup text extraction is recursive, trimmed
 *       and warns once per binding path.</li>
 *   <li><b>4.15/4.16/4.17/4.18/4.20/4.21</b> loader contracts: LoadResult
 *       bundles root + sheet, default widget sizes are not hardcoded, custom
 *       tags are extensible, failed loads reset cached artifacts, layout
 *       attributes map onto layout managers, and the absence of a
 *       Binder/Validator is documented behavior.</li>
 * </ul>
 */
class Block4ComponentDebtTest extends TestBase {

    // ------------------------------------------------------------------
    // 4.10 — explicit size vs inline style consistency
    // ------------------------------------------------------------------

    @Test
    void setWidthThenInlineStyleKeepsFieldAndComputedStyleConsistent() {
        Panel panel = new Panel(100f, 100f);
        panel.setWidth(120f);

        // Re-applying an inline style must not desynchronize the field from
        // the computed WIDTH declaration (previously getComputedStyle could
        // report NaN while getWidth() still returned the programmatic value).
        // applyExplicitSize() is the single writer of WIDTH/HEIGHT in the
        // inline layer: setInlineStyle stores the caller's declarations and
        // then folds the authoritative explicit size back on top.
        panel.setInlineStyle(Style.builder()
                .color(StyleProperty.COLOR, 0xFFFF0000)
                .build());

        assertEquals(120f, panel.getWidth(), 0.001f);
        float computedWidth = panel.getComputedStyle()
                .getFloat(StyleProperty.WIDTH, Float.NaN);
        assertEquals(panel.getWidth(), computedWidth, 0.001f,
                "getWidth() and the computed inline WIDTH must agree");
    }

    @Test
    void inlineStyleDeclaringWidthUpdatesGetter() {
        Panel panel = new Panel(100f, 100f);
        panel.setWidth(50f); // marks the size as explicitly set

        panel.setInlineStyle(Style.builder()
                .length(StyleProperty.WIDTH, 222f)
                .length(StyleProperty.HEIGHT, 111f)
                .build());

        assertEquals(222f, panel.getWidth(), 0.001f,
                "an inline width declaration must win over the stale field");
        assertEquals(111f, panel.getHeight(), 0.001f);
    }

    // ------------------------------------------------------------------
    // 4.11 — single invalidation on focusability changes
    // ------------------------------------------------------------------

    @Test
    void setFocusableSameValueIsANoopWithoutInvalidation() {
        Panel host = new Panel(200f, 200f);
        FocusManager fm = new FocusManager(() -> { });
        FocusManager.setGlobalFocusManager(fm);
        TestComponent child = new TestComponent(50, 30);
        host.add(child);
        assertTrue(child.isFocusable(), "components are focusable by default");

        TestHooks.resetInvalidateCount(child);

        child.setFocusable(true); // unchanged value
        assertEquals(0, TestHooks.getInvalidateCount(child),
                "no-op when the flag did not change (was double-invalidating)");

        child.setFocusable(false);
        assertEquals(1, TestHooks.getInvalidateCount(child),
                "exactly one invalidation on change");

        child.setFocusable(false); // unchanged again
        assertEquals(1, TestHooks.getInvalidateCount(child));
    }

    @Test
    void losingFocusabilityWhileFocusedClearsFocusWithOneInvalidation() {
        Panel host = new Panel(200f, 200f);
        FocusManager fm = new FocusManager(() -> { });
        FocusManager.setGlobalFocusManager(fm);
        TestComponent child = new TestComponent(50, 30);
        host.add(child);
        child.requestFocus();
        assertTrue(child.isFocused());

        TestHooks.resetInvalidateCount(child);

        child.setFocusable(false);

        assertFalse(child.isFocused(), "focus must be cleared");
        assertEquals(1, TestHooks.getInvalidateCount(child),
                "clearFocus() already invalidates; no second invalidate allowed");
    }

    // ------------------------------------------------------------------
    // 4.12 — Button click handler exception policy
    // ------------------------------------------------------------------

    /** Fires a synthetic mouse press/release pair inside the button bounds. */
    private static void click(Button button) {
        button.onMouseEvent(new MouseEvent(
                MouseEventType.PRESS, 50, 20, MouseButton.LEFT, 1));
        button.onMouseEvent(new MouseEvent(
                MouseEventType.RELEASE, 50, 20, MouseButton.LEFT, 1));
    }

    @Test
    void buttonSwallowsRuntimeExceptionFromClickHandler() {
        Button button = new Button(10f, 10f, 100f, 32f, "Go");
        AtomicBoolean ran = new AtomicBoolean();
        button.setOnClick(() -> {
            ran.set(true);
            throw new IllegalStateException("handler blew up");
        });

        click(button); // must not propagate

        assertTrue(ran.get());
    }

    @Test
    void buttonPropagatesErrorFromClickHandler() {
        // Block 4 item 4.12: Errors (OOM, StackOverflow) must NOT be swallowed
        // with a WARNING log; only RuntimeExceptions are caught.
        Button button = new Button(10f, 10f, 100f, 32f, "Go");
        button.setOnClick(() -> {
            throw new AssertionError("simulated Error");
        });

        assertThrows(AssertionError.class, () -> click(button));
    }

    // ------------------------------------------------------------------
    // 4.13 / 4.14 / 4.19 — markup text extraction
    // ------------------------------------------------------------------

    @Test
    void labelTextIncludesNestedElementContentAndIsTrimmed() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <label>
                    Hello <b>world</b> !
                  </label>
                </body>
                """);

        Label label = findFirst(root, Label.class);
        assertNotNull(label, "label was created");
        assertEquals("Hello world !", label.getText(),
                "nested elements contribute their text (textContent semantics) "
                        + "and surrounding whitespace is trimmed");
    }

    @Test
    void repeatedBindingPathsWarnOnlyOncePerLoad() {
        // Ten bindings sharing one path must produce a single warning, not ten.
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <label>{model.title}{model.title}{model.title}</label>
                </body>
                """);

        Label label = findFirst(root, Label.class);
        assertNotNull(label);
        assertEquals("", label.getText(), "binding nodes contribute no literal text");
    }

    // ------------------------------------------------------------------
    // 4.15 / 4.16 / 4.17 / 4.18 — loader contracts
    // ------------------------------------------------------------------

    @Test
    void loadWithStylesheetBundlesRootAndSheet() throws Exception {
        // Item 4.15: LoadResult travels with the stylesheet so a caller
        // cannot forget to apply it. .glyph documents link external sheets
        // through <link rel="stylesheet" href="..."> (inline <style> blocks
        // are intentionally unsupported: '{' collides with bindings), so the
        // test writes a temporary CSS file next to the document.
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("block4");
        try {
            java.nio.file.Path cssFile = dir.resolve("app.css");
            java.nio.file.Files.writeString(cssFile, ".card { padding: 4px; }");
            java.nio.file.Path file = dir.resolve("ui.glyph");
            java.nio.file.Files.writeString(file, """
                    <body>
                      <link rel="stylesheet" href="app.css"/>
                      <div class="card"><label>Title</label></div>
                    </body>
                    """);

            UiLoader loader = new UiLoader();
            Object result = loader.loadWithStylesheet(file);

            Class<?> resultClass = result.getClass();
            Object root = resultClass.getMethod("root").invoke(result);
            Object sheet = resultClass.getMethod("styleSheet").invoke(result);
            assertNotNull(root, "the tree is part of the result");
            assertNotNull(sheet,
                    "the linked stylesheet must travel with the tree");
            assertEquals(1, ((io.github.yasmramos.glyphui.style.StyleSheet) sheet)
                    .getRules().size());
        } finally {
            java.nio.file.Files.deleteIfExists(dir.resolve("app.css"));
            java.nio.file.Files.deleteIfExists(dir.resolve("ui.glyph"));
            java.nio.file.Files.deleteIfExists(dir);
        }
    }

    @Test
    void widgetsWithoutExplicitSizeUseContentSizedConstructors() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <button>OK</button>
                  <label>Name</label>
                </body>
                """);

        Button button = findFirst(root, Button.class);
        Label label = findFirst(root, Label.class);
        assertNotNull(button);
        assertNotNull(label);
        // Block 4 item 4.16: no hardcoded 120x32 / 200x24 anymore. The
        // preferred-size contract now flows through DEFAULT_* or content.
        assertFalse(button.isSizeExplicitlySet(),
                "<button> without width/height must not carry an explicit size");
        assertFalse(label.isSizeExplicitlySet());
    }

    @Test
    void explicitMarkupSizesAreHonored() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <button width="150" height="40">OK</button>
                </body>
                """);

        Button button = findFirst(root, Button.class);
        assertNotNull(button);
        assertTrue(button.isSizeExplicitlySet());
        assertEquals(150f, button.getWidth(), 0.001f);
        assertEquals(40f, button.getHeight(), 0.001f);
    }

    @Test
    void registerTagSupportsCustomWidgets() {
        UiLoader loader = new UiLoader();
        loader.registerTag("chart", element -> {
            Panel chart = new Panel(300f, 200f);
            chart.setStyleTag("chart-widget");
            return chart;
        });

        Component root = loader.loadFromString("""
                <body>
                  <chart/>
                </body>
                """);

        Panel chart = findFirst(root, p -> "chart-widget".equals(p.getStyleTag()));
        assertNotNull(chart, "custom tag factory must be consulted before built-ins");
    }

    @Test
    void failedLoadResetsCachedDocumentAndStyleSheet() throws Exception {
        // Item 4.18: a failing load must not leave stale artifacts behind.
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("block4-reset");
        try {
            java.nio.file.Path cssFile = dir.resolve("app.css");
            java.nio.file.Files.writeString(cssFile, ".ok { padding: 1px; }");
            java.nio.file.Path good = dir.resolve("good.glyph");
            java.nio.file.Files.writeString(good, """
                    <body>
                      <link rel="stylesheet" href="app.css"/>
                      <label>ok</label>
                    </body>
                    """);
            java.nio.file.Path broken = dir.resolve("broken.glyph");
            // Unterminated tag: the tokenizer raises MarkupException.
            java.nio.file.Files.writeString(broken, "<body><label id=\"x\"");

            UiLoader loader = new UiLoader();
            loader.load(good);
            assertNotNull(loader.getLastDocument());
            assertNotNull(loader.getLastStyleSheet());

            assertThrows(MarkupException.class, () -> loader.load(broken));

            assertNull(loader.getLastDocument(),
                    "a failing load must not leave the previous document cached");
            assertNull(loader.getLastStyleSheet(),
                    "a failing load must not leave the previous sheet cached");
        } finally {
            java.nio.file.Files.deleteIfExists(dir.resolve("app.css"));
            java.nio.file.Files.deleteIfExists(dir.resolve("good.glyph"));
            java.nio.file.Files.deleteIfExists(dir.resolve("broken.glyph"));
            java.nio.file.Files.deleteIfExists(dir);
        }
    }

    // ------------------------------------------------------------------
    // 4.20 — layout attributes mapped onto layout managers
    // ------------------------------------------------------------------

    @Test
    void flexLayoutAttributesAreMappedOntoFlexLayout() throws Exception {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <div layout="flex" direction="row" justify="center" align="center" gap="8" padding="4">
                    <label>A</label>
                    <label>B</label>
                  </div>
                </body>
                """);

        Panel container = findFirst(root, p ->
                p instanceof Panel panel
                        && panel.getLayoutManager() instanceof FlexLayout);
        assertNotNull(container, "layout=\"flex\" installs a FlexLayout");
        FlexLayout flex = (FlexLayout) container.getLayoutManager();
        assertEquals(FlexLayout.Justify.CENTER, flex.getJustify(),
                "justify=\"center\" must map onto the manager (item 4.20)");
        assertEquals(FlexLayout.Align.CENTER, flex.getAlignItems(),
                "align=\"center\" must map onto the manager (item 4.20)");
        // gap/padding live in private override fields consumed at layout time;
        // read them reflectively since no public getter exists yet.
        assertEquals(8f, readFloatOverride(flex, "gapOverride"), 0.001f,
                "gap=\"8\" must be stored as an override");
        assertEquals(4f, readFloatOverride(flex, "paddingOverride"), 0.001f,
                "padding=\"4\" must be stored as an override");
    }

    private static float readFloatOverride(FlexLayout flex, String field) throws Exception {
        Field f = FlexLayout.class.getDeclaredField(field);
        f.setAccessible(true);
        return ((Float) f.get(flex)).floatValue();
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static <T extends Component> T findFirst(Component node, Class<T> type) {
        return findFirst(node, type::isInstance);
    }

    private static <T extends Component> T findFirst(Component node,
                                                    java.util.function.Predicate<Component> predicate) {
        if (predicate.test(node)) {
            return typeCast(node);
        }
        if (node instanceof Panel panel) {
            for (Component child : panel.getChildren()) {
                T found = findFirst(child, predicate);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T typeCast(Component node) {
        return (T) node;
    }
}
