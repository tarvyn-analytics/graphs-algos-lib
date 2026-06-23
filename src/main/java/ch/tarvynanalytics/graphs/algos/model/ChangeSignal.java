package ch.tarvynanalytics.graphs.algos.model;

/**
 * The structural-change signal emitted per consecutive matrix transition
 * {@code C_{t-1} -> C_t} by the Initiative-S S3 detector: the {@link ChangeMetrics}
 * for the transition plus the two-sided CUSUM state and the fire decision.
 *
 * <p><strong>Both CUSUM arms are emitted from day one.</strong> {@link #sPlus()} is the
 * fusion (structure-tightening) arm that fires the v1 exit alert; {@link #sMinus()} is the
 * de-fusion (structure-loosening) arm, computed and surfaced so re-entry detection is a later
 * configuration flip rather than a redesign. {@link #fired()} is the AND of the absolute
 * level gate and the (configured) firing arm crossing its decision interval, with a
 * one-fire-per-window debounce; a NaN-change "gap" never fires.</p>
 *
 * <p>"No fire" is <em>censored</em>, never a silent zero: a run that never fires simply emits
 * no {@code fired=true} row (see {@code ChangeDetector.firstFire}).</p>
 *
 * @param seq     monotonic emission sequence number, starting at 0 for the first transition
 *                (the very first matrix produces no signal)
 * @param metrics the change metrics for this transition (primary {@code weighted_change} plus
 *                the secondary density / edge-XOR / cluster features)
 * @param sPlus   the CUSUM upper-arm accumulator at this transition (fusion)
 * @param sMinus  the CUSUM lower-arm accumulator at this transition (de-fusion / re-entry)
 * @param fired   whether this transition opened an alert on the configured firing arm
 */
public record ChangeSignal(long seq, ChangeMetrics metrics, double sPlus, double sMinus, boolean fired) {

    /**
     * Validates that the metrics component is present.
     */
    public ChangeSignal {
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
    }
}
