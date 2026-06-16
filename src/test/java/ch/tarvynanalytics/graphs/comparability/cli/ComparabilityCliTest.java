package ch.tarvynanalytics.graphs.comparability.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComparabilityCliTest {

    // A triangle K3 (all off-diagonals strong): a complete comparability graph, 3! = 6 orientations.
    private static final String TRIANGLE = """
            a,b,c
            1.0,0.9,0.9
            0.9,1.0,0.9
            0.9,0.9,1.0
            """;

    // The 5-cycle C5 (edges 0-1-2-3-4-0 strong, chords weak): the classic odd hole, not comparability.
    private static final String C5 = """
            A,B,C,D,E
            1.0,0.9,0.1,0.1,0.9
            0.9,1.0,0.9,0.1,0.1
            0.1,0.9,1.0,0.9,0.1
            0.1,0.1,0.9,1.0,0.9
            0.9,0.1,0.1,0.9,1.0
            """;

    // A single pair at 0.6: an edge at the default threshold 0.5, no edge at 0.7.
    private static final String PAIR_06 = """
            x,y
            1.0,0.6
            0.6,1.0
            """;

    @TempDir
    Path dir;

    private ByteArrayOutputStream outBuf;
    private ByteArrayOutputStream errBuf;
    private PrintStream out;
    private PrintStream err;

    @BeforeEach
    void setUp() {
        outBuf = new ByteArrayOutputStream();
        errBuf = new ByteArrayOutputStream();
        out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
        err = new PrintStream(errBuf, true, StandardCharsets.UTF_8);
    }

    private Path csv(String name, String content) {
        try {
            Path file = dir.resolve(name);
            Files.writeString(file, content);
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private int run(String... args) {
        return ComparabilityCli.run(args, out, err);
    }

    private String out() {
        return outBuf.toString(StandardCharsets.UTF_8);
    }

    private String err() {
        return errBuf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void run_ComparabilityGraph_ReportsYesAndOrientationCount() {
        int code = run(csv("k3.csv", TRIANGLE).toString());

        assertEquals(0, code);
        assertTrue(out().contains("comparability: YES"), out());
        assertTrue(out().contains("transitive orientations: 6"), out());
    }

    @Test
    void run_OddHole_ReportsNoAndTheObstructingCycle() {
        int code = run(csv("c5.csv", C5).toString());

        assertEquals(0, code);
        assertTrue(out().contains("comparability: NO"), out());
        assertTrue(out().contains("obstructing odd cycle (length 5)"), out());
        assertTrue(out().contains("weakest edge:"), out());
    }

    @Test
    void run_NoThresholdGiven_DefaultsToHalf() {
        int code = run(csv("pair.csv", PAIR_06).toString());

        // 0.6 > 0.5 -> one edge, a single edge (K2) is comparability with 2 orientations.
        assertEquals(0, code);
        assertTrue(out().contains("threshold:     0.5"), out());
        assertTrue(out().contains("edges:         1"), out());
        assertTrue(out().contains("transitive orientations: 2"), out());
    }

    @Test
    void run_ThresholdAboveTheEdge_DropsTheEdge() {
        int code = run(csv("pair.csv", PAIR_06).toString(), "--threshold", "0.7");

        // 0.6 is not > 0.7 -> no edge; the empty graph is trivially comparability (1 orientation).
        assertEquals(0, code);
        assertTrue(out().contains("edges:         0"), out());
        assertTrue(out().contains("transitive orientations: 1"), out());
    }

    @Test
    void run_JsonFlag_EmitsJson() {
        int code = run(csv("k3.csv", TRIANGLE).toString(), "--json");

        assertEquals(0, code);
        assertTrue(out().startsWith("{"), out());
        assertTrue(out().contains("\"comparability\":true"), out());
    }

    @Test
    void run_MissingFile_ReturnsInputError() {
        int code = run(dir.resolve("absent.csv").toString());

        assertEquals(1, code);
        assertTrue(err().contains("error: cannot read file"), err());
    }

    @Test
    void run_NonNumericCsv_ReturnsInputError() {
        int code = run(csv("bad.csv", "a,b\n1.0,oops\noops,1.0\n").toString());

        assertEquals(1, code);
        assertTrue(err().contains("[oops]"), err());
    }

    @Test
    void run_LabelCountMismatch_ReturnsInputError() {
        // 3 header labels but a 2x2 matrix: GraphInput rejects it.
        int code = run(csv("ragged.csv", "a,b,c\n1.0,0.9\n0.9,1.0\n").toString());

        assertEquals(1, code);
        assertTrue(err().startsWith("error:"), err());
    }

    @Test
    void run_NoArguments_ReturnsUsageError() {
        int code = run();

        assertEquals(2, code);
        assertTrue(err().contains("missing <csv-path>"), err());
    }

    @Test
    void run_UnknownOption_ReturnsUsageError() {
        int code = run("--bogus");

        assertEquals(2, code);
        assertTrue(err().contains("unknown option [--bogus]"), err());
    }

    @Test
    void run_ExtraPositionalArgument_ReturnsUsageError() {
        int code = run(csv("k3.csv", TRIANGLE).toString(), "extra.csv");

        assertEquals(2, code);
        assertTrue(err().contains("unexpected extra argument"), err());
    }

    @Test
    void run_ThresholdWithoutValue_ReturnsUsageError() {
        int code = run(csv("k3.csv", TRIANGLE).toString(), "--threshold");

        assertEquals(2, code);
        assertTrue(err().contains("requires a value"), err());
    }

    @Test
    void run_InvalidThreshold_ReturnsUsageError() {
        int code = run(csv("k3.csv", TRIANGLE).toString(), "-t", "abc");

        assertEquals(2, code);
        assertTrue(err().contains("invalid threshold, got [abc]"), err());
    }

    @Test
    void run_HelpFlag_PrintsUsageAndSucceeds() {
        int code = run("--help");

        assertEquals(0, code);
        assertTrue(out().contains("Usage: comparability"), out());
    }
}
