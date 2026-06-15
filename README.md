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

The number of distinct transitive orientations is the product over all levels of
`2^(#MIN_STABLE modules) × Π factorial(clique size)` — exactly the thesis
formula, computed as a `BigInteger` so it never overflows.

When the graph is **not** a comparability graph, `failure()` returns the
`FailureCycle`: the odd-length chordless cycle that obstructs any transitive
orientation, lifted back to original vertices (one representative per module), so
it is an actual odd hole in your input graph.

> **Scope of the test.** This is the thesis algorithm: it certifies
> non-comparability through the **odd-hole obstruction** uncovered while building
> minimal modules, and certifies comparability constructively via the
> decomposition. It is not a from-scratch reimplementation of the full Gallai
> characterization; it reproduces the thesis prototype's behaviour faithfully,
> which is exact on the graph families it was built for (cliques, paths, cycles,
> cographs, correlation-network graphs).

## Package layout

```
ch.tarvynanalytics.graphs.comparability
  ComparabilityAnalyzer   – entry point: analyze(GraphInput) -> AnalysisResult
  GraphInput              – build the graph from a correlation or adjacency matrix
  (package-private)       – Node, Graph, FactorGraphLevel, GraphParser, ResultBuilder:
                            the analysis engine; implementations are not exported
  .model                  – immutable result types (records):
                            AnalysisResult, GraphView, NodeView, EdgeView,
                            FactorGraphLevelView, ModuleView, ModuleType, FailureCycle
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
