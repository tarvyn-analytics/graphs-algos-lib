package ch.tarvynanalytics.graphs.comparability.model;

import java.util.ArrayList;
import java.util.List;

/**
 * How robust the correlation graph is to the <em>choice of estimator</em>. Several
 * named correlation matrices over the same variables (e.g. Pearson, Spearman,
 * Kendall, partial) are matched on selectivity — each contributes its top-K
 * strongest edges by magnitude — and the report says where they agree.
 *
 * <p>It carries three views of that agreement:</p>
 * <ul>
 *   <li>{@link #jaccard(int, int)} — the pairwise top-K edge-set overlap
 *       (Jaccard index) between estimators {@code i} and {@code j};</li>
 *   <li>{@link #stableCore()} — the edges every estimator's top-K contains (the
 *       "gold core" of links robust to the estimator choice);</li>
 *   <li>{@link #uniqueEdges()} / {@link #uniqueTo(int)} — the edges only one
 *       estimator's top-K contains (the outlier-sensitive, distrust-worthy links).</li>
 * </ul>
 *
 * <p>{@link #edges()} is the full set of edges that appear in at least one
 * estimator's top-K, each a {@link RobustEdge} annotated with the estimators that
 * agree on it, ordered by descending support then by endpoints.</p>
 *
 * @param estimatorNames the estimator names, in input order (parallel to the Jaccard axes)
 * @param topK           the selectivity applied — the number of strongest edges taken per estimator
 *                       (clamped to the number of vertex pairs)
 * @param jaccard        the pairwise top-K Jaccard matrix; {@code jaccard.get(i).get(j)} is the
 *                       overlap of estimators {@code i} and {@code j} (symmetric, 1.0 on the diagonal)
 * @param edges          every edge in at least one estimator's top-K, support-annotated and ordered
 */
public record CrossEstimatorReport(List<String> estimatorNames,
                                   int topK,
                                   List<List<Double>> jaccard,
                                   List<RobustEdge> edges) {

    /**
     * Canonical constructor; defensively copies the lists (including the inner Jaccard rows).
     *
     * @param estimatorNames the estimator names in input order
     * @param topK           the selectivity applied
     * @param jaccard        the pairwise Jaccard matrix
     * @param edges          the support-annotated union of the top-K edge sets
     */
    public CrossEstimatorReport {
        estimatorNames = List.copyOf(estimatorNames);
        List<List<Double>> rows = new ArrayList<>(jaccard.size());
        for (List<Double> row : jaccard) {
            rows.add(List.copyOf(row));
        }
        jaccard = List.copyOf(rows);
        edges = List.copyOf(edges);
    }

    /**
     * @return the number of estimators compared
     */
    public int estimatorCount() {
        return estimatorNames.size();
    }

    /**
     * The top-K edge-set overlap of two estimators.
     *
     * @param i the first estimator's index
     * @param j the second estimator's index
     * @return the Jaccard index {@code |A∩B| / |A∪B|} of their top-K edge sets
     */
    public double jaccard(int i, int j) {
        return jaccard.get(i).get(j);
    }

    /**
     * The stable core: edges that every estimator's top-K contains (robust to the
     * estimator choice).
     *
     * @return the edges with full support, in {@link #edges()} order
     */
    public List<RobustEdge> stableCore() {
        List<RobustEdge> core = new ArrayList<>();
        for (RobustEdge e : edges) {
            if (e.support() == estimatorCount()) {
                core.add(e);
            }
        }
        return core;
    }

    /**
     * The estimator-unique edges: edges only a single estimator's top-K contains
     * (the outlier-sensitive links).
     *
     * @return the edges with support 1, in {@link #edges()} order
     */
    public List<RobustEdge> uniqueEdges() {
        List<RobustEdge> unique = new ArrayList<>();
        for (RobustEdge e : edges) {
            if (e.support() == 1) {
                unique.add(e);
            }
        }
        return unique;
    }

    /**
     * The edges unique to one estimator — present in its top-K and no other's.
     *
     * @param estimator the estimator's index
     * @return that estimator's unique edges, in {@link #edges()} order
     */
    public List<RobustEdge> uniqueTo(int estimator) {
        List<RobustEdge> unique = new ArrayList<>();
        for (RobustEdge e : edges) {
            if (e.support() == 1 && e.estimatorIndices().get(0) == estimator) {
                unique.add(e);
            }
        }
        return unique;
    }
}
