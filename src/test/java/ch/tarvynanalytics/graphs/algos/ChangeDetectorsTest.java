package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Calibration arithmetic and factory guards (spec §2.1, §2.2). */
class ChangeDetectorsTest {

    @Test
    void calibrate_PopulationStdDev_NotSample() {
        // change [0,2] => mu=1, pstdev=sqrt(((0-1)^2+(2-1)^2)/2)=1.0 (sample stdev would be sqrt(2)).
        Calibration cal = ChangeDetectors.calibrate(
                new double[]{0.0, 2.0}, new double[]{0.5, 0.5}, DetectorConfig.equity());
        assertEquals(1.0, cal.mu(), 1e-12);
        assertEquals(1.0, cal.sigma(), 1e-12);
    }

    @Test
    void calibrate_ConstantCalmChange_FloorsSigmaToEpsilon() {
        Calibration cal = ChangeDetectors.calibrate(
                new double[]{0.1, 0.1, 0.1}, new double[]{0.3, 0.3, 0.3}, DetectorConfig.crypto());
        assertEquals(1.0, cal.sigma(), 1e-12);   // epsilonSigma = 1.0
    }

    @Test
    void calibrate_NearestRankPercentile_p90AndP99() {
        double[] density = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        double[] change = density.clone();
        assertEquals(9.0, ChangeDetectors.calibrate(change, density, DetectorConfig.equity()).level(), 1e-12);
        assertEquals(10.0, ChangeDetectors.calibrate(change, density, DetectorConfig.crypto()).level(), 1e-12);
    }

    @Test
    void calibrate_DropsNonFiniteBeforeStatistics() {
        Calibration cal = ChangeDetectors.calibrate(
                new double[]{0.0, Double.NaN, 2.0, Double.POSITIVE_INFINITY},
                new double[]{0.5, 0.5, 0.5, 0.5}, DetectorConfig.equity());
        assertEquals(1.0, cal.mu(), 1e-12);   // mean of the two finite values [0,2]
    }

    @Test
    void calibrate_AllNonFiniteChange_Throws() {
        assertThrows(InvalidInputException.class, () -> ChangeDetectors.calibrate(
                new double[]{Double.NaN, Double.NaN}, new double[]{0.5, 0.5}, DetectorConfig.equity()));
    }

    @Test
    void calibrate_EmptyChange_Throws() {
        assertThrows(InvalidInputException.class, () -> ChangeDetectors.calibrate(
                new double[0], new double[]{0.5}, DetectorConfig.equity()));
    }

    @Test
    void calibrate_AllNonFiniteDensity_Throws() {
        assertThrows(InvalidInputException.class, () -> ChangeDetectors.calibrate(
                new double[]{0.1, 0.2}, new double[]{Double.NaN, Double.NaN}, DetectorConfig.equity()));
    }

    @Test
    void calibrate_NullConfig_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeDetectors.calibrate(new double[]{0.1}, new double[]{0.5}, null));
    }

    @Test
    void calibrate_NullSlice_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeDetectors.calibrate(null, new double[]{0.5}, DetectorConfig.equity()));
    }

    @Test
    void create_ValidArguments_ReturnsDetector() {
        ChangeDetector d = ChangeDetectors.create(3, DetectorConfig.crypto(), new Calibration(0.0, 0.1, 0.9));
        assertNotNull(d);
    }

    @Test
    void create_NullConfig_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeDetectors.create(3, null, new Calibration(0.0, 0.1, 0.9)));
    }

    @Test
    void create_NullCalibration_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ChangeDetectors.create(3, DetectorConfig.crypto(), null));
    }

    @Test
    void create_NegativeOrder_Throws() {
        assertThrows(InvalidInputException.class,
                () -> ChangeDetectors.create(-1, DetectorConfig.crypto(), new Calibration(0.0, 0.1, 0.9)));
    }

    @Test
    void nearestRankPercentile_EmptySeries_IsNaN() {
        assertTrue(Double.isNaN(ChangeDetectors.nearestRankPercentile(new double[0], 90.0)));
    }

    @Test
    void nearestRankPercentile_NearestRank_NotInterpolated() {
        double[] v = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(9.0, ChangeDetectors.nearestRankPercentile(v, 90.0), 1e-12);
        assertEquals(10.0, ChangeDetectors.nearestRankPercentile(v, 99.0), 1e-12);
    }

    @Test
    void nearestRankPercentile_ClampsRankAtBothEnds() {
        double[] v = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertEquals(1.0, ChangeDetectors.nearestRankPercentile(v, 0.0), 1e-12);    // ceil(0)=0 -> clamp to 1
        assertEquals(10.0, ChangeDetectors.nearestRankPercentile(v, 100.0), 1e-12); // ceil(n)=n
    }
}
