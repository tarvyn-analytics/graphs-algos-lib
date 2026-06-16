package ch.tarvynanalytics.graphs.comparability.cli;

import ch.tarvynanalytics.graphs.comparability.exception.InvalidInputException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads a correlation-matrix CSV into a {@code double[][]} plus vertex labels for
 * the command-line interface. Hand-rolled (no CSV dependency) — the supported
 * shape is simple: an optional header row of labels, then one row of
 * comma-separated values per vertex.
 *
 * <p>The header is auto-detected: if the first cell of the first row does not
 * parse as a number it is taken to be a labels row; otherwise the file is treated
 * as headerless and index labels ({@code "0".."n-1"}) are generated. Squareness
 * and finiteness are not checked here — that is left to
 * {@link ch.tarvynanalytics.graphs.comparability.GraphInput#fromCorrelation}.</p>
 */
final class CorrelationCsv {

    private CorrelationCsv() {
    }

    /**
     * @param labels one label per vertex (parsed header, or generated indices)
     * @param matrix the parsed correlation values, row by row
     */
    record Parsed(String[] labels, double[][] matrix) {
    }

    /**
     * Reads and parses the CSV at {@code path}.
     *
     * @param path the CSV file
     * @return the parsed labels and matrix
     * @throws IOException           if the file cannot be read
     * @throws InvalidInputException if the file is empty or holds a non-numeric value
     */
    static Parsed read(Path path) throws IOException {
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (!line.isBlank()) {
                rows.add(line.strip());
            }
        }
        if (rows.isEmpty()) {
            throw new InvalidInputException("correlation CSV is empty: [" + path + "]");
        }

        String[] firstCells = split(rows.get(0));
        boolean hasHeader = !isNumeric(firstCells[0]);
        int dataStart = hasHeader ? 1 : 0;
        int n = rows.size() - dataStart;

        double[][] matrix = new double[n][];
        for (int r = 0; r < n; r++) {
            String[] cells = split(rows.get(dataStart + r));
            double[] row = new double[cells.length];
            for (int c = 0; c < cells.length; c++) {
                row[c] = parse(cells[c]);
            }
            matrix[r] = row;
        }

        String[] labels = hasHeader ? firstCells : indexLabels(n);
        return new Parsed(labels, matrix);
    }

    private static String[] split(String line) {
        String[] parts = line.split(",", -1);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].strip();
        }
        return parts;
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static double parse(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new InvalidInputException("correlation CSV has a non-numeric value, got [" + s + "]");
        }
    }

    private static String[] indexLabels(int n) {
        String[] labels = new String[n];
        for (int i = 0; i < n; i++) {
            labels[i] = Integer.toString(i);
        }
        return labels;
    }
}
