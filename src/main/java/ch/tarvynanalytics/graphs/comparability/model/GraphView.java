package ch.tarvynanalytics.graphs.comparability.model;

import java.util.List;

/**
 * An immutable, undirected graph snapshot: its vertices and its edges. Used
 * both for the input graph and for every quotient (factor) graph in the
 * decomposition hierarchy.
 *
 * @param nodes the vertices, in id order
 * @param edges the undirected edges, each listed once with {@code source < target}
 */
public record GraphView(List<NodeView> nodes, List<EdgeView> edges) {

    /**
     * Canonical constructor; defensively copies the lists into unmodifiable
     * views so the record is deeply immutable.
     *
     * @param nodes the vertices
     * @param edges the edges
     */
    public GraphView {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
    }

    /**
     * @return the number of vertices
     */
    public int order() {
        return nodes.size();
    }

    /**
     * @return the number of edges
     */
    public int size() {
        return edges.size();
    }
}
