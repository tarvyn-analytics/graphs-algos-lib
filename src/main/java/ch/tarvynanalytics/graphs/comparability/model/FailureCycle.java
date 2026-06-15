package ch.tarvynanalytics.graphs.comparability.model;

import java.util.List;

/**
 * The obstruction that proves a graph is <em>not</em> a comparability graph:
 * an odd-length chordless cycle (an "odd hole") discovered during the
 * decomposition. A comparability graph admits no such cycle, so its presence
 * is a certificate of non-orientability.
 *
 * <p>The cycle is reported in original-graph vertex ids (and labels) regardless
 * of the level at which it was found, so callers can map it straight back to
 * their input. {@code weakestCorrelation} is the smallest-magnitude correlation
 * among the cycle's edges — the edge most cheaply removed (e.g. by raising the
 * threshold) to break the obstruction; it is {@link Double#NaN} when the input
 * was a plain adjacency matrix with no correlation values.</p>
 *
 * @param level              level index at which the cycle was detected
 * @param nodeIds            cycle vertices in order (original-graph ids)
 * @param nodeLabels         cycle vertices in order (labels), parallel to {@code nodeIds}
 * @param weakestCorrelation smallest-magnitude correlation on the cycle, or {@code NaN}
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
