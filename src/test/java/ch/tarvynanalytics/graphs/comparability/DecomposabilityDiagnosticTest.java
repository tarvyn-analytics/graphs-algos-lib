package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.DecomposabilityReport;
import ch.tarvynanalytics.graphs.comparability.model.EdgeView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The weakest-link decomposability repair. Verdicts are proved against known graph
 * theory and the engine's chordality primitive: removing the reported links must
 * actually make the graph chordal, the removal must be empty exactly when the graph
 * is already chordal, and the weakest edge on each obstruction must be the one
 * chosen.
 */
class DecomposabilityDiagnosticTest {

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
            int j = (i + 1) % n;
            a[i][j] = true;
            a[j][i] = true;
        }
        return a;
    }

    /** A correlation matrix for the n-cycle with edge i–(i+1) weighted w[i]. */
    private static double[][] weightedCycle(double[] w) {
        int n = w.length;
        double[][] m = new double[n][n];
        for (int i = 0; i < n; i++) {
            m[i][i] = 1.0;
            int j = (i + 1) % n;
            m[i][j] = w[i];
            m[j][i] = w[i];
        }
        return m;
    }

    /** Removes the report's links from {@code adj} and returns whether the result is chordal. */
    private static boolean removalMakesChordal(boolean[][] adj, DecomposabilityReport report) {
        int n = adj.length;
        boolean[][] working = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            working[i] = adj[i].clone();
        }
        for (EdgeView e : report.weakestLinksToRemove()) {
            working[e.source()][e.target()] = false;
            working[e.target()][e.source()] = false;
        }
        return Chordality.holeOrNull(working) == null;
    }

    @Test
    void nullInput_Throws() {
        assertThrows(IllegalArgumentException.class, () -> DecomposabilityDiagnostic.analyze(null));
    }

    @Test
    void alreadyChordal_NeedsNoRemoval() {
        DecomposabilityReport r = DecomposabilityDiagnostic.analyze(GraphInput.fromAdjacency(complete(4)));
        assertTrue(r.isDecomposable());
        assertEquals(0, r.removalCount());
        assertEquals(0, r.fillInAlternative());
        assertTrue(Double.isNaN(r.suggestedThreshold()));
    }

    @Test
    void weightedFiveCycle_RemovesTheWeakestLink() {
        // C5 edges 0-1..4-0 weighted 0.9,0.8,0.7,0.6,0.55 -> the 4-0 link (0.55) is weakest.
        double[] w = {0.9, 0.8, 0.7, 0.6, 0.55};
        DecomposabilityReport r = DecomposabilityDiagnostic.analyze(
                GraphInput.fromCorrelation(weightedCycle(w), 0.5));

        assertFalse(r.isDecomposable());
        assertEquals(1, r.removalCount(), "one removal turns C5 into a chordal path");
        assertEquals(new EdgeView(0, 4), r.weakestLinksToRemove().get(0));
        assertEquals(0.55, r.removedCorrelations().get(0), 1e-12);
        assertEquals(0.55, r.suggestedThreshold(), 1e-12);
        assertEquals(2, r.fillInAlternative(), "the add-edges alternative triangulates C5 with 2 chords");
        assertTrue(removalMakesChordal(cycle(5), r));
    }

    @Test
    void adjacencyFourCycle_RemovesOneLink_NoCorrelations() {
        DecomposabilityReport r = DecomposabilityDiagnostic.analyze(GraphInput.fromAdjacency(cycle(4)));
        assertFalse(r.isDecomposable());
        assertEquals(1, r.removalCount());
        assertTrue(Double.isNaN(r.removedCorrelations().get(0)), "no correlations for adjacency input");
        assertTrue(Double.isNaN(r.suggestedThreshold()));
        assertEquals(1, r.fillInAlternative());
        assertTrue(removalMakesChordal(cycle(4), r));
    }

    @Test
    void twoSeparateHoles_RemovesWeakestOfEach_ThresholdIsHeaviest() {
        // Two disjoint 4-cycles: 0-1-2-3 (weakest link 0.60) and 4-5-6-7 (weakest link 0.80).
        double[][] m = new double[8][8];
        for (int i = 0; i < 8; i++) {
            m[i][i] = 1.0;
        }
        double[][] weights = {
                {0, 1, 0.70}, {1, 2, 0.70}, {2, 3, 0.70}, {3, 0, 0.60},
                {4, 5, 0.90}, {5, 6, 0.90}, {6, 7, 0.90}, {7, 4, 0.80}
        };
        for (double[] e : weights) {
            int a = (int) e[0];
            int b = (int) e[1];
            m[a][b] = e[2];
            m[b][a] = e[2];
        }
        DecomposabilityReport r = DecomposabilityDiagnostic.analyze(GraphInput.fromCorrelation(m, 0.5));

        assertFalse(r.isDecomposable());
        assertEquals(2, r.removalCount(), "one removal per disjoint hole");
        assertEquals(0.80, r.suggestedThreshold(), 1e-12, "the heaviest removed link sets the threshold");
        boolean[][] adj = new boolean[8][8];
        for (double[] e : weights) {
            int a = (int) e[0];
            int b = (int) e[1];
            adj[a][b] = true;
            adj[b][a] = true;
        }
        assertTrue(removalMakesChordal(adj, r));
    }

    @Test
    void variousHoles_RemovalAlwaysReachesChordal() {
        for (int k : new int[]{4, 5, 6, 7, 8}) {
            DecomposabilityReport r = DecomposabilityDiagnostic.analyze(GraphInput.fromAdjacency(cycle(k)));
            assertFalse(r.isDecomposable(), "C" + k + " is not chordal");
            assertTrue(removalMakesChordal(cycle(k), r), "removal makes C" + k + " chordal");
            // Deleting any one edge of a pure cycle leaves a path, which is chordal — so just one
            // removal (whereas the add-edges completion would need k-3 chords to triangulate it).
            assertEquals(1, r.removalCount(), "one deletion breaks the single cycle C" + k);
            assertEquals(k - 3, r.fillInAlternative(), "the completion triangulates C" + k + " with k-3 chords");
        }
    }
}
