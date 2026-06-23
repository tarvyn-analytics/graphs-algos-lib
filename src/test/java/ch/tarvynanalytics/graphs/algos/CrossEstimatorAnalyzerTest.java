package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.CrossEstimatorReport;
import ch.tarvynanalytics.graphs.algos.model.RobustEdge;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrossEstimatorAnalyzerTest {

    // Four variables w,x,y,z -> six pairs. Magnitudes chosen so the top-3 sets are hand-known.
    // Estimator A top-3 by |corr|: (0,1)=.9, (2,3)=.8, (0,2)=.7   (then (1,3)=.3, (0,3)=.2, (1,2)=.1)
    private static final double[][] A = {
            {1.0, 0.9, 0.7, 0.2},
            {0.9, 1.0, 0.1, 0.3},
            {0.7, 0.1, 1.0, 0.8},
            {0.2, 0.3, 0.8, 1.0}
    };
    // Estimator B top-3 by |corr|: (0,1)=.95, (2,3)=.85, (1,3)=.6  (then (0,2)=.4, (0,3)=.3, (1,2)=.2)
    private static final double[][] B = {
            {1.0, 0.95, 0.4, 0.3},
            {0.95, 1.0, 0.2, 0.6},
            {0.4, 0.2, 1.0, 0.85},
            {0.3, 0.6, 0.85, 1.0}
    };
    private static final String[] LABELS = {"w", "x", "y", "z"};

    private static List<EstimatorMatrix> ab() {
        return List.of(EstimatorMatrix.of("a", A), EstimatorMatrix.of("b", B));
    }

    @Test
    void analyze_NamesTopKAndDiagonal() {
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(ab(), LABELS, 3);
        assertEquals(2, r.estimatorCount());
        assertEquals(List.of("a", "b"), r.estimatorNames());
        assertEquals(3, r.topK());
        assertEquals(1.0, r.jaccard(0, 0), "an estimator overlaps itself fully");
        assertEquals(1.0, r.jaccard(1, 1));
    }

    @Test
    void analyze_JaccardIsTheTopKOverlap_AndSymmetric() {
        // A top-3 = {wx, yz, wy}; B top-3 = {wx, yz, xz}; intersection {wx, yz}=2, union=4 -> 0.5.
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(ab(), LABELS, 3);
        assertEquals(0.5, r.jaccard(0, 1));
        assertEquals(r.jaccard(0, 1), r.jaccard(1, 0), "Jaccard is symmetric");
    }

    @Test
    void analyze_StableCore_IsTheEdgesInEveryTopK() {
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(ab(), LABELS, 3);
        List<RobustEdge> core = r.stableCore();
        assertEquals(2, core.size());
        // ordered by descending support then endpoints: (0,1) then (2,3)
        assertEdge(core.get(0), 0, 1, "w", "x", 2);
        assertEdge(core.get(1), 2, 3, "y", "z", 2);
    }

    @Test
    void analyze_UniqueEdges_AreEstimatorSpecific() {
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(ab(), LABELS, 3);
        // A-only: (0,2)=wy ; B-only: (1,3)=xz
        List<RobustEdge> uniqueA = r.uniqueTo(0);
        List<RobustEdge> uniqueB = r.uniqueTo(1);
        assertEquals(1, uniqueA.size());
        assertEdge(uniqueA.get(0), 0, 2, "w", "y", 1);
        assertEquals(List.of(0), uniqueA.get(0).estimatorIndices());
        assertEquals(1, uniqueB.size());
        assertEdge(uniqueB.get(0), 1, 3, "x", "z", 1);
        assertEquals(List.of(1), uniqueB.get(0).estimatorIndices());
        assertEquals(2, r.uniqueEdges().size(), "two estimator-unique edges in all");
    }

    @Test
    void analyze_Edges_AreOrderedBySupportThenEndpoints() {
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(ab(), LABELS, 3);
        // union = {(0,1),(2,3)} support 2, then {(0,2),(1,3)} support 1
        List<RobustEdge> e = r.edges();
        assertEquals(4, e.size());
        assertEquals(2, e.get(0).support());
        assertEquals(2, e.get(1).support());
        assertEquals(1, e.get(2).support());
        assertEquals(1, e.get(3).support());
        assertEdge(e.get(0), 0, 1, "w", "x", 2);
        assertEdge(e.get(2), 0, 2, "w", "y", 1);
        assertEdge(e.get(3), 1, 3, "x", "z", 1);
    }

    @Test
    void analyze_DefaultLabels_AreVariableIndices() {
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(ab(), 3);
        assertEdge(r.stableCore().get(0), 0, 1, "0", "1", 2);
    }

    @Test
    void analyze_ThreeEstimators_StableCoreNeedsAllThree() {
        // C top-3 = {wx, wy, xz} (no yz); A and B share {wx, yz}, so only wx is in all three.
        double[][] c = {
                {1.0, 0.99, 0.95, 0.1},
                {0.99, 1.0, 0.1, 0.5},
                {0.95, 0.1, 1.0, 0.05},
                {0.1, 0.5, 0.05, 1.0}
        };
        List<EstimatorMatrix> three = List.of(
                EstimatorMatrix.of("a", A), EstimatorMatrix.of("b", B), EstimatorMatrix.of("c", c));
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(three, LABELS, 3);
        List<RobustEdge> core = r.stableCore();
        assertEquals(1, core.size(), "only w-x is in every estimator's top-3");
        assertEdge(core.get(0), 0, 1, "w", "x", 3);
    }

    @Test
    void analyze_TopKAbovePairCount_IsClampedToAllPairs() {
        // 4 vars -> 6 pairs; asking for 100 clamps to 6, so both sets are all pairs: full overlap.
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(ab(), LABELS, 100);
        assertEquals(6, r.topK());
        assertEquals(1.0, r.jaccard(0, 1), "identical (all-pairs) sets overlap fully");
        assertEquals(6, r.stableCore().size());
        assertTrue(r.uniqueEdges().isEmpty());
    }

    @Test
    void analyze_TieAtTheCut_BreaksDeterministicallyByEndpoints() {
        // (0,1) and (0,2) both 0.5; top-1 must deterministically take the lexicographically smaller (0,1).
        double[][] tie = {
                {1.0, 0.5, 0.5},
                {0.5, 1.0, 0.3},
                {0.5, 0.3, 1.0}
        };
        List<EstimatorMatrix> two = List.of(EstimatorMatrix.of("a", tie), EstimatorMatrix.of("b", tie));
        CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(two, 1);
        assertEquals(1, r.edges().size());
        assertEdge(r.edges().get(0), 0, 1, "0", "1", 2);
    }

    @Test
    void analyze_NullEstimators_Throws() {
        assertThrows(IllegalArgumentException.class, () -> CrossEstimatorAnalyzer.analyze(null, 3));
    }

    @Test
    void analyze_FewerThanTwoEstimators_Throws() {
        List<EstimatorMatrix> one = List.of(EstimatorMatrix.of("a", A));
        assertThrows(InvalidInputException.class, () -> CrossEstimatorAnalyzer.analyze(one, 3));
    }

    @Test
    void analyze_NonPositiveTopK_Throws() {
        assertThrows(InvalidInputException.class, () -> CrossEstimatorAnalyzer.analyze(ab(), 0));
    }

    @Test
    void analyze_MismatchedOrders_Throws() {
        double[][] small = {{1.0, 0.5}, {0.5, 1.0}};
        List<EstimatorMatrix> mixed = List.of(EstimatorMatrix.of("a", A), EstimatorMatrix.of("b", small));
        assertThrows(InvalidInputException.class, () -> CrossEstimatorAnalyzer.analyze(mixed, 1));
    }

    @Test
    void analyze_WrongLabelCount_Throws() {
        assertThrows(InvalidInputException.class,
                () -> CrossEstimatorAnalyzer.analyze(ab(), new String[]{"only-one"}, 3));
    }

    @Test
    void analyze_SingleVariable_Throws() {
        double[][] one = {{1.0}};
        List<EstimatorMatrix> trivial = List.of(EstimatorMatrix.of("a", one), EstimatorMatrix.of("b", one));
        assertThrows(InvalidInputException.class, () -> CrossEstimatorAnalyzer.analyze(trivial, 1));
    }

    private static void assertEdge(RobustEdge e, int source, int target,
                                   String sourceLabel, String targetLabel, int support) {
        assertEquals(source, e.source());
        assertEquals(target, e.target());
        assertEquals(sourceLabel, e.sourceLabel());
        assertEquals(targetLabel, e.targetLabel());
        assertEquals(support, e.support());
    }
}
