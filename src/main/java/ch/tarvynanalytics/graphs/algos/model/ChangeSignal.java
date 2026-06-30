package ch.tarvynanalytics.graphs.algos.model;

/**
 * The structural-change signal emitted per consecutive matrix transition
 * {@code C_{t-1} -> C_t} by the Initiative-S S3 detector: the {@link ChangeMetrics}
 * for the transition plus the CUSUM state and the fire decision.
 *
 * <p><strong>Three CUSUM arms are emitted.</strong> {@link #sPlus()} is the fusion
 * (structure-tightening) arm that fires the v1 exit alert. {@link #sMinus()} is the lower arm on the
 * change metric — kept for continuity, but <em>informational only</em>: it is structurally unable to
 * trigger on real data (see {@code initiative-s-defusion-rule-spec.md} §1), so it is not the re-entry
 * signal. {@link #sDensityMinus()} is the de-fusion arm — a lower-arm CUSUM on the <em>density</em>
 * series — which <em>is</em> the correct "all-clear" primitive (disabled by default).</p>
 *
 * <p>{@link #fireDirection()} says which transition, if any, opened an alert: {@link FireDirection#FUSION}
 * (upper arm), {@link FireDirection#DEFUSION} (de-fusion / re-entry), or {@link FireDirection#NONE}.
 * {@link #fired()} is the convenience predicate {@code fireDirection != NONE}.</p>
 *
 * <p>"No fire" is <em>censored</em>, never a silent zero: a run that never fires simply emits
 * no firing row (see {@code ChangeDetector.firstFire}).</p>
 *
 * @param seq           monotonic emission sequence number, starting at 0 for the first transition
 *                      (the very first matrix produces no signal)
 * @param metrics       the change metrics for this transition (primary {@code weighted_change} plus
 *                      the secondary density / edge-XOR / cluster features)
 * @param sPlus         the CUSUM upper-arm accumulator at this transition (fusion)
 * @param sMinus        the CUSUM lower-arm accumulator on the change metric (informational; see above)
 * @param sDensityMinus the de-fusion lower-arm accumulator on the density series at this transition
 * @param fireDirection which transition, if any, opened an alert ({@link FireDirection#NONE} if none)
 */
public record ChangeSignal(long seq, ChangeMetrics metrics, double sPlus, double sMinus,
                           double sDensityMinus, FireDirection fireDirection) {

    /**
     * Validates that the metrics and fire-direction components are present.
     */
    public ChangeSignal {
        if (metrics == null) {
            throw new IllegalArgumentException("metrics must not be null");
        }
        if (fireDirection == null) {
            throw new IllegalArgumentException("fireDirection must not be null");
        }
    }

    /**
     * Whether this transition opened an alert (on either arm).
     *
     * @return {@code true} iff {@link #fireDirection()} is not {@link FireDirection#NONE}
     */
    public boolean fired() {
        return fireDirection != FireDirection.NONE;
    }
}
