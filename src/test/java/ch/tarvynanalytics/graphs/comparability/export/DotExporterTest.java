package ch.tarvynanalytics.graphs.comparability.export;

import ch.tarvynanalytics.graphs.comparability.ComparabilityAnalyzer;
import ch.tarvynanalytics.graphs.comparability.GraphInput;
import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DotExporterTest {

    private static boolean[][] cycle(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a[i][j] = true;
            a[j][i] = true;
        }
        return a;
    }

    @Test
    void inputGraph_RendersUndirectedDot() {
        AnalysisResult r = ComparabilityAnalyzer.analyze(
                GraphInput.fromAdjacency(cycle(4), new String[]{"a", "b", "c", "d"}));
        String dot = DotExporter.inputGraphToDot(r);

        assertTrue(dot.startsWith("graph inputGraph {"));
        assertTrue(dot.contains("[label=\"a\"]"));
        assertTrue(dot.contains("0 -- 1;"));
        assertTrue(dot.trim().endsWith("}"));
    }

    @Test
    void factorLevel_RendersLevelGraph() {
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(cycle(4)));
        String dot = DotExporter.factorLevelToDot(r.levels().get(0));
        assertTrue(dot.startsWith("graph level0 {"));
        assertTrue(dot.contains(" -- "));
    }

    @Test
    void failureCycle_HighlightsCycleAndWeakestEdge() {
        int n = 5;
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            m[i][i] = 1.0;
        }
        double[] w = {0.9, 0.8, 0.7, 0.6, 0.55};
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            m[i][j] = w[i];
            m[j][i] = w[i];
        }
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(m, 0.5));
        String dot = DotExporter.failureCycleToDot(r.failure().orElseThrow());

        assertTrue(dot.startsWith("graph failureCycle {"));
        assertTrue(dot.contains("color=red"));
        assertTrue(dot.contains("penwidth=2"));
        assertTrue(dot.contains("weakest 0.55"));
        // a 5-cycle has exactly 5 undirected edges
        assertTrue(dot.lines().filter(l -> l.contains(" -- ")).count() == 5);
    }

    @Test
    void failureCycle_WithoutCorrelation_NoWeakestHighlight() {
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(cycle(5)));
        String dot = DotExporter.failureCycleToDot(r.failure().orElseThrow());
        assertTrue(dot.contains("color=red"));
        assertTrue(!dot.contains("penwidth=2"), "no weakest edge highlighted for adjacency input");
    }

    @Test
    void chordalCompletion_DrawsFillInEdgesDashed() {
        // C4 is a hole: its chordal completion adds exactly one chord (drawn dashed/blue).
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(cycle(4)));
        String dot = DotExporter.chordalCompletionToDot(r);

        assertTrue(dot.startsWith("graph chordalCompletion {"));
        assertTrue(dot.contains("0 -- 1;"), "input edges are solid");
        assertTrue(dot.contains("style=dashed, color=blue"), "fill-in edge is dashed");
        assertTrue(dot.lines().filter(l -> l.contains("style=dashed")).count() == 1, dot);
    }

    @Test
    void chordalCompletion_ChordalGraph_HasNoFillIn() {
        // A triangle is already chordal: no fill-in edges, hence no dashed lines.
        boolean[][] triangle = {
                {false, true, true},
                {true, false, true},
                {true, true, false}
        };
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(triangle));
        String dot = DotExporter.chordalCompletionToDot(r);
        assertTrue(dot.startsWith("graph chordalCompletion {"));
        assertTrue(!dot.contains("dashed"), "already chordal -> no fill-in edges");
    }
}
