package ch.tarvynanalytics.graphs.comparability.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * The immutable outcome of a comparability analysis.
 *
 * <p>If {@link #comparability()} is {@code true} the input is a comparability
 * graph (transitively orientable): {@link #levels()} holds the full factor-graph
 * decomposition hierarchy and {@link #transitiveOrientationCount()} is the number
 * of distinct transitive orientations. If it is {@code false}, {@link #failure()}
 * carries the obstructing odd chordless cycle, the count is zero, and
 * {@link #levels()} holds the decomposition produced up to the failing level.</p>
 *
 * @param comparability              whether the input graph is a comparability graph
 * @param inputGraph                 the level-0 graph built from the input matrix
 * @param levels                     the decomposition levels (level 0 = input graph)
 * @param transitiveOrientationCount number of distinct transitive orientations
 *                                   ({@link BigInteger#ZERO} when not comparability)
 * @param failureCycle               the obstructing cycle, or {@code null} when comparability
 */
public record AnalysisResult(boolean comparability,
                             GraphView inputGraph,
                             List<FactorGraphLevelView> levels,
                             BigInteger transitiveOrientationCount,
                             FailureCycle failureCycle) {

    /**
     * Canonical constructor; defensively copies the levels list.
     *
     * @param comparability              the verdict
     * @param inputGraph                 the input graph
     * @param levels                     the decomposition levels
     * @param transitiveOrientationCount the orientation count
     * @param failureCycle               the failure cycle (nullable)
     */
    public AnalysisResult {
        levels = List.copyOf(levels);
    }

    /**
     * @return {@code true} iff the input graph is a comparability graph
     */
    public boolean isComparability() {
        return comparability;
    }

    /**
     * @return the obstructing cycle if the graph is not a comparability graph,
     *         otherwise an empty optional
     */
    public Optional<FailureCycle> failure() {
        return Optional.ofNullable(failureCycle);
    }
}
