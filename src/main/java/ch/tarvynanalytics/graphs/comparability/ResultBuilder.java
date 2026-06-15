package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;
import ch.tarvynanalytics.graphs.comparability.model.EdgeView;
import ch.tarvynanalytics.graphs.comparability.model.FactorGraphLevelView;
import ch.tarvynanalytics.graphs.comparability.model.FailureCycle;
import ch.tarvynanalytics.graphs.comparability.model.GraphView;
import ch.tarvynanalytics.graphs.comparability.model.ModuleType;
import ch.tarvynanalytics.graphs.comparability.model.ModuleView;
import ch.tarvynanalytics.graphs.comparability.model.NodeView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts the engine's mutable state into the immutable public
 * {@link AnalysisResult}. Factor-graph nodes are labelled by the set of original
 * (leaf) vertices they stand for, and the failure cycle is lifted back to a
 * chordless odd cycle of original vertices via one representative per module.
 */
final class ResultBuilder {

    private ResultBuilder() {
    }

    static AnalysisResult build(GraphInput input, GraphParser parser) {
        Graph level0 = parser.fGraphLevels.get(0).initGraph;
        GraphView inputGraph = toView(level0);

        List<FactorGraphLevelView> levels = new ArrayList<>();
        for (int k = 0; k < parser.fGraphLevels.size(); k++) {
            FactorGraphLevel lvl = parser.fGraphLevels.get(k);
            levels.add(new FactorGraphLevelView(
                    k,
                    toView(lvl.initGraph),
                    modulesOf(lvl),
                    toView(lvl.factorGraph)));
        }

        FailureCycle failure = parser.acceptsTransitiveOrientation
                ? null
                : buildFailure(input, parser);

        return new AnalysisResult(
                parser.acceptsTransitiveOrientation,
                inputGraph,
                levels,
                parser.calcNumOfOrientations(),
                failure);
    }

    private static FailureCycle buildFailure(GraphInput input, GraphParser parser) {
        List<Integer> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (Node cycleNode : parser.failureCycle) {
            Node leaf = representativeLeaf(cycleNode);
            ids.add(leaf.id);
            labels.add(leaf.name);
        }
        boolean hasCorr = input.hasCorrelation();
        double weakest = hasCorr ? parser.failureMinCorrel : Double.NaN;
        int weakestFrom = hasCorr ? parser.failureWeakestFrom : -1;
        int weakestTo = hasCorr ? parser.failureWeakestTo : -1;
        return new FailureCycle(
                Math.max(0, parser.failureFactorGraphLevel - 1),
                ids,
                labels,
                weakest,
                weakestFrom,
                weakestTo);
    }

    private static GraphView toView(Graph g) {
        Map<Node, Integer> idx = indexMap(g.nodes);
        List<NodeView> nodes = new ArrayList<>();
        for (int i = 0; i < g.nodes.size(); i++) {
            nodes.add(new NodeView(i, labelOf(g.nodes.get(i))));
        }
        List<EdgeView> edges = new ArrayList<>();
        for (int i = 0; i < g.nodes.size(); i++) {
            Node a = g.nodes.get(i);
            for (Node b : a.adjacentNodes) {
                Integer j = idx.get(b);
                if (j != null && i < j) {
                    edges.add(new EdgeView(i, j));
                }
            }
        }
        return new GraphView(nodes, edges);
    }

    private static List<ModuleView> modulesOf(FactorGraphLevel lvl) {
        Map<Node, Integer> idx = indexMap(lvl.initGraph.nodes);
        List<ModuleView> out = new ArrayList<>();
        int factorId = 0;
        factorId = addModules(out, lvl.notLinkedMaxStable, ModuleType.INDEPENDENT_SET, idx, factorId);
        factorId = addModules(out, lvl.fullMaxStable, ModuleType.CLIQUE, idx, factorId);
        addModules(out, lvl.minStable, ModuleType.MIN_STABLE, idx, factorId);
        return out;
    }

    private static int addModules(List<ModuleView> out, List<Graph> modules, ModuleType type,
                                  Map<Node, Integer> idx, int factorIdStart) {
        int factorId = factorIdStart;
        for (Graph module : modules) {
            List<Integer> members = new ArrayList<>();
            for (Node n : module.nodes) {
                Integer i = idx.get(n);
                if (i != null) {
                    members.add(i);
                }
            }
            ModuleType actual = members.size() == 1 ? ModuleType.SINGLETON : type;
            out.add(new ModuleView(factorId++, actual, members));
        }
        return factorId;
    }

    private static Map<Node, Integer> indexMap(List<Node> nodes) {
        Map<Node, Integer> idx = new IdentityHashMap<>();
        for (int i = 0; i < nodes.size(); i++) {
            idx.put(nodes.get(i), i);
        }
        return idx;
    }

    private static String labelOf(Node n) {
        if (n.respectiveSubGraph == null) {
            return n.name;
        }
        List<Node> leaves = leaves(n);
        leaves.sort(Comparator.comparingInt(x -> x.id));
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < leaves.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(leaves.get(i).name);
        }
        return sb.append('}').toString();
    }

    private static Node representativeLeaf(Node n) {
        Node current = n;
        while (current.respectiveSubGraph != null) {
            current = current.respectiveSubGraph.nodes.get(0);
        }
        return current;
    }

    private static List<Node> leaves(Node n) {
        List<Node> out = new ArrayList<>();
        if (n.respectiveSubGraph == null) {
            out.add(n);
        } else {
            for (Node sub : n.respectiveSubGraph.nodes) {
                out.addAll(leaves(sub));
            }
        }
        return out;
    }
}
