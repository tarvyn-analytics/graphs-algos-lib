package ch.tarvynanalytics.graphs.algos;

/**
 * The de-fusion <strong>recovery gauge</strong>: a streaming, fixed-window
 * <em>in-band occupancy</em>. It answers "how much of the recent window has the structure been back in
 * the calm band?" — the trailing fraction of the last {@code N_g} samples whose density sat in the calm
 * band ({@code density ≤ L_band}). After a fusion alarm it runs <strong>0 → 1</strong> as the structure
 * heals (the dashboard's gradual-recovery track); a binary all-clear is a threshold crossing on it (see
 * {@link CusumChangeDetector}). See {@code initiative-s-defusion-gauge-spec.md} §1.
 *
 * <p>Anchoring is by {@link #reset()} (called by the detector at the most recent fusion fire and at a
 * session boundary): the window never reaches back before the anchor, so the gauge starts from the
 * fully-fused state and climbs only as density settles into the band. A ring buffer of the last
 * {@code N_g} in-band indicators gives O(1) {@link #push} and O({@code N_g}) state, with no allocation
 * per sample and no dependency. Package-private state holder behind {@link CusumChangeDetector};
 * single-writer like the detector that drives it.</p>
 */
final class RecoveryGauge {

    private final int windowSamples;
    private final boolean[] buffer;
    private long samplesSinceReset;
    private int inBandInWindow;

    /**
     * @param windowSamples the gauge window length {@code N_g} in samples ({@code >= 1}); the caller
     *                      derives it from the time window {@code W_g} and the sampling cadence
     */
    RecoveryGauge(int windowSamples) {
        this.windowSamples = windowSamples;
        this.buffer = new boolean[windowSamples];
    }

    /**
     * Records this transition's in-band indicator, sliding the trailing window forward by one.
     *
     * @param inBand whether this transition's density sat in the calm band ({@code density ≤ L_band})
     */
    void push(boolean inBand) {
        int slot = (int) (samplesSinceReset % windowSamples);
        if (samplesSinceReset >= windowSamples && buffer[slot]) {
            inBandInWindow--;   // the sample leaving the trailing window
        }
        buffer[slot] = inBand;
        if (inBand) {
            inBandInWindow++;
        }
        samplesSinceReset++;
    }

    /**
     * The current trailing in-band fraction in {@code [0, 1]}, or {@link Double#NaN} before the first
     * {@link #push} since the last {@link #reset} (no measurement yet).
     *
     * @return the recovery gauge value, or {@code NaN} when empty
     */
    double value() {
        if (samplesSinceReset == 0) {
            return Double.NaN;
        }
        long windowSize = Math.min(samplesSinceReset, windowSamples);
        return (double) inBandInWindow / windowSize;
    }

    /**
     * Whether a full window {@code N_g} of samples has accumulated since the anchor — the all-clear
     * confirmation guard (no fire until the gauge reflects a complete {@code W_g} of post-fusion
     * observation).
     *
     * @return {@code true} once {@code >= N_g} samples have been pushed since the last reset
     */
    boolean windowFull() {
        return samplesSinceReset >= windowSamples;
    }

    /** Re-anchors the gauge (a fusion fire or a session boundary): the next push starts a fresh window. */
    void reset() {
        samplesSinceReset = 0;
        inBandInWindow = 0;
    }
}
