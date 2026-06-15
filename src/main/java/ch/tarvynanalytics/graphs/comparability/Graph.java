package ch.tarvynanalytics.graphs.comparability;

import java.util.ArrayList;
import java.util.List;

/**
 * An undirected graph or sub-graph used by the engine — the analysis-only port
 * of the thesis prototype's {@code Graph_NonOriented} (all WinForms drawing
 * state and methods are dropped).
 *
 * <p>{@code correlMatrix} is the thresholded correlation matrix of the original
 * input graph: entry {@code [i][j]} is the correlation for an edge and {@code 0}
 * for a non-edge. It is only populated on the level-0 graph (the one whose nodes
 * are leaves); factor graphs leave it {@code null} because the weakest-edge
 * lookup always drills down to original leaf nodes.</p>
 */
final class Graph {

    final List<Node> nodes;

    /** Thresholded correlation matrix (original graph only); {@code null} for adjacency-only input or factor graphs. */
    double[][] correlMatrix;

    /** Chordless chain that seeded a minimal stable (MIN_STABLE) module. */
    List<Node> nonTriangulableChain;
    /** The chain extended to cover every edge of a minimal stable module. */
    List<Node> nonTriangulableChainAllLinks;

    /** Empty graph (factor graph or accumulator). */
    Graph() {
        this.nodes = new ArrayList<>();
    }

    /** Sub-graph wrapping an existing set of nodes (a module). */
    Graph(List<Node> nodes) {
        this.nodes = new ArrayList<>(nodes);
    }

    /**
     * Builds the level-0 graph from an input: one leaf node per matrix index,
     * undirected edges per {@link GraphInput#adjacent}, and the thresholded
     * correlation matrix when correlation values are available.
     *
     * @param input the validated graph input
     * @return the level-0 graph
     */
    static Graph fromInput(GraphInput input) {
        int n = input.order();
        Graph g = new Graph();
        for (int i = 0; i < n; i++) {
            g.nodes.add(new Node(i, g, input.label(i)));
        }
        if (input.hasCorrelation()) {
            g.correlMatrix = new double[n][n];
        }
        for (int i = 0; i < n; i++) {
            Node ni = g.nodes.get(i);
            for (int j = 0; j < n; j++) {
                if (i != j && input.adjacent(i, j)) {
                    ni.adjacentNodes.add(g.nodes.get(j));
                    ni.nodesNotOriented.add(g.nodes.get(j));
                    if (g.correlMatrix != null) {
                        g.correlMatrix[i][j] = input.correlation(i, j);
                    }
                }
            }
        }
        return g;
    }
}
