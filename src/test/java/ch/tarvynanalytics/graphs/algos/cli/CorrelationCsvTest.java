package ch.tarvynanalytics.graphs.algos.cli;

import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CorrelationCsvTest {

    @TempDir
    Path dir;

    private Path write(String content) throws IOException {
        Path file = dir.resolve("matrix.csv");
        Files.writeString(file, content);
        return file;
    }

    @Test
    void read_HeaderRow_UsesItAsLabels() throws IOException {
        CorrelationCsv.Parsed parsed = CorrelationCsv.read(write("a,b\n1.0,0.8\n0.8,1.0\n"));

        assertArrayEquals(new String[] {"a", "b"}, parsed.labels());
        assertArrayEquals(new double[] {1.0, 0.8}, parsed.matrix()[0]);
        assertArrayEquals(new double[] {0.8, 1.0}, parsed.matrix()[1]);
    }

    @Test
    void read_NoHeader_GeneratesIndexLabels() throws IOException {
        CorrelationCsv.Parsed parsed = CorrelationCsv.read(write("1.0,0.8\n0.8,1.0\n"));

        assertArrayEquals(new String[] {"0", "1"}, parsed.labels());
        assertEquals(2, parsed.matrix().length);
    }

    @Test
    void read_DottedTickerLabels_ArePreserved() throws IOException {
        CorrelationCsv.Parsed parsed = CorrelationCsv.read(write("BF.B,BRK.B\n1.0,0.3\n0.3,1.0\n"));

        assertArrayEquals(new String[] {"BF.B", "BRK.B"}, parsed.labels());
    }

    @Test
    void read_BlankLines_AreSkipped() throws IOException {
        CorrelationCsv.Parsed parsed = CorrelationCsv.read(write("a,b\n\n1.0,0.8\n   \n0.8,1.0\n"));

        assertArrayEquals(new String[] {"a", "b"}, parsed.labels());
        assertEquals(2, parsed.matrix().length);
    }

    @Test
    void read_NonNumericValue_ThrowsWithTheOffendingValue() throws IOException {
        Path file = write("a,b\n1.0,oops\noops,1.0\n");

        InvalidInputException ex = assertThrows(InvalidInputException.class, () -> CorrelationCsv.read(file));
        assertTrue(ex.getMessage().contains("[oops]"), ex.getMessage());
    }

    @Test
    void read_EmptyFile_Throws() throws IOException {
        Path file = write("\n   \n");

        assertThrows(InvalidInputException.class, () -> CorrelationCsv.read(file));
    }
}
