package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;
import ch.tarvynanalytics.graphs.algos.model.ChordalityView;
import ch.tarvynanalytics.graphs.algos.model.EdgeView;
import ch.tarvynanalytics.graphs.algos.model.FactorGraphLevelView;
import ch.tarvynanalytics.graphs.algos.model.FailureCycle;
import ch.tarvynanalytics.graphs.algos.model.GraphView;
import ch.tarvynanalytics.graphs.algos.model.NodeView;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the engine and assembles the immutable public {@link AnalysisResult}:
 * the comparability verdict and obstruction come from {@link ForcingRelation}
 * (Golumbic's Γ test), the orientation count and factor-graph levels from
 * {@link ModularDecomposition}.
 */
final class ResultBuilder {

    private ResultBuilder() {
    }

    static AnalysisResult build(GraphInput input) {
        int n = input.order();
        boolean[][] adj = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j && input.adjacent(i, j)) {
                    adj[i][j] = true;
                }
            }
        }

        ForcingRelation.Result fr = ForcingRelation.decide(adj);
        ModularDecomposition md = new ModularDecomposition(adj);

        GraphView inputGraph = inputGraphView(input, adj);
        List<FactorGraphLevelView> levels = md.levels(input);
        BigInteger count = fr.comparable ? md.orientationCount() : BigInteger.ZERO;
        FailureCycle failure = fr.comparable ? null : buildFailure(input, fr.failureWalk);
        ChordalityView chordality = buildChordality(input, Chordality.analyze(adj));

        return new AnalysisResult(fr.comparable, inputGraph, levels, count, failure, chordality);
    }

    private static ChordalityView buildChordality(GraphInput input, Chordality.Result result) {
        List<Integer> peoIds = new ArrayList<>();
        List<String> peoLabels = new ArrayList<>();
        for (int v : result.eliminationOrder) {
            peoIds.add(v);
            peoLabels.add(input.label(v));
        }
        List<Integer> holeIds = new ArrayList<>();
        List<String> holeLabels = new ArrayList<>();
        for (int v : result.chordlessCycle) {
            holeIds.add(v);
            holeLabels.add(input.label(v));
        }
        List<EdgeView> fillIn = new ArrayList<>();
        for (int[] e : result.fillIn) {
            fillIn.add(new EdgeView(e[0], e[1]));
        }
        return new ChordalityView(result.chordal, peoIds, peoLabels, holeIds, holeLabels, fillIn);
    }

    private static GraphView inputGraphView(GraphInput input, boolean[][] adj) {
        int n = input.order();
        List<NodeView> nodes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            nodes.add(new NodeView(i, input.label(i)));
        }
        List<EdgeView> edges = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (adj[i][j]) {
                    edges.add(new EdgeView(i, j));
                }
            }
        }
        return new GraphView(nodes, edges);
    }

    private static FailureCycle buildFailure(GraphInput input, int[] walk) {
        List<Integer> ids = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        for (int v : walk) {
            ids.add(v);
            labels.add(input.label(v));
        }

        double weakest = Double.NaN;
        int weakestFrom = -1;
        int weakestTo = -1;
        if (input.hasCorrelation() && walk.length > 0) {
            double best = Double.POSITIVE_INFINITY;
            for (int i = 0; i < walk.length; i++) {
                int a = walk[i];
                int b = walk[(i + 1) % walk.length];
                if (a == b) {
                    continue;
                }
                double c = input.correlation(a, b);
                if (Math.abs(c) < best) {
                    best = Math.abs(c);
                    weakest = c;
                    weakestFrom = a;
                    weakestTo = b;
                }
            }
        }
        return new FailureCycle(0, ids, labels, weakest, weakestFrom, weakestTo);
    }
}
