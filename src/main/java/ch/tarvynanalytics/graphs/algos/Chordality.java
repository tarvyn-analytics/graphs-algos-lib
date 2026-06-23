package ch.tarvynanalytics.graphs.algos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * Chordality (triangulation) test of the graph, plus a greedy chordal
 * completion. A graph is <em>chordal</em> iff every cycle of length &ge; 4 has a
 * chord — equivalently, iff it has a <em>perfect elimination ordering</em> (PEO):
 * a vertex order in which the later neighbours of every vertex form a clique.
 *
 * <p>Why this matters for the correlation graph: chordal graphs are exactly the
 * <em>decomposable</em> models. When the (partial-)correlation graph is chordal
 * the Gaussian graphical model factorizes over a clique (junction) tree and the
 * covariance / precision MLE is closed-form and modular; when it is not, the
 * chordal completion (fill-in edges) is the minimal extra structure that makes it
 * decomposable.</p>
 *
 * <p>Detection uses <strong>maximum-cardinality search</strong> (Tarjan &amp;
 * Yannakakis, 1984): MCS produces a vertex order whose reverse is a PEO iff the
 * graph is chordal. On failure a chordless cycle (a hole) of length &ge; 4 is
 * returned as the obstruction. The completion runs the <em>elimination game</em>
 * with a minimum-degree heuristic — eliminate the current lowest-degree vertex,
 * adding the edges that make its remaining neighbourhood a clique; the added
 * edges are the fill-in and the elimination order is a PEO of the filled graph.
 * Any elimination order yields a chordal completion (Rose–Tarjan–Lueker); the
 * heuristic only affects how <em>small</em> the fill-in is. Minimum fill-in is
 * NP-hard, so this is a heuristic — not guaranteed minimal.</p>
 *
 * <p>Correctness is pinned against a brute-force oracle (a vertex subset induces
 * a hole iff it has &ge; 4 vertices, is connected and every vertex has degree
 * exactly two inside it) over all graphs up to six vertices in
 * {@code ChordalityTest}, per invariant&nbsp;#2.</p>
 */
final class Chordality {

    private final boolean[][] adj;
    private final int n;

    Chordality(boolean[][] adj) {
        this.adj = adj;
        this.n = adj.length;
    }

    /** Outcome: the verdict, a perfect elimination order, and (when not chordal) a hole + completion. */
    static final class Result {
        /** Whether the input graph is chordal. */
        final boolean chordal;
        /** A perfect elimination ordering of the chordal completion (= the graph itself when chordal). */
        final int[] eliminationOrder;
        /** A chordless cycle (hole) of length &ge; 4 when not chordal; empty when chordal. */
        final int[] chordlessCycle;
        /** Fill-in edges {@code {u,v}} (u &lt; v) of the chordal completion; empty when already chordal. */
        final int[][] fillIn;

        Result(boolean chordal, int[] eliminationOrder, int[] chordlessCycle, int[][] fillIn) {
            this.chordal = chordal;
            this.eliminationOrder = eliminationOrder;
            this.chordlessCycle = chordlessCycle;
            this.fillIn = fillIn;
        }
    }

    static Result analyze(boolean[][] adj) {
        return new Chordality(adj).analyze();
    }

    /**
     * A chordless cycle (hole) of length &ge; 4 if {@code adj} is not chordal, otherwise an empty
     * array. Detection only (no completion) — the cheap primitive for callers that repeatedly test a
     * mutating graph (e.g. the weakest-link decomposability repair).
     *
     * @param adj the adjacency matrix
     * @return a hole, or an empty array if the graph is chordal
     */
    static int[] holeOrEmpty(boolean[][] adj) {
        if (adj.length == 0) {
            return new int[0];
        }
        Chordality c = new Chordality(adj);
        int[] peo = c.perfectEliminationCandidate();
        return c.isPerfectEliminationOrder(peo) ? new int[0] : c.findHole();
    }

    Result analyze() {
        if (n == 0) {
            return new Result(true, new int[0], new int[0], new int[0][]);
        }
        int[] peo = perfectEliminationCandidate();
        if (isPerfectEliminationOrder(peo)) {
            return new Result(true, peo, new int[0], new int[0][]);
        }
        int[] hole = findHole();
        Completion completion = greedyCompletion();
        return new Result(false, completion.order, hole, completion.fillIn);
    }

    // ------------------------------------------------------------------
    // Detection: maximum-cardinality search + perfect-elimination check
    // ------------------------------------------------------------------

    /**
     * A PEO candidate: the reverse of the maximum-cardinality-search visiting
     * order. MCS repeatedly picks the unvisited vertex adjacent to the most
     * already-visited vertices; the reverse of that order is a PEO iff the graph
     * is chordal.
     */
    private int[] perfectEliminationCandidate() {
        int[] weight = new int[n];
        boolean[] visited = new boolean[n];
        int[] visitOrder = new int[n];
        for (int k = 0; k < n; k++) {
            int best = -1;
            for (int v = 0; v < n; v++) {
                if (!visited[v] && (best < 0 || weight[v] > weight[best])) {
                    best = v;
                }
            }
            visited[best] = true;
            visitOrder[k] = best;
            for (int u = 0; u < n; u++) {
                if (adj[best][u] && !visited[u]) {
                    weight[u]++;
                }
            }
        }
        int[] peo = new int[n];
        for (int i = 0; i < n; i++) {
            peo[i] = visitOrder[n - 1 - i];
        }
        return peo;
    }

    /**
     * Whether {@code peo} is a perfect elimination ordering: for every vertex the
     * neighbours that come later in the order are pairwise adjacent (a clique).
     */
    private boolean isPerfectEliminationOrder(int[] peo) {
        int[] pos = new int[n];
        for (int i = 0; i < n; i++) {
            pos[peo[i]] = i;
        }
        for (int i = 0; i < n; i++) {
            int v = peo[i];
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

    // ------------------------------------------------------------------
    // Obstruction: a chordless cycle (hole) when the graph is not chordal
    // ------------------------------------------------------------------

    /**
     * A chordless cycle of length &ge; 4, or {@code null} if the graph is chordal.
     * Picks a vertex {@code v} with two non-adjacent neighbours {@code x}, {@code y}
     * and a shortest {@code x}–{@code y} path whose interior avoids {@code N[v]};
     * a shortest path is induced, so {@code v} plus that path is a hole. Such a
     * triple always exists when the graph is not chordal (take any hole vertex and
     * its two cycle-neighbours).
     */
    private int[] findHole() {
        for (int v = 0; v < n; v++) {
            List<Integer> neighbours = new ArrayList<>();
            for (int u = 0; u < n; u++) {
                if (adj[v][u]) {
                    neighbours.add(u);
                }
            }
            for (int a = 0; a < neighbours.size(); a++) {
                for (int b = a + 1; b < neighbours.size(); b++) {
                    int x = neighbours.get(a);
                    int y = neighbours.get(b);
                    if (adj[x][y]) {
                        continue;
                    }
                    int[] path = shortestPathAvoidingNeighbourhood(x, y, v);
                    if (path != null) {
                        int[] cycle = new int[path.length + 1];
                        cycle[0] = v;
                        System.arraycopy(path, 0, cycle, 1, path.length);
                        return cycle;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Shortest path from {@code x} to {@code y} whose interior vertices avoid the
     * closed neighbourhood of {@code v} (so the path, with {@code v}, closes into a
     * chordless cycle), or {@code null} if none exists.
     */
    private int[] shortestPathAvoidingNeighbourhood(int x, int y, int v) {
        int[] parent = new int[n];
        Arrays.fill(parent, -2);
        parent[x] = -1;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(x);
        while (!queue.isEmpty()) {
            int cur = queue.poll();
            if (cur == y) {
                return reconstruct(parent, x, y);
            }
            for (int u = 0; u < n; u++) {
                if (adj[cur][u] && parent[u] == -2 && (u == y || (u != v && !adj[v][u]))) {
                    parent[u] = cur;
                    queue.add(u);
                }
            }
        }
        return null;
    }

    private static int[] reconstruct(int[] parent, int x, int y) {
        List<Integer> rev = new ArrayList<>();
        for (int cur = y; cur != -1; cur = parent[cur]) {
            rev.add(cur);
        }
        int[] path = new int[rev.size()];
        for (int i = 0; i < path.length; i++) {
            path[i] = rev.get(path.length - 1 - i); // x ... y
        }
        return path;
    }

    // ------------------------------------------------------------------
    // Chordal completion: minimum-degree elimination game
    // ------------------------------------------------------------------

    /** Carrier for a completion: a PEO of the filled graph and the fill-in edges. Plain class (not a
     * record) because its fields are arrays — record equals/hashCode would compare them by identity. */
    private static final class Completion {
        final int[] order;
        final int[][] fillIn;

        Completion(int[] order, int[][] fillIn) {
            this.order = order;
            this.fillIn = fillIn;
        }
    }

    /**
     * A chordal completion via the elimination game with a minimum-degree
     * heuristic. Repeatedly eliminates the remaining vertex of lowest current
     * degree, adding the edges that make its remaining neighbourhood a clique; the
     * added edges are the fill-in and the elimination order is a PEO of the filled
     * graph.
     */
    private Completion greedyCompletion() {
        boolean[][] filled = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            filled[i] = adj[i].clone();
        }
        boolean[] removed = new boolean[n];
        int[] order = new int[n];
        List<int[]> fillIn = new ArrayList<>();
        for (int k = 0; k < n; k++) {
            int best = -1;
            int bestDegree = Integer.MAX_VALUE;
            for (int v = 0; v < n; v++) {
                if (removed[v]) {
                    continue;
                }
                int degree = 0;
                for (int u = 0; u < n; u++) {
                    if (!removed[u] && filled[v][u]) {
                        degree++;
                    }
                }
                if (degree < bestDegree) {
                    bestDegree = degree;
                    best = v;
                }
            }
            order[k] = best;
            removed[best] = true;
            List<Integer> neighbours = new ArrayList<>();
            for (int u = 0; u < n; u++) {
                if (!removed[u] && filled[best][u]) {
                    neighbours.add(u);
                }
            }
            for (int a = 0; a < neighbours.size(); a++) {
                for (int b = a + 1; b < neighbours.size(); b++) {
                    int p = neighbours.get(a);
                    int q = neighbours.get(b);
                    if (!filled[p][q]) {
                        filled[p][q] = true;
                        filled[q][p] = true;
                        fillIn.add(new int[]{Math.min(p, q), Math.max(p, q)});
                    }
                }
            }
        }
        return new Completion(order, fillIn.toArray(new int[0][]));
    }
}
