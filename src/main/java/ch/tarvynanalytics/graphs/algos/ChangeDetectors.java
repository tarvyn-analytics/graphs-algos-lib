package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;

import java.util.Arrays;

/**
 * Factory and calibration helper for the S3 {@link ChangeDetector} — the public
 * entry point for the streaming change-point detector (mirrors the analyzer/factory recipe of
 * the rest of the library).
 */
public final class ChangeDetectors {

    private ChangeDetectors() {
    }

    /**
     * Creates a streaming change-point detector for a fixed variable set.
     *
     * @param order       the order {@code m} of every correlation matrix in the stream (the variable
     *                    set is stable across the stream)
     * @param config      the per-asset tuning (see {@link DetectorConfig#crypto()} / {@link DetectorConfig#equity()})
     * @param calibration the calm-window calibration from {@link #calibrate}
     * @return a new, single-writer {@link ChangeDetector}
     * @throws IllegalArgumentException if {@code config} or {@code calibration} is {@code null}
     * @throws InvalidInputException    if {@code order} is negative
     */
    public static ChangeDetector create(int order, DetectorConfig config, Calibration calibration) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (calibration == null) {
            throw new IllegalArgumentException("calibration must not be null");
        }
        return new CusumChangeDetector(order, config, calibration);
    }

    /**
     * Computes the calm-window {@link Calibration} from two parallel calm slices: the calm
     * {@code weighted_change} series (for the CUSUM mean/sigma) and the calm density series (for
     * the absolute level gate {@code L}). The caller is responsible for the slices being leak-free
     * (disjoint from and preceding the detection span — walk-forward discipline). Non-finite
     * entries are dropped before the statistics; a constant calm change series floors sigma to
     * {@code config.epsilonSigma()}.
     *
     * @param calmWeightedChange the calm {@code weighted_change} values
     * @param calmDensityLevel   the calm density values (for the level gate)
     * @param config             the tuning supplying the level percentile and the sigma floor
     * @return the calibration to feed into {@link #create}
     * @throws IllegalArgumentException if any argument is {@code null}
     * @throws InvalidInputException    if either slice has no finite values
     */
    public static Calibration calibrate(double[] calmWeightedChange, double[] calmDensityLevel,
                                        DetectorConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (calmWeightedChange == null || calmDensityLevel == null) {
            throw new IllegalArgumentException("calm slices must not be null");
        }

        double[] changes = finiteOnly(calmWeightedChange);
        if (changes.length == 0) {
            throw new InvalidInputException("calm slice has no finite change values");
        }
        double mu = mean(changes);
        // A constant calm block has true sigma 0 (statistics.pstdev is exact for it); naive
        // float summation can instead yield a tiny non-zero sigma when the mean is not bit-exact
        // (e.g. mean of [0.1,0.1,0.1]), which would blow up z and defeat the degenerate guard.
        // Detect the constant case directly so the epsilonSigma floor fires as the spike's does.
        double sigma = isConstant(changes) ? 0.0 : populationStdDev(changes, mu);
        if (sigma == 0.0) {
            sigma = config.epsilonSigma();
        }

        double[] densities = finiteOnly(calmDensityLevel);
        if (densities.length == 0) {
            throw new InvalidInputException("calm slice has no finite density values");
        }
        double level = nearestRankPercentile(densities, config.levelPctile());

        // De-fusion recovery gauge: mean/sigma of the calm DENSITY series (a different series than the
        // change CUSUM), which form the calm band L_band = muDensity + bandC*sigmaDensity. Always computed
        // so enabling de-fusion is a config flip, not a re-calibration; harmless when firing is disabled
        // (the gauge is still emitted, but no DEFUSION can fire).
        double muDensity = mean(densities);
        double sigmaDensity = isConstant(densities) ? 0.0 : populationStdDev(densities, muDensity);
        if (sigmaDensity == 0.0) {
            sigmaDensity = config.epsilonSigma();
        }

        return new Calibration(mu, sigma, level, muDensity, sigmaDensity);
    }

    private static double[] finiteOnly(double[] values) {
        int n = 0;
        for (double v : values) {
            if (Double.isFinite(v)) {
                n++;
            }
        }
        double[] out = new double[n];
        int i = 0;
        for (double v : values) {
            if (Double.isFinite(v)) {
                out[i++] = v;
            }
        }
        return out;
    }

    /** True when every value equals the first (a degenerate, constant calm block). */
    private static boolean isConstant(double[] values) {
        for (int i = 1; i < values.length; i++) {
            if (values[i] != values[0]) {
                return false;
            }
        }
        return true;
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** Population standard deviation (divisor n), matching {@code statistics.pstdev}. */
    private static double populationStdDev(double[] values, double mu) {
        double sumSq = 0.0;
        for (double v : values) {
            double d = v - mu;
            sumSq += d * d;
        }
        return Math.sqrt(sumSq / values.length);
    }

    /**
     * Nearest-rank percentile (no interpolation), matching the spike's {@code _percentile}:
     * sort ascending, {@code rank = clamp(ceil(pct/100 * n), 1, n)}, return {@code sorted[rank-1]}.
     */
    static double nearestRankPercentile(double[] values, double pct) {
        int n = values.length;
        if (n == 0) {
            return Double.NaN;   // percentile of an empty series is undefined (callers guard against this)
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(pct / 100.0 * n);
        if (rank < 1) {
            rank = 1;
        }
        if (rank > n) {
            rank = n;
        }
        return sorted[rank - 1];
    }
}
