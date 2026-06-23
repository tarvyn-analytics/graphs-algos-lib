package ch.tarvynanalytics.graphs.algos.export;

import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;
import ch.tarvynanalytics.graphs.algos.model.ChordalityView;
import ch.tarvynanalytics.graphs.algos.model.EdgeView;
import ch.tarvynanalytics.graphs.algos.model.FactorGraphLevelView;
import ch.tarvynanalytics.graphs.algos.model.FailureCycle;
import ch.tarvynanalytics.graphs.algos.model.GraphView;
import ch.tarvynanalytics.graphs.algos.model.NodeView;

/**
 * Renders pieces of an {@link AnalysisResult} as Graphviz DOT text (undirected
 * {@code graph}). Hand-rolled so the library keeps zero runtime dependencies;
 * pipe the output to {@code dot} (e.g. {@code dot -Tsvg}) to view it.
 */
public final class DotExporter {

    private DotExporter() {
    }

    /**
     * @param result the analysis result
     * @return the input graph as DOT
     */
    public static String inputGraphToDot(AnalysisResult result) {
        return graphToDot("inputGraph", result.inputGraph());
    }

    /**
     * Renders one decomposition level's graph as DOT. Vertex labels show the
     * original vertices a factor node stands for (e.g. {@code {AAPL,MSFT}}).
     *
     * @param level the level to render
     * @return the level graph as DOT
     */
    public static String factorLevelToDot(FactorGraphLevelView level) {
        return graphToDot("level" + level.level(), level.graph());
    }

    /**
     * Renders the obstructing odd cycle as DOT: the cycle vertices and the cycle
     * edges in red, with the weakest correlation edge drawn bold and labelled.
     *
     * @param failure the failure cycle
     * @return the cycle as DOT
     */
    public static String failureCycleToDot(FailureCycle failure) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("graph failureCycle {\n");
        sb.append("  node [shape=circle];\n");
        int n = failure.length();
        for (int i = 0; i < n; i++) {
            sb.append("  ").append(failure.nodeIds().get(i)).append(" [label=");
            quote(sb, failure.nodeLabels().get(i));
            sb.append("];\n");
        }
        boolean hasWeakest = failure.weakestEdgeSource() >= 0 && failure.weakestEdgeTarget() >= 0;
        for (int i = 0; i < n; i++) {
            int a = failure.nodeIds().get(i);
            int b = failure.nodeIds().get((i + 1) % n);
            sb.append("  ").append(a).append(" -- ").append(b);
            if (hasWeakest && isSameEdge(a, b, failure.weakestEdgeSource(), failure.weakestEdgeTarget())) {
                sb.append(" [color=red, penwidth=2, label=")
                        .append('"').append("weakest ").append(failure.weakestCorrelation()).append('"').append(']');
            } else {
                sb.append(" [color=red]");
            }
            sb.append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Renders the chordal completion as DOT: the input graph's edges solid, plus
     * the fill-in edges (the extra edges that make the graph chordal) dashed and
     * blue. For an already-chordal graph there are no fill-in edges, so this is
     * just the input graph.
     *
     * @param result the analysis result
     * @return the chordal completion as DOT
     */
    public static String chordalCompletionToDot(AnalysisResult result) {
        GraphView g = result.inputGraph();
        ChordalityView c = result.chordality();
        StringBuilder sb = new StringBuilder(160);
        sb.append("graph chordalCompletion {\n");
        sb.append("  node [shape=circle];\n");
        for (NodeView node : g.nodes()) {
            sb.append("  ").append(node.id()).append(" [label=");
            quote(sb, node.label());
            sb.append("];\n");
        }
        for (EdgeView e : g.edges()) {
            sb.append("  ").append(e.source()).append(" -- ").append(e.target()).append(";\n");
        }
        for (EdgeView e : c.fillInEdges()) {
            sb.append("  ").append(e.source()).append(" -- ").append(e.target())
                    .append(" [style=dashed, color=blue];\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static boolean isSameEdge(int a, int b, int c, int d) {
        return (a == c && b == d) || (a == d && b == c);
    }

    private static String graphToDot(String name, GraphView g) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("graph ").append(name).append(" {\n");
        sb.append("  node [shape=circle];\n");
        for (NodeView node : g.nodes()) {
            sb.append("  ").append(node.id()).append(" [label=");
            quote(sb, node.label());
            sb.append("];\n");
        }
        for (EdgeView e : g.edges()) {
            sb.append("  ").append(e.source()).append(" -- ").append(e.target()).append(";\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    private static void quote(StringBuilder sb, String label) {
        sb.append('"');
        if (label != null) {
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (c == '"' || c == '\\') {
                    sb.append('\\');
                }
                sb.append(c);
            }
        }
        sb.append('"');
    }
}
