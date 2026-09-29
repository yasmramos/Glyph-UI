package com.glyphui.integration;

import com.glyphui.core.Application;
import com.glyphui.ui.Component;
import com.glyphui.ui.Label;
import com.glyphui.ui.Panel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test: mutating a real widget inside a running
 * {@link Application} must propagate {@code invalidate()} up to the root and
 * mark the application paint-dirty ({@code requestRepaint()}).
 *
 * <p>Requires GLFW (skipped in headless environments).</p>
 */
public class InvalidateRepaintIT {

    private Application app;

    @BeforeEach
    public void setUp() {
        app = new Application();
        boolean initialized = app.init("Invalidate IT", 800, 600, true);
        Assumptions.assumeTrue(initialized, "GLFW not available (headless environment); skipping test");
    }

    @AfterEach
    public void tearDown() {
        Component.setGlobalRepaintRequester(null);
        if (app != null) {
            app.destroy();
        }
    }

    @Test
    public void testSetTextMarksApplicationPaintDirty() {
        Panel root = app.getRootPanel();
        Label label = new Label(20, 20, 300, 30, "initial");

        // Reset counters after construction/add so the assertions below only
        // observe invalidations caused by the explicit mutation.
        label.resetInvalidateCount();
        root.resetInvalidateCount();

        root.add(label);
        label.resetInvalidateCount();
        root.resetInvalidateCount();

        assertTrue(app.isPaintDirty(), "application should be paint-dirty after init");

        label.setText("updated");

        assertEquals(1, label.getInvalidateCount(),
                "setText() must call invalidate() on the component");
        assertEquals(1, root.getInvalidateCount(),
                "invalidate() must propagate from child to parent panel");
        assertTrue(app.isPaintDirty(),
                "invalidation reaching the root must keep the app paint-dirty");
    }

    @Test
    public void testNestedComponentMutationReachesApplication() {
        Panel root = app.getRootPanel();
        Panel nested = new Panel(10, 10, 400, 300);
        Label deepLabel = new Label(5, 5, 200, 20, "deep");
        nested.add(deepLabel);
        root.add(nested);

        nested.resetInvalidateCount();
        root.resetInvalidateCount();
        deepLabel.resetInvalidateCount();

        deepLabel.setVisible(false);

        assertEquals(1, nested.getInvalidateCount(),
                "setVisible() on a child must invalidate its direct parent");
        assertEquals(1, root.getInvalidateCount(),
                "setVisible() on a grandchild must propagate all the way to the root panel");
        assertTrue(app.isPaintDirty());
    }
}
