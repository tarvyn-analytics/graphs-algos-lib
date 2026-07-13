package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.StructuralBalanceView;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides whether the <em>signed</em> correlation graph is structurally balanced
 * (Heider / Harary): its vertices split into two camps that are internally
 * positively correlated and mutually negatively correlated, equivalently every
 * cycle has an even number of negative edges.
 *
 * <p>An edge's sign is the sign of its correlation, so this needs correlation
 * input ({@link GraphInput#fromCorrelation}); a graph built from a plain boolean
 * adjacency carries no signs (every edge is treated as positive), so it is
 * trivially balanced. Balance is sign-dependent, whereas the main
 * {@link ComparabilityAnalyzer} result is sign-agnostic — hence this is a
 * separate, opt-in analyzer.</p>
 *
 * <pre>{@code
 * StructuralBalanceView b = StructuralBalanceAnalyzer.analyze(
 *         GraphInput.fromCorrelation(matrix, 0.5));
 * if (b.isBalanced()) {
 *     System.out.println("two blocs: " + b.verticesInCamp(0) + " vs " + b.verticesInCamp(1));
 * } else {
 *     System.out.println("frustrated cycle: " + b.frustratedCycleLabels());
 * }
 * }</pre>
 */
public final class StructuralBalanceAnalyzer {

    private StructuralBalanceAnalyzer() {
    }

    /**
     * Decides structural balance for the given input.
     *
     * @param input the graph to analyse
     * @return the balance verdict with the two camps (balanced) or a frustrated cycle (not balanced)
     * @throws IllegalArgumentException if {@code input} is {@code null}
     */
    public static StructuralBalanceView analyze(GraphInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        int n = input.order();
        boolean[][] adj = new boolean[n][n];
        boolean[][] negative = new boolean[n][n];
        int negativeEdges = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (input.adjacent(i, j)) {
                    adj[i][j] = true;
                    adj[j][i] = true;
                    if (input.hasCorrelation() && input.correlation(i, j) < 0) {
                        negative[i][j] = true;
                        negative[j][i] = true;
                        negativeEdges++;
                    }
                }
            }
        }

        StructuralBalance.Result result = StructuralBalance.decide(adj, negative);

        List<Integer> camp = new ArrayList<>();
        for (int c : result.camp) {
            camp.add(c);
        }
        List<Integer> cycleIds = new ArrayList<>();
        List<String> cycleLabels = new ArrayList<>();
        for (int v : result.frustratedCycle) {
            cycleIds.add(v);
            cycleLabels.add(input.label(v));
        }
        return new StructuralBalanceView(result.balanced, camp, cycleIds, cycleLabels, negativeEdges);
    }
}
