package ch.tarvynanalytics.graphs.algos;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Oracle A1 for the {@link RecoveryGauge}: ground
 * truth is the hand-computed trailing-occupancy trace, never the implementation's output. A small window
 * {@code N_g=4} makes every fraction hand-verifiable.
 */
class RecoveryGaugeTest {

    @Test
    void value_EmptyBeforeFirstPush_IsNaN() {
        RecoveryGauge g = new RecoveryGauge(4);
        assertTrue(Double.isNaN(g.value()));
        assertFalse(g.windowFull());
    }

    @Test
    void pushSequence_MatchesHandComputedOccupancyTrace() {
        // inBand = [F,T,T,T,F,T] (densities [1,0,0,0,1,0] with L_band=0.5), N_g=4:
        //   value = 0/1, 1/2, 2/3, 3/4, 3/4, 3/4 ; windowFull from the 4th push.
        RecoveryGauge g = new RecoveryGauge(4);
        boolean[] inBand = {false, true, true, true, false, true};
        double[] expected = {0.0, 0.5, 2.0 / 3.0, 0.75, 0.75, 0.75};
        boolean[] full = {false, false, false, true, true, true};
        for (int t = 0; t < inBand.length; t++) {
            g.push(inBand[t]);
            assertEquals(expected[t], g.value(), 1e-12, "value at push " + t);
            assertEquals(full[t], g.windowFull(), "windowFull at push " + t);
        }
    }

    @Test
    void reset_ReturnsToEmptyState() {
        RecoveryGauge g = new RecoveryGauge(4);
        for (int i = 0; i < 6; i++) {
            g.push(true);
        }
        assertEquals(1.0, g.value(), 1e-12);
        assertTrue(g.windowFull());
        g.reset();
        assertTrue(Double.isNaN(g.value()));
        assertFalse(g.windowFull());
        g.push(false);
        assertEquals(0.0, g.value(), 1e-12);   // fresh window after reset, no stale entries
    }

    @Test
    void value_SlidesWindow_DropsOldestInBand() {
        // Fill in-band (value 1.0), then push out-of-band: oldest in-band leaves, fraction falls 3/4 each step.
        RecoveryGauge g = new RecoveryGauge(4);
        for (int i = 0; i < 4; i++) {
            g.push(true);
        }
        assertEquals(1.0, g.value(), 1e-12);
        g.push(false);
        assertEquals(0.75, g.value(), 1e-12);  // window [T,T,T,F]
        g.push(false);
        assertEquals(0.5, g.value(), 1e-12);   // window [T,T,F,F]
    }
}
