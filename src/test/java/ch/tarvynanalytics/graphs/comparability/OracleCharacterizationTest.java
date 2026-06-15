package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cross-checks the engine against a brute-force ground-truth oracle that counts
 * transitive orientations directly from the definition. This is the correctness
 * net for the Golumbic Γ verdict and the modular-decomposition count (CGD-6):
 * over <em>every</em> labeled graph on up to five vertices the verdict and the
 * orientation count must match exactly, and a set of named families and the
 * specific small graphs that the old thesis engine misclassified (CGD-5) are
 * checked explicitly.
 */
class OracleCharacterizationTest {

    // ---- brute-force oracle ------------------------------------------------

    /** Exact number of transitive orientations; 0 iff not a comparability graph. */
    private static long oracleCount(boolean[][] adj) {
        int n = adj.length;
        List<int[]> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adj[i][j]) {
                    edges.add(new int[]{i, j});
                }
            }
        }
        int m = edges.size();
        long count = 0;
        for (long mask = 0; mask < (1L << m); mask++) {
            boolean[][] d = new boolean[n][n];
            for (int e = 0; e < m; e++) {
                int a = edges.get(e)[0];
                int b = edges.get(e)[1];
                if (((mask >> e) & 1L) == 0) {
                    d[a][b] = true;
                } else {
                    d[b][a] = true;
                }
            }
            if (isTransitive(d, n)) {
                count++;
            }
        }
        return count;
    }

    private static boolean isTransitive(boolean[][] d, int n) {
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                if (d[x][y]) {
                    for (int z = 0; z < n; z++) {
                        if (d[y][z] && !d[x][z]) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    // ---- exhaustive small graphs ------------------------------------------

    @Test
    void everyLabeledGraphUpToFiveVertices_MatchesOracle_VerdictAndCount() {
        for (int n = 1; n <= 5; n++) {
            int edgeSlots = n * (n - 1) / 2;
            int[][] pair = new int[edgeSlots][2];
            int idx = 0;
            for (int i = 0; i < n; i++) {
                for (int j = i + 1; j < n; j++) {
                    pair[idx][0] = i;
                    pair[idx][1] = j;
                    idx++;
                }
            }
            for (long mask = 0; mask < (1L << edgeSlots); mask++) {
                boolean[][] a = new boolean[n][n];
                for (int e = 0; e < edgeSlots; e++) {
                    if (((mask >> e) & 1L) != 0) {
                        a[pair[e][0]][pair[e][1]] = true;
                        a[pair[e][1]][pair[e][0]] = true;
                    }
                }
                AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(a));
                long oracle = oracleCount(a);
                String g = "n=" + n + " edges=" + edges(a);
                assertEquals(oracle > 0, r.isComparability(), "verdict for " + g);
                assertEquals(BigInteger.valueOf(oracle), r.transitiveOrientationCount(), "count for " + g);
            }
        }
    }

    // ---- named families and the former-divergence regressions --------------

    @Test
    void namedFamilies_MatchOracle() {
        assertMatches("diamond K(1,1,2)", n(4, new int[][]{{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}}));
        assertMatches("bull", n(5, new int[][]{{0, 1}, {1, 2}, {0, 2}, {3, 0}, {4, 1}}));
        assertMatches("gem", n(5, new int[][]{{0, 4}, {1, 4}, {2, 4}, {3, 4}, {0, 1}, {1, 2}, {2, 3}}));
        assertMatches("net", n(6, new int[][]{{0, 1}, {1, 2}, {0, 2}, {3, 0}, {4, 1}, {5, 2}}));
        assertMatches("3-sun S3", sun3());
        assertMatches("domino", n(6, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}, {1, 4}, {4, 5}, {5, 2}}));
        assertMatches("K(3,3)", n(6, new int[][]{{0, 3}, {0, 4}, {0, 5}, {1, 3}, {1, 4}, {1, 5}, {2, 3}, {2, 4}, {2, 5}}));
    }

    @Test
    void formerThesisEngineDivergences_AreNowCorrect() {
        // 3-sun: thesis engine accepted it (false positive). Truth: not comparability.
        AnalysisResult sun = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(sun3()));
        assertEquals(false, sun.isComparability(), "3-sun is not a comparability graph");

        // n=6 graph the thesis engine rejected (false negative). Truth: comparability.
        boolean[][] fn = n(6, new int[][]{{0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 2}, {1, 4}, {1, 5}, {2, 3}});
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(fn));
        assertEquals(true, r.isComparability(), "this graph IS a comparability graph");
        assertEquals(oracleCount(fn) > 0, r.isComparability());

        // n=6 graph the thesis engine accepted (false positive). Truth: not comparability.
        boolean[][] fp = n(6, new int[][]{{0, 1}, {0, 2}, {0, 4}, {0, 5}, {1, 2}, {1, 3}, {1, 5}, {2, 3}, {2, 4}});
        assertEquals(false, ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(fp)).isComparability());
    }

    // ---- helpers -----------------------------------------------------------

    private static void assertMatches(String name, boolean[][] a) {
        AnalysisResult r = ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(a));
        long oracle = oracleCount(a);
        assertEquals(oracle > 0, r.isComparability(), "verdict for " + name);
        assertEquals(BigInteger.valueOf(oracle), r.transitiveOrientationCount(), "count for " + name);
    }

    private static boolean[][] n(int order, int[][] edges) {
        boolean[][] a = new boolean[order][order];
        for (int[] e : edges) {
            a[e[0]][e[1]] = true;
            a[e[1]][e[0]] = true;
        }
        return a;
    }

    private static boolean[][] sun3() {
        return n(6, new int[][]{{0, 1}, {1, 2}, {0, 2}, {3, 0}, {3, 1}, {4, 1}, {4, 2}, {5, 0}, {5, 2}});
    }

    private static String edges(boolean[][] a) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < a.length; i++) {
            for (int j = i + 1; j < a.length; j++) {
                if (a[i][j]) {
                    sb.append(i).append('-').append(j).append(' ');
                }
            }
        }
        return sb.toString();
    }
}
