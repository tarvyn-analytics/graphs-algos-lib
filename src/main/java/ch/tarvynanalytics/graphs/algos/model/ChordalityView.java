package ch.tarvynanalytics.graphs.algos.model;

import java.util.List;

/**
 * Whether the input graph is <strong>chordal</strong> (triangulated) and the
 * structure that follows from it. A graph is chordal iff every cycle of length
 * &ge; 4 has a chord — equivalently, iff it has a perfect elimination ordering.
 * Chordal graphs are exactly the <em>decomposable</em> models: when the
 * (partial-)correlation graph is chordal the Gaussian graphical model factorizes
 * over a clique (junction) tree and the covariance / precision MLE is closed-form
 * and modular.
 *
 * <p>If {@link #chordal()} is {@code true}, {@link #perfectEliminationOrder()}
 * holds a perfect elimination ordering of the graph and both
 * {@link #chordlessCycle()} and {@link #fillInEdges()} are empty. If it is
 * {@code false}, {@link #chordlessCycle()} holds a witnessing hole (a chordless
 * cycle of length &ge; 4), {@link #fillInEdges()} holds the extra edges of a
 * greedy chordal completion (minimum fill-in is NP-hard, so this is a heuristic —
 * not guaranteed minimal), and {@link #perfectEliminationOrder()} is a perfect
 * elimination ordering of that completion (the original graph plus the fill-in).</p>
 *
 * @param chordal                  whether the input graph is chordal
 * @param perfectEliminationOrder  a PEO of the chordal completion (= the graph when chordal), vertex ids
 * @param perfectEliminationLabels the PEO vertices as labels, parallel to {@code perfectEliminationOrder}
 * @param chordlessCycle           a witnessing hole (vertex ids) when not chordal, otherwise empty
 * @param chordlessCycleLabels     the hole vertices as labels, parallel to {@code chordlessCycle}
 * @param fillInEdges              the chordal-completion fill-in edges, empty when already chordal
 */
public record ChordalityView(boolean chordal,
                             List<Integer> perfectEliminationOrder,
                             List<String> perfectEliminationLabels,
                             List<Integer> chordlessCycle,
                             List<String> chordlessCycleLabels,
                             List<EdgeView> fillInEdges) {

    /**
     * Canonical constructor; defensively copies the lists.
     *
     * @param chordal                  the verdict
     * @param perfectEliminationOrder  the perfect elimination ordering (vertex ids)
     * @param perfectEliminationLabels the perfect elimination ordering (labels)
     * @param chordlessCycle           the witnessing hole (vertex ids)
     * @param chordlessCycleLabels     the witnessing hole (labels)
     * @param fillInEdges              the chordal-completion fill-in edges
     */
    public ChordalityView {
        perfectEliminationOrder = List.copyOf(perfectEliminationOrder);
        perfectEliminationLabels = List.copyOf(perfectEliminationLabels);
        chordlessCycle = List.copyOf(chordlessCycle);
        chordlessCycleLabels = List.copyOf(chordlessCycleLabels);
        fillInEdges = List.copyOf(fillInEdges);
    }

    /**
     * @return {@code true} iff the input graph is chordal (decomposable)
     */
    public boolean isChordal() {
        return chordal;
    }

    /**
     * @return the number of fill-in edges in the chordal completion (0 when already chordal)
     */
    public int fillInCount() {
        return fillInEdges.size();
    }
}
