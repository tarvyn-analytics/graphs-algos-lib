package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.DecomposabilityReport;
import ch.tarvynanalytics.graphs.comparability.model.EdgeView;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnoses how to make a correlation graph <em>decomposable</em> (chordal) by
 * <strong>removing</strong> its weakest frustrating links — the deletion
 * complement of the chordal completion carried on every {@code AnalysisResult}
 * (which instead <em>adds</em> fill-in edges).
 *
 * <p>This is the repaired, decomposability-targeted form of the thesis
 * "weakest-edge-first" idea: while the graph has a chordless cycle, the weakest
 * edge on that cycle (smallest {@code |correlation|}) is removed, until the graph
 * is chordal. The weakest link on each obstruction is the cheapest to drop (e.g.
 * by raising the threshold), so the removed set names exactly which weak
 * dependencies frustrate a decomposable (junction-tree) model.</p>
 *
 * <p>The repair is greedy; minimum edge deletion to chordal is NP-hard, so the
 * removed set is not guaranteed minimum. It always terminates (each step removes
 * an edge; the empty graph is chordal).</p>
 *
 * <pre>{@code
 * DecomposabilityReport r = DecomposabilityDiagnostic.analyze(
 *         GraphInput.fromCorrelation(matrix, 0.18));
 * if (!r.isDecomposable()) {
 *     System.out.println("drop " + r.removalCount() + " link(s) to decompose; "
 *             + "heaviest |corr| = " + r.suggestedThreshold());
 * }
 * }</pre>
 */
public final class DecomposabilityDiagnostic {

    private DecomposabilityDiagnostic() {
    }

    /**
     * Diagnoses the decomposability repair for the given input.
     *
     * @param input the graph to diagnose
     * @return the weakest-link deletion repair (empty when already chordal)
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static DecomposabilityReport analyze(GraphInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        boolean[][] adj = buildAdjacency(input);
        int fillInAlternative = Chordality.analyze(adj).fillIn.length;

        boolean[][] working = new boolean[adj.length][];
        for (int i = 0; i < adj.length; i++) {
            working[i] = adj[i].clone();
        }
        List<EdgeView> removed = new ArrayList<>();
        List<Double> removedCorrelations = new ArrayList<>();
        double heaviest = Double.NaN;

        for (int[] hole = Chordality.holeOrEmpty(working);
             hole.length > 0;
             hole = Chordality.holeOrEmpty(working)) {
            int[] weakest = weakestEdgeOnCycle(input, hole);
            int a = weakest[0];
            int b = weakest[1];
            working[a][b] = false;
            working[b][a] = false;
            removed.add(new EdgeView(Math.min(a, b), Math.max(a, b)));
            double corr = input.hasCorrelation() ? input.correlation(a, b) : Double.NaN;
            removedCorrelations.add(corr);
            heaviest = updateHeaviest(heaviest, input, corr);
        }

        return new DecomposabilityReport(removed.isEmpty(), removed, removedCorrelations,
                heaviest, fillInAlternative);
    }

    private static boolean[][] buildAdjacency(GraphInput input) {
        int n = input.order();
        boolean[][] adj = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && input.adjacent(i, j)) {
                    adj[i][j] = true;
                }
            }
        }
        return adj;
    }

    /** The running heaviest removed magnitude, updated for one removal (unchanged for adjacency input). */
    private static double updateHeaviest(double heaviest, GraphInput input, double corr) {
        if (!input.hasCorrelation()) {
            return heaviest;
        }
        double mag = Math.abs(corr);
        return Double.isNaN(heaviest) ? mag : Math.max(heaviest, mag);
    }

    /**
     * The weakest edge on the chordless cycle: the consecutive pair (with
     * wrap-around) of smallest {@code |correlation|}. For adjacency input (no
     * correlations) every magnitude is equal, so the deterministic tie-break — the
     * lexicographically smallest {@code (min,max)} endpoint pair — decides.
     */
    private static int[] weakestEdgeOnCycle(GraphInput input, int[] cycle) {
        int k = cycle.length;
        int bestA = -1;
        int bestB = -1;
        double bestMag = Double.POSITIVE_INFINITY;
        for (int i = 0; i < k; i++) {
            int a = cycle[i];
            int b = cycle[(i + 1) % k];
            double mag = input.hasCorrelation() ? Math.abs(input.correlation(a, b)) : 0.0;
            if (mag < bestMag || (mag == bestMag && earlierPair(a, b, bestA, bestB))) {
                bestMag = mag;
                bestA = a;
                bestB = b;
            }
        }
        return new int[]{bestA, bestB};
    }

    /** Whether {@code (a,b)} precedes {@code (cA,cB)} as an unordered, then ordered, endpoint pair. */
    private static boolean earlierPair(int a, int b, int cA, int cB) {
        if (cA < 0) {
            return true;
        }
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        int clo = Math.min(cA, cB);
        int chi = Math.max(cA, cB);
        return lo < clo || (lo == clo && hi < chi);
    }
}
