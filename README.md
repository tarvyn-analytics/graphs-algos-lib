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

## Storing / exporting the result

The result is a plain object — keep it, or serialize it with the built-in,
zero-dependency exporters in `…comparability.export`:

```java
import ch.tarvynanalytics.graphs.comparability.export.JsonExporter;
import ch.tarvynanalytics.graphs.comparability.export.DotExporter;

String json = JsonExporter.toJson(result);                 // full result as JSON
String dot  = DotExporter.inputGraphToDot(result);         // input graph as Graphviz DOT
String lvl  = DotExporter.factorLevelToDot(result.levels().get(0));

result.failure().ifPresent(f ->
    System.out.println(DotExporter.failureCycleToDot(f)));  // odd cycle, weakest edge in red
```

Render DOT with Graphviz, e.g. `dot -Tsvg graph.dot -o graph.svg`. The JSON
mirrors the model: `comparability`, `transitiveOrientationCount` (a bare
arbitrary-precision integer), `inputGraph`, `levels[]` (each with `graph`,
`modules`, `factorGraph`) and `failure` (`null`, or the cycle with its
`weakestCorrelation` / `weakestEdge`; a non-finite correlation is `null`).

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

## Package layout

```
ch.tarvynanalytics.graphs.comparability
  ComparabilityAnalyzer   – entry point: analyze(GraphInput) -> AnalysisResult
  BatchAnalyzer           – run many analyses / threshold sweeps, optionally parallel
  GraphInput              – build the graph from a correlation or adjacency matrix
  (package-private)       – ForcingRelation (Golumbic Γ verdict + obstruction),
                            ModularDecomposition (count + factor-graph levels),
                            ResultBuilder: the engine; not exported
  .model                  – immutable result types (records):
                            AnalysisResult, GraphView, NodeView, EdgeView,
                            FactorGraphLevelView, ModuleView, ModuleType, FailureCycle
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

This is a library — there is no application to run; the tests are the executable
spec. The published artifact (jar + sources + javadoc) goes to GitHub Packages
under `ch.tarvynanalytics.graphs:graphs-comparability-lib`.

## Development

GitFlow: `feature/CGD-<n>-eb-<desc>` → squash-merge to `develop`; releases merge
`develop` → `main`. CI validates every PR (build, tests, coverage, SonarCloud
quality gate) and publishes on push. See `CLAUDE.md` for the working rules and
`.claude/skills/` for the Jira and release helpers. Work is tracked in Jira
project **CGD** (Comparability Graph Detection).
