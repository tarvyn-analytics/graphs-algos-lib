package ch.tarvynanalytics.graphs.comparability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Structural balance of a <em>signed</em> graph (Heider / Harary / Cartwright).
 * A signed graph is <strong>balanced</strong> iff every cycle has an even number
 * of negative edges — equivalently (Harary's theorem) iff its vertices split into
 * two camps with all positive edges <em>within</em> a camp and all negative edges
 * <em>between</em> the camps. For a correlation graph the sign is the sign of the
 * correlation, so balance means the names fall into two blocs that are internally
 * positively correlated and mutually negatively correlated.
 *
 * <p>Decided in linear time by a signed breadth-first 2-colouring per connected
 * component: walking a positive edge keeps the camp, a negative edge flips it. A
 * vertex reached with a conflicting camp witnesses an <em>unbalanced cycle</em>
 * (an odd number of negative edges), recovered from the BFS tree and returned as
 * the obstruction.</p>
 *
 * <p>Correctness is pinned against a brute-force oracle (balanced iff some 2-
 * colouring satisfies every edge) over all signed graphs up to five vertices in
 * {@code StructuralBalanceTest}, per invariant&nbsp;#2.</p>
 */
final class StructuralBalance {

    private StructuralBalance() {
    }

    /** Outcome: the verdict, the two-camp colouring when balanced, else an unbalanced cycle. */
    static final class Result {
        /** Whether the signed graph is balanced. */
        final boolean balanced;
        /** Camp (0 or 1) of each vertex when balanced; empty when not. */
        final int[] camp;
        /** A cycle with an odd number of negative edges when not balanced; empty when balanced. */
        final int[] frustratedCycle;

        Result(boolean balanced, int[] camp, int[] frustratedCycle) {
            this.balanced = balanced;
            this.camp = camp;
            this.frustratedCycle = frustratedCycle;
        }
    }

    /**
     * Decides balance of the signed graph given its adjacency and the negative-edge mask.
     *
     * @param adj      symmetric adjacency
     * @param negative symmetric mask: {@code negative[i][j]} iff the edge {@code {i,j}} is negative
     * @return the verdict with the camps (balanced) or an unbalanced cycle (not balanced)
     */
    static Result decide(boolean[][] adj, boolean[][] negative) {
        int n = adj.length;
        int[] camp = new int[n];
        Arrays.fill(camp, -1);
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        for (int start = 0; start < n; start++) {
            if (camp[start] >= 0) {
                continue;
            }
            int[] cycle = colourComponent(start, adj, negative, camp, parent);
            if (cycle.length > 0) {
                return new Result(false, new int[0], cycle);
            }
        }
        return new Result(true, camp, new int[0]);
    }

    /**
     * Signed-BFS 2-colours the component of {@code start} (filling {@code camp} / {@code parent}),
     * returning an unbalanced cycle if a conflict is found, otherwise an empty array.
     */
    private static int[] colourComponent(int start, boolean[][] adj, boolean[][] negative,
                                         int[] camp, int[] parent) {
        int n = adj.length;
        camp[start] = 0;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            int u = queue.poll();
            for (int v = 0; v < n; v++) {
                if (!adj[u][v]) {
                    continue;
                }
                int want = negative[u][v] ? 1 - camp[u] : camp[u];
                if (camp[v] < 0) {
                    camp[v] = want;
                    parent[v] = u;
                    queue.add(v);
                } else if (camp[v] != want) {
                    return buildCycle(u, v, parent);
                }
            }
        }
        return new int[0];
    }

    /**
     * The unbalanced cycle closed by the conflicting edge {@code {u,v}}: the two BFS-tree paths from
     * {@code u} and {@code v} up to their lowest common ancestor, plus that edge.
     */
    private static int[] buildCycle(int u, int v, int[] parent) {
        List<Integer> pathU = ancestors(u, parent);
        List<Integer> pathV = ancestors(v, parent);
        Set<Integer> onU = new HashSet<>(pathU);
        int lca = -1;
        for (int x : pathV) {
            if (onU.contains(x)) {
                lca = x;
                break;
            }
        }
        List<Integer> cycle = new ArrayList<>();
        for (int x : pathU) {
            cycle.add(x); // u .. lca (inclusive)
            if (x == lca) {
                break;
            }
        }
        List<Integer> vSide = new ArrayList<>();
        for (int x : pathV) {
            if (x == lca) {
                break;
            }
            vSide.add(x); // v .. child-of-lca (lca excluded)
        }
        Collections.reverse(vSide);
        cycle.addAll(vSide); // .. lca, child-of-lca, .., v
        int[] out = new int[cycle.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = cycle.get(i);
        }
        return out;
    }

    /** The path of tree ancestors from {@code x} up to its component root (inclusive of both). */
    private static List<Integer> ancestors(int x, int[] parent) {
        List<Integer> path = new ArrayList<>();
        for (int cur = x; cur != -1; cur = parent[cur]) {
            path.add(cur);
        }
        return path;
    }
}
