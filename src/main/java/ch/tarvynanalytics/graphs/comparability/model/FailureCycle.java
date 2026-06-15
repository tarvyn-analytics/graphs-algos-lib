package ch.tarvynanalytics.graphs.comparability.model;

import java.util.List;

/**
 * The obstruction that proves a graph is <em>not</em> a comparability graph: an
 * odd-length closed <strong>forcing walk</strong>. By Golumbic's theorem an edge
 * lies in the same implication class as its reverse, and the walk witnesses that
 * — every transitive orientation would have to orient one of its edges both ways.
 *
 * <p>When the obstruction is an odd hole (e.g. C₅, C₇) the walk is exactly that
 * chordless odd cycle; for a folded obstruction (e.g. the 3-sun) it is a closed
 * walk that may revisit vertices. Either way every consecutive pair (with
 * wrap-around) is a real edge. It is reported in original-graph vertex ids and
 * labels. {@code weakestCorrelation} is the smallest-magnitude correlation among
 * the walk's edges — the edge most cheaply removed (e.g. by raising the
 * threshold) to break the obstruction; it is {@link Double#NaN} when the input
 * was a plain adjacency matrix with no correlation values.</p>
 *
 * @param level              level index at which the obstruction was detected (0 = input graph)
 * @param nodeIds            walk vertices in order (original-graph ids)
 * @param nodeLabels         walk vertices in order (labels), parallel to {@code nodeIds}
 * @param weakestCorrelation smallest-magnitude correlation on the walk, or {@code NaN}
 * @param weakestEdgeSource  source id of the weakest edge ({@code -1} if not applicable)
 * @param weakestEdgeTarget  target id of the weakest edge ({@code -1} if not applicable)
 */
public record FailureCycle(int level,
                           List<Integer> nodeIds,
                           List<String> nodeLabels,
                           double weakestCorrelation,
                           int weakestEdgeSource,
                           int weakestEdgeTarget) {

    /**
     * Canonical constructor; defensively copies the lists.
     *
     * @param level              the level index
     * @param nodeIds            the cycle vertex ids
     * @param nodeLabels         the cycle vertex labels
     * @param weakestCorrelation the weakest correlation magnitude
     * @param weakestEdgeSource  the weakest edge source id
     * @param weakestEdgeTarget  the weakest edge target id
     */
    public FailureCycle {
        nodeIds = List.copyOf(nodeIds);
        nodeLabels = List.copyOf(nodeLabels);
    }

    /**
     * @return the number of vertices on the cycle (its odd length)
     */
    public int length() {
        return nodeIds.size();
    }
}
