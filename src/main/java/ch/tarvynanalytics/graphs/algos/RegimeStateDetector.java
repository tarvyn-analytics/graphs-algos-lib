package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.RegimeSignal;
import ch.tarvynanalytics.graphs.algos.model.RegimeState;
import ch.tarvynanalytics.graphs.algos.model.RegimeTransition;

/**
 * Package-private {@link RegimeDetector} implementation: the level+hysteresis (Schmitt-trigger)
 * state machine on a scalar level series. See {@link RegimeDetector} for the public contract and
 * {@link RegimeDetectors} for construction.
 *
 * <p>Two states ({@link RegimeState#CALM} / {@link RegimeState#FUSED}) and two persistence runs. In
 * {@code CALM} the up-run counts consecutive samples at/above {@code hi} and resets on any finite
 * sample below it; reaching {@code confirmBars} opens a regime ({@link RegimeTransition#FUSION_ONSET}).
 * In {@code FUSED} the down-run counts consecutive samples at/below {@code lo} and resets on any
 * finite sample above it; reaching {@code confirmBars} closes it ({@link RegimeTransition#CALM_ONSET}).
 * A non-finite (gap) sample is skipped whole — no run advances or resets, no state change. A cold
 * stream starts {@code CALM} (diversified until proven fused). This is a faithful streaming port of
 * the spike oracle {@code h2r1_regime_model.py}. Single-writer, O(1) state and time.</p>
 */
final class RegimeStateDetector implements RegimeDetector {

    private final double hi;
    private final double lo;
    private final int confirmBars;

    private RegimeState state;
    private int upRun;
    private int downRun;
    private long seq;

    RegimeStateDetector(RegimeConfig config) {
        this.hi = config.hi();
        this.lo = config.lo();
        this.confirmBars = config.confirmBars();
        this.state = RegimeState.CALM;
    }

    @Override
    public RegimeSignal step(double level) {
        RegimeTransition transition = RegimeTransition.NONE;

        if (Double.isFinite(level)) {
            if (state == RegimeState.CALM) {
                upRun = level >= hi ? upRun + 1 : 0;
                if (upRun >= confirmBars) {
                    state = RegimeState.FUSED;
                    upRun = 0;
                    downRun = 0;
                    transition = RegimeTransition.FUSION_ONSET;
                }
            } else {
                downRun = level <= lo ? downRun + 1 : 0;
                if (downRun >= confirmBars) {
                    state = RegimeState.CALM;
                    upRun = 0;
                    downRun = 0;
                    transition = RegimeTransition.CALM_ONSET;
                }
            }
        }
        // Non-finite level: a data gap — carry the regime and both runs unchanged (transition NONE).

        return new RegimeSignal(seq++, level, state, transition);
    }

    @Override
    public void reset() {
        state = RegimeState.CALM;
        upRun = 0;
        downRun = 0;
        seq = 0;
    }
}
