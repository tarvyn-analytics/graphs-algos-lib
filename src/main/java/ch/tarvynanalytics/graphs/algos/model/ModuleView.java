package ch.tarvynanalytics.graphs.algos.model;

import java.util.List;

/**
 * One module (stable set) found at a decomposition level. It collapses to the
 * factor-graph vertex {@code factorNodeId} at the next level.
 *
 * @param factorNodeId  id of the vertex this module becomes in the level's factor graph
 * @param type          the kind of module
 * @param memberNodeIds ids (within this level's graph) of the vertices in the module
 */
public record ModuleView(int factorNodeId, ModuleType type, List<Integer> memberNodeIds) {

    /**
     * Canonical constructor; defensively copies the member list.
     *
     * @param factorNodeId  id of the factor vertex
     * @param type          the module type
     * @param memberNodeIds the member vertex ids
     */
    public ModuleView {
        memberNodeIds = List.copyOf(memberNodeIds);
    }

    /**
     * @return the number of vertices in this module
     */
    public int cardinality() {
        return memberNodeIds.size();
    }
}
