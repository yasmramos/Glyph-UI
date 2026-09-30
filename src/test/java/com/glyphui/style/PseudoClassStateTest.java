package com.glyphui.style;

import com.glyphui.core.FocusManager;
import com.glyphui.ui.Button;
import com.glyphui.ui.Component;
import com.glyphui.ui.ComponentState;
import com.glyphui.ui.Panel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that pseudo-class rules ({@code :hover}, {@code :focus},
 * {@code :disabled}, {@code :active}) are re-evaluated by the cascade when
 * the component state changes — which is exactly what {@code Application}
 * does once per frame through its {@link StyleEngine}.
 */
class PseudoClassStateTest {

    private StyleSheet sheet;
    private Panel root;
    private Button button;
    private StyleEngine engine;
    private FocusManager focusManager;

    @BeforeEach
    void setUp() {
        sheet = StyleSheet.parse("""
                button { color: #00000011; }
                button:hover { color: #00000022; }
                button:focus { color: #00000033; }
                button:active { color: #00000044; }
                button:disabled { color: #00000055; }
                """);
        root = new Panel(0, 0, 400, 300);
        button = new Button(0, 0, 100, 30, "click");
        root.add(button);
        engine = new StyleEngine(sheet);
        focusManager = new FocusManager(null);
        focusManager.setRoot(root);
        FocusManager.setGlobalFocusManager(focusManager);
        engine.apply(root);
    }

    @AfterEach
    void tearDown() {
        FocusManager.setGlobalFocusManager(null);
    }

    private int computedColor() {
        engine.apply(root);
        return button.getComputedStyle().getInt(StyleProperty.COLOR, 0);
    }

    @Test
    void baseStateUsesTypeRuleOnly() {
        button.setState(ComponentState.IDLE);
        assertEquals(0xFF000011, computedColor());
    }

    @Test
    void hoverStatePicksHoverRule() {
        button.setState(ComponentState.HOVER);
        assertEquals(0xFF000022, computedColor(), ":hover must win over the type rule");
    }

    @Test
    void focusStatePicksFocusRule() {
        button.setFocusable(true);
        button.requestFocus();
        assertTrue(button.isFocused());
        assertEquals(0xFF000033, computedColor(), ":focus matches via isFocused()");
    }

    @Test
    void activeStatePicksActiveRule() {
        button.setState(ComponentState.PRESSED);
        assertEquals(0xFF000044, computedColor(), ":active maps to ComponentState.PRESSED");
    }

    @Test
    void disabledBeatsHoverBecauseItComesLaterAtEqualSpecificity() {
        button.setEnabled(false);
        button.setState(ComponentState.HOVER);
        // Both :hover and :disabled match; :disabled has the same specificity
        // but appears later in the sheet, so it wins (declaration order).
        assertEquals(0xFF000055, computedColor());
    }

    @Test
    void disablingAFocusedButtonClearsFocusAndRecomputesStyle() {
        button.setFocusable(true);
        button.requestFocus();
        assertEquals(0xFF000033, computedColor());

        button.setEnabled(false);
        assertFalse(button.isFocused(), "FocusManager must drop focus from disabled widgets");
        assertEquals(0xFF000055, computedColor());
    }

    @Test
    void stateChangeWithoutReapplyKeepsOldStyleUntilNextPass() {
        int before = button.getComputedStyle().getInt(StyleProperty.COLOR, 0);
        button.setState(ComponentState.HOVER);
        assertEquals(before, button.getComputedStyle().getInt(StyleProperty.COLOR, 0),
                "styles are only refreshed by an explicit cascade pass");
        assertNotEquals(before, computedColor());
    }

    @Test
    void pseudoSelectorRejectsUnknownName() {
        assertThrows(IllegalArgumentException.class, () -> Selector.parse("button:checked"));
    }

    @Test
    void componentMatchesPseudoReflectsRuntimeFlags() {
        Component c = button;
        c.setState(ComponentState.IDLE);
        assertFalse(c.matchesPseudo(Selector.PseudoClass.HOVER));
        c.setState(ComponentState.HOVER);
        assertTrue(c.matchesPseudo(Selector.PseudoClass.HOVER));
        assertFalse(c.matchesPseudo(Selector.PseudoClass.DISABLED));
        c.setEnabled(false);
        assertTrue(c.matchesPseudo(Selector.PseudoClass.DISABLED));
    }
}
