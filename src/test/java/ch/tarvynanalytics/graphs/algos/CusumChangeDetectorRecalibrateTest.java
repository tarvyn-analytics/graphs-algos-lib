package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.model.ChangeSignal;
import ch.tarvynanalytics.graphs.algos.model.FireDirection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GAL-A oracle for {@link ChangeDetector#recalibrate} (Initiative-S H2, numerics spec Q1):
 * recalibrate-then-score must be <strong>bit-identical</strong> to build-fresh-then-score on the
 * post-epoch slice (the recalibrate path and the constructor path are different code, so this is a
 * genuine cross-check), the CUSUM arms must RESET (never inherit the old-baseline accumulation),
 * and the previous matrix / was-fused latch / recovery-gauge buffer must be preserved (a
 * recalibration is not a session boundary). A 2x2 matrix has a single pair, so
 * {@code weighted_change = |Δr|} and density is the single edge indicator — exact control over the
 * series fed into the detector. Hand literals from the numerics spec: old calm (μ₀,σ₀)=(2,1), new
 * epoch (μ₁,σ₁)=(4,1), k=1.5 ({@link DetectorConfig#crypto()}).
 */
class CusumChangeDetectorRecalibrateTest {

    private static double[][] m2(double r) {
        return new double[][]{{1.0, r}, {r, 1.0}};
    }

    /** Off-diagonal walk whose successive |Δr| reproduce the spec's post slice [4.1 3.9 4.0 4.2 3.8 4.0] + a 0.5-σ-excess bar. */
    private static final double[] POST_R = {5.9, 2.0, 6.0, 1.8, 5.6, 1.6, 7.6, 3.6};
    private static final double[] POST_S_PLUS = {0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.5, 0.0};

    @Test
    void recalibrate_CalmPost_BitIdenticalToFresh() {
        // Recalibrated path: calm pre-slice under (2,1), epoch opens to (4,1), then the post slice.
        ChangeDetector recal = ChangeDetectors.create(2, DetectorConfig.crypto(), new Calibration(2.0, 1.0, 0.0));
        assertNull(recal.onMatrix(m2(0.0)));
        recal.onMatrix(m2(2.0));     // c=2.0, z=0
        recal.onMatrix(m2(-0.1));    // c=2.1, z=+0.1
        recal.onMatrix(m2(1.8));     // c=1.9, z=-0.1  -> arms pinned at 0, boundary r=1.8
        recal.recalibrate(new Calibration(4.0, 1.0, 0.0));

        // Independent reference: a from-scratch detector built with the new calibration, primed on
        // the same boundary matrix, fed only the post slice.
        ChangeDetector fresh = ChangeDetectors.create(2, DetectorConfig.crypto(), new Calibration(4.0, 1.0, 0.0));
        assertNull(fresh.onMatrix(m2(1.8)));

        for (int i = 0; i < POST_R.length; i++) {
            ChangeSignal a = recal.onMatrix(m2(POST_R[i]));
            ChangeSignal b = fresh.onMatrix(m2(POST_R[i]));
            assertEquals(b.sPlus(), a.sPlus(), 0.0, "S+ diverges from build-fresh at post bar " + i);
            assertEquals(b.sMinus(), a.sMinus(), 0.0, "S- diverges from build-fresh at post bar " + i);
            assertEquals(POST_S_PLUS[i], a.sPlus(), 0.0, "S+ off the hand trace at post bar " + i);
        }
    }

    @Test
    void recalibrate_StraddleTremor_DoesNotInheritAccumulation() {
        // pre_hot = [2, 5, 5, 5] under (2,1): z = 0,3,3,3 -> S+ = 0, 1.5, 3.0, 4.5 at the boundary.
        // Level 2.0 is unreachable (density in [0,1]) so the gate never interferes.
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.crypto(), new Calibration(2.0, 1.0, 2.0));
        assertNull(d.onMatrix(m2(0.0)));
        d.onMatrix(m2(2.0));                             // c=2.0 -> S+=0
        d.onMatrix(m2(-3.0));                            // c=5.0 -> S+=1.5
        d.onMatrix(m2(2.0));                             // c=5.0 -> S+=3.0
        ChangeSignal boundary = d.onMatrix(m2(-3.0));    // c=5.0 -> S+=4.5
        assertEquals(4.5, boundary.sPlus(), 0.0);

        d.recalibrate(new Calibration(4.0, 1.0, 2.0));

        // Post slice [4.1, 3.9]: reset trajectory is [0, 0]; carry-forward would be [3.1, 1.5] — the
        // regression guard against a future "optimization" that carries the arms forward.
        ChangeSignal p0 = d.onMatrix(m2(1.1));           // c=4.1, z=+0.1
        ChangeSignal p1 = d.onMatrix(m2(-2.8));          // c=3.9, z=-0.1
        assertEquals(0.0, p0.sPlus(), 0.0, "arms must reset on recalibrate, not inherit S+=4.5");
        assertEquals(0.0, p1.sPlus(), 0.0);
    }

    @Test
    void recalibrate_PreservesMatrixLatchAndGauge_AllClearStillLands() {
        // De-fusion-enabled crypto-shaped config: gauge N_g=4, theta=0.75; L_band=0.2+0.75*0.4=0.5,
        // so a 2x2 density of 0 is in-band and 1 is out-of-band.
        DetectorConfig cfg = new DetectorConfig(1.5, 8.0, 99.0, 0.5, 1.0, FireArm.UPPER,
                new DefusionConfig(0.75, 0.75, 4, true));
        ChangeDetector d = ChangeDetectors.create(2, cfg, new Calibration(0.0, 1.0, 0.5, 0.2, 0.4));

        assertNull(d.onMatrix(m2(0.0)));
        d.onMatrix(m2(6.0));                             // c=6, z=6 -> S+=4.5; density 1, no fire yet
        d.onMatrix(m2(0.0));                             // c=6 -> S+=9.0; density 0 closes the gate
        ChangeSignal fusion = d.onMatrix(m2(6.0));       // c=6 -> S+=13.5, density 1 -> FUSION
        assertEquals(FireDirection.FUSION, fusion.fireDirection());

        d.onMatrix(m2(0.4));                             // in-band, gauge 1/1 since the fusion anchor
        d.onMatrix(m2(0.3));                             // in-band, gauge 2/2

        d.recalibrate(new Calibration(0.05, 1.0, 0.5, 0.2, 0.4));

        // (a) previous matrix preserved: the next transition is scored (not a re-priming null) and
        // its change is measured from the pre-recalibrate boundary matrix r=0.3.
        ChangeSignal next = d.onMatrix(m2(0.2));
        assertNotNull(next, "recalibrate must not drop the previous matrix");
        assertEquals(0.1, next.metrics().weightedChange(), 1e-12);
        // (b) gauge buffer preserved: still the honest trailing occupancy (3 in-band pushes since the
        // fusion anchor), not NaN and not a re-anchored 1/1.
        assertEquals(1.0, next.recoveryGauge(), 0.0);
        assertFalse(next.fired(), "window not yet full: the all-clear needs N_g=4 post-fusion samples");

        // (c) latch + buffer together: the 4th in-band push since the fusion fills the window and the
        // all-clear fires. A reset latch would suppress it; a reset buffer would delay it 2 bars.
        ChangeSignal allClear = d.onMatrix(m2(0.1));
        assertEquals(FireDirection.DEFUSION, allClear.fireDirection(),
                "wasFused latch and gauge occupancy must survive a recalibration");
    }

    @Test
    void recalibrate_NewLevelGate_TakesEffect() {
        // Under the old L=0.5 a density-1 bar opens the gate (the control detector fires on the same
        // series); after recalibrating to the unreachable L=2.0 the identical bars must not fire.
        ChangeDetector recal = ChangeDetectors.create(2, DetectorConfig.crypto(), new Calibration(0.0, 1.0, 0.5));
        ChangeDetector control = ChangeDetectors.create(2, DetectorConfig.crypto(), new Calibration(0.0, 1.0, 0.5));
        assertNull(recal.onMatrix(m2(0.0)));
        assertNull(control.onMatrix(m2(0.0)));
        recal.onMatrix(m2(6.0));                          // c=6, z=6 -> S+=4.5
        control.onMatrix(m2(6.0));

        recal.recalibrate(new Calibration(0.0, 1.0, 2.0)); // higher L; arms reset

        ChangeSignal r1 = recal.onMatrix(m2(0.0));         // c=6 -> S+=4.5 (from 0); density 0
        ChangeSignal c1 = control.onMatrix(m2(0.0));       // c=6 -> S+=9.0; density 0, gate closed
        assertFalse(r1.fired());
        assertFalse(c1.fired());

        ChangeSignal r2 = recal.onMatrix(m2(6.0));         // S+=9.0 > h, density 1 < L=2.0 -> no fire
        ChangeSignal c2 = control.onMatrix(m2(6.0));       // S+=13.5 > h, density 1 >= 0.5 -> fires
        assertTrue(r2.sPlus() > DetectorConfig.crypto().h(), "the arm is open — only the gate differs");
        assertFalse(r2.fired(), "the recalibrated L must gate the fire");
        assertTrue(c2.fired(), "control: the same series fires under the old L");
        assertTrue(recal.firstFire().isEmpty());
    }

    @Test
    void recalibrate_NullCalibration_Throws() {
        ChangeDetector d = ChangeDetectors.create(2, DetectorConfig.crypto(), new Calibration(0.0, 1.0, 0.5));
        assertThrows(IllegalArgumentException.class, () -> d.recalibrate(null));
    }
}
