package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.ChangeMetrics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Computes the Initiative-S S3 structural-change metric and its secondary
 * features for one transition {@code C_{t-1} -> C_t}, in a single
 * {@code O(m^2)} pass over the upper triangle. Package-private engine behind
 * {@link ChangeMetricsAnalyzer}; see that class for the public contract.
 *
 * <p><strong>The two denominators are deliberately different and must never be
 * swapped:</strong> {@link #weightedChange} and {@code edgeXor} normalize by the
 * <em>intersection</em> valid-pair count {@code |V|} (pairs finite in both
 * matrices); {@code densityLevel} normalizes by {@code |P|}, the count of
 * <em>all</em> pairs. A value is "finite" iff it is not {@code NaN} and not
 * {@code ±Infinity}; both are treated identically as invalid.</p>
 */
final class ChangeMetricsEngine {

    private ChangeMetricsEngine() {
    }

    /**
     * Computes the change metrics for one transition.
     *
     * @param previous  {@code C_{t-1}}, square, order {@code m}
     * @param current   {@code C_t}, square, order {@code m}
     * @param threshold edge magnitude threshold (strict {@code |r| > threshold} creates an edge)
     * @return the immutable change-metric record
     */
    static ChangeMetrics compute(double[][] previous, double[][] current, double threshold) {
        int m = current.length;
        double cut = Math.abs(threshold);

        double sumAbsDelta = 0.0;
        long validPairCount = 0;
        long allPairCount = 0;
        long densityEdgeCount = 0;
        long xorEdgeCount = 0;

        boolean[][] densityEdge = new boolean[m][m]; // C_t edges, |r| > threshold, over ALL pairs

        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                allPairCount++;
                double a = previous[i][j];
                double b = current[i][j];
                boolean aFinite = Double.isFinite(a);
                boolean bFinite = Double.isFinite(b);

                if (bFinite && Math.abs(b) > cut) {
                    densityEdge[i][j] = true;
                    densityEdge[j][i] = true;
                    densityEdgeCount++;
                }

                if (aFinite && bFinite) {
                    validPairCount++;
                    sumAbsDelta += Math.abs(b - a);
                    boolean edgeNow = Math.abs(b) > cut;
                    boolean edgeBefore = Math.abs(a) > cut;
                    if (edgeNow != edgeBefore) {
                        xorEdgeCount++;
                    }
                }
            }
        }

        double weightedChange = validPairCount > 0 ? sumAbsDelta / validPairCount : Double.NaN;
        double densityLevel = allPairCount > 0 ? (double) densityEdgeCount / allPairCount : Double.NaN;
        double edgeXor = validPairCount > 0 ? (double) xorEdgeCount / validPairCount : Double.NaN;

        ClusterResult cluster = clusterSignal(m, densityEdge);

        return new ChangeMetrics(weightedChange, densityLevel, edgeXor,
                cluster.nComponents, cluster.largestComponentFraction, cluster.componentSizes);
    }

    /** Connected components of the density edge set, via hand-rolled union-find (zero deps). */
    private static ClusterResult clusterSignal(int m, boolean[][] densityEdge) {
        UnionFind uf = new UnionFind(m);
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if (densityEdge[i][j]) {
                    uf.union(i, j);
                }
            }
        }
        int[] sizeByRoot = new int[m];
        for (int v = 0; v < m; v++) {
            sizeByRoot[uf.find(v)]++;
        }
        List<Integer> sizes = new ArrayList<>();
        for (int v = 0; v < m; v++) {
            if (sizeByRoot[v] > 0) {
                sizes.add(sizeByRoot[v]);
            }
        }
        sizes.sort(Collections.reverseOrder());
        int nComponents = sizes.size();
        double largestFraction = m == 0 ? Double.NaN : (double) sizes.get(0) / m;
        return new ClusterResult(nComponents, largestFraction, sizes);
    }

    /** Small holder for the three cluster outputs before they are folded into {@link ChangeMetrics}. */
    private static final class ClusterResult {
        final int nComponents;
        final double largestComponentFraction;
        final List<Integer> componentSizes;

        ClusterResult(int nComponents, double largestComponentFraction, List<Integer> componentSizes) {
            this.nComponents = nComponents;
            this.largestComponentFraction = largestComponentFraction;
            this.componentSizes = componentSizes;
        }
    }

    /** Union-find with path compression, mirroring the spike's {@code _UnionFind} (zero deps). */
    private static final class UnionFind {
        private final int[] parent;

        UnionFind(int n) {
            parent = new int[n];
            Arrays.setAll(parent, i -> i);
        }

        int find(int x) {
            int root = x;
            while (parent[root] != root) {
                root = parent[root];
            }
            while (parent[x] != root) {
                int next = parent[x];
                parent[x] = root;
                x = next;
            }
            return root;
        }

        void union(int a, int b) {
            int ra = find(a);
            int rb = find(b);
            if (ra != rb) {
                parent[ra] = rb;
            }
        }
    }
}
