package com.glyphui.ui;

import com.glyphui.graphics.Dimension;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the measurement system: {@link Component#measure(float, float)}
 * default behavior (preferred size clamped to constraints) and constraint
 * consumption by {@code FlowLayout}.
 */
public class MeasureTest {

    @Test
    public void testDefaultMeasureReturnsPreferredSize() {
        TestComponent comp = new TestComponent(0, 0, 50, 30);

        Dimension size = comp.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);

        assertEquals(50.0f, size.getWidth(), 0.001f);
        assertEquals(30.0f, size.getHeight(), 0.001f);
    }

    @Test
    public void testDefaultMeasureClampsToMaxConstraints() {
        TestComponent comp = new TestComponent(0, 0, 50, 30);

        Dimension clamped = comp.measure(20, 10);
        assertEquals(20.0f, clamped.getWidth(), 0.001f);
        assertEquals(10.0f, clamped.getHeight(), 0.001f);

        // Constraints larger than the preferred size do not grow it.
        Dimension roomy = comp.measure(500, 500);
        assertEquals(50.0f, roomy.getWidth(), 0.001f);
        assertEquals(30.0f, roomy.getHeight(), 0.001f);
    }

    @Test
    public void testCustomMeasureOverrideRespectsConstraints() {
        // A component that adapts its height to the width given (word wrap).
        Component wrapping = new TestComponent(0, 0, 100, 20) {
            @Override
            public Dimension measure(float maxWidth, float maxHeight) {
                float w = Math.min(100.0f, Math.max(0.0f, maxWidth));
                int lines = (int) Math.ceil(100.0 / Math.max(1.0, w));
                return new Dimension(w, lines * 20.0f);
            }
        };

        Dimension half = wrapping.measure(50, Float.POSITIVE_INFINITY);
        assertEquals(50.0f, half.getWidth(), 0.001f);
        assertEquals(40.0f, half.getHeight(), 0.001f);

        Dimension full = wrapping.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY);
        assertEquals(100.0f, full.getWidth(), 0.001f);
        assertEquals(20.0f, full.getHeight(), 0.001f);
    }

    @Test
    public void testFlowLayoutConsumesMeasureForWrappingComponents() {
        Panel panel = new Panel(0, 0, 100, 200);
        panel.setLayoutManager(new com.glyphui.layout.FlowLayout(5, 10));

        Component first = new TestComponent(0, 0, 50, 30);
        // Second component reports a preferred width wider than the row can
        // hold once the first one is placed — it must wrap AND be re-measured
        // against the fresh row width.
        Component second = new TestComponent(0, 0, 90, 30) {
            @Override
            public Dimension measure(float maxWidth, float maxHeight) {
                float w = Math.min(90.0f, Math.max(0.0f, maxWidth));
                return new Dimension(w, 30.0f);
            }
        };

        panel.add(first);
        panel.add(second);
        panel.doLayout();

        // First at (10, 10), unchanged preferred size.
        assertEquals(10.0f, first.getX(), 0.001f);
        assertEquals(10.0f, first.getY(), 0.001f);

        // Second wraps to a new row at y = 10 + 30 + 5 = 45 and fills the
        // available width (100 - 2*padding = 80) after being re-measured.
        assertEquals(10.0f, second.getX(), 0.001f);
        assertEquals(45.0f, second.getY(), 0.001f);
        assertTrue(second.getWidth() <= 80.0f + 0.001f,
                "Wrapped component must respect the row constraint");
    }
}
