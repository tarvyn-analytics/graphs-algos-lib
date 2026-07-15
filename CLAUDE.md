# CLAUDE.md

Guidance for Claude Code when working in this repository. Read `README.md` first — it owns
the overview, the public API examples and the scope of each analysis.

## Commands

```bash
./mvnw clean verify                       # full build: tests + coverage gates — run before claiming done
./mvnw test -Dtest=ClassName              # single test class
./mvnw -Ppublish -DskipTests javadoc:jar  # CI's javadoc gate — plain verify never runs it
```

Single module, published as `io.github.tarvyn-analytics.graphs:graphs-algos-lib`. **JDK 21+**, target
`--release 21`. This is a library — the tests are the executable spec. Run the CLI:
`./mvnw -q package -DskipTests` then `java -jar target/graphs-algos-lib-*.jar matrix.csv
--threshold 0.5`; exit codes are result-only (`0` ran / `2` usage / `1` input-IO).

## Invariants — never break these

1. **Dependencies: lean, not zero.** Prefer hand-rolling small things (a JSON line, an
   escaper) over pulling in Guava/commons-*; justify each new dependency by what it saves.
2. **Correct, standard algorithms — not the thesis port.** The original C# chain-folding
   engine was unsound/incomplete (see `docs/theory-review.md`, GAL-5) and was replaced:
   comparability verdict + obstruction via **Golumbic's forcing relation (Γ)**
   (`ForcingRelation`); orientation count + factor-graph levels via **canonical modular
   decomposition** (`ModularDecomposition`: ∏ `k!`/series, `1`/parallel, `2`/prime).
   `Chordality` is a separate engine (MCS perfect-elimination; hole witness; greedy
   completion). Every engine is pinned by an exhaustive brute-force oracle over all small
   graphs (`OracleCharacterizationTest` n≤5 verdict+count, `ChordalityTest` n≤6,
   `StructuralBalanceTest` all signed graphs n≤5). Prove any engine change against the
   oracle; never regress to the thesis heuristic.
3. **The public result is immutable.** Everything in `model/` is a record with defensive
   `List.copyOf`; never leak internal working state.
4. **Implementations are package-private.** Public surface = the entry points
   (`ComparabilityAnalyzer`, `BatchAnalyzer`, `DecomposabilityDiagnostic`,
   `StructuralBalanceAnalyzer`, `CrossEstimatorAnalyzer`, `ChangeMetricsAnalyzer`,
   `cli.ComparabilityCli`), inputs (`GraphInput`, `EstimatorMatrix`), `model` records,
   `export` serializers, exceptions.
5. **Validation errors throw `InvalidInputException`** with offending values in `[brackets]`;
   null `GraphInput` throws `IllegalArgumentException`.
6. **Orientation count is a `BigInteger`** — it grows factorially; never narrow it.
7. **Coverage gates 80% line / 70% branch** enforced by `verify`; tests in the same commit.

New analyzers/exporters/result fields mirror the existing precedents (public entry point +
package-private engine + oracle or hand-computed-fixture test + immutable record). The
console/`System.exit` Sonar rules are silenced only for `**/cli/*.java`.

## Testing conventions

Naming `method_Scenario_Expectation`; test packages mirror main 1:1; assertions
`(expected, actual)`. Verdicts are proven against **known graph theory** (cliques have `n!`
orientations, odd cycles are non-comparability, cographs decompose) and brute-force oracles —
never against the code's own output.

## Delivery

Jira **GAL** (`.claude/tools/jira/jira.sh`; sub-task type is `Subtask`). GitFlow: PR-only
**squash** into `develop`, branch `feature/GAL-<n>-eb-<desc>`; releases `develop`→`main` as a
true merge commit, then back-merge and bump `-SNAPSHOT` (procedure:
`.claude/skills/release/SKILL.md`). Conventional commits with issue key; `--no-gpg-sign`
under WSL. CI: `-Ppublish clean verify sonar:sonar` on PRs (javadoc errors fail there — the
published jar ships javadoc); push to `main` publishes a GPG-signed release to Maven Central +
tag (develop is the gate only; bump before release merges). No DB, no Docker.
