package io.github.yasmramos.glyphui.markup;

import io.github.yasmramos.glyphui.ui.ColorPicker;
import io.github.yasmramos.glyphui.ui.Component;
import io.github.yasmramos.glyphui.ui.Dialog;
import io.github.yasmramos.glyphui.ui.Table;
import io.github.yasmramos.glyphui.ui.TreeView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Markup tests for the data widgets added on top of the classic vocabulary:
 * {@code table}, {@code treeview}, {@code dialog} and {@code colorpicker}.
 */
class TableTreeDialogMarkupTest {

    @Test
    void tableTagReadsItsColumns() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><table id=\"t\" columns=\"Name, Value\"/></body>");

        Table table = (Table) UiLoader.findById(root, "t");
        assertNotNull(table, "<table> must map to Table");
        assertEquals(2, table.getColumnCount());
        assertEquals("Name", table.getColumnTitle(0));
        assertEquals("Value", table.getColumnTitle(1));
    }

    @Test
    void tableRowsComeFromRowChildren() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <table id="t" columns="Name, Value">
                    <row><cell>Alpha</cell><cell value="1"/></row>
                    <row><cell>Beta</cell><cell value="2"/></row>
                  </table>
                </body>
                """);

        Table table = (Table) UiLoader.findById(root, "t");
        assertEquals(2, table.getRowCount());
        assertEquals("Alpha", table.getCell(0, 0));
        assertEquals("1", table.getCell(0, 1));
        assertEquals("Beta", table.getCell(1, 0));
        assertEquals("2", table.getCell(1, 1));
    }

    @Test
    void treeViewTagBuildsNestedNodes() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <treeview id="tree">
                    <node expanded>Root
                      <node>Child</node>
                    </node>
                    <node label="Other"/>
                  </treeview>
                </body>
                """);

        TreeView tree = (TreeView) UiLoader.findById(root, "tree");
        assertNotNull(tree, "<treeview> must map to TreeView");
        assertEquals(2, tree.getRoots().size());

        TreeView.Node rootNode = tree.getRoots().get(0);
        assertEquals("Root", rootNode.getLabel());
        assertTrue(rootNode.isExpanded());
        assertEquals(1, rootNode.getChildren().size());
        assertEquals("Child", rootNode.getChildren().get(0).getLabel());

        assertEquals("Other", tree.getRoots().get(1).getLabel());
    }

    @Test
    void collapsedTreeNodeStartsClosed() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString("""
                <body>
                  <treeview id="tree">
                    <node>Root
                      <node>Child</node>
                    </node>
                  </treeview>
                </body>
                """);

        TreeView tree = (TreeView) UiLoader.findById(root, "tree");
        assertFalse(tree.getRoots().get(0).isExpanded());
    }

    @Test
    void dialogTagReadsTitleMessageAndActions() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><dialog id=\"d\" title=\"Delete?\" actions=\"Cancel, Delete\">"
                        + "This cannot be undone.</dialog></body>");

        Dialog dialog = (Dialog) UiLoader.findById(root, "d");
        assertNotNull(dialog, "<dialog> must map to Dialog");
        assertEquals("Delete?", dialog.getTitle());
        assertEquals("This cannot be undone.", dialog.getMessage());
        assertEquals(List.of("Cancel", "Delete"), dialog.getActions().stream()
                .map(Dialog.Action::getLabel).toList());
    }

    @Test
    void dialogTagCanBeNonDismissible() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><dialog id=\"d\" title=\"Confirm\" static/></body>");

        Dialog dialog = (Dialog) UiLoader.findById(root, "d");
        assertFalse(dialog.isDismissible());
    }

    @Test
    void dialogTagStartsHidden() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><dialog id=\"d\" title=\"Hi\"/></body>");

        Dialog dialog = (Dialog) UiLoader.findById(root, "d");
        assertFalse(dialog.isOpen(), "a dialog must not show until show() is called");
    }

    @Test
    void colorPickerTagReadsPaletteValueAndFlags() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><colorpicker id=\"c\" palette=\"#FF0000, #00FF00\" "
                        + "value=\"#00FF00\" columns=\"1\" no-preview no-hex/></body>");

        ColorPicker picker = (ColorPicker) UiLoader.findById(root, "c");
        assertNotNull(picker, "<colorpicker> must map to ColorPicker");
        assertEquals(2, picker.getSwatchCount());
        assertEquals(0xFFFF0000, picker.getSwatch(0));
        assertEquals(0xFF00FF00, picker.getValue());
        assertEquals(1, picker.getColumns());
        assertFalse(picker.isShowPreview());
        assertFalse(picker.isShowHex());
    }

    @Test
    void badPaletteColorsAreSkippedWithAWarning() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><colorpicker id=\"c\" palette=\"#FF0000, nope\"/></body>");

        ColorPicker picker = (ColorPicker) UiLoader.findById(root, "c");
        assertEquals(1, picker.getSwatchCount());
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("nope")));
    }

    @Test
    void badColorPickerValueWarns() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><colorpicker id=\"c\" value=\"#ZZZ\"/></body>");

        assertNotNull(UiLoader.findById(root, "c"));
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("#ZZZ")));
    }

    @Test
    void badColumnCountWarns() {
        UiLoader loader = new UiLoader();
        Component root = loader.loadFromString(
                "<body><colorpicker id=\"c\" columns=\"many\"/></body>");

        assertNotNull(UiLoader.findById(root, "c"));
        assertTrue(loader.getWarnings().stream().anyMatch(w -> w.contains("many")));
    }
}
