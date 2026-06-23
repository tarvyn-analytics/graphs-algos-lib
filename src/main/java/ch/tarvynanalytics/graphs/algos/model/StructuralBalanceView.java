package ch.tarvynanalytics.graphs.algos.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Whether the <em>signed</em> correlation graph is <strong>structurally balanced</strong>
 * (Heider / Harary). A signed graph is balanced iff every cycle has an even number of
 * negative edges — equivalently iff its vertices split into two camps with positive edges
 * within a camp and negative edges between them. For a correlation graph an edge's sign is
 * the sign of the correlation, so balance means the names fall into two blocs that are
 * internally positively correlated and mutually negatively correlated.
 *
 * <p>When {@link #balanced()} is {@code true}, {@link #camp()} gives each vertex's camp
 * (0 or 1) and {@link #frustratedCycle()} is empty. When it is {@code false},
 * {@link #frustratedCycle()} is a witnessing cycle with an odd number of negative edges and
 * {@link #camp()} is empty.</p>
 *
 * @param balanced             whether the signed graph is balanced
 * @param camp                 each vertex's camp (0/1) by id when balanced, otherwise empty
 * @param frustratedCycle      a cycle with an odd number of negative edges when not balanced, else empty
 * @param frustratedCycleLabels the frustrated cycle's vertices as labels, parallel to {@code frustratedCycle}
 * @param negativeEdgeCount    the number of negative edges in the graph (informational)
 */
public record StructuralBalanceView(boolean balanced,
                                    List<Integer> camp,
                                    List<Integer> frustratedCycle,
                                    List<String> frustratedCycleLabels,
                                    int negativeEdgeCount) {

    /**
     * Canonical constructor; defensively copies the lists.
     *
     * @param balanced              the verdict
     * @param camp                  the per-vertex camp assignment
     * @param frustratedCycle       the unbalanced cycle vertex ids
     * @param frustratedCycleLabels the unbalanced cycle labels
     * @param negativeEdgeCount     the number of negative edges
     */
    public StructuralBalanceView {
        camp = List.copyOf(camp);
        frustratedCycle = List.copyOf(frustratedCycle);
        frustratedCycleLabels = List.copyOf(frustratedCycleLabels);
    }

    /**
     * @return {@code true} iff the signed graph is balanced
     */
    public boolean isBalanced() {
        return balanced;
    }

    /**
     * The vertices assigned to one of the two camps (only meaningful when balanced).
     *
     * @param which the camp, {@code 0} or {@code 1}
     * @return the ids of the vertices in that camp, in ascending id order
     */
    public List<Integer> verticesInCamp(int which) {
        List<Integer> members = new ArrayList<>();
        for (int v = 0; v < camp.size(); v++) {
            if (camp.get(v) == which) {
                members.add(v);
            }
        }
        return members;
    }
}
