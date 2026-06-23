package ch.tarvynanalytics.graphs.algos;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Differential test pinning the near-linear {@code fracture} builder
 * ({@link ModularDecomposition#useLinearBuilder()}) against the trusted simple
 * recursion (GAL-16, step 3). For a large random corpus (n = 6..14) and the named
 * graph families, the two builders must produce the <em>same modular decomposition
 * tree</em>.
 *
 * <p>The comparison is the canonical, order-invariant tree signature
 * ({@link ModularDecomposition#treeSignature()}): node kinds plus the sorted
 * signatures of their children. This is the right invariant — the orientation count
 * is a fold over this tree, and the factor-graph levels are a view of it, but the
 * <em>display order</em> of a node's children is not canonical (the two builders may
 * emit modules in a different order, which cascades into the order-dependent deeper
 * levels — see plan §7). The tree signature subsumes the count and the level-0
 * partition while being immune to that ordering. The orientation count is asserted
 * too, as an independent cross-check that also exercises the linear count path.</p>
 *
 * <p>The simple recursion is itself pinned to a brute-force oracle by
 * {@link OracleCharacterizationTest}, so agreement here transitively validates the
 * linear builder against the oracle.</p>
 */
class ModularDecompositionLinearDifferentialTest {

    @Test
    void linearBuilder_OverRandomGraphs_MatchesSimpleRecursion() {
        Random rnd = new Random(20260616L);
        for (int order = 6; order <= 14; order++) {
            for (int rep = 0; rep < 1200; rep++) {
                double p = 0.1 + rnd.nextDouble() * 0.8;
                boolean[][] adj = randomGraph(order, p, rnd);
                assertSameDecomposition("random n=" + order + " rep=" + rep, adj);
            }
        }
    }

    @Test
    void linearBuilder_OverNamedFamilies_MatchesSimpleRecursion() {
        for (int order = 1; order <= 12; order++) {
            assertSameDecomposition("complete K" + order, complete(order));
            assertSameDecomposition("empty E" + order, new boolean[order][order]);
        }
        for (int order = 4; order <= 12; order++) {
            assertSameDecomposition("path P" + order, path(order));
            assertSameDecomposition("cycle C" + order, cycle(order));
        }
        // Cographs (disjoint unions / joins of cliques) exercise the series/parallel cases.
        assertSameDecomposition("2K2", disjointCliques(2, 2));
        assertSameDecomposition("3K3", disjointCliques(3, 3));
        assertSameDecomposition("K2+K3+K4", disjointCliques(2, 3, 4));
        // The named graphs the oracle test pins, which stress the prime case.
        assertSameDecomposition("diamond", n(4, new int[][]{{0, 1}, {0, 2}, {0, 3}, {1, 2}, {1, 3}}));
        assertSameDecomposition("bull", n(5, new int[][]{{0, 1}, {1, 2}, {0, 2}, {3, 0}, {4, 1}}));
        assertSameDecomposition("gem", n(5, new int[][]{{0, 4}, {1, 4}, {2, 4}, {3, 4}, {0, 1}, {1, 2}, {2, 3}}));
        assertSameDecomposition("net", n(6, new int[][]{{0, 1}, {1, 2}, {0, 2}, {3, 0}, {4, 1}, {5, 2}}));
        assertSameDecomposition("3-sun",
                n(6, new int[][]{{0, 1}, {1, 2}, {0, 2}, {3, 0}, {3, 1}, {4, 1}, {4, 2}, {5, 0}, {5, 2}}));
        assertSameDecomposition("domino", n(6, new int[][]{{0, 1}, {1, 2}, {2, 3}, {3, 0}, {1, 4}, {4, 5}, {5, 2}}));
        assertSameDecomposition("K3,3",
                n(6, new int[][]{{0, 3}, {0, 4}, {0, 5}, {1, 3}, {1, 4}, {1, 5}, {2, 3}, {2, 4}, {2, 5}}));
    }

    @Test
    void linearBuilder_OnSingletonAndEmptyGraph_MatchesSimpleRecursion() {
        assertSameDecomposition("single vertex", new boolean[1][1]);
        assertSameDecomposition("two isolated", new boolean[2][2]);
        assertSameDecomposition("single edge", complete(2));
    }

    // ---- the differential assertion ----------------------------------------

    private static void assertSameDecomposition(String name, boolean[][] adj) {
        String simpleTree = new ModularDecomposition(adj).treeSignature();
        String linearTree = new ModularDecomposition(adj).useLinearBuilder().treeSignature();
        assertEquals(simpleTree, linearTree, "decomposition tree for " + name);

        BigInteger simpleCount = new ModularDecomposition(adj).orientationCount();
        BigInteger linearCount = new ModularDecomposition(adj).useLinearBuilder().orientationCount();
        assertEquals(simpleCount, linearCount, "orientation count for " + name);
    }

    // ---- graph fixtures (built from graph theory, not the engine) ----------

    private static boolean[][] randomGraph(int order, double p, Random rnd) {
        boolean[][] adj = new boolean[order][order];
        for (int i = 0; i < order; i++) {
            for (int j = i + 1; j < order; j++) {
                if (rnd.nextDouble() < p) {
                    adj[i][j] = true;
                    adj[j][i] = true;
                }
            }
        }
        return adj;
    }

    private static boolean[][] complete(int order) {
        boolean[][] adj = new boolean[order][order];
        for (int i = 0; i < order; i++) {
            for (int j = i + 1; j < order; j++) {
                adj[i][j] = true;
                adj[j][i] = true;
            }
        }
        return adj;
    }

    private static boolean[][] path(int order) {
        boolean[][] adj = new boolean[order][order];
        for (int i = 0; i + 1 < order; i++) {
            adj[i][i + 1] = true;
            adj[i + 1][i] = true;
        }
        return adj;
    }

    private static boolean[][] cycle(int order) {
        boolean[][] adj = path(order);
        adj[0][order - 1] = true;
        adj[order - 1][0] = true;
        return adj;
    }

    private static boolean[][] disjointCliques(int... sizes) {
        int order = 0;
        for (int s : sizes) {
            order += s;
        }
        boolean[][] adj = new boolean[order][order];
        int base = 0;
        for (int s : sizes) {
            for (int i = 0; i < s; i++) {
                for (int j = i + 1; j < s; j++) {
                    adj[base + i][base + j] = true;
                    adj[base + j][base + i] = true;
                }
            }
            base += s;
        }
        return adj;
    }

    private static boolean[][] n(int order, int[][] edges) {
        boolean[][] adj = new boolean[order][order];
        for (int[] e : edges) {
            adj[e[0]][e[1]] = true;
            adj[e[1]][e[0]] = true;
        }
        return adj;
    }
}
