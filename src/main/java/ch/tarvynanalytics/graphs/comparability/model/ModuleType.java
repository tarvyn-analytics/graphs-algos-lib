package ch.tarvynanalytics.graphs.comparability.model;

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
     * A minimal module that is neither a clique nor an independent set —
     * captured through a non-triangulable (chordless) chain. These are the
     * modules whose orientation feasibility is tested for the obstructing
     * odd cycle.
     */
    MIN_STABLE
}
