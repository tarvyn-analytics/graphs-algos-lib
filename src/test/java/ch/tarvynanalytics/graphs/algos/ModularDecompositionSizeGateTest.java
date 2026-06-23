package ch.tarvynanalytics.graphs.algos;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the size gate (GAL-16, step 4): the default ({@code AUTO}) builder uses the simple
 * recursion below {@link ModularDecomposition#LINEAR_THRESHOLD} and the near-linear
 * {@code fracture} builder at or above it. The two builders are proven equivalent by
 * {@link ModularDecompositionLinearDifferentialTest}, so the gate is a pure performance
 * switch; this test fixes <em>where</em> it flips and confirms the auto path stays correct
 * on both sides of the threshold (extending the differential check above n = 14).
 */
class ModularDecompositionSizeGateTest {

    @Test
    void sizeGate_BelowThreshold_PicksSimpleAtOrAbovePicksLinear() {
        int t = ModularDecomposition.LINEAR_THRESHOLD;
        assertFalse(new ModularDecomposition(new boolean[t - 1][t - 1]).usesLinearBuilder(),
                "graphs smaller than the threshold use the simple recursion");
        assertTrue(new ModularDecomposition(new boolean[t][t]).usesLinearBuilder(),
                "graphs at the threshold use the near-linear builder");
        assertTrue(new ModularDecomposition(new boolean[t + 1][t + 1]).usesLinearBuilder(),
                "graphs above the threshold use the near-linear builder");
    }

    @Test
    void forcedBuilders_OverrideTheGate() {
        int t = ModularDecomposition.LINEAR_THRESHOLD;
        // A large graph forced onto the simple recursion, and a tiny one forced onto linear.
        assertFalse(new ModularDecomposition(new boolean[t + 5][t + 5]).useSimpleBuilder().usesLinearBuilder());
        assertTrue(new ModularDecomposition(new boolean[3][3]).useLinearBuilder().usesLinearBuilder());
    }

    @Test
    void autoPath_AroundAndAboveThreshold_MatchesBothBuilders() {
        int t = ModularDecomposition.LINEAR_THRESHOLD;
        Random rnd = new Random(99L);
        for (int order : new int[]{t - 1, t, t + 1, 60}) {
            for (int rep = 0; rep < 10; rep++) {
                boolean[][] adj = randomGraph(order, 0.1 + rnd.nextDouble() * 0.8, rnd);
                String simple = new ModularDecomposition(adj).useSimpleBuilder().treeSignature();
                String linear = new ModularDecomposition(adj).useLinearBuilder().treeSignature();
                String auto = new ModularDecomposition(adj).treeSignature();
                assertEquals(simple, linear, "simple vs linear for n=" + order + " rep=" + rep);
                // The auto path must equal whichever builder the gate selects for this size.
                assertEquals(order >= t ? linear : simple, auto, "auto path for n=" + order + " rep=" + rep);
            }
        }
    }

    private static boolean[][] randomGraph(int order, double p, Random rnd) {
        boolean[][] adj = new boolean[order][order];
        for (int i = 0; i < order; i++) {
            for (int j = i + 1; j < order; j++) {
                if (rnd.nextDouble() < p) {
                    adj[i][j] = true;
                    adj[j][i] = true;
                }
            }
        }
        return adj;
    }
}
