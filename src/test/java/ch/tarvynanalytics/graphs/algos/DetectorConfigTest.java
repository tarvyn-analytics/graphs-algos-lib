package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Pins the settled per-asset constants and the config validation (spec §4). */
class DetectorConfigTest {

    @Test
    void crypto_HasSettledCryptoConstants() {
        DetectorConfig c = DetectorConfig.crypto();
        assertEquals(1.5, c.k());
        assertEquals(8.0, c.h());
        assertEquals(99.0, c.levelPctile());
        assertEquals(0.5, c.edgeThreshold());
        assertEquals(1.0, c.epsilonSigma());
        assertEquals(FireArm.UPPER, c.fireArm());
    }

    @Test
    void equity_HasOriginalReplayAlertConstants() {
        DetectorConfig c = DetectorConfig.equity();
        assertEquals(1.0, c.k());
        assertEquals(5.0, c.h());
        assertEquals(90.0, c.levelPctile());
        assertEquals(FireArm.UPPER, c.fireArm());
    }

    @Test
    void constructor_NegativeK_ThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> new DetectorConfig(-1.0, 5.0, 90.0, 0.5, 1.0, FireArm.UPPER));
    }

    @Test
    void constructor_NonPositiveH_ThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> new DetectorConfig(1.0, 0.0, 90.0, 0.5, 1.0, FireArm.UPPER));
    }

    @Test
    void constructor_PercentileOutOfRange_ThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> new DetectorConfig(1.0, 5.0, 101.0, 0.5, 1.0, FireArm.UPPER));
    }

    @Test
    void constructor_NonPositiveEpsilonSigma_ThrowsInvalidInputException() {
        assertThrows(InvalidInputException.class,
                () -> new DetectorConfig(1.0, 5.0, 90.0, 0.5, 0.0, FireArm.UPPER));
    }

    @Test
    void constructor_NullFireArm_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DetectorConfig(1.0, 5.0, 90.0, 0.5, 1.0, null));
    }

    @Test
    void fireArm_ValueOf_ResolvesBothArms() {
        assertEquals(FireArm.UPPER, FireArm.valueOf("UPPER"));
        assertEquals(FireArm.LOWER, FireArm.valueOf("LOWER"));
    }

    @Test
    void factories_HaveDefusionDisabledByDefault() {
        assertFalse(DetectorConfig.crypto().defusion().enabled());
        assertFalse(DetectorConfig.equity().defusion().enabled());
        // crypto mirrors the fusion k/h; equity mirrors its own.
        assertEquals(1.5, DetectorConfig.crypto().defusion().k());
        assertEquals(8.0, DetectorConfig.crypto().defusion().h());
        assertEquals(25.0, DetectorConfig.crypto().defusion().lowLevelPctile());
        assertEquals(1.0, DetectorConfig.equity().defusion().k());
    }

    @Test
    void sixArgConstructor_DefaultsToDisabledDefusion() {
        DetectorConfig c = new DetectorConfig(1.0, 5.0, 90.0, 0.5, 1.0, FireArm.UPPER);
        assertFalse(c.defusion().enabled());
        assertEquals(DefusionConfig.disabled(), c.defusion());
    }

    @Test
    void constructor_NullDefusion_ThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
                () -> new DetectorConfig(1.0, 5.0, 90.0, 0.5, 1.0, FireArm.UPPER, null));
    }
}
