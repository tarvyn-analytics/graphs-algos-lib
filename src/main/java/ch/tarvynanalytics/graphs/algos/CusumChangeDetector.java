package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.ChangeMetrics;
import ch.tarvynanalytics.graphs.algos.model.ChangeSignal;

import java.util.Optional;

/**
 * Package-private {@link ChangeDetector} implementation: the streaming change metric
 * ({@link ChangeMetricsEngine}) wired to the two-sided CUSUM ({@link CusumAccumulator}) and
 * the AND(level-gate, firing-arm) fire rule with one-fire-per-window debounce. See
 * {@link ChangeDetector} for the public contract and {@link ChangeDetectors} for construction.
 */
final class CusumChangeDetector implements ChangeDetector {

    private final int order;
    private final DetectorConfig config;
    private final Calibration calibration;
    private final CusumAccumulator cusum;

    private double[][] previous;
    private long seq;
    private boolean alreadyFired;
    private ChangeSignal firstFire;

    CusumChangeDetector(int order, DetectorConfig config, Calibration calibration) {
        if (order < 0) {
            throw new InvalidInputException("matrix order must be >= 0; got [" + order + "]");
        }
        this.order = order;
        this.config = config;
        this.calibration = calibration;
        this.cusum = new CusumAccumulator(calibration.mu(), calibration.sigma(), config.k());
    }

    @Override
    public ChangeSignal onMatrix(double[][] correlation) {
        requireSquareOfOrder(correlation);
        if (previous == null) {
            previous = copy(correlation);
            return null;
        }

        ChangeMetrics metrics = ChangeMetricsEngine.compute(previous, correlation, config.edgeThreshold());
        double change = metrics.weightedChange();
        boolean gap = Double.isNaN(change);

        if (!gap) {
            cusum.step(change);   // NaN-change gap: carry accumulators unchanged
        }

        double sPlus = cusum.sPlus();
        double sMinus = cusum.sMinus();

        boolean gateLevel = metrics.densityLevel() >= calibration.level();   // NaN density => false
        double firingArm = config.fireArm() == FireArm.UPPER ? sPlus : sMinus;
        boolean gateCusum = firingArm > config.h();
        boolean fire = !alreadyFired && !gap && gateLevel && gateCusum;

        ChangeSignal signal = new ChangeSignal(seq++, metrics, sPlus, sMinus, fire);

        if (fire) {
            alreadyFired = true;
            cusum.resetArm(config.fireArm());   // debounce: reset the firing arm after firing
            if (firstFire == null) {
                firstFire = signal;
            }
        }
        previous = copy(correlation);
        return signal;
    }

    @Override
    public void onSessionBoundary() {
        previous = null;
        cusum.reset();
        alreadyFired = false;
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
