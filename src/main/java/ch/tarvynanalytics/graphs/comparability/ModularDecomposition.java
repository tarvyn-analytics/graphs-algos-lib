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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    // Decomposition tree (built once, consumed by both outputs)
    // ------------------------------------------------------------------

    /** Type of a {@link MDNode}: an internal parallel/series/prime node or a single-vertex leaf. */
    private enum Kind { LEAF, PARALLEL, SERIES, PRIME }

    /** A node of the modular decomposition tree. */
    private record MDNode(Kind kind, List<Integer> vertices, List<MDNode> children) {
    }

    private MDNode rootTree;

    /** When set, {@link #tree()} uses {@link #buildTreeLinear()} instead of the simple recursion. */
    private boolean useLinear;

    /**
     * Switches this instance to the near-linear {@code fracture} builder
     * ({@link #buildTreeLinear()}). Package-private and intended for the differential
     * test that pins the linear builder against the simple recursion; the production
     * path keeps the simple recursion until the size gate (step 4) selects per graph.
     *
     * @return {@code this}, for chaining
     */
    ModularDecomposition useLinearBuilder() {
        this.useLinear = true;
        return this;
    }

    /**
     * The decomposition tree of the whole graph, computed lazily and cached so the
     * orientation count and the factor-graph levels share a single decomposition
     * (a comparability graph would otherwise be decomposed twice). {@code buildTree}
     * is the one swappable place a near-linear algorithm replaces.
     */
    private MDNode tree() {
        if (rootTree == null) {
            if (useLinear) {
                rootTree = buildTreeLinear();
            } else {
                List<Integer> all = new ArrayList<>();
                for (int i = 0; i < n; i++) {
                    all.add(i);
                }
                rootTree = buildTree(all);
            }
        }
        return rootTree;
    }

    /** Recursively decomposes {@code verts} into the parallel/series/prime tree (leaf at size &le; 1). */
    private MDNode buildTree(List<Integer> verts) {
        if (verts.size() <= 1) {
            return new MDNode(Kind.LEAF, verts, List.of());
        }
        Partition p = rootPartition(verts);
        List<MDNode> children = new ArrayList<>();
        for (List<Integer> block : p.blocks()) {
            children.add(buildTree(block));
        }
        Kind kind = switch (p.type()) {
            case PARALLEL -> Kind.PARALLEL;
            case SERIES -> Kind.SERIES;
            case PRIME -> Kind.PRIME;
        };
        return new MDNode(kind, verts, children);
    }

    // ------------------------------------------------------------------
    // Near-linear builder: a port of the `fracture` algorithm
    // ------------------------------------------------------------------
    //
    // Java port of the `fracture` modular-decomposition algorithm from
    // jonasspinner/modular-decomposition (MIT, Copyright (c) 2024 Jonas Spinner),
    // following the readable reference `crates/fracture/src/base.rs`. The algorithm
    // computes a factorizing permutation by partition refinement, parenthesizes it
    // into the fracture tree, prunes the dummy (non-module) nodes and reads off the
    // canonical parallel/series/prime decomposition.
    //
    // References:
    //   [CHM02] Capelle, Habib, de Montgolfier, "Graph Decompositions and
    //           Factorizing Permutations" (2002).
    //   [HPV99] Habib, Paul, Viennot, "Partition Refinement Techniques: An
    //           Interesting Algorithmic Tool Kit" (1999).
    //
    // It builds the whole tree at once (not by recursing on induced subgraphs like
    // the simple builder) and yields the same canonical tree; equivalence is pinned
    // by ModularDecompositionLinearDifferentialTest.

    /** A node of the intermediate fracture forest: a single vertex leaf or an ordered group of children. */
    private record Raw(int firstLeaf, int leafVertex, List<Raw> children) {
        static Raw leaf(int v) {
            return new Raw(v, v, null);
        }

        static Raw group(List<Raw> children) {
            return new Raw(children.get(0).firstLeaf(), -1, children);
        }

        boolean isLeaf() {
            return children == null;
        }
    }

    /** Builds the whole decomposition tree via the {@code fracture} algorithm. */
    private MDNode buildTreeLinear() {
        if (n == 1) {
            return new MDNode(Kind.LEAF, List.of(0), List.of());
        }
        int[] p = factorizingPermutation();
        int[] op = new int[n];
        int[] cl = new int[n];
        int[] lc = new int[n];
        int[] uc = new int[n];
        op[0] = 1;
        cl[n - 1] = 1;
        for (int i = 0; i < n - 1; i++) {
            lc[i] = i;
            uc[i] = i + 1;
        }
        buildParenthesizing(p, op, cl, lc, uc);
        removeNonModuleDummyNodes(op, cl, lc, uc);
        createConsecutiveTwinNodes(op, cl, lc, uc);
        removeSingletonDummyNodes(op, cl);
        List<Raw> forest = buildFractureForest(p, op, cl);
        return classifyGroup(forest);
    }

    /**
     * The factorizing permutation of the graph: a vertex order in which every strong
     * module is a contiguous block. Computed by ordered partition refinement.
     */
    private int[] factorizingPermutation() {
        List<List<Integer>> partition = new ArrayList<>();
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            all.add(i);
        }
        partition.add(all);
        int[] center = {0};
        List<List<Integer>> pivots = new ArrayList<>();
        List<List<Integer>> modules = new ArrayList<>();
        Map<List<Integer>, Integer> firstPivot = new HashMap<>();

        while (initPartition(partition, center, pivots, modules, firstPivot)) {
            while (!pivots.isEmpty()) {
                List<Integer> e = pivots.remove(pivots.size() - 1);
                Set<Integer> eh = new HashSet<>(e);
                for (int x : e) {
                    Set<Integer> s = new HashSet<>();
                    for (int v = 0; v < n; v++) {
                        if (adj[x][v] && !eh.contains(v)) {
                            s.add(v);
                        }
                    }
                    refine(partition, s, x, center, pivots, modules);
                }
            }
        }
        int[] perm = new int[partition.size()];
        for (int i = 0; i < partition.size(); i++) {
            perm[i] = partition.get(i).get(0);
        }
        return perm;
    }

    /** Seeds the next refinement round; returns {@code false} once every part is a singleton. */
    private boolean initPartition(List<List<Integer>> partition, int[] center, List<List<Integer>> pivots,
                                  List<List<Integer>> modules, Map<List<Integer>, Integer> firstPivot) {
        boolean allSingletons = true;
        for (List<Integer> part : partition) {
            if (part.size() > 1) {
                allSingletons = false;
                break;
            }
        }
        if (allSingletons) {
            return false;
        }
        if (!modules.isEmpty()) {
            List<Integer> x = modules.remove(0);
            int v = x.get(0);
            List<Integer> piv = new ArrayList<>();
            piv.add(v);
            pivots.add(piv);
            firstPivot.put(x, v);
        } else {
            for (int i = 0; i < partition.size(); i++) {
                List<Integer> x = partition.get(i);
                if (x.size() <= 1) {
                    continue;
                }
                int v = firstPivot.getOrDefault(x, x.get(0));
                List<Integer> a = new ArrayList<>();
                List<Integer> nn = new ArrayList<>();
                for (int y : x) {
                    if (y == v) {
                        continue;
                    }
                    if (adj[v][y]) {
                        a.add(y);
                    } else {
                        nn.add(y);
                    }
                }
                splice(partition, i, a, v, nn);
                center[0] = v;
                if (a.size() <= nn.size()) {
                    pivots.add(a);
                    modules.add(nn);
                } else {
                    pivots.add(nn);
                    modules.add(a);
                }
                break;
            }
        }
        return true;
    }

    /** Replaces {@code partition[i]} with the sequence {@code [first, {pivot}, third]}, dropping empties. */
    private static void splice(List<List<Integer>> partition, int i, List<Integer> first, int pivot,
                               List<Integer> third) {
        List<Integer> mid = new ArrayList<>();
        mid.add(pivot);
        boolean firstEmpty = first.isEmpty();
        boolean thirdEmpty = third.isEmpty();
        if (firstEmpty && thirdEmpty) {
            partition.set(i, mid);
        } else if (firstEmpty) {
            partition.set(i, mid);
            partition.add(i + 1, third);
        } else if (thirdEmpty) {
            partition.set(i, first);
            partition.add(i + 1, mid);
        } else {
            partition.set(i, first);
            partition.add(i + 1, mid);
            partition.add(i + 2, third);
        }
    }

    /** Refines the partition by the pivot set {@code s = N(y) \ E}, splitting straddling parts. */
    private void refine(List<List<Integer>> partition, Set<Integer> s, int y, int[] center,
                        List<List<Integer>> pivots, List<List<Integer>> modules) {
        int i = -1;
        boolean between = false;
        while (i + 1 < partition.size()) {
            i++;
            List<Integer> x = partition.get(i);
            if (x.contains(center[0]) || x.contains(y)) {
                between = !between;
                continue;
            }
            List<Integer> xa = new ArrayList<>();
            List<Integer> xrest = new ArrayList<>();
            for (int z : x) {
                if (s.contains(z)) {
                    xa.add(z);
                } else {
                    xrest.add(z);
                }
            }
            if (xa.isEmpty() || xrest.isEmpty()) {
                continue;
            }
            partition.set(i, xrest);
            partition.add(i + (between ? 1 : 0), xa);
            addPivot(xrest, xa, pivots, modules);
            i++;
        }
    }

    /** Records the two halves of a freshly split part as a new pivot and (smaller/larger) module. */
    private static void addPivot(List<Integer> x, List<Integer> xa, List<List<Integer>> pivots,
                                 List<List<Integer>> modules) {
        if (pivots.contains(x)) {
            pivots.add(xa);
        } else {
            int idx = modules.indexOf(x);
            List<Integer> smaller;
            List<Integer> larger;
            if (x.size() <= xa.size()) {
                smaller = x;
                larger = xa;
            } else {
                smaller = xa;
                larger = x;
            }
            pivots.add(smaller);
            if (idx >= 0) {
                modules.set(idx, larger);
            } else {
                modules.add(larger);
            }
        }
    }

    /**
     * Parenthesizes the factorizing permutation into the fracture tree. {@code op[i]} /
     * {@code cl[i]} count opening / closing brackets at position {@code i}; {@code lc} /
     * {@code uc} record the left / right "cutter" of each gap (the witness that the
     * consecutive vertices differ). Quadratic readable variant from {@code base.rs}.
     */
    private void buildParenthesizing(int[] p, int[] op, int[] cl, int[] lc, int[] uc) {
        for (int j = 0; j < n - 1; j++) {
            for (int i = 0; i < j; i++) {
                if (adj[p[i]][p[j]] != adj[p[i]][p[j + 1]]) {
                    op[i]++;
                    cl[j]++;
                    lc[j] = i;
                    break;
                }
            }
            for (int i = n - 1; i > j + 1; i--) {
                if (adj[p[i]][p[j]] != adj[p[i]][p[j + 1]]) {
                    op[j + 1]++;
                    cl[i]++;
                    uc[j] = i;
                    break;
                }
            }
        }
    }

    /**
     * Post-order walk of the fracture tree that deletes bracket pairs whose span is not
     * a module (its cutters reach outside the span).
     */
    private static void removeNonModuleDummyNodes(int[] op, int[] cl, int[] lc, int[] uc) {
        int n = op.length;
        Deque<Integer> stack = new ArrayDeque<>();
        for (int j = 0; j < n; j++) {
            int opens = op[j];
            int closes = cl[j];
            for (int t = 0; t < opens; t++) {
                stack.push(j);
            }
            for (int t = 0; t < closes; t++) {
                int i = stack.pop();
                if (i < j) {
                    int l = lc[i];
                    int u = uc[i];
                    for (int k = i + 1; k < j; k++) {
                        l = Math.min(l, lc[k]);
                        u = Math.max(u, uc[k]);
                    }
                    if (i <= l && u <= j) {
                        continue;
                    }
                }
                op[i]--;
                cl[j]--;
            }
        }
    }

    /** Inserts brackets that group maximal runs of consecutive twin children. */
    private static void createConsecutiveTwinNodes(int[] op, int[] cl, int[] lc, int[] uc) {
        int n = op.length;
        Deque<int[]> stack = new ArrayDeque<>();
        int l = 0;
        for (int k = 0; k < n; k++) {
            stack.push(new int[]{k, l});
            l = k;
            for (int t = 0; t < op[k]; t++) {
                stack.push(new int[]{k, k});
            }
            for (int c = cl[k]; c >= 0; c--) {
                int[] top = stack.pop();
                int j = top[0];
                int i = top[1];
                l = i;
                if (i >= j) {
                    continue;
                }
                if (i <= lc[j - 1] && lc[j - 1] < uc[j - 1] && uc[j - 1] <= k) {
                    if (c > 0) {
                        op[i]++;
                        cl[k]++;
                        l = k + 1;
                    }
                } else {
                    if (i < j - 1) {
                        op[i]++;
                        cl[j - 1]++;
                    }
                    l = j;
                }
            }
        }
    }

    /** Removes redundant single-child bracket pairs, then strips the outermost (root) pair. */
    private static void removeSingletonDummyNodes(int[] op, int[] cl) {
        int n = op.length;
        Deque<Integer> stack = new ArrayDeque<>();
        for (int j = 0; j < n; j++) {
            int opens = op[j];
            int closes = cl[j];
            for (int t = 0; t < opens; t++) {
                stack.push(j);
            }
            int prev = Integer.MIN_VALUE;
            for (int t = 0; t < closes; t++) {
                int i = stack.pop();
                if (i == prev) {
                    op[i]--;
                    cl[j]--;
                }
                prev = i;
            }
        }
        op[0]--;
        cl[n - 1]--;
    }

    /** Reads the bracketed permutation into a forest of {@link Raw} nodes (root level = top modules). */
    private List<Raw> buildFractureForest(int[] p, int[] op, int[] cl) {
        Deque<List<Raw>> stack = new ArrayDeque<>();
        stack.push(new ArrayList<>());
        for (int j = 0; j < n; j++) {
            for (int t = 0; t < op[j]; t++) {
                stack.push(new ArrayList<>());
            }
            stack.peek().add(Raw.leaf(p[j]));
            for (int t = 0; t < cl[j]; t++) {
                List<Raw> node = stack.pop();
                stack.peek().add(Raw.group(node));
            }
        }
        return stack.pop();
    }

    /**
     * Types a fracture group as parallel / series / prime by counting the edges between
     * its children (using one representative leaf per child — valid because children are
     * modules) and recurses. Mirrors {@code classify_nodes} in {@code base.rs}.
     */
    private MDNode classifyGroup(List<Raw> children) {
        if (children.size() == 1) {
            return toNode(children.get(0));
        }
        int k = children.size();
        int[] reps = new int[k];
        for (int i = 0; i < k; i++) {
            reps[i] = children.get(i).firstLeaf();
        }
        long edges = 0;
        for (int i = 0; i < k; i++) {
            for (int jj = i + 1; jj < k; jj++) {
                if (adj[reps[i]][reps[jj]]) {
                    edges++;
                }
            }
        }
        Kind kind;
        if (edges == 0) {
            kind = Kind.PARALLEL;
        } else if (2 * edges == (long) k * (k - 1)) {
            kind = Kind.SERIES;
        } else {
            kind = Kind.PRIME;
        }
        List<MDNode> mdChildren = new ArrayList<>(k);
        List<Integer> verts = new ArrayList<>();
        for (Raw child : children) {
            MDNode node = toNode(child);
            mdChildren.add(node);
            verts.addAll(node.vertices());
        }
        return new MDNode(kind, verts, mdChildren);
    }

    /** Converts a single {@link Raw} node into an {@link MDNode} (leaf, or a classified group). */
    private MDNode toNode(Raw raw) {
        if (raw.isLeaf()) {
            return new MDNode(Kind.LEAF, List.of(raw.leafVertex()), List.of());
        }
        return classifyGroup(raw.children());
    }

    /**
     * A canonical, order-invariant signature of the decomposition tree: each node's
     * kind plus the sorted signatures of its children (a leaf carries its vertex id).
     * Two graphs share a modular decomposition iff their signatures are equal — the
     * exact invariant the differential test pins the linear builder against (the
     * orientation count and the factor-graph levels both derive from this tree, but
     * the levels' display order does not, so they are not directly comparable across
     * builders; see plan §7). Package-private for that test.
     *
     * @return the canonical tree signature
     */
    String treeSignature() {
        return signature(tree());
    }

    private static String signature(MDNode node) {
        if (node.kind() == Kind.LEAF) {
            return "v" + node.vertices().get(0);
        }
        List<String> childSignatures = new ArrayList<>(node.children().size());
        for (MDNode child : node.children()) {
            childSignatures.add(signature(child));
        }
        childSignatures.sort(Comparator.naturalOrder());
        String tag = switch (node.kind()) {
            case PARALLEL -> "P";
            case SERIES -> "S";
            case PRIME -> "R";
            case LEAF -> "v";
        };
        return tag + "[" + String.join(",", childSignatures) + "]";
    }

    // ------------------------------------------------------------------
    // Orientation count
    // ------------------------------------------------------------------

    /** Number of distinct transitive orientations, assuming the graph is a comparability graph. */
    BigInteger orientationCount() {
        if (n == 0) {
            return BigInteger.ONE;
        }
        return count(tree());
    }

    /** Folds the tree: a series node contributes {@code k!}, a prime node {@code 2}, parallel/leaf {@code 1}. */
    private BigInteger count(MDNode node) {
        BigInteger prod = BigInteger.ONE;
        for (MDNode child : node.children()) {
            prod = prod.multiply(count(child));
        }
        return switch (node.kind()) {
            case LEAF, PARALLEL -> prod;
            case SERIES -> factorial(node.children().size()).multiply(prod);
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
            // Level 0 is the original graph: reuse the shared tree's root (the expensive
            // decomposition) instead of recomputing it. Deeper levels are cheap quotient graphs.
            List<List<Integer>> blocks = level == 0 ? rootBlocks(tree()) : levelBlocks(curAdj, curN);
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

    /**
     * Level-0 blocks taken from the shared decomposition tree's root, applying the same
     * degenerate-flattening rule as {@link #levelBlocks}: a complete (series) or empty
     * (parallel) graph is shown as a single module.
     */
    private List<List<Integer>> rootBlocks(MDNode root) {
        if (root.kind() == Kind.LEAF) {
            return List.of(root.vertices());
        }
        List<List<Integer>> blocks = new ArrayList<>();
        boolean allSingletons = true;
        for (MDNode child : root.children()) {
            blocks.add(child.vertices());
            if (child.vertices().size() != 1) {
                allSingletons = false;
            }
        }
        if (allSingletons && (root.kind() == Kind.SERIES || root.kind() == Kind.PARALLEL)) {
            return List.of(root.vertices());
        }
        return blocks;
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
        Set<Integer> seen = new HashSet<>();
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
        Set<Integer> unclassified = new LinkedHashSet<>(verts);
        while (!unclassified.isEmpty()) {
            int x = unclassified.iterator().next();
            Set<Integer> block = new LinkedHashSet<>();
            block.add(x);
            for (int y : verts) {
                if (y != x && unclassified.contains(y) && !block.contains(y)) {
                    Set<Integer> mm = minimalModule(verts, x, y);
                    if (mm.size() < verts.size()) {
                        block.addAll(mm); // the whole proper module shares x's class
                    }
                }
            }
            unclassified.removeAll(block);
            blocks.add(new ArrayList<>(block));
        }
        return blocks;
    }

    /**
     * Smallest module of the induced subgraph on {@code verts} containing {@code x}
     * and {@code y}, grown by absorbing every vertex that distinguishes the set.
     * {@code adjCount[w]} tracks how many current members {@code w} is adjacent to,
     * updated incrementally so each membership test is O(1) (overall O(n²)).
     */
    private Set<Integer> minimalModule(List<Integer> verts, int x, int y) {
        Set<Integer> m = new HashSet<>();
        m.add(x);
        m.add(y);
        int[] adjCount = new int[n];
        for (int w : verts) {
            adjCount[w] = (adj[w][x] ? 1 : 0) + (adj[w][y] ? 1 : 0);
        }
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int w : verts) {
                if (!m.contains(w) && adjCount[w] > 0 && adjCount[w] < m.size()) {
                    m.add(w); // w is adjacent to some but not all of m, so a module must absorb it
                    for (int u : verts) {
                        if (adj[u][w]) {
                            adjCount[u]++;
                        }
                    }
                    changed = true;
                }
            }
        }
        return m;
    }
}
