package io.github.yasmramos.glyphui.integration;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.humbleui.skija.Color;
import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.graphics.Theme;
import io.github.yasmramos.glyphui.layout.FlexLayout;
import io.github.yasmramos.glyphui.reactive.Computed;
import io.github.yasmramos.glyphui.reactive.Reactor;
import io.github.yasmramos.glyphui.reactive.Signal;
import io.github.yasmramos.glyphui.reactive.SignalList;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.style.StyleSheet;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ImageView;
import io.github.yasmramos.glyphui.ui.Label;
import io.github.yasmramos.glyphui.ui.ObjectFit;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for Block 5 (missing features): reactive ergonomics
 * (peek/update), TextField submit and word-selection, ImageView object-fit
 * and placeholder, FlexLayout wrap/shrink/baseline, Panel background
 * resolution (theme-aware, CSS-aware, explicit flag) and display:none in
 * rendering. Mirrors the item numbering of the Block 5 design-debt list.
 */
class Block5FeaturesTest extends TestBase {

    private static final int KEY_LEFT = 263;
    private static final int KEY_RIGHT = 262;
    private static final int KEY_ENTER = 257;

    /** A panel with fixed geometry that never delegates to a layout manager. */
    private static Panel container(float width, float height) {
        Panel panel = new Panel(0, 0, width, height);
        panel.setComputedStyle(Style.builder().build());
        return panel;
    }

    /**
     * A button whose measured size is pinned through CSS width/height. The
     * component itself stays content-sized (no explicit constructor bounds);
     * the declared box must survive measurement untouched so wrap/shrink
     * accounting sees the true overflow.
     */
    private static Button sizedButton(String text, float w, float h) {
        // No caption: an empty label keeps the intrinsic width at padding
        // only, and the CSS-declared box below wins in measure() regardless
        // of the constraint handed by the layout pass.
        Button b = new Button();
        b.setInlineStyle(Style.builder()
                .length(StyleProperty.WIDTH, w)
                .length(StyleProperty.HEIGHT, h)
                .build());
        return b;
    }

    /**
     * A button with explicit constructor bounds plus a caption. Mirrors the
     * historical FlexLayoutTest fixture: bounds-pinned widgets keep their
     * geometry through layout passes because Panel/Button treat a
     * constructor-pinned size as authoritative in measure().
     */
    private static Button boxedButton(String text, float w, float h) {
        Button b = new Button(w, h, text);
        return b;
    }

    /** Asserts a button pinned by {@link #sizedButton} measures to its box. */
    private static void assertPinnedSize(Button b, float w, float h) {
        Dimension m = b.measure(Float.MAX_VALUE, Float.MAX_VALUE);
        assertEquals(w, m.getWidth(), 0.01f, "pinned CSS width must win in measure");
        assertEquals(h, m.getHeight(), 0.01f, "pinned CSS height must win in measure");
    }

    @BeforeEach
    void setUpUiThread() {
        Reactor.setUiThread(Thread.currentThread());
    }

    @AfterEach
    void clearUiThread() {
        Reactor.setUiThread(null);
    }

    // ------------------------------------------------------------------
    // 5.1 peek(): read without registering a dependency
    // ------------------------------------------------------------------

    @Test
    void signalPeekReadsValueWithoutTracking() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger effectRuns = new AtomicInteger();
        Computed<Integer> doubled = new Computed<>(() -> {
            effectRuns.incrementAndGet();
            return count.peek() * 2;
        });
        // peek() never subscribes, so the computed has no sources: it is a
        // pure snapshot of whatever was cached at its last evaluation.
        assertEquals(0, doubled.get());
        int runsAfterFirstEval = effectRuns.get();
        count.set(5);
        // peek() must not register a dependency: the computed body never
        // re-runs while it is observed only through peek().
        assertEquals(runsAfterFirstEval, effectRuns.get());
        // peek() returns the current source value without subscribing...
        assertEquals(5, count.peek());
        assertEquals(runsAfterFirstEval, effectRuns.get());
        // ...and the computed body never re-runs: it has no registered
        // dependency on `count`, so it stays clean and returns its snapshot.
        assertEquals(0, doubled.get());
        assertEquals(runsAfterFirstEval, effectRuns.get());
    }

    @Test
    void computedAndSignalListPeekReturnCurrentValues() {
        Signal<Integer> base = new Signal<>(2);
        Computed<Integer> squared = new Computed<>(() -> base.get() * base.get());
        assertEquals(4, squared.peek());
        base.set(3);
        // peek() is side-effect free: while dirty it returns the cached value
        // instead of re-running the function. get() recomputes on demand.
        assertEquals(4, squared.peek());
        assertEquals(9, squared.get());

        SignalList<String> list = new SignalList<>();
        list.add("a");
        list.add("b");
        assertEquals(2, list.peek().size());
        assertEquals("a", list.peek().get(0));
    }

    // ------------------------------------------------------------------
    // 5.2 Signal.update(): atomic read-modify-write
    // ------------------------------------------------------------------

    @Test
    void signalUpdateAppliesFunctionAndNotifies() {
        Signal<Integer> counter = new Signal<>(10);
        AtomicInteger notified = new AtomicInteger();
        counter.subscribe(notified::incrementAndGet);
        counter.update(v -> v + 5);
        assertEquals(15, counter.get());
        assertEquals(1, notified.get());
        counter.update(v -> v * 2);
        assertEquals(30, counter.get());
        assertEquals(2, notified.get());
    }

    @Test
    void signalUpdateWithIdentityDoesNotNotify() {
        Signal<Integer> counter = new Signal<>(7);
        AtomicInteger notified = new AtomicInteger();
        counter.subscribe(notified::incrementAndGet);
        counter.update(v -> v);
        assertEquals(0, notified.get());
    }

    // ------------------------------------------------------------------
    // 5.3 TextField.onSubmit: Enter fires the submit handler
    // ------------------------------------------------------------------

    @Test
    void enterFiresSubmitHandlerAndCollapsesSelection() {
        FocusManager previous = FocusManager.getGlobalFocusManager();
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
        try {
            TextField field = new TextField(0, 0, 200, 30);
            field.setVisible(true);
            field.requestFocus();
            field.setText("hello world");
            AtomicInteger submits = new AtomicInteger();
            field.setOnSubmit(submits::incrementAndGet);

            // Select everything, then press Enter: selection collapses and
            // the submit handler runs exactly once.
            field.selectAll();
            assertTrue(field.hasSelection());
            field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_ENTER, ' ',
                    EnumSet.noneOf(KeyModifier.class)));
            assertEquals(1, submits.get());
            assertFalse(field.hasSelection());

            // Enter again with no handler change still fires.
            field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_ENTER, ' ',
                    EnumSet.noneOf(KeyModifier.class)));
            assertEquals(2, submits.get());
            field.dispose();
        } finally {
            FocusManager.setGlobalFocusManager(previous);
        }
    }

    // ------------------------------------------------------------------
    // 5.4 Ctrl+Shift+Left/Right extends the selection by words
    // ------------------------------------------------------------------

    @Test
    void ctrlShiftArrowsSelectWordWise() {
        FocusManager previous = FocusManager.getGlobalFocusManager();
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
        try {
            TextField field = new TextField(0, 0, 200, 30);
            field.setVisible(true);
            field.requestFocus();
            field.setText("one two three");
            // Caret sits at the end after setText. Move one word left while
            // selecting: the tail word "three" becomes the selection.
            field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_LEFT, ' ',
                    EnumSet.of(KeyModifier.CTRL, KeyModifier.SHIFT)));
            assertTrue(field.hasSelection(), "Ctrl+Shift+Left must extend selection");
            assertEquals("three", field.getSelectedText());
            // And right shrinks it back word-wise.
            field.onKeyEvent(new KeyEvent(KeyEventType.PRESS, KEY_RIGHT, ' ',
                    EnumSet.of(KeyModifier.CTRL, KeyModifier.SHIFT)));
            assertFalse(field.hasSelection());
            field.dispose();
        } finally {
            FocusManager.setGlobalFocusManager(previous);
        }
    }

    // ------------------------------------------------------------------
    // 5.7 / 5.8 ImageView object-fit and placeholder
    // ------------------------------------------------------------------

    @Test
    void imageViewDefaultsToObjectFitFillAndGrayPlaceholder() {
        ImageView view = new ImageView();
        view.setWidth(100);
        view.setHeight(100);
        assertEquals(ObjectFit.FILL, view.getObjectFit());
        assertEquals(Color.makeARGB(255, 128, 128, 128), view.getPlaceholderColor());
        view.setObjectFit(ObjectFit.CONTAIN);
        assertEquals(ObjectFit.CONTAIN, view.getObjectFit());
        // Null fit keeps the current value (documented contract).
        view.setObjectFit(null);
        assertEquals(ObjectFit.CONTAIN, view.getObjectFit());
        int blue = Color.makeARGB(255, 0, 0, 255);
        view.setPlaceholderColor(blue);
        assertEquals(blue, view.getPlaceholderColor());
        view.dispose();
    }

    // ------------------------------------------------------------------
    // 5.9 FlexLayout flex-wrap: items that overflow start a new line
    // ------------------------------------------------------------------

    @Test
    void rowWrapMovesOverflowingItemsToNewLines() {
        FlexLayout flex = new FlexLayout(FlexLayout.Direction.ROW,
                FlexLayout.Justify.FLEX_START, FlexLayout.Align.FLEX_START,
                FlexLayout.Wrap.WRAP);
        flex.setGap(0);
        Panel panel = container(100, 100);
        panel.setLayoutManager(flex);

        Button a = sizedButton("A", 60, 20);
        Button b = sizedButton("B", 60, 20);
        Button c = sizedButton("C", 60, 20);
        panel.add(a);
        panel.add(b);
        panel.add(c);
        flex.layout(panel);

        // a fits on line 1; b and c would overflow 100px so they wrap.
        assertEquals(0f, a.getY(), 0.01f);
        assertEquals(0f, b.getX(), 0.01f);
        assertEquals(20f, b.getY(), 0.01f);
        assertEquals(40f, c.getY(), 0.01f);
    }

    @Test
    void nowrapKeepsHistoricalSingleLineBehavior() {
        FlexLayout flex = new FlexLayout();
        flex.setDirection(FlexLayout.Direction.ROW);
        flex.setGap(0);
        Panel panel = container(100, 100);
        panel.setLayoutManager(flex);

        Button a = boxedButton("A", 60, 20);
        Button b = boxedButton("B", 60, 20);
        panel.add(a);
        panel.add(b);
        flex.layout(panel);

        // Without wrap, b stays on the same (overflowing) line.
        assertEquals(60f, b.getX(), 0.01f);
        assertEquals(0f, b.getY(), 0.01f);
    }

    @Test
    void cssFlexWrapDrivesMultiLineLayout() {
        FlexLayout flex = new FlexLayout();
        Panel panel = container(100, 100);
        panel.setComputedStyle(Style.builder()
                .keyword(StyleProperty.FLEX_WRAP, "wrap")
                .build());
        panel.setLayoutManager(flex);

        Button a = boxedButton("A", 60, 20);
        Button b = boxedButton("B", 60, 20);
        for (Button btn : new Button[]{a, b}) {
            panel.add(btn);
        }
        // gap: 0 declared in the container's computed style so line offsets
        // are deterministic (the layout default gap would otherwise add 10px).
        panel.setComputedStyle(Style.builder()
                .keyword(StyleProperty.FLEX_WRAP, "wrap")
                .setFloat(StyleProperty.GAP, 0f)
                .build());
        flex.layout(panel);

        assertEquals(20f, b.getY(), 0.01f,
                "flex-wrap: wrap in computed style must enable multi-line layout");
    }

    // ------------------------------------------------------------------
    // 5.9 FlexLayout flex-shrink: overflowing items shrink proportionally
    // ------------------------------------------------------------------

    @Test
    void overflowShrinksItemsProportionallyToFlexShrink() {
        FlexLayout flex = new FlexLayout(FlexLayout.Direction.ROW,
                FlexLayout.Justify.FLEX_START, FlexLayout.Align.FLEX_START);
        flex.setGap(0);
        Panel panel = container(100, 50);
        panel.setLayoutManager(flex);

        Button a = sizedButton("A", 80, 20);
        Button b = sizedButton("B", 80, 20);
        a.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 80)
                .length(StyleProperty.HEIGHT, 20)
                .setFloat(StyleProperty.FLEX_SHRINK, 1f)
                .build());
        b.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 80)
                .length(StyleProperty.HEIGHT, 20)
                .setFloat(StyleProperty.FLEX_SHRINK, 1f)
                .build());
        panel.add(a);
        panel.add(b);
        flex.layout(panel);

        // Total base 160 into 100 => deficit 60 split evenly (equal weights).
        assertEquals(50f, a.getWidth(), 0.5f);
        assertEquals(50f, b.getWidth(), 0.5f);
        assertEquals(50f, b.getX(), 0.5f);
    }

    @Test
    void flexShrinkZeroKeepsItemAtBaseSize() {
        FlexLayout flex = new FlexLayout(FlexLayout.Direction.ROW,
                FlexLayout.Justify.FLEX_START, FlexLayout.Align.FLEX_START);
        flex.setGap(0);
        Panel panel = container(100, 50);
        panel.setLayoutManager(flex);

        Button a = sizedButton("A", 80, 20);
        a.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 80)
                .length(StyleProperty.HEIGHT, 20)
                .setFloat(StyleProperty.FLEX_SHRINK, 0f)
                .build());
        Button b = sizedButton("B", 80, 20);
        b.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 80)
                .length(StyleProperty.HEIGHT, 20)
                .setFloat(StyleProperty.FLEX_SHRINK, 1f)
                .build());
        panel.add(a);
        panel.add(b);
        flex.layout(panel);

        // Only b absorbs the 60px deficit; a keeps its base size.
        assertEquals(80f, a.getWidth(), 0.5f);
        assertEquals(20f, b.getWidth(), 0.5f);
    }

    // ------------------------------------------------------------------
    // 5.10 align-items: baseline shares one text baseline per line
    // ------------------------------------------------------------------

    @Test
    void baselineAlignmentPositionsItemsByFontBaselineApproximation() {
        FlexLayout flex = new FlexLayout(FlexLayout.Direction.ROW,
                FlexLayout.Justify.FLEX_START, FlexLayout.Align.BASELINE);
        flex.setGap(0);
        Panel panel = container(200, 100);
        panel.setLayoutManager(flex);

        // Both buttons report font-size 16 via computed style; heights differ.
        Button small = sizedButton("S", 40, 20);
        small.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 40)
                .length(StyleProperty.HEIGHT, 20)
                .setFloat(StyleProperty.FONT_SIZE, 16f)
                .build());
        Button big = sizedButton("B", 40, 40);
        big.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 40)
                .length(StyleProperty.HEIGHT, 40)
                .setFloat(StyleProperty.FONT_SIZE, 16f)
                .build());
        panel.add(small);
        panel.add(big);
        flex.layout(panel);

        // Baseline offset = h/2 + fontSize/4 => small: 10+4=14, big: 20+4=24.
        // Shared max baseline 24 => small.y = 24-14 = 10, big.y = 0.
        assertEquals(10f, small.getY(), 0.01f);
        assertEquals(0f, big.getY(), 0.01f);
    }

    @Test
    void nonTextItemsAlignAtBottomUnderBaselineMode() {
        FlexLayout flex = new FlexLayout(FlexLayout.Direction.ROW,
                FlexLayout.Justify.FLEX_START, FlexLayout.Align.BASELINE);
        Panel panel = container(200, 100);
        panel.setLayoutManager(flex);

        Button plain = sizedButton("P", 40, 30); // no FONT_SIZE => baseline = bottom
        Button texty = sizedButton("T", 40, 20);
        texty.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 40)
                .length(StyleProperty.HEIGHT, 20)
                .setFloat(StyleProperty.FONT_SIZE, 16f)
                .build());
        panel.add(plain);
        panel.add(texty);
        flex.layout(panel);

        // Max baseline = 30 (plain bottom). Texty baseline = 20/2+4 = 14,
        // so texty.y = 30 - 14 = 16.
        assertEquals(0f, plain.getY(), 0.01f);
        assertEquals(16f, texty.getY(), 0.01f);
    }

    // ------------------------------------------------------------------
    // 5.25 / 5.26 / 5.27 Panel background resolution
    // ------------------------------------------------------------------

    @Test
    void panelBackgroundResolvesThroughComponentThemeAccessor() {
        Panel panel = new Panel(0, 0, 10, 10);
        // No explicit background: falls back to the active theme color.
        assertFalse(hasExplicitBackground(panel));
        assertEquals(Theme.current().getBackgroundColor(), panel.getBackgroundColor());
    }

    @Test
    void explicitlyTransparentBackgroundIsPreserved() {
        Panel panel = new Panel(0, 0, 10, 10);
        int transparent = Color.makeARGB(0, 0, 0, 0);
        panel.setBackgroundColor(transparent);
        // Item 5.26: an explicit transparent color must NOT fall back to the
        // theme background; the hasExplicitBackground flag distinguishes it.
        assertTrue(hasExplicitBackground(panel));
        assertEquals(transparent, panel.getBackgroundColor());
    }

    @Test
    void cssBackgroundIsPaintedEvenWithAlphaZeroSentinelField() {
        Panel panel = new Panel(0, 0, 10, 10);
        // Item 5.27: getBackgroundColor() consults the resolved style, so a
        // CSS-provided background wins over the sentinel field regardless of
        // its alpha byte.
        int red = Color.makeARGB(255, 255, 0, 0);
        panel.setComputedStyle(Style.builder()
                .color(StyleProperty.BACKGROUND, red)
                .build());
        assertEquals(red, panel.getBackgroundColor());
        assertPaintsBackground(panel);
    }

    // ------------------------------------------------------------------
    // 5.28 display: none hides children from rendering too
    // ------------------------------------------------------------------

    @Test
    void displayNoneChildIsSkippedByPanelRender() {
        Panel panel = container(100, 100);
        Button hidden = sizedButton("H", 20, 20);
        hidden.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, 20)
                .length(StyleProperty.HEIGHT, 20)
                .keyword(StyleProperty.DISPLAY, "none")
                .build());
        Button shown = sizedButton("S", 20, 20);
        panel.add(hidden);
        panel.add(shown);

        // Item 5.28: the render loop consults the same predicate as layout,
        // so a display:none child is skipped when painting too.
        assertTrue(PanelIsDisplayNone(hidden), "display:none child must be flagged");
        assertFalse(PanelIsDisplayNone(shown));
    }


    // ------------------------------------------------------------------
    // 5.6 Label line step includes leading (multiline spacing)
    // ------------------------------------------------------------------

    @Test
    void labelPreferredHeightAccountsForLeadingInMultiline() {
        // Content-sized label: explicit bounds would short-circuit the
        // preferred-height computation with the fixed height value.
        Label label = new Label("");
        label.setText("line one\nline two\nline three");
        label.measure(200, 100);
        // Three lines must be taller than two lines' worth of space; the
        // exact px depend on font metrics, so we assert monotonic growth.
        float threeLines = label.getPreferredHeight();
        label.setText("line one\nline two");
        label.measure(200, 100);
        float twoLines = label.getPreferredHeight();
        assertTrue(threeLines > twoLines,
                "each extra line must add ascent+descent+leading to the preferred height");
        label.dispose();
    }

    // ------------------------------------------------------------------
    // Reflection helpers for package-private Block 5 contracts
    // ------------------------------------------------------------------

    /** Calls the package-private Panel.isDisplayNone static predicate. */
    private static boolean PanelIsDisplayNone(Component c) {
        try {
            Class<?> panel = Class.forName("io.github.yasmramos.glyphui.ui.Panel");
            java.lang.reflect.Method m = panel.getDeclaredMethod("isDisplayNone", Component.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(null, c);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Panel.isDisplayNone() contract missing", e);
        }
    }

    private static boolean hasExplicitBackground(Panel p) {
        try {
            java.lang.reflect.Method m = Panel.class.getDeclaredMethod("hasExplicitBackground");
            m.setAccessible(true);
            return (Boolean) m.invoke(p);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Panel.hasExplicitBackground() contract missing", e);
        }
    }

    private static void assertPaintsBackground(Panel p) {
        try {
            java.lang.reflect.Method m = Panel.class.getDeclaredMethod("shouldPaintBackground");
            m.setAccessible(true);
            assertTrue((Boolean) m.invoke(p), "panel with CSS background must paint it");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Panel.shouldPaintBackground() contract missing", e);
        }
    }
}
