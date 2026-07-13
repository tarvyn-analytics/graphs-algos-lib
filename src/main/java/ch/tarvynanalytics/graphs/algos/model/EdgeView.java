package ch.tarvynanalytics.graphs.algos.model;

/**
 * An undirected edge between two vertices of a {@link GraphView}, given by
 * their ids. By convention {@code source < target} so each undirected edge is
 * listed exactly once.
 *
 * @param source id of the lower-numbered endpoint
 * @param target id of the higher-numbered endpoint
 */
public record EdgeView(int source, int target) {
}
