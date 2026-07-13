package ch.tarvynanalytics.graphs.algos.model;

import java.util.List;

/**
 * An undirected edge that appears in the selectivity-matched top-K edge set of at
 * least one estimator, annotated with <em>which</em> estimators agree on it — the
 * unit of the cross-estimator robustness comparison in {@link CrossEstimatorReport}.
 *
 * <p>An edge present in every estimator's top-K is part of the <strong>stable
 * core</strong> (the "gold core" of links robust to the choice of estimator); one
 * present in a single estimator is an estimator-unique, outlier-sensitive link. The
 * {@code support()} count is the number of estimators that agree.</p>
 *
 * <p>By convention {@code source < target}; the labels are parallel to the
 * endpoints. {@code estimatorIndices} are indices into
 * {@link CrossEstimatorReport#estimatorNames()}, in ascending order.</p>
 *
 * @param source           id of the lower-numbered endpoint
 * @param target           id of the higher-numbered endpoint
 * @param sourceLabel      label of {@code source}
 * @param targetLabel      label of {@code target}
 * @param estimatorIndices the estimators (by index) whose top-K contains this edge, ascending
 */
public record RobustEdge(int source,
                         int target,
                         String sourceLabel,
                         String targetLabel,
                         List<Integer> estimatorIndices) {

    /**
     * Canonical constructor; defensively copies the estimator-index list.
     *
     * @param source           the lower-numbered endpoint
     * @param target           the higher-numbered endpoint
     * @param sourceLabel      the source label
     * @param targetLabel      the target label
     * @param estimatorIndices the agreeing estimators by index
     */
    public RobustEdge {
        estimatorIndices = List.copyOf(estimatorIndices);
    }

    /**
     * @return how many estimators have this edge in their top-K (1 = estimator-unique)
     */
    public int support() {
        return estimatorIndices.size();
    }
}
