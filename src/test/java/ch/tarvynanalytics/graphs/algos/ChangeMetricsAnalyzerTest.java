package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.ChangeMetrics;
import ch.tarvynanalytics.graphs.algos.model.PairChange;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle A (§8.1 A1+A2 of the S3 change-metric spec) for the D1 metric core:
 * ground truth is hand arithmetic written as literals in this test, never the
 * implementation's own output. Mirrors {@code s3_change_metric_probe.py} /
 * {@code replay_cluster_signal.py} semantics.
 */
class ChangeMetricsAnalyzerTest {

    // ---- A1: weighted_change on a tiny 3x3, hand-computed (spec §1.5 / §8.1-A1) ----

    // Pairs (0,1),(0,2),(1,2): C_a -> C_b deltas 0.40, 0.00, 0.30 => weighted = 0.70/3.
    private static final double[][] C_A = {
            {1.00, 0.10, 0.20},
            {0.10, 1.00, 0.30},
            {0.20, 0.30, 1.00}
    };
    private static final double[][] C_B = {
            {1.00, 0.50, 0.20},
            {0.50, 1.00, 0.60},
            {0.20, 0.60, 1.00}
    };

    @Test
    void weightedChange_HandComputedThreeByThree_MatchesPointSevenOverThree() {
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(C_A, C_B, 0.5);
        assertEquals(0.70 / 3, m.weightedChange(), 1e-12);
    }

    @Test
    void weightedChange_NaNVariant_UsesIntersectionDenominator() {
        // pair (0,2) NaN in C_b => V = {(0,1),(1,2)}; weighted = (0.40 + 0.30) / 2 = 0.35 exact.
        double[][] cb = {
                {1.00, 0.50, Double.NaN},
                {0.50, 1.00, 0.60},
                {Double.NaN, 0.60, 1.00}
        };
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(C_A, cb, 0.5);
        assertEquals(0.35, m.weightedChange(), 1e-12);
    }

    @Test
    void weightedChange_AllPairsInvalidInOneWindow_IsNaN() {
        double[][] allNaN = {
                {1.00, Double.NaN, Double.NaN},
                {Double.NaN, 1.00, Double.NaN},
                {Double.NaN, Double.NaN, 1.00}
        };
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(allNaN, C_B, 0.5);
        assertTrue(Double.isNaN(m.weightedChange()));
    }

    @Test
    void weightedChange_PositiveAndNegativeInfinity_TreatedAsInvalidLikeNaN() {
        double[][] cb = {
                {1.00, 0.50, Double.POSITIVE_INFINITY},
                {0.50, 1.00, 0.60},
                {Double.POSITIVE_INFINITY, 0.60, 1.00}
        };
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(C_A, cb, 0.5);
        assertEquals(0.35, m.weightedChange(), 1e-12);

        double[][] ca = {
                {1.00, 0.10, Double.NEGATIVE_INFINITY},
                {0.10, 1.00, 0.30},
                {Double.NEGATIVE_INFINITY, 0.30, 1.00}
        };
        ChangeMetrics m2 = ChangeMetricsAnalyzer.analyze(ca, C_B, 0.5);
        assertEquals(0.35, m2.weightedChange(), 1e-12);
    }

    // ---- A2: secondary features, hand-computed (spec §3 / §8.1-A2) ----

    @Test
    void densityLevel_UsesAllPairsDenominator_NotValidPairs() {
        // C_b has exactly one edge above threshold among the 3 pairs: (1,2)=0.60 -> 1/3.
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(C_A, C_B, 0.5);
        assertEquals(1.0 / 3, m.densityLevel(), 1e-12);
    }

    @Test
    void densityLevel_InvalidPairExcludedFromEdges_ButDenominatorStaysAllPairs() {
        // (0,2) NaN in C_b: excluded from the edge count, but the |P|=3 denominator is unchanged.
        double[][] cb = {
                {1.00, 0.50, Double.NaN},
                {0.50, 1.00, 0.60},
                {Double.NaN, 0.60, 1.00}
        };
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(C_A, cb, 0.5);
        assertEquals(1.0 / 3, m.densityLevel(), 1e-12);
    }

    @Test
    void edgeXor_UsesValidPairDenominator_LikeWeightedChange() {
        // edges_now (|r|>0.5 in C_b): (0,1)=0.50? strict '>' so 0.50 is NOT an edge; (1,2)=0.60 is.
        // edges_before (C_a): none above 0.5. So only (1,2) flips edge membership: xor = 1/3.
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(C_A, C_B, 0.5);
        assertEquals(1.0 / 3, m.edgeXor(), 1e-12);
    }

    @Test
    void edgeXor_AllPairsInvalidInOneWindow_IsNaN() {
        double[][] allNaN = {
                {1.00, Double.NaN, Double.NaN},
                {Double.NaN, 1.00, Double.NaN},
                {Double.NaN, Double.NaN, 1.00}
        };
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(allNaN, C_B, 0.5);
        assertTrue(Double.isNaN(m.edgeXor()));
    }

    @Test
    void densityLevel_DegenerateZeroOrder_IsNaN() {
        double[][] empty = new double[0][0];
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(empty, empty, 0.5);
        assertTrue(Double.isNaN(m.weightedChange()));
        assertTrue(Double.isNaN(m.densityLevel()));
        assertTrue(Double.isNaN(m.edgeXor()));
        assertEquals(0, m.nComponents());
        assertTrue(Double.isNaN(m.largestComponentFraction()));
        assertEquals(List.of(), m.componentSizes());
    }

    // ---- cluster signal: replay_cluster_signal.py smoke values, ported verbatim ----

    @Test
    void clusterSignal_TwoDisjointEdgesAmongFiveNames_ThreeComponents() {
        // 5 names, edges (0,1) and (2,3); name 4 isolated => n_components=3, lcf=0.4, sizes=[2,2,1].
        double[][] c = identity(5);
        c[0][1] = c[1][0] = 0.9;
        c[2][3] = c[3][2] = 0.9;
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(c, c, 0.5);
        assertEquals(3, m.nComponents());
        assertEquals(0.4, m.largestComponentFraction(), 1e-12);
        assertEquals(List.of(2, 2, 1), m.componentSizes());
    }

    @Test
    void clusterSignal_FullyFusedChain_OneComponent() {
        // 5 names, a connected chain (0,1)-(1,2)-(2,3)-(3,4) fuses all into one component.
        double[][] c = identity(5);
        c[0][1] = c[1][0] = 0.9;
        c[1][2] = c[2][1] = 0.9;
        c[2][3] = c[3][2] = 0.9;
        c[3][4] = c[4][3] = 0.9;
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(c, c, 0.5);
        assertEquals(1, m.nComponents());
        assertEquals(1.0, m.largestComponentFraction(), 1e-12);
        assertEquals(List.of(5), m.componentSizes());
    }

    @Test
    void clusterSignal_NoEdges_EveryNameItsOwnComponent() {
        double[][] c = identity(5);
        ChangeMetrics m = ChangeMetricsAnalyzer.analyze(c, c, 0.5);
        assertEquals(5, m.nComponents());
        assertEquals(0.2, m.largestComponentFraction(), 1e-12);
        assertEquals(List.of(1, 1, 1, 1, 1), m.componentSizes());
    }

    // ---- topContributors: hand-computed attribution (H1, GAL-29) ----
    // C_A -> C_B per-pair |Δr|: (0,1)=0.40, (1,2)=0.30, (0,2)=0.00.

    @Test
    void topContributors_RanksAllValidPairsByAbsDeltaDescending() {
        List<PairChange> top = ChangeMetricsAnalyzer.topContributors(C_A, C_B, 3);
        assertEquals(3, top.size());
        assertEquals(new PairChange(0, 1, 0.40), top.get(0));
        assertEquals(new PairChange(1, 2, 0.30), top.get(1));
        assertEquals(new PairChange(0, 2, 0.00), top.get(2));
    }

    @Test
    void topContributors_CapsToK_WhenMoreValidPairsExist() {
        List<PairChange> top = ChangeMetricsAnalyzer.topContributors(C_A, C_B, 2);
        assertEquals(List.of(new PairChange(0, 1, 0.40), new PairChange(1, 2, 0.30)), top);
    }

    @Test
    void topContributors_ReturnsAllPairs_WhenKExceedsValidPairCount() {
        List<PairChange> top = ChangeMetricsAnalyzer.topContributors(C_A, C_B, 99);
        assertEquals(3, top.size());
    }

    @Test
    void topContributors_KZeroOrNegative_IsEmpty() {
        assertEquals(List.of(), ChangeMetricsAnalyzer.topContributors(C_A, C_B, 0));
        assertEquals(List.of(), ChangeMetricsAnalyzer.topContributors(C_A, C_B, -1));
    }

    @Test
    void topContributors_BreaksTiesByLowerIndexThenLowerJ() {
        // prev: all off-diagonals 0; cur: (0,1)=(0,2)=(1,2)=0.5, rest 0 => three pairs tie at |Δr|=0.5.
        double[][] prev = identity(4);
        double[][] cur = identity(4);
        cur[0][1] = cur[1][0] = 0.5;
        cur[0][2] = cur[2][0] = 0.5;
        cur[1][2] = cur[2][1] = 0.5;
        List<PairChange> top = ChangeMetricsAnalyzer.topContributors(prev, cur, 3);
        assertEquals(List.of(new PairChange(0, 1, 0.5), new PairChange(0, 2, 0.5), new PairChange(1, 2, 0.5)), top);
    }

    @Test
    void topContributors_ExcludesPairsInvalidInEitherWindow() {
        // (0,2) NaN in cur => only (0,1)=0.40 and (1,2)=0.30 are eligible.
        double[][] cb = {
                {1.00, 0.50, Double.NaN},
                {0.50, 1.00, 0.60},
                {Double.NaN, 0.60, 1.00}
        };
        List<PairChange> top = ChangeMetricsAnalyzer.topContributors(C_A, cb, 5);
        assertEquals(List.of(new PairChange(0, 1, 0.40), new PairChange(1, 2, 0.30)), top);
    }

    @Test
    void topContributors_NoValidPairs_IsEmpty() {
        double[][] allNaN = {
                {1.00, Double.NaN, Double.NaN},
                {Double.NaN, 1.00, Double.NaN},
                {Double.NaN, Double.NaN, 1.00}
        };
        assertEquals(List.of(), ChangeMetricsAnalyzer.topContributors(allNaN, C_B, 3));
    }

    @Test
    void topContributors_NullMatrix_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChangeMetricsAnalyzer.topContributors(null, C_B, 3));
        assertThrows(IllegalArgumentException.class, () -> ChangeMetricsAnalyzer.topContributors(C_A, null, 3));
    }

    @Test
    void topContributors_NonSquare_ThrowsInvalidInputException() {
        double[][] ragged = {{1.0, 0.1}, {0.1}};
        assertThrows(InvalidInputException.class, () -> ChangeMetricsAnalyzer.topContributors(ragged, C_B, 3));
    }

    @Test
    void topContributors_MismatchedDimensions_ThrowsInvalidInputException() {
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> ChangeMetricsAnalyzer.topContributors(C_A, identity(2), 3));
        assertTrue(ex.getMessage().contains("[3x3]"));
        assertTrue(ex.getMessage().contains("[2x2]"));
    }

    // ---- validation ----

    @Test
    void analyze_NullPrevious_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChangeMetricsAnalyzer.analyze(null, C_B, 0.5));
    }

    @Test
    void analyze_NullCurrent_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ChangeMetricsAnalyzer.analyze(C_A, null, 0.5));
    }

    @Test
    void analyze_NonSquarePrevious_ThrowsInvalidInputException() {
        double[][] ragged = {{1.0, 0.1}, {0.1}};
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> ChangeMetricsAnalyzer.analyze(ragged, C_B, 0.5));
        assertTrue(ex.getMessage().contains("previous"));
    }

    @Test
    void analyze_NonSquareCurrent_ThrowsInvalidInputException() {
        double[][] ragged = {{1.0, 0.1}, {0.1}};
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> ChangeMetricsAnalyzer.analyze(C_A, ragged, 0.5));
        assertTrue(ex.getMessage().contains("current"));
    }

    @Test
    void analyze_MismatchedDimensions_ThrowsInvalidInputException() {
        double[][] small = identity(2);
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> ChangeMetricsAnalyzer.analyze(C_A, small, 0.5));
        assertTrue(ex.getMessage().contains("[3x3]"));
        assertTrue(ex.getMessage().contains("[2x2]"));
    }

    // ---- exhaustive sweep: independent double-loop reference re-implementation of §1.1 ----

    @Test
    void weightedChange_ExhaustiveThreeVariableSweep_MatchesIndependentReferenceImplementation() {
        double[] values = {-1.0, -0.6, 0.0, 0.6, 1.0};
        for (double a01 : values) {
            for (double a02 : values) {
                for (double a12 : values) {
                    double[][] prev = matrixOf(a01, a02, a12);
                    for (double b01 : values) {
                        for (double b02 : values) {
                            for (double b12 : values) {
                                double[][] curr = matrixOf(b01, b02, b12);
                                ChangeMetrics m = ChangeMetricsAnalyzer.analyze(prev, curr, 0.5);
                                double expected = referenceWeightedChange(prev, curr);
                                if (Double.isNaN(expected)) {
                                    assertTrue(Double.isNaN(m.weightedChange()));
                                } else {
                                    assertEquals(expected, m.weightedChange(), 1e-12);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Dead-simple double-loop re-implementation of spec §1.1, structurally independent of
     * {@link ChangeMetricsEngine} (no shared helper code) -- the exhaustive-oracle idiom.
     */
    private static double referenceWeightedChange(double[][] prev, double[][] curr) {
        int m = curr.length;
        double sum = 0.0;
        int count = 0;
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < m; j++) {
                if (j <= i) {
                    continue;
                }
                double a = prev[i][j];
                double b = curr[i][j];
                if (Double.isFinite(a) && Double.isFinite(b)) {
                    sum += Math.abs(b - a);
                    count++;
                }
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double[][] matrixOf(double r01, double r02, double r12) {
        return new double[][] {
                {1.0, r01, r02},
                {r01, 1.0, r12},
                {r02, r12, 1.0}
        };
    }

    private static double[][] identity(int n) {
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            m[i][i] = 1.0;
        }
        return m;
    }
}
