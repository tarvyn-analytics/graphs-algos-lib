package ch.tarvynanalytics.graphs.algos;

/**
 * Two-sided tabular CUSUM accumulator, ported verbatim from the spike's {@code replay_alert}:
 * with {@code z_t = (change_t - mu) / sigma},
 * <pre>
 *   S+_t = max(0, S+_{t-1} + z_t - k)     (fusion / upper arm)
 *   S-_t = max(0, S-_{t-1} - z_t - k)     (de-fusion / lower arm)
 * </pre>
 * Package-private state holder behind {@link CusumChangeDetector}; both arms are always updated.
 */
final class CusumAccumulator {

    private final double mu;
    private final double sigma;
    private final double k;

    private double sPlus;
    private double sMinus;

    CusumAccumulator(double mu, double sigma, double k) {
        this.mu = mu;
        this.sigma = sigma;
        this.k = k;
    }

    /**
     * Advances both arms by one finite change value. The caller guarantees {@code change} is
     * finite (a NaN-change gap carries the accumulators unchanged and must not call this).
     *
     * @param change the finite {@code weighted_change} for this transition
     */
    void step(double change) {
        double z = (change - mu) / sigma;
        sPlus = Math.max(0.0, sPlus + z - k);
        sMinus = Math.max(0.0, sMinus - z - k);
    }

    double sPlus() {
        return sPlus;
    }

    double sMinus() {
        return sMinus;
    }

    /** Resets the firing arm to zero after a fire (the spike's debounce). */
    void resetArm(FireArm arm) {
        if (arm == FireArm.UPPER) {
            sPlus = 0.0;
        } else {
            sMinus = 0.0;
        }
    }

    /** Resets both arms to zero (a session boundary). */
    void reset() {
        sPlus = 0.0;
        sMinus = 0.0;
    }
}
