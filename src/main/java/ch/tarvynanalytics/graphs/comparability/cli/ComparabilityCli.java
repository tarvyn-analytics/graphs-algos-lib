package ch.tarvynanalytics.graphs.comparability.cli;

import ch.tarvynanalytics.graphs.comparability.ComparabilityAnalyzer;
import ch.tarvynanalytics.graphs.comparability.GraphInput;
import ch.tarvynanalytics.graphs.comparability.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.comparability.export.JsonExporter;
import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Command-line entry point: analyse a correlation-matrix CSV for comparability
 * (transitive orientability).
 *
 * <pre>{@code
 * java -jar graphs-comparability-lib.jar <csv-path> [--threshold <t>] [--json]
 * }</pre>
 *
 * <p>An undirected edge is created for every pair whose
 * {@code |correlation| > |threshold|} (threshold default {@code 0.5}). The verdict
 * and details are printed to standard output (or the full result as JSON with
 * {@code --json}). Exit codes are <em>result-only</em>: {@code 0} when the
 * analysis ran (whatever the verdict), {@code 2} for a usage error and {@code 1}
 * for an input/IO error; the comparability verdict is read from the output, not
 * the exit code.</p>
 */
public final class ComparabilityCli {

    private static final double DEFAULT_THRESHOLD = 0.5;

    private ComparabilityCli() {
    }

    /**
     * Process entry point; delegates to {@link #run} and maps the result to the
     * process exit code.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Runs the CLI against explicit streams (so it is testable without exiting the
     * JVM).
     *
     * @param args command-line arguments
     * @param out  stream for the report / JSON / help text
     * @param err  stream for error and usage messages
     * @return the exit code: {@code 0} success, {@code 2} usage error, {@code 1} input/IO error
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        String path = null;
        double threshold = DEFAULT_THRESHOLD;
        boolean json = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    printUsage(out);
                    return 0;
                }
                case "--json" -> json = true;
                case "-t", "--threshold" -> {
                    if (i + 1 >= args.length) {
                        err.println("error: " + arg + " requires a value");
                        printUsage(err);
                        return 2;
                    }
                    String value = args[++i];
                    try {
                        threshold = Double.parseDouble(value);
                    } catch (NumberFormatException e) {
                        err.println("error: invalid threshold, got [" + value + "]");
                        return 2;
                    }
                }
                default -> {
                    if (arg.startsWith("-")) {
                        err.println("error: unknown option [" + arg + "]");
                        printUsage(err);
                        return 2;
                    }
                    if (path != null) {
                        err.println("error: unexpected extra argument [" + arg + "]");
                        printUsage(err);
                        return 2;
                    }
                    path = arg;
                }
            }
        }

        if (path == null) {
            err.println("error: missing <csv-path>");
            printUsage(err);
            return 2;
        }

        CorrelationCsv.Parsed parsed;
        try {
            parsed = CorrelationCsv.read(Path.of(path));
        } catch (IOException e) {
            err.println("error: cannot read file [" + path + "]: " + e.getMessage());
            return 1;
        } catch (InvalidInputException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }

        AnalysisResult result;
        try {
            result = ComparabilityAnalyzer.analyze(
                    GraphInput.fromCorrelation(parsed.matrix(), threshold, parsed.labels()));
        } catch (InvalidInputException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }

        if (json) {
            out.println(JsonExporter.toJson(result));
        } else {
            printSummary(out, path, threshold, parsed.labels(), result);
        }
        return 0;
    }

    private static void printSummary(PrintStream out, String path, double threshold,
                                     String[] labels, AnalysisResult result) {
        out.println("file:          " + path);
        out.println("vertices:      " + result.inputGraph().nodes().size());
        out.println("threshold:     " + threshold);
        out.println("edges:         " + result.inputGraph().edges().size());
        if (result.isComparability()) {
            out.println("comparability: YES");
            out.println("transitive orientations: " + result.transitiveOrientationCount());
            out.println("decomposition levels:    " + result.levels().size());
        } else {
            out.println("comparability: NO");
            result.failure().ifPresent(f -> {
                out.println("obstructing odd cycle (length " + f.length() + "): "
                        + String.join(" - ", f.nodeLabels()));
                int source = f.weakestEdgeSource();
                int target = f.weakestEdgeTarget();
                if (source >= 0 && target >= 0) {
                    String corr = Double.isFinite(f.weakestCorrelation())
                            ? String.format(Locale.ROOT, "%.4f", f.weakestCorrelation())
                            : "n/a";
                    out.println("weakest edge:  " + labels[source] + " - " + labels[target]
                            + " (correlation " + corr + ")");
                }
            });
        }
    }

    private static void printUsage(PrintStream s) {
        s.println("Usage: comparability <csv-path> [--threshold <t>] [--json]");
        s.println();
        s.println("  Reads a correlation-matrix CSV (optional header row of labels, then n");
        s.println("  rows of n comma-separated values) and reports whether the graph whose");
        s.println("  edges are the pairs with |correlation| > |threshold| is a comparability");
        s.println("  graph (transitively orientable).");
        s.println();
        s.println("  <csv-path>            path to the correlation-matrix CSV");
        s.println("  -t, --threshold <t>   edge threshold magnitude (default 0.5)");
        s.println("      --json            emit the full analysis result as JSON");
        s.println("  -h, --help            show this help");
    }
}
