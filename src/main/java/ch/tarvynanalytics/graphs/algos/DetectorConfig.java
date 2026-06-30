package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;

/**
 * The per-asset / per-regime tuning of the {@link ChangeDetector}. Every threshold
 * is configuration, never a literal baked into the detector body, so a new market is
 * wired rather than coded (Initiative-S S3, build-design §3.3/§6). Two named factories
 * carry the settled defaults: {@link #crypto()} (the recalibrated crypto constants) and
 * {@link #equity()} (the original {@code replay_alert} constants).
 *
 * <p>The calm-block geometry (e.g. crypto's "45 days ending 21 days pre-event") is
 * <strong>not</strong> part of this config — it is <em>which calm slice the caller passes
 * to {@link ChangeDetectors#calibrate}</em>; the detector is calendar-agnostic and never
 * computes calendar offsets. Likewise the window length {@code W} is owned by S1 (it is the
 * window of the matrices S1 emits); S3 does not re-window.</p>
 *
 * @param k             CUSUM reference value, in units of calm sigma (must be {@code >= 0})
 * @param h             CUSUM decision interval, in units of calm sigma (must be {@code > 0})
 * @param levelPctile   percentile of the calm density series for the absolute level gate {@code L},
 *                      in {@code [0, 100]}
 * @param edgeThreshold edge magnitude threshold {@code τ}; an edge exists where {@code |r| > τ}
 * @param epsilonSigma  floor applied to the calm sigma when the calm block is degenerate/constant
 *                      (must be {@code > 0}); prevents divide-by-zero and spurious firing
 * @param fireArm       which CUSUM arm opens the primary alert in this configuration (v1: {@link FireArm#UPPER})
 * @param defusion      the de-fusion ("all-clear" / re-entry) sub-tuning (disabled by default; see
 *                      {@link DefusionConfig})
 */
public record DetectorConfig(double k, double h, double levelPctile,
                             double edgeThreshold, double epsilonSigma, FireArm fireArm,
                             DefusionConfig defusion) {

    /**
     * Validates the tuning constants; rejects non-finite or out-of-range values so a
     * misconfiguration fails loudly at construction rather than silently mis-firing.
     */
    public DetectorConfig {
        if (!(k >= 0)) {
            throw new InvalidInputException("k must be >= 0; got [" + k + "]");
        }
        if (!(h > 0)) {
            throw new InvalidInputException("h must be > 0; got [" + h + "]");
        }
        if (!(levelPctile >= 0 && levelPctile <= 100)) {
            throw new InvalidInputException("levelPctile must be in [0,100]; got [" + levelPctile + "]");
        }
        if (!(epsilonSigma > 0)) {
            throw new InvalidInputException("epsilonSigma must be > 0; got [" + epsilonSigma + "]");
        }
        if (fireArm == null) {
            throw new IllegalArgumentException("fireArm must not be null");
        }
        if (defusion == null) {
            throw new IllegalArgumentException("defusion config must not be null");
        }
    }

    /**
     * Backward-compatible constructor with de-fusion {@link DefusionConfig#disabled() disabled} — the
     * fusion-only configuration used everywhere before de-fusion existed.
     *
     * @param k             CUSUM reference value
     * @param h             CUSUM decision interval
     * @param levelPctile   calm-density percentile for the level gate
     * @param edgeThreshold edge magnitude threshold {@code τ}
     * @param epsilonSigma  degenerate-calm sigma floor
     * @param fireArm       which arm opens the primary alert
     */
    public DetectorConfig(double k, double h, double levelPctile,
                          double edgeThreshold, double epsilonSigma, FireArm fireArm) {
        this(k, h, levelPctile, edgeThreshold, epsilonSigma, fireArm, DefusionConfig.disabled());
    }

    /**
     * The settled crypto constants (crypto-n8-verdict / the spike recalibration):
     * {@code k=1.5, h=8.0, levelPctile=99.0, τ=0.5, epsilonSigma=1.0}, firing on the upper arm.
     * De-fusion firing is disabled (see {@link DefusionConfig}).
     *
     * @return a crypto-tuned configuration
     */
    public static DetectorConfig crypto() {
        return new DetectorConfig(1.5, 8.0, 99.0, 0.5, 1.0, FireArm.UPPER,
                DefusionConfig.disabled());
    }

    /**
     * The original {@code replay_alert} equity constants:
     * {@code k=1.0, h=5.0, levelPctile=90.0, τ=0.5, epsilonSigma=1.0}, firing on the upper arm.
     * De-fusion firing is disabled (see {@link DefusionConfig}).
     *
     * @return an equity-tuned configuration
     */
    public static DetectorConfig equity() {
        return new DetectorConfig(1.0, 5.0, 90.0, 0.5, 1.0, FireArm.UPPER,
                DefusionConfig.disabled());
    }
}
