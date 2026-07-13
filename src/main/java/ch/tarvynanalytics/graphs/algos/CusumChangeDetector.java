package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.ChangeMetrics;
import ch.tarvynanalytics.graphs.algos.model.ChangeSignal;
import ch.tarvynanalytics.graphs.algos.model.FireDirection;

import java.util.Optional;

/**
 * Package-private {@link ChangeDetector} implementation: the streaming change metric
 * ({@link ChangeMetricsEngine}) wired to the two-sided CUSUM ({@link CusumAccumulator}) and
 * the AND(level-gate, firing-arm) fire rule with one-fire-per-window debounce. See
 * {@link ChangeDetector} for the public contract and {@link ChangeDetectors} for construction.
 *
 * <p>The <strong>fusion</strong> (upper/primary arm) path is unchanged from v1. The
 * <strong>de-fusion</strong> ("all-clear" / re-entry) path is the {@link RecoveryGauge} — the trailing
 * in-band occupancy of the density series — which fires when the gauge crosses {@code θ} with a full
 * window, gated by a was-recently-fused latch. The gauge is <em>always computed and emitted</em>
 * (informational); the binary fire is <em>disabled by default</em> ({@code config.defusion().enabled()
 * == false}) and entirely inert in that case, so the pinned upper-arm behaviour cannot move.</p>
 */
final class CusumChangeDetector implements ChangeDetector {

    private final int order;
    private final DetectorConfig config;
    private final RecoveryGauge gauge;

    // Re-derived by recalibrate(..) when an adaptive caller opens a new calibration epoch.
    private Calibration calibration;
    private CusumAccumulator cusum;
    private double lBand;

    private double[][] previous;
    private long seq;
    private boolean alreadyFiredPrimary;
    private boolean alreadyFiredDefusion;
    private boolean wasFused;
    private ChangeSignal firstFire;
    private boolean hasWindowId;
    private long currentWindowId;

    CusumChangeDetector(int order, DetectorConfig config, Calibration calibration) {
        if (order < 0) {
            throw new InvalidInputException("matrix order must be >= 0; got [" + order + "]");
        }
        this.order = order;
        this.config = config;
        this.calibration = calibration;
        this.cusum = new CusumAccumulator(calibration.mu(), calibration.sigma(), config.k());
        this.gauge = new RecoveryGauge(config.defusion().gaugeWindowSamples());
        // The calm band L_band = μ_D + bandC·σ_D; NaN when de-fusion is uncalibrated (gauge stays NaN/inert).
        this.lBand = calibration.muDensity() + config.defusion().bandC() * calibration.sigmaDensity();
    }

    @Override
    public ChangeSignal onMatrix(double[][] correlation) {
        return ingest(correlation);
    }

    @Override
    public ChangeSignal onMatrix(double[][] correlation, long windowId) {
        if (hasWindowId && windowId != currentWindowId) {
            // The S3 change-metric spec's reset_ids rule: a new window re-arms the debounce and the firing arm only —
            // the previous matrix and the opposite arm are left intact (cf. onSessionBoundary()).
            alreadyFiredPrimary = false;
            cusum.resetArm(config.fireArm());
            if (config.defusion().enabled()) {
                // Re-arm the de-fusion debounce only; the gauge buffer (a trailing occupancy) is data
                // state, kept across the window boundary like the previous matrix and the opposite arm.
                alreadyFiredDefusion = false;
            }
        }
        hasWindowId = true;
        currentWindowId = windowId;
        return ingest(correlation);
    }

    private ChangeSignal ingest(double[][] correlation) {
        requireSquareOfOrder(correlation);
        if (previous == null) {
            previous = copy(correlation);
            return null;
        }

        ChangeMetrics metrics = ChangeMetricsEngine.compute(previous, correlation, config.edgeThreshold());
        double change = metrics.weightedChange();
        boolean gap = Double.isNaN(change);

        if (!gap) {
            cusum.step(change);   // NaN-change gap: carry the change accumulators unchanged
        }

        double density = metrics.densityLevel();
        boolean defusionEnabled = config.defusion().enabled();

        // Recovery gauge: the trailing in-band occupancy. Computed whenever de-fusion is calibrated
        // (L_band finite), independent of whether firing is enabled — it is the always-emitted recovery
        // track. The gauge advances even on a change gap: density is observable from C_t alone.
        boolean inBand = Double.isFinite(lBand) && Double.isFinite(density) && density <= lBand;
        if (Double.isFinite(lBand) && Double.isFinite(density)) {
            gauge.push(inBand);
        }
        double recoveryGauge = gauge.value();

        double sPlus = cusum.sPlus();
        double sMinus = cusum.sMinus();

        // Primary (fireArm-selected) fire: AND(level gate, firing arm > h), debounced. Unchanged.
        boolean gateLevel = density >= calibration.level();   // NaN density => false
        double firingArm = config.fireArm() == FireArm.UPPER ? sPlus : sMinus;
        boolean firePrimary = !alreadyFiredPrimary && !gap && gateLevel && firingArm > config.h();

        // De-fusion fire: only after a fusion fire (wasFused), once a full gauge window has filled and the
        // gauge crosses θ with the current sample itself back in the calm band. Inert unless enabled.
        boolean fireDefusion = defusionEnabled && wasFused && !alreadyFiredDefusion
                && gauge.windowFull() && recoveryGauge >= config.defusion().theta() && inBand;

        FireDirection direction = fireDirection(firePrimary, fireDefusion);
        ChangeSignal signal = new ChangeSignal(seq++, metrics, sPlus, sMinus, recoveryGauge, direction);

        if (firePrimary) {
            alreadyFiredPrimary = true;
            cusum.resetArm(config.fireArm());
            wasFused = config.fireArm() == FireArm.UPPER;   // a fusion fire arms the all-clear; a lower fire clears it
            if (wasFused) {
                gauge.reset();                              // re-anchor the recovery gauge at this fusion
            }
        } else if (fireDefusion) {
            alreadyFiredDefusion = true;
            wasFused = false;                                // all-clear sounded; require a new fusion next
        }
        if (direction != FireDirection.NONE && firstFire == null) {
            firstFire = signal;
        }
        previous = copy(correlation);
        return signal;
    }

    /** FUSION takes precedence over DEFUSION when both somehow trip (their gates are disjoint in practice). */
    private FireDirection fireDirection(boolean firePrimary, boolean fireDefusion) {
        if (firePrimary) {
            return config.fireArm() == FireArm.UPPER ? FireDirection.FUSION : FireDirection.DEFUSION;
        }
        return fireDefusion ? FireDirection.DEFUSION : FireDirection.NONE;
    }

    @Override
    public void recalibrate(Calibration calibration) {
        if (calibration == null) {
            throw new IllegalArgumentException("calibration must not be null");
        }
        // Re-baseline: fresh accumulator on the new (mu, sigma) with S+/S- = 0 — the old
        // accumulation is in sigma-of-the-old-baseline units and would be a spurious head-start
        // against a baseline just redefined to make it look normal. The
        // previous matrix, wasFused latch, gauge buffer, debounce flags and window-id state are
        // deliberately untouched: a recalibration is not a session boundary.
        this.calibration = calibration;
        this.cusum = new CusumAccumulator(calibration.mu(), calibration.sigma(), config.k());
        this.lBand = calibration.muDensity() + config.defusion().bandC() * calibration.sigmaDensity();
    }

    @Override
    public void onSessionBoundary() {
        previous = null;
        cusum.reset();
        gauge.reset();
        alreadyFiredPrimary = false;
        alreadyFiredDefusion = false;
        wasFused = false;
        hasWindowId = false;   // the boundary is itself the reset; the next windowed call must not re-arm again
    }

    @Override
    public Optional<ChangeSignal> firstFire() {
        return Optional.ofNullable(firstFire);
    }

    private void requireSquareOfOrder(double[][] matrix) {
        if (matrix == null) {
            throw new IllegalArgumentException("correlation matrix must not be null");
        }
        if (matrix.length != order) {
            throw new InvalidInputException(
                    "matrix order must match the detector; got [" + matrix.length + "], expected [" + order + "]");
        }
        for (int i = 0; i < order; i++) {
            if (matrix[i] == null || matrix[i].length != order) {
                int len = matrix[i] == null ? -1 : matrix[i].length;
                throw new InvalidInputException(
                        "correlation matrix must be square; row " + i + " has length [" + len
                                + "], expected [" + order + "]");
            }
        }
    }

    private static double[][] copy(double[][] matrix) {
        double[][] out = new double[matrix.length][];
        for (int i = 0; i < matrix.length; i++) {
            out[i] = matrix[i].clone();
        }
        return out;
    }
}
