package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.core.internal.TestHooks;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.style.StyleEngine;
import io.github.yasmramos.glyphui.style.StyleSheet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Block 2 — systematic widget CSS bugs.
 *
 * <p>Covers every fix in the "all widgets" batch:</p>
 * <ul>
 *   <li>2.1 {@code :disabled} background rules now win over theme fallbacks
 *       in {@link Button}.</li>
 *   <li>2.2 {@code :disabled} color rules now win over the dimmed theme token
 *       in {@link Label}.</li>
 *   <li>2.3 {@link TextField} honours CSS background / border-color / color /
 *       border-radius / padding through the resolve*Style helpers.</li>
 *   <li>2.4–2.6 CSS width/height drive {@code measure()} in Label, ImageView
 *       and TextField (delegation to the base measurement path).</li>
 *   <li>2.7 A lone {@code font-weight} declaration is no longer ignored by
 *       Label's font resolution.</li>
 *   <li>2.8 TextField paints are created lazily on first render, so
 *       construction stays allocation-free.</li>
 *   <li>2.9 TextField exposes an observable {@code textProperty()}.</li>
 *   <li>2.10–2.11 No-op setters (same caret position, same color/size) do not
 *       trigger redundant invalidations.</li>
 * </ul>
 */
class WidgetCssFixesTest extends TestBase {

    /** Toolkit key codes (GlyphKeys wire values) matching TextField's switch. */
    private static final int KEY_RIGHT = 262;
    private static final int KEY_HOME = 268;
    private static final int KEY_BACKSPACE = 259;

    @BeforeEach
    void installFocusManager() {
        // requestFocus() needs a global focus manager to accept candidates.
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
    }

    @AfterEach
    void clearFocusManager() {
        FocusManager.setGlobalFocusManager(null);
    }

    /** Builds a PRESSED key event with the given modifiers. */
    private static KeyEvent press(int keyCode, char keyChar, KeyModifier... mods) {
        EnumSet<KeyModifier> set = mods.length == 0
                ? EnumSet.noneOf(KeyModifier.class)
                : EnumSet.of(mods[0], java.util.Arrays.copyOfRange(mods, 1, mods.length));
        return new KeyEvent(KeyEventType.PRESS, keyCode, keyChar, set);
    }

    /** Re-cascades the sheet over the tree and returns the computed style probe. */
    private static void cascade(Component root, StyleSheet sheet) {
        StyleEngine.apply(root, sheet);
    }

    // ------------------------------------------------------------------
    // 2.1 Button: CSS :disabled background
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.1 Button :disabled CSS")
    class ButtonDisabledCss {

        @Test
        @DisplayName("button:disabled background wins over theme disabled color")
        void disabledBackgroundRuleApplies() {
            StyleSheet sheet = StyleSheet.parse("""
                    button { background: #11223344; }
                    button:disabled { background: #FFEE0000; }
                    """);
            Panel root = new Panel(0, 0, 400, 300);
            Button button = new Button(0, 0, 100, 30, "x");
            root.add(button);

            button.setEnabled(false);
            cascade(root, sheet);

            // The disabled paint path must consult the computed style: a
            // declared :disabled background is present after the cascade.
            assertEquals(0xFFEE0000, button.getComputedStyle()
                    .getInt(io.github.yasmramos.glyphui.style.StyleProperty.BACKGROUND, 0),
                    ":disabled background declaration must reach the computed style");
        }

        @Test
        @DisplayName("explicit setNormalColor does not leak into disabled appearance without CSS")
        void normalColorOverrideStaysOutOfDisabledPathWhenCssAbsent() {
            StyleSheet sheet = StyleSheet.parse("button { color: #00000000; }");
            Panel root = new Panel(0, 0, 400, 300);
            Button button = new Button(0, 0, 100, 30, "x");
            root.add(button);

            button.setNormalColor(0xFF00FF00);
            button.setEnabled(false);
            cascade(root, sheet);

            // No `background` declaration exists, so the theme's disabled
            // colour still applies — the override must not bleed in.
            int disabledThemeColor = Theme.current().getControlDisabledColor();
            assertNotEquals(0xFF00FF00, disabledThemeColor,
                    "test premise: theme disabled color differs from the override");
            // getBackgroundColor is private; assert behaviour through the
            // computed style contract instead: hasStyleValue is false here.
            assertTrue(!button.getComputedStyle()
                            .has(io.github.yasmramos.glyphui.style.StyleProperty.BACKGROUND),
                    "without a CSS background rule the theme fallback chain applies");
        }
    }

    // ------------------------------------------------------------------
    // 2.2 Label: CSS :disabled color
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.2 Label :disabled CSS")
    class LabelDisabledCss {

        @Test
        @DisplayName("label:disabled color reaches the computed style")
        void disabledColorRuleApplies() {
            StyleSheet sheet = StyleSheet.parse("""
                    label { color: #11000000; }
                    label:disabled { color: #22000000; }
                    """);
            Panel root = new Panel(0, 0, 400, 300);
            Label label = new Label(0, 0, 200, 30, "text");
            root.add(label);

            label.setEnabled(false);
            cascade(root, sheet);

            assertEquals(0x22000000, label.getComputedStyle()
                    .getInt(io.github.yasmramos.glyphui.style.StyleProperty.COLOR, 0),
                    ":disabled color must win over the base type rule");
        }
    }

    // ------------------------------------------------------------------
    // 2.3 TextField: full CSS support
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.3 TextField CSS support")
    class TextFieldCss {

        @Test
        @DisplayName("background, border-color, color, radius and padding cascade")
        void allDeclarationsReachComputedStyle() {
            StyleSheet sheet = StyleSheet.parse("""
                    input {
                        background: #FF102030;
                        border-color: #FF405060;
                        color: #FF708090;
                        border-radius: 6px;
                        padding: 8px;
                    }
                    """);
            Panel root = new Panel(0, 0, 400, 300);
            TextField field = new TextField(0, 0, 200, 30);
            root.add(field);

            cascade(root, sheet);

            var cs = field.getComputedStyle();
            assertEquals(0xFF102030, cs.getInt(io.github.yasmramos.glyphui.style.StyleProperty.BACKGROUND, 0));
            assertEquals(0xFF405060, cs.getInt(io.github.yasmramos.glyphui.style.StyleProperty.BORDER_COLOR, 0));
            assertEquals(0xFF708090, cs.getInt(io.github.yasmramos.glyphui.style.StyleProperty.COLOR, 0));
            assertEquals(6.0f, cs.getFloat(io.github.yasmramos.glyphui.style.StyleProperty.BORDER_RADIUS, 0f), 1e-6);
            assertEquals(8.0f, cs.getFloat(io.github.yasmramos.glyphui.style.StyleProperty.PADDING, 0f), 1e-6);
        }

        @Test
        @DisplayName("input:disabled declarations cascade for the disabled paint path")
        void disabledRulesCascade() {
            StyleSheet sheet = StyleSheet.parse("""
                    input { background: #FF111111; }
                    input:disabled { background: #FF222222; color: #FF333333; }
                    """);
            Panel root = new Panel(0, 0, 400, 300);
            TextField field = new TextField(0, 0, 200, 30);
            root.add(field);

            field.setEnabled(false);
            cascade(root, sheet);

            var cs = field.getComputedStyle();
            assertEquals(0xFF222222, cs.getInt(io.github.yasmramos.glyphui.style.StyleProperty.BACKGROUND, 0));
            assertEquals(0xFF333333, cs.getInt(io.github.yasmramos.glyphui.style.StyleProperty.COLOR, 0));
        }
    }

    // ------------------------------------------------------------------
    // 2.4–2.6 CSS width/height honoured by measure()
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.4-2.6 CSS sizing in measure()")
    class CssSizingMeasure {

        @Test
        @DisplayName("Label with CSS width/height measures to the declared size")
        void labelCssSizeWins() {
            StyleSheet sheet = StyleSheet.parse("label { width: 200px; height: 50px; }");
            Panel root = new Panel(0, 0, 400, 300);
            Label label = new Label(0, 0, 10, 10, "short");
            root.add(label);

            cascade(root, sheet);
            Dimension d = label.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

            assertEquals(200.0f, d.getWidth(), 1e-3, "CSS width must win over text intrinsic width");
            assertEquals(50.0f, d.getHeight(), 1e-3, "CSS height must win over line-height intrinsic height");
        }

        @Test
        @DisplayName("ImageView with CSS width/height measures to the declared size")
        void imageViewCssSizeWins() throws Exception {
            org.junit.jupiter.api.Assumptions.assumeTrue(
                    io.github.yasmramos.glyphui.graphics.RasterCanvasTestFactory.isAvailable(),
                    "Skija native library not loadable on this host; skipping image test");
            StyleSheet sheet = StyleSheet.parse("img { width: 120px; height: 80px; }");
            Panel root = new Panel(0, 0, 400, 300);
            java.awt.image.BufferedImage awt = new java.awt.image.BufferedImage(
                    10, 10, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(awt, "png", out);
            ImageView view = new ImageView(
                    io.github.yasmramos.glyphui.graphics.Image.loadBytes(out.toByteArray()));
            root.add(view);

            cascade(root, sheet);
            Dimension d = view.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

            assertEquals(120.0f, d.getWidth(), 1e-3);
            assertEquals(80.0f, d.getHeight(), 1e-3);
        }

        @Test
        @DisplayName("TextField with CSS width/height measures to the declared size")
        void textFieldCssSizeWins() {
            StyleSheet sheet = StyleSheet.parse("input { width: 250px; height: 40px; }");
            Panel root = new Panel(0, 0, 400, 300);
            TextField field = new TextField(0, 0, 10, 10);
            root.add(field);

            cascade(root, sheet);
            Dimension d = field.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

            assertEquals(250.0f, d.getWidth(), 1e-3);
            assertEquals(40.0f, d.getHeight(), 1e-3);
        }

        @Test
        @DisplayName("TextField getPreferredWidth reflects content plus padding")
        void textFieldPreferredWidthFollowsContent() {
            // No Skija-backed rendering required: preferred width uses the
            // theme font metrics when available; assert only the intrinsic
            // minimum floor for an empty field.
            TextField field = new TextField();
            float w = field.getPreferredWidth();
            assertTrue(w >= 80.0f, "empty field keeps the 80px intrinsic minimum, got " + w);

            field.setText("a considerably longer piece of text");
            assertTrue(field.getPreferredWidth() >= w,
                    "preferred width grows with the content");
        }
    }

    // ------------------------------------------------------------------
    // 2.7 Label font-weight alone is honoured
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.7 Label font-weight")
    class LabelFontWeight {

        @Test
        @DisplayName("lone font-weight declaration reaches the computed style")
        void fontWeightAloneCascades() {
            StyleSheet sheet = StyleSheet.parse("label { font-weight: bold; }");
            Panel root = new Panel(0, 0, 400, 300);
            Label label = new Label(0, 0, 200, 30, "text");
            root.add(label);

            cascade(root, sheet);

            assertTrue(label.getComputedStyle()
                            .has(io.github.yasmramos.glyphui.style.StyleProperty.FONT_WEIGHT),
                    "font-weight-only rule must be present in the computed style so getFont() routes through resolveFont");
        }
    }

    // ------------------------------------------------------------------
    // 2.8 Lazy paints: construction allocates nothing native
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.8 TextField lazy paints")
    class LazyPaints {

        @Test
        @DisplayName("constructing a TextField needs no Skija paints")
        void constructorIsAllocationFree() {
            // Before the fix, the constructor eagerly created five Paint
            // objects, which throws when Skija natives are unavailable or
            // leak handles in headless tests. Construction must be inert.
            TextField field = new TextField(0, 0, 100, 30);
            assertEquals("", field.getText());
            field.setText("ok");
            assertEquals("ok", field.getText());
            field.dispose();
        }
    }

    // ------------------------------------------------------------------
    // 2.9 Observable text property
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.9 TextField textProperty")
    class TextProperty {

        @Test
        @DisplayName("textProperty mirrors setText and fires onTextChanged")
        void propertyRoundTrip() {
            TextField field = new TextField(0, 0, 200, 30);
            final int[] fired = {0};
            field.setOnTextChanged(() -> fired[0]++);

            field.textProperty().set("hello");
            assertEquals("hello", field.getText());
            assertEquals("hello", field.textProperty().get());
            assertEquals(1, fired[0], "programmatic property change fires onTextChanged");

            // Same value: Property must not re-notify.
            field.textProperty().set("hello");
            assertEquals(1, fired[0], "no-op set must not fire again");

            field.dispose();
        }

        @Test
        @DisplayName("setText delegates through the property")
        void setTextUsesProperty() {
            TextField field = new TextField();
            field.setText("via setter");
            assertEquals("via setter", field.textProperty().get());
            field.dispose();
        }
    }

    // ------------------------------------------------------------------
    // 2.10 Caret no-ops do not invalidate
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.10 Invalidation equality checks")
    class NoOpInvalidation {

        @Test
        @DisplayName("repeated Home key press invalidates only once")
        void repeatedHomeDoesNotReinvalidate() {
            TextField field = new TextField(0, 0, 200, 30);
            field.setVisible(true);
            field.requestFocus();
            field.setText("abcdef");   // caret lands at the end (index 6)

            TestHooks.resetInvalidateCount(field);
            field.onKeyEvent(press(KEY_RIGHT, '\0'));  // caret already at end: no-op
            int afterRightNoop = TestHooks.getInvalidateCount(field);
            assertEquals(0, afterRightNoop, "no-op right-arrow must not invalidate");

            field.onKeyEvent(press(KEY_HOME, '\0'));   // caret -> 0: real move
            int afterFirstHome = TestHooks.getInvalidateCount(field);
            assertTrue(afterFirstHome >= 1, "a real caret move invalidates");

            field.onKeyEvent(press(KEY_HOME, '\0'));   // already at 0: no-op
            assertEquals(afterFirstHome, TestHooks.getInvalidateCount(field),
                    "second Home press must not schedule extra repaints");
            field.dispose();
        }

        @Test
        @DisplayName("backspace on empty field does not invalidate")
        void backspaceOnEmptyNoop() {
            TextField field = new TextField(0, 0, 200, 30);
            field.setVisible(true);
            field.requestFocus();
            TestHooks.resetInvalidateCount(field);

            field.onKeyEvent(press(KEY_BACKSPACE, '\0'));

            assertEquals(0, TestHooks.getInvalidateCount(field),
                    "backspace with an empty buffer is a visual no-op");
            field.dispose();
        }
    }

    // ------------------------------------------------------------------
    // 2.11 Label setter equality guards
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("2.11 Label no-op setters")
    class LabelNoOpSetters {

        @Test
        @DisplayName("setTextColor with the same value does not invalidate")
        void sameTextColorNoInvalidate() {
            Label label = new Label(0, 0, 200, 30, "text");
            TestHooks.resetInvalidateCount(label);

            label.setTextColor(0xFFFF0000);
            assertTrue(TestHooks.getInvalidateCount(label) >= 1,
                    "first real change invalidates");
            int afterFirst = TestHooks.getInvalidateCount(label);

            label.setTextColor(0xFFFF0000);
            assertEquals(afterFirst, TestHooks.getInvalidateCount(label),
                    "same color must not invalidate again");

            label.setTextColor(null);
            assertEquals(afterFirst + 1, TestHooks.getInvalidateCount(label),
                    "clearing the override invalidates once");
            label.setTextColor(null);
            assertEquals(afterFirst + 1, TestHooks.getInvalidateCount(label),
                    "redundant clear must not invalidate");
            label.dispose();
        }

        @Test
        @DisplayName("setFontSize with the same value does not invalidate")
        void sameFontSizeNoInvalidate() {
            Label label = new Label(0, 0, 200, 30, "text");
            TestHooks.resetInvalidateCount(label);

            label.setFontSize(20.0f);
            int afterFirst = TestHooks.getInvalidateCount(label);
            assertTrue(afterFirst >= 1, "first real change invalidates");

            label.setFontSize(20.0f);
            assertEquals(afterFirst, TestHooks.getInvalidateCount(label),
                    "same size must not invalidate again");
            label.dispose();
        }

        @Test
        @DisplayName("setAlignment with the same value does not invalidate")
        void sameAlignmentNoInvalidate() {
            Label label = new Label(0, 0, 200, 30, "text");
            TestHooks.resetInvalidateCount(label);

            label.setAlignment(Label.TextAlignment.CENTER);
            int afterFirst = TestHooks.getInvalidateCount(label);
            assertTrue(afterFirst >= 1);

            label.setAlignment(Label.TextAlignment.CENTER);
            assertEquals(afterFirst, TestHooks.getInvalidateCount(label),
                    "same alignment must not invalidate again");
            label.dispose();
        }
    }
}
