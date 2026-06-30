package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The {@link DefusionConfig} de-fusion sub-tuning: defaults, accessors, and validation. */
class DefusionConfigTest {

    @Test
    void disabled_IsOffWithSensibleDefaults() {
        DefusionConfig d = DefusionConfig.disabled();
        assertFalse(d.enabled());
        assertEquals(1.0, d.k());
        assertEquals(5.0, d.h());
        assertEquals(25.0, d.lowLevelPctile());
    }

    @Test
    void constructor_ExposesAllComponents() {
        DefusionConfig d = new DefusionConfig(1.5, 8.0, 30.0, true);
        assertEquals(1.5, d.k());
        assertEquals(8.0, d.h());
        assertEquals(30.0, d.lowLevelPctile());
        assertEquals(true, d.enabled());
    }

    @Test
    void constructor_NegativeK_Throws() {
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(-0.1, 5.0, 25.0, true));
    }

    @Test
    void constructor_NonPositiveH_Throws() {
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(1.0, 0.0, 25.0, true));
    }

    @Test
    void constructor_LowLevelPctileOutOfRange_Throws() {
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(1.0, 5.0, -1.0, true));
        assertThrows(InvalidInputException.class, () -> new DefusionConfig(1.0, 5.0, 101.0, true));
    }
}
