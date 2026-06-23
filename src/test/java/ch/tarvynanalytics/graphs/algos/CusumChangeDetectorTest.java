package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.ChangeSignal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end detector behaviour (spec §2, §5): first-matrix null, AND(level, CUSUM) fire rule,
 * one-fire-per-window debounce, both arms emitted, NaN-change gap, session reset, censored miss.
 * A 2x2 matrix has a single pair, so {@code weighted_change = |Δr|} and the level gate is the
 * single edge indicator — exact control over the change fed into the detector.
 */
class CusumChangeDetectorTest {

    private static double[][] m2(double r) {
        return new double[][]{{1.0, r}, {r, 1.0}};
    }

    @Test
    void onMatrix_FirstMatrix_ReturnsNull() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        assertNull(d.onMatrix(m2(0.5)));
    }

    @Test
    void onMatrix_SecondMatrix_EmitsSeqZero() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        d.onMatrix(m2(0.0));
        ChangeSignal s = d.onMatrix(m2(0.6));
        assertEquals(0L, s.seq());
        assertEquals(0.6, s.metrics().weightedChange(), 1e-12);
    }

    @Test
    void onMatrix_FiresOncePerWindow_ThenDebounces() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        assertNull(d.onMatrix(m2(0.0)));
        ChangeSignal s0 = d.onMatrix(m2(0.6));    // z=6 -> S+=5 (not > h=5)
        assertFalse(s0.fired());
        ChangeSignal s1 = d.onMatrix(m2(0.0));    // z=6 -> S+=10 > 5, level gate open
        assertTrue(s1.fired());
        assertEquals(10.0, s1.sPlus(), 1e-12);    // emitted crossing value, before the debounce reset
        ChangeSignal s2 = d.onMatrix(m2(0.6));    // gates would reopen, but already fired
        assertFalse(s2.fired());
        assertTrue(d.firstFire().isPresent());
        assertEquals(1L, d.firstFire().get().seq());
    }

    @Test
    void onMatrix_CusumOpenButLevelGateClosed_DoesNotFire() {
        // level = 2.0 is unreachable (density in [0,1]) -> gate A never opens.
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 2.0));
        d.onMatrix(m2(0.0));
        d.onMatrix(m2(0.6));
        ChangeSignal s = d.onMatrix(m2(0.0));
        assertTrue(s.sPlus() > DetectorConfig.equity().h());   // CUSUM arm is open...
        assertFalse(s.fired());                                 // ...but the level gate blocks the fire
        assertTrue(d.firstFire().isEmpty());
    }

    @Test
    void onMatrix_DefusionArmIsComputedAndEmitted() {
        // mu=1.0, a flat change of 0 gives z=-10 -> S- grows, S+ stays 0.
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(1.0, 0.1, 2.0));
        d.onMatrix(m2(0.0));
        ChangeSignal s = d.onMatrix(m2(0.0));
        assertEquals(9.0, s.sMinus(), 1e-12);
        assertEquals(0.0, s.sPlus(), 1e-12);
        assertFalse(s.fired());                  // v1 fires on UPPER only
    }

    @Test
    void onMatrix_LowerArmConfig_FiresOnDefusion() {
        DetectorConfig reentry = new DetectorConfig(1.0, 5.0, 90.0, 0.5, 1.0, FireArm.LOWER);
        ChangeDetector d = ChangeDetectors.create(2, reentry, new Calibration(1.0, 0.1, 0.0));
        assertNull(d.onMatrix(m2(0.9)));
        ChangeSignal s = d.onMatrix(m2(0.9));     // change 0 -> z=-10 -> S-=9 > h=5; density(0.9)=1 >= 0
        assertTrue(s.sMinus() > reentry.h());
        assertTrue(s.fired());
        assertTrue(d.firstFire().isPresent());
    }

    @Test
    void onMatrix_NaNChangeGap_CarriesAccumulatorsAndDoesNotFire() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        d.onMatrix(m2(0.0));
        ChangeSignal built = d.onMatrix(m2(0.6));
        assertEquals(5.0, built.sPlus(), 1e-12);
        double[][] nan = {{1.0, Double.NaN}, {Double.NaN, 1.0}};
        ChangeSignal gap = d.onMatrix(nan);
        assertTrue(Double.isNaN(gap.metrics().weightedChange()));
        assertEquals(5.0, gap.sPlus(), 1e-12);          // carried unchanged, not stepped
        assertFalse(gap.fired());
        assertEquals(2, gap.metrics().nComponents());    // secondary features still emitted from C_t
    }

    @Test
    void onSessionBoundary_ResetsPreviousAndAccumulators() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        d.onMatrix(m2(0.0));
        d.onMatrix(m2(0.6));    // S+ built up
        d.onSessionBoundary();
        assertNull(d.onMatrix(m2(0.9)));         // previous dropped -> no transition
        ChangeSignal s = d.onMatrix(m2(0.9));    // change 0 -> z=0 -> S+ = max(0, -1) = 0
        assertEquals(0.0, s.sPlus(), 1e-12);
    }

    @Test
    void firstFire_NeverFires_IsEmpty() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.5, 0.1, 0.0));
        d.onMatrix(m2(0.5));
        for (int i = 0; i < 5; i++) {
            assertFalse(d.onMatrix(m2(0.5)).fired());
        }
        assertTrue(d.firstFire().isEmpty());
    }

    @Test
    void onMatrix_OrderMismatch_ThrowsInvalidInputException() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        double[][] threeByThree = {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}};
        assertThrows(InvalidInputException.class, () -> d.onMatrix(threeByThree));
    }

    @Test
    void onMatrix_NonSquare_ThrowsInvalidInputException() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        double[][] ragged = {{1.0, 0.0}, {0.0}};
        assertThrows(InvalidInputException.class, () -> d.onMatrix(ragged));
    }

    @Test
    void onMatrix_Null_ThrowsIllegalArgumentException() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        assertThrows(IllegalArgumentException.class, () -> d.onMatrix(null));
    }
}
