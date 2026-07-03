package ch.tarvynanalytics.graphs.algos.model;

/**
 * The regime-state signal emitted per level sample by the level+hysteresis
 * {@code RegimeStateDetector} (Initiative-S H2R-2 backbone): the level fed in, the regime
 * <em>after</em> this step, and which regime edge — if any — this step crossed.
 *
 * <p>This is the continuous-tape backbone the H2R-1 finding recommended: the density level read
 * with two thresholds and persistence self-segments calm from fused where the change-CUSUM could
 * not (see {@code initiative-s-h2-lifecycle-revision-design.md}). One {@link RegimeTransition}
 * other than {@link RegimeTransition#NONE} marks exactly one regime cycle edge — a
 * {@link RegimeTransition#FUSION_ONSET} opens a regime, the paired {@link RegimeTransition#CALM_ONSET}
 * closes it (the all-clear).</p>
 *
 * @param seq        monotonic sample index in the stream, starting at 0 for the first level fed
 * @param level      the level value fed to this step (already smoothed by the caller); may be
 *                   {@link Double#NaN}/{@code ±Infinity} for a data gap, in which case the state is
 *                   carried unchanged and {@link #transition()} is {@link RegimeTransition#NONE}
 * @param state      the regime state after this step
 * @param transition the regime edge this step crossed ({@link RegimeTransition#NONE} if none)
 */
public record RegimeSignal(long seq, double level, RegimeState state, RegimeTransition transition) {

    /**
     * Validates that the state and transition components are present.
     */
    public RegimeSignal {
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (transition == null) {
            throw new IllegalArgumentException("transition must not be null");
        }
    }

    /**
     * Whether this step crossed a regime edge (opened or closed a regime).
     *
     * @return {@code true} iff {@link #transition()} is not {@link RegimeTransition#NONE}
     */
    public boolean crossed() {
        return transition != RegimeTransition.NONE;
    }
}
