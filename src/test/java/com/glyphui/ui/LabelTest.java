package com.glyphui.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the Label widget, focusing on constructors without explicit
 * bounds and preferred-size computation.
 */
public class LabelTest {

    @Test
    public void testBoundsConstructorMarksSizeExplicit() {
        Label label = new Label(10, 20, 120, 40, "Hello");
        assertTrue(label.isSizeExplicitlySet());
        assertEquals(120f, label.getWidth());
        assertEquals(40f, label.getHeight());
        // Explicit sizes must be respected by the preferred-size API.
        assertEquals(120f, label.getPreferredWidth());
        assertEquals(40f, label.getPreferredHeight());
    }

    @Test
    public void testTextOnlyConstructorLeavesSizeUnset() {
        Label label = new Label("Hello world");
        assertFalse(label.isSizeExplicitlySet());
        assertEquals(0f, label.getWidth());
        assertEquals(0f, label.getHeight());
        assertEquals("Hello world", label.getText());
    }

    @Test
    public void testNoArgConstructorDefaults() {
        Label label = new Label();
        assertFalse(label.isSizeExplicitlySet());
        assertEquals("", label.getText());
    }

    @Test
    public void testPreferredSizeMeasuresText() {
        // Requires Skija native libraries (font measurement).
        Label label = new Label("Some sample text");
        float w = label.getPreferredWidth();
        float h = label.getPreferredHeight();
        assertTrue(w > 0f, "Measured width should be positive, got " + w);
        assertTrue(h > 0f, "Measured height should be positive, got " + h);

        Label longer = new Label("Some sample text that is much longer than before");
        assertTrue(longer.getPreferredWidth() > w,
                "Longer text must produce a wider preferred width");
    }

    @Test
    public void testEmptyTextPreferredWidthIsNotNegative() {
        Label label = new Label("");
        assertTrue(label.getPreferredWidth() >= 0f);
    }

    @Test
    public void testSetTextKeepsPreferredSizingDynamic() {
        Label label = new Label("a");
        float shortWidth = label.getPreferredWidth();
        label.setText("aaaaaaaaaaaaaaaaaaaa");
        assertTrue(label.getPreferredWidth() > shortWidth,
                "Preferred width must grow with the text");
    }

    @Test
    public void testExplicitSizeOverridesMeasurement() {
        Label measured = new Label("Wide text content here");
        float autoWidth = measured.getPreferredWidth();

        Label fixed = new Label(0, 0, 300, 50, "Wide text content here");
        assertEquals(300f, fixed.getPreferredWidth());
        assertEquals(50f, fixed.getPreferredHeight());
        assertNotEquals(autoWidth, fixed.getPreferredWidth(),
                "Explicit bounds must not be replaced by measured size");
    }
}
