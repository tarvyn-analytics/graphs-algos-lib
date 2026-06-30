package ch.tarvynanalytics.graphs.algos.model;

/**
 * The direction of a {@link ChangeSignal} fire — which structural transition opened the alert.
 * A {@link #NONE} signal did not fire.
 *
 * <p>{@link #FUSION} is the structure-tightening exit alert (the upper-arm CUSUM on the change
 * metric, the v1 default). {@link #DEFUSION} is the structure-loosening "all-clear" / re-entry
 * alert. The de-fusion detector is a lower-arm CUSUM on the <em>density</em> series gated by a
 * was-recently-fused latch (see {@code initiative-s-defusion-rule-spec.md}); it is disabled by
 * default and so {@code DEFUSION} is emitted only when de-fusion firing is explicitly configured.</p>
 */
public enum FireDirection {

    /** This transition did not open an alert. */
    NONE,

    /** Structure tightening — the fusion / exit alert (upper-arm CUSUM on the change metric). */
    FUSION,

    /** Structure loosening back — the de-fusion / re-entry "all-clear" alert. */
    DEFUSION
}
