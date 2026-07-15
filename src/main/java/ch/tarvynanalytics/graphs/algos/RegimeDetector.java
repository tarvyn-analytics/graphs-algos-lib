package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.RegimeSignal;

/**
 * Streaming <strong>regime-state</strong> detector: it consumes a stream of
 * scalar level samples (in practice the smoothed correlation density over one timescale) and emits,
 * per sample, a {@link RegimeSignal} carrying the current regime and any regime edge crossed. This
 * is the continuous-tape backbone — a level+hysteresis (Schmitt)
 * read that self-segments calm from fused where the change-CUSUM ({@link ChangeDetector}) could not.
 *
 * <p>A {@code RegimeDetector} is <strong>single-writer</strong>: it is not safe for concurrent
 * {@link #step} calls, exactly like the {@link ChangeDetector}. Obtain one via
 * {@link RegimeDetectors#create(RegimeConfig)}.</p>
 *
 * <p><strong>One detector per timescale.</strong> The caller owns the level series' aggregation and
 * smoothing (which frequency, how many bars per sample, what smoothing window) — the detector is
 * cadence-agnostic and only counts samples, so {@code confirmBars} is a sample count, never a
 * calendar span.</p>
 */
public interface RegimeDetector {

    /**
     * Advances the detector by one level sample and returns the regime signal for it.
     *
     * @param level the next scalar level (already smoothed by the caller). A non-finite value
     *              ({@link Double#NaN}/{@code ±Infinity}) is an expected, handled data gap: the
     *              regime is carried unchanged and neither persistence run advances or resets, so a
     *              gap can neither confirm nor break a pending crossing.
     * @return the regime signal for this sample (never {@code null}; every sample yields a signal —
     *         a single level is a complete observation, unlike a matrix transition)
     */
    RegimeSignal step(double level);

    /**
     * Resets the detector to a fresh {@link ch.tarvynanalytics.graphs.algos.model.RegimeState#CALM}
     * start with both persistence runs zeroed and the sample index restarted — a caller-driven
     * session boundary, the regime analogue of {@link ChangeDetector#onSessionBoundary()}.
     */
    void reset();
}
