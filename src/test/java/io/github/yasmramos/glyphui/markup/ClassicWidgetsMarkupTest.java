package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.AccessibleRole;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.GroupBox;
import io.github.yasmramos.glyphui.ui.Link;
import io.github.yasmramos.glyphui.ui.ListBox;
import io.github.yasmramos.glyphui.ui.Panel;
import io.github.yasmramos.glyphui.ui.Rating;
import io.github.yasmramos.glyphui.ui.Skeleton;
import io.github.yasmramos.glyphui.ui.SplitPane;
import io.github.yasmramos.glyphui.ui.Stepper;
import io.github.yasmramos.glyphui.ui.Toolbar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markup tests for the classic widgets added after the first vocabulary pass:
 * {@code a}, {@code rating}, {@code skeleton}, {@code stepper},
 * {@code toolbar}, {@code groupbox}, {@code listbox} and {@code splitpane}.
 */
class ClassicWidgetsMarkupTest {

    @Test
    void anchorTagBuildsALinkWithItsTarget() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <a id="docs" href="https://example.com/docs">Docs</a>
                </body>
                """);

        Link link = (Link) UiLoader.findById(root, "docs");
        assertNotNull(link, "<a> must map to Link");
        assertEquals("Docs", link.getText());
        assertEquals("https://example.com/docs", link.getTarget());
        assertEquals(AccessibleRole.LINK, link.getAccessibleRole());
    }

    @Test
    void anchorFlagsSetVisitedAndUnderline() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><a id=\"v\" href=\"x\" visited underline>Seen</a></body>");

        Link link = (Link) UiLoader.findById(root, "v");
        assertTrue(link.isVisited());
        assertTrue(link.isAlwaysUnderline());
    }

    @Test
    void ratingTagReadsItsAttributes() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><rating id=\"r\" max=\"4\" value=\"2.5\" half readonly/></body>");

        Rating rating = (Rating) UiLoader.findById(root, "r");
        assertEquals(4, rating.getMax());
        assertEquals(2.5f, rating.getValue(), 1e-6);
        assertTrue(rating.isAllowHalf());
        assertTrue(rating.isReadOnly());
    }

    @Test
    void skeletonTagReadsItsShape() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><skeleton id=\"s\" shape=\"circle\" static/></body>");

        Skeleton skeleton = (Skeleton) UiLoader.findById(root, "s");
        assertEquals(Skeleton.Shape.CIRCLE, skeleton.getShape());
        assertFalse(skeleton.isAnimated());
    }

    @Test
    void unknownSkeletonShapeWarnsAndFallsBack() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><skeleton id=\"s\" shape=\"blob\"/></body>");

        Skeleton skeleton = (Skeleton) UiLoader.findById(root, "s");
        assertEquals(Skeleton.Shape.TEXT, skeleton.getShape());
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("blob")));
    }

    @Test
    void stepperTagReadsStepsAndCurrent() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><stepper id=\"s\" steps=\"One, Two, Three\" current=\"2\"/></body>");

        Stepper stepper = (Stepper) UiLoader.findById(root, "s");
        assertEquals(3, stepper.getStepCount());
        assertEquals("Two", stepper.getStepLabel(1));
        assertEquals(1, stepper.getCurrentStep());
    }

    @Test
    void stepperStepsCanComeFromChildElements() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <stepper id="s" free>
                    <step>Cart</step>
                    <step current>Pay</step>
                    <step>Done</step>
                  </stepper>
                </body>
                """);

        Stepper stepper = (Stepper) UiLoader.findById(root, "s");
        assertEquals(3, stepper.getStepCount());
        assertEquals("Cart", stepper.getStepLabel(0));
        assertEquals(1, stepper.getCurrentStep());
        assertFalse(stepper.isLinear());
    }

    @Test
    void toolbarAndGroupBoxAreContainers() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <toolbar id="bar">
                    <button id="run">Run</button>
                  </toolbar>
                  <groupbox id="box" title="Account">
                    <input id="user" value="ada"/>
                  </groupbox>
                </body>
                """);

        Toolbar toolbar = (Toolbar) UiLoader.findById(root, "bar");
        assertNotNull(toolbar);
        assertEquals(1, toolbar.getChildren().size());

        GroupBox box = (GroupBox) UiLoader.findById(root, "box");
        assertNotNull(box);
        assertEquals("Account", box.getTitle());
        assertEquals(1, box.getChildren().size());
    }

    @Test
    void listBoxItemsComeFromChildElements() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <listbox id="list">
                    <item>Alpha</item>
                    <item selected>Beta</item>
                    <item>Gamma</item>
                  </listbox>
                </body>
                """);

        ListBox list = (ListBox) UiLoader.findById(root, "list");
        assertEquals(3, list.getItemCount());
        assertEquals("Alpha", list.getItem(0));
        assertEquals(1, list.getSelectedIndex());
        assertEquals("Beta", list.getSelectedItem());
    }

    @Test
    void splitPaneTakesTwoPanesAndOrientation() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <splitpane id="split" orientation="vertical" ratio="0.25">
                    <div id="top"/>
                    <div id="bottom"/>
                  </splitpane>
                </body>
                """);

        SplitPane pane = (SplitPane) UiLoader.findById(root, "split");
        assertNotNull(pane);
        assertEquals(SplitPane.Orientation.VERTICAL, pane.getOrientation());
        assertEquals(0.25f, pane.getRatio(), 1e-6);
        assertEquals(2, pane.getChildren().size());
        assertNotNull(UiLoader.findById(pane, "top"));
        assertNotNull(UiLoader.findById(pane, "bottom"));
    }

    @Test
    void splitPaneUnknownOrientationWarns() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><splitpane id=\"s\" orientation=\"diagonal\"/></body>");

        SplitPane pane = (SplitPane) UiLoader.findById(root, "s");
        assertEquals(SplitPane.Orientation.HORIZONTAL, pane.getOrientation());
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("diagonal")));
    }

    @Test
    void linkTagStaysDocumentMetadata() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <head><link rel="stylesheet" href="app.css"/></head>
                <body><label>Hi</label></body>
                """);

        assertTrue(root instanceof Panel);
        assertEquals(0, countByType(root, Link.class),
                "<link> must stay document metadata, not become a Link widget");
    }

    /** Counts the widgets of the given type anywhere in the tree. */
    private static int countByType(Component root, Class<?> type) {
        int count = type.isInstance(root) ? 1 : 0;
        if (root instanceof Panel panel) {
            for (Component child : panel.getChildren()) {
                count += countByType(child, type);
            }
        }
        return count;
    }
}
