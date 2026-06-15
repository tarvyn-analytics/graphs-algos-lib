package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.exception.InvalidInputException;

import java.util.Arrays;

/**
 * Immutable description of the undirected graph to analyse, built from a square
 * matrix. Two flavours of input are supported:
 *
 * <ul>
 *   <li>a <strong>correlation matrix</strong> plus a threshold — an undirected
 *       edge {@code (i, j)} exists iff {@code i != j} and
 *       {@code |m[i][j]| > |threshold|} (mirrors the thesis prototype, where a
 *       value at or below the threshold is treated as no correlation). The
 *       matrix is retained so the weakest correlation along a failure cycle can
 *       be reported;</li>
 *   <li>a boolean <strong>adjacency matrix</strong> — an edge exists iff
 *       {@code adjacency[i][j]} (the diagonal is ignored). No correlation
 *       values are available, so a failure cycle reports
 *       {@link Double#NaN NaN} for its weakest edge.</li>
 * </ul>
 *
 * <p>Inputs are expected to be symmetric; to be forgiving, an edge is created
 * when the relationship holds in <em>either</em> direction. The instance is
 * fully defensive — callers may keep mutating their arrays after construction.</p>
 */
public final class GraphInput {

    private final int order;
    private final boolean[][] adjacency;
    private final double[][] correlation; // null when built from a boolean adjacency matrix
    private final String[] labels;

    private GraphInput(int order, boolean[][] adjacency, double[][] correlation, String[] labels) {
        this.order = order;
        this.adjacency = adjacency;
        this.correlation = correlation;
        this.labels = labels;
    }

    /**
     * Builds an input from a correlation matrix, using index labels.
     *
     * @param correlation square, symmetric matrix of correlation coefficients
     * @param threshold   magnitude below or equal to which a value is treated as
     *                    no edge ({@code |m[i][j]| > |threshold|} creates an edge)
     * @return the graph input
     * @throws InvalidInputException if the matrix is not square or contains non-finite values
     */
    public static GraphInput fromCorrelation(double[][] correlation, double threshold) {
        return fromCorrelation(correlation, threshold, null);
    }

    /**
     * Builds an input from a correlation matrix with explicit vertex labels.
     *
     * @param correlation square, symmetric matrix of correlation coefficients
     * @param threshold   magnitude below or equal to which a value is treated as no edge
     * @param labels      one label per vertex, or {@code null} to use index labels
     * @return the graph input
     * @throws InvalidInputException if the matrix is malformed or {@code labels} has the wrong length
     */
    public static GraphInput fromCorrelation(double[][] correlation, double threshold, String[] labels) {
        int n = requireSquare(correlation);
        double[][] corr = new double[n][n];
        boolean[][] adj = new boolean[n][n];
        double cut = Math.abs(threshold);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double v = correlation[i][j];
                if (!Double.isFinite(v)) {
                    throw new InvalidInputException(
                            "correlation matrix contains a non-finite value at [" + i + "][" + j + "]: " + v);
                }
                corr[i][j] = v;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean edge = Math.abs(corr[i][j]) > cut || Math.abs(corr[j][i]) > cut;
                adj[i][j] = edge;
                adj[j][i] = edge;
            }
        }
        return new GraphInput(n, adj, corr, normalizeLabels(labels, n));
    }

    /**
     * Builds an input from a boolean adjacency matrix, using index labels.
     *
     * @param adjacency square adjacency matrix (the diagonal is ignored)
     * @return the graph input
     * @throws InvalidInputException if the matrix is not square
     */
    public static GraphInput fromAdjacency(boolean[][] adjacency) {
        return fromAdjacency(adjacency, null);
    }

    /**
     * Builds an input from a boolean adjacency matrix with explicit labels.
     *
     * @param adjacency square adjacency matrix (the diagonal is ignored)
     * @param labels    one label per vertex, or {@code null} to use index labels
     * @return the graph input
     * @throws InvalidInputException if the matrix is not square or {@code labels} has the wrong length
     */
    public static GraphInput fromAdjacency(boolean[][] adjacency, String[] labels) {
        int n = requireSquare(adjacency);
        boolean[][] adj = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                boolean edge = adjacency[i][j] || adjacency[j][i];
                adj[i][j] = edge;
                adj[j][i] = edge;
            }
        }
        return new GraphInput(n, adj, null, normalizeLabels(labels, n));
    }

    private static int requireSquare(double[][] m) {
        if (m == null) {
            throw new InvalidInputException("matrix must not be null");
        }
        int n = m.length;
        for (int i = 0; i < n; i++) {
            if (m[i] == null || m[i].length != n) {
                int len = m[i] == null ? -1 : m[i].length;
                throw new InvalidInputException(
                        "matrix must be square; row " + i + " has length [" + len + "], expected [" + n + "]");
            }
        }
        return n;
    }

    private static int requireSquare(boolean[][] m) {
        if (m == null) {
            throw new InvalidInputException("matrix must not be null");
        }
        int n = m.length;
        for (int i = 0; i < n; i++) {
            if (m[i] == null || m[i].length != n) {
                int len = m[i] == null ? -1 : m[i].length;
                throw new InvalidInputException(
                        "matrix must be square; row " + i + " has length [" + len + "], expected [" + n + "]");
            }
        }
        return n;
    }

    private static String[] normalizeLabels(String[] labels, int n) {
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

    /**
     * @return the number of vertices
     */
    public int order() {
        return order;
    }

    /**
     * @return {@code true} iff vertices {@code i} and {@code j} are adjacent
     */
    boolean adjacent(int i, int j) {
        return adjacency[i][j];
    }

    /**
     * @return {@code true} iff correlation values are available (correlation input)
     */
    boolean hasCorrelation() {
        return correlation != null;
    }

    /**
     * @return the correlation between {@code i} and {@code j}, or {@code NaN}
     *         when the input was a plain adjacency matrix
     */
    double correlation(int i, int j) {
        return correlation == null ? Double.NaN : correlation[i][j];
    }

    /**
     * @return the label of vertex {@code i}
     */
    String label(int i) {
        return labels[i];
    }

    /**
     * @return a private copy of the vertex labels
     */
    String[] labels() {
        return labels.clone();
    }

    @Override
    public String toString() {
        return "GraphInput{order=" + order
                + ", hasCorrelation=" + hasCorrelation()
                + ", labels=" + Arrays.toString(labels) + '}';
    }
}
