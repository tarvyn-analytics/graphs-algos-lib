package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;

/**
 * The calm-window calibration a {@link ChangeDetector} runs against: the mean and
 * (population, already sigma-floored) standard deviation of the calm {@code weighted_change}
 * series, plus the absolute level gate {@code L} taken from the calm density series. Produced
 * by {@link ChangeDetectors#calibrate} on a leak-free calm slice that the caller supplies
 * (walk-forward discipline — the calm slice must precede and be disjoint from the detection span).
 *
 * @param mu           mean of the calm {@code weighted_change} series
 * @param sigma        population standard deviation of the calm {@code weighted_change} series, already
 *                     floored to {@code epsilonSigma} when degenerate (must be {@code > 0})
 * @param level        the absolute level gate {@code L} = a percentile of the calm density series
 * @param muDensity    mean of the calm density series (the de-fusion lower arm's mean); {@link Double#NaN}
 *                     when de-fusion is not calibrated (the inert default), which keeps de-fusion silent
 * @param sigmaDensity population standard deviation of the calm density series, floored when degenerate;
 *                     {@link Double#NaN} when de-fusion is not calibrated
 * @param lowLevel     the inverted low-density confirmation gate {@code L_low} = a low percentile of the
 *                     calm density series; {@link Double#NaN} when de-fusion is not calibrated
 */
public record Calibration(double mu, double sigma, double level,
                          double muDensity, double sigmaDensity, double lowLevel) {

    /**
     * Validates that the change-arm sigma is strictly positive (it is always floored by
     * {@link ChangeDetectors#calibrate}); a non-positive sigma would divide by zero in the CUSUM
     * standardization. The density-arm fields are not validated here: {@link Double#NaN} is the
     * sanctioned inert marker (de-fusion uncalibrated), and {@code calibrate} floors {@code sigmaDensity}
     * when it does compute it.
     */
    public Calibration {
        if (!(sigma > 0)) {
            throw new InvalidInputException(
                    "sigma must be > 0 (already floored by calibrate); got [" + sigma + "]");
        }
    }

    /**
     * Fusion-only calibration with the de-fusion arm left uncalibrated ({@link Double#NaN}), so
     * de-fusion can never fire. Preserves every pre-de-fusion {@code new Calibration(mu, sigma, level)}
     * call site.
     *
     * @param mu    mean of the calm change series
     * @param sigma population std of the calm change series (already floored)
     * @param level the absolute level gate
     */
    public Calibration(double mu, double sigma, double level) {
        this(mu, sigma, level, Double.NaN, Double.NaN, Double.NaN);
    }
}
