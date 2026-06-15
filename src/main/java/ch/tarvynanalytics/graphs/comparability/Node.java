package ch.tarvynanalytics.graphs.comparability;

import java.util.ArrayList;
import java.util.List;

/**
 * A graph vertex used by the analysis engine — the analysis-only port of the
 * thesis prototype's {@code Node} class (the WinForms {@code PictureBox} base
 * and all drawing/event members are dropped).
 *
 * <p>Identity matters: nodes are compared by reference (no {@code equals}
 * override), exactly as the original relied on reference identity in its
 * {@code List<Node>.Contains} calls.</p>
 */
final class Node {

    /** Undirected neighbours. */
    final List<Node> adjacentNodes = new ArrayList<>();
    /** Directed out-neighbours, populated while a chain is oriented. */
    final List<Node> nodesTo = new ArrayList<>();
    /** Directed in-neighbours, populated while a chain is oriented. */
    final List<Node> nodesFrom = new ArrayList<>();
    /** Neighbours not yet oriented; working copy of {@link #adjacentNodes}. */
    final List<Node> nodesNotOriented = new ArrayList<>();

    /** Graph this node belongs to (its {@code correlMatrix} is the source for edge weights). */
    Graph parentGraph;
    /** For a factor-graph node: the module it represents; {@code null} for an original leaf node. */
    Graph respectiveSubGraph;

    /** Display label. */
    String name;
    /** Index of this node within the original input graph; the correlation-matrix index for leaves. */
    int id;

    /** Creates a factor-graph node standing in for {@code subGraph}. */
    Node(Graph parentGraph, Graph subGraph) {
        this.parentGraph = parentGraph;
        this.respectiveSubGraph = subGraph;
    }

    /** Creates an original leaf node with the given index and label. */
    Node(int id, Graph parentGraph, String name) {
        this.id = id;
        this.parentGraph = parentGraph;
        this.name = name;
    }
}
