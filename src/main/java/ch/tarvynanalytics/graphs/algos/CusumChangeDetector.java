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
 * <strong>de-fusion</strong> ("all-clear" / re-entry) path — a lower-arm CUSUM on the density series
 * gated by a was-recently-fused latch and an inverted low-density gate — is <em>disabled by default</em>
 * ({@code config.defusion().enabled() == false}) and is entirely inert in that case, so the pinned
 * upper-arm behaviour cannot move. See {@code initiative-s-defusion-rule-spec.md}.</p>
 */
final class CusumChangeDetector implements ChangeDetector {

    private final int order;
    private final DetectorConfig config;
    private final Calibration calibration;
    private final CusumAccumulator cusum;

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
        this.cusum = new CusumAccumulator(calibration.mu(), calibration.sigma(), config.k(),
                calibration.muDensity(), calibration.sigmaDensity(), config.defusion().k());
    }

    @Override
    public ChangeSignal onMatrix(double[][] correlation) {
        return ingest(correlation);
    }

    @Override
    public ChangeSignal onMatrix(double[][] correlation, long windowId) {
        if (hasWindowId && windowId != currentWindowId) {
            // spec §2.4 reset_ids: a new window re-arms the debounce and the firing arm only —
            // the previous matrix and the opposite arm are left intact (cf. onSessionBoundary()).
            alreadyFiredPrimary = false;
            cusum.resetArm(config.fireArm());
            if (config.defusion().enabled()) {
                alreadyFiredDefusion = false;
                cusum.resetDensityArm();
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
        if (defusionEnabled && Double.isFinite(density)) {
            // The density arm advances even on a change gap — density is observable from C_t alone.
            cusum.stepDensity(density);
        }

        double sPlus = cusum.sPlus();
        double sMinus = cusum.sMinus();
        double sDensityMinus = cusum.sDensityMinus();

        // Primary (fireArm-selected) fire: AND(level gate, firing arm > h), debounced. Unchanged.
        boolean gateLevel = density >= calibration.level();   // NaN density => false
        double firingArm = config.fireArm() == FireArm.UPPER ? sPlus : sMinus;
        boolean firePrimary = !alreadyFiredPrimary && !gap && gateLevel && firingArm > config.h();

        // De-fusion fire: only after a fusion fire (wasFused), with the inverted low-density gate and
        // the density lower arm past its threshold. Inert unless enabled.
        boolean gateLow = Double.isFinite(density) && density <= calibration.lowLevel();
        boolean fireDefusion = defusionEnabled && wasFused && !alreadyFiredDefusion
                && gateLow && sDensityMinus > config.defusion().h();

        FireDirection direction = fireDirection(firePrimary, fireDefusion);
        ChangeSignal signal = new ChangeSignal(seq++, metrics, sPlus, sMinus, sDensityMinus, direction);

        if (firePrimary) {
            alreadyFiredPrimary = true;
            cusum.resetArm(config.fireArm());
            wasFused = config.fireArm() == FireArm.UPPER;   // a fusion fire arms the all-clear; a lower fire clears it
        } else if (fireDefusion) {
            alreadyFiredDefusion = true;
            wasFused = false;                                // all-clear sounded; require a new fusion next
            cusum.resetDensityArm();
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
    public void onSessionBoundary() {
        previous = null;
        cusum.reset();
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
