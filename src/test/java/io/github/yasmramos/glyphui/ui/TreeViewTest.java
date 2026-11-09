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
import io.github.yasmramos.glyphui.events.ScrollEvent;
import io.github.yasmramos.glyphui.graphics.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behaviour tests for {@link TreeView}: node building, flattening of expanded
 * branches, mouse and keyboard navigation, and the selection handler.
 */
class TreeViewTest extends TestBase {

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

    /** A tree with one expanded branch ("Root" > "Child") and one leaf. */
    private static TreeView sizedTree() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("Child");
        tree.addRoot("Other");
        root.setExpanded(true);
        tree.refresh();
        tree.setWidth(200.0f);
        tree.setHeight(4 * TreeView.ROW_HEIGHT);
        return tree;
    }

    @Test
    void rootsAndChildrenAreTracked() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");
        TreeView.Node child = root.addChild("Child");

        assertEquals(1, tree.getRoots().size());
        assertEquals("Root", root.getLabel());
        assertEquals(List.of(child), root.getChildren());
        assertSame(root, child.getParent());
        assertTrue(child.isLeaf());
        assertFalse(root.isLeaf());
    }

    @Test
    void nullLabelsBecomeEmpty() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot((String) null);

        assertEquals("", root.getLabel());
    }

    @Test
    void leafIgnoresTheExpandedFlag() {
        TreeView tree = new TreeView();
        TreeView.Node leaf = tree.addRoot("Leaf");

        leaf.setExpanded(true);

        assertFalse(leaf.isExpanded());
    }

    @Test
    void collapsedBranchesContributeNoRows() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("Child");
        tree.refresh();

        tree.onKeyEvent(key(GlyphKeys.END));
        assertEquals("Root", tree.getSelectedNode().getLabel());

        root.setExpanded(true);
        tree.refresh();
        tree.onKeyEvent(key(GlyphKeys.END));
        assertEquals("Child", tree.getSelectedNode().getLabel());
    }

    @Test
    void arrowKeysMoveThroughTheFlattenedRows() {
        TreeView tree = sizedTree();

        tree.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals("Root", tree.getSelectedNode().getLabel());

        tree.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals("Child", tree.getSelectedNode().getLabel());

        tree.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals("Other", tree.getSelectedNode().getLabel());

        tree.onKeyEvent(key(GlyphKeys.UP));
        assertEquals("Child", tree.getSelectedNode().getLabel());
    }

    @Test
    void upFromNoSelectionSelectsTheLastRow() {
        TreeView tree = sizedTree();

        tree.onKeyEvent(key(GlyphKeys.UP));

        assertEquals("Other", tree.getSelectedNode().getLabel());
    }

    @Test
    void homeAndEndJumpToTheBounds() {
        TreeView tree = sizedTree();

        tree.onKeyEvent(key(GlyphKeys.END));
        assertEquals("Other", tree.getSelectedNode().getLabel());

        tree.onKeyEvent(key(GlyphKeys.HOME));
        assertEquals("Root", tree.getSelectedNode().getLabel());
    }

    @Test
    void rightExpandsThenStepsIntoTheFirstChild() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("Child");
        tree.refresh();

        tree.onKeyEvent(key(GlyphKeys.HOME));
        tree.onKeyEvent(key(GlyphKeys.RIGHT));
        assertTrue(root.isExpanded());

        tree.onKeyEvent(key(GlyphKeys.RIGHT));
        assertEquals("Child", tree.getSelectedNode().getLabel());
    }

    @Test
    void leftCollapsesThenWalksUpToTheParent() {
        TreeView tree = sizedTree();
        TreeView.Node root = tree.getRoots().get(0);

        tree.onKeyEvent(key(GlyphKeys.DOWN));
        tree.onKeyEvent(key(GlyphKeys.DOWN));
        assertEquals("Child", tree.getSelectedNode().getLabel());

        tree.onKeyEvent(key(GlyphKeys.LEFT));
        assertEquals("Root", tree.getSelectedNode().getLabel());

        tree.onKeyEvent(key(GlyphKeys.LEFT));
        assertFalse(root.isExpanded());
    }

    @Test
    void enterTogglesABranch() {
        TreeView tree = sizedTree();
        TreeView.Node root = tree.getRoots().get(0);

        tree.onKeyEvent(key(GlyphKeys.HOME));
        tree.onKeyEvent(key(GlyphKeys.ENTER));

        assertFalse(root.isExpanded());
    }

    @Test
    void clickingARowSelectsIt() {
        TreeView tree = sizedTree();

        tree.onMouseEvent(mouse(MouseEventType.PRESS, 100, TreeView.ROW_HEIGHT + 2));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 100, TreeView.ROW_HEIGHT + 2));

        assertEquals("Child", tree.getSelectedNode().getLabel());
    }

    @Test
    void clickingTheDisclosureGlyphTogglesInsteadOfSelecting() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("Child");
        tree.refresh();
        tree.setWidth(200.0f);
        tree.setHeight(4 * TreeView.ROW_HEIGHT);

        tree.onMouseEvent(mouse(MouseEventType.PRESS, 10, 2));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 10, 2));

        assertTrue(root.isExpanded());
        assertNull(tree.getSelectedNode());
    }

    @Test
    void movingTheSelectionFiresTheHandler() {
        TreeView tree = sizedTree();
        List<String> seen = new ArrayList<>();
        tree.setOnSelect(node -> seen.add(node.getLabel()));

        tree.onKeyEvent(key(GlyphKeys.DOWN));
        tree.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals(List.of("Root", "Child"), seen);
    }

    @Test
    void programmaticSelectionDoesNotFireTheHandler() {
        TreeView tree = sizedTree();
        List<String> seen = new ArrayList<>();
        tree.setOnSelect(node -> seen.add(node.getLabel()));

        tree.setSelectedNode(tree.getRoots().get(1));

        assertTrue(seen.isEmpty());
        assertEquals("Other", tree.getSelectedNode().getLabel());
    }

    @Test
    void userDrivenSelectionKeepsTheObservablePropertyInSync() {
        TreeView tree = sizedTree();
        Property<TreeView.Node> selection = tree.selectedNodeProperty();

        tree.onKeyEvent(key(GlyphKeys.DOWN));

        assertSame(tree.getSelectedNode(), selection.get());
        assertEquals("Root", selection.get().getLabel());
    }

    @Test
    void selectionPropertyDrivesTheState() {
        TreeView tree = sizedTree();
        TreeView.Node other = tree.getRoots().get(1);

        tree.selectedNodeProperty().set(other);

        assertSame(other, tree.getSelectedNode());
    }

    @Test
    void clearRemovesNodesAndSelection() {
        TreeView tree = sizedTree();
        tree.onKeyEvent(key(GlyphKeys.DOWN));

        tree.clear();

        assertTrue(tree.getRoots().isEmpty());
        assertNull(tree.getSelectedNode());
        assertEquals(-1, tree.getSelectedRow());
    }

    @Test
    void clearKeepsTheObservablePropertyInSync() {
        TreeView tree = sizedTree();
        tree.onKeyEvent(key(GlyphKeys.DOWN));
        Property<TreeView.Node> selection = tree.selectedNodeProperty();
        assertEquals("Root", selection.get().getLabel());

        tree.clear();

        assertNull(selection.get());
    }

    @Test
    void handlerFailureIsSwallowed() {
        TreeView tree = sizedTree();
        tree.setOnSelect(node -> {
            throw new IllegalStateException("boom");
        });

        tree.onKeyEvent(key(GlyphKeys.DOWN));

        assertEquals("Root", tree.getSelectedNode().getLabel());
    }

    @Test
    void disabledTreeIgnoresInput() {
        TreeView tree = sizedTree();
        tree.setEnabled(false);

        tree.onKeyEvent(key(GlyphKeys.DOWN));

        assertNull(tree.getSelectedNode());
    }

    @Test
    void preferredHeightUsesTheVisibleRowCount() {
        TreeView tree = new TreeView();
        TreeView.Node root = tree.addRoot("Root");
        root.addChild("Child");
        tree.addRoot("Other");

        assertEquals(TreeView.ROW_HEIGHT * 2, tree.getPreferredHeight());

        root.setExpanded(true);
        tree.refresh();

        assertEquals(TreeView.ROW_HEIGHT * 3, tree.getPreferredHeight());
        assertTrue(tree.getPreferredWidth() > 0.0f);
    }

    @Test
    void accessibleRoleIsTree() {
        assertEquals(AccessibleRole.TREE, new TreeView().getAccessibleRole());
    }

    @Test
    void theWheelScrollsTheVisibleNodesWithoutChangingSelection() {
        TreeView tree = new TreeView();
        for (int i = 0; i < 20; i++) {
            tree.addRoot("Node " + i);
        }
        tree.refresh();
        // Three rows fit, so the wheel can scroll the window.
        tree.setWidth(200.0f);
        tree.setHeight(3 * TreeView.ROW_HEIGHT);

        tree.onMouseEvent(mouse(MouseEventType.PRESS, 20, 5));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 20, 5));
        assertEquals(0, tree.getSelectedRow());

        boolean consumed = tree.onScroll(new ScrollEvent(0f, 2f, 20, 5));

        assertTrue(consumed);
        assertEquals(0, tree.getSelectedRow(),
                "the wheel scrolls the view without moving the selection");
        // The window scrolled two rows, so clicking the top visible row now
        // selects the node that was previously at index 2.
        tree.onMouseEvent(mouse(MouseEventType.PRESS, 20, 5));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 20, 5));
        assertEquals(2, tree.getSelectedRow());
    }

    @Test
    void theWheelClampsAtTheEndOfTheTree() {
        TreeView tree = new TreeView();
        for (int i = 0; i < 6; i++) {
            tree.addRoot("Node " + i);
        }
        tree.refresh();
        tree.setWidth(200.0f);
        tree.setHeight(3 * TreeView.ROW_HEIGHT);

        assertTrue(tree.onScroll(new ScrollEvent(0f, 100f, 20, 5)));
        assertFalse(tree.onScroll(new ScrollEvent(0f, 1f, 20, 5)),
                "a scroll at the end of the tree is not consumed");
    }

    @Test
    void draggingTheThumbScrollsTheTree() {
        TreeView tree = twentyRootsThreeRows();

        // The bar is 8 wide on the trailing edge, 2 in from the right.
        tree.onMouseEvent(mouse(MouseEventType.PRESS, 194, 4));
        tree.onMouseEvent(mouse(MouseEventType.DRAG, 194, 200));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 194, 200));

        // Scrolled to the end (20 nodes, 3 visible, so the top row is index 17).
        tree.onMouseEvent(mouse(MouseEventType.PRESS, 20, 5));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 20, 5));
        assertEquals(17, tree.getSelectedRow(),
                "the thumb drag scrolled the tree to the end");
    }

    @Test
    void pressingTheTrackPagesTheTree() {
        TreeView tree = twentyRootsThreeRows();

        // A press below the thumb pages down by a viewport (three rows).
        tree.onMouseEvent(mouse(MouseEventType.PRESS, 194, 60));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 194, 60));

        tree.onMouseEvent(mouse(MouseEventType.PRESS, 20, 5));
        tree.onMouseEvent(mouse(MouseEventType.RELEASE, 20, 5));
        assertEquals(3, tree.getSelectedRow(),
                "a track press paged the window down by one viewport");
    }

    private static TreeView twentyRootsThreeRows() {
        TreeView tree = new TreeView();
        for (int i = 0; i < 20; i++) {
            tree.addRoot("Node " + i);
        }
        tree.refresh();
        tree.setWidth(200.0f);
        tree.setHeight(3 * TreeView.ROW_HEIGHT);
        return tree;
    }

    @Test
    void theScrollbarPaintsOnTheTrailingEdge() {
        // width 200: bar spans [190, 198).
        ScrollbarRaster.assertBarPaintsOnTrailingEdge(twentyRootsThreeRows(), 190);
    }
}
