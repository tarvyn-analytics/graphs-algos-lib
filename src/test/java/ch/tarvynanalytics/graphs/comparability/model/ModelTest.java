package ch.tarvynanalytics.graphs.comparability.model;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelTest {

    private static GraphView triangle() {
        return new GraphView(
                List.of(new NodeView(0, "a"), new NodeView(1, "b"), new NodeView(2, "c")),
                List.of(new EdgeView(0, 1), new EdgeView(0, 2), new EdgeView(1, 2)));
    }

    @Test
    void graphView_OrderAndSize() {
        GraphView g = triangle();
        assertEquals(3, g.order());
        assertEquals(3, g.size());
    }

    @Test
    void graphView_IsDeeplyImmutable() {
        GraphView g = triangle();
        assertThrows(UnsupportedOperationException.class, () -> g.nodes().clear());
        assertThrows(UnsupportedOperationException.class, () -> g.edges().clear());
    }

    @Test
    void moduleView_CardinalityAndCopy() {
        ModuleView m = new ModuleView(0, ModuleType.CLIQUE, List.of(1, 2, 3));
        assertEquals(3, m.cardinality());
        assertThrows(UnsupportedOperationException.class, () -> m.memberNodeIds().clear());
    }

    @Test
    void failureCycle_LengthAndAccessors() {
        FailureCycle f = new FailureCycle(0, List.of(0, 1, 2, 3, 4),
                List.of("0", "1", "2", "3", "4"), 0.55, 4, 0);
        assertEquals(5, f.length());
        assertEquals(0.55, f.weakestCorrelation());
        assertEquals(4, f.weakestEdgeSource());
        assertEquals(0, f.weakestEdgeTarget());
    }

    private static ChordalityView chordal() {
        return new ChordalityView(true, List.of(2, 1, 0), List.of("c", "b", "a"),
                List.of(), List.of(), List.of());
    }

    @Test
    void analysisResult_FailureOptionalAndImmutability() {
        FactorGraphLevelView level = new FactorGraphLevelView(0, triangle(), List.of(), triangle());
        AnalysisResult ok = new AnalysisResult(true, triangle(), List.of(level), BigInteger.valueOf(6),
                null, chordal());
        assertTrue(ok.isComparability());
        assertTrue(ok.failure().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> ok.levels().clear());

        FailureCycle f = new FailureCycle(0, List.of(0, 1, 2), List.of("0", "1", "2"), Double.NaN, -1, -1);
        AnalysisResult bad = new AnalysisResult(false, triangle(), List.of(level), BigInteger.ZERO,
                f, chordal());
        assertEquals(f, bad.failure().orElseThrow());
    }

    @Test
    void chordalityView_AccessorsAndImmutability() {
        ChordalityView yes = chordal();
        assertTrue(yes.isChordal());
        assertEquals(0, yes.fillInCount());
        assertThrows(UnsupportedOperationException.class, () -> yes.perfectEliminationOrder().clear());

        ChordalityView no = new ChordalityView(false, List.of(0, 1, 2, 3), List.of("0", "1", "2", "3"),
                List.of(0, 1, 2, 3), List.of("0", "1", "2", "3"), List.of(new EdgeView(0, 2)));
        assertFalse(no.isChordal());
        assertEquals(1, no.fillInCount());
        assertEquals(new EdgeView(0, 2), no.fillInEdges().get(0));
        assertThrows(UnsupportedOperationException.class, () -> no.fillInEdges().clear());
    }

    @Test
    void robustEdge_SupportAndImmutability() {
        RobustEdge e = new RobustEdge(0, 1, "a", "b", List.of(0, 2));
        assertEquals(2, e.support());
        assertEquals(List.of(0, 2), e.estimatorIndices());
        assertThrows(UnsupportedOperationException.class, () -> e.estimatorIndices().clear());
    }

    @Test
    void crossEstimatorReport_AccessorsViewsAndImmutability() {
        RobustEdge shared = new RobustEdge(0, 1, "a", "b", List.of(0, 1));
        RobustEdge onlyP = new RobustEdge(0, 2, "a", "c", List.of(0));
        RobustEdge onlyS = new RobustEdge(1, 2, "b", "c", List.of(1));
        CrossEstimatorReport r = new CrossEstimatorReport(
                List.of("pearson", "spearman"), 2,
                List.of(List.of(1.0, 0.5), List.of(0.5, 1.0)),
                List.of(shared, onlyP, onlyS));

        assertEquals(2, r.estimatorCount());
        assertEquals(0.5, r.jaccard(0, 1));
        assertEquals(List.of(shared), r.stableCore());
        assertEquals(List.of(onlyP, onlyS), r.uniqueEdges());
        assertEquals(List.of(onlyP), r.uniqueTo(0));
        assertEquals(List.of(onlyS), r.uniqueTo(1));
        assertThrows(UnsupportedOperationException.class, () -> r.edges().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.jaccard().get(0).clear());
    }

    @Test
    void records_EqualityAndToString() {
        assertEquals(new NodeView(0, "a"), new NodeView(0, "a"));
        assertNotEquals(new NodeView(0, "a"), new NodeView(1, "a"));
        assertEquals(new EdgeView(0, 1), new EdgeView(0, 1));
        assertTrue(new NodeView(7, "x").toString().contains("7"));
        assertEquals(ModuleType.MIN_STABLE, ModuleType.valueOf("MIN_STABLE"));
        assertEquals(4, ModuleType.values().length);
    }
}
