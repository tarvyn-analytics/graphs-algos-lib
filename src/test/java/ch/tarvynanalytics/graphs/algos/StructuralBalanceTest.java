package ch.tarvynanalytics.graphs.algos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins {@link StructuralBalance} against a brute-force oracle, per invariant&nbsp;#2:
 * a signed graph is balanced iff some 0/1 colouring satisfies every edge (positive
 * edges keep the colour, negative edges flip it). Over <em>every</em> signed graph
 * on up to five vertices (each edge absent / positive / negative) the verdict must
 * match, the camps must be a valid colouring when balanced, and the reported
 * frustrated cycle must be a genuine cycle with an odd number of negative edges
 * when not.
 */
class StructuralBalanceTest {

    // ---- brute-force oracle ------------------------------------------------

    private static boolean oracleBalanced(boolean[][] adj, boolean[][] negative, int n) {
        for (int colour = 0; colour < (1 << n); colour++) {
            if (colouringSatisfiesAll(adj, negative, n, colour)) {
                return true;
            }
        }
        return false;
    }

    private static boolean colouringSatisfiesAll(boolean[][] adj, boolean[][] negative, int n, int colour) {
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (!adj[i][j]) {
                    continue;
                }
                boolean same = ((colour >> i) & 1) == ((colour >> j) & 1);
                if (negative[i][j] == same) {
                    return false; // negative wants different camps, positive wants the same
                }
            }
        }
        return true;
    }

    // ---- exhaustive small signed graphs -----------------------------------

    @Test
    void everySignedGraphUpToFiveVertices_MatchesOracle_AndValidWitness() {
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
            int total = pow3(edgeSlots);
            for (int assignment = 0; assignment < total; assignment++) {
                boolean[][] adj = new boolean[n][n];
                boolean[][] negative = new boolean[n][n];
                int a = assignment;
                for (int e = 0; e < edgeSlots; e++) {
                    int digit = a % 3;
                    a /= 3;
                    if (digit != 0) {
                        int i = pair[e][0];
                        int j = pair[e][1];
                        adj[i][j] = true;
                        adj[j][i] = true;
                        if (digit == 2) {
                            negative[i][j] = true;
                            negative[j][i] = true;
                        }
                    }
                }
                StructuralBalance.Result r = StructuralBalance.decide(adj, negative);
                boolean oracle = oracleBalanced(adj, negative, n);
                String g = "n=" + n + " assignment=" + assignment;
                assertEquals(oracle, r.balanced, "verdict for " + g);
                if (r.balanced) {
                    assertTrue(isValidColouring(adj, negative, n, r.camp), "valid camps for " + g);
                    assertEquals(0, r.frustratedCycle.length, "no cycle when balanced for " + g);
                } else {
                    assertTrue(isFrustratedCycle(adj, negative, r.frustratedCycle), "odd-negative cycle for " + g);
                }
            }
        }
    }

    // ---- named families ----------------------------------------------------

    @Test
    void namedSignedTriangles_MatchTheParityRule() {
        // A cycle is balanced iff it has an EVEN number of negative edges.
        assertTrue(StructuralBalance.decide(triangle(), negs()).balanced, "all-positive triangle is balanced");
        assertFalse(StructuralBalance.decide(triangle(), negs(0, 1)).balanced, "one negative -> unbalanced");
        assertTrue(StructuralBalance.decide(triangle(), negs(0, 1, 1, 2)).balanced, "two negatives -> balanced");
        assertFalse(StructuralBalance.decide(triangle(), negs(0, 1, 1, 2, 0, 2)).balanced,
                "three negatives -> unbalanced");
    }

    @Test
    void singleNegativeEdge_IsBalanced_IntoTwoCamps() {
        boolean[][] adj = {{false, true}, {true, false}};
        boolean[][] negative = {{false, true}, {true, false}};
        StructuralBalance.Result r = StructuralBalance.decide(adj, negative);
        assertTrue(r.balanced);
        assertEquals(2, r.camp.length);
        assertTrue(r.camp[0] != r.camp[1], "the negative edge separates the two camps");
    }

    // ---- validators --------------------------------------------------------

    private static boolean isValidColouring(boolean[][] adj, boolean[][] negative, int n, int[] camp) {
        if (camp.length != n) {
            return false;
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adj[i][j]) {
                    boolean same = camp[i] == camp[j];
                    if (negative[i][j] == same) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean isFrustratedCycle(boolean[][] adj, boolean[][] negative, int[] cycle) {
        int k = cycle.length;
        if (k < 3) {
            return false;
        }
        int negativeCount = 0;
        for (int i = 0; i < k; i++) {
            int a = cycle[i];
            int b = cycle[(i + 1) % k];
            if (a == b || !adj[a][b]) {
                return false; // consecutive vertices must be a real edge
            }
            if (negative[a][b]) {
                negativeCount++;
            }
            for (int j = i + 1; j < k; j++) {
                if (cycle[i] == cycle[j]) {
                    return false; // vertices must be distinct
                }
            }
        }
        return negativeCount % 2 == 1; // an unbalanced cycle has an odd number of negative edges
    }

    // ---- builders ----------------------------------------------------------

    private static int pow3(int exp) {
        int p = 1;
        for (int i = 0; i < exp; i++) {
            p *= 3;
        }
        return p;
    }

    private static boolean[][] triangle() {
        boolean[][] a = new boolean[3][3];
        a[0][1] = a[1][0] = true;
        a[1][2] = a[2][1] = true;
        a[0][2] = a[2][0] = true;
        return a;
    }

    /** A 3x3 negative mask with the given edge endpoints (flattened pairs) marked negative. */
    private static boolean[][] negs(int... endpoints) {
        boolean[][] m = new boolean[3][3];
        for (int i = 0; i < endpoints.length; i += 2) {
            m[endpoints[i]][endpoints[i + 1]] = true;
            m[endpoints[i + 1]][endpoints[i]] = true;
        }
        return m;
    }
}
