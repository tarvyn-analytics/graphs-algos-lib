package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.ChangeMetrics;
import ch.tarvynanalytics.graphs.algos.model.PairChange;

import java.util.List;

/**
 * Computes the S3 structural-change metric between two
 * consecutive correlation matrices over the same ordered variable set — the
 * pure-function metric core (D1). A later deliverable layers the two-sided
 * CUSUM change-point detector on top of this metric; this analyzer has no
 * detector state and is, like the other analyzers in this library, stateless
 * and thread-safe.
 *
 * <p>The <strong>primary metric</strong>, {@link ChangeMetrics#weightedChange()},
 * is the mean absolute correlation change {@code |Δr|} over the pairs valid
 * (finite) in <em>both</em> matrices — the intersection valid-pair set
 * {@code V}; {@code |V| = 0} (no pair finite in both windows) yields
 * {@link Double#NaN}, a gap, not a silent zero. The <strong>secondary
 * features</strong> ({@code densityLevel}, {@code edgeXor}, the cluster
 * signal) are computed in the same single {@code O(m^2)} pass; see
 * {@link ChangeMetrics} for each one's exact definition and denominator.</p>
 *
 * <pre>{@code
 * ChangeMetrics m = ChangeMetricsAnalyzer.analyze(previousCorrelation, currentCorrelation, 0.5);
 * if (Double.isNaN(m.weightedChange())) {
 *     // no pair was valid in both windows -- a gap, not a zero
 * }
 * }</pre>
 *
 * <p>Unlike {@link GraphInput#fromCorrelation}, this analyzer does
 * <strong>not</strong> reject non-finite correlation entries: {@code NaN} and
 * {@code ±Infinity} cells are an expected, handled input (a zero-variance
 * series upstream), excluded pair-by-pair rather than rejected outright. The
 * raw {@code double[][]} matrices are read directly because the primary
 * metric needs sub-threshold magnitudes that a boolean adjacency would drop;
 * no new bridge is introduced (the matrix stays the only artifact crossing
 * the boundary).</p>
 */
public final class ChangeMetricsAnalyzer {

    private ChangeMetricsAnalyzer() {
    }

    /**
     * Computes the change metrics for one transition {@code previous -> current}.
     *
     * @param previous  {@code C_{t-1}}, a square correlation matrix
     * @param current   {@code C_t}, a square correlation matrix of the same order as {@code previous}
     * @param threshold edge magnitude threshold; an edge exists where {@code |r| > |threshold|}
     * @return the immutable change-metric record for this transition
     * @throws IllegalArgumentException if {@code previous} or {@code current} is {@code null}
     * @throws InvalidInputException    if either matrix is not square, or their orders differ
     */
    public static ChangeMetrics analyze(double[][] previous, double[][] current, double threshold) {
        if (previous == null || current == null) {
            throw new IllegalArgumentException("previous and current matrices must not be null");
        }
        int m = requireSquare(previous, "previous");
        int n = requireSquare(current, "current");
        if (m != n) {
            throw new InvalidInputException(
                    "matrix dimension changed; got [" + m + "x" + m + "] and [" + n + "x" + n + "]");
        }
        return ChangeMetricsEngine.compute(previous, current, threshold);
    }

    /**
     * The top-{@code k} variable pairs contributing to {@link ChangeMetrics#weightedChange()} for one
     * transition {@code previous -> current}, ranked by {@code |Δr|} descending (ties broken by lower
     * {@code i}, then lower {@code j}). Only pairs finite in <em>both</em> matrices — the intersection
     * valid-pair set {@code V} that {@code weightedChange} averages — are eligible; {@code NaN} /
     * {@code ±Infinity} pairs are excluded. Returns at most {@code k} pairs (all eligible pairs when
     * fewer than {@code k} exist), and an empty list when {@code k <= 0} or no pair is valid in both
     * windows.
     *
     * <p>This is an opt-in, second {@code O(m^2)} pass over the matrices, deliberately separate from
     * {@link #analyze} so the per-transition metric cost stays bounded for callers that do not need
     * attribution.</p>
     *
     * @param previous {@code C_{t-1}}, a square correlation matrix
     * @param current  {@code C_t}, a square correlation matrix of the same order as {@code previous}
     * @param k        the maximum number of contributing pairs to return
     * @return the top contributing pairs, descending by {@code |Δr|} (never {@code null})
     * @throws IllegalArgumentException if {@code previous} or {@code current} is {@code null}
     * @throws InvalidInputException    if either matrix is not square, or their orders differ
     */
    public static List<PairChange> topContributors(double[][] previous, double[][] current, int k) {
        if (previous == null || current == null) {
            throw new IllegalArgumentException("previous and current matrices must not be null");
        }
        int m = requireSquare(previous, "previous");
        int n = requireSquare(current, "current");
        if (m != n) {
            throw new InvalidInputException(
                    "matrix dimension changed; got [" + m + "x" + m + "] and [" + n + "x" + n + "]");
        }
        return ChangeMetricsEngine.topContributors(previous, current, k);
    }

    private static int requireSquare(double[][] matrix, String name) {
        int n = matrix.length;
        for (int i = 0; i < n; i++) {
            if (matrix[i] == null || matrix[i].length != n) {
                int len = matrix[i] == null ? -1 : matrix[i].length;
                throw new InvalidInputException(
                        name + " matrix must be square; row " + i + " has length [" + len
                                + "], expected [" + n + "]");
            }
        }
        return n;
    }
}
