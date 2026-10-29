package io.github.yasmramos.glyphui.markup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class of every AST node produced by {@link Parser}.
 *
 * <p>
 * Nodes keep the 1-based start position (line/column) of the construct they
 * represent so that later phases (validator, binder, diagnostics) can point
 * back into the original {@code .glyph} source. End positions are not tracked
 * per node (item 5.21): the tokenizer records no lengths and free text spans
 * arbitrary line breaks, so a reliable end column would require re-plumbing
 * every production. The parser instead anchors each {@link Element} to the
 * position of its matching end tag ({@link Element#getCloseLine()} /
 * {@link Element#getCloseColumn()}) and each {@link BindingNode} to its
 * closing brace — enough to bracket the full construct for ranged
 * diagnostics without inventing fake extents for text runs.</p>
 *
 * <p>
 * Parent links (item 5.22) are established by a post-parse linking pass
 * ({@link #linkParents(Document)}), invoked automatically by
 * {@link Parser#parse(String)}. Every child reachable from the document
 * root knows its owner: {@link TextNode}, {@link BindingNode} and nested
 * {@link Element}s return their enclosing element (or the owning
 * {@link Document} at top level) from {@link #getParent()}. The field is
 * mutable because the tree is built bottom-up (children exist before their
 * parent's close tag is seen); it is written exactly once, during linking,
 * and treated as read-only afterwards.</p>
 */
public abstract sealed class AstNode permits Element, TextNode, BindingNode {

    /**
     * Owning node assigned by the post-parse linking pass, or null when this
     * node is detached (constructed directly, never linked).
     */
    private AstContainer parent;

    /**
     * Minimal container contract needed for parent linking: AST nodes plus
     * {@link Document}, which is the owner of top-level children but is not
     * itself an {@code AstNode}.
     */
    interface AstContainer {
        List<? extends AstNode> getChildren();
    }

    /**
     * @return the owning element/document, or null when this node has no
     * parent (top-level nodes report their {@link Document}; detached nodes
     * report null)
     */
    public AstNode getParent() {
        return parent instanceof AstNode node ? node : null;
    }

    /**
     * Returns the raw parent link including non-AstNode owners such as the
     * document root. Used by the linking walk and by diagnostics tooling.
     *
     * @return the owner assigned during linking, or null when detached
     */
    Object getParentLink() {
        return parent;
    }

    /**
     * Assigns the owner of this node (parser-internal, called once per node
     * by {@link #linkParents(Document)}).
     *
     * @param parent the owning container
     */
    void setParent(AstContainer parent) {
        this.parent = parent;
    }

    /**
     * Runs the parent-linking pass over a freshly parsed document: walks the
     * tree depth-first and assigns each child its owner before recursing, so
     * {@link #getParent()} answers correctly for every node afterwards.
     * Idempotent — re-running it only refreshes the same links.
     *
     * @param document the document whose tree should carry parent links
     */
    public static void linkParents(Document document) {
        if (document == null) {
            return;
        }
        for (AstNode child : document.getChildren()) {
            child.setParent(document);
            linkRecursively(child);
        }
    }

    private static void linkRecursively(AstNode node) {
        for (AstNode child : node.getChildren()) {
            child.setParent((AstContainer) node);
            linkRecursively(child);
        }
    }

    /**
     * Child list used by the linking pass. Leaf nodes (text, bindings)
     * override to an empty view; elements return their children.
     *
     * @return the node's children in source order (never null)
     */
    abstract List<? extends AstNode> getChildren();

    /**
     * 1-based line where this node starts.
     */
    protected final int line;

    /**
     * 1-based column where this node starts.
     */
    protected final int column;

    /**
     * Records the start position of a node.
     *
     * @param line 1-based line
     * @param column 1-based column
     */
    protected AstNode(int line, int column) {
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException(
                    "positions are 1-based, got line=" + line + " column=" + column);
        }
        this.line = line;
        this.column = column;
    }

    /**
     * @return the 1-based start line of this node
     */
    public int getLine() {
        return line;
    }

    /**
     * @return the 1-based start column of this node
     */
    public int getColumn() {
        return column;
    }

    /**
     * Formats a child list for {@code toString()} implementations.
     *
     * @param children the children to render
     * @return comma-separated rendering wrapped in brackets
     */
    static String formatChildren(List<? extends AstNode> children) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(children.get(i));
        }
        return sb.append(']').toString();
    }

    /**
     * Unmodifiable view helper used by subclasses.
     *
     * @param list the backing list
     * @param <T> element type
     * @return an unmodifiable view of {@code list}
     */
    static <T> List<T> frozen(List<T> list) {
        return Collections.unmodifiableList(list);
    }

    /**
     * Mutable copy helper used by subclass constructors.
     *
     * @param list the source list (may be null → empty)
     * @param <T> element type
     * @return a new modifiable {@link ArrayList} with the same elements
     */
    static <T> List<T> mutableCopy(List<T> list) {
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }
}
