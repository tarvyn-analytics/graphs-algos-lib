package ch.tarvynanalytics.graphs.algos.model;

/**
 * The regime edge, if any, that a {@link RegimeSignal} step crossed — the hysteresis
 * (Schmitt-trigger) transition on the density level.
 *
 * <p>{@link #FUSION_ONSET} is the up-crossing ({@link RegimeState#CALM} → {@link RegimeState#FUSED}):
 * density held at/above the high mark for the confirmation run — a regime opened.
 * {@link #CALM_ONSET} is the down-crossing ({@link RegimeState#FUSED} → {@link RegimeState#CALM}):
 * density held at/below the low mark for the confirmation run — the regime closed / all-clear. Most
 * steps cross no edge and report {@link #NONE}.</p>
 */
public enum RegimeTransition {

    /** This step crossed no regime edge (the state is unchanged). */
    NONE,

    /** Up-crossing: {@link RegimeState#CALM} → {@link RegimeState#FUSED} — a regime opened. */
    FUSION_ONSET,

    /** Down-crossing: {@link RegimeState#FUSED} → {@link RegimeState#CALM} — the regime closed (all-clear). */
    CALM_ONSET
}
