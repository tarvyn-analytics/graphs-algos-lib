package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;

/**
 * The tuning of the level+hysteresis {@code RegimeStateDetector}:
 * the two Schmitt-trigger marks and the persistence run. Every value is configuration — a new
 * market or universe is <em>wired</em> (its own thresholds), not coded (family invariant "thresholds
 * are config, not code"); the detector itself is asset- and cadence-agnostic and never names a
 * market or a calendar.
 *
 * <p>The trigger runs on a scalar level series (in practice the smoothed correlation density, but
 * the primitive does not know that). It opens a regime when the level holds at/above {@code hi} for
 * {@code confirmBars} consecutive samples and closes it when the level holds at/below {@code lo} for
 * {@code confirmBars}. Two distinct marks ({@code lo < hi}) is what stops the flip-flop metronome
 * — once fused the regime is not declared calm again until the level falls well below where it rose.</p>
 *
 * @param hi          the high mark; the level must reach/exceed it to open a regime
 * @param lo          the low mark; the level must reach/fall below it to close a regime (must be
 *                    {@code < hi} — a hysteresis trigger requires two distinct marks)
 * @param confirmBars the persistence run in samples: how many consecutive samples past a mark
 *                    confirm the crossing (must be {@code >= 1})
 */
public record RegimeConfig(double hi, double lo, int confirmBars) {

    /**
     * Validates the marks and persistence; rejects non-finite, mis-ordered or non-positive values so
     * a misconfiguration fails loudly at construction rather than silently mis-segmenting.
     */
    public RegimeConfig {
        if (!Double.isFinite(hi)) {
            throw new InvalidInputException("hi must be finite; got [" + hi + "]");
        }
        if (!Double.isFinite(lo)) {
            throw new InvalidInputException("lo must be finite; got [" + lo + "]");
        }
        if (!(lo < hi)) {
            throw new InvalidInputException("lo must be < hi; got lo [" + lo + "], hi [" + hi + "]");
        }
        if (confirmBars < 1) {
            throw new InvalidInputException("confirmBars must be >= 1; got [" + confirmBars + "]");
        }
    }

    /**
     * The settled crypto regime marks ({@code h2r1_regime_model.py}, validated on the continuous
     * continuous 17-symbol tape: 12 fused-regime cycles, calm FA 0.008/day, covid recovery −4 d and
     * ftx +4 d vs the walk-forward references): {@code hi=0.85, lo=0.45, confirmBars=3}. The caller
     * feeds a daily-aggregated, 3-day-median-smoothed density so {@code confirmBars=3} ≈ 3 days.
     *
     * @return a crypto-tuned regime configuration
     */
    public static RegimeConfig crypto() {
        return new RegimeConfig(0.85, 0.45, 3);
    }
}
