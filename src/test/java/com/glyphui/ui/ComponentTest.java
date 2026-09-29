package com.glyphui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Component base class.
 * Uses TestComponent to avoid requiring Skija native initialization.
 */
public class ComponentTest {

    @Test
    public void testDefaultStateAfterConstruction() {
        TestComponent comp = new TestComponent(5, 7, 120, 40);

        assertEquals(5f, comp.getX());
        assertEquals(7f, comp.getY());
        assertEquals(120f, comp.getWidth());
        assertEquals(40f, comp.getHeight());
        assertTrue(comp.isVisible(), "Components should be visible by default");
        assertTrue(comp.isEnabled(), "Components should be enabled by default");
        assertNull(comp.getParent(), "Components should have no parent by default");
        assertEquals(ComponentState.IDLE, comp.getState(), "Initial state should be IDLE");
        assertNotNull(comp.getId(), "Component should have a generated ID");
    }

    @Test
    public void testPositionAndSizeSetters() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);

        comp.setX(3.5f);
        comp.setY(-2.0f);
        comp.setWidth(64.25f);
        comp.setHeight(32.75f);

        assertEquals(3.5f, comp.getX());
        assertEquals(-2.0f, comp.getY());
        assertEquals(64.25f, comp.getWidth());
        assertEquals(32.75f, comp.getHeight());
    }

    @Test
    public void testVisibilityToggle() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);

        comp.setVisible(false);
        assertFalse(comp.isVisible());

        comp.setVisible(true);
        assertTrue(comp.isVisible());
    }

    @Test
    public void testEnabledToggle() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);

        comp.setEnabled(false);
        assertFalse(comp.isEnabled());

        comp.setEnabled(true);
        assertTrue(comp.isEnabled());
    }

    @Test
    public void testParentSetAndGet() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);
        Panel panel = new Panel(0, 0, 100, 100);

        comp.setParent(panel);
        assertSame(panel, comp.getParent());

        comp.setParent(null);
        assertNull(comp.getParent());
    }

    @Test
    public void testIdSetterOverridesGeneratedId() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);

        comp.setId("my_button");
        assertEquals("my_button", comp.getId());
    }

    @Test
    public void testStateSetter() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);

        for (ComponentState state : ComponentState.values()) {
            comp.setState(state);
            assertEquals(state, comp.getState());
        }
    }

    @Test
    public void testContainsInsideAndCorners() {
        // Component at (10, 10) with size 100x40 -> covers x:[10,110], y:[10,50]
        TestComponent comp = new TestComponent(10, 10, 100, 40);

        assertTrue(comp.contains(10, 10), "Top-left corner is inclusive");
        assertTrue(comp.contains(110, 50), "Bottom-right corner is inclusive");
        assertTrue(comp.contains(60, 30), "Center point is inside");
    }

    @Test
    public void testContainsOutsidePoints() {
        TestComponent comp = new TestComponent(10, 10, 100, 40);

        assertFalse(comp.contains(9, 30), "Just left of bounds");
        assertFalse(comp.contains(111, 30), "Just right of bounds");
        assertFalse(comp.contains(60, 9), "Just above bounds");
        assertFalse(comp.contains(60, 51), "Just below bounds");
        assertFalse(comp.contains(0, 0), "Far outside");
    }

    @Test
    public void testGetPreferredHeightDefaultsToHeight() {
        TestComponent comp = new TestComponent(0, 0, 50, 22);

        assertEquals(22f, comp.getPreferredHeight(),
                "Default preferred height should equal the component height");
    }

    @Test
    public void testDisposeIsNoOpByDefault() {
        TestComponent comp = new TestComponent(0, 0, 10, 10);

        assertDoesNotThrow(comp::dispose, "Default dispose should not throw");
    }
}
