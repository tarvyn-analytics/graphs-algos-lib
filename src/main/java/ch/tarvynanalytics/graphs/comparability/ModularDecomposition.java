package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.EdgeView;
import ch.tarvynanalytics.graphs.comparability.model.FactorGraphLevelView;
import ch.tarvynanalytics.graphs.comparability.model.GraphView;
import ch.tarvynanalytics.graphs.comparability.model.ModuleType;
import ch.tarvynanalytics.graphs.comparability.model.ModuleView;
import ch.tarvynanalytics.graphs.comparability.model.NodeView;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

/**
 * Canonical modular (substitution) decomposition of the input graph. It supplies
 * both the exact number of transitive orientations and the human-readable
 * factor-graph levels that show how vertices group.
 *
 * <p>Each internal node of the decomposition tree is <em>parallel</em> (the
 * subgraph is disconnected), <em>series</em> (its complement is disconnected) or
 * <em>prime</em>. The number of transitive orientations of a comparability graph
 * is the product over the tree of: {@code k!} for a series node with {@code k}
 * children, {@code 1} for a parallel node, and {@code 2} for a prime node — the
 * standard Gallai / Golumbic count. (The verdict, hence whether prime nodes are
 * orientable, is decided independently by {@link ForcingRelation}; this class is
 * only asked for the count when the graph is already known to be a comparability
 * graph.)</p>
 */
final class ModularDecomposition {

    private final boolean[][] adj;
    private final int n;

    ModularDecomposition(boolean[][] adj) {
        this.adj = adj;
        this.n = adj.length;
    }

    private enum NodeType { PARALLEL, SERIES, PRIME }

    private record Partition(NodeType type, List<List<Integer>> blocks) {
    }

    // ------------------------------------------------------------------
    // Orientation count
    // ------------------------------------------------------------------

    /** Number of distinct transitive orientations, assuming the graph is a comparability graph. */
    BigInteger orientationCount() {
        if (n == 0) {
            return BigInteger.ONE;
        }
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            all.add(i);
        }
        return count(all);
    }

    private BigInteger count(List<Integer> verts) {
        if (verts.size() <= 1) {
            return BigInteger.ONE;
        }
        Partition p = rootPartition(verts);
        BigInteger prod = BigInteger.ONE;
        for (List<Integer> block : p.blocks()) {
            prod = prod.multiply(count(block));
        }
        return switch (p.type()) {
            case PARALLEL -> prod;
            case SERIES -> factorial(p.blocks().size()).multiply(prod);
            case PRIME -> BigInteger.TWO.multiply(prod);
        };
    }

    private static BigInteger factorial(int k) {
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= k; i++) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        return f;
    }

    // ------------------------------------------------------------------
    // Factor-graph levels (grouping display)
    // ------------------------------------------------------------------

    List<FactorGraphLevelView> levels(GraphInput input) {
        List<FactorGraphLevelView> out = new ArrayList<>();
        if (n == 0) {
            return out;
        }
        boolean[][] curAdj = adj;
        int curN = n;
        List<List<Integer>> leaves = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            leaves.add(List.of(i));
        }
        int level = 0;
        while (true) {
            GraphView curGraph = graphView(curAdj, curN, leaves, input);
            List<List<Integer>> blocks = levelBlocks(curAdj, curN);
            List<ModuleView> modules = new ArrayList<>();
            for (int b = 0; b < blocks.size(); b++) {
                modules.add(new ModuleView(b, classify(curAdj, blocks.get(b)), blocks.get(b)));
            }
            int fN = modules.size();
            boolean[][] fAdj = quotientAdjacency(curAdj, blocks);
            List<List<Integer>> fLeaves = quotientLeaves(blocks, leaves);
            GraphView factorGraph = graphView(fAdj, fN, fLeaves, input);
            out.add(new FactorGraphLevelView(level, curGraph, modules, factorGraph));
            if (fN >= curN || fN <= 1) {
                break; // prime level (no reduction) or fully collapsed
            }
            curAdj = fAdj;
            curN = fN;
            leaves = fLeaves;
            level++;
        }
        return out;
    }

    /** The modules to display at one level: the root's children, with degenerate nodes flattened. */
    private List<List<Integer>> levelBlocks(boolean[][] a, int m) {
        List<Integer> verts = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            verts.add(i);
        }
        if (m == 1) {
            return List.of(verts);
        }
        Partition p = rootPartition(verts);
        boolean allSingletons = p.blocks().stream().allMatch(b -> b.size() == 1);
        if (allSingletons && (p.type() == NodeType.SERIES || p.type() == NodeType.PARALLEL)) {
            // A complete (series) or empty (parallel) graph: present the whole graph as one module.
            return List.of(verts);
        }
        return p.blocks();
    }

    private ModuleType classify(boolean[][] a, List<Integer> block) {
        if (block.size() == 1) {
            return ModuleType.SINGLETON;
        }
        boolean allAdj = true;
        boolean noneAdj = true;
        for (int i = 0; i < block.size(); i++) {
            for (int j = i + 1; j < block.size(); j++) {
                if (a[block.get(i)][block.get(j)]) {
                    noneAdj = false;
                } else {
                    allAdj = false;
                }
            }
        }
        if (allAdj) {
            return ModuleType.CLIQUE;
        }
        if (noneAdj) {
            return ModuleType.INDEPENDENT_SET;
        }
        return ModuleType.MIN_STABLE;
    }

    private boolean[][] quotientAdjacency(boolean[][] a, List<List<Integer>> blocks) {
        int k = blocks.size();
        boolean[][] q = new boolean[k][k];
        for (int i = 0; i < k; i++) {
            for (int j = i + 1; j < k; j++) {
                boolean edge = a[blocks.get(i).get(0)][blocks.get(j).get(0)];
                q[i][j] = edge;
                q[j][i] = edge;
            }
        }
        return q;
    }

    private List<List<Integer>> quotientLeaves(List<List<Integer>> blocks, List<List<Integer>> leaves) {
        List<List<Integer>> out = new ArrayList<>();
        for (List<Integer> block : blocks) {
            List<Integer> merged = new ArrayList<>();
            for (int node : block) {
                merged.addAll(leaves.get(node));
            }
            out.add(merged);
        }
        return out;
    }

    private GraphView graphView(boolean[][] a, int m, List<List<Integer>> leaves, GraphInput input) {
        List<NodeView> nodes = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            nodes.add(new NodeView(i, label(leaves.get(i), input)));
        }
        List<EdgeView> edges = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if (a[i][j]) {
                    edges.add(new EdgeView(i, j));
                }
            }
        }
        return new GraphView(nodes, edges);
    }

    private static String label(List<Integer> leafIds, GraphInput input) {
        if (leafIds.size() == 1) {
            return input.label(leafIds.get(0));
        }
        List<Integer> sorted = new ArrayList<>(leafIds);
        sorted.sort(Comparator.naturalOrder());
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < sorted.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(input.label(sorted.get(i)));
        }
        return sb.append('}').toString();
    }

    // ------------------------------------------------------------------
    // Root partition: parallel / series / prime, over an induced vertex set
    // ------------------------------------------------------------------

    private Partition rootPartition(List<Integer> verts) {
        List<List<Integer>> comps = components(verts, false);
        if (comps.size() > 1) {
            return new Partition(NodeType.PARALLEL, comps);
        }
        List<List<Integer>> coComps = components(verts, true);
        if (coComps.size() > 1) {
            return new Partition(NodeType.SERIES, coComps);
        }
        return new Partition(NodeType.PRIME, maximalModularPartition(verts));
    }

    /** Connected components of the induced subgraph (or its complement when {@code complement}). */
    private List<List<Integer>> components(List<Integer> verts, boolean complement) {
        List<List<Integer>> comps = new ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int start : verts) {
            if (seen.contains(start)) {
                continue;
            }
            List<Integer> comp = new ArrayList<>();
            Deque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            while (!queue.isEmpty()) {
                int u = queue.poll();
                comp.add(u);
                for (int v : verts) {
                    if (!seen.contains(v) && u != v && (adj[u][v] != complement)) {
                        seen.add(v);
                        queue.add(v);
                    }
                }
            }
            comps.add(comp);
        }
        return comps;
    }

    /** Children of a prime node: classes of "the minimal module containing the pair is proper". */
    private List<List<Integer>> maximalModularPartition(List<Integer> verts) {
        List<List<Integer>> blocks = new ArrayList<>();
        java.util.Set<Integer> unclassified = new java.util.LinkedHashSet<>(verts);
        while (!unclassified.isEmpty()) {
            int x = unclassified.iterator().next();
            List<Integer> block = new ArrayList<>();
            block.add(x);
            for (int y : verts) {
                if (y != x && unclassified.contains(y)
                        && minimalModule(verts, x, y).size() < verts.size()) {
                    block.add(y);
                }
            }
            unclassified.removeAll(block);
            blocks.add(block);
        }
        return blocks;
    }

    /** Smallest module of the induced subgraph on {@code verts} containing {@code x} and {@code y}. */
    private java.util.Set<Integer> minimalModule(List<Integer> verts, int x, int y) {
        java.util.Set<Integer> m = new java.util.HashSet<>();
        m.add(x);
        m.add(y);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int z : verts) {
                if (!m.contains(z) && distinguishes(z, m)) {
                    m.add(z); // a module cannot be split, so it must absorb z
                    changed = true;
                }
            }
        }
        return m;
    }

    /** Whether {@code z} is adjacent to some but not all of {@code m} (so it splits it). */
    private boolean distinguishes(int z, java.util.Set<Integer> m) {
        boolean adjSome = false;
        boolean adjAll = true;
        for (int u : m) {
            if (adj[z][u]) {
                adjSome = true;
            } else {
                adjAll = false;
            }
        }
        return adjSome && !adjAll;
    }
}
