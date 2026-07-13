package ch.tarvynanalytics.graphs.algos;

/**
 * Which arm of the two-sided CUSUM opens an alert. v1 fires on the upper arm
 * (fusion / structure-tightening); the lower arm (de-fusion / structure-loosening,
 * the re-entry signal) is always computed and emitted, and becomes the firing arm
 * by a configuration flip rather than a code change by configuration.
 */
public enum FireArm {

    /** Fusion / structure-tightening — {@code S+ > h} fires the exit alert (v1 default). */
    UPPER,

    /** De-fusion / structure-loosening — {@code S- > h} fires the re-entry alert (later config flip). */
    LOWER
}
