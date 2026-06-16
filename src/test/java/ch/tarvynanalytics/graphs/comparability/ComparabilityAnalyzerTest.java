package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;
import ch.tarvynanalytics.graphs.comparability.model.EdgeView;
import ch.tarvynanalytics.graphs.comparability.model.FailureCycle;
import ch.tarvynanalytics.graphs.comparability.model.ModuleType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.time.Duration;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparabilityAnalyzerTest {

    // ---- graph builders -------------------------------------------------

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

    private static boolean[][] cycle(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            link(a, i, (i + 1) % n);
        }
        return a;
    }

    private static boolean[][] path(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i + 1 < n; i++) {
            link(a, i, i + 1);
        }
        return a;
    }

    private static void link(boolean[][] a, int i, int j) {
        a[i][j] = true;
        a[j][i] = true;
    }

    private static AnalysisResult analyze(boolean[][] adjacency) {
        return ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(adjacency));
    }

    private static boolean hasEdge(AnalysisResult r, int a, int b) {
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        return r.inputGraph().edges().contains(new EdgeView(lo, hi));
    }

    // ---- trivial graphs -------------------------------------------------

    @Test
    void edgelessGraph_IsComparability_WithSingleOrientation() {
        AnalysisResult r = analyze(new boolean[4][4]);
        assertTrue(r.isComparability());
        assertEquals(BigInteger.ONE, r.transitiveOrientationCount());
        assertTrue(r.failure().isEmpty());
    }

    @Test
    void singleNode_IsComparability() {
        AnalysisResult r = analyze(new boolean[1][1]);
        assertTrue(r.isComparability());
        assertEquals(1, r.inputGraph().order());
        assertEquals(0, r.inputGraph().size());
    }

    // ---- complete graphs: n! transitive orientations --------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6})
    void completeGraph_HasFactorialOrientations(int n) {
        AnalysisResult r = analyze(complete(n));
        assertTrue(r.isComparability(), "K" + n + " is a comparability graph");
        assertEquals(factorial(n), r.transitiveOrientationCount());
    }

    @Test
    void completeGraph_IsOneCliqueModule() {
        AnalysisResult r = analyze(complete(4));
        assertEquals(1, r.levels().get(0).modules().size());
        assertEquals(ModuleType.CLIQUE, r.levels().get(0).modules().get(0).type());
        assertEquals(4, r.levels().get(0).modules().get(0).cardinality());
    }

    // ---- paths and even cycles are comparability ------------------------

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 7})
    void path_IsComparability(int n) {
        assertTrue(analyze(path(n)).isComparability(), "P" + n + " is a comparability graph");
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 6, 8, 10})
    void evenCycle_IsComparability(int n) {
        assertTrue(analyze(cycle(n)).isComparability(), "C" + n + " (even) is a comparability graph");
    }

    // ---- odd cycles (odd holes) are NOT comparability -------------------

    @ParameterizedTest
    @ValueSource(ints = {5, 7, 9, 11})
    void oddCycle_IsNotComparability_AndReportsTheCycle(int n) {
        AnalysisResult r = analyze(cycle(n));
        assertFalse(r.isComparability(), "C" + n + " (odd hole) is not a comparability graph");
        assertEquals(BigInteger.ZERO, r.transitiveOrientationCount());

        FailureCycle f = r.failure().orElseThrow();
        assertEquals(n, f.length(), "the whole odd cycle is reported");
        assertEquals(Set.copyOf(rangeIds(n)), Set.copyOf(f.nodeIds()), "all vertices are on the cycle");

        // consecutive cycle vertices (with wraparound) must be real edges
        for (int k = 0; k < f.length(); k++) {
            int a = f.nodeIds().get(k);
            int b = f.nodeIds().get((k + 1) % f.length());
            assertTrue(hasEdge(r, a, b), "cycle edge " + a + "-" + b + " exists in the input");
        }
    }

    @Test
    void adjacencyFailure_HasNoCorrelationInfo() {
        FailureCycle f = analyze(cycle(5)).failure().orElseThrow();
        assertTrue(Double.isNaN(f.weakestCorrelation()));
        assertEquals(-1, f.weakestEdgeSource());
        assertEquals(-1, f.weakestEdgeTarget());
    }

    // ---- modular decomposition -----------------------------------------

    @Test
    void twoDisjointEdges_DecomposeIntoCliquesThenIndependentSet() {
        boolean[][] a = new boolean[4][4];
        link(a, 0, 1);
        link(a, 2, 3);
        AnalysisResult r = analyze(a);

        assertTrue(r.isComparability());
        // 2 cliques of size 2 (2! each) at level 0, independent at level 1 -> 4 orientations
        assertEquals(BigInteger.valueOf(4), r.transitiveOrientationCount());
        assertTrue(r.levels().size() >= 2, "the cograph decomposes over multiple levels");
        assertEquals(2, r.levels().get(0).modules().size());
        assertTrue(r.levels().get(0).modules().stream().allMatch(m -> m.type() == ModuleType.CLIQUE));
    }

    // ---- the 3-sun: a chordal NON-comparability graph -------------------

    @Test
    void threeSun_IsNotComparability_AndTerminates() {
        // The 3-sun (Hajós graph): triangle 0-1-2 plus an outer vertex on each
        // pair of triangle edges. It is chordal (no odd hole) yet not a
        // comparability graph — a brute-force oracle gives it zero transitive
        // orientations. The thesis chain engine both overflowed the stack here
        // and (once bounded) wrongly accepted it; the Golumbic Γ engine rejects
        // it via an odd forcing walk.
        boolean[][] a = new boolean[6][6];
        link(a, 0, 1);
        link(a, 1, 2);
        link(a, 0, 2);
        link(a, 3, 0);
        link(a, 3, 1);
        link(a, 4, 1);
        link(a, 4, 2);
        link(a, 5, 0);
        link(a, 5, 2);

        AnalysisResult r = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> analyze(a));
        assertFalse(r.isComparability(), "the 3-sun is not a comparability graph");
        assertEquals(BigInteger.ZERO, r.transitiveOrientationCount());

        // the obstruction is a real closed walk: consecutive vertices are edges
        FailureCycle f = r.failure().orElseThrow();
        for (int k = 0; k < f.length(); k++) {
            int x = f.nodeIds().get(k);
            int y = f.nodeIds().get((k + 1) % f.length());
            assertTrue(hasEdge(r, x, y), "walk step " + x + "-" + y + " is an edge");
        }
    }

    @Test
    void completeBipartite_IsComparability() {
        // K(2,3): parts {0,1} and {2,3,4}, all cross edges, no intra-part edges
        boolean[][] a = new boolean[5][5];
        for (int i = 0; i <= 1; i++) {
            for (int j = 2; j <= 4; j++) {
                link(a, i, j);
            }
        }
        assertTrue(analyze(a).isComparability());
    }

    // ---- correlation input ---------------------------------------------

    @Test
    void correlationOddCycle_ReportsWeakestEdge() {
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
        FailureCycle f = r.failure().orElseThrow();
        assertEquals(0.55, f.weakestCorrelation(), 1e-12);
        assertEquals(Set.of(4, 0), Set.of(f.weakestEdgeSource(), f.weakestEdgeTarget()));
    }

    @Test
    void correlationClique_CountsOrientations() {
        double[][] m = {
                {1.0, 0.9, 0.8},
                {0.9, 1.0, 0.7},
                {0.8, 0.7, 1.0}
        };
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(m, 0.5));
        assertTrue(r.isComparability());
        assertEquals(BigInteger.valueOf(6), r.transitiveOrientationCount());
    }

    // ---- result immutability & API -------------------------------------

    @Test
    void result_LevelsAreUnmodifiable() {
        AnalysisResult r = analyze(complete(3));
        assertThrows(UnsupportedOperationException.class, () -> r.levels().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.inputGraph().edges().clear());
    }

    @Test
    void analyze_NullInput_Throws() {
        assertThrows(IllegalArgumentException.class, () -> ComparabilityAnalyzer.analyze(null));
        assertThrows(IllegalArgumentException.class, () -> new ComparabilityAnalyzer().run(null));
    }

    // ---- helpers --------------------------------------------------------

    private static BigInteger factorial(int n) {
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        return f;
    }

    private static java.util.List<Integer> rangeIds(int n) {
        java.util.List<Integer> ids = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(i);
        }
        return ids;
    }
}
