package ch.tarvynanalytics.graphs.algos;

/**
 * Two-sided tabular CUSUM accumulator, ported verbatim from the spike's {@code replay_alert},
 * plus a third arm for de-fusion. On the {@code weighted_change} series, with
 * {@code z_t = (change_t - mu) / sigma}:
 * <pre>
 *   S+_t = max(0, S+_{t-1} + z_t - k)     (fusion / upper arm)
 *   S-_t = max(0, S-_{t-1} - z_t - k)     (lower arm on the change metric — informational only;
 *                                          structurally dead on real data, see defusion spec §1)
 * </pre>
 * and, independently, on the {@code density_level} series with {@code zd_t = (density_t - muD) / sigmaD}:
 * <pre>
 *   Sd-_t = max(0, Sd-_{t-1} - zd_t - kDefusion)   (de-fusion: persistent structural loosening)
 * </pre>
 * Package-private state holder behind {@link CusumChangeDetector}. The {@code S+/S-} arms are always
 * updated; the density arm is updated only when the detector's de-fusion path is enabled.
 */
final class CusumAccumulator {

    private final double mu;
    private final double sigma;
    private final double k;
    private final double muDensity;
    private final double sigmaDensity;
    private final double kDefusion;

    private double sPlus;
    private double sMinus;
    private double sDensityMinus;

    CusumAccumulator(double mu, double sigma, double k,
                     double muDensity, double sigmaDensity, double kDefusion) {
        this.mu = mu;
        this.sigma = sigma;
        this.k = k;
        this.muDensity = muDensity;
        this.sigmaDensity = sigmaDensity;
        this.kDefusion = kDefusion;
    }

    /**
     * Advances both change-metric arms by one finite change value. The caller guarantees
     * {@code change} is finite (a NaN-change gap carries the accumulators unchanged and must not
     * call this).
     *
     * @param change the finite {@code weighted_change} for this transition
     */
    void step(double change) {
        double z = (change - mu) / sigma;
        sPlus = Math.max(0.0, sPlus + z - k);
        sMinus = Math.max(0.0, sMinus - z - k);
    }

    /**
     * Advances the de-fusion (density lower) arm by one density value. Only called when de-fusion is
     * enabled; the caller guarantees {@code density} is finite (it always is for a valid {@code C_t},
     * even on a NaN-change gap).
     *
     * @param density the {@code density_level} for this transition
     */
    void stepDensity(double density) {
        double zd = (density - muDensity) / sigmaDensity;
        sDensityMinus = Math.max(0.0, sDensityMinus - zd - kDefusion);
    }

    double sPlus() {
        return sPlus;
    }

    double sMinus() {
        return sMinus;
    }

    double sDensityMinus() {
        return sDensityMinus;
    }

    /** Resets the firing arm to zero after a fire (the spike's debounce). */
    void resetArm(FireArm arm) {
        if (arm == FireArm.UPPER) {
            sPlus = 0.0;
        } else {
            sMinus = 0.0;
        }
    }

    /** Resets the de-fusion (density lower) arm to zero after a de-fusion fire. */
    void resetDensityArm() {
        sDensityMinus = 0.0;
    }

    /** Resets all arms to zero (a session boundary). */
    void reset() {
        sPlus = 0.0;
        sMinus = 0.0;
        sDensityMinus = 0.0;
    }
}
