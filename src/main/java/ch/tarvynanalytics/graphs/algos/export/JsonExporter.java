package ch.tarvynanalytics.graphs.algos.export;

import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;
import ch.tarvynanalytics.graphs.algos.model.ChordalityView;
import ch.tarvynanalytics.graphs.algos.model.CrossEstimatorReport;
import ch.tarvynanalytics.graphs.algos.model.DecomposabilityReport;
import ch.tarvynanalytics.graphs.algos.model.EdgeView;
import ch.tarvynanalytics.graphs.algos.model.FactorGraphLevelView;
import ch.tarvynanalytics.graphs.algos.model.FailureCycle;
import ch.tarvynanalytics.graphs.algos.model.GraphView;
import ch.tarvynanalytics.graphs.algos.model.ModuleView;
import ch.tarvynanalytics.graphs.algos.model.NodeView;
import ch.tarvynanalytics.graphs.algos.model.RobustEdge;
import ch.tarvynanalytics.graphs.algos.model.StructuralBalanceView;

/**
 * Serializes an {@link AnalysisResult} to a compact JSON document. Hand-rolled
 * so the library keeps zero runtime dependencies.
 *
 * <p>Shape:</p>
 * <pre>{@code
 * {
 *   "comparability": true,
 *   "transitiveOrientationCount": 6,
 *   "inputGraph": { "nodes": [{"id":0,"label":"a"}], "edges": [{"source":0,"target":1}] },
 *   "levels": [ { "level":0, "graph": {...}, "modules": [...], "factorGraph": {...} } ],
 *   "failure": null,
 *   "chordality": { "chordal":true, "perfectEliminationOrder":[2,1,0],
 *                   "chordlessCycle":[], "fillInEdges":[] }
 * }
 * }</pre>
 *
 * <p>A non-finite weakest correlation is emitted as {@code null} (JSON has no
 * NaN). The orientation count is emitted as a bare JSON integer of arbitrary
 * precision.</p>
 */
public final class JsonExporter {

    private JsonExporter() {
    }

    /**
     * @param result the analysis result
     * @return the result as a JSON document
     */
    public static String toJson(AnalysisResult result) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"comparability\":").append(result.comparability());
        sb.append(",\"transitiveOrientationCount\":").append(result.transitiveOrientationCount());
        sb.append(",\"inputGraph\":");
        graph(sb, result.inputGraph());
        sb.append(",\"levels\":[");
        for (int i = 0; i < result.levels().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            level(sb, result.levels().get(i));
        }
        sb.append(']');
        sb.append(",\"failure\":");
        if (result.failureCycle() == null) {
            sb.append("null");
        } else {
            failure(sb, result.failureCycle());
        }
        sb.append(",\"chordality\":");
        chordality(sb, result.chordality());
        sb.append('}');
        return sb.toString();
    }

    /**
     * Serializes a {@link DecomposabilityReport} (the weakest-link deletion repair) to JSON.
     *
     * @param report the decomposability diagnostic
     * @return the report as a JSON document
     */
    public static String toJson(DecomposabilityReport report) {
        StringBuilder sb = new StringBuilder(128);
        sb.append("{\"decomposable\":").append(report.decomposable());
        sb.append(",\"suggestedThreshold\":");
        if (Double.isFinite(report.suggestedThreshold())) {
            sb.append(report.suggestedThreshold());
        } else {
            sb.append("null");
        }
        sb.append(",\"fillInAlternative\":").append(report.fillInAlternative());
        sb.append(",\"weakestLinksToRemove\":[");
        for (int i = 0; i < report.weakestLinksToRemove().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            EdgeView e = report.weakestLinksToRemove().get(i);
            double corr = report.removedCorrelations().get(i);
            edgeRef(sb, e.source(), e.target()).append(",\"correlation\":");
            if (Double.isFinite(corr)) {
                sb.append(corr);
            } else {
                sb.append("null");
            }
            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void chordality(StringBuilder sb, ChordalityView c) {
        sb.append("{\"chordal\":").append(c.chordal());
        sb.append(",\"perfectEliminationOrder\":");
        ints(sb, c.perfectEliminationOrder());
        sb.append(",\"chordlessCycle\":");
        ints(sb, c.chordlessCycle());
        sb.append(",\"fillInEdges\":[");
        for (int i = 0; i < c.fillInEdges().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            EdgeView e = c.fillInEdges().get(i);
            edgeRef(sb, e.source(), e.target()).append('}');
        }
        sb.append("]}");
    }

    /**
     * Serializes a {@link CrossEstimatorReport} (the cross-estimator robustness comparison) to JSON.
     *
     * @param report the cross-estimator report
     * @return the report as a JSON document
     */
    public static String toJson(CrossEstimatorReport report) {
        StringBuilder sb = new StringBuilder(256);
        sb.append("{\"topK\":").append(report.topK());
        sb.append(",\"stableCoreSize\":").append(report.stableCore().size());
        sb.append(",\"estimatorNames\":[");
        for (int i = 0; i < report.estimatorNames().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            string(sb, report.estimatorNames().get(i));
        }
        sb.append("],\"jaccard\":[");
        for (int i = 0; i < report.jaccard().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('[');
            java.util.List<Double> row = report.jaccard().get(i);
            for (int j = 0; j < row.size(); j++) {
                if (j > 0) {
                    sb.append(',');
                }
                sb.append(row.get(j).doubleValue());
            }
            sb.append(']');
        }
        sb.append("],\"edges\":[");
        for (int i = 0; i < report.edges().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            robustEdge(sb, report.edges().get(i));
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void robustEdge(StringBuilder sb, RobustEdge e) {
        edgeRef(sb, e.source(), e.target());
        sb.append(",\"sourceLabel\":");
        string(sb, e.sourceLabel());
        sb.append(",\"targetLabel\":");
        string(sb, e.targetLabel());
        sb.append(",\"support\":").append(e.support());
        sb.append(",\"estimatorIndices\":");
        ints(sb, e.estimatorIndices());
        sb.append('}');
    }

    /**
     * Serializes a {@link StructuralBalanceView} (the signed-graph balance verdict) to JSON.
     *
     * @param balance the structural-balance view
     * @return the view as a JSON document
     */
    public static String toJson(StructuralBalanceView balance) {
        StringBuilder sb = new StringBuilder(96);
        sb.append("{\"balanced\":").append(balance.balanced());
        sb.append(",\"negativeEdgeCount\":").append(balance.negativeEdgeCount());
        sb.append(",\"camp\":");
        ints(sb, balance.camp());
        sb.append(",\"frustratedCycle\":");
        ints(sb, balance.frustratedCycle());
        sb.append('}');
        return sb.toString();
    }

    /** Appends {@code {"source":S,"target":T} without the closing brace, for the caller to finish. */
    private static StringBuilder edgeRef(StringBuilder sb, int source, int target) {
        return sb.append("{\"source\":").append(source).append(",\"target\":").append(target);
    }

    private static void level(StringBuilder sb, FactorGraphLevelView level) {
        sb.append("{\"level\":").append(level.level());
        sb.append(",\"graph\":");
        graph(sb, level.graph());
        sb.append(",\"modules\":[");
        for (int i = 0; i < level.modules().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            module(sb, level.modules().get(i));
        }
        sb.append(']');
        sb.append(",\"factorGraph\":");
        graph(sb, level.factorGraph());
        sb.append('}');
    }

    private static void module(StringBuilder sb, ModuleView m) {
        sb.append("{\"factorNodeId\":").append(m.factorNodeId());
        sb.append(",\"type\":\"").append(m.type()).append('"');
        sb.append(",\"members\":");
        ints(sb, m.memberNodeIds());
        sb.append('}');
    }

    private static void graph(StringBuilder sb, GraphView g) {
        sb.append("{\"nodes\":[");
        for (int i = 0; i < g.nodes().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            NodeView n = g.nodes().get(i);
            sb.append("{\"id\":").append(n.id()).append(",\"label\":");
            string(sb, n.label());
            sb.append('}');
        }
        sb.append("],\"edges\":[");
        for (int i = 0; i < g.edges().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            EdgeView e = g.edges().get(i);
            edgeRef(sb, e.source(), e.target()).append('}');
        }
        sb.append("]}");
    }

    private static void failure(StringBuilder sb, FailureCycle f) {
        sb.append("{\"level\":").append(f.level());
        sb.append(",\"nodeIds\":");
        ints(sb, f.nodeIds());
        sb.append(",\"nodeLabels\":[");
        for (int i = 0; i < f.nodeLabels().size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            string(sb, f.nodeLabels().get(i));
        }
        sb.append(']');
        sb.append(",\"weakestCorrelation\":");
        if (Double.isFinite(f.weakestCorrelation())) {
            sb.append(f.weakestCorrelation());
        } else {
            sb.append("null");
        }
        sb.append(",\"weakestEdge\":");
        edgeRef(sb, f.weakestEdgeSource(), f.weakestEdgeTarget()).append('}');
        sb.append('}');
    }

    private static void ints(StringBuilder sb, java.util.List<Integer> values) {
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i).intValue());
        }
        sb.append(']');
    }

    private static void string(StringBuilder sb, String s) {
        sb.append('"');
        if (s != null) {
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                    }
                }
            }
        }
        sb.append('"');
    }
}
