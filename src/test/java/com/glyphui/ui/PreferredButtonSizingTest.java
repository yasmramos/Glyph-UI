package com.glyphui.ui;

import com.glyphui.graphics.RasterCanvasTestFactory;
import io.github.humbleui.skija.Font;
import io.github.humbleui.skija.FontStyle;
import io.github.humbleui.skija.Typeface;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Unit tests for the Button widget: constructors without explicit bounds and
 * preferred-size computation (text measurement plus padding).
 */
public class PreferredButtonSizingTest {

    /** Reference font matching Button's internal default (size 16). */
    private static Font referenceFont;

    @BeforeAll
    static void checkSkijaAvailable() {
        assumeTrue(RasterCanvasTestFactory.isAvailable(),
                "Skija native library not available on this host");
        referenceFont = new Font(Typeface.makeFromName(null, FontStyle.NORMAL), 16.0f);
    }

    @AfterAll
    static void closeReferenceFont() {
        if (referenceFont != null) {
            referenceFont.close();
            referenceFont = null;
        }
    }

    @Test
    public void testBoundsConstructorMarksSizeExplicit() {
        Button button = new Button(10, 20, 200, 60, "Click me");
        assertTrue(button.isSizeExplicitlySet());
        assertEquals(200f, button.getPreferredWidth());
        assertEquals(60f, button.getPreferredHeight());
    }

    @Test
    public void testTextOnlyConstructorLeavesSizeUnset() {
        Button button = new Button("Click here!");
        assertFalse(button.isSizeExplicitlySet());
        assertEquals(0f, button.getWidth());
        assertEquals("Click here!", button.getText());
    }

    @Test
    public void testNoArgConstructorDefaults() {
        Button button = new Button();
        assertFalse(button.isSizeExplicitlySet());
        assertEquals("", button.getText());
    }

    @Test
    public void testPreferredWidthIsTextPlusHorizontalPadding() {
        String text = "Click here!";
        Button button = new Button(text);
        float measuredTextWidth = referenceFont.measureTextWidth(text);
        assertEquals(measuredTextWidth + Button.HORIZONTAL_PADDING * 2,
                button.getPreferredWidth(), 0.01f);
    }

    @Test
    public void testPreferredHeightIncludesVerticalPadding() {
        Button button = new Button("Click here!");
        float h = button.getPreferredHeight();
        assertTrue(h > Button.VERTICAL_PADDING,
                "Preferred height must include vertical padding, got " + h);
    }

    @Test
    public void testLongerTextGrowsPreferredWidth() {
        Button shortBtn = new Button("Hi");
        Button longBtn = new Button("A much longer button label");
        assertTrue(longBtn.getPreferredWidth() > shortBtn.getPreferredWidth());
    }

    @Test
    public void testSetTextUpdatesPreferredWidth() {
        Button button = new Button("a");
        float before = button.getPreferredWidth();
        button.setText("aaaaaaaaaaaaaaaaaaaaaa");
        assertTrue(button.getPreferredWidth() > before,
                "Preferred width must follow the current text");
    }
}
