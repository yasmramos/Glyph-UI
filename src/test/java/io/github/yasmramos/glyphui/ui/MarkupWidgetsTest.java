package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for the widgets introduced with the markup vocabulary.
 */
class MarkupWidgetsTest {

    @Test
    void checkboxTogglesAndNotifiesOnlyOnRealChanges() {
        CheckBox checkBox = new CheckBox("Enabled");
        List<Boolean> changes = new ArrayList<>();
        checkBox.setOnChange(changes::add);

        assertFalse(checkBox.isChecked());
        checkBox.setChecked(true);
        checkBox.setChecked(true);
        checkBox.toggle();

        assertEquals(List.of(true, false), changes);
        assertEquals("Enabled", checkBox.getText());
        assertEquals(AccessibleRole.CHECKBOX, checkBox.getAccessibleRole());
    }

    @Test
    void separatorReportsItsOrientationAndNeverTakesFocus() {
        Separator separator = new Separator();

        assertEquals(Separator.Orientation.HORIZONTAL, separator.getOrientation());
        assertEquals(Separator.THICKNESS, separator.getPreferredHeight());
        assertFalse(separator.isFocusable());
        assertFalse(separator.onMouseEvent(new MouseEvent(
                MouseEventType.PRESS, 0, 0, MouseButton.LEFT, 1)));

        separator.setOrientation(Separator.Orientation.VERTICAL);
        assertEquals(Separator.THICKNESS, separator.getPreferredWidth());
        assertEquals(AccessibleRole.SEPARATOR, separator.getAccessibleRole());
    }

    @Test
    void scrollPaneClampsAndScrollsWithTheKeyboard() {
        ScrollPane pane = new ScrollPane();
        pane.setWidth(100);
        pane.setHeight(100);
        Label bottom = new Label();
        bottom.setY(200);
        bottom.setHeight(50);
        pane.add(bottom);

        assertEquals(150, pane.getMaxScrollY());
        pane.setScrollY(1_000);
        assertEquals(150, pane.getScrollY());

        pane.onKeyEvent(new KeyEvent(KeyEventType.PRESS, GlyphKeys.UP, '\0', null));
        assertEquals(126, pane.getScrollY());

        pane.onKeyEvent(new KeyEvent(KeyEventType.PRESS, GlyphKeys.PAGE_UP, '\0', null));
        assertEquals(26, pane.getScrollY());

        pane.onKeyEvent(new KeyEvent(KeyEventType.PRESS, GlyphKeys.DOWN, '\0', null));
        assertEquals(50, pane.getScrollY());
        assertEquals(AccessibleRole.SCROLL_PANE, pane.getAccessibleRole());
    }

    @Test
    void scrollPaneHitTestsChildrenInScrolledCoordinates() {
        ScrollPane pane = new ScrollPane();
        pane.setWidth(100);
        pane.setHeight(100);

        Button button = new Button(0, 0, 50, 50, "top");
        int[] clicks = {0};
        button.setOnClick(() -> clicks[0]++);
        pane.add(button);

        Label spacer = new Label();
        spacer.setY(200);
        spacer.setHeight(50);
        pane.add(spacer);
        pane.setScrollY(50);

        // The button is scrolled above the viewport; a click at the top of
        // the pane must not hit it even though its unscrolled y is 0.
        pane.onMouseEvent(new MouseEvent(MouseEventType.PRESS, 10, 10, MouseButton.LEFT, 1));
        pane.onMouseEvent(new MouseEvent(MouseEventType.RELEASE, 10, 10, MouseButton.LEFT, 1));

        assertEquals(0, clicks[0]);
        assertTrue(pane.isFocusable());
    }
}
