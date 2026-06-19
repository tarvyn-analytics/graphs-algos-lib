package ch.tarvynanalytics.graphs.comparability;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link Chordality} against a brute-force ground-truth oracle, per
 * invariant&nbsp;#2. The oracle decides chordality straight from the definition —
 * a graph is chordal iff no vertex subset induces a hole, and a subset induces a
 * hole iff it has &ge; 4 vertices, is connected and every vertex has degree
 * exactly two inside it. Over <em>every</em> labeled graph on up to six vertices
 * the verdict must match, the reported hole must be a genuine chordless cycle, and
 * the chordal completion must be chordal, a supergraph of the input, and admit the
 * reported perfect elimination ordering.
 */
class ChordalityTest {

    // ---- brute-force oracle ------------------------------------------------

    /** Whether the graph is chordal: no induced subgraph is a chordless cycle of length &ge; 4. */
    private static boolean oracleChordal(boolean[][] adj) {
        int n = adj.length;
        for (int mask = 1; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) < 4) {
                continue;
            }
            if (inducesHole(adj, mask)) {
                return false;
            }
        }
        return true;
    }

    /** Whether the vertex set {@code mask} induces a single chordless cycle (2-regular and connected). */
    private static boolean inducesHole(boolean[][] adj, int mask) {
        List<Integer> s = new ArrayList<>();
        for (int v = 0; v < adj.length; v++) {
            if ((mask & (1 << v)) != 0) {
                s.add(v);
            }
        }
        for (int v : s) {
            int degree = 0;
            for (int u : s) {
                if (u != v && adj[v][u]) {
                    degree++;
                }
            }
            if (degree != 2) {
                return false; // not 2-regular -> not a single induced cycle
            }
        }
        return connected(adj, s); // a connected 2-regular induced graph is one chordless cycle
    }

    private static boolean connected(boolean[][] adj, List<Integer> s) {
        Deque<Integer> queue = new ArrayDeque<>();
        List<Integer> seen = new ArrayList<>();
        queue.add(s.get(0));
        seen.add(s.get(0));
        while (!queue.isEmpty()) {
            int v = queue.poll();
            for (int u : s) {
                if (adj[v][u] && !seen.contains(u)) {
                    seen.add(u);
                    queue.add(u);
                }
            }
        }
        return seen.size() == s.size();
    }

    // ---- exhaustive small graphs ------------------------------------------

    @Test
    void everyLabeledGraphUpToSixVertices_MatchesOracle_AndProducesValidWitnessAndCompletion() {
        for (int n = 1; n <= 6; n++) {
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
            for (int mask = 0; mask < (1 << edgeSlots); mask++) {
                boolean[][] a = new boolean[n][n];
                for (int e = 0; e < edgeSlots; e++) {
                    if ((mask & (1 << e)) != 0) {
                        a[pair[e][0]][pair[e][1]] = true;
                        a[pair[e][1]][pair[e][0]] = true;
                    }
                }
                Chordality.Result r = Chordality.analyze(a);
                boolean oracle = oracleChordal(a);
                String g = "n=" + n + " edges=" + edges(a);
                assertEquals(oracle, r.chordal, "verdict for " + g);
                if (r.chordal) {
                    assertTrue(isPermutation(r.eliminationOrder, n), "PEO is a permutation for " + g);
                    assertTrue(isPerfectEliminationOrder(a, r.eliminationOrder), "valid PEO for " + g);
                    assertEquals(0, r.chordlessCycle.length, "no hole when chordal for " + g);
                    assertEquals(0, r.fillIn.length, "no fill-in when chordal for " + g);
                } else {
                    assertTrue(isHole(a, r.chordlessCycle), "valid hole for " + g);
                    boolean[][] filled = applyFillIn(a, r.fillIn);
                    assertTrue(isSupergraph(filled, a), "completion keeps all input edges for " + g);
                    assertTrue(oracleChordal(filled), "completion is chordal for " + g);
                    assertTrue(isPerfectEliminationOrder(filled, r.eliminationOrder),
                            "elimination order is a PEO of the completion for " + g);
                }
            }
        }
    }

    // ---- named families ----------------------------------------------------

    @Test
    void completeGraphsAndTreesAndTheThreeSun_AreChordal() {
        for (int n = 1; n <= 6; n++) {
            assertTrue(Chordality.analyze(complete(n)).chordal, "K" + n + " is chordal");
            assertTrue(Chordality.analyze(path(n)).chordal, "P" + n + " is chordal");
        }
        // The 3-sun is chordal yet NOT a comparability graph — the converse pairing.
        assertTrue(Chordality.analyze(threeSun()).chordal, "the 3-sun is chordal");
    }

    @Test
    void holes_AreNotChordal_AndReportThemselves() {
        for (int k : new int[]{4, 5, 6}) {
            Chordality.Result r = Chordality.analyze(cycle(k));
            assertFalse(r.chordal, "C" + k + " is not chordal");
            assertTrue(isHole(cycle(k), r.chordlessCycle), "C" + k + " reports a hole");
            assertEquals(k, r.chordlessCycle.length, "the whole C" + k + " is the hole");
        }
    }

    @Test
    void fiveCycle_CompletesWithTwoFillInEdges() {
        // Triangulating C5 needs exactly two chords (any triangulation of an n-gon adds n-3).
        Chordality.Result r = Chordality.analyze(cycle(5));
        assertFalse(r.chordal);
        assertEquals(2, r.fillIn.length, "C5 triangulation adds n-3 = 2 chords");
        assertTrue(oracleChordal(applyFillIn(cycle(5), r.fillIn)), "the completion is chordal");
    }

    // ---- witness / PEO validators -----------------------------------------

    private static boolean isPerfectEliminationOrder(boolean[][] adj, int[] order) {
        int n = adj.length;
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) {
            pos[order[i]] = i;
        }
        for (int i = 0; i < n; i++) {
            int v = order[i];
            List<Integer> later = new ArrayList<>();
            for (int u = 0; u < n; u++) {
                if (adj[v][u] && pos[u] > i) {
                    later.add(u);
                }
            }
            for (int a = 0; a < later.size(); a++) {
                for (int b = a + 1; b < later.size(); b++) {
                    if (!adj[later.get(a)][later.get(b)]) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** Whether {@code cycle} is a chordless cycle of length &ge; 4: distinct, consecutive-adjacent, no chords. */
    private static boolean isHole(boolean[][] adj, int[] cycle) {
        int k = cycle.length;
        if (k < 4) {
            return false;
        }
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                if (cycle[i] == cycle[j]) {
                    return false; // vertices must be distinct
                }
                boolean consecutive = (j == i + 1) || (i == 0 && j == k - 1);
                if (adj[cycle[i]][cycle[j]] != consecutive) {
                    return false; // edges exactly on the cycle, no chords
                }
            }
        }
        return true;
    }

    private static boolean isPermutation(int[] order, int n) {
        if (order.length != n) {
            return false;
        }
        boolean[] seen = new boolean[n];
        for (int v : order) {
            if (v < 0 || v >= n || seen[v]) {
                return false;
            }
            seen[v] = true;
        }
        return true;
    }

    private static boolean[][] applyFillIn(boolean[][] adj, int[][] fillIn) {
        int n = adj.length;
        boolean[][] filled = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            filled[i] = adj[i].clone();
        }
        for (int[] e : fillIn) {
            filled[e[0]][e[1]] = true;
            filled[e[1]][e[0]] = true;
        }
        return filled;
    }

    private static boolean isSupergraph(boolean[][] big, boolean[][] small) {
        int n = small.length;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (small[i][j] && !big[i][j]) {
                    return false;
                }
            }
        }
        return true;
    }

    // ---- graph builders ----------------------------------------------------

    private static boolean[][] complete(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    a[i][j] = true;
                }
            }
        }
        return a;
    }

    private static boolean[][] cycle(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            link(a, i, (i + 1) % n);
        }
        return a;
    }

    private static boolean[][] path(int n) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i + 1 < n; i++) {
            link(a, i, i + 1);
        }
        return a;
    }

    private static boolean[][] threeSun() {
        boolean[][] a = new boolean[6][6];
        link(a, 0, 1);
        link(a, 1, 2);
        link(a, 0, 2);
        link(a, 3, 0);
        link(a, 3, 1);
        link(a, 4, 1);
        link(a, 4, 2);
        link(a, 5, 0);
        link(a, 5, 2);
        return a;
    }

    private static void link(boolean[][] a, int i, int j) {
        a[i][j] = true;
        a[j][i] = true;
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
