package ch.tarvynanalytics.graphs.algos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** The {@link ChangeSignal} record: accessors and the non-null metrics guard. */
class ChangeSignalTest {

    private static ChangeMetrics metrics() {
        return new ChangeMetrics(0.04, 0.7, 0.1, 2, 0.6, List.of(3, 2));
    }

    @Test
    void constructor_ExposesAllComponents() {
        ChangeMetrics m = metrics();
        ChangeSignal s = new ChangeSignal(7L, m, 9.4, 0.0, true);
        assertEquals(7L, s.seq());
        assertEquals(m, s.metrics());
        assertEquals(9.4, s.sPlus());
        assertEquals(0.0, s.sMinus());
        assertEquals(true, s.fired());
    }

    @Test
    void metrics_AreReachableThroughTheSignal() {
        ChangeSignal s = new ChangeSignal(0L, metrics(), 0.0, 0.0, false);
        assertEquals(0.04, s.metrics().weightedChange());
        assertFalse(s.fired());
    }

    @Test
    void constructor_NullMetrics_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new ChangeSignal(0L, null, 0.0, 0.0, false));
    }
}
