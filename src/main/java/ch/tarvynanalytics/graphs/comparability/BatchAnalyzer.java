package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;

import java.util.List;
import java.util.Objects;

/**
 * Runs many <em>independent</em> comparability analyses, optionally in parallel.
 *
 * <p>A single analysis is already fast and its engine is inherently sequential,
 * so there is no intra-analysis parallelism — the worthwhile parallelism is at the
 * <strong>batch</strong> level: threshold sweeps, rolling time windows and
 * Monte-Carlo / bootstrap runs are all embarrassingly parallel across independent
 * {@link ComparabilityAnalyzer#analyze} calls. {@code analyze} is stateless and
 * thread-safe (each call builds its own working graph from an immutable
 * {@link GraphInput} and returns an immutable {@link AnalysisResult}), so the
 * parallel variants simply fan the calls out over the common {@code ForkJoinPool}.</p>
 *
 * <p>Parallelism is never silent: the {@code *Parallel} methods opt in explicitly
 * and fall back to sequential below {@link #MIN_PARALLEL_BATCH} inputs, where the
 * fork/join overhead would outweigh the gain. Every method preserves input order
 * and returns an unmodifiable list.</p>
 */
public final class BatchAnalyzer {

    /** Batches smaller than this run sequentially even via the parallel methods. */
    public static final int MIN_PARALLEL_BATCH = 8;

    private BatchAnalyzer() {
    }

    /**
     * Analyses each input sequentially.
     *
     * @param inputs the graphs to analyse
     * @return the results, one per input, in input order
     */
    public static List<AnalysisResult> analyzeAll(List<GraphInput> inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        return inputs.stream().map(ComparabilityAnalyzer::analyze).toList();
    }

    /**
     * Analyses the inputs in parallel over the common {@code ForkJoinPool} (falling
     * back to sequential below {@link #MIN_PARALLEL_BATCH}).
     *
     * @param inputs the graphs to analyse
     * @return the results, one per input, in input order
     */
    public static List<AnalysisResult> analyzeAllParallel(List<GraphInput> inputs) {
        Objects.requireNonNull(inputs, "inputs must not be null");
        if (inputs.size() < MIN_PARALLEL_BATCH) {
            return analyzeAll(inputs);
        }
        return inputs.parallelStream().map(ComparabilityAnalyzer::analyze).toList();
    }

    /**
     * Analyses one correlation matrix at each of several thresholds — the thesis's
     * threshold-raising workflow (an edge exists when {@code |corr| > |threshold|}).
     *
     * @param correlation square, symmetric correlation matrix
     * @param thresholds  the thresholds to apply
     * @return the results, one per threshold, in {@code thresholds} order
     */
    public static List<AnalysisResult> thresholdSweep(double[][] correlation, double[] thresholds) {
        return analyzeAll(sweepInputs(correlation, thresholds));
    }

    /**
     * Parallel {@link #thresholdSweep(double[][], double[])}.
     *
     * @param correlation square, symmetric correlation matrix
     * @param thresholds  the thresholds to apply
     * @return the results, one per threshold, in {@code thresholds} order
     */
    public static List<AnalysisResult> thresholdSweepParallel(double[][] correlation, double[] thresholds) {
        return analyzeAllParallel(sweepInputs(correlation, thresholds));
    }

    private static List<GraphInput> sweepInputs(double[][] correlation, double[] thresholds) {
        Objects.requireNonNull(thresholds, "thresholds must not be null");
        return java.util.Arrays.stream(thresholds)
                .mapToObj(t -> GraphInput.fromCorrelation(correlation, t))
                .toList();
    }
}
