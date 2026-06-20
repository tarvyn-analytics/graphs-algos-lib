package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.exception.InvalidInputException;

/**
 * One <em>named</em> correlation matrix — an estimator's view of the same set of
 * variables — for the cross-estimator robustness comparison in
 * {@link CrossEstimatorAnalyzer}. The name (e.g. {@code "pearson"},
 * {@code "spearman"}, {@code "kendall"}, {@code "partial"}) labels the estimator
 * in the resulting Jaccard table and the estimator-unique edge flags.
 *
 * <p>Unlike {@link GraphInput} this carries no threshold: the cross-estimator
 * comparison ranks edges by correlation <em>magnitude</em> and matches the
 * estimators on selectivity (each one's top-K strongest edges), because marginal
 * estimators live on different scales (Kendall's &tau; is systematically smaller),
 * so a single absolute threshold would not compare like with like.</p>
 *
 * <p>The matrix is copied defensively, so callers may keep mutating their array
 * after construction.</p>
 */
public final class EstimatorMatrix {

    private final String name;
    private final double[][] correlation;

    private EstimatorMatrix(String name, double[][] correlation) {
        this.name = name;
        this.correlation = correlation;
    }

    /**
     * Builds a named estimator matrix.
     *
     * @param name        the estimator's name (non-blank)
     * @param correlation square, symmetric matrix of correlation coefficients
     * @return the estimator matrix
     * @throws IllegalArgumentException if {@code name} or {@code correlation} is {@code null}
     * @throws InvalidInputException    if {@code name} is blank, or the matrix is not square or
     *                                  contains a non-finite value
     */
    public static EstimatorMatrix of(String name, double[][] correlation) {
        if (name == null) {
            throw new IllegalArgumentException("name must not be null");
        }
        if (correlation == null) {
            throw new IllegalArgumentException("correlation must not be null");
        }
        if (name.isBlank()) {
            throw new InvalidInputException("estimator name must not be blank, got [" + name + "]");
        }
        int n = correlation.length;
        double[][] copy = new double[n][n];
        for (int i = 0; i < n; i++) {
            if (correlation[i] == null || correlation[i].length != n) {
                int len = correlation[i] == null ? -1 : correlation[i].length;
                throw new InvalidInputException("estimator [" + name + "] matrix must be square; row " + i
                        + " has length [" + len + "], expected [" + n + "]");
            }
            for (int j = 0; j < n; j++) {
                double v = correlation[i][j];
                if (!Double.isFinite(v)) {
                    throw new InvalidInputException("estimator [" + name + "] matrix contains a non-finite value at ["
                            + i + "][" + j + "]: " + v);
                }
                copy[i][j] = v;
            }
        }
        return new EstimatorMatrix(name, copy);
    }

    /**
     * @return the estimator's name
     */
    public String name() {
        return name;
    }

    /**
     * @return the number of variables (the matrix order)
     */
    public int order() {
        return correlation.length;
    }

    /**
     * @return the correlation magnitude {@code |m[i][j]|} between variables {@code i} and {@code j}
     */
    double magnitude(int i, int j) {
        return Math.abs(correlation[i][j]);
    }

    @Override
    public String toString() {
        return "EstimatorMatrix{name=" + name + ", order=" + order() + '}';
    }
}
