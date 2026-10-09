package com.glyphui.core;

import com.glyphui.ui.Button;
import com.glyphui.ui.Component;
import com.glyphui.ui.Panel;
import com.glyphui.ui.TestComponent;
import com.glyphui.ui.TextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link FocusManager}: focus traversal order, Tab /
 * Shift+Tab cycling, and automatic focus clearing when a component is
 * hidden, disabled or made unfocusable.
 */
public class FocusManagerTest {

    /** Repaint counter shared with the manager built in setUp. */
    private final int[] repaintCounter = {0};

    private Panel root;
    private FocusManager manager;
    private Button first;
    private TestComponent notFocusable;
    private TextField second;

    @BeforeEach
    public void buildTree() {
        manager = new FocusManager(() -> repaintCounter[0]++);
        FocusManager.setGlobalFocusManager(manager);

        root = new Panel(0, 0, 400, 200);
        first = new Button(10, 10, 100, 30);
        first.setText("First");
        notFocusable = new TestComponent(10, 50, 100, 30);
        notFocusable.setFocusable(false);
        second = new TextField(10, 90, 200, 30);

        root.add(first);
        root.add(notFocusable);
        root.add(second);
        manager.setRoot(root);
    }

    @AfterEach
    public void clearGlobal() {
        FocusManager.setGlobalFocusManager(null);
    }

    @Test
    public void testRequestFocusAndFocusedGetter() {
        assertTrue(manager.requestFocus(first));
        assertSame(first, manager.getFocused());
        assertTrue(first.isFocused());
    }

    @Test
    public void testFocusNextSkipsUnfocusableComponents() {
        assertTrue(manager.requestFocus(first));

        // Tab from the first button must skip the non-focusable component
        // and land on the text field (tree order).
        assertTrue(manager.focusNext());
        assertSame(second, manager.getFocused());

        // Next tab wraps around to the first focusable component.
        assertTrue(manager.focusNext());
        assertSame(first, manager.getFocused());
    }

    @Test
    public void testFocusPreviousCyclesBackwards() {
        assertTrue(manager.requestFocus(second));

        assertTrue(manager.focusPrevious());
        assertSame(first, manager.getFocused());

        // Wrapping backwards from the first goes to the last focusable.
        assertTrue(manager.focusPrevious());
        assertSame(second, manager.getFocused());
    }

    @Test
    public void testHidingFocusedComponentClearsFocus() {
        manager.requestFocus(second);
        assertSame(second, manager.getFocused());

        second.setVisible(false);

        assertNull(manager.getFocused(), "Hidden components lose focus");
        assertFalse(second.isFocused());
    }

    @Test
    public void testDisablingFocusedComponentClearsFocus() {
        manager.requestFocus(first);

        first.setEnabled(false);

        assertNull(manager.getFocused(), "Disabled components lose focus");
        assertFalse(first.isFocused());
    }

    @Test
    public void testMakingFocusedComponentUnfocusableClearsFocus() {
        manager.requestFocus(first);

        first.setFocusable(false);

        assertNull(manager.getFocused());
    }

    @Test
    public void testFocusCallbacksFireOnGainAndLoss() {
        final int[] gained = {0};
        final int[] lost = {0};
        Component listener = new TestComponent(0, 0, 50, 30) {
            @Override
            public void onFocusGained() {
                gained[0]++;
            }

            @Override
            public void onFocusLost() {
                lost[0]++;
            }
        };
        root.add(listener);

        manager.requestFocus(listener);
        assertEquals(1, gained[0]);
        assertEquals(0, lost[0]);

        manager.requestFocus(first);
        assertEquals(1, lost[0]);
        assertEquals(listener.isFocused(), false);
    }

    @Test
    public void testRepaintCallbackFiresOnFocusChange() {
        int before = repaintCounter[0];
        manager.requestFocus(first);
        assertTrue(repaintCounter[0] > before,
                "Focus changes must request a repaint");
    }
}
