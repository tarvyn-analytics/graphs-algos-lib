package ch.tarvynanalytics.graphs.comparability.model;

/**
 * A vertex in a {@link GraphView}: a stable numeric {@code id} (its index
 * within the graph it belongs to) and a human-readable {@code label}.
 *
 * @param id    zero-based index of the vertex within its graph
 * @param label display label (defaults to the index as text)
 */
public record NodeView(int id, String label) {
}
