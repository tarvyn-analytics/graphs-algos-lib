package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;

/**
 * Entry point of the library: decides whether a graph is a comparability graph
 * (transitively orientable) and returns the full {@link AnalysisResult}.
 *
 * <p>Typical use:</p>
 * <pre>{@code
 * double[][] correlation = ...;          // square, symmetric
 * AnalysisResult result = ComparabilityAnalyzer.analyze(
 *         GraphInput.fromCorrelation(correlation, 0.5));
 *
 * if (result.isComparability()) {
 *     System.out.println("transitive orientations: " + result.transitiveOrientationCount());
 * } else {
 *     System.out.println("obstructing odd cycle: " + result.failure().orElseThrow().nodeLabels());
 * }
 * }</pre>
 *
 * <p>The analyzer is stateless and thread-safe; each call builds its own working
 * graph from the input, so a single instance (or the static {@link #analyze}
 * helper) can be shared freely.</p>
 */
public final class ComparabilityAnalyzer {

    /**
     * Analyses the given input with a fresh analyzer.
     *
     * @param input the graph to analyse
     * @return the analysis result
     */
    public static AnalysisResult analyze(GraphInput input) {
        return new ComparabilityAnalyzer().run(input);
    }

    /**
     * Creates an analyzer. Instances are stateless and reusable.
     */
    public ComparabilityAnalyzer() {
        // stateless
    }

    /**
     * Analyses the given input.
     *
     * @param input the graph to analyse
     * @return the analysis result
     */
    public AnalysisResult run(GraphInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        return ResultBuilder.build(input);
    }
}
