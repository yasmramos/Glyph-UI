package io.github.yasmramos.glyphui.style;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.ui.Button;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.ComponentState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block-1 regression tests (item 1.4): the per-frame CSS cascade is gated
 * behind a global style-dirty latch instead of running unconditionally on
 * every frame. These tests pin down the contract between {@code Component}
 * mutators and {@code Application.frameStep()}:
 *
 * <ul>
 *   <li>{@link Component#consumeStyleDirty()} atomically reads and clears
 *       the latch, so a cascade runs at most once per dirty episode;</li>
 *   <li>every style-affecting mutator (state, visibility, enabled flag,
 *       style classes, id, inline style) must set the latch;</li>
 *   <li>no-op mutations (same value) must not dirty the cascade.</li>
 * </ul>
 */
class StyleDirtyFlagTest extends TestBase {

    private Button button;

    /**
     * Drains the package-private test hook on Component via reflection so the
     * latch starts from a known state. The method is intentionally not public:
     * it must never leak into the frozen public API surface.
     */
    private static void resetLatch() {
        try {
            var m = Component.class.getDeclaredMethod("resetStyleDirtyForTests");
            m.setAccessible(true);
            m.invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Component.resetStyleDirtyForTests missing", e);
        }
    }

    @BeforeEach
    void setUp() {
        // Drain any latch left over by unrelated setup so each test starts clean.
        resetLatch();
        button = new Button("Cascade");
        // Constructors can touch observable properties; start from clean.
        resetLatch();
    }

    @AfterEach
    void tearDown() {
        resetLatch();
    }

    @Test
    @DisplayName("consumeStyleDirty is atomic: first call sees it, second does not")
    void consumeClearsTheLatch() {
        Component.invalidateStyle();
        assertTrue(Component.consumeStyleDirty(), "latch must be visible to the first consumer");
        assertFalse(Component.consumeStyleDirty(), "the latch must be consumed exactly once");
    }

    @Test
    @DisplayName("setState dirties the cascade because pseudo-class rules depend on it")
    void stateChangeInvalidatesStyle() {
        button.setState(ComponentState.HOVER);
        assertTrue(Component.consumeStyleDirty(), ":hover rules can only change via setState");
        // Setting the same state again is a no-op and must stay quiet.
        button.setState(ComponentState.HOVER);
        assertFalse(Component.consumeStyleDirty(), "identical state assignment must not re-dirty");
    }

    @Test
    @DisplayName("setEnabled dirties the cascade (:disabled rules)")
    void enabledChangeInvalidatesStyle() {
        button.setEnabled(false);
        assertTrue(Component.consumeStyleDirty(), ":disabled pseudo-class depends on the enabled flag");
        button.setEnabled(false);
        assertFalse(Component.consumeStyleDirty(), "disabling twice must not re-dirty the cascade");
    }

    @Test
    @DisplayName("setVisible dirties the cascade (focus loss changes :focus matching)")
    void visibilityChangeInvalidatesStyle() {
        button.setVisible(false);
        assertTrue(Component.consumeStyleDirty(), "visibility toggles participate in selector matching");
        button.setVisible(false);
        assertFalse(Component.consumeStyleDirty(), "redundant visibility writes must stay quiet");
    }

    @Test
    @DisplayName("addStyleClass / removeStyleClass dirty the cascade")
    void classListMutationsInvalidateStyle() {
        button.addStyleClass("primary");
        assertTrue(Component.consumeStyleDirty(), ".primary rules require a fresh cascade");

        button.removeStyleClass("primary");
        assertTrue(Component.consumeStyleDirty(), "removing a class also requires a fresh cascade");

        // Removing an absent class is a no-op and must stay quiet.
        button.removeStyleClass("does-not-exist");
        assertFalse(Component.consumeStyleDirty(), "no-op class removal must not dirty the cascade");

        // Adding an already-present class is likewise a no-op.
        button.addStyleClass("ghost");
        Component.consumeStyleDirty();
        button.addStyleClass("ghost");
        assertFalse(Component.consumeStyleDirty(), "duplicate class add must not dirty the cascade");
    }

    @Test
    @DisplayName("setId dirties the cascade (#id rules)")
    void idChangeInvalidatesStyle() {
        button.setId("submit");
        assertTrue(Component.consumeStyleDirty(), "#submit rules require a fresh cascade");
    }

    @Test
    @DisplayName("setInlineStyle dirties the cascade (inline declarations win)")
    void inlineStyleInvalidatesStyle() {
        Style inline = Style.builder()
                .set(StyleProperty.BACKGROUND, 0xFFFF0000)
                .build();
        button.setInlineStyle(inline);
        assertTrue(Component.consumeStyleDirty(), "inline styles are part of the cascade output");
    }

    @Test
    @DisplayName("setStyleTag dirties the cascade (type selectors key off the tag)")
    void styleTagChangeInvalidatesStyle() {
        button.setStyleTag("link");
        assertTrue(Component.consumeStyleDirty(), "type selectors match styleTag(): changing it re-cascades");
    }

    @Test
    @DisplayName("Latch aggregates many mutations into a single cascade pass")
    void manyMutationsOnePass() {
        for (int i = 0; i < 50; i++) {
            button.addStyleClass("c" + i);
        }
        // Exactly one consume returns true regardless of how many setters fired.
        assertTrue(Component.consumeStyleDirty());
        assertFalse(Component.consumeStyleDirty());
    }
}
