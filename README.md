# graphs-comparability-lib

Pure-Java library that decides whether an undirected graph is a **comparability
graph** (equivalently, *transitively orientable*), and if so describes how. The
graph is built from a square **correlation** or **adjacency** matrix; the
analysis is a faithful, GUI-free port of the algorithm from the master's thesis
*"Securities-market analysis using transitively orientable graphs"* (the C#
prototype's `GraphParser` / `Graph_NonOriented` / `FactorGraphLevel` / `Node`).

Given a matrix it produces one of two outcomes:

- **comparability** — the factor-graph (modular) decomposition hierarchy plus
  the number of distinct transitive orientations; or
- **not comparability** — the obstructing **odd chordless cycle** (an "odd
  hole"), reported in the original vertices, together with the weakest
  correlation edge on it (the cheapest edge to drop, e.g. by raising the
  threshold, to break the obstruction).

Independently of that verdict it also reports whether the same graph is
**chordal** (decomposable) — a perfect elimination ordering when it is, otherwise
a witnessing hole and a greedy **chordal completion** (the fill-in edges that make
it decomposable). See [Chordality / decomposability](#chordality--decomposability).

There is no GUI and there are **zero runtime dependencies**; the result is a
plain immutable object you can inspect or serialize.

## Quick start

```java
import ch.tarvynanalytics.graphs.comparability.ComparabilityAnalyzer;
import ch.tarvynanalytics.graphs.comparability.GraphInput;
import ch.tarvynanalytics.graphs.comparability.model.AnalysisResult;

double[][] correlation = {
    {1.00, 0.82, 0.10, 0.05},
    {0.82, 1.00, 0.07, 0.04},
    {0.10, 0.07, 1.00, 0.91},
    {0.05, 0.04, 0.91, 1.00},
};

// An edge exists when |correlation| > |threshold|.
AnalysisResult result = ComparabilityAnalyzer.analyze(
        GraphInput.fromCorrelation(correlation, 0.5));

if (result.isComparability()) {
    System.out.println("transitive orientations: " + result.transitiveOrientationCount());
    System.out.println("decomposition levels:    " + result.levels().size());
} else {
    result.failure().ifPresent(f -> {
        System.out.println("not a comparability graph; obstructing cycle: " + f.nodeLabels());
        System.out.println("weakest edge: " + f.weakestEdgeSource() + "-" + f.weakestEdgeTarget()
                + " (corr " + f.weakestCorrelation() + ")");
    });
}
```

Already have a boolean adjacency matrix (or want labels)?

```java
AnalysisResult r = ComparabilityAnalyzer.analyze(
        GraphInput.fromAdjacency(adjacency, new String[] {"AAPL", "MSFT", "KO", "PEP"}));
```

## What it computes

For a comparability graph the library returns the **substitution (modular)
decomposition** as a list of levels. Level 0 is the input graph; each subsequent
level is the *factor graph* of the previous one — every maximal stable set
(module) collapses to a single vertex. Three module kinds are distinguished:

| `ModuleType`      | meaning                                                            |
|-------------------|--------------------------------------------------------------------|
| `INDEPENDENT_SET` | maximal module of pairwise **non-adjacent** vertices               |
| `CLIQUE`          | maximal module of pairwise **adjacent** vertices (a complete part) |
| `MIN_STABLE`      | minimal module captured by a chordless chain (neither of the above)|
| `SINGLETON`       | a module of one vertex                                             |

The number of distinct transitive orientations is computed from the canonical
**modular decomposition** tree — the product over its nodes of `k!` for a series
(join) node with `k` children, `1` for a parallel (union) node and `2` for a
prime node — the standard Gallai/Golumbic count, held as a `BigInteger` so it
never overflows.

When the graph is **not** a comparability graph, `failure()` returns the
`FailureCycle`: an odd-length closed **forcing walk** that obstructs any
transitive orientation, in original vertices. When the obstruction is an odd hole
(e.g. C₅, C₇) it is exactly that hole; for a folded obstruction (e.g. the 3-sun)
it is a closed walk that may revisit vertices — every consecutive pair is a real
edge either way.

> **Scope of the test.** The verdict and obstruction are decided by **Golumbic's
> forcing relation (Γ)** — the standard, *sound and complete* comparability test
> (a graph is a comparability graph iff no implication class contains an edge and
> its reverse). The orientation count comes from the canonical modular
> decomposition. Correctness is cross-checked against a brute-force oracle over
> every graph up to 5 vertices. (Earlier releases ported a thesis prototype that
> was found to be unsound, incomplete and to under-count; see
> `docs/theory-review.md`.)

## Chordality / decomposability

Every result also carries `chordality()`, computed independently of the
comparability verdict. A graph is **chordal** (triangulated) iff every cycle of
length ≥ 4 has a chord — equivalently, iff it has a *perfect elimination
ordering*. Chordal graphs are exactly the **decomposable** models: when the
(partial-)correlation graph is chordal, the Gaussian graphical model factorizes
over a clique (junction) tree and the covariance / precision MLE is closed-form
and modular.

```java
ChordalityView c = result.chordality();
if (c.isChordal()) {
    // a perfect elimination ordering of the graph (the clique-tree order)
    System.out.println("decomposable; PEO: " + c.perfectEliminationOrder());
} else {
    System.out.println("chordless cycle: " + c.chordlessCycleLabels());
    System.out.println("chordal completion adds " + c.fillInCount() + " edge(s): "
            + c.fillInEdges());            // the fill-in that makes it decomposable
}
```

When the graph is **chordal**, `perfectEliminationOrder()` is a PEO and both the
hole and the fill-in are empty. When it is **not**, `chordlessCycle()` is a
witnessing hole, `fillInEdges()` is a greedy chordal completion and
`perfectEliminationOrder()` is a PEO of that completion (input graph + fill-in).

> **Scope.** Detection is maximum-cardinality search (Tarjan–Yannakakis); on
> failure a hole is recovered as the obstruction. The completion is the
> elimination game with a minimum-degree heuristic — any elimination order yields
> a chordal completion, the heuristic only keeps the fill-in small. **Minimum**
> fill-in is NP-hard, so the completion is *not* guaranteed minimal. Verdict,
> witness and completion are cross-checked against a brute-force oracle over every
> graph up to six vertices.

Note comparability and chordality are independent: `C₄` is comparability but not
chordal, the `3-sun` is chordal but not comparability, `K₄` is both and `C₅` is
neither.

### Weakest-link repair (`DecomposabilityDiagnostic`)

`ChordalityView.fillInEdges()` repairs decomposability by *adding* edges. The
complementary repair — *removing* the weakest links that frustrate it — is
`DecomposabilityDiagnostic`, the decomposability-targeted form of the thesis
"weakest-edge-first" idea:

```java
DecomposabilityReport r = DecomposabilityDiagnostic.analyze(
        GraphInput.fromCorrelation(matrix, 0.18));
if (!r.isDecomposable()) {
    System.out.println("drop " + r.removalCount() + " link(s) to decompose");
    System.out.println("heaviest such link |corr| = " + r.suggestedThreshold());
    System.out.println("(or add " + r.fillInAlternative() + " fill-in edge(s) instead)");
}
```

While the graph has a chordless cycle, the weakest edge on it (smallest
`|correlation|`) is removed, until the graph is chordal. The removed set names
exactly which weak dependencies frustrate a decomposable (junction-tree) model.
It is greedy (minimum edge deletion to chordal is NP-hard, so the set is not
guaranteed minimum) but always terminates and is a principled diagnostic.

## Storing / exporting the result

The result is a plain object — keep it, or serialize it with the built-in,
zero-dependency exporters in `…comparability.export`:

```java
import ch.tarvynanalytics.graphs.comparability.export.JsonExporter;
import ch.tarvynanalytics.graphs.comparability.export.DotExporter;

String json = JsonExporter.toJson(result);                 // full result as JSON
String dot  = DotExporter.inputGraphToDot(result);         // input graph as Graphviz DOT
String lvl  = DotExporter.factorLevelToDot(result.levels().get(0));
String comp = DotExporter.chordalCompletionToDot(result);  // input edges solid, fill-in dashed

result.failure().ifPresent(f ->
    System.out.println(DotExporter.failureCycleToDot(f)));  // odd cycle, weakest edge in red
```

Render DOT with Graphviz, e.g. `dot -Tsvg graph.dot -o graph.svg`. The JSON
mirrors the model: `comparability`, `transitiveOrientationCount` (a bare
arbitrary-precision integer), `inputGraph`, `levels[]` (each with `graph`,
`modules`, `factorGraph`), `failure` (`null`, or the cycle with its
`weakestCorrelation` / `weakestEdge`; a non-finite correlation is `null`) and
`chordality` (`chordal`, `perfectEliminationOrder`, `chordlessCycle`,
`fillInEdges`).

## Batch analysis (threshold sweeps, parallel)

A single analysis is fast and its engine is sequential, so the worthwhile
parallelism is at the **batch** level — and `analyze` is stateless and
thread-safe. `BatchAnalyzer` runs many independent analyses, optionally in
parallel over the common `ForkJoinPool`:

```java
import ch.tarvynanalytics.graphs.comparability.BatchAnalyzer;

// the thesis workflow: analyse one correlation matrix across rising thresholds
double[] thresholds = {0.3, 0.4, 0.5, 0.6, 0.7};
List<AnalysisResult> sweep = BatchAnalyzer.thresholdSweepParallel(correlation, thresholds);

// or fan out arbitrary independent inputs (rolling windows, Monte-Carlo, …)
List<AnalysisResult> results = BatchAnalyzer.analyzeAllParallel(inputs);
```

Parallelism is explicit (the `*Parallel` methods) and falls back to sequential
for small batches; results keep input order and the returned list is
unmodifiable.

## Command-line interface

The jar is runnable — point it at a correlation-matrix CSV and it prints the
verdict. The CSV is an optional header row of labels followed by one row of `n`
comma-separated values per vertex (the corrcalc exports are exactly this shape):

```
AAPL,MSFT,SPY,GLD,TLT
1.0,0.02,0.29,-0.15,0.11
...
```

```bash
./mvnw -q package -DskipTests
java -jar target/graphs-comparability-lib-0.1.0-SNAPSHOT.jar matrix.csv --threshold 0.5
```

```
file:          matrix.csv
vertices:      5
threshold:     0.5
edges:         1
comparability: YES
transitive orientations: 2
decomposition levels:    4
chordal:       YES (decomposable)
```

When the graph is not chordal the CLI prints `chordal: NO` instead, with the
chordless cycle and the number of fill-in edges its chordal completion adds.

An edge is created for every pair with `|correlation| > |threshold|`
(`--threshold` / `-t`, default `0.5`). `--json` emits the result as JSON (the
`JsonExporter` shape); `--repair` switches to the decomposability diagnostic —
the weakest links to remove to make the graph chordal (with `--json`, the
`DecomposabilityReport` JSON); `--help` shows usage. Exit codes are
**result-only**: `0` when the analysis ran (whatever the verdict), `2` for a
usage error, `1` for an input/IO error — read the verdict from the output, not
the exit code.

## Package layout

```
ch.tarvynanalytics.graphs.comparability
  ComparabilityAnalyzer   – entry point: analyze(GraphInput) -> AnalysisResult
  BatchAnalyzer           – run many analyses / threshold sweeps, optionally parallel
  DecomposabilityDiagnostic – weakest-link repair to a chordal (decomposable) graph
  GraphInput              – build the graph from a correlation or adjacency matrix
  (package-private)       – ForcingRelation (Golumbic Γ verdict + obstruction),
                            ModularDecomposition (count + factor-graph levels),
                            Chordality (chordality verdict + PEO / hole / completion),
                            ResultBuilder: the engine; not exported
  .cli                    – ComparabilityCli (java -jar entry point over a CSV;
                            package-private CorrelationCsv reader)
  .model                  – immutable result types (records):
                            AnalysisResult, GraphView, NodeView, EdgeView,
                            FactorGraphLevelView, ModuleView, ModuleType,
                            FailureCycle, ChordalityView, DecomposabilityReport
  .export                 – JsonExporter, DotExporter (zero-dependency serializers)
  .exception              – ComparabilityException, InvalidInputException
```

Only interfaces/entry points, the input builder, the model and the exceptions
are public; the engine classes are package-private.

## Build

Requires JDK 21+. Uses the Maven wrapper.

```bash
./mvnw clean verify      # tests + JaCoCo coverage gates (80% line / 70% branch)
./mvnw test              # tests only
./mvnw test -Dtest=ComparabilityAnalyzerTest
```

This is primarily a library and the tests are its executable spec; it also ships
a small command-line interface (see above) for analysing a correlation-matrix CSV
directly. The published artifact (jar + sources + javadoc) goes to GitHub Packages
under `ch.tarvynanalytics.graphs:graphs-comparability-lib`.

## Development

GitFlow: `feature/CGD-<n>-eb-<desc>` → squash-merge to `develop`; releases merge
`develop` → `main`. CI validates every PR (build, tests, coverage, SonarCloud
quality gate) and publishes on push. See `CLAUDE.md` for the working rules and
`.claude/skills/` for the Jira and release helpers. Work is tracked in Jira
project **CGD** (Comparability Graph Detection).
