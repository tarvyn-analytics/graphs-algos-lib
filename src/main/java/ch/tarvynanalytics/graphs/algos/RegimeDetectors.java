package ch.tarvynanalytics.graphs.algos;

/**
 * Factory for the {@link RegimeDetector} — the public entry point for the
 * level+hysteresis regime-state detector (mirrors the {@link ChangeDetectors} factory recipe of the
 * rest of the library). The detector implementation stays package-private (family invariant:
 * implementations are package-private; public surface is the entry points).
 */
public final class RegimeDetectors {

    private RegimeDetectors() {
    }

    /**
     * Creates a streaming regime-state detector for one timescale's level series.
     *
     * @param config the Schmitt-trigger tuning (see {@link RegimeConfig#crypto()})
     * @return a new, single-writer {@link RegimeDetector} starting in
     *         {@link ch.tarvynanalytics.graphs.algos.model.RegimeState#CALM}
     * @throws IllegalArgumentException if {@code config} is {@code null}
     */
    public static RegimeDetector create(RegimeConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        return new RegimeStateDetector(config);
    }
}
