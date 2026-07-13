package ch.tarvynanalytics.graphs.algos.cli;

import ch.tarvynanalytics.graphs.algos.ComparabilityAnalyzer;
import ch.tarvynanalytics.graphs.algos.CrossEstimatorAnalyzer;
import ch.tarvynanalytics.graphs.algos.DecomposabilityDiagnostic;
import ch.tarvynanalytics.graphs.algos.EstimatorMatrix;
import ch.tarvynanalytics.graphs.algos.GraphInput;
import ch.tarvynanalytics.graphs.algos.StructuralBalanceAnalyzer;
import ch.tarvynanalytics.graphs.algos.exception.InvalidInputException;
import ch.tarvynanalytics.graphs.algos.export.JsonExporter;
import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;
import ch.tarvynanalytics.graphs.algos.model.ChordalityView;
import ch.tarvynanalytics.graphs.algos.model.CrossEstimatorReport;
import ch.tarvynanalytics.graphs.algos.model.DecomposabilityReport;
import ch.tarvynanalytics.graphs.algos.model.EdgeView;
import ch.tarvynanalytics.graphs.algos.model.RobustEdge;
import ch.tarvynanalytics.graphs.algos.model.StructuralBalanceView;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Command-line entry point: analyse a correlation-matrix CSV for comparability
 * (transitive orientability).
 *
 * <pre>{@code
 * java -jar graphs-algos-lib.jar <csv-path> [--threshold <t>] [--json] [--repair|--balance]
 * java -jar graphs-algos-lib.jar --robust <csv-path>... [--top <k>] [--threshold <t>] [--json]
 * }</pre>
 *
 * <p>An undirected edge is created for every pair whose
 * {@code |correlation| > |threshold|} (threshold default {@code 0.5}). The verdict
 * and details are printed to standard output (or the full result as JSON with
 * {@code --json}). With {@code --repair} the tool instead prints the
 * decomposability diagnostic (the weakest links to remove to make the graph
 * chordal); with {@code --balance} it prints the signed-graph structural-balance
 * verdict (the two correlation blocs, or a frustrated cycle); with {@code --robust}
 * it compares several estimator CSVs over the same variables (top-K Jaccard overlap,
 * the stable core and the estimator-unique edges). Exit codes are
 * <em>result-only</em>: {@code 0} when the analysis ran (whatever the verdict),
 * {@code 2} for a usage error and {@code 1} for an input/IO error; the verdict is
 * read from the output, not the exit code.</p>
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

        if (options.robust()) {
            return runRobust(out, err, options);
        }

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
    private record Options(List<String> paths, double threshold, Integer top,
                           boolean json, boolean repair, boolean balance, boolean robust) {
        /** The single CSV path of the non-robust modes. */
        String path() {
            return paths.get(0);
        }
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
        List<String> paths = new ArrayList<>();
        double threshold = DEFAULT_THRESHOLD;
        Integer top = null;
        boolean json = false;
        boolean repair = false;
        boolean balance = false;
        boolean robust = false;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> {
                    printUsage(out);
                    return ParseResult.exit(0);
                }
                case "--json" -> json = true;
                case "--repair" -> repair = true;
                case "--balance" -> balance = true;
                case "--robust" -> robust = true;
                case "-t", "--threshold" -> {
                    Double value = parseDoubleArg(args, i, arg, err);
                    if (value == null) {
                        return ParseResult.exit(2);
                    }
                    threshold = value;
                    i++;
                }
                case "--top" -> {
                    Integer value = parseIntArg(args, i, arg, err);
                    if (value == null) {
                        return ParseResult.exit(2);
                    }
                    top = value;
                    i++;
                }
                default -> {
                    if (arg.startsWith("-")) {
                        err.println("error: unknown option [" + arg + "]");
                        printUsage(err);
                        return ParseResult.exit(2);
                    }
                    paths.add(arg);
                }
            }
        }
        return validate(new Options(paths, threshold, top, json, repair, balance, robust), err);
    }

    /** Cross-option validation: arity per mode and mutually exclusive modes. */
    private static ParseResult validate(Options options, PrintStream err) {
        int modes = (options.repair() ? 1 : 0) + (options.balance() ? 1 : 0) + (options.robust() ? 1 : 0);
        if (modes > 1) {
            err.println("error: choose at most one of --repair, --balance, --robust");
            printUsage(err);
            return ParseResult.exit(2);
        }
        if (options.top() != null && !options.robust()) {
            err.println("error: --top is only valid with --robust");
            printUsage(err);
            return ParseResult.exit(2);
        }
        if (options.paths().isEmpty()) {
            err.println(options.robust() ? "error: --robust needs at least 2 <csv-path> arguments"
                    : "error: missing <csv-path>");
            printUsage(err);
            return ParseResult.exit(2);
        }
        if (options.robust() && options.paths().size() < 2) {
            err.println("error: --robust needs at least 2 <csv-path> arguments, got ["
                    + options.paths().size() + "]");
            printUsage(err);
            return ParseResult.exit(2);
        }
        if (!options.robust() && options.paths().size() > 1) {
            err.println("error: unexpected extra argument [" + options.paths().get(1) + "]");
            printUsage(err);
            return ParseResult.exit(2);
        }
        return ParseResult.ok(options);
    }

    /** Parses the double value following {@code flag} at {@code args[i]}, or {@code null} on error. */
    private static Double parseDoubleArg(String[] args, int i, String flag, PrintStream err) {
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

    /** Parses the integer value following {@code flag} at {@code args[i]}, or {@code null} on error. */
    private static Integer parseIntArg(String[] args, int i, String flag, PrintStream err) {
        if (i + 1 >= args.length) {
            err.println("error: " + flag + " requires a value");
            printUsage(err);
            return null;
        }
        String value = args[i + 1];
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            err.println("error: invalid --top value, got [" + value + "]");
            return null;
        }
    }

    private static int dispatch(PrintStream out, Options options, String[] labels, GraphInput input) {
        if (options.balance()) {
            StructuralBalanceView balance = StructuralBalanceAnalyzer.analyze(input);
            if (options.json()) {
                out.println(JsonExporter.toJson(balance));
            } else {
                printBalance(out, options.path(), options.threshold(), labels, balance);
            }
            return 0;
        }
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

    /** The {@code --robust} mode: compare several estimator CSVs over the same variables. */
    private static int runRobust(PrintStream out, PrintStream err, Options options) {
        List<EstimatorMatrix> estimators = new ArrayList<>();
        String[] labels = null;
        int referenceEdgeCount = 0;
        for (int p = 0; p < options.paths().size(); p++) {
            String path = options.paths().get(p);
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
            try {
                estimators.add(EstimatorMatrix.of(estimatorName(path), parsed.matrix()));
            } catch (InvalidInputException e) {
                err.println("error: " + e.getMessage());
                return 1;
            }
            if (p == 0) {
                labels = parsed.labels();
                referenceEdgeCount = countEdges(parsed.matrix(), options.threshold());
            }
        }

        int topK = options.top() != null ? options.top() : referenceEdgeCount;
        if (topK < 1) {
            err.println("error: no edges above |threshold| " + options.threshold()
                    + " in the first estimator; pass --top <k>");
            return 1;
        }

        CrossEstimatorReport report;
        try {
            report = CrossEstimatorAnalyzer.analyze(estimators, labels, topK);
        } catch (InvalidInputException e) {
            err.println("error: " + e.getMessage());
            return 1;
        }

        if (options.json()) {
            out.println(JsonExporter.toJson(report));
        } else {
            printRobust(out, report, options.top() == null, options.threshold());
        }
        return 0;
    }

    /** Counts the undirected edges {@code |m[i][j]| > |threshold|} (the selectivity for the first estimator). */
    private static int countEdges(double[][] matrix, double threshold) {
        double cut = Math.abs(threshold);
        int count = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = i + 1; j < matrix.length && j < matrix[i].length; j++) {
                if (Math.abs(matrix[i][j]) > cut) {
                    count++;
                }
            }
        }
        return count;
    }

    /** The estimator name from a CSV path: the file name without its extension. */
    private static String estimatorName(String path) {
        String name = Path.of(path).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static void printRobust(PrintStream out, CrossEstimatorReport report,
                                    boolean derivedTop, double threshold) {
        out.println("estimators:    " + report.estimatorCount() + " ("
                + String.join(", ", report.estimatorNames()) + ")");
        String selectivity = derivedTop
                ? " (matched to |corr| > " + threshold + " on " + report.estimatorNames().get(0) + ")"
                : " (selectivity-matched)";
        out.println("top-K:         " + report.topK() + selectivity);

        out.println("Jaccard overlap (top-K edge sets):");
        List<String> names = report.estimatorNames();
        for (int i = 0; i < names.size(); i++) {
            for (int j = i + 1; j < names.size(); j++) {
                out.println("  " + names.get(i) + " - " + names.get(j) + ": "
                        + String.format(Locale.ROOT, "%.2f", report.jaccard(i, j)));
            }
        }

        List<RobustEdge> core = report.stableCore();
        out.println("stable core (in all " + report.estimatorCount() + "): " + core.size() + " edge(s)");
        for (RobustEdge e : core) {
            out.println("  " + e.sourceLabel() + " - " + e.targetLabel());
        }

        out.println("estimator-unique edges (outlier-sensitive):");
        for (int e = 0; e < names.size(); e++) {
            List<RobustEdge> unique = report.uniqueTo(e);
            StringJoiner joiner = new StringJoiner(", ");
            for (RobustEdge edge : unique) {
                joiner.add(edge.sourceLabel() + "-" + edge.targetLabel());
            }
            out.println("  " + names.get(e) + " (" + unique.size() + "): " + joiner);
        }
    }

    private static void printBalance(PrintStream out, String path, double threshold,
                                     String[] labels, StructuralBalanceView balance) {
        out.println("file:          " + path);
        out.println("threshold:     " + threshold);
        out.println("negative edges: " + balance.negativeEdgeCount());
        if (balance.isBalanced()) {
            out.println("balanced:      YES");
            printCamp(out, "A", balance.verticesInCamp(0), labels);
            printCamp(out, "B", balance.verticesInCamp(1), labels);
        } else {
            out.println("balanced:      NO");
            out.println("frustrated cycle (length " + balance.frustratedCycle().size() + "): "
                    + String.join(" - ", balance.frustratedCycleLabels()));
        }
    }

    private static void printCamp(PrintStream out, String name, List<Integer> members, String[] labels) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < members.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(labels[members.get(i)]);
        }
        out.println("  camp " + name + " (" + members.size() + "): " + sb);
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
        s.println("Usage: comparability <csv-path> [--threshold <t>] [--json] [--repair|--balance]");
        s.println("       comparability --robust <csv-path>... [--top <k>] [--threshold <t>] [--json]");
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
        s.println("      --balance         instead print the signed-graph structural-balance");
        s.println("                        verdict: the two correlation blocs, or a frustrated cycle");
        s.println("      --robust          compare several estimator CSVs over the same variables:");
        s.println("                        top-K Jaccard overlap, the stable core, unique edges");
        s.println("      --top <k>         (--robust) strongest edges per estimator; default = the");
        s.println("                        first estimator's edge count above --threshold");
        s.println("  -h, --help            show this help");
    }
}
