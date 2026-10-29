package io.github.yasmramos.glyphui.layout;

import io.github.yasmramos.glyphui.style.Style;
import io.github.yasmramos.glyphui.style.StyleProperty;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Panel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link AbsoluteLayout}, the supported replacement for Swing's
 * {@code setLayout(null)} idiom (Swing/AWT lesson #4).
 */
class AbsoluteLayoutTest {

    /** A panel with an explicit size and the given layout installed. */
    private static Panel container(float width, float height, LayoutManager layout) {
        Panel panel = new Panel(0, 0, width, height);
        panel.setComputedStyle(Style.builder().build());
        panel.setLayoutManager(layout);
        return panel;
    }

    /** A button whose measured size is fixed through inline style. */
    private static Button sizedButton(String text, float w, float h) {
        Button b = new Button(0, 0, w, h);
        b.setText(text);
        b.setComputedStyle(Style.builder()
                .length(StyleProperty.WIDTH, w)
                .length(StyleProperty.HEIGHT, h)
                .build());
        return b;
    }

    @Test
    void layoutKeepsManualPositionsVerbatim() {
        Panel panel = container(400, 300, new AbsoluteLayout());

        Button token = sizedButton("T", 64, 24);
        token.setX(120);
        token.setY(40);
        panel.add(token);

        panel.doLayout();

        assertEquals(120f, token.getX(), 0.001f, "x must be honored verbatim");
        assertEquals(40f, token.getY(), 0.001f, "y must be honored verbatim");
        assertEquals(64f, token.getWidth(), 0.001f, "width must be honored verbatim");
        assertEquals(24f, token.getHeight(), 0.001f, "height must be honored verbatim");
    }

    @Test
    void switchingFromAbsoluteToFlexRepositionsChildren() {
        Panel panel = container(300, 100, new AbsoluteLayout());

        Button first = sizedButton("A", 50, 20);
        first.setX(200);
        first.setY(200);
        panel.add(first);

        Button second = sizedButton("B", 50, 20);
        second.setX(250);
        second.setY(250);
        panel.add(second);

        // Stale manual positions must not survive a switch to a real layout:
        // FlexLayout (default row direction, no gap/padding) re-lays
        // children from x=0.
        FlexLayout flex = new FlexLayout();
        panel.setLayoutManager(flex);
        flex.layout(panel);

        assertEquals(0f, first.getX(), 0.001f,
                "first child must be repositioned by flex layout");
        // FlexLayout's default gap (10px) separates the second child from
        // the first one's right edge at x=50.
        assertEquals(60f, second.getX(), 0.001f,
                "second child must follow the first");
    }
}
