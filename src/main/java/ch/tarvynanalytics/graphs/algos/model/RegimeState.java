package ch.tarvynanalytics.graphs.algos.model;

/**
 * The correlation-structure regime a {@link RegimeSignal} reports — the state of the
 * level+hysteresis (Schmitt-trigger) read of the density level.
 *
 * <p>Density (the share of asset pairs with {@code |r| > τ}) <em>is</em> the regime: {@link #CALM}
 * is diversified (few strong edges, idiosyncratic moves), {@link #FUSED} is risk-off (one macro
 * factor dominates, almost everything moves together). The market slides between the two; two
 * thresholds with persistence decide
 * which regime is in force without flip-flopping.</p>
 */
public enum RegimeState {

    /** Diversified / low-density: assets move on their own news; a shock stays local. */
    CALM,

    /** Risk-off / high-density: one macro fear factor dominates; diversification stops working. */
    FUSED
}
