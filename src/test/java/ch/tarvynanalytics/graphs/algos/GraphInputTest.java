package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;
import ch.tarvynanalytics.graphs.algos.model.EdgeView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphInputTest {

    private static List<EdgeView> edges(GraphInput input) {
        // Round-trip through the analyzer's input graph view to read the built edges.
        AnalysisResult r = ComparabilityAnalyzer.analyze(input);
        return r.inputGraph().edges();
    }

    @Test
    void fromCorrelation_AboveThreshold_CreatesEdge() {
        double[][] m = {
                {1.0, 0.8},
                {0.8, 1.0}
        };
        assertEquals(List.of(new EdgeView(0, 1)), edges(GraphInput.fromCorrelation(m, 0.5)));
    }

    @Test
    void fromCorrelation_AtThreshold_CreatesNoEdge() {
        double[][] m = {
                {1.0, 0.5},
                {0.5, 1.0}
        };
        // |0.5| is not > |0.5|, so no edge (mirrors the thesis prototype).
        assertTrue(edges(GraphInput.fromCorrelation(m, 0.5)).isEmpty());
    }

    @Test
    void fromCorrelation_NegativeCorrelationAboveThreshold_CreatesEdge() {
        double[][] m = {
                {1.0, -0.9},
                {-0.9, 1.0}
        };
        assertEquals(List.of(new EdgeView(0, 1)), edges(GraphInput.fromCorrelation(m, 0.5)));
    }

    @Test
    void fromCorrelation_AsymmetricInput_EdgeWhenEitherDirectionExceeds() {
        double[][] m = {
                {1.0, 0.9},
                {0.0, 1.0}
        };
        assertEquals(List.of(new EdgeView(0, 1)), edges(GraphInput.fromCorrelation(m, 0.5)));
    }

    @Test
    void fromCorrelation_NonSquare_Throws() {
        double[][] m = {
                {1.0, 0.0, 0.0},
                {0.0, 1.0}
        };
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> GraphInput.fromCorrelation(m, 0.5));
        assertTrue(ex.getMessage().contains("[2]") && ex.getMessage().contains("[3]"));
    }

    @Test
    void fromCorrelation_NonFinite_Throws() {
        double[][] m = {
                {1.0, Double.NaN},
                {Double.NaN, 1.0}
        };
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> GraphInput.fromCorrelation(m, 0.5));
        assertTrue(ex.getMessage().contains("[0][1]"));
    }

    @Test
    void fromCorrelation_NullMatrix_Throws() {
        assertThrows(InvalidInputException.class, () -> GraphInput.fromCorrelation(null, 0.5));
    }

    @Test
    void fromCorrelation_NullRow_Throws() {
        double[][] m = new double[2][];
        m[0] = new double[]{1.0, 0.0};
        m[1] = null;
        assertThrows(InvalidInputException.class, () -> GraphInput.fromCorrelation(m, 0.5));
    }

    @Test
    void labels_WrongLength_Throws() {
        double[][] m = {
                {1.0, 0.0},
                {0.0, 1.0}
        };
        InvalidInputException ex = assertThrows(InvalidInputException.class,
                () -> GraphInput.fromCorrelation(m, 0.5, new String[]{"only-one"}));
        assertTrue(ex.getMessage().contains("[1]") && ex.getMessage().contains("[2]"));
    }

    @Test
    void labels_Provided_AreUsedInResult() {
        double[][] m = {
                {1.0, 0.8},
                {0.8, 1.0}
        };
        AnalysisResult r = ComparabilityAnalyzer.analyze(
                GraphInput.fromCorrelation(m, 0.5, new String[]{"AAPL", "MSFT"}));
        assertEquals("AAPL", r.inputGraph().nodes().get(0).label());
        assertEquals("MSFT", r.inputGraph().nodes().get(1).label());
    }

    @Test
    void labels_Default_AreIndexStrings() {
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(new boolean[3][3]));
        assertEquals("0", r.inputGraph().nodes().get(0).label());
        assertEquals("2", r.inputGraph().nodes().get(2).label());
    }

    @Test
    void fromAdjacency_BuildsSymmetricEdges() {
        boolean[][] a = new boolean[3][3];
        a[0][2] = true; // only one direction set
        assertEquals(List.of(new EdgeView(0, 2)), edges(GraphInput.fromAdjacency(a)));
    }

    @Test
    void fromAdjacency_NonSquare_Throws() {
        boolean[][] a = new boolean[2][];
        a[0] = new boolean[2];
        a[1] = new boolean[1];
        assertThrows(InvalidInputException.class, () -> GraphInput.fromAdjacency(a));
    }

    @Test
    void fromAdjacency_NullMatrix_Throws() {
        assertThrows(InvalidInputException.class, () -> GraphInput.fromAdjacency((boolean[][]) null));
    }

    @Test
    void diagonalIsIgnored_NoSelfLoops() {
        boolean[][] a = new boolean[2][2];
        a[0][0] = true;
        a[1][1] = true;
        assertTrue(edges(GraphInput.fromAdjacency(a)).isEmpty());
    }

    @Test
    void inputMatrix_NotMutatedByAnalysis() {
        double[][] m = {
                {1.0, 0.8},
                {0.8, 1.0}
        };
        ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(m, 0.5));
        assertEquals(0.8, m[0][1]);
        assertEquals(1.0, m[0][0]);
    }

    @Test
    void order_ReflectsMatrixSize() {
        assertEquals(4, GraphInput.fromAdjacency(new boolean[4][4]).order());
    }

    @Test
    void toString_MentionsOrderAndCorrelationFlag() {
        String s = GraphInput.fromAdjacency(new boolean[2][2]).toString();
        assertTrue(s.contains("order=2"));
        assertFalse(s.contains("hasCorrelation=true"));
    }
}
