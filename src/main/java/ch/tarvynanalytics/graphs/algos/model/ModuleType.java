package ch.tarvynanalytics.graphs.algos.model;

/**
 * The kind of stable set (module) a {@link ModuleView} represents in the
 * modular decomposition of a level. Every vertex of a level belongs to exactly
 * one module, and each module collapses to a single vertex of the next level's
 * factor graph.
 */
public enum ModuleType {

    /** A module consisting of a single vertex. */
    SINGLETON,

    /**
     * A maximal module whose members are pairwise non-adjacent
     * (an independent set that is also a module).
     */
    INDEPENDENT_SET,

    /**
     * A maximal module whose members are pairwise adjacent
     * (a clique / complete subgraph that is also a module).
     */
    CLIQUE,

    /**
     * A module that is neither a clique nor an independent set — its induced
     * subgraph has both edges and non-edges (a prime or otherwise non-degenerate
     * module of the modular decomposition).
     */
    MIN_STABLE
}
