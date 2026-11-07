package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import io.github.yasmramos.glyphui.core.FocusManager;
import io.github.yasmramos.glyphui.events.GlyphKeys;
import io.github.yasmramos.glyphui.events.KeyEvent;
import io.github.yasmramos.glyphui.events.KeyEventType;
import io.github.yasmramos.glyphui.events.KeyModifier;
import io.github.yasmramos.glyphui.events.MouseButton;
import io.github.yasmramos.glyphui.events.MouseEvent;
import io.github.yasmramos.glyphui.events.MouseEventType;
import io.github.yasmramos.glyphui.graphics.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Dialog}: action management, title/message
 * properties, escape and scrim dismissal, and the close handler.
 */
class DialogTest extends TestBase {

    @BeforeEach
    void setUp() {
        FocusManager.setGlobalFocusManager(new FocusManager(() -> { }));
    }

    private static KeyEvent key(int keyCode) {
        return new KeyEvent(KeyEventType.PRESS, keyCode, ' ',
                EnumSet.noneOf(KeyModifier.class));
    }

    private static MouseEvent mouse(MouseEventType type, float x, float y) {
        return new MouseEvent(type, (int) x, (int) y, MouseButton.LEFT, 1);
    }

    /** A shown dialog sized to a small surface, so hit-testing is predictable. */
    private static Dialog shownDialog() {
        Dialog dialog = new Dialog("Title", "Message");
        dialog.show();
        dialog.setWidth(300.0f);
        dialog.setHeight(200.0f);
        return dialog;
    }

    @Test
    void startsHiddenWithEmptyContent() {
        Dialog dialog = new Dialog();

        assertFalse(dialog.isOpen());
        assertEquals("", dialog.getTitle());
        assertEquals("", dialog.getMessage());
        assertTrue(dialog.getActions().isEmpty());
    }

    @Test
    void nullTitleAndMessageBecomeEmpty() {
        Dialog dialog = new Dialog(null, null);

        assertEquals("", dialog.getTitle());
        assertEquals("", dialog.getMessage());
    }

    @Test
    void showMakesItVisibleAndCloseHidesIt() {
        Dialog dialog = new Dialog("Title");

        dialog.show();
        assertTrue(dialog.isOpen());

        dialog.close();
        assertFalse(dialog.isOpen());
    }

    @Test
    void titleAndMessagePropertiesRoundTrip() {
        Dialog dialog = new Dialog();
        Property<String> title = dialog.titleProperty();
        Property<String> message = dialog.messageProperty();

        dialog.setTitle("Heads up");
        dialog.setMessage("Something happened");

        assertEquals("Heads up", title.get());
        assertEquals("Something happened", message.get());
    }

    @Test
    void settingNullThroughThePropertyBecomesEmpty() {
        Dialog dialog = new Dialog("Title", "Message");

        dialog.titleProperty().set(null);
        dialog.messageProperty().set(null);

        assertEquals("", dialog.getTitle());
        assertEquals("", dialog.getMessage());
    }

    @Test
    void actionsAreTrackedInOrder() {
        Dialog dialog = new Dialog();
        dialog.addAction("Cancel");
        dialog.addAction("Delete", () -> { });

        List<Dialog.Action> actions = dialog.getActions();
        assertEquals(2, actions.size());
        assertEquals("Cancel", actions.get(0).getLabel());
        assertEquals("Delete", actions.get(1).getLabel());
        assertTrue(actions.get(1).getOnClick() != null);
    }

    @Test
    void clearActionsRemovesThemAll() {
        Dialog dialog = new Dialog();
        dialog.addAction("One");
        dialog.addAction("Two");

        dialog.clearActions();

        assertTrue(dialog.getActions().isEmpty());
    }

    @Test
    void onlyOneActionIsTheDefault() {
        Dialog dialog = new Dialog();
        dialog.addAction("One").setDefaultAction(true);
        dialog.addAction("Two");

        assertEquals(0, dialog.getDefaultActionIndex());

        dialog.setDefaultActionIndex(1);
        assertEquals(1, dialog.getDefaultActionIndex());
        assertFalse(dialog.getActions().get(0).isDefault());
    }

    @Test
    void escapeDismissesADismissibleDialog() {
        Dialog dialog = shownDialog();
        int[] closes = {0};
        dialog.setOnClose(() -> closes[0]++);

        dialog.onKeyEvent(key(GlyphKeys.ESCAPE));

        assertFalse(dialog.isOpen());
        assertEquals(1, closes[0]);
    }

    @Test
    void escapeIsIgnoredWhenNotDismissible() {
        Dialog dialog = shownDialog();
        dialog.setDismissible(false);

        dialog.onKeyEvent(key(GlyphKeys.ESCAPE));

        assertTrue(dialog.isOpen());
    }

    @Test
    void enterActivatesTheDefaultActionAndCloses() {
        Dialog dialog = shownDialog();
        int[] activations = {0};
        dialog.addAction("Cancel");
        dialog.addAction("Delete", () -> activations[0]++).setDefaultAction(true);

        dialog.onKeyEvent(key(GlyphKeys.ENTER));

        assertEquals(1, activations[0]);
        assertFalse(dialog.isOpen());
    }

    @Test
    void enterFallsBackToTheFirstActionWithoutADefault() {
        Dialog dialog = shownDialog();
        int[] activations = {0};
        dialog.addAction("Ok", () -> activations[0]++);

        dialog.onKeyEvent(key(GlyphKeys.ENTER));

        assertEquals(1, activations[0]);
    }

    @Test
    void disabledDefaultActionBlocksEnter() {
        Dialog dialog = shownDialog();
        int[] activations = {0};
        dialog.addAction("Delete", () -> activations[0]++)
                .setDefaultAction(true).setEnabled(false);

        dialog.onKeyEvent(key(GlyphKeys.ENTER));

        assertEquals(0, activations[0]);
        assertTrue(dialog.isOpen());
    }

    @Test
    void clickingTheScrimDismissesADismissibleDialog() {
        Dialog dialog = shownDialog();

        // (2, 2) is well outside the centred surface.
        dialog.onMouseEvent(mouse(MouseEventType.PRESS, 2, 2));
        dialog.onMouseEvent(mouse(MouseEventType.RELEASE, 2, 2));

        assertFalse(dialog.isOpen());
    }

    @Test
    void clickingTheScrimIsIgnoredWhenNotDismissible() {
        Dialog dialog = shownDialog();
        dialog.setDismissible(false);

        dialog.onMouseEvent(mouse(MouseEventType.PRESS, 2, 2));
        dialog.onMouseEvent(mouse(MouseEventType.RELEASE, 2, 2));

        assertTrue(dialog.isOpen());
    }

    @Test
    void pointerInputIsAlwaysSwallowedSoThePageBehindStaysInert() {
        Dialog dialog = shownDialog();
        dialog.setDismissible(false);

        boolean consumed = dialog.onMouseEvent(mouse(MouseEventType.PRESS, 2, 2));

        assertTrue(consumed, "a modal dialog must swallow pointer input");
    }

    @Test
    void closeHandlerRunsOnlyOnce() {
        Dialog dialog = shownDialog();
        int[] closes = {0};
        dialog.setOnClose(() -> closes[0]++);

        dialog.close();
        dialog.close();

        assertEquals(1, closes[0]);
    }

    @Test
    void closeHandlerFailureIsSwallowed() {
        Dialog dialog = shownDialog();
        dialog.setOnClose(() -> {
            throw new IllegalStateException("boom");
        });

        dialog.close();

        assertFalse(dialog.isOpen());
    }

    @Test
    void actionHandlerFailureIsSwallowedAndStillCloses() {
        Dialog dialog = shownDialog();
        dialog.addAction("Boom", () -> {
            throw new IllegalStateException("boom");
        }).setDefaultAction(true);

        dialog.onKeyEvent(key(GlyphKeys.ENTER));

        assertFalse(dialog.isOpen());
    }

    @Test
    void disabledDialogIgnoresInput() {
        Dialog dialog = shownDialog();
        dialog.setEnabled(false);

        assertFalse(dialog.onMouseEvent(mouse(MouseEventType.PRESS, 2, 2)));
        dialog.onKeyEvent(key(GlyphKeys.ESCAPE));

        assertTrue(dialog.isOpen());
    }

    @Test
    void preferredSizeGrowsWithContent() {
        Dialog plain = new Dialog();
        Dialog rich = new Dialog("A much longer title than the default width",
                "With a message that needs room");
        rich.addAction("Cancel");
        rich.addAction("Confirm");

        assertTrue(rich.getPreferredWidth() >= Dialog.MIN_WIDTH);
        assertTrue(rich.getPreferredHeight() > plain.getPreferredHeight());
    }

    @Test
    void accessibilityDescribesTheDialog() {
        Dialog dialog = new Dialog("Delete file?");

        assertEquals(AccessibleRole.DIALOG, dialog.getAccessibleRole());
        assertTrue(dialog.getAccessibleName().contains("Delete file?"));
    }

    @Test
    void defaultActionIndexOutOfRangeClearsIt() {
        Dialog dialog = new Dialog();
        dialog.addAction("One").setDefaultAction(true);

        dialog.setDefaultActionIndex(9);

        assertEquals(-1, dialog.getDefaultActionIndex());
    }

    @Test
    void showIsIdempotent() {
        Dialog dialog = new Dialog("Title");

        dialog.show();
        dialog.show();

        assertTrue(dialog.isOpen());
    }
}
