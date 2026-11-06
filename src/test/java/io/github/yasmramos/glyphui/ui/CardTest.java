package io.github.yasmramos.glyphui.ui;

import io.github.yasmramos.glyphui.TestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link Card}: title handling, header offset and surface
 * colors. No Skija native code is touched because the paints are created
 * lazily on the first render.
 */
class CardTest extends TestBase {

    @Test
    void defaultsToNoTitle() {
        Card card = new Card();

        assertEquals("", card.getTitle());
    }

    @Test
    void nullTitleIsNormalisedToEmpty() {
        Card card = new Card(null);

        assertEquals("", card.getTitle());
    }

    @Test
    void titleDrivesTheHeaderHeight() {
        Card untitled = new Card();
        Card titled = new Card("Details");

        assertTrue(titled.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        .getHeight()
                > untitled.measure(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        .getHeight());
    }

    @Test
    void contentIsOffsetBelowTheTitleStrip() {
        Card card = new Card("Details");
        card.setWidth(200.0f);
        card.setHeight(120.0f);
        Label content = new Label("body");
        card.add(content);

        card.doLayout();

        assertTrue(content.getY() >= Card.CONTENT_PADDING + Card.TITLE_GAP,
                "content must sit below the title strip");
    }

    @Test
    void contentOffsetDoesNotAccumulateAcrossLayouts() {
        Card card = new Card("Details");
        card.setWidth(200.0f);
        card.setHeight(120.0f);
        Label content = new Label("body");
        card.add(content);

        card.doLayout();
        float first = content.getY();
        card.doLayout();
        card.doLayout();

        assertEquals(first, content.getY(), 1e-3f);
    }

    @Test
    void contentIsOffsetByTheContentPaddingWithoutATitle() {
        Card card = new Card();
        card.setWidth(200.0f);
        card.setHeight(120.0f);
        Label content = new Label("body");
        card.add(content);

        card.doLayout();

        assertEquals(Card.CONTENT_PADDING, content.getY(), 1e-3f);
    }

    @Test
    void titlePropertyKeepsTitleInSync() {
        Card card = new Card("a");

        card.titleProperty().set("b");

        assertEquals("b", card.getTitle());
    }

    @Test
    void usesTheInputSurfaceColorByDefault() {
        Card card = new Card();

        assertEquals(card.getTheme().getInputBackgroundColor(),
                card.getBackgroundColor());
    }

    @Test
    void anExplicitBackgroundOverridesTheTheme() {
        Card card = new Card();
        card.setBackgroundColor(0xFF204060);

        assertEquals(0xFF204060, card.getBackgroundColor());
    }

    @Test
    void reportsItsAccessibleRole() {
        Card card = new Card("x");

        assertEquals(AccessibleRole.CARD, card.getAccessibleRole());
    }

    @Test
    void accessibleNameFallsBackToTheTitle() {
        Card card = new Card("Summary");

        assertEquals("Summary", card.getAccessibleName());
    }

    @Test
    void childrenAreReachableThroughTheTree() {
        Card card = new Card("x");
        Label content = new Label("body");
        card.add(content);

        assertNotNull(card.getChildren());
        assertEquals(1, card.getChildren().size());
        assertEquals(content, card.getChildren().get(0));
    }
}
