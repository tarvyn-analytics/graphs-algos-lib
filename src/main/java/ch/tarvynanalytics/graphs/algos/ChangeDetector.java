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
     * Ingests the next correlation matrix tagged with a caller-supplied <strong>window id</strong>,
     * for one-fire-per-window false-alarm measurement over a long multi-session calm span (spec
     * §2.4 {@code reset_ids}). Behaves exactly like {@link #onMatrix(double[][])} except that when
     * {@code windowId} differs from the previous windowed call's id, the detector first
     * <strong>re-arms</strong>: the one-fire-per-window debounce is cleared and the firing CUSUM arm
     * is reset to zero ({@code already_fired ← false}, {@code S+ ← 0}). Unlike
     * {@link #onSessionBoundary()}, the re-arm does <strong>not</strong> drop the previous matrix (the
     * transition into the new window is still scored) and does <strong>not</strong> reset the opposite
     * arm — it is a debounce re-arm, not a state reset.
     *
     * <p>Without it, the global "fires once ever" debounce undercounts the false-alarm rate by orders
     * of magnitude on a long calm series; with it, each window gets one independent chance to fire and
     * the caller sums the emitted {@link ChangeSignal#fired()} flags per window. Mixing windowed and
     * non-windowed {@code onMatrix} calls in one stream is not supported — pick one mode per stream
     * (the no-id overload is the single-window form, equivalent to a constant id).</p>
     *
     * @param correlation the next square correlation matrix, as in {@link #onMatrix(double[][])}
     * @param windowId    the window this step belongs to; a change from the previous windowed call's
     *                    id re-arms the debounce and the firing arm before this step is scored
     * @return the change signal for this transition, or {@code null} for the very first matrix
     * @throws IllegalArgumentException                                             if {@code correlation} is {@code null}
     * @throws ch.tarvynanalytics.graphs.algos.exception.InvalidInputException if {@code correlation}
     *         is not square or its order differs from the detector's
     */
    ChangeSignal onMatrix(double[][] correlation, long windowId);

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
