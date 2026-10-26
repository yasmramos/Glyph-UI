package io.github.yasmramos.glyphui.layout;

import io.github.yasmramos.glyphui.graphics.Dimension;
import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ComponentState;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.TestComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression suite for Block 3 (infrastructure refactor).
 *
 * <p>Covers the split invalidation intents (3.1), the {@code LayoutManager}
 * measure contract with {@code Panel} delegation (3.2), flow-row advancement
 * by applied height (3.3), CSS-driven gap/padding in {@code FlowLayout}
 * (3.4), layout-config change notification to the owning panel (3.5) and the
 * late {@code Application} resolution of properties (3.7).</p>
 */
public class Block3InfrastructureTest {

    /** Shared panel used by tests that need pre-set bounds without a constructor. */
    private Panel panel;

    /** Drains the global style-dirty latch so each test starts clean. */
    private static void clearStyleLatch() throws Exception {
        Method m = Component.class.getDeclaredMethod("resetStyleDirtyForTests");
        m.setAccessible(true);
        m.invoke(null);
    }

    /** Lays out a panel immediately, mirroring FlowLayoutTest's direct call. */
    private static void forceLayout(Panel p) {
        p.doLayout();
    }

    @BeforeEach
    void setUp() throws Exception {
        clearStyleLatch();
        // Content-sized panel with a 300x200 drawing surface: the bounds are
        // assigned through setWidth/setHeight (as application code would do)
        // WITHOUT pinning them as user-explicit, so Panel.measure can still
        // delegate to the layout manager (Block 3 item 3.2). The test-only
        // hook mirrors what a root frame does when it sizes its content panel.
        panel = new Panel(0f, 0f, 0f, 0f);
        panel.setSizeForTests(300f, 200f);
    }

    // ------------------------------------------------------------------
    // 3.1 Split invalidation intents
    // ------------------------------------------------------------------

    @Test
    void setTextOnChildMarksParentPanelLayoutDirty() throws Exception {
        Panel panel = new Panel(0f, 0f, 0f, 0f);
        panel.setSizeForTests(300f, 200f);
        TestComponent child = new TestComponent();
        panel.add(child);
        assertFalse(layoutDirty(panel), "fresh panel should not be layout dirty");

        child.setWidth(80f); // explicit size change -> layout intent
        assertTrue(layoutDirty(panel),
                "the child's relayout request must reach the direct parent panel");

        panel.doLayout(); // consume the pending relayout (no layout manager: no-op drain)
        assertFalse(layoutDirty(panel),
                "a layout invalidation must keep the panel dirty until the relayout runs");
    }

    @Test
    void stateChangeSetsStyleLatchAndLayoutFlag() throws Exception {
        TestComponent c = new TestComponent();
        clearStyleLatch();
        c.setState(ComponentState.HOVER);

        assertTrue(Component.consumeStyleDirty(),
                "setState changes pseudo-class matching, so a re-cascade must be requested");
        // A detached component has no panel to dirty; attaching one first and
        // mutating again proves the style intent also carries a relayout.
        Panel host = new Panel(300f, 200f);
        TestComponent attached = new TestComponent();
        host.add(attached);
        clearStyleLatch();
        attached.setState(ComponentState.HOVER);
        assertTrue(layoutDirty(host),
                "style invalidation also implies a relayout (styles can change sizes)");
    }

    @Test
    void addStyleClassInvalidatesStyleButNotUnrelatedComponents() {
        TestComponent c = new TestComponent();
        clearStyleLatchQuietly();
        c.addStyleClass("active");
        assertTrue(Component.consumeStyleDirty(),
                "addStyleClass must request a cascade pass");

        // Setting the same class again is a no-op: the latch must stay clean.
        c.addStyleClass("active");
        assertFalse(Component.consumeStyleDirty(),
                "no-op mutations must not dirty the cascade");
    }

    @Test
    void inlineStyleMutationInvalidatesStyleLatch() {
        TestComponent c = new TestComponent();
        clearStyleLatchQuietly();
        c.setInlineStyle(Style.builder().length(StyleProperty.PADDING, 8f).build());
        assertTrue(Component.consumeStyleDirty(),
                "setInlineStyle must request a re-cascade");
    }

    // ------------------------------------------------------------------
    // 3.2 LayoutManager.measure + Panel delegation
    // ------------------------------------------------------------------

    @Test
    void flowLayoutMeasureReturnsContentBoundingBox() {
        panel.setLayoutManager(new FlowLayout(10f, 10f));
        panel.add(new TestComponent()); // preferred 50x30
        panel.add(new TestComponent());

        Dimension d = panel.measure(300f, 200f);
        // One row: 10(pad) + 50 + 10(gap) + 50 + 10(pad) = 130 wide
        assertEquals(130f, d.getWidth(), 0.01f,
                "flow measure must include children widths, gaps and padding");
        assertEquals(50f, d.getHeight(), 0.01f,
                "flow measure height = one row (30) plus top+bottom padding (20)");
    }

    @Test
    void flexRowMeasureSumsMainAxisAndMaxCrossAxis() {
        FlexLayout flex = new FlexLayout(FlexLayout.Direction.ROW,
                FlexLayout.Justify.FLEX_START, FlexLayout.Align.FLEX_START);
        flex.setGap(10f);
        flex.setPadding(10f);
        panel.setLayoutManager(flex);
        panel.add(new TestComponent()); // 50x30
        panel.add(new TestComponent());

        Dimension d = panel.measure(300f, 200f);
        assertEquals(130f, d.getWidth(), 0.01f,
                "row direction main axis: pad + 50 + gap + 50 + pad");
        assertEquals(50f, d.getHeight(), 0.01f,
                "cross axis: max child height + vertical padding");
    }

    @Test
    void panelWithExplicitBoundsSkipsLayoutManagerMeasure() {
        Panel panel = new Panel(250f, 90f);
        panel.setLayoutManager(new FlowLayout(10f, 10f));
        panel.add(new TestComponent());

        Dimension d = panel.measure(1000f, 1000f);
        assertEquals(250f, d.getWidth(), 0.01f,
                "explicit bounds are a hard constraint and win over content sizing");
        assertEquals(90f, d.getHeight(), 0.01f);
    }

    // ------------------------------------------------------------------
    // 3.3 FlowLayout advances rows by the APPLIED height
    // ------------------------------------------------------------------

    @Test
    void flowRowsAdvanceByAppliedHeightWhenExplicitlyTaller() {
        panel.setLayoutManager(new FlowLayout(5f, 10f)); // gap=5, padding=10
        // Two 50-wide children wrap onto separate rows inside the 80px content box.
        TestComponent first = new TestComponent(50, 30);
        first.setHeight(60f); // explicitly taller than its measured height
        TestComponent second = new TestComponent(50, 30);
        panel.add(first);
        panel.add(second);

        forceLayout(panel);

        // Row advance must use the applied height (60), not the measured one:
        // second.y = padding(10) + 60 + gap(5) = 75. With the old bug it was 45.
        assertEquals(75f, second.getY(), 0.01f,
                "the next row must start below the applied height to avoid overlap");
    }

    // ------------------------------------------------------------------
    // 3.4 FlowLayout reads gap/padding from the panel's CSS
    // ------------------------------------------------------------------

    @Test
    void flowLayoutReadsGapAndPaddingFromPanelComputedStyle() {
        panel.setInlineStyle(Style.builder()
                .length(StyleProperty.GAP, 30f)
                .length(StyleProperty.PADDING, 20f)
                .build());
        panel.setLayoutManager(new FlowLayout()); // constructor defaults 10/10

        TestComponent a = new TestComponent(50, 30);
        TestComponent b = new TestComponent(50, 30);
        panel.add(a);
        panel.add(b);
        forceLayout(panel);

        assertEquals(20f, a.getX(), 0.01f, "CSS padding must inset the first item");
        assertEquals(100f, b.getX(), 0.01f,
                "CSS gap (20 + 50 + 30) must space items, overriding the field default");
    }

    // ------------------------------------------------------------------
    // 3.5 Layout configuration setters notify the owning panel
    // ------------------------------------------------------------------

    @Test
    void flowSetGapAfterAttachDirtiesOwnerLayout() throws Exception {
        FlowLayout layout = new FlowLayout(5f, 10f);
        panel.setLayoutManager(layout);
        panel.add(new TestComponent());

        assertFalse(layoutDirty(panel), "add consumed the flag via propagation");

        layout.setGap(25f);
        assertTrue(layoutDirty(panel),
                "changing layout configuration must schedule a relayout");
    }

    @Test
    void flexSetDirectionAfterAttachDirtiesOwnerLayout() throws Exception {
        FlexLayout flex = new FlexLayout();
        panel.setLayoutManager(flex);
        panel.add(new TestComponent());

        flex.setDirection(FlexLayout.Direction.COLUMN);
        assertTrue(layoutDirty(panel),
                "FlexLayout config changes must reach the owning panel");
    }

    @Test
    void detachedLayoutSetterDoesNotThrow() {
        FlowLayout layout = new FlowLayout();
        assertDoesNotThrow(() -> layout.setPadding(15f),
                "notifyOwner must be a no-op while the manager is detached");
    }

    // ------------------------------------------------------------------
    // 3.7 Property resolves the Application at set time, not construction
    // ------------------------------------------------------------------

    @Test
    void propertyWithoutApplicationAppliesValueImmediately() {
        AtomicInteger changeCount = new AtomicInteger();
        io.github.yasmramos.glyphui.graphics.Property<String> p =
                new io.github.yasmramos.glyphui.graphics.Property<>(null, "a",
                        v -> changeCount.incrementAndGet());
        p.set("b");
        assertEquals("b", p.get(),
                "a null-application property applies synchronously");
        assertEquals(1, changeCount.get(),
                "the change hook must run exactly once for one real change");
        p.set("b"); // no-op set must not re-notify
        assertEquals(1, changeCount.get());
    }

    /** Reads the package-private layout-dirty flag via reflection. */
    private static boolean layoutDirty(Component c) throws Exception {
        java.lang.reflect.Field f = Component.class.getDeclaredField("layoutDirty");
        f.setAccessible(true);
        return f.getBoolean(c);
    }

    /** Quiet wrapper used inside assertions that consume the latch themselves. */
    private static void clearStyleLatchQuietly() {
        try {
            clearStyleLatch();
        } catch (Exception e) {
            fail("style latch reset hook missing: " + e.getMessage());
        }
    }
}
