package ch.tarvynanalytics.graphs.comparability.export;

import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;
import ch.tarvynanalytics.graphs.comparability.model.EdgeView;
import ch.tarvynanalytics.graphs.comparability.model.FactorGraphLevelView;
import ch.tarvynanalytics.graphs.comparability.model.FailureCycle;
import ch.tarvynanalytics.graphs.comparability.model.GraphView;
import ch.tarvynanalytics.graphs.comparability.model.ModuleView;
import ch.tarvynanalytics.graphs.comparability.model.NodeView;

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
 *   "failure": null
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
        sb.append('}');
        return sb.toString();
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
            sb.append("{\"source\":").append(e.source()).append(",\"target\":").append(e.target()).append('}');
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
        sb.append(",\"weakestEdge\":{\"source\":").append(f.weakestEdgeSource())
                .append(",\"target\":").append(f.weakestEdgeTarget()).append('}');
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
