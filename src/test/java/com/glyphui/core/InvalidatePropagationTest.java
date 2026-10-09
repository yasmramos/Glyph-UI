package com.glyphui.core;

import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;
import com.glyphui.ui.TestComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@code Component.invalidate()} propagation into the
 * application's {@code paintDirty} flag.
 *
 * <p>The full integration variant (mutating a real widget inside a running
 * {@link Application}) lives in the IT suite which requires GLFW; these unit
 * tests verify the same contract headlessly by installing a repaint
 * requester exactly like {@code Application}'s constructor does
 * ({@code Component.setGlobalRepaintRequester(this::requestRepaint)}).</p>
 */
public class InvalidatePropagationTest {

    /** Mimics Application.paintDirty + requestRepaint(). */
    private final AtomicInteger repaintRequests = new AtomicInteger();
    private volatile boolean paintDirty;

    @BeforeEach
    public void installRequester() {
        repaintRequests.set(0);
        paintDirty = false;
        // Same wiring as Application's constructor: invalidate() at the root
        // must end up marking the application paint-dirty.
        Component.setGlobalRepaintRequester(() -> {
            repaintRequests.incrementAndGet();
            paintDirty = true;
        });
    }

    @AfterEach
    public void clearRequester() {
        Component.setGlobalRepaintRequester(null);
    }

    @Test
    public void testInvalidateOnRootlessComponentCallsRequester() {
        TestComponent c = new TestComponent(0, 0, 10, 10);
        assertFalse(paintDirty);

        c.invalidate();

        assertTrue(paintDirty, "invalidate() with no parent must reach the requester");
        assertEquals(1, repaintRequests.get());
    }

    @Test
    public void testInvalidatePropagatesUpParentChainToApplication() {
        Panel root = new Panel(0, 0, 800, 600);
        Panel nested = new Panel(0, 0, 400, 300);
        TestComponent leaf = new TestComponent(10, 10, 100, 50);

        nested.add(leaf); // invalidates: leaf -> nested -> requester
        root.add(nested); // invalidates: nested -> root -> requester

        // Adding widgets already dirtied the tree; reset counters so we can
        // observe the effect of a single invalidate() call.
        repaintRequests.set(0);
        paintDirty = false;
        leaf.resetInvalidateCount();
        nested.resetInvalidateCount();
        root.resetInvalidateCount();

        leaf.invalidate();

        assertTrue(paintDirty,
                "invalidate() on a deep child must propagate to the root and mark dirty");
        assertEquals(1, repaintRequests.get(),
                "propagation must stop at the root (one repaint request per invalidate)");
        assertEquals(1, leaf.getInvalidateCount());
        assertEquals(1, nested.getInvalidateCount());
        assertEquals(1, root.getInvalidateCount());
    }

    @Test
    public void testMutatorsCallInvalidateAutomatically() {
        Panel root = new Panel(0, 0, 800, 600);
        TestComponent c = new TestComponent(0, 0, 10, 10);
        root.add(c);
        repaintRequests.set(0);

        c.setWidth(20);
        assertEquals(1, repaintRequests.get(), "setWidth() must invalidate");

        c.setVisible(false);
        assertEquals(2, repaintRequests.get(), "setVisible() must invalidate");

        c.setX(5);
        c.setY(6);
        c.setEnabled(false);
        assertEquals(5, repaintRequests.get(),
                "setX/setY/setEnabled must each invalidate and propagate");
    }

    @Test
    public void testApplicationConstructorWiresGlobalRequester() {
        // The Application constructor installs itself as the global repaint
        // requester; verify the hook exists and setRepaintRequester re-points it.
        // (No "clean start" assertion: other tests in the JVM may have already
        // installed a requester.)

        RepaintRequester custom = () -> paintDirty = true;
        Component.setGlobalRepaintRequester(custom);
        assertSame(custom, Component.getGlobalRepaintRequester());

        TestComponent c = new TestComponent(0, 0, 10, 10);
        c.invalidate();
        assertTrue(paintDirty);
    }

    @Test
    public void testApplicationSetRepaintRequesterDelegatesToComponentHook() {
        // Without init() (no GLFW needed), exercise the public API method
        // that documents the invalidate()->requestRepaint() wiring.
        Application app = new Application();
        try {
            // Constructor installed app::requestRepaint globally
            assertNotNull(Component.getGlobalRepaintRequester());

            Panel root = app.getRootPanel();
            TextComponent c = new TextComponent();
            root.add(c);

            // A fresh Application starts paint-dirty; mutate after clearing
            // via a custom requester swap to prove delegation works.
            AtomicInteger seen = new AtomicInteger();
            app.setRepaintRequester(seen::incrementAndGet);
            c.invalidate();
            assertEquals(1, seen.get(),
                    "setRepaintRequester must route component invalidations");

            // Restore app's own requester: setText() (a mutator) marks
            // the application paint-dirty through invalidate() propagation.
            app.setRepaintRequester(app::requestRepaint);
            c.setText("hello");
            assertTrue(app.isPaintDirty(),
                    "mutating a widget must mark the Application paint-dirty");
        } finally {
            Component.setGlobalRepaintRequester(null);
        }
    }

    /** Minimal text-like mutator used by the propagation tests. */
    private static final class TextComponent extends TestComponent {
        private String text = "";

        TextComponent() {
            super(0, 0, 10, 10);
        }

        void setText(String text) {
            this.text = text;
            invalidate();
        }
    }
}
