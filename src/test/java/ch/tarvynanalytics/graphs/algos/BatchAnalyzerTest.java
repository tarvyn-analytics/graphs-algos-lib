package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchAnalyzerTest {

    private static boolean[][] cycle(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            a[i][j] = true;
            a[j][i] = true;
        }
        return a;
    }

    private static boolean[][] complete(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    a[i][j] = true;
                }
            }
        }
        return a;
    }

    /** C5 with descending edge correlations; weakest edge is 4-0 at 0.55. */
    private static double[][] weightedC5() {
        int n = 5;
        double[][] m = new double[n][n];
        double[] w = {0.9, 0.8, 0.7, 0.6, 0.55};
        for (int i = 0; i < n; i++) {
            m[i][i] = 1.0;
            int j = (i + 1) % n;
            m[i][j] = w[i];
            m[j][i] = w[i];
        }
        return m;
    }

    @Test
    void analyzeAll_MatchesIndividualAnalyze_InOrder() {
        List<GraphInput> inputs = List.of(
                GraphInput.fromAdjacency(complete(4)),
                GraphInput.fromAdjacency(cycle(5)),
                GraphInput.fromAdjacency(cycle(6)));
        List<AnalysisResult> batch = BatchAnalyzer.analyzeAll(inputs);

        assertEquals(3, batch.size());
        for (int i = 0; i < inputs.size(); i++) {
            assertEquals(ComparabilityAnalyzer.analyze(inputs.get(i)), batch.get(i));
        }
    }

    @Test
    void analyzeAllParallel_EqualsSequential_OnLargeBatch() {
        List<GraphInput> inputs = new ArrayList<>();
        for (int k = 0; k < 40; k++) {
            int n = 3 + (k % 6);
            inputs.add(GraphInput.fromAdjacency((k % 2 == 0) ? complete(n) : cycle(n)));
        }
        assertTrue(inputs.size() >= BatchAnalyzer.MIN_PARALLEL_BATCH, "batch exercises the parallel path");
        assertEquals(BatchAnalyzer.analyzeAll(inputs), BatchAnalyzer.analyzeAllParallel(inputs));
    }

    @Test
    void analyzeAllParallel_SmallBatch_StillCorrect() {
        List<GraphInput> inputs = List.of(GraphInput.fromAdjacency(cycle(5)));
        assertTrue(inputs.size() < BatchAnalyzer.MIN_PARALLEL_BATCH);
        assertEquals(BatchAnalyzer.analyzeAll(inputs), BatchAnalyzer.analyzeAllParallel(inputs));
    }

    @Test
    void thresholdSweep_RaisingThreshold_BreaksTheOddCycle() {
        double[][] m = weightedC5();
        double[] thresholds = {0.50, 0.56};
        List<AnalysisResult> results = BatchAnalyzer.thresholdSweep(m, thresholds);

        assertEquals(2, results.size());
        // below the weakest edge: full C5, not a comparability graph
        assertFalse(results.get(0).isComparability());
        // above the weakest edge (0.55): that edge drops, the cycle opens into a path
        assertTrue(results.get(1).isComparability());

        // matches building each input by hand
        for (int i = 0; i < thresholds.length; i++) {
            assertEquals(ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(m, thresholds[i])),
                    results.get(i));
        }
    }

    @Test
    void thresholdSweepParallel_EqualsSequential() {
        double[][] m = weightedC5();
        double[] thresholds = new double[20];
        for (int i = 0; i < thresholds.length; i++) {
            thresholds[i] = 0.30 + i * 0.02;
        }
        assertEquals(BatchAnalyzer.thresholdSweep(m, thresholds),
                BatchAnalyzer.thresholdSweepParallel(m, thresholds));
    }

    @Test
    void emptyBatch_ReturnsEmpty() {
        assertTrue(BatchAnalyzer.analyzeAll(List.of()).isEmpty());
        assertTrue(BatchAnalyzer.analyzeAllParallel(List.of()).isEmpty());
    }

    @Test
    void result_IsUnmodifiable() {
        List<AnalysisResult> r = BatchAnalyzer.analyzeAll(List.of(GraphInput.fromAdjacency(complete(3))));
        assertThrows(UnsupportedOperationException.class, () -> r.add(null));
    }

    @Test
    void nullArguments_Throw() {
        assertThrows(NullPointerException.class, () -> BatchAnalyzer.analyzeAll(null));
        assertThrows(NullPointerException.class, () -> BatchAnalyzer.analyzeAllParallel(null));
        assertThrows(NullPointerException.class, () -> BatchAnalyzer.thresholdSweep(weightedC5(), null));
    }
}
