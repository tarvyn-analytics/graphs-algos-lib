package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.StructuralBalanceView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuralBalanceAnalyzerTest {

    // Triangle with two negative edges (0-2, 1-2): balanced -> camps {0,1} vs {2}.
    private static final double[][] BALANCED = {
            {1.0, 0.9, -0.9},
            {0.9, 1.0, -0.9},
            {-0.9, -0.9, 1.0}
    };

    // Triangle with one negative edge (0-2): an odd negative cycle -> unbalanced.
    private static final double[][] UNBALANCED = {
            {1.0, 0.9, -0.9},
            {0.9, 1.0, 0.9},
            {-0.9, 0.9, 1.0}
    };

    @Test
    void nullInput_Throws() {
        assertThrows(IllegalArgumentException.class, () -> StructuralBalanceAnalyzer.analyze(null));
    }

    @Test
    void twoNegativeTriangle_IsBalanced_IntoTwoBlocs() {
        StructuralBalanceView b = StructuralBalanceAnalyzer.analyze(GraphInput.fromCorrelation(BALANCED, 0.5));
        assertTrue(b.isBalanced());
        assertEquals(2, b.negativeEdgeCount());
        assertEquals(List.of(0, 1), b.verticesInCamp(0));
        assertEquals(List.of(2), b.verticesInCamp(1));
        assertTrue(b.frustratedCycle().isEmpty());
    }

    @Test
    void oneNegativeTriangle_IsUnbalanced_AndReportsAnOddCycle() {
        StructuralBalanceView b = StructuralBalanceAnalyzer.analyze(GraphInput.fromCorrelation(UNBALANCED, 0.5));
        assertFalse(b.isBalanced());
        assertEquals(1, b.negativeEdgeCount());
        assertEquals(3, b.frustratedCycle().size(), "the whole triangle is the frustrated cycle");
        assertTrue(b.camp().isEmpty());
    }

    @Test
    void adjacencyInput_HasNoSigns_AndIsTriviallyBalanced() {
        // A 5-cycle (odd hole) is NOT a comparability graph, but with no signs it is trivially balanced.
        boolean[][] c5 = new boolean[5][5];
        for (int i = 0; i < 5; i++) {
            int j = (i + 1) % 5;
            c5[i][j] = true;
            c5[j][i] = true;
        }
        StructuralBalanceView b = StructuralBalanceAnalyzer.analyze(GraphInput.fromAdjacency(c5));
        assertTrue(b.isBalanced(), "all-positive graph is balanced");
        assertEquals(0, b.negativeEdgeCount());
    }
}
