package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;

/**
 * The de-fusion ("all-clear" / re-entry) sub-tuning of a {@link DetectorConfig}. The all-clear is a
 * <strong>recovery gauge</strong> — the trailing fraction of a window the density series has spent back
 * in the calm band ({@code density ≤ L_band}, with {@code L_band = μ_D + bandC·σ_D}) — and a binary fire
 * when that gauge crosses {@link #theta()}, gated by a was-recently-fused latch and a full-window
 * confirmation (see {@link RecoveryGauge}, {@link CusumChangeDetector}, and
 * {@code initiative-s-defusion-gauge-spec.md}). This supersedes the earlier density-lower-arm CUSUM,
 * which was falsified on real recovery data (it detected departure <em>below</em> the calm baseline, not
 * the fused→calm return); the gauge is the validated primitive.
 *
 * <p><strong>Firing is disabled by default</strong> ({@link #disabled()}): the gauge is always computed
 * and emitted (informational), but the binary all-clear stays off until a market is validated. When
 * disabled, the entire de-fusion fire branch of the detector is inert and the upper/fusion fire behaviour
 * is byte-for-byte unchanged.</p>
 *
 * @param bandC             the calm-band width: {@code L_band = μ_D + bandC·σ_D} ({@code >= 0})
 * @param theta             the all-clear gauge threshold — the in-band fraction that opens the alert,
 *                          in {@code [0, 1]}
 * @param gaugeWindowSamples the gauge window length {@code N_g} in samples ({@code >= 1}); the caller
 *                          derives it from the time window {@code W_g} and the sampling cadence (e.g.
 *                          48 h at a 30-min cadence is {@code 96}), keeping the library cadence-agnostic
 * @param enabled           whether the binary all-clear fire is active (default {@code false})
 */
public record DefusionConfig(double bandC, double theta, int gaugeWindowSamples, boolean enabled) {

    /**
     * Validates the de-fusion tuning (mirrors {@link DetectorConfig}'s checks) so a misconfiguration
     * fails loudly at construction.
     */
    public DefusionConfig {
        if (!(bandC >= 0)) {
            throw new InvalidInputException("bandC must be >= 0; got [" + bandC + "]");
        }
        if (!(theta >= 0 && theta <= 1)) {
            throw new InvalidInputException("theta must be in [0,1]; got [" + theta + "]");
        }
        if (gaugeWindowSamples < 1) {
            throw new InvalidInputException("gaugeWindowSamples must be >= 1; got [" + gaugeWindowSamples + "]");
        }
    }

    /**
     * The default disabled de-fusion tuning ({@code bandC=0.75, theta=0.80, gaugeWindowSamples=96,
     * enabled=false}). The constants are the settled crypto gauge (a 48 h window at a 30-min cadence;
     * θ=0.80 fires genuine recoveries and suppresses transient re-fusing dips, see the gauge spec §2.2);
     * the gauge is still computed while disabled, only the binary fire is off.
     *
     * @return a firing-disabled de-fusion configuration
     */
    public static DefusionConfig disabled() {
        return new DefusionConfig(0.75, 0.80, 96, false);
    }
}
