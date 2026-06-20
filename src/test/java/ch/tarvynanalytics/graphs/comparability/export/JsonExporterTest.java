package ch.tarvynanalytics.graphs.comparability.export;

import ch.tarvynanalytics.graphs.comparability.ComparabilityAnalyzer;
import ch.tarvynanalytics.graphs.comparability.DecomposabilityDiagnostic;
import ch.tarvynanalytics.graphs.comparability.GraphInput;
import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;
import ch.tarvynanalytics.graphs.comparability.model.DecomposabilityReport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonExporterTest {

    private static boolean[][] cycle(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a[i][j] = true;
            a[j][i] = true;
        }
        return a;
    }

    private static double[][] correlationCycle5() {
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
        return m;
    }

    @Test
    void comparabilityResult_SerializesCountAndNullFailure() {
        double[][] k3 = {
                {1.0, 0.9, 0.8},
                {0.9, 1.0, 0.7},
                {0.8, 0.7, 1.0}
        };
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(k3, 0.5));
        String json = JsonExporter.toJson(r);

        assertTrue(json.contains("\"comparability\":true"));
        assertTrue(json.contains("\"transitiveOrientationCount\":6"));
        assertTrue(json.contains("\"failure\":null"));
        assertTrue(json.contains("\"inputGraph\":"));
        assertTrue(json.contains("\"levels\":["));
        // K3 is also chordal: chordal=true, empty hole and fill-in.
        assertTrue(json.contains("\"chordality\":{\"chordal\":true"));
        assertTrue(json.contains("\"chordlessCycle\":[]"));
        assertTrue(json.contains("\"fillInEdges\":[]"));
        assertBalanced(json);
    }

    @Test
    void nonChordalGraph_SerializesHoleAndFillIn() {
        // C4 is a hole: not chordal, witnessed by the 4-cycle, completed with one chord.
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(cycle(4)));
        String json = JsonExporter.toJson(r);

        assertTrue(json.contains("\"chordality\":{\"chordal\":false"));
        assertTrue(json.contains("\"chordlessCycle\":[0,1,2,3]"), json);
        assertTrue(json.contains("\"fillInEdges\":[{\"source\":1,\"target\":3}]"), json);
        assertBalanced(json);
    }

    @Test
    void adjacencyFailure_SerializesFailureWithNullCorrelation() {
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(cycle(5)));
        String json = JsonExporter.toJson(r);

        assertTrue(json.contains("\"comparability\":false"));
        assertTrue(json.contains("\"transitiveOrientationCount\":0"));
        assertFalse(json.contains("\"failure\":null"));
        assertTrue(json.contains("\"weakestCorrelation\":null"));
        assertTrue(json.contains("\"nodeIds\":[0,1,2,3,4]"));
        assertBalanced(json);
    }

    @Test
    void correlationFailure_SerializesWeakestEdge() {
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(correlationCycle5(), 0.5));
        String json = JsonExporter.toJson(r);

        assertTrue(json.contains("\"weakestCorrelation\":0.55"));
        assertTrue(json.contains("\"weakestEdge\":{\"source\":4,\"target\":0}")
                || json.contains("\"weakestEdge\":{\"source\":0,\"target\":4}"));
        assertBalanced(json);
    }

    @Test
    void labels_AreJsonEscaped() {
        AnalysisResult r = ComparabilityAnalyzer.analyze(
                GraphInput.fromAdjacency(new boolean[1][1], new String[]{"a\"b\\c"}));
        String json = JsonExporter.toJson(r);
        assertTrue(json.contains("\"label\":\"a\\\"b\\\\c\""));
    }

    @Test
    void decomposabilityReport_SerializesRemovalsAndThreshold() {
        AnalysisResult ignored = ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(correlationCycle5(), 0.5));
        assertFalse(ignored.chordality().isChordal()); // C5 is a hole
        DecomposabilityReport report = DecomposabilityDiagnostic.analyze(
                GraphInput.fromCorrelation(correlationCycle5(), 0.5));
        String json = JsonExporter.toJson(report);

        assertTrue(json.contains("\"decomposable\":false"), json);
        assertTrue(json.contains("\"fillInAlternative\":2"), json);
        assertTrue(json.contains("\"suggestedThreshold\":0.55"), json);
        assertTrue(json.contains("\"weakestLinksToRemove\":[{\"source\":0,\"target\":4,\"correlation\":0.55}]"), json);
        assertBalanced(json);
    }

    @Test
    void decomposabilityReport_AlreadyChordal_EmptyRemovalNullThreshold() {
        double[][] k3 = {{1.0, 0.9, 0.8}, {0.9, 1.0, 0.7}, {0.8, 0.7, 1.0}};
        DecomposabilityReport report = DecomposabilityDiagnostic.analyze(GraphInput.fromCorrelation(k3, 0.5));
        String json = JsonExporter.toJson(report);

        assertTrue(json.contains("\"decomposable\":true"), json);
        assertTrue(json.contains("\"suggestedThreshold\":null"), json);
        assertTrue(json.contains("\"weakestLinksToRemove\":[]"), json);
        assertBalanced(json);
    }

    private static void assertBalanced(String json) {
        int braces = 0;
        int brackets = 0;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> inString = true;
                case '{' -> braces++;
                case '}' -> braces--;
                case '[' -> brackets++;
                case ']' -> brackets--;
                default -> { /* ignore */ }
            }
        }
        assertEquals(0, braces, "braces balanced");
        assertEquals(0, brackets, "brackets balanced");
    }
}
