# CLAUDE.md

Guidance for Claude Code when working in this repository.

Read `README.md` first — it owns the project overview, the public API examples,
the package layout and the scope of the comparability test. This file tells you
how to work on the code: the rules that must hold and the common task recipes.

## Commands

```bash
./mvnw clean verify           # full build: tests + coverage gates — run before claiming done
./mvnw test                   # tests only (faster iteration)
./mvnw test -Dtest=ClassName  # single test class
```

Single module; the artifact `ch.tarvynanalytics.graphs:graphs-comparability-lib`
is published to GitHub Packages. Building requires **JDK 21+**; the bytecode
target is `--release 21`. Coverage report:
`target/site/jacoco/index.html` (CSV next to it for scripting). This is a
library — there is no application to run; the tests are the executable spec.

## Invariants — never break these

1. **Dependencies: lean, not zero.** Runtime dependencies are allowed — add one
   when it clearly pulls its weight, but be mindful not to overcrowd: prefer
   hand-rolling small things (a JSON line, a format escaper) over pulling in
   Guava/commons-* for a one-liner, and justify each new dependency by what it
   saves. (Test scope, e.g. JUnit, is unconstrained.)
2. **Correct, standard algorithms — not the thesis port.** The original C#
   chain-folding engine was found to be unsound *and* incomplete and to
   under-count orientations (see `docs/theory-review.md`, CGD-5); it has been
   replaced. The verdict + obstruction come from **Golumbic's forcing relation
   (Γ)** in `ForcingRelation` (sound & complete: a graph is a comparability graph
   iff no implication class contains an arc and its reverse). The orientation
   count and the factor-graph levels come from the **canonical modular
   decomposition** in `ModularDecomposition` (count = ∏ over the tree: `k!` for a
   series node, `1` for parallel, `2` for prime). Correctness is pinned by an
   exhaustive brute-force-oracle test (`OracleCharacterizationTest`, all labeled
   graphs n ≤ 5, verdict + count). Do not regress to the thesis heuristic; prove
   any engine change against the oracle.
3. **The public result is immutable.** Everything in `model/` is a `record` with
   defensive `List.copyOf` in its compact constructor. The engine works on a
   plain `boolean[][]` adjacency built from `GraphInput`; never leak internal
   working state across the API.
4. **Implementations are package-private.** Public surface is only the entry
   points (`ComparabilityAnalyzer`, `BatchAnalyzer`, `cli.ComparabilityCli`),
   `GraphInput`, the `model` records, the `export` serializers and the exceptions.
   Keep it that way — e.g. the CLI's `CorrelationCsv` reader stays package-private,
   and the engine (`ForcingRelation`, `ModularDecomposition`, `Chordality`,
   `ResultBuilder`) is never exported.
5. **Validation errors throw `InvalidInputException`** with the offending values
   in brackets, e.g. `"... got [3x0]"`. Null `GraphInput` to the analyzer throws
   `IllegalArgumentException`.
6. **Orientation count is a `BigInteger`.** It grows factorially; never narrow it
   to `int`/`long`.
7. **Coverage gates 80% line / 70% branch** are enforced by `verify`. New code
   arrives with tests in the same commit.

## Testing conventions

- Naming: `method_Scenario_Expectation`
  (`oddCycle_IsNotComparability_AndReportsTheCycle`).
- Test packages mirror main 1:1; a test class covers the class it is named after.
- Prove verdicts against **known graph theory**, not against the code's own
  output: cliques have `n!` orientations, paths and even cycles are comparability,
  odd cycles (C5, C7, …) are not and yield a failure cycle of that length,
  disjoint cliques (cographs) decompose over multiple levels. Build the fixtures
  (`complete`, `cycle`, `path`) inside the test.
- Assertion arguments are `(expected, actual)` — expected value first.

## Task guides

### Touch the engine (`ForcingRelation` / `ModularDecomposition`)

`OracleCharacterizationTest` (exhaustive over all labeled graphs n ≤ 5, verdict
*and* count vs a brute-force oracle) plus the known-graph tests in
`ComparabilityAnalyzerTest` are the safety net — they must keep passing.
`ForcingRelation` decides comparability via Γ implication classes and, on
failure, returns a shortest odd forcing walk (a clean odd hole when one exists,
otherwise a closed walk that may revisit vertices — see `arcsToCycle`).
`ModularDecomposition` recurses parallel (disconnected) / series (co-disconnected)
/ prime (maximal strong modules, found via minimal-module closure) and supplies
both the count and the level/grouping view. Any change must still match the
oracle; add new graph families to the oracle test.

`Chordality` is a separate, independent engine (chordality ≠ comparability): it
decides chordality via maximum-cardinality-search perfect-elimination ordering,
recovers a hole on failure and produces a greedy (minimum-degree elimination
game) chordal completion. Its own oracle is `ChordalityTest` (exhaustive over all
graphs n ≤ 6: verdict, plus the witness is a real hole and the completion is
chordal — invariant #2). It is computed for every analysis and surfaced as
`AnalysisResult.chordality()`.

### Add a result field

Add the component to the relevant `model/` record (with a defensive copy if it is
a collection), populate it in `ResultBuilder`, and exercise it in `ModelTest`
plus the analyzer suite. Keep the record's javadoc accurate — the published jar
ships javadoc and the `-Ppublish` build fails on javadoc errors.

### Add an exporter (e.g. JSON, DOT)

Put it in `export/` as a public final class with static methods over the `model`
records. Hand-roll the format — a JSON/DOT serializer isn't worth a dependency
here (keep deps lean, invariant #1). Escape strings yourself. Test the exact
output on a small known result.

### Touch the CLI (`cli/`)

`ComparabilityCli` is the `java -jar` entry point; the jar's `Main-Class` is set
in the pom's `maven-jar-plugin`. Keep `main` a one-liner that delegates to the
package-private `run(args, out, err)` (returns the exit code) so behaviour is unit
tested without `System.exit`. CSV parsing lives in the package-private
`CorrelationCsv` (hand-rolled, invariant #1); leave squareness/finiteness to
`GraphInput.fromCorrelation`. Exit codes are result-only (`0` ran / `2` usage /
`1` input-IO). Run it: `./mvnw -q package -DskipTests` then
`java -jar target/graphs-comparability-lib-*.jar matrix.csv --threshold 0.5`. The
console/`System.exit` Sonar rules (`S106`/`S1147`) are silenced for `**/cli/*.java`
in the pom.

## Delivery: Jira, Git, PRs, CI

- **Jira** (project `CGD`, *Comparability Graph Detection*): use
  `.claude/tools/jira/jira.sh` — full usage in `.claude/skills/jira/SKILL.md`.
  Every piece of work hangs off an issue; epic for the initiative, task per
  deliverable. Transition to `In Progress` when starting, `Done` with a PR/commit
  reference when finished. (Sub-task issue type is named `Subtask`.)
- **GitFlow**: `main` (released) ← `develop` (integration) ← `feature/*`. Branch
  naming: `feature/CGD-<n>-eb-<short-description>`. PRs target `develop` and are
  **squash**-merged; only release merges go `develop` → `main` and use a **true
  merge commit** (`gh pr merge --merge`), never squash. After a release,
  back-merge main into develop and bump the pom to the next `-SNAPSHOT`. Full
  procedure: `.claude/skills/release/SKILL.md`.
- **Commit style**: conventional commits with scope and issue key, e.g.
  `feat(lib): [CGD-3]: port the comparability engine`; body explains the why.
  GPG signing fails under WSL ("Unusable secret key") — use
  `git commit --no-gpg-sign` from WSL.
- **GitHub** (`tarvyn-analytics/graphs-comparability-lib`, private): use the `gh`
  CLI directly — `gh pr create --base develop`, `gh pr checks --watch`,
  `gh pr merge --squash`, `gh run watch`.
- **CI** (`.github/workflows/`): `validate-on-pull-request.yml` runs
  `./mvnw -Ppublish clean verify sonar:sonar` on PRs to develop/main, uploads the
  JaCoCo report and enforces the SonarCloud quality gate; `build-on-push.yml`
  publishes the jar (with sources and javadoc) to GitHub Packages on pushes —
  develop publishes the SNAPSHOT, main strips the suffix, publishes the release
  and pushes the `vX.Y.Z` tag. The same release version cannot be published
  twice, so bump the version on develop before each release merge to main.
  SonarCloud org/host/projectKey come from the pom. No DB, no Docker — keep it so.
