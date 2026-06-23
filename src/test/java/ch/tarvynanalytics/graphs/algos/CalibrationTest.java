package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The {@link Calibration} record's sigma {@code > 0} guard (spec §2.1). */
class CalibrationTest {

    @Test
    void constructor_ValidValues_AreExposed() {
        Calibration c = new Calibration(0.1, 0.05, 0.9);
        assertEquals(0.1, c.mu());
        assertEquals(0.05, c.sigma());
        assertEquals(0.9, c.level());
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
