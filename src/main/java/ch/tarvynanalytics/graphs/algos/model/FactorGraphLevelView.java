package ch.tarvynanalytics.graphs.algos.model;

import java.util.List;

/**
 * One level of the modular (substitution) decomposition. Level {@code 0} is the
 * input graph; each subsequent level is the factor graph of the previous one.
 *
 * @param level       zero-based level index (0 = input graph)
 * @param graph       the graph analysed at this level
 * @param modules     the stable sets the graph was partitioned into; each becomes
 *                    one vertex of {@code factorGraph}
 * @param factorGraph the quotient graph (one vertex per module); empty if this is
 *                    the final, prime level that was not decomposed further
 */
public record FactorGraphLevelView(int level,
                                   GraphView graph,
                                   List<ModuleView> modules,
                                   GraphView factorGraph) {

    /**
     * Canonical constructor; defensively copies the module list.
     *
     * @param level       the level index
     * @param graph       the graph at this level
     * @param modules     the modules found
     * @param factorGraph the resulting factor graph
     */
    public FactorGraphLevelView {
        modules = List.copyOf(modules);
    }
}
