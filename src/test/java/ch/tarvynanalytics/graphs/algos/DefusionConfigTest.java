package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The {@link DefusionConfig} recovery-gauge sub-tuning: defaults, accessors, and validation. */
class DefusionConfigTest {

    @Test
    void disabled_IsOffWithSensibleDefaults() {
        DefusionConfig d = DefusionConfig.disabled();
        assertFalse(d.enabled());
        assertEquals(0.75, d.bandC());
        assertEquals(0.80, d.theta());
        assertEquals(96, d.gaugeWindowSamples());
    }

    @Test
    void constructor_ExposesAllComponents() {
        DefusionConfig d = new DefusionConfig(0.5, 0.6, 48, true);
        assertEquals(0.5, d.bandC());
        assertEquals(0.6, d.theta());
        assertEquals(48, d.gaugeWindowSamples());
        assertEquals(true, d.enabled());
    }

    @Test
    void constructor_NegativeBandC_Throws() {
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(-0.1, 0.8, 96, true));
    }

    @Test
    void constructor_ThetaOutOfRange_Throws() {
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(0.75, -0.1, 96, true));
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(0.75, 1.1, 96, true));
    }

    @Test
    void constructor_NonPositiveGaugeWindow_Throws() {
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(0.75, 0.8, 0, true));
    }
}
