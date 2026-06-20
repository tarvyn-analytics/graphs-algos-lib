package ch.tarvynanalytics.graphs.comparability.cli;

import ch.tarvynanalytics.graphs.comparability.ComparabilityAnalyzer;
import ch.tarvynanalytics.graphs.comparability.DecomposabilityDiagnostic;
import ch.tarvynanalytics.graphs.comparability.GraphInput;
import ch.tarvynanalytics.graphs.comparability.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.comparability.export.JsonExporter;
import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;
import ch.tarvynanalytics.graphs.comparability.model.ChordalityView;
import ch.tarvynanalytics.graphs.comparability.model.DecomposabilityReport;
import ch.tarvynanalytics.graphs.comparability.model.EdgeView;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Command-line entry point: analyse a correlation-matrix CSV for comparability
 * (transitive orientability).
 *
 * <pre>{@code
 * java -jar graphs-comparability-lib.jar <csv-path> [--threshold <t>] [--json] [--repair]
 * }</pre>
 *
 * <p>An undirected edge is created for every pair whose
 * {@code |correlation| > |threshold|} (threshold default {@code 0.5}). The verdict
 * and details are printed to standard output (or the full result as JSON with
 * {@code --json}). With {@code --repair} the tool instead prints the
 * decomposability diagnostic — the weakest links to remove to make the graph
 * chordal. Exit codes are <em>result-only</em>: {@code 0} when the analysis ran
 * (whatever the verdict), {@code 2} for a usage error and {@code 1} for an
 * input/IO error; the verdict is read from the output, not the exit code.</p>
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
        ParseResult parse = parseArgs(args, out, err);
        if (parse.shouldExit()) {
            return parse.exitCode();
        }
        Options options = parse.options();

        CorrelationCsv.Parsed parsed;
        try {
            parsed = CorrelationCsv.read(Path.of(options.path()));
        } catch (IOException e) {
            err.println("error: cannot read file [" + options.path() + "]: " + e.getMessage());
            return 1;
        } catch (InvalidInputException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }

        GraphInput input;
        try {
            input = GraphInput.fromCorrelation(parsed.matrix(), options.threshold(), parsed.labels());
        } catch (InvalidInputException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }

        return dispatch(out, options, parsed.labels(), input);
    }

    /** The parsed command-line options. */
    private record Options(String path, double threshold, boolean json, boolean repair) {
    }

    /** Either parsed options, or an exit code to return immediately (help / usage error). */
    private record ParseResult(Options options, int exitCode) {
        static ParseResult ok(Options options) {
            return new ParseResult(options, -1);
        }

        static ParseResult exit(int code) {
            return new ParseResult(null, code);
        }

        boolean shouldExit() {
            return options == null;
        }
    }

    private static ParseResult parseArgs(String[] args, PrintStream out, PrintStream err) {
        String path = null;
        double threshold = DEFAULT_THRESHOLD;
        boolean json = false;
        boolean repair = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    printUsage(out);
                    return ParseResult.exit(0);
                }
                case "--json" -> json = true;
                case "--repair" -> repair = true;
                case "-t", "--threshold" -> {
                    Double value = parseThreshold(args, i, arg, err);
                    if (value == null) {
                        return ParseResult.exit(2);
                    }
                    threshold = value;
                    i++;
                }
                default -> {
                    String error = positionalError(arg, path);
                    if (error != null) {
                        err.println(error);
                        printUsage(err);
                        return ParseResult.exit(2);
                    }
                    path = arg;
                }
            }
        }
        if (path == null) {
            err.println("error: missing <csv-path>");
            printUsage(err);
            return ParseResult.exit(2);
        }
        return ParseResult.ok(new Options(path, threshold, json, repair));
    }

    /** Parses the threshold value following {@code flag} at {@code args[i]}, or {@code null} on error. */
    private static Double parseThreshold(String[] args, int i, String flag, PrintStream err) {
        if (i + 1 >= args.length) {
            err.println("error: " + flag + " requires a value");
            printUsage(err);
            return null;
        }
        String value = args[i + 1];
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            err.println("error: invalid threshold, got [" + value + "]");
            return null;
        }
    }

    /** An error message for a positional argument (unknown option / extra path), or {@code null} if valid. */
    private static String positionalError(String arg, String currentPath) {
        if (arg.startsWith("-")) {
            return "error: unknown option [" + arg + "]";
        }
        if (currentPath != null) {
            return "error: unexpected extra argument [" + arg + "]";
        }
        return null;
    }

    private static int dispatch(PrintStream out, Options options, String[] labels, GraphInput input) {
        if (options.repair()) {
            DecomposabilityReport report = DecomposabilityDiagnostic.analyze(input);
            if (options.json()) {
                out.println(JsonExporter.toJson(report));
            } else {
                printRepair(out, options.path(), options.threshold(), labels, report);
            }
            return 0;
        }
        AnalysisResult result = ComparabilityAnalyzer.analyze(input);
        if (options.json()) {
            out.println(JsonExporter.toJson(result));
        } else {
            printSummary(out, options.path(), options.threshold(), labels, result);
        }
        return 0;
    }

    private static void printRepair(PrintStream out, String path, double threshold,
                                    String[] labels, DecomposabilityReport report) {
        out.println("file:          " + path);
        out.println("threshold:     " + threshold);
        if (report.isDecomposable()) {
            out.println("decomposable:  YES (already chordal)");
            return;
        }
        out.println("decomposable:  NO");
        out.println("weakest links to remove (" + report.removalCount() + "):");
        for (int i = 0; i < report.removalCount(); i++) {
            EdgeView e = report.weakestLinksToRemove().get(i);
            double corr = report.removedCorrelations().get(i);
            String c = Double.isFinite(corr) ? String.format(Locale.ROOT, "%.4f", corr) : "n/a";
            out.println("  " + labels[e.source()] + " - " + labels[e.target()] + " (correlation " + c + ")");
        }
        if (Double.isFinite(report.suggestedThreshold())) {
            out.println("suggested threshold: " + String.format(Locale.ROOT, "%.4f", report.suggestedThreshold())
                    + " (drop links at or below it)");
        }
        out.println("fill-in alternative: " + report.fillInAlternative() + " edge(s) (add instead of remove)");
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
        printChordality(out, result.chordality());
    }

    private static void printChordality(PrintStream out, ChordalityView chordality) {
        if (chordality.isChordal()) {
            out.println("chordal:       YES (decomposable)");
        } else {
            out.println("chordal:       NO");
            out.println("chordless cycle (length " + chordality.chordlessCycle().size() + "): "
                    + String.join(" - ", chordality.chordlessCycleLabels()));
            out.println("chordal completion: " + chordality.fillInCount() + " fill-in edge(s)");
        }
    }

    private static void printUsage(PrintStream s) {
        s.println("Usage: comparability <csv-path> [--threshold <t>] [--json] [--repair]");
        s.println();
        s.println("  Reads a correlation-matrix CSV (optional header row of labels, then n");
        s.println("  rows of n comma-separated values) and reports whether the graph whose");
        s.println("  edges are the pairs with |correlation| > |threshold| is a comparability");
        s.println("  graph (transitively orientable) and whether it is chordal (decomposable).");
        s.println();
        s.println("  <csv-path>            path to the correlation-matrix CSV");
        s.println("  -t, --threshold <t>   edge threshold magnitude (default 0.5)");
        s.println("      --json            emit the result as JSON");
        s.println("      --repair          instead print the decomposability diagnostic:");
        s.println("                        the weakest links to remove to make it chordal");
        s.println("  -h, --help            show this help");
    }
}
