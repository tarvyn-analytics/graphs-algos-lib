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

1. **Zero runtime dependencies.** Test scope (JUnit) is the only exception. Do
   not add a JSON library, Guava, commons-*, anything — hand-roll it.
2. **Faithful to the thesis algorithm.** The engine (`GraphParser`, `Graph`,
   `Node`, `FactorGraphLevel`) is a deliberate port of the C# prototype
   (`## Anexa` of the thesis). Keep the decomposition order
   (not-linked → full → minimal), the chordless-chain machinery and the
   odd-cycle test behaviourally identical. When in doubt, match the original;
   do not "improve" the graph theory. Engine nodes are compared by **reference
   identity** (no `equals` override) — list membership relies on it.
3. **The public result is immutable.** Everything in `model/` is a `record` with
   defensive `List.copyOf` in its compact constructor. The engine is mutable and
   package-private; never leak engine `Node`/`Graph` objects across the API.
4. **Implementations are package-private.** Only `ComparabilityAnalyzer`,
   `GraphInput`, the `model` records and the exceptions are public. Keep it that
   way.
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

### Touch the decomposition engine (`GraphParser` & friends)

The known-graph tests in `ComparabilityAnalyzerTest` are the safety net — they
must keep passing. The trickiest part is the minimal-module chain extension
(`getNonTriangChain` → `continueNonTriangChain` → `createNonTriangApendix` /
`increaseApendix` / `reverseAppendNonTriangs`): it grows a chordless chain to
cover every edge of a minimal module, and the result feeds `canCreateOddCycle`.
The orientation bookkeeping (`nodesTo`/`nodesFrom`/`nodesNotOriented`) is scratch
state that drives which edges still need covering; it is reset before the
odd-cycle test and no concrete direction is ever exposed. If you change it,
re-verify against the odd-cycle fixtures and add the new case to the suite.

### Add a result field

Add the component to the relevant `model/` record (with a defensive copy if it is
a collection), populate it in `ResultBuilder`, and exercise it in `ModelTest`
plus the analyzer suite. Keep the record's javadoc accurate — the published jar
ships javadoc and the `-Ppublish` build fails on javadoc errors.

### Add an exporter (e.g. JSON, DOT)

Put it in `export/` as a public final class with static methods over the `model`
records. Hand-roll the format (invariant 1 — no dependencies). Escape strings
yourself. Test the exact output on a small known result.

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
