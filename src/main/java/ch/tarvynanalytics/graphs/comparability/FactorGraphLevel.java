package ch.tarvynanalytics.graphs.comparability;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds everything for one level of the decomposition — the analysis-only port
 * of the thesis prototype's {@code FactorGraphLevel} (drawing/dispose dropped).
 *
 * <p>The three module lists are kept in the order the factor graph lays them
 * out: not-linked (independent-set) modules first, then full (clique) modules,
 * then minimal stable modules.</p>
 */
final class FactorGraphLevel {

    /** The graph analysed at this level (the level's input). */
    final Graph initGraph;
    /** The quotient graph produced from {@link #initGraph}'s modules. */
    final Graph factorGraph = new Graph();

    /** Maximal modules whose members are pairwise non-adjacent. */
    final List<Graph> notLinkedMaxStable = new ArrayList<>();
    /** Maximal modules whose members are pairwise adjacent (cliques). */
    final List<Graph> fullMaxStable = new ArrayList<>();
    /** Minimal modules that are neither cliques nor independent sets. */
    final List<Graph> minStable = new ArrayList<>();

    FactorGraphLevel(Graph initGraph) {
        this.initGraph = initGraph;
    }
}
