package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.ChangeSignal;

import java.util.Optional;

/**
 * Streaming Initiative-S S3 change-point detector: it consumes a stream of consecutive
 * correlation matrices over one timescale and emits, per transition, a {@link ChangeSignal}
 * carrying the change metrics and the two-sided CUSUM state. This is the library's one
 * stateful object on the S3 side (mirroring S1's streaming engine in corrcalc-lib); the
 * one-shot analyzers — including {@link ChangeMetricsAnalyzer} — remain stateless and
 * thread-safe. A {@code ChangeDetector} is <strong>single-writer</strong>: it is not safe for
 * concurrent {@link #onMatrix} calls.
 *
 * <p><strong>One detector per timescale.</strong> A separate instance runs on each timescale's
 * matrix stream (macro daily, micro intraday); a detector never sees a blended-frequency
 * series. The only cross-timescale combination is the upstream matrix blend, applied before
 * the matrix reaches the detector.</p>
 *
 * <p>Obtain one via {@link ChangeDetectors#create(int, DetectorConfig, Calibration)}.</p>
 */
public interface ChangeDetector {

    /**
     * Ingests the next correlation matrix in the stream and returns the change signal for the
     * transition from the previous matrix to this one.
     *
     * @param correlation the next square correlation matrix, of the order the detector was
     *                    created with; {@code NaN}/{@code ±Infinity} cells are an expected,
     *                    handled input (a zero-variance series upstream), not rejected
     * @return the change signal for this transition, or {@code null} for the very first matrix
     *         (no previous matrix yet, so no transition)
     * @throws IllegalArgumentException                                             if {@code correlation} is {@code null}
     * @throws ch.tarvynanalytics.graphs.algos.exception.InvalidInputException if {@code correlation}
     *         is not square or its order differs from the detector's
     */
    ChangeSignal onMatrix(double[][] correlation);

    /**
     * Signals a session boundary (a caller-driven reset, e.g. a calendar session change): the
     * previous matrix is dropped (the next matrix produces no transition), both CUSUM arms are
     * reset to zero, and the one-fire-per-window debounce is re-armed. This is distinct from a
     * data-driven NaN-change gap, which carries the accumulators unchanged.
     */
    void onSessionBoundary();

    /**
     * The first transition at which this detector fired, if any.
     *
     * @return the first fired {@link ChangeSignal}, or {@link Optional#empty()} if the detector
     *         has not fired (a censored miss — never reported as a zero)
     */
    Optional<ChangeSignal> firstFire();
}
