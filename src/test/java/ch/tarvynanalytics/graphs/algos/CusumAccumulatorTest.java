package ch.tarvynanalytics.graphs.algos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Oracle A3 (§8.1 of the S3 spec) for the two-sided CUSUM recursion: ground truth is the
 * hand-computed trace, never the implementation's output. With {@code mu=0, sigma=1} the
 * fed value <em>is</em> the standardized {@code z}, so the recursion is exercised directly.
 */
class CusumAccumulatorTest {

    @Test
    void step_FusionSequence_MatchesHandComputedUpperArmTrace() {
        // z = [.2,-.1,.3,3,3,3,0], k=1.0  =>  S+ = [0,0,0,2,4,6,5]  (crosses h=5 at t=5)
        CusumAccumulator c = new CusumAccumulator(0.0, 1.0, 1.0);
        double[] z = {0.2, -0.1, 0.3, 3, 3, 3, 0};
        double[] expectedSPlus = {0, 0, 0, 2, 4, 6, 5};
        for (int t = 0; t < z.length; t++) {
            c.step(z[t]);
            assertEquals(expectedSPlus[t], c.sPlus(), 1e-12, "S+ at t=" + t);
            assertEquals(0.0, c.sMinus(), 1e-12, "S- at t=" + t);
        }
    }

    @Test
    void step_DefusionSequence_MatchesHandComputedLowerArmMirror() {
        // z = [-3,-3,-3], k=1.0  =>  S- = [2,4,6], S+ = [0,0,0]  (the symmetric mirror)
        CusumAccumulator c = new CusumAccumulator(0.0, 1.0, 1.0);
        double[] z = {-3, -3, -3};
        double[] expectedSMinus = {2, 4, 6};
        for (int t = 0; t < z.length; t++) {
            c.step(z[t]);
            assertEquals(expectedSMinus[t], c.sMinus(), 1e-12, "S- at t=" + t);
            assertEquals(0.0, c.sPlus(), 1e-12, "S+ at t=" + t);
        }
    }

    @Test
    void resetArm_Upper_ZeroesUpperArmOnly() {
        CusumAccumulator c = new CusumAccumulator(0.0, 1.0, 1.0);
        c.step(3);
        c.step(3);   // S+ = 4
        assertEquals(4.0, c.sPlus(), 1e-12);
        c.resetArm(FireArm.UPPER);
        assertEquals(0.0, c.sPlus(), 1e-12);
    }

    @Test
    void resetArm_Lower_ZeroesLowerArmOnly() {
        CusumAccumulator c = new CusumAccumulator(0.0, 1.0, 1.0);
        c.step(-3);
        c.step(-3);  // S- = 4
        assertEquals(4.0, c.sMinus(), 1e-12);
        c.resetArm(FireArm.LOWER);
        assertEquals(0.0, c.sMinus(), 1e-12);
    }

    @Test
    void reset_ZeroesBothArms() {
        CusumAccumulator c = new CusumAccumulator(0.0, 1.0, 1.0);
        c.step(3);
        c.step(-9);
        c.reset();
        assertEquals(0.0, c.sPlus(), 1e-12);
        assertEquals(0.0, c.sMinus(), 1e-12);
    }
}
