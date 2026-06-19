package ch.tarvynanalytics.graphs.comparability.model;

import java.util.List;

/**
 * A diagnostic for turning a correlation graph into a <em>decomposable</em>
 * (chordal) one by <strong>removing</strong> its weakest frustrating links — the
 * deletion complement of the chordal completion in {@link ChordalityView} (which
 * <em>adds</em> fill-in edges).
 *
 * <p>The repair is greedy and weight-aware: while the graph has a chordless cycle,
 * the weakest edge (smallest {@code |correlation|}) on that cycle is removed, until
 * the graph is chordal. The empty graph is chordal, so it always terminates.
 * Minimum edge deletion to a chordal graph is NP-hard, so this is a heuristic — the
 * removed set is <em>not</em> guaranteed minimum — but it is a principled
 * diagnostic of which weak links frustrate decomposability.</p>
 *
 * <p>When the input graph is already chordal, {@link #decomposable()} is
 * {@code true} and the removal lists are empty.</p>
 *
 * @param decomposable           whether the input graph is already chordal (no removal needed)
 * @param weakestLinksToRemove   the edges to delete (weakest-first) to reach decomposability
 * @param removedCorrelations    the correlation of each removed edge (parallel; {@code NaN} for
 *                               adjacency input), in removal order
 * @param suggestedThreshold     the largest {@code |correlation|} among the removed links — every
 *                               frustrating link sits at or below it; {@code NaN} when already
 *                               decomposable or the input carried no correlations
 * @param fillInAlternative      the size of the add-edges chordal completion (the other repair), for
 *                               comparison
 */
public record DecomposabilityReport(boolean decomposable,
                                    List<EdgeView> weakestLinksToRemove,
                                    List<Double> removedCorrelations,
                                    double suggestedThreshold,
                                    int fillInAlternative) {

    /**
     * Canonical constructor; defensively copies the lists.
     *
     * @param decomposable         whether the input graph is already chordal
     * @param weakestLinksToRemove the edges to delete to reach decomposability
     * @param removedCorrelations  the correlations of the removed edges
     * @param suggestedThreshold   the largest removed magnitude
     * @param fillInAlternative    the chordal-completion size
     */
    public DecomposabilityReport {
        weakestLinksToRemove = List.copyOf(weakestLinksToRemove);
        removedCorrelations = List.copyOf(removedCorrelations);
    }

    /**
     * @return {@code true} iff the input graph is already decomposable (chordal)
     */
    public boolean isDecomposable() {
        return decomposable;
    }

    /**
     * @return the number of weakest links to remove to reach decomposability (0 when already chordal)
     */
    public int removalCount() {
        return weakestLinksToRemove.size();
    }
}
