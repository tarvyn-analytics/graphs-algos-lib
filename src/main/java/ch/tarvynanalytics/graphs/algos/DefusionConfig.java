package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;

/**
 * The de-fusion ("all-clear" / re-entry) sub-tuning of a {@link DetectorConfig}. De-fusion is a
 * lower-arm CUSUM on the <em>density</em> series (structure loosening back), gated by a
 * was-recently-fused latch and an inverted low-density confirmation gate — a different series and
 * gate than the fusion arm, so it cannot be expressed by {@link FireArm} alone (see
 * {@code initiative-s-defusion-rule-spec.md} §1–§3 for why the lower arm on the change metric is
 * structurally dead).
 *
 * <p><strong>Disabled by default</strong> ({@link #disabled()}): the de-fusion path is unvalidatable
 * on data without a post-event recovery phase, so it stays off until a market with a real recovery
 * phase justifies enabling it. When disabled, the entire de-fusion branch of the detector is inert
 * and the upper/fusion fire behaviour is byte-for-byte unchanged.</p>
 *
 * @param k              de-fusion CUSUM reference value, in calm-density-sigma units ({@code >= 0})
 * @param h              de-fusion CUSUM decision interval, in calm-density-sigma units ({@code > 0})
 * @param lowLevelPctile a <em>low</em> percentile of the calm density series for the inverted
 *                       confirmation gate {@code L_low} (the all-clear must return density to a low
 *                       calm level), in {@code [0, 100]}
 * @param enabled        whether de-fusion firing is active (default {@code false})
 */
public record DefusionConfig(double k, double h, double lowLevelPctile, boolean enabled) {

    /**
     * Validates the de-fusion tuning (mirrors {@link DetectorConfig}'s checks) so a misconfiguration
     * fails loudly at construction.
     */
    public DefusionConfig {
        if (!(k >= 0)) {
            throw new InvalidInputException("kDefusion must be >= 0; got [" + k + "]");
        }
        if (!(h > 0)) {
            throw new InvalidInputException("hDefusion must be > 0; got [" + h + "]");
        }
        if (!(lowLevelPctile >= 0 && lowLevelPctile <= 100)) {
            throw new InvalidInputException("lowLevelPctile must be in [0,100]; got [" + lowLevelPctile + "]");
        }
    }

    /**
     * The default disabled de-fusion tuning ({@code k=1.0, h=5.0, lowLevelPctile=25, enabled=false}).
     * The constants are inert while {@code enabled} is {@code false}; they are sensible starting
     * points for when de-fusion is later enabled and re-validated.
     *
     * @return a disabled de-fusion configuration
     */
    public static DefusionConfig disabled() {
        return new DefusionConfig(1.0, 5.0, 25.0, false);
    }
}
