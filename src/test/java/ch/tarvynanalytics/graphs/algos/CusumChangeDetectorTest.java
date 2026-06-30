package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.ChangeSignal;
import ch.tarvynanalytics.graphs.algos.model.FireDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void onMatrix_ChangeMetricLowerArm_IsComputedAndEmitted() {
        // mu=1.0, a flat change of 0 gives z=-10 -> S- grows, S+ stays 0.
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(1.0, 0.1, 2.0));
        d.onMatrix(m2(0.0));
        ChangeSignal s = d.onMatrix(m2(0.0));
        assertEquals(9.0, s.sMinus(), 1e-12);
        assertEquals(0.0, s.sPlus(), 1e-12);
        assertFalse(s.fired());                  // v1 fires on UPPER only
    }

    @Test
    void onMatrix_LegacyLowerArmConfig_FiresOnChangeMetricLowerArm_MechanicsOnly() {
        // MECHANICS PIN, not a usable de-fusion config: fireArm=LOWER fires on the change-metric lower
        // arm (sMinus), which is structurally dead on real data (defusion spec §1). The real all-clear
        // is the density-arm path (defusionEnabled), tested below. Kept to guard the max(0,S- - z - k)
        // recursion + the fireArm=LOWER primary path. With z chosen far-negative (mu=1, change=0) the
        // arm can be made to fire here; the direction of a lower-arm primary fire is DEFUSION.
        DetectorConfig reentry = new DetectorConfig(1.0, 5.0, 90.0, 0.5, 1.0, FireArm.LOWER);
        ChangeDetector d = ChangeDetectors.create(2, reentry, new Calibration(1.0, 0.1, 0.0));
        assertNull(d.onMatrix(m2(0.9)));
        ChangeSignal s = d.onMatrix(m2(0.9));     // change 0 -> z=-10 -> S-=9 > h=5; density(0.9)=1 >= 0
        assertTrue(s.sMinus() > reentry.h());
        assertTrue(s.fired());
        assertEquals(FireDirection.DEFUSION, s.fireDirection());
        assertTrue(d.firstFire().isPresent());
    }

    // ---- de-fusion ("all-clear") path: the recovery gauge + was-fused latch + full-window confirmation ----
    // (Oracle A2-A6 of initiative-s-defusion-gauge-spec.md §6). 2x2 matrix => density in {0,1} exact.
    // muDensity=0.2, sigmaDensity=0.4, bandC=0.75 => L_band=0.5: density 0 in-band, density 1 out-of-band.
    // Gauge window N_g=4 and theta=0.75 give hand-computable fractions.

    /** A de-fusion-enabled crypto-shaped config: fusion on UPPER, gauge N_g=4, theta=0.75. */
    private static DetectorConfig defusionConfig() {
        return new DetectorConfig(1.0, 5.0, 90.0, 0.5, 1.0, FireArm.UPPER,
                new DefusionConfig(0.75, 0.75, 4, true));
    }

    /** change mu=0/sigma=0.1, level gate L=0.5 (density 1 opens fusion); muDensity=0.2/sigmaDensity=0.4 -> L_band=0.5. */
    private static Calibration defusionCalibration() {
        return new Calibration(0.0, 0.1, 0.5, 0.2, 0.4);
    }

    @Test
    void onMatrix_DefusionBeforeAnyFusion_DoesNotFire_WasFusedLatch() {
        // A2: the gauge fills to 1.0 with a full window (density back in the band), but no fusion ever
        // fired => wasFused false => the all-clear cannot precede the alarm.
        ChangeDetector d = ChangeDetectors.create(2, defusionConfig(), defusionCalibration());
        assertNull(d.onMatrix(m2(0.0)));            // prime (density 0)
        ChangeSignal s = null;
        for (int i = 0; i < 4; i++) {
            s = d.onMatrix(m2(0.0));                // 4 in-band samples: gauge -> 1.0, window full at the 4th
        }
        assertEquals(1.0, s.recoveryGauge(), 1e-12);
        assertFalse(s.fired());                      // full, saturated gauge, but no prior fusion => no all-clear
    }

    @Test
    void onMatrix_FusionThenDefusion_Alternates() {
        // A3: fusion fires (density up), then after a full gauge window of in-band samples the all-clear
        // fires (gauge 1.0 >= theta), clearing the latch. The full-window guard delays the fire from the
        // first in-band sample (gauge already 1.0) to the fourth (window full).
        ChangeDetector d = ChangeDetectors.create(2, defusionConfig(), defusionCalibration());
        assertNull(d.onMatrix(m2(0.0)));            // prime (density 0)
        ChangeSignal fusion = d.onMatrix(m2(0.9));  // change 0.9 -> z=9 -> S+=8 > 5; density 1 >= L -> FUSION
        assertEquals(FireDirection.FUSION, fusion.fireDirection());
        assertEquals(0.0, fusion.recoveryGauge(), 1e-12);   // density 1 out-of-band; gauge re-anchored after
        for (int i = 0; i < 3; i++) {
            ChangeSignal s = d.onMatrix(m2(0.0));   // 3 in-band samples: gauge 1.0 but window not yet full
            assertEquals(1.0, s.recoveryGauge(), 1e-12);
            assertNotEquals(FireDirection.DEFUSION, s.fireDirection());
        }
        ChangeSignal defusion = d.onMatrix(m2(0.0)); // 4th in-band sample: window full -> DEFUSION
        assertEquals(FireDirection.DEFUSION, defusion.fireDirection());
        assertEquals(1.0, defusion.recoveryGauge(), 1e-12);
    }

    @Test
    void onMatrix_GaugeFullAndOverThetaButSampleOutOfBand_DoesNotDefuse_GateAnd() {
        // A4: wasFused true, window full and gauge=0.75 >= theta, but the CURRENT sample is out-of-band
        // (density 1) => no fire; the next in-band sample (gauge still 0.75) then fires. Pins the AND gate.
        ChangeDetector d = ChangeDetectors.create(2, defusionConfig(), defusionCalibration());
        assertNull(d.onMatrix(m2(0.0)));
        d.onMatrix(m2(0.9));                          // FUSION (sets wasFused, re-anchors gauge)
        d.onMatrix(m2(0.0));                          // post-fusion in-band #1 (gauge 1/1)
        d.onMatrix(m2(0.0));                          // #2 (2/2)
        d.onMatrix(m2(0.0));                          // #3 (3/3)
        ChangeSignal out = d.onMatrix(m2(0.9));       // #4 density 1: window full, gauge mean[T,T,T,F]=0.75, but out-of-band
        assertEquals(0.75, out.recoveryGauge(), 1e-12);
        assertNotEquals(FireDirection.DEFUSION, out.fireDirection());
        ChangeSignal back = d.onMatrix(m2(0.0));      // density 0: gauge mean[T,T,F,T]=0.75, in-band -> DEFUSION
        assertEquals(0.75, back.recoveryGauge(), 1e-12);
        assertEquals(FireDirection.DEFUSION, back.fireDirection());
    }

    @Test
    void onMatrix_DefusionDisabledByDefault_NeverDefuses_ButGaugeStillComputed_Regression() {
        // A5: the same fusion->recovery stream under the default crypto() config (firing OFF) never emits
        // DEFUSION, yet the gauge IS still computed (informational, independent of the enabled flag).
        // sigma=0.08 makes z=11.25 so a single step clears crypto's h=8 (k=1.5) and fusion fires.
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.crypto(),
                new Calibration(0.0, 0.08, 0.5, 0.2, 0.4));
        d.onMatrix(m2(0.0));
        ChangeSignal fusion = d.onMatrix(m2(0.9));    // S+=9.75>8, density 1 >= L -> FUSION (re-anchors the gauge)
        assertEquals(FireDirection.FUSION, fusion.fireDirection());
        for (int i = 0; i < 5; i++) {
            ChangeSignal s = d.onMatrix(m2(0.0));     // in-band recovery samples
            assertNotEquals(FireDirection.DEFUSION, s.fireDirection());
            assertEquals(1.0, s.recoveryGauge(), 1e-12);   // gauge climbs to 1.0 even though firing is OFF
        }
    }

    @Test
    void onMatrix_DensityHoldsAtPlateau_GaugeStaysLow_DoesNotDefuse() {
        // A6: de-fusion enabled and the change-metric lower arm sMinus is large (mu=1, change=0), but
        // density holds at the plateau (1, out-of-band) so the gauge never enters the band -> no all-clear.
        // The re-entry trigger is the gauge occupancy, not sMinus.
        ChangeDetector d = ChangeDetectors.create(2, defusionConfig(),
                new Calibration(1.0, 0.1, 0.5, 0.2, 0.4));
        assertNull(d.onMatrix(m2(0.9)));             // prime, density 1
        ChangeSignal s = null;
        for (int i = 0; i < 5; i++) {
            s = d.onMatrix(m2(0.9));                  // change 0 -> sMinus grows; density stays 1 (out-of-band)
        }
        assertTrue(s.sMinus() > 0.0);
        assertEquals(0.0, s.recoveryGauge(), 1e-12); // gauge never in-band
        assertFalse(s.fired());
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

    /**
     * Spec §2.4 {@code reset_ids}: a window-id change re-arms the one-fire-per-window debounce, so a
     * second window can fire again where the global debounce would have suppressed it. The same matrix
     * sequence fed without window ids fires only once — that gap is exactly what would undercount the
     * false-alarm rate over a long multi-session calm span.
     */
    @Test
    void onMatrixWindowed_WindowIdChange_ReArmsDebounceAndFiresOncePerWindow() {
        ChangeDetector windowed = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        int windowedFires = 0;
        assertNull(windowed.onMatrix(m2(0.0), 1L));                           // first matrix: no transition
        windowedFires += windowed.onMatrix(m2(0.6), 1L).fired() ? 1 : 0;      // S+=5, not > h=5
        windowedFires += windowed.onMatrix(m2(0.0), 1L).fired() ? 1 : 0;      // S+=10 > 5 -> FIRE, debounce
        windowedFires += windowed.onMatrix(m2(0.6), 1L).fired() ? 1 : 0;      // already fired this window
        windowedFires += windowed.onMatrix(m2(0.0), 2L).fired() ? 1 : 0;      // new window re-arms; S+ back to 5
        windowedFires += windowed.onMatrix(m2(0.6), 2L).fired() ? 1 : 0;      // S+=10 > 5 -> FIRE again
        assertEquals(2, windowedFires);                                        // one fire per window
        assertEquals(1L, windowed.firstFire().get().seq());                    // first fire is the window-1 one

        // Same sequence, single window (no ids): the global debounce suppresses the second fire.
        ChangeDetector single = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        int singleFires = 0;
        single.onMatrix(m2(0.0));
        for (double r : new double[]{0.6, 0.0, 0.6, 0.0, 0.6}) {
            singleFires += single.onMatrix(m2(r)).fired() ? 1 : 0;
        }
        assertEquals(1, singleFires);                                          // fires once ever -> would undercount FA
    }

    @Test
    void onMatrixWindowed_WindowIdChange_KeepsPreviousMatrix_NotASessionReset() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        assertNull(d.onMatrix(m2(0.0), 1L));
        d.onMatrix(m2(0.6), 1L);
        // Crossing into window 2 must NOT drop the previous matrix (cf. onSessionBoundary): the
        // transition into the new window is still scored against the last matrix of window 1.
        ChangeSignal firstOfWindow2 = d.onMatrix(m2(0.0), 2L);
        assertNotNull(firstOfWindow2);
        assertEquals(0.6, firstOfWindow2.metrics().weightedChange(), 1e-12);   // |0.0 - 0.6|, prev kept
    }

    @Test
    void onMatrixWindowed_WindowIdChange_LeavesOppositeArmUntouched() {
        // mu = 1.0 makes a flat change of 0 give z = -10, so the lower arm grows while the firing
        // (UPPER) arm stays 0. The re-arm resets only the firing arm, so S- must carry across windows.
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(1.0, 0.1, 0.0));
        assertNull(d.onMatrix(m2(0.9), 1L));
        ChangeSignal w1 = d.onMatrix(m2(0.9), 1L);     // z=-10 -> S- = max(0, 0+10-1) = 9
        assertEquals(9.0, w1.sMinus(), 1e-12);
        ChangeSignal w2 = d.onMatrix(m2(0.9), 2L);     // window change re-arms S+ only; S- = max(0, 9+10-1) = 18
        assertEquals(18.0, w2.sMinus(), 1e-12);
        assertEquals(0.0, w2.sPlus(), 1e-12);
    }

    @Test
    void onSessionBoundary_AfterWindowedCalls_ClearsWindowTracking() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.equity(), new Calibration(0.0, 0.1, 0.0));
        d.onMatrix(m2(0.0), 7L);
        d.onMatrix(m2(0.6), 7L);                 // S+ built up
        d.onSessionBoundary();                    // full reset, including window tracking
        // Same id 7 as before, but the boundary dropped the previous matrix and cleared tracking:
        // this is a fresh first matrix (null), not a continuation.
        assertNull(d.onMatrix(m2(0.9), 7L));
        ChangeSignal s = d.onMatrix(m2(0.9), 8L); // new id: re-arm is a no-op (S+ already 0); change 0 -> S+ stays 0
        assertEquals(0.0, s.sPlus(), 1e-12);
    }
}
