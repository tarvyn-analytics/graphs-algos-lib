package ch.tarvynanalytics.graphs.comparability;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The decomposition and comparability-test engine — a faithful, analysis-only
 * Java port of the thesis prototype's {@code GraphParser}.
 *
 * <p>It repeatedly partitions the current graph into maximal stable sets
 * (modules) of three kinds — independent-set ({@code notLinkedMaxStable}),
 * clique ({@code fullMaxStable}) and minimal ({@code minStable}) — collapses
 * each into one vertex of a factor (quotient) graph, and recurses on that factor
 * graph. While building a minimal module it grows a chordless ("non-triangulable")
 * chain; if that chain can close into an odd cycle the graph is not a
 * comparability graph and the obstructing cycle is recorded.</p>
 *
 * <p>The orientation bookkeeping ({@code nodesTo}/{@code nodesFrom}/
 * {@code nodesNotOriented}) is retained because it drives the chain-extension
 * that decides which edges a minimal module's chain must cover; the concrete
 * directions are scratch state and are reset before the odd-cycle test. No
 * concrete orientation is emitted — only the verdict, the decomposition and the
 * orientation count are exposed.</p>
 */
final class GraphParser {

    final List<FactorGraphLevel> fGraphLevels = new ArrayList<>();
    boolean acceptsTransitiveOrientation = true;

    /** The obstructing odd chordless cycle (level nodes), or {@code null} if none. */
    List<Node> failureCycle;
    /** One-based count of levels at the moment the failure was detected (matches the original). */
    int failureFactorGraphLevel = -1;
    /** Smallest-magnitude correlation on the failure cycle (1.0 sentinel until updated). */
    double failureMinCorrel = 1.0;
    /** Original (leaf) endpoints of the weakest edge on the failure cycle. */
    int failureWeakestFrom = -1;
    int failureWeakestTo = -1;
    /** Highest processed level index (mirrors {@code totalFGraphs}). */
    int totalFactorGraphs;

    /** Argmin endpoints from the most recent {@link #findMinEdge} call. */
    private int lastMinEdgeFrom = -1;
    private int lastMinEdgeTo = -1;

    GraphParser(Graph initGraph) {
        for (Node n : initGraph.nodes) {
            n.nodesFrom.clear();
            n.nodesTo.clear();
            n.nodesNotOriented.clear();
            n.nodesNotOriented.addAll(n.adjacentNodes);
        }
        fGraphLevels.add(new FactorGraphLevel(initGraph));
    }

    private FactorGraphLevel last() {
        return fGraphLevels.get(fGraphLevels.size() - 1);
    }

    /**
     * Runs the full decomposition.
     *
     * @return {@code true} iff the input is a comparability graph
     */
    boolean parseGraph() {
        acceptsTransitiveOrientation = true;
        while (generateSubGraphs() < last().initGraph.nodes.size() && acceptsTransitiveOrientation) {
            generateFactorGraph();
            if (last().factorGraph.nodes.size() > 1) {
                fGraphLevels.add(new FactorGraphLevel(last().factorGraph));
            } else {
                break;
            }
        }
        totalFactorGraphs = fGraphLevels.size() - 1;
        return acceptsTransitiveOrientation;
    }

    private int generateSubGraphs() {
        FactorGraphLevel curr = last();
        List<Node> pool = new ArrayList<>(curr.initGraph.nodes);
        generateNotLinkedMaxStables(curr, pool, true);
        generateFullMaxStables(curr, pool);
        acceptsTransitiveOrientation = generateMinStables(curr, pool);
        if (acceptsTransitiveOrientation) {
            generateNotLinkedMaxStables(curr, pool, false);
            for (Node n : pool) { // every remaining node becomes a K1 clique module
                List<Node> k1 = new ArrayList<>();
                k1.add(n);
                curr.fullMaxStable.add(new Graph(k1));
            }
        }
        return curr.notLinkedMaxStable.size() + curr.fullMaxStable.size() + curr.minStable.size();
    }

    private boolean generateMinStables(FactorGraphLevel curr, List<Node> pool) {
        List<Node> newMinStableNodes = new ArrayList<>();
        List<Node> nonTriangChain = new ArrayList<>();
        List<Node> usedNodes = new ArrayList<>();
        if (pool.size() > 2) {
            for (int i = 0; i < pool.size() - 1; i++) {
                if (!usedNodes.contains(pool.get(i))) {
                    for (int j = i + 1; j < pool.size(); j++) {
                        if (!usedNodes.contains(pool.get(j)) && pool.get(i).adjacentNodes.contains(pool.get(j))) {
                            nonTriangChain.add(pool.get(i));
                            nonTriangChain.add(pool.get(j));
                            if (getNonTriangChain(nonTriangChain, pool)) {
                                for (Node n : nonTriangChain) {
                                    if (!newMinStableNodes.contains(n)) {
                                        newMinStableNodes.add(n);
                                        usedNodes.add(n);
                                    }
                                }
                                if (isStableGroup(newMinStableNodes)) {
                                    Graph minSt = new Graph(newMinStableNodes);
                                    curr.minStable.add(minSt);
                                    minSt.nonTriangulableChain = new ArrayList<>(nonTriangChain);
                                    minSt.nonTriangulableChainAllLinks = new ArrayList<>(minSt.nonTriangulableChain);
                                    orientLinksInMinStable(minSt.nonTriangulableChainAllLinks);
                                    continueNonTriangChain(minSt.nonTriangulableChainAllLinks);
                                    resetOrientation(minSt.nodes);
                                    if (canCreateOddCycle(minSt.nonTriangulableChainAllLinks)) {
                                        return false;
                                    }
                                    break;
                                } else {
                                    for (Node n : newMinStableNodes) {
                                        usedNodes.remove(n);
                                    }
                                    newMinStableNodes.clear();
                                }
                                nonTriangChain = new ArrayList<>();
                            } else {
                                return false;
                            }
                        }
                    }
                }
                for (Node n : usedNodes) {
                    pool.remove(n);
                }
                usedNodes.clear();
                newMinStableNodes.clear();
                nonTriangChain.clear();
            }
        }
        return true;
    }

    private static void resetOrientation(List<Node> nodes) {
        for (Node n : nodes) {
            n.nodesFrom.clear();
            n.nodesTo.clear();
            n.nodesNotOriented.clear();
            n.nodesNotOriented.addAll(n.adjacentNodes);
        }
    }

    private boolean getNonTriangChain(List<Node> chain, List<Node> pool) {
        boolean acceptsTranzOrient = true;
        if (!pool.isEmpty()) {
            boolean canContinueChain;
            do {
                canContinueChain = false;
                Node nodeToAdd = null;
                Node nodeToAppendTo = null;
                Node head = chain.get(chain.size() - 1);
                Node beforeHead = chain.get(chain.size() - 2);
                for (Node n : head.adjacentNodes) {
                    if (pool.contains(n) && !beforeHead.adjacentNodes.contains(n) && !chain.contains(n)) {
                        nodeToAdd = n;
                        nodeToAppendTo = head;
                        canContinueChain = true;
                        break;
                    }
                }
                if (!canContinueChain && chain.size() > 1) {
                    for (int i = chain.size() - 2; i > -1 && !canContinueChain; i--) {
                        for (Node n : chain.get(i).adjacentNodes) {
                            if (pool.contains(n) && !chain.get(i + 1).adjacentNodes.contains(n) && !chain.contains(n)) {
                                nodeToAdd = n;
                                nodeToAppendTo = chain.get(i);
                                canContinueChain = true;
                                break;
                            }
                        }
                    }
                }
                if (canContinueChain) {
                    appendToChain(chain, nodeToAdd, nodeToAppendTo);
                    acceptsTranzOrient = !canCreateOddCycle(chain);
                }
            } while (canContinueChain && acceptsTranzOrient);
        }
        return acceptsTranzOrient;
    }

    private void appendToChain(List<Node> chain, Node nodeToAdd, Node nodeToAppendTo) {
        int i = chain.size() - 2;
        while (chain.get(chain.size() - 1) != nodeToAppendTo) {
            chain.add(chain.get(i));
            i--;
        }
        chain.add(nodeToAdd);
    }

    private List<Node> getActualNodes(Node subgraf) {
        List<Node> actual = new ArrayList<>();
        if (subgraf.respectiveSubGraph == null) {
            actual.add(subgraf);
        } else {
            for (Node subNode : subgraf.respectiveSubGraph.nodes) {
                actual.addAll(getActualNodes(subNode));
            }
        }
        return actual;
    }

    private double findMinEdge(Node from, Node to) {
        List<Node> nodesFrom = getActualNodes(from);
        List<Node> nodesTo = getActualNodes(to);
        double minEdge = 1.0;
        boolean found = false;
        for (Node nf : nodesFrom) {
            double[][] cm = nf.parentGraph.correlMatrix;
            if (cm == null) {
                lastMinEdgeFrom = -1;
                lastMinEdgeTo = -1;
                return Double.NaN;
            }
            for (Node nt : nodesTo) {
                double currEdge = cm[nf.id][nt.id];
                if (currEdge != 0.0 && Math.abs(currEdge) < Math.abs(minEdge)) {
                    minEdge = currEdge;
                    lastMinEdgeFrom = nf.id;
                    lastMinEdgeTo = nt.id;
                    found = true;
                }
            }
        }
        if (!found) {
            lastMinEdgeFrom = -1;
            lastMinEdgeTo = -1;
        }
        return minEdge;
    }

    private boolean canCreateOddCycle(List<Node> chain) {
        if (chain.size() <= 4) {
            return false;
        }
        for (int i = chain.size() - 1; i > 3; i--) {
            for (int j = i - 4; j > -1; j -= 2) {
                if (chain.get(i).adjacentNodes.contains(chain.get(j))
                        && !chain.get(i - 1).adjacentNodes.contains(chain.get(j))
                        && !chain.get(i).adjacentNodes.contains(chain.get(j + 1))) {
                    recordFailureCycle(chain, i, j);
                    return true;
                }
            }
        }
        return false;
    }

    private void recordFailureCycle(List<Node> chain, int i, int j) {
        failureCycle = new ArrayList<>();
        failureFactorGraphLevel = fGraphLevels.size();
        for (int k = j; k < i + 1; k++) {
            failureCycle.add(chain.get(k));
            Node failureNodeFrom = chain.get(k);
            Node failureNodeTo = (k < i) ? chain.get(k + 1) : chain.get(j);
            double failureCorrelValue = findMinEdge(failureNodeFrom, failureNodeTo);
            if (Math.abs(failureCorrelValue) < Math.abs(failureMinCorrel)) {
                failureMinCorrel = failureCorrelValue;
                failureWeakestFrom = lastMinEdgeFrom;
                failureWeakestTo = lastMinEdgeTo;
            }
        }
    }

    private void generateFullMaxStables(FactorGraphLevel curr, List<Node> pool) {
        List<List<Node>> stableLinkedPairs = new ArrayList<>();
        for (int i = 0; i < pool.size() - 1; i++) {
            for (int j = i + 1; j < pool.size(); j++) {
                if (pool.get(i).adjacentNodes.contains(pool.get(j))) {
                    List<Node> pair = new ArrayList<>();
                    pair.add(pool.get(i));
                    pair.add(pool.get(j));
                    if (isStableGroup(pair)) {
                        stableLinkedPairs.add(pair);
                    }
                }
            }
        }
        while (!stableLinkedPairs.isEmpty()) {
            List<Node> newFull = new ArrayList<>();
            newFull.add(stableLinkedPairs.get(0).get(0));
            newFull.add(stableLinkedPairs.get(0).get(1));
            stableLinkedPairs.remove(0);
            for (List<Node> pair : stableLinkedPairs) {
                if (newFull.contains(pair.get(0)) && !newFull.contains(pair.get(1))) {
                    newFull.add(pair.get(1));
                } else if (newFull.contains(pair.get(1)) && !newFull.contains(pair.get(0))) {
                    newFull.add(pair.get(0));
                }
            }
            curr.fullMaxStable.add(new Graph(newFull));
            int i = 0;
            while (i < stableLinkedPairs.size()) {
                if (newFull.contains(stableLinkedPairs.get(i).get(0))) {
                    stableLinkedPairs.remove(i);
                } else {
                    i++;
                }
            }
            for (Node n : newFull) {
                pool.remove(n);
            }
        }
    }

    private void generateNotLinkedMaxStables(FactorGraphLevel curr, List<Node> pool, boolean firstCall) {
        if (firstCall) {
            List<Node> newSet = new ArrayList<>();
            for (Node n : pool) {
                if (n.adjacentNodes.isEmpty()) {
                    newSet.add(n);
                }
            }
            if (!newSet.isEmpty()) {
                curr.notLinkedMaxStable.add(new Graph(newSet));
                for (Node n : newSet) {
                    pool.remove(n);
                }
            }
            return;
        }
        List<Node> newSet = new ArrayList<>();
        while (existsPairOfNotLinkedStableNodes(pool, newSet)) {
            for (int i = 0; i < pool.size(); i++) {
                if (!newSet.contains(pool.get(i))) {
                    int j;
                    for (j = 0; j < newSet.size(); j++) {
                        if (newSet.get(j).adjacentNodes.contains(pool.get(i))) {
                            break;
                        }
                    }
                    if (j == newSet.size()) {
                        newSet.add(pool.get(i));
                        if (!isStableGroup(newSet)) {
                            newSet.remove(pool.get(i));
                        }
                    }
                }
            }
            curr.notLinkedMaxStable.add(new Graph(newSet));
            for (Node n : newSet) {
                pool.remove(n);
            }
            newSet = new ArrayList<>();
        }
    }

    private boolean existsPairOfNotLinkedStableNodes(List<Node> pool, List<Node> notLinkedPair) {
        if (pool.size() < 2) {
            return false;
        }
        for (int i = 0; i < pool.size() - 1; i++) {
            for (int j = i + 1; j < pool.size(); j++) {
                if (!pool.get(i).adjacentNodes.contains(pool.get(j))) {
                    notLinkedPair.add(pool.get(i));
                    notLinkedPair.add(pool.get(j));
                    if (isStableGroup(notLinkedPair)) {
                        return true;
                    }
                    notLinkedPair.clear();
                }
            }
        }
        return false;
    }

    private boolean isStableGroup(List<Node> group) {
        for (Node n : last().initGraph.nodes) {
            if (!group.contains(n)) {
                boolean adjToFirst = group.get(0).adjacentNodes.contains(n);
                for (int i = 1; i < group.size(); i++) {
                    if (adjToFirst != group.get(i).adjacentNodes.contains(n)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private void generateFactorGraph() {
        FactorGraphLevel curr = last();
        Graph fg = curr.factorGraph;
        for (Graph g : curr.notLinkedMaxStable) {
            fg.nodes.add(new Node(fg, g));
        }
        for (Graph g : curr.fullMaxStable) {
            fg.nodes.add(new Node(fg, g));
        }
        for (Graph g : curr.minStable) {
            fg.nodes.add(new Node(fg, g));
        }
        for (int i = 0; i < fg.nodes.size() - 1; i++) {
            for (int j = i + 1; j < fg.nodes.size(); j++) {
                Node a = fg.nodes.get(i);
                Node b = fg.nodes.get(j);
                if (a.respectiveSubGraph.nodes.get(0).adjacentNodes.contains(b.respectiveSubGraph.nodes.get(0))) {
                    a.adjacentNodes.add(b);
                    a.nodesNotOriented.add(b);
                    b.adjacentNodes.add(a);
                    b.nodesNotOriented.add(a);
                }
            }
        }
    }

    /**
     * @return the number of distinct transitive orientations, or zero if the
     *         graph is not a comparability graph
     */
    BigInteger calcNumOfOrientations() {
        if (!acceptsTransitiveOrientation) {
            return BigInteger.ZERO;
        }
        BigInteger total = BigInteger.ONE;
        for (int i = 0; i <= totalFactorGraphs; i++) {
            FactorGraphLevel lvl = fGraphLevels.get(i);
            total = total.multiply(BigInteger.TWO.pow(lvl.minStable.size()));
            for (Graph g : lvl.fullMaxStable) {
                total = total.multiply(factorial(g.nodes.size()));
            }
        }
        return total;
    }

    private static BigInteger factorial(int n) {
        BigInteger f = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            f = f.multiply(BigInteger.valueOf(i));
        }
        return f;
    }

    // ------------------------------------------------------------------
    // Chain-extension / orientation bookkeeping. The directions produced
    // here are scratch state (reset before the odd-cycle test); the purpose
    // is to drive which edges a minimal module's full chain must cover.
    // ------------------------------------------------------------------

    private void orientLinksInMinStable(List<Node> chain) {
        if (chain == null) {
            return;
        }
        for (int i = 0; i < chain.size() - 1; i++) {
            Node a = chain.get(i);
            Node b = chain.get(i + 1);
            if (a.nodesNotOriented.contains(b)) {
                if (i % 2 == 0) {
                    orient(a, b);
                } else {
                    orient(b, a);
                }
            }
        }
    }

    private static void orient(Node from, Node to) {
        from.nodesTo.add(to);
        from.nodesNotOriented.remove(to);
        to.nodesFrom.add(from);
        to.nodesNotOriented.remove(from);
    }

    private void continueNonTriangChain(List<Node> fullChain) {
        if (fullChain == null) {
            return;
        }
        int i;
        do {
            for (i = fullChain.size() - 1; i > -1; i--) {
                if (!fullChain.get(i).nodesNotOriented.isEmpty()) {
                    List<Node> apendix = new ArrayList<>();
                    apendix.add(fullChain.get(i));
                    apendix.add(fullChain.get(i).nodesNotOriented.get(0));
                    int positionToAppend = createNonTriangApendix(fullChain, apendix);
                    if (positionToAppend < 0) {
                        // No triangulation-free attachment point exists; orient the
                        // dangling edge directly so the loop still makes progress.
                        orient(apendix.get(0), apendix.get(1));
                    } else {
                        reverseAppendNonTriangs(fullChain, positionToAppend, apendix);
                    }
                    break;
                }
            }
        } while (i > -1);
    }

    private int createNonTriangApendix(List<Node> fullChain, List<Node> apendix) {
        if (fullChain == null || apendix == null) {
            return -1;
        }
        Node apendixLast = apendix.get(apendix.size() - 1);
        Node apendixPreLast = apendix.get(apendix.size() - 2);
        for (int i = fullChain.size() - 1; i > -1; i--) {
            if (fullChain.get(i) == apendixLast) {
                if (i == fullChain.size() - 1) {
                    return i;
                }
                if (!fullChain.get(i + 1).adjacentNodes.contains(apendixPreLast)) {
                    if (i + 1 == fullChain.size() - 1) {
                        return i;
                    }
                    if (!fullChain.get(i + 2).adjacentNodes.contains(apendixLast)) {
                        return i;
                    }
                }
            }
        }
        if (increaseApendix(apendix) < 0) {
            return -1;
        }
        return createNonTriangApendix(fullChain, apendix);
    }

    private int increaseApendix(List<Node> apendix) {
        if (apendix == null) {
            return -1;
        }
        for (int i = apendix.size() - 1; i > -1; i--) {
            for (Node n : apendix.get(i).adjacentNodes) {
                if (i == apendix.size() - 1) {
                    if (!apendix.get(i - 1).adjacentNodes.contains(n) && apendix.get(i - 1) != n) {
                        apendix.add(n);
                        return 0;
                    }
                } else if (!apendix.get(i + 1).adjacentNodes.contains(n) && apendix.get(i + 1) != n) {
                    for (int k = apendix.size() - 2; k > i; k--) {
                        apendix.add(apendix.get(k));
                    }
                    apendix.add(apendix.get(i));
                    apendix.add(n);
                    return 1;
                }
            }
        }
        return -1;
    }

    private void reverseAppendNonTriangs(List<Node> fullChain, int positionToAppend, List<Node> apendix) {
        int currInFullIdx = fullChain.size() - 2;
        while (positionToAppend <= currInFullIdx) {
            fullChain.add(fullChain.get(currInFullIdx));
            currInFullIdx--;
        }
        for (int i = apendix.size() - 2; i > -1; i--) {
            fullChain.add(apendix.get(i));
            Node last = fullChain.get(fullChain.size() - 1);
            Node preLast = fullChain.get(fullChain.size() - 2);
            if (preLast.nodesNotOriented.contains(last)) {
                Node prePreLast = fullChain.get(fullChain.size() - 3);
                if (preLast.nodesTo.contains(prePreLast)) {
                    orient(preLast, last);
                } else {
                    orient(last, preLast);
                }
            }
        }
    }
}
