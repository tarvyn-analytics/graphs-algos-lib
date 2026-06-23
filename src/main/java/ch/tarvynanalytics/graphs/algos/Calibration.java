package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;

/**
 * The calm-window calibration a {@link ChangeDetector} runs against: the mean and
 * (population, already sigma-floored) standard deviation of the calm {@code weighted_change}
 * series, plus the absolute level gate {@code L} taken from the calm density series. Produced
 * by {@link ChangeDetectors#calibrate} on a leak-free calm slice that the caller supplies
 * (walk-forward discipline — the calm slice must precede and be disjoint from the detection span).
 *
 * @param mu     mean of the calm {@code weighted_change} series
 * @param sigma  population standard deviation of the calm {@code weighted_change} series, already
 *               floored to {@code epsilonSigma} when degenerate (must be {@code > 0})
 * @param level  the absolute level gate {@code L} = a percentile of the calm density series
 */
public record Calibration(double mu, double sigma, double level) {

    /**
     * Validates that sigma is strictly positive (it is always floored by
     * {@link ChangeDetectors#calibrate}); a non-positive sigma would divide by zero in the
     * CUSUM standardization.
     */
    public Calibration {
        if (!(sigma > 0)) {
            throw new InvalidInputException(
                    "sigma must be > 0 (already floored by calibrate); got [" + sigma + "]");
        }
    }
}
