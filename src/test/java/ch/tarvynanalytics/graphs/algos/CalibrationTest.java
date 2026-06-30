package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The {@link Calibration} record's sigma {@code > 0} guard (spec §2.1) and de-fusion fields. */
class CalibrationTest {

    @Test
    void constructor_ValidValues_AreExposed() {
        Calibration c = new Calibration(0.1, 0.05, 0.9);
        assertEquals(0.1, c.mu());
        assertEquals(0.05, c.sigma());
        assertEquals(0.9, c.level());
    }

    @Test
    void threeArgConstructor_LeavesDefusionFieldsNaN_Inert() {
        Calibration c = new Calibration(0.1, 0.05, 0.9);
        assertTrue(Double.isNaN(c.muDensity()));
        assertTrue(Double.isNaN(c.sigmaDensity()));
        assertTrue(Double.isNaN(c.lowLevel()));
    }

    @Test
    void sixArgConstructor_ExposesDefusionFields() {
        Calibration c = new Calibration(0.1, 0.05, 0.9, 0.7, 0.12, 0.25);
        assertEquals(0.7, c.muDensity());
        assertEquals(0.12, c.sigmaDensity());
        assertEquals(0.25, c.lowLevel());
    }

    @Test
    void constructor_ZeroSigma_ThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new Calibration(0.1, 0.0, 0.9));
    }

    @Test
    void constructor_NegativeSigma_ThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class, () -> new Calibration(0.1, -0.01, 0.9));
    }
}
