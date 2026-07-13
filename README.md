# graphs-algos-lib

> Research program frozen 2026-07; this library is complete and maintained as-is. Entry point: [corrcalc-graphs-meta](https://github.com/tarvyn-analytics/corrcalc-graphs-meta).

Pure-Java **graph-algorithms** library for the structural analysis of a graph
built from a square **correlation** or **adjacency** matrix. It began as a
transitive-orientation (comparability) analysis ported from the master's thesis
*"Securities-market analysis using transitively orientable graphs"* (the C#
prototype's `GraphParser` / `Graph_NonOriented` / `FactorGraphLevel` / `Node`) and
has since grown a family of independent structural analyses — hence
`graphs-algos-lib`:

- **Comparability** (transitive orientability) — the factor-graph (modular)
  decomposition hierarchy and the number of distinct transitive orientations, or,
  when the graph is not comparability, the obstructing **odd chordless cycle**
  (an "odd hole") with the weakest correlation edge on it (the cheapest edge to
  drop to break the obstruction). See [What it computes](#what-it-computes).
- **Chordality / decomposability** — a perfect elimination ordering when the graph
  is chordal, otherwise a witnessing hole and a greedy **chordal completion**.
  See [Chordality / decomposability](#chordality--decomposability).
- **Decomposability repair** — weakest-link edge deletion down to a chordal
  (decomposable) graph.
- **Structural balance** — signed-graph (Heider/Harary) balance, with an
  odd-negative cycle as the conflict witness.
- **Cross-estimator robustness** — the stable core and outlier-sensitive edges
  across several correlation estimators of the same variables.

There is no GUI and there are **zero runtime dependencies**; every result is a
plain immutable object you can inspect or serialize.

## Quick start

As a dependency (published to GitHub Packages, private — resolve via a PAT with
`read:packages`, or `./mvnw -DskipTests install` from this checkout):

```xml
<dependency>
    <groupId>ch.tarvynanalytics.graphs</groupId>
    <artifactId>graphs-algos-lib</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

```java
import ch.tarvynanalytics.graphs.algos.ComparabilityAnalyzer;
import ch.tarvynanalytics.graphs.algos.GraphInput;
import ch.tarvynanalytics.graphs.algos.model.AnalysisResult;

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

## Structural balance (signed correlation graph)

The analyses above ignore the *sign* of a correlation. `StructuralBalanceAnalyzer`
keeps it and tests **structural balance** (Heider / Harary): a signed graph is
balanced iff every cycle has an even number of negative edges — equivalently, iff
its vertices split into two camps with positive edges *within* a camp and negative
edges *between* them. For a correlation graph that means the names fall into two
blocs that are internally positively correlated and mutually negatively correlated.

```java
StructuralBalanceView b = StructuralBalanceAnalyzer.analyze(
        GraphInput.fromCorrelation(matrix, 0.5));
if (b.isBalanced()) {
    System.out.println("two blocs: " + b.verticesInCamp(0) + " vs " + b.verticesInCamp(1));
} else {
    System.out.println("frustrated cycle: " + b.frustratedCycleLabels());  // odd # of negatives
}
```

It is a separate, opt-in analyzer because balance is sign-dependent whereas the
main `AnalysisResult` is sign-agnostic. Decided in linear time by a signed BFS
2-colouring; on failure it returns a witnessing cycle with an odd number of
negative edges. A graph built from a plain boolean adjacency carries no signs
(every edge positive), so it is trivially balanced.

## Cross-estimator robustness (stable core)

The analyses above look at one matrix. `CrossEstimatorAnalyzer` looks across
several — Pearson, Spearman, Kendall, partial — and reports **how robust the edge
structure is to the choice of estimator**: which links every estimator agrees on
(the stable, trustworthy "gold core") versus which are seen by only one (the
outlier-sensitive links).

Marginal estimators live on different scales (Kendall's τ is systematically
smaller than Pearson's r), so a single absolute threshold would not compare like
with like. Estimators are matched on **selectivity** instead: each contributes its
top-K strongest edges by magnitude.

```java
import ch.tarvynanalytics.graphs.algos.CrossEstimatorAnalyzer;
import ch.tarvynanalytics.graphs.algos.EstimatorMatrix;
import ch.tarvynanalytics.graphs.algos.model.CrossEstimatorReport;

CrossEstimatorReport r = CrossEstimatorAnalyzer.analyze(
        List.of(EstimatorMatrix.of("pearson",  pearson),
                EstimatorMatrix.of("spearman", spearman),
                EstimatorMatrix.of("kendall",  kendall),
                EstimatorMatrix.of("partial",  partial)),
        tickers, 144);                              // top-144 strongest edges each

r.jaccard(0, 1);            // pairwise top-K edge-set overlap (Jaccard), pearson vs spearman
r.stableCore();             // edges in every estimator's top-K (robust to the estimator choice)
r.uniqueTo(0);              // edges only pearson's top-K has (outlier-sensitive)
```

`CrossEstimatorReport` carries the estimator names, the pairwise Jaccard matrix
and, in `edges()`, every edge that appears in at least one estimator's top-K as a
`RobustEdge` annotated with its `support()` (how many estimators agree) and the
estimator indices that contain it — from which `stableCore()` (full support),
`uniqueEdges()` (support 1) and `uniqueTo(estimator)` are derived. Ties at the
top-K cut are broken deterministically by endpoint, and `topK` is clamped to the
number of vertex pairs. This is set algebra over the top-K edge sets, not a
graph-property decision, so it has no brute-force oracle; it is pinned by
hand-computed fixtures.

## Storing / exporting the result

The result is a plain object — keep it, or serialize it with the built-in,
zero-dependency exporters in `…algos.export`:

```java
import ch.tarvynanalytics.graphs.algos.export.JsonExporter;
import ch.tarvynanalytics.graphs.algos.export.DotExporter;

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
import ch.tarvynanalytics.graphs.algos.BatchAnalyzer;

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
java -jar target/graphs-algos-lib-0.1.0-SNAPSHOT.jar matrix.csv --threshold 0.5
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
`DecomposabilityReport` JSON); `--balance` switches to the signed-graph
structural-balance verdict (the two correlation blocs, or a frustrated cycle);
`--help` shows usage. Exit codes are **result-only**: `0` when the analysis ran
(whatever the verdict), `2` for a usage error, `1` for an input/IO error — read
the verdict from the output, not the exit code.

To compare several estimators, pass `--robust` and two or more CSVs over the same
variables (the estimator name is each file's stem):

```bash
java -jar target/graphs-algos-lib-0.1.0-SNAPSHOT.jar --robust \
    pearson.csv spearman.csv kendall.csv partial.csv --top 144
```

```
estimators:    4 (pearson, spearman, kendall, partial)
top-K:         144 (selectivity-matched)
Jaccard overlap (top-K edge sets):
  pearson - spearman: 0.78
  ...
stable core (in all 4): 51 edge(s)
  ADI - MCHP
  ...
estimator-unique edges (outlier-sensitive):
  pearson (16): AEE-EVRG, AEP-CMS, ...
```

`--top <k>` is the per-estimator selectivity; it defaults to the first
estimator's edge count above `--threshold` (so `--robust ... --threshold 0.8`
matches the K to Pearson's 0.8 graph). With `--json` it emits the
`CrossEstimatorReport` shape.

## Package layout

```
ch.tarvynanalytics.graphs.algos
  ComparabilityAnalyzer   – entry point: analyze(GraphInput) -> AnalysisResult
  BatchAnalyzer           – run many analyses / threshold sweeps, optionally parallel
  DecomposabilityDiagnostic – weakest-link repair to a chordal (decomposable) graph
  StructuralBalanceAnalyzer – signed-graph structural balance (Heider/Harary)
  CrossEstimatorAnalyzer  – cross-estimator robustness / stable core (top-K Jaccard)
  GraphInput              – build the graph from a correlation or adjacency matrix
  EstimatorMatrix         – a named correlation matrix (input to CrossEstimatorAnalyzer)
  (package-private)       – ForcingRelation (Golumbic Γ verdict + obstruction),
                            ModularDecomposition (count + factor-graph levels),
                            Chordality (chordality verdict + PEO / hole / completion),
                            StructuralBalance (signed BFS 2-colouring),
                            ResultBuilder: the engine; not exported
  .cli                    – ComparabilityCli (java -jar entry point over a CSV;
                            package-private CorrelationCsv reader)
  .model                  – immutable result types (records):
                            AnalysisResult, GraphView, NodeView, EdgeView,
                            FactorGraphLevelView, ModuleView, ModuleType,
                            FailureCycle, ChordalityView, DecomposabilityReport,
                            StructuralBalanceView, CrossEstimatorReport, RobustEdge
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
under `ch.tarvynanalytics.graphs:graphs-algos-lib`.

## Development

GitFlow: `feature/GAL-<n>-eb-<desc>` → squash-merge to `develop`; releases merge
`develop` → `main`. CI validates every PR (build, tests, coverage, SonarCloud
quality gate) and publishes on push. See `CLAUDE.md` for the working rules and
`.claude/skills/` for the Jira and release helpers. Work is tracked in Jira
project **GAL** (Graph Algos Lib).
