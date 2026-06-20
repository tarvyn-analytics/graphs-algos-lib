package ch.tarvynanalytics.graphs.comparability;

import ch.tarvynanalytics.graphs.comparability.exception.InvalidInputException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EstimatorMatrixTest {

    private static double[][] pair() {
        return new double[][]{
                {1.0, -0.7},
                {-0.7, 1.0}
        };
    }

    @Test
    void of_NameAndOrder_AreExposed() {
        EstimatorMatrix m = EstimatorMatrix.of("pearson", pair());
        assertEquals("pearson", m.name());
        assertEquals(2, m.order());
    }

    @Test
    void of_Magnitude_IsTheAbsoluteCorrelation() {
        EstimatorMatrix m = EstimatorMatrix.of("pearson", pair());
        assertEquals(0.7, m.magnitude(0, 1), "magnitude is |corr|, sign dropped");
        assertEquals(0.7, m.magnitude(1, 0));
    }

    @Test
    void of_CopiesDefensively_SoLaterMutationIsIgnored() {
        double[][] data = pair();
        EstimatorMatrix m = EstimatorMatrix.of("pearson", data);
        data[0][1] = 0.0; // mutate the caller's array after construction
        assertEquals(0.7, m.magnitude(0, 1), "the matrix was copied, not aliased");
    }

    @Test
    void of_NullName_Throws() {
        assertThrows(IllegalArgumentException.class, () -> EstimatorMatrix.of(null, pair()));
    }

    @Test
    void of_NullMatrix_Throws() {
        assertThrows(IllegalArgumentException.class, () -> EstimatorMatrix.of("pearson", null));
    }

    @Test
    void of_BlankName_Throws() {
        assertThrows(InvalidInputException.class, () -> EstimatorMatrix.of("  ", pair()));
    }

    @Test
    void of_NonSquare_Throws() {
        double[][] ragged = {
                {1.0, 0.5, 0.5},
                {0.5, 1.0}
        };
        assertThrows(InvalidInputException.class, () -> EstimatorMatrix.of("pearson", ragged));
    }

    @Test
    void of_NonFinite_Throws() {
        double[][] nan = {
                {1.0, Double.NaN},
                {Double.NaN, 1.0}
        };
        assertThrows(InvalidInputException.class, () -> EstimatorMatrix.of("pearson", nan));
    }
}
