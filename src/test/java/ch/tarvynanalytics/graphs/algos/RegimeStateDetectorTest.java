package ch.tarvynanalytics.graphs.algos;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.model.RegimeSignal;
import ch.tarvynanalytics.graphs.algos.model.RegimeState;
import ch.tarvynanalytics.graphs.algos.model.RegimeTransition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * H2R-2 oracle for the level+hysteresis {@link RegimeStateDetector} (design
 * {@code initiative-s-h2-lifecycle-revision-design.md} §4). Correctness is proven against an
 * <strong>independent naive batch reference</strong> ({@link #naiveTransitions}) — a straight loop
 * with an explicit regime flag and a single pending-run counter, a different mental model from the
 * streaming two-run state machine — and against <strong>hand-computed onset/offset indices</strong>,
 * never against the detector's own output. The Schmitt trigger is integer counting + comparisons, so
 * every assertion is EXACT (tolerance 0): the transition <em>indices</em> and <em>states</em> are
 * literals. Defaults from the crypto tuning: {@code hi=0.85, lo=0.45, confirmBars=3}.
 */
class RegimeStateDetectorTest {

    private static RegimeConfig cfg() {
        return new RegimeConfig(0.85, 0.45, 3);
    }

    /** Feeds a level series through the streaming detector and returns every emitted signal. */
    private static List<RegimeSignal> feed(RegimeDetector d, double... levels) {
        List<RegimeSignal> out = new ArrayList<>(levels.length);
        for (double level : levels) {
            out.add(d.step(level));
        }
        return out;
    }

    /**
     * Independent naive reference: batch-scan the levels with one regime flag and one pending-run
     * counter, emitting {@code (index, transition)} for every crossing. NaN/Inf samples are skipped
     * whole (no advance, no reset). This is deliberately a different structure from the detector.
     */
    private static List<int[]> naiveTransitions(double hi, double lo, int confirm, double... levels) {
        List<int[]> events = new ArrayList<>();
        boolean fused = false;
        int run = 0;
        for (int i = 0; i < levels.length; i++) {
            double x = levels[i];
            if (Double.isNaN(x) || Double.isInfinite(x)) {
                continue;
            }
            boolean pastMark = fused ? (x <= lo) : (x >= hi);
            run = pastMark ? run + 1 : 0;
            if (run >= confirm) {
                events.add(new int[]{i, fused ? RegimeTransition.CALM_ONSET.ordinal()
                        : RegimeTransition.FUSION_ONSET.ordinal()});
                fused = !fused;
                run = 0;
            }
        }
        return events;
    }

    /** The first index whose signal carries the given transition, or -1. */
    private static int indexOf(List<RegimeSignal> signals, RegimeTransition t) {
        for (RegimeSignal s : signals) {
            if (s.transition() == t) {
                return (int) s.seq();
            }
        }
        return -1;
    }

    // ---- hand-literal cases (design §4) ----

    @Test
    void upCross_HiForConfirmBars_FiresFusionOnset() {
        List<RegimeSignal> s = feed(RegimeDetectors.create(cfg()), 0.5, 0.9, 0.9, 0.9);
        assertEquals(3, indexOf(s, RegimeTransition.FUSION_ONSET));   // the 3rd consecutive >= hi
        assertEquals(RegimeState.FUSED, s.get(3).state());
        assertTrue(s.get(3).crossed());
        assertFalse(s.get(2).crossed());                              // 2 in a row is not yet a regime
    }

    @Test
    void upCross_HiBrokenBeforeConfirm_DoesNotFireUntilRunCompletes() {
        // The first run of 2 is broken by 0.5 (persistence resets); onset only on the later full run.
        List<RegimeSignal> s = feed(RegimeDetectors.create(cfg()), 0.9, 0.9, 0.5, 0.9, 0.9, 0.9);
        assertEquals(5, indexOf(s, RegimeTransition.FUSION_ONSET));
        assertEquals(RegimeState.CALM, s.get(4).state());            // still calm after the reset run
    }

    @Test
    void downCross_LoForConfirmBars_FiresCalmOnset() {
        RegimeDetector d = RegimeDetectors.create(cfg());
        feed(d, 0.9, 0.9, 0.9);                                      // drive to FUSED (onset at idx 2)
        List<RegimeSignal> s = feed(d, 0.4, 0.4, 0.4);              // three at/below lo -> all-clear
        assertEquals(RegimeTransition.CALM_ONSET, s.get(2).transition());
        assertEquals(RegimeState.CALM, s.get(2).state());
        assertEquals(5, s.get(2).seq());                            // seq is the global stream index
    }

    @Test
    void hysteresis_MidBandDoesNotFlipFlop() {
        RegimeDetector d = RegimeDetectors.create(cfg());
        feed(d, 0.9, 0.9, 0.9);                                     // FUSED
        // A long run strictly between lo and hi never completes the down-run: stays FUSED (no metronome).
        for (RegimeSignal sig : feed(d, 0.6, 0.7, 0.6, 0.7, 0.8, 0.5, 0.6)) {
            assertEquals(RegimeTransition.NONE, sig.transition());
            assertEquals(RegimeState.FUSED, sig.state());
        }
    }

    @Test
    void nanLevel_CarriesRegime_NoCounterAdvance() {
        RegimeDetector d = RegimeDetectors.create(cfg());
        feed(d, 0.9, 0.9, 0.9);                                     // FUSED
        List<RegimeSignal> s = feed(d, Double.NaN, 0.4, 0.4, 0.4);  // gap then three at/below lo
        assertEquals(RegimeTransition.NONE, s.get(0).transition()); // the gap changes nothing
        assertEquals(RegimeState.FUSED, s.get(0).state());
        assertEquals(RegimeTransition.CALM_ONSET, s.get(3).transition()); // fires on the 3rd finite, not early
    }

    @Test
    void gapMidRun_DoesNotBreakPersistence() {
        RegimeDetector d = RegimeDetectors.create(cfg());
        feed(d, 0.9, 0.9, 0.9);                                     // FUSED
        // 0.4, gap, 0.4, 0.4 -> the gap neither advances nor resets; the down-run of 3 completes.
        List<RegimeSignal> s = feed(d, 0.4, Double.POSITIVE_INFINITY, 0.4, 0.4);
        assertEquals(RegimeTransition.NONE, s.get(1).transition());
        assertEquals(RegimeTransition.CALM_ONSET, s.get(3).transition());
    }

    @Test
    void coldStart_BeginsCalm_NoSpuriousOnset() {
        List<RegimeSignal> s = feed(RegimeDetectors.create(cfg()), 0.99);
        assertEquals(RegimeState.CALM, s.get(0).state());           // one elevated bar is not a regime
        assertEquals(RegimeTransition.NONE, s.get(0).transition());
    }

    @Test
    void reset_ReturnsToCalm() {
        RegimeDetector d = RegimeDetectors.create(cfg());
        feed(d, 0.9, 0.9, 0.9);                                     // FUSED
        d.reset();
        RegimeSignal after = d.step(0.4);
        assertEquals(RegimeState.CALM, after.state());              // reset -> CALM, runs zeroed
        assertEquals(0, after.seq());                               // and the sample index restarts
        assertEquals(RegimeTransition.NONE, after.transition());
    }

    @Test
    void inclusiveMarks_ExactlyAtHiAndLoCount() {
        RegimeDetector d = RegimeDetectors.create(cfg());
        List<RegimeSignal> up = feed(d, 0.85, 0.85, 0.85);          // level == hi is "at/above"
        assertEquals(RegimeTransition.FUSION_ONSET, up.get(2).transition());
        List<RegimeSignal> down = feed(d, 0.45, 0.45, 0.45);        // level == lo is "at/below"
        assertEquals(RegimeTransition.CALM_ONSET, down.get(2).transition());
    }

    // ---- independent-reference cross-check over a multi-cycle stream ----

    @Test
    void multiCycleStream_MatchesNaiveReferenceAndHandAnchors() {
        double[] levels = {
                0.5, 0.9, 0.9, 0.9,        // FUSION_ONSET @ 3
                0.7, 0.9, 0.4, 0.4, 0.4,   // CALM_ONSET   @ 8
                0.6, 0.3, 0.4, 0.4, 0.5,   // calm noise, no crossing
                0.9, 0.86, 0.88,           // FUSION_ONSET @ 16
                0.2, 0.44, 0.44,           // CALM_ONSET   @ 19
                0.44, 0.9                  // calm tail
        };
        List<RegimeSignal> streaming = feed(RegimeDetectors.create(cfg()), levels);

        // Independent batch reference agrees on every transition (index + kind), in order.
        List<int[]> naive = naiveTransitions(0.85, 0.45, 3, levels);
        List<int[]> fromStream = new ArrayList<>();
        for (RegimeSignal s : streaming) {
            if (s.crossed()) {
                fromStream.add(new int[]{(int) s.seq(), s.transition().ordinal()});
            }
        }
        assertEquals(naive.size(), fromStream.size());
        for (int i = 0; i < naive.size(); i++) {
            assertEquals(naive.get(i)[0], fromStream.get(i)[0]);    // same index
            assertEquals(naive.get(i)[1], fromStream.get(i)[1]);    // same transition kind
        }

        // Hand-computed anchors (so the cross-check is not purely self-referential).
        assertEquals(4, fromStream.size());                        // two full fused-regime cycles
        assertEquals(3, indexOf(streaming, RegimeTransition.FUSION_ONSET));
        assertEquals(8, streaming.stream().filter(RegimeSignal::crossed).toList().get(1).seq());
        assertEquals(RegimeState.CALM, streaming.get(levels.length - 1).state()); // ends calm (0.9 -> up-run 1)
    }

    @Test
    void everySampleYieldsSignalWithMonotonicSeq() {
        List<RegimeSignal> s = feed(RegimeDetectors.create(cfg()), 0.1, 0.2, 0.3, 0.4);
        for (int i = 0; i < s.size(); i++) {
            assertNotNull(s.get(i));
            assertEquals(i, s.get(i).seq());
            assertEquals(0.1 * (i + 1), s.get(i).level(), 1e-12);  // the level fed is echoed back
        }
    }

    // ---- config + factory validation ----

    @Test
    void config_LoNotBelowHi_Throws() {
        assertThrows(InvalidInputException.class, () -> new RegimeConfig(0.5, 0.5, 3));  // equal marks
        assertThrows(InvalidInputException.class, () -> new RegimeConfig(0.4, 0.6, 3));  // lo > hi
    }

    @Test
    void config_NonPositiveConfirmBars_Throws() {
        assertThrows(InvalidInputException.class, () -> new RegimeConfig(0.85, 0.45, 0));
    }

    @Test
    void config_NonFiniteMarks_Throw() {
        assertThrows(InvalidInputException.class, () -> new RegimeConfig(Double.NaN, 0.45, 3));
        assertThrows(InvalidInputException.class, () -> new RegimeConfig(0.85, Double.NEGATIVE_INFINITY, 3));
    }

    @Test
    void cryptoDefaults_AreTheSettledMarks() {
        RegimeConfig c = RegimeConfig.crypto();
        assertEquals(0.85, c.hi(), 0.0);
        assertEquals(0.45, c.lo(), 0.0);
        assertEquals(3, c.confirmBars());
    }

    @Test
    void factory_NullConfig_Throws() {
        assertThrows(IllegalArgumentException.class, () -> RegimeDetectors.create(null));
    }

    @Test
    void signal_NullState_OrTransition_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new RegimeSignal(0, 0.5, null, RegimeTransition.NONE));
        assertThrows(IllegalArgumentException.class,
                () -> new RegimeSignal(0, 0.5, RegimeState.CALM, null));
    }
}
