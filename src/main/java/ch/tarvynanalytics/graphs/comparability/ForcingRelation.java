package ch.tarvynanalytics.graphs.comparability;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Golumbic's <em>forcing relation</em> (Γ) comparability test — the standard,
 * sound and complete decision procedure for comparability graphs (Golumbic,
 * <em>Algorithmic Graph Theory and Perfect Graphs</em>; Gilmore–Hoffman).
 *
 * <p>Each undirected edge {@code {a,b}} gives two arcs {@code a→b} and
 * {@code b→a}. The relation Γ forces orientations between arcs that share an
 * endpoint whose other two endpoints are non-adjacent:
 * {@code (x,y) Γ (x,z)} when {@code x~z} and {@code y≁z}, and
 * {@code (x,y) Γ (z,y)} when {@code z~y} and {@code x≁z}. Γ is symmetric, so its
 * arcs split into connected <em>implication classes</em>. A graph is a
 * comparability graph <strong>iff</strong> no implication class contains both an
 * arc and its reverse (Theorem 2.22 in the thesis, i.e. every "non-triangulable
 * cycle" is even). The thesis's non-triangulable chain is exactly a Γ-forcing
 * chain; an odd non-triangulable cycle is an edge forced into both orientations.</p>
 *
 * <p>When the graph is not a comparability graph this returns a shortest closed
 * odd forcing walk (the obstruction), reported as a vertex sequence; for an odd
 * hole it is the hole itself, for a folded obstruction (e.g. the 3-sun) it is a
 * closed walk that may revisit vertices.</p>
 */
final class ForcingRelation {

    private ForcingRelation() {
    }

    /** Outcome of the test: the verdict and, when false, the obstructing walk. */
    static final class Result {
        final boolean comparable;
        /** Closed odd forcing walk (vertex ids, no repeated closing vertex), or {@code null}. */
        final int[] failureWalk;

        Result(boolean comparable, int[] failureWalk) {
            this.comparable = comparable;
            this.failureWalk = failureWalk;
        }
    }

    static Result decide(boolean[][] adj) {
        int n = adj.length;
        // Label every arc's Γ-implication class via BFS over the (symmetric) Γ graph.
        int[] comp = new int[n * n];
        Arrays.fill(comp, -1);
        int classes = 0;
        for (int a = 0; a < n; a++) {
            for (int b = 0; b < n; b++) {
                if (a != b && adj[a][b] && comp[a * n + b] < 0) {
                    labelClass(adj, n, a, b, comp, classes++);
                }
            }
        }
        // Comparability iff no edge has both its arcs in the same class.
        for (int a = 0; a < n; a++) {
            for (int b = a + 1; b < n; b++) {
                if (adj[a][b] && comp[a * n + b] == comp[b * n + a]) {
                    return new Result(false, witness(adj, n, a, b));
                }
            }
        }
        return new Result(true, null);
    }

    private static void labelClass(boolean[][] adj, int n, int a, int b, int[] comp, int label) {
        Deque<int[]> queue = new ArrayDeque<>();
        comp[a * n + b] = label;
        queue.add(new int[]{a, b});
        while (!queue.isEmpty()) {
            int[] arc = queue.poll();
            forEachNeighbour(adj, n, arc[0], arc[1], (x, y) -> {
                if (comp[x * n + y] < 0) {
                    comp[x * n + y] = label;
                    queue.add(new int[]{x, y});
                }
            });
        }
    }

    /** Shortest closed odd forcing walk through edge {@code {a,b}} (a→b … b→a). */
    private static int[] witness(boolean[][] adj, int n, int a, int b) {
        int src = a * n + b;
        int tgt = b * n + a;
        int[] parent = new int[n * n];
        Arrays.fill(parent, -2);
        parent[src] = -1;
        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(src);
        while (!queue.isEmpty()) {
            int arc = queue.poll();
            if (arc == tgt) {
                break;
            }
            int x = arc / n;
            int y = arc % n;
            forEachNeighbour(adj, n, x, y, (p, q) -> {
                int id = p * n + q;
                if (parent[id] == -2) {
                    parent[id] = arc;
                    queue.add(id);
                }
            });
        }
        // Reconstruct the arc path src … tgt.
        List<int[]> arcs = new ArrayList<>();
        for (int cur = tgt; cur != -1; cur = parent[cur]) {
            arcs.add(new int[]{cur / n, cur % n});
        }
        Collections.reverse(arcs);
        return arcsToCycle(arcs);
    }

    /**
     * Converts a Γ-path of arcs into the obstructing closed vertex walk. The path
     * starts at arc {@code a→b} and ends at its reverse {@code b→a} (the same
     * edge, forced both ways) — that final arc is the contradiction marker and is
     * dropped; the remaining arcs are an edge-trail traversed as a walk, with the
     * shared pivot re-inserted whenever a high-degree "fan" would otherwise break
     * the trail. For an odd hole this yields the hole; otherwise it is a closed
     * walk that may revisit vertices.
     */
    private static int[] arcsToCycle(List<int[]> arcs) {
        int m = arcs.size() - 1; // drop the final reverse (contradiction) arc
        int[] pivot = new int[Math.max(0, m - 1)];
        for (int i = 0; i < m - 1; i++) {
            int[] cur = arcs.get(i);
            int[] nxt = arcs.get(i + 1);
            pivot[i] = (cur[0] == nxt[0] || cur[0] == nxt[1]) ? cur[0] : cur[1];
        }
        List<Integer> walk = new ArrayList<>();
        int[] e0 = arcs.get(0);
        int cur = (m > 1 && e0[0] == pivot[0]) ? e0[1] : e0[0];
        walk.add(cur);
        for (int i = 0; i < m; i++) {
            int[] e = arcs.get(i);
            if (cur != e[0] && cur != e[1]) {
                // fan break: step back to the pivot shared with the previous edge
                int bridge = pivot[i - 1];
                walk.add(bridge);
                cur = bridge;
            }
            int next = (cur == e[0]) ? e[1] : e[0];
            walk.add(next);
            cur = next;
        }
        // The trail closes back to its start; drop the duplicate closing vertex.
        if (walk.size() > 1 && walk.get(walk.size() - 1).equals(walk.get(0))) {
            walk.remove(walk.size() - 1);
        }
        int[] out = new int[walk.size()];
        for (int i = 0; i < walk.size(); i++) {
            out[i] = walk.get(i);
        }
        return out;
    }

    private interface ArcConsumer {
        void accept(int x, int y);
    }

    /** Visits every arc Γ-related to {@code (x,y)}. */
    private static void forEachNeighbour(boolean[][] adj, int n, int x, int y, ArcConsumer out) {
        for (int z = 0; z < n; z++) {
            // same source x: (x,y) Γ (x,z) when x~z and y≁z
            if (z != y && adj[x][z] && !adj[y][z]) {
                out.accept(x, z);
            }
            // same sink y: (x,y) Γ (z,y) when z~y and x≁z
            if (z != x && adj[z][y] && !adj[x][z]) {
                out.accept(z, y);
            }
        }
    }
}
