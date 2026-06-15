package ch.tarvynanalytics.graphs.comparability.model;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void analysisResult_FailureOptionalAndImmutability() {
        FactorGraphLevelView level = new FactorGraphLevelView(0, triangle(), List.of(), triangle());
        AnalysisResult ok = new AnalysisResult(true, triangle(), List.of(level), BigInteger.valueOf(6), null);
        assertTrue(ok.isComparability());
        assertTrue(ok.failure().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> ok.levels().clear());

        FailureCycle f = new FailureCycle(0, List.of(0, 1, 2), List.of("0", "1", "2"), Double.NaN, -1, -1);
        AnalysisResult bad = new AnalysisResult(false, triangle(), List.of(level), BigInteger.ZERO, f);
        assertEquals(f, bad.failure().orElseThrow());
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
