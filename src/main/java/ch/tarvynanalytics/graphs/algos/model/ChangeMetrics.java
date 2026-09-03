package ch.tarvynanalytics.graphs.algos.model;

import java.util.List;

/**
 * The structural-change signal between two consecutive correlation matrices
 * {@code C_{t-1} -> C_t} over the same ordered variable set (the S3 change-metric spec's pure-metric core).
 *
 * <p><strong>{@link #weightedChange()}</strong> is the primary metric: the mean
 * absolute correlation change over the pairs valid (finite) in <em>both</em>
 * windows — the intersection valid-pair set {@code V}. <strong>The secondary
 * features below use a different denominator</strong> where noted; mixing the
 * two up is the #1 bug risk of this metric (see {@code ChangeMetricsAnalyzer}).</p>
 *
 * @param weightedChange            primary metric: mean {@code |Δr|} over the intersection valid-pair
 *                                  set {@code V} ({@code |V| = 0} ⇒ {@link Double#NaN})
 * @param densityLevel              {@code n_edges / |P|} at {@code C_t} — denominator is <em>all</em>
 *                                  pairs {@code |P|}, not just the valid ones
 * @param edgeXor                   {@code |E_t Δ E_{t-1}| / |V|} — denominator is the intersection
 *                                  valid-pair set {@code V}, matching {@link #weightedChange()}
 * @param nComponents               number of connected components of the density edge set of {@code C_t}
 *                                  (every variable is a node, including zero-edge isolates)
 * @param largestComponentFraction  {@code |largest component| / m} ({@link Double#NaN} when {@code m == 0})
 * @param componentSizes            component sizes at {@code C_t}, descending, summing to {@code m}
 * @param definedPairCount          {@code |V|}, the number of pairs finite in both matrices; {@code -1} via
 *                                  the legacy 6-arg constructor, where it is not known
 */
public record ChangeMetrics(double weightedChange,
                            double densityLevel,
                            double edgeXor,
                            int nComponents,
                            double largestComponentFraction,
                            List<Integer> componentSizes,
                            int definedPairCount) {

    /**
     * Canonical constructor; defensively copies {@code componentSizes}.
     *
     * @param weightedChange           the primary metric
     * @param densityLevel             the all-pairs edge density of {@code C_t}
     * @param edgeXor                  the valid-pair edge symmetric-difference fraction
     * @param nComponents              the number of connected components
     * @param largestComponentFraction the largest component's fraction of all variables
     * @param componentSizes           the component sizes, descending
     * @param definedPairCount         the intersection valid-pair count {@code |V|}
     */
    public ChangeMetrics {
        componentSizes = List.copyOf(componentSizes);
    }

    /**
     * Pre-{@code definedPairCount} arity; delegates with {@code -1} (not known) — this silently
     * drops the count on any rebuild from an existing instance, use the 7-arg canonical form.
     *
     * @deprecated since 1.1.0; loses {@code definedPairCount} — carry {@link #definedPairCount()}
     *             through the 7-arg canonical constructor instead
     */
    @Deprecated(since = "1.1.0")
    public ChangeMetrics(double weightedChange, double densityLevel, double edgeXor, int nComponents,
            double largestComponentFraction, List<Integer> componentSizes) {
        this(weightedChange, densityLevel, edgeXor, nComponents, largestComponentFraction, componentSizes, -1);
    }
}
