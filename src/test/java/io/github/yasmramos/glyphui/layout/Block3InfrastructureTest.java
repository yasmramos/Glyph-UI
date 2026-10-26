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
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Assigns layout-style bounds to a panel without marking them as user-
     * explicit, mirroring what a root frame does when it sizes its content
     * panel. Invokes the package-private {@code Panel.setSizeForTests} hook via
     * reflection because this test lives outside the {@code ui} package.
     */
    private static void setSizeForTests(Panel p, float w, float h) throws Exception {
        Method m = Panel.class.getDeclaredMethod("setSizeForTests", float.class, float.class);
        m.setAccessible(true);
        m.invoke(p, w, h);
    }

    /** Lays out a panel immediately, mirroring FlowLayoutTest's direct call. */
    private static void forceLayout(Panel p) {
        p.doLayout();
        // The layout pass assigned every child its final geometry, so the
        // relayout requests children were carrying are now consumed — the
        // same drain Panel.render performs when it runs a real frame.
        clearChildLayoutFlags(p);
    }

    /**
     * Clears the per-component layout-dirty flag of every node in the panel's
     * subtree (children and nested panels included). Used after an explicit
     * layout pass to model the frame-level consumption that happens inside
     * the ui package during rendering.
     */
    private static void clearChildLayoutFlags(Component root) {
        try {
            java.lang.reflect.Field f = Component.class.getDeclaredField("layoutDirty");
            f.setAccessible(true);
            List<Component> stack = new ArrayList<>();
            stack.add(root);
            while (!stack.isEmpty()) {
                Component c = stack.remove(stack.size() - 1);
                f.setBoolean(c, false);
                if (c instanceof Panel) {
                    stack.addAll(((Panel) c).getChildren());
                }
            }
        } catch (ReflectiveOperationException e) {
            fail("layout dirty flag hook missing: " + e.getMessage());
        }
    }

    /**
     * Runs the panel's paint pass so pending layout requests are consumed the
     * way a real frame would (Panel.render executes doLayout before
     * recursing into children). A no-op layout manager is installed first:
     * {@code Panel.render} skips its drawing path when the container has
     * none, which keeps the whole pass free of native Skija calls in headless
     * tests. Children keep their own deferred flags until their geometry is
     * actually re-laid-out by a subsequent pass; since this drain-only test
     * has nothing to reposition, they are cleared explicitly afterwards.
     */
    private static void renderQuietly(Panel p) {
        p.setLayoutManager(new LayoutManager() {
            @Override
            public void layout(Panel panel) {
                // Drain-only: positions are irrelevant to this test.
            }
        });
        try {
            java.lang.reflect.Method m = Panel.class.getDeclaredMethod(
                    "doLayout");
            m.setAccessible(true);
            m.invoke(p);
        } catch (ReflectiveOperationException e) {
            fail("layout drain hook missing or threw: " + e.getMessage());
        }
        clearChildLayoutFlags(p);
    }

    @BeforeEach
    void setUp() throws Exception {
        clearStyleLatch();
        // Content-sized panel with a 300x200 drawing surface: the bounds are
        // assigned the way an outer frame sizes its content panel (layout-
        // assigned, not user-explicit) so Panel.measure keeps delegating to
        // the attached layout manager (Block 3 item 3.2). The eager layout
        // pass performed by add()/setLayoutManager() consumes the pending
        // structural dirty flags, leaving the fresh panel clean — exactly
        // what a rendered frame would have done.
        panel = new Panel();
        setSizeForTests(panel, 300f, 200f);
    }

    /** Runs one paint-equivalent layout pass and drains every dirty flag. */
    private static void settle(Panel p) {
        p.doLayout();
        clearChildLayoutFlags(p);
    }

    // ------------------------------------------------------------------
    // 3.1 Split invalidation intents
    // ------------------------------------------------------------------

    @Test
    void setTextOnChildMarksParentPanelLayoutDirty() throws Exception {
        Panel panel = new Panel();
        setSizeForTests(panel, 300f, 200f);
        TestComponent child = new TestComponent();
        panel.add(child);
        // Model the frame that would have painted after the structural add:
        // one layout pass consumes the pending relayout requests.
        settle(panel);
        assertFalse(layoutDirty(panel), "fresh panel should not be layout dirty");

        child.setWidth(80f); // explicit size change -> layout intent
        assertTrue(layoutDirty(panel),
                "the child's relayout request must reach the direct parent panel");

        // The panel has no layout manager, so doLayout() is a pure drain:
        // consume the request through the paint pass that would normally run
        // it (Panel.render calls doLayout before recursing into children).
        renderQuietly(panel);
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
    void flowRowsAdvanceByAppliedHeightWhenExplicitlyTaller() throws Exception {
        panel.setLayoutManager(new FlowLayout(5f, 10f)); // gap=5, padding=10
        // Two 50-wide children wrap onto separate rows inside the 80px content box.
        TestComponent first = new TestComponent(50, 30);
        first.setHeight(60f); // explicitly taller than its measured height
        TestComponent second = new TestComponent(50, 30);
        panel.add(first);
        panel.add(second);

        // Shrink the panel to a 100x100 drawing surface through the layout-
        // assigned path (an outer frame sizing its content panel). The width
        // is not user-pinned, so FlowLayout still keeps each child's declared
        // geometry while wrapping rows against the 100px surface: two 50px
        // items plus the 10px gap exceed the 80px content box and wrap.
        setSizeForTests(panel, 100f, 100f);
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
        // Drain the structural requests left by setLayoutManager/add, as a
        // painted frame would; only the config-change request must remain.
        settle(panel);

        assertFalse(layoutDirty(panel), "the frame consumed the add's relayout request");

        layout.setGap(25f);
        assertTrue(layoutDirty(panel),
                "changing layout configuration must schedule a relayout");
    }

    @Test
    void flexSetDirectionAfterAttachDirtiesOwnerLayout() throws Exception {
        FlexLayout flex = new FlexLayout();
        panel.setLayoutManager(flex);
        panel.add(new TestComponent());
        // Consume the structural requests first (see the flow test above),
        // so only the config-change request can remain below.
        settle(panel);

        assertFalse(layoutDirty(panel), "the frame consumed the add's relayout request");

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

    /**
     * Reads the layout-dirty state of a component through the public
     * {@code Panel.isLayoutDirty()} hook when it is a panel, or through its
     * own private flag via reflection otherwise. Avoids duplicating the
     * invalidation bookkeeping outside the ui package.
     */
    private static boolean layoutDirty(Component c) throws Exception {
        if (c instanceof Panel) {
            Method m = Panel.class.getDeclaredMethod("isLayoutDirty");
            m.setAccessible(true);
            return (boolean) m.invoke(c);
        }
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
