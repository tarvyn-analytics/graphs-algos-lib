package ch.tarvynanalytics.graphs.algos.model;

/**
 * One variable pair's absolute correlation change {@code |Δr|} across a transition
 * {@code C_{t-1} -> C_t}, identified by zero-based column index into the matrices' shared variable
 * order ({@code i < j}, upper triangle). These are the <em>contributors</em> to the
 * {@link ChangeMetrics#weightedChange()} metric — the pairs that moved most — as ranked and capped
 * by {@code ChangeMetricsAnalyzer.topContributors}.
 *
 * <p>The library is label-agnostic: a pair is reported by index, and it is the caller (which owns
 * the variable order) that maps {@code (i, j)} back to names.</p>
 *
 * @param i        the lower zero-based variable index of the pair
 * @param j        the higher zero-based variable index of the pair ({@code j > i})
 * @param absDelta the absolute correlation change {@code |C_t[i][j] − C_{t-1}[i][j]|} (finite, {@code >= 0})
 */
public record PairChange(int i, int j, double absDelta) {
}
