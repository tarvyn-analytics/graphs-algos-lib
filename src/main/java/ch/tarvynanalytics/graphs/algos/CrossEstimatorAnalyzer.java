package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.CrossEstimatorReport;
import ch.tarvynanalytics.graphs.algos.model.RobustEdge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Compares several correlation estimators of the same variables and reports how
 * <strong>robust the edge structure is to the choice of estimator</strong> — the
 * productionized form of the ad-hoc cross-estimator analysis (Pearson / Spearman /
 * Kendall / partial) in the S&amp;P 500 investigation.
 *
 * <p>Marginal estimators live on different scales (Kendall's &tau; is systematically
 * smaller than Pearson's r), so a single absolute threshold would not compare like
 * with like. Instead the estimators are matched on <em>selectivity</em>: each
 * contributes its {@code topK} strongest edges by correlation magnitude. The result
 * (see {@link CrossEstimatorReport}) gives the pairwise top-K
 * {@linkplain CrossEstimatorReport#jaccard(int, int) Jaccard} overlap, the
 * {@linkplain CrossEstimatorReport#stableCore() stable core} (edges every estimator
 * agrees on — the gold core robust to the estimator choice) and the
 * {@linkplain CrossEstimatorReport#uniqueTo(int) estimator-unique} edges (the
 * outlier-sensitive links one estimator sees and the others do not).</p>
 *
 * <pre>{@code
 * CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(
 *         List.of(EstimatorMatrix.of("pearson",  pearson),
 *                 EstimatorMatrix.of("spearman", spearman),
 *                 EstimatorMatrix.of("kendall",  kendall),
 *                 EstimatorMatrix.of("partial",  partial)),
 *         tickers, 144);
 * System.out.println("gold core: " + r.stableCore().size() + " edges");
 * System.out.println("pearson/kendall overlap: " + r.jaccard(0, 2));
 * }</pre>
 *
 * <p>This is opt-in and orthogonal to the per-graph {@link ComparabilityAnalyzer}
 * result, which looks at a single matrix at a single threshold.</p>
 */
public final class CrossEstimatorAnalyzer {

    private CrossEstimatorAnalyzer() {
    }

    /**
     * Compares the estimators using index labels ({@code "0".."n-1"}).
     *
     * @param estimators the named correlation matrices to compare (at least two, same order)
     * @param topK       the selectivity — strongest edges taken per estimator (clamped to the pair count)
     * @return the cross-estimator robustness report
     * @throws IllegalArgumentException if {@code estimators} or any element is {@code null}
     * @throws InvalidInputException    if there are fewer than two estimators, their orders differ,
     *                                  the order is below two, or {@code topK < 1}
     */
    public static CrossEstimatorReport analyze(List<EstimatorMatrix> estimators, int topK) {
        return analyze(estimators, null, topK);
    }

    /**
     * Compares the estimators using explicit variable labels.
     *
     * @param estimators the named correlation matrices to compare (at least two, same order)
     * @param labels     one label per variable, or {@code null} to use index labels
     * @param topK       the selectivity — strongest edges taken per estimator (clamped to the pair count)
     * @return the cross-estimator robustness report
     * @throws IllegalArgumentException if {@code estimators} or any element is {@code null}
     * @throws InvalidInputException    if there are fewer than two estimators, their orders differ,
     *                                  the order is below two, {@code labels} has the wrong length,
     *                                  or {@code topK < 1}
     */
    public static CrossEstimatorReport analyze(List<EstimatorMatrix> estimators, String[] labels, int topK) {
        if (estimators == null) {
            throw new IllegalArgumentException("estimators must not be null");
        }
        if (estimators.size() < 2) {
            throw new InvalidInputException(
                    "need at least 2 estimators to compare, got [" + estimators.size() + "]");
        }
        if (topK < 1) {
            throw new InvalidInputException("topK must be at least 1, got [" + topK + "]");
        }
        int n = requireSharedOrder(estimators);
        if (n < 2) {
            throw new InvalidInputException("need at least 2 variables to compare, got [" + n + "]");
        }
        String[] names = resolveLabels(labels, n);

        int pairCount = n * (n - 1) / 2;
        int k = Math.min(topK, pairCount);

        List<Set<Long>> topSets = new ArrayList<>(estimators.size());
        for (EstimatorMatrix m : estimators) {
            topSets.add(topKEdges(m, n, k));
        }

        List<List<Double>> jaccard = jaccardMatrix(topSets);
        List<RobustEdge> edges = unionEdges(topSets, n, names);
        List<String> estimatorNames = estimators.stream().map(EstimatorMatrix::name).toList();
        return new CrossEstimatorReport(estimatorNames, k, jaccard, edges);
    }

    /** Verifies every estimator shares one order and returns it. */
    private static int requireSharedOrder(List<EstimatorMatrix> estimators) {
        int n = estimators.get(0).order();
        for (EstimatorMatrix m : estimators) {
            if (m.order() != n) {
                throw new InvalidInputException("all estimators must share the same order; estimator ["
                        + m.name() + "] has order [" + m.order() + "], expected [" + n + "]");
            }
        }
        return n;
    }

    private static String[] resolveLabels(String[] labels, int n) {
        if (labels == null) {
            String[] generated = new String[n];
            for (int i = 0; i < n; i++) {
                generated[i] = Integer.toString(i);
            }
            return generated;
        }
        if (labels.length != n) {
            throw new InvalidInputException(
                    "labels length [" + labels.length + "] does not match matrix order [" + n + "]");
        }
        return labels.clone();
    }

    /** The encoded {@code (i,j)} pairs of an estimator's {@code k} strongest edges by magnitude. */
    private static Set<Long> topKEdges(EstimatorMatrix m, int n, int k) {
        PairMagnitude[] all = new PairMagnitude[n * (n - 1) / 2];
        int idx = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                all[idx++] = new PairMagnitude(m.magnitude(i, j), encode(i, j, n));
            }
        }
        // Strongest first; ties broken by the (i,j) code so the cut is deterministic.
        java.util.Arrays.sort(all, BY_MAGNITUDE_DESC);
        Set<Long> top = new HashSet<>(k * 2);
        for (int t = 0; t < k; t++) {
            top.add(all[t].code());
        }
        return top;
    }

    private static final Comparator<PairMagnitude> BY_MAGNITUDE_DESC =
            Comparator.comparingDouble(PairMagnitude::magnitude).reversed()
                    .thenComparingLong(PairMagnitude::code);

    /** A vertex pair with the magnitude that ranks it within an estimator's top-K. */
    private record PairMagnitude(double magnitude, long code) {
    }

    private static List<List<Double>> jaccardMatrix(List<Set<Long>> topSets) {
        int e = topSets.size();
        List<List<Double>> matrix = new ArrayList<>(e);
        for (int i = 0; i < e; i++) {
            List<Double> row = new ArrayList<>(e);
            for (int j = 0; j < e; j++) {
                row.add(jaccard(topSets.get(i), topSets.get(j)));
            }
            matrix.add(row);
        }
        return matrix;
    }

    private static double jaccard(Set<Long> a, Set<Long> b) {
        int intersection = 0;
        Set<Long> smaller = a.size() <= b.size() ? a : b;
        Set<Long> larger = smaller == a ? b : a;
        for (Long code : smaller) {
            if (larger.contains(code)) {
                intersection++;
            }
        }
        int union = a.size() + b.size() - intersection;
        return union == 0 ? 1.0 : (double) intersection / union;
    }

    /** Every edge in at least one top-K set, support-annotated, ordered by descending support then endpoints. */
    private static List<RobustEdge> unionEdges(List<Set<Long>> topSets, int n, String[] names) {
        // TreeMap by code keeps a stable (source, target) order before the support re-sort.
        TreeMap<Long, List<Integer>> support = new TreeMap<>();
        for (int e = 0; e < topSets.size(); e++) {
            for (Long code : topSets.get(e)) {
                support.computeIfAbsent(code, c -> new ArrayList<>()).add(e);
            }
        }
        List<RobustEdge> edges = new ArrayList<>(support.size());
        for (var entry : support.entrySet()) {
            long code = entry.getKey();
            int source = (int) (code / n);
            int target = (int) (code % n);
            List<Integer> indices = entry.getValue(); // already ascending: estimators visited in order
            edges.add(new RobustEdge(source, target, names[source], names[target], indices));
        }
        edges.sort(Comparator.comparingInt(RobustEdge::support).reversed()
                .thenComparingInt(RobustEdge::source)
                .thenComparingInt(RobustEdge::target));
        return edges;
    }

    private static long encode(int i, int j, int n) {
        return (long) i * n + j;
    }
}
