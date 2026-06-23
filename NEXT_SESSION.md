# graphs-algos-lib — next-session work plan

Hand-off for continuing work on this library. Read this top-to-bottom once, then
`CLAUDE.md` for the day-to-day rules. Everything below is specific to *this*
codebase as it stands at commit on `develop` after GAL-1..4.

---

## ⚠️ Status update — GAL-10..15 DONE (read this first; much below is now historical)

The **entire roadmap (P0, D1–D5) is delivered and merged to `develop`** — PRs
#3–#8. The engine has been **replaced**. Sections 0–5 below described the *old*
thesis-ported engine and its bugs; they are kept for history but no longer
reflect the code. The **one open item** is the DJIA reproduction (see end of this
section).

- **GAL-10 (P0)** — the 3-sun `StackOverflow` was fixed (engine made total).
  *Superseded by the rewrite, but it unblocked the sweep.*
- **GAL-5 / GAL-11 (D5)** — `docs/theory-review.md`. Verdict: the thesis
  *criterion* (Thm 2.22) is correct (it is Golumbic's forcing relation), but
  **Algorithm 2.3.1 was unsound, incomplete and under-counted**. Evidence from a
  brute-force oracle sweep.
- **GAL-6 / GAL-12 (D4 + the fix)** — the engine was **rewritten**. The old
  `GraphParser`/`Graph`/`Node`/`FactorGraphLevel` are **deleted**. Now:
  `ForcingRelation` (Golumbic Γ verdict + odd forcing-walk obstruction —
  sound & complete) and `ModularDecomposition` (canonical modular decomposition
  → exact orientation count + factor-graph levels). `OracleCharacterizationTest`
  pins verdict **and** count against brute force for every labeled graph n ≤ 5,
  named families, and the n = 6 graphs the old engine got wrong.
- **CLAUDE.md invariant #2** ("faithful to the thesis algorithm") is **retired**;
  the new rule is "correct, standard algorithms, proven against the oracle."

### Remaining epics — all DONE

- **D1 / GAL-7 (PR #6) — DONE.** Mostly *subsumed* by the GAL-12 rewrite; PR #6
  was residual polish (imports) + this roadmap refresh.
- **D3 / GAL-8 (PR #7) — DONE.** Benchmark harness committed
  (`Benchmark.main`), baselines + before/after in `docs/performance.md`. Hotspot
  was the prime-case `minimalModule` closure; made incremental O(n²)/call →
  ~2× on the realistic threshold-sweep path; cographs ~3 ms at n=150.
  **Known non-target limitation:** a *fully prime* uniform-random dense graph
  (p=0.5, n ≳ 100) is still **O(n⁴)** (~0.4–2 s). Not the target workload
  (thresholded correlation nets are sparse/structured). Fix if ever needed:
  near-linear MD (partition refinement), guarded by `OracleCharacterizationTest`.
- **D2 / GAL-9 (PR #8) — DONE.** `BatchAnalyzer` — `analyzeAll` /
  `thresholdSweep` (+ `*Parallel` variants over the common ForkJoinPool, gated
  below `MIN_PARALLEL_BATCH`). Intra-analysis parallelism *not* pursued
  (documented decision, with D3 evidence). Engine is stateless/thread-safe per call.

### The one OPEN item — DJIA reproduction (D4 / GAL-6 sub-goal)

Reproducing the thesis's **DJIA worked example** (Ch. III,
`thesis_en/03_application_djia.md`, figs ~`image63/64`) is **not done**: the
chapter has narrative + figures but **no machine-readable correlation matrix**,
so it is *blocked on source data*. Everything else in D4 (oracle, sound/complete
engine, correct count) is delivered. The GAL-6 epic is left open over just this.
To finish: obtain the DJIA closing-price / correlation data, then add a
`thresholdSweep` integration test/`docs/` note reproducing the network +
threshold-raising iteration.

### Where to find things (current code)

- `ForcingRelation` — Γ verdict + odd forcing-walk obstruction (sound & complete).
- `ModularDecomposition` — parallel/series/prime decomposition → count + levels.
- `BatchAnalyzer` — batch/parallel API. `ResultBuilder` — assembles the result.
- `OracleCharacterizationTest` — brute-force oracle net (verdict + count, n ≤ 5).
- Docs: `docs/theory-review.md` (D5), `docs/performance.md` (D3).

---

## 0. Current state (what exists)

- **Repo**: `tarvyn-analytics/graphs-algos-lib` (private), default branch
  `develop`, GitFlow + branch protection (PR + `Verify PR` check; admin-merge
  allowed for the single maintainer). Local checkout:
  `/mnt/d/projects/startups/graphs/graphs-algos-lib`.
- **What it does**: matrix → graph → comparability verdict + factor-graph
  decomposition + transitive-orientation **count**, or the obstructing cycle with
  the weakest correlation edge. Faithful, GUI-free port of the thesis C# appendix.
- **Quality bar**: 59 tests green, ~89% line / ~78% branch, `./mvnw -Ppublish
  clean verify` clean, SNAPSHOT published to GitHub Packages, SonarCloud gate
  passing.
- **Jira**: project **GAL** (*Graph Algos Lib*); GAL-1..4 are
  **Done**. New work = new epics (see §4–§5). Tool: `.claude/tools/jira/jira.sh`.

### Source map (the parts you'll touch)
```
src/main/java/ch/tarvynanalytics/graphs/algos/
  ComparabilityAnalyzer.java   public entry point
  GraphInput.java              public; matrix -> graph (threshold |r|>cut)
  GraphParser.java             THE engine (decomposition + odd-cycle test + count)
  Graph.java, Node.java        mutable, identity-based internal model
  FactorGraphLevel.java        per-level module lists
  ResultBuilder.java           engine state -> immutable model (lifts cycle to originals)
  model/                       immutable result records (public)
  export/                      JsonExporter, DotExporter (zero-dep)
  exception/                   ComparabilityException, InvalidInputException
```

### The thesis (sources for the correctness work — directions 4 & 5)
Located in the sibling repo `/mnt/d/projects/startups/graphs/graphs-transitively-orientable/`:
- `thesis_ro.md` — full Romanian thesis. **Ch. II theorems/lemmas ~lines 560–800**;
  the **C# appendix (`## Anexa`) from ~line 1334** is the original this port mirrors;
  **Ch. III DJIA application ~lines 1080–1300** (worked example to reproduce);
  conclusions ~lines 1284–1300.
- `thesis_en/02_orientable_graphs.md` (theory), `03_application_djia.md` (DJIA),
  `04_conclusions_bibliography.md` (conclusions + reference list).

---

## 1. Findings already established (don't re-discover these)

These were probed empirically/structurally during the initial build. Use them as
the starting facts.

1. **The algorithm is stronger than "odd-hole detection."** The chain machinery
   (`getNonTriangChain` → `continueNonTriangChain` → `createNonTriangApendix` /
   `increaseApendix` / `reverseAppendNonTriangs`) folds chains into closed walks
   that revisit vertices — this resembles Golumbic's **Γ / forcing relation**, not
   just chordless cycles. Evidence: the **net** (triangle with a pendant on each
   vertex — chordal, *no* odd hole, but provably **not** a comparability graph) is
   correctly classified `comparability=false` with a folded walk
   `[3,0,1,4,1,2,5,2,0]` (note the repeated 1 and 2). The **bull** is correctly
   accepted. So the central question in §3-D5 is *whether the folding is a faithful,
   complete forcing-relation test* — early evidence says it is non-trivially
   correct, not a naive hole check.

2. **🔴 KNOWN BUG (priority 0): the 3-sun StackOverflows.** Input: triangle
   `0-1-2` plus outer vertices each adjacent to two triangle vertices
   (`3-0,3-1, 4-1,4-2, 5-0,5-2`). `ComparabilityAnalyzer.analyze` recurses without
   bound (in `createNonTriangApendix` → `increaseApendix`, which can keep growing
   the "apendix" when it revisits vertices and never finds an attachment point).
   The existing `positionToAppend < 0` guard only covers the "cannot grow" case,
   not the "grows forever" case. **The engine must be made total — always
   terminate with a verdict, never crash/hang.** This blocks any correctness sweep
   (§3-D4) because random/structured inputs will hit it. Fix candidates: bound the
   apendix length (it can never need to exceed the module's edge count), or detect
   a repeated `(node, notOriented-edge)` state. Add the 3-sun as a regression test.

3. **Failure "cycles" can be non-simple walks** (repeated vertices, as in the net
   above). `FailureCycle` currently reports them lifted to one representative per
   step. Decide (in D4/D1) whether to (a) keep the raw forcing-walk, (b) reduce it
   to a simple chordless odd cycle when one exists, or (c) expose both. Document
   the contract either way.

4. **Author-vs-cited theorems (matters for D5).** In `thesis_ro.md`, results tagged
   `[5]` (and other bracketed numbers) are **citations from the bibliography**
   (the main theorem — *"a graph is transitively orientable iff every
   non-triangulable cycle has even length"* — is `[5]`, i.e. an established
   result). The results **without a citation bracket are the author's own** and are
   the ones to scrutinize: **Lema 2.28** (~line 738), **Lema 2.211** (~line 790),
   the **Notă after Lema 2.210** (~line 786), and **Algorithm 2.3.1** itself
   (the recursive factor-graph construction). Confirm the marker convention with
   the user before relying on it.

5. **One deliberate deviation from the C#** already exists: in
   `continueNonTriangChain`, when no triangulation-free attach point is found
   (`positionToAppend < 0`) the Java orients the dangling edge directly to
   guarantee progress, where the C# would recurse. Verify whether this ever
   triggers on valid inputs and whether it changes any verdict; document it as
   intended or remove it once the recursion is properly bounded (ties into bug #2).

---

## 2. Operating rules (essentials; full version in CLAUDE.md)

- Branch `feature/GAL-<n>-eb-<short-desc>` off `develop`; PR to `develop`;
  squash-merge (admin bypass ok); `Verify PR` must be green.
- `./mvnw clean verify` before claiming done (80/70 coverage gate enforced).
- Jira: `JIRA_PROJECT=GAL` is the default in `.claude/tools/jira/jira.sh`;
  transition issues In Progress → Done with a PR/commit reference.
- **Faithfulness invariant is currently in force** (CLAUDE.md #2). Directions
  D1/D3/D5 will likely *relax or retire* it deliberately — when you do, update
  CLAUDE.md #2 in the same PR and lock behaviour with characterization tests
  first (see D4). Note: any change to **iteration order** over `adjacentNodes`
  (e.g. `List` → `HashSet`) can change *which* chordless chain / failure walk is
  found (the verdict and count should be invariant, but the reported cycle may
  differ) — preserve insertion order (e.g. `LinkedHashSet`) unless you have the
  oracle in place and accept the change consciously.
- WSL gotcha: new shell scripts / `mvnw` lose the executable bit through the
  DrvFs mount; set it with `git update-index --chmod=+x <file>` or CI fails with
  exit 126.

---

## 3. The five work streams

Each is an epic (D1–D5 map to your directions 1–5). For each: **goal · concrete
starting points · deliverables · complexity · suggested model**.

### D5 — Validate the thesis theory (is the algorithm actually correct?)
- **Goal**: determine whether Algorithm 2.3.1 + the author's lemmas constitute a
  *sound and complete* comparability test, or only a partial one; document gaps.
- **Starting points**: scrutinize the **author-authored** results (finding §1.4):
  Lema 2.28, Lema 2.211, the Notă after 2.210, Algorithm 2.3.1. Take the `[5]`
  main theorem as given. Compare the chain-folding to the standard **Golumbic
  Γ-relation / forcing** algorithm and **Gallai's** forbidden-subgraph
  characterization. Resolve the apparent tension: the net (chordal, no odd hole)
  is non-comparability yet the impl rejects it — explain *why* the folding catches
  it, and find whether any non-comparability graph is *missed* (a false
  "comparability"), or any comparability graph is *wrongly rejected*.
- **Deliverables**: `docs/theory-review.md` — per-lemma assessment, soundness &
  completeness verdict with proof sketches or counterexamples, and a list of
  graph families that confirm/refute it (hand these to D4 as test cases). If a gap
  is found, propose the minimal algorithmic fix.
- **Complexity**: **High (L)** — research/proof-level reasoning, no large diff.
- **Model**: **Opus 4.8.**

### D4 — Correctness of the implementation vs. theory & thesis
- **Goal**: a trustworthy correctness oracle + characterization tests; quantify
  agreement with ground-truth comparability; reproduce the thesis's worked results.
- **Starting points**: (a) **fix bug §1.2 first** (engine must be total) — likely
  the first task. (b) Build a brute-force oracle for small n (try all
  transitive orientations / use the forcing relation directly) and cross-check the
  library's verdict + count on all graphs up to ~7–8 vertices, and on the families
  from D5 (net, bull, 3-sun, suns, complements of cycles, random cographs,
  random Gᵢ). (c) Reproduce the **DJIA example** from thesis Ch. III
  (`03_application_djia.md`, figs ~`image63/64`) and compare the produced
  network/decomposition and the threshold-raising iteration to the thesis.
- **Deliverables**: an oracle test source set; a documented agreement matrix
  (where impl == ground truth, where it diverges and why); the DJIA reproduction
  as an integration test or `docs/` note; decision recorded for the non-simple
  failure-walk contract (§1.3).
- **Complexity**: **Medium-High (M–L).**
- **Model**: **Opus 4.8** for oracle design & the divergence analysis; **Sonnet
  4.6** for grinding out the bulk parametrized tests.

### D1 — Code quality & organization
- **Goal**: make the engine idiomatic and maintainable without changing behaviour
  (guarded by D4's characterization tests).
- **Starting points**: replace identity `List<Node>.contains` patterns (O(n)
  membership everywhere) with order-preserving sets / int-indexed `BitSet`
  adjacency; separate concerns (graph model · module detection · chain forcing ·
  result lifting); rename the `Apendix`/`nonTriangulable` machinery to intent-
  revealing names; make the pool-mutation-during-iteration in `generateMinStables`
  explicit; consider an internal immutable int-indexed graph so `Node` identity
  tricks disappear. Decide the fate of the faithfulness invariant here.
- **Deliverables**: refactored engine, same outputs (or consciously-changed,
  documented ones), CLAUDE.md updated, coverage maintained.
- **Complexity**: **Medium (M).**
- **Model**: **Sonnet 4.6** for the bulk; **Opus 4.8** to design the adjacency/
  graph-representation change (it interacts with iteration order and D3).

### D3 — Performance
- **Goal**: faster single-analysis on realistic sizes; measure before optimizing.
- **Starting points**: add a small benchmark harness (JMH module like
  corrcalc-lib, or a simple timed generator over random correlation matrices /
  threshold sweeps / known-hard graphs). Likely hotspots: O(1) adjacency (shared
  with D1, biggest lever); `canCreateOddCycle` is recomputed fully after *every*
  append in `getNonTriangChain` (O(L²) each → up to O(L³) per chain) — make it
  incremental (test only the new head); memoize `getActualNodes`/`findMinEdge`
  leaf expansions; avoid the chain's quadratic fold-copies.
- **Deliverables**: benchmark harness + baseline numbers; targeted optimizations
  with before/after; complexity notes in javadoc.
- **Complexity**: **Medium (M).**
- **Model**: **Sonnet 4.6**; **Opus 4.8** for the incremental odd-cycle algorithm.

### D2 — Parallelization
- **Goal**: decide *what*, if anything, is worth parallelizing, and do it.
- **Starting points**: the core decomposition is inherently sequential (recursive,
  stateful chain extension, pool mutation) and graphs are small (DJIA ~30, S&P
  ~500), so **intra-analysis parallelism is likely low-value** — confirm with D3's
  benchmarks. The real win is **batch-level**: threshold sweeps, rolling time
  windows, Monte-Carlo / bootstrap runs — embarrassingly parallel across
  independent `analyze` calls. Mirror corrcalc-lib's profile-gated approach
  (parallelism behind a threshold, never silent). Keep the core stateless &
  thread-safe (it already is per-call).
- **Deliverables**: a batch/parallel API (e.g. analyze-many / threshold-sweep) if
  benchmarks justify it, or a documented decision that intra-analysis parallelism
  isn't worth it with evidence.
- **Complexity**: **Low-Medium (S–M).**
- **Model**: **Sonnet 4.6.**

---

## 4. Recommended order (and why)

**P0 → D5 → D4 → D1 → D3 → D2.**

- **P0 (the 3-sun StackOverflow, §1.2)** first, as the opening task of the
  correctness epic: you cannot run a correctness sweep while some inputs crash.
- **D5 before everything substantive**: if the algorithm is unsound/incomplete,
  that changes what you build — never refactor or optimize an algorithm whose
  validity is unknown. Highest uncertainty, highest leverage.
- **D4 next**: it produces the **oracle / characterization tests** that make every
  later change safe. D4 and D5 are tightly coupled — run them as one *correctness
  phase* with D5 leading; D4 turns D5's findings into executable checks.
- **D1 (refactor) before D3 (perf)**: clean structure makes optimization tractable,
  and both share the adjacency-representation rework — do that change once, in D1,
  with perf in mind. Refactor only once D4's net is in place.
- **D3 then D2**: optimize single-threaded first; parallelize last (and probably
  only at the batch level, per D2). Parallelizing before the hotspots are known or
  the algorithm is correct is wasted effort.

If you want parallel tracks: D5 (Opus, research) and the P0 fix (Sonnet) can run
concurrently; D1 and D3 can partially merge (the adjacency change). Keep D2 last.

---

## 5. Jira epics (created)

These epics now exist in project **GAL** (execution-priority order):

| Jira | Direction | Title |
|------|-----------|-------|
| **GAL-5** | D5 | validate the thesis theory |
| **GAL-6** | D4 | implementation correctness vs theory + thesis (oracles, DJIA) |
| **GAL-7** | D1 | code quality & organization |
| **GAL-8** | D3 | performance |
| **GAL-9** | D2 | parallelization |

Work each as `feature/GAL-<n>-eb-<desc>` → PR → `develop`, with the task issues
hung under the relevant epic. The commands used to create the epics, for the
record:

```bash
J=.claude/tools/jira/jira.sh   # default project GAL

$J create --type Epic --labels graphs-algos-lib,theory \
  --summary "D5: validate the thesis theory — is Algorithm 2.3.1 a sound & complete comparability test?" \
  --description "Scrutinize the author-authored results (Lema 2.28, Lema 2.211, the Nota after 2.210, Algorithm 2.3.1; bracketed [5] etc. are citations). Relate the chain-folding to Golumbic's forcing relation and Gallai's forbidden subgraphs. Explain why the net is correctly rejected; find any missed non-comparability or wrongly-rejected comparability graph. Output docs/theory-review.md with soundness/completeness verdict and counterexample/confirming families. Suggested model: Opus 4.8. Complexity: High."

$J create --type Epic --labels graphs-algos-lib,correctness \
  --summary "D4: implementation correctness vs theory + thesis (oracles, DJIA reproduction)" \
  --description "FIRST fix the 3-sun StackOverflow (engine must be total). Build a brute-force transitive-orientation oracle for small n; cross-check verdict+count against ground truth on n<=7-8 and on D5 families. Reproduce the thesis DJIA example (Ch. III) and its threshold-raising iteration. Decide the failure-walk contract (non-simple walks). Suggested model: Opus 4.8 (design) + Sonnet 4.6 (bulk tests). Complexity: Medium-High."

$J create --type Epic --labels graphs-algos-lib,refactor \
  --summary "D1: code quality & organization of the engine" \
  --description "Replace identity List.contains with order-preserving sets / int-indexed BitSet adjacency; separate graph model / module detection / chain forcing / result lifting; rename the apendix/non-triangulable machinery; make pool mutation explicit; consider an immutable int-indexed internal graph. Preserve behaviour (guarded by D4) or document conscious changes; update CLAUDE.md faithfulness invariant. Suggested model: Sonnet 4.6 (Opus 4.8 for the graph-representation design). Complexity: Medium."

$J create --type Epic --labels graphs-algos-lib,performance \
  --summary "D3: performance — measure then optimize single-analysis" \
  --description "Add a benchmark harness (JMH or simple) with realistic generators. Make canCreateOddCycle incremental (avoid O(L^3) recompute), memoize getActualNodes/findMinEdge, adopt O(1) adjacency (shared with D1), avoid quadratic chain fold-copies. Report before/after. Suggested model: Sonnet 4.6 (Opus 4.8 for the incremental odd-cycle algorithm). Complexity: Medium."

$J create --type Epic --labels graphs-algos-lib,parallelism \
  --summary "D2: parallelization — decide what's worth it, act on it" \
  --description "Core is sequential and graphs are small; confirm with D3 benchmarks that intra-analysis parallelism is low-value. Deliver batch-level parallelism (threshold sweeps, rolling windows, Monte-Carlo) over independent analyze calls, profile-gated like corrcalc-lib, or a documented decision against intra-analysis parallelism with evidence. Suggested model: Sonnet 4.6. Complexity: Low-Medium."
```

---

## 6. Quick reference — model & complexity summary

Execution order top-to-bottom.

| Jira | Dir | Epic | Complexity | Model | Depends on |
|------|-----|------|-----------|-------|-----------|
| (GAL-6, 1st task) | P0  | make engine total — fix 3-sun crash | Small | Sonnet 4.6 | — |
| **GAL-5** | D5  | Validate thesis theory | High | **Opus 4.8** | — |
| **GAL-6** | D4  | Implementation correctness + oracles | Med-High | Opus 4.8 + Sonnet 4.6 | P0, D5 |
| **GAL-7** | D1  | Code quality / refactor | Medium | Sonnet 4.6 (+Opus design) | D4 |
| **GAL-8** | D3  | Performance | Medium | Sonnet 4.6 (+Opus algo) | D1 |
| **GAL-9** | D2  | Parallelization | Low-Med | Sonnet 4.6 | D3 |
