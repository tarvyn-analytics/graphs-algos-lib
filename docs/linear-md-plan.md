# Plan: near-linear modular decomposition for large graphs (CGD-16)

A start-here implementation plan for replacing the modular-decomposition
**prime-case** hotspot with a near-linear algorithm, size-gated so small graphs
keep the simpler low-constant code. Read `docs/performance.md` (the measured
hotspot) and `CLAUDE.md` invariant #2 (correctness over the brute-force oracle)
first.

## 0. Status — start here

Progress against §5 (update this section whenever a step lands):

- [x] **Step 1 — tree refactor.** Done, PR #11 (squash `5c26dc8`).
  `ModularDecomposition` builds one cached decomposition tree (`MDNode` +
  `buildTree()`); `orientationCount()` folds it and `levels()` takes its level-0
  partition from it (`rootBlocks`). Behaviour unchanged; oracle + full suite green.
- [x] **Step 2 — `buildTreeLinear()`**, ported from the `fracture` algorithm in
  `jonasspinner/modular-decomposition` (§3). Done, PR #16. A Java port of the readable
  `crates/fracture/src/base.rs`: factorizing permutation by partition refinement →
  parenthesizing → dummy-node pruning → parallel/series/prime read-off into `MDNode`.
  Wired as an alternative path (`useLinearBuilder()`); the simple recursion stays the
  default (the gate is step 4). No runtime dependency added.
- [x] **Step 3 — differential test.** Done, PR #16.
  `ModularDecompositionLinearDifferentialTest` pins the linear builder against the
  simple recursion over ~10.8k random graphs (n = 6..14) + named families, comparing the
  canonical order-invariant tree signature (subsumes count + level-0 partition) and the
  orientation count.
- [x] **Step 4 — size gate.** Done, PR #17. `ModularDecomposition` picks the builder by
  size: simple recursion below `LINEAR_THRESHOLD` (= 50 vertices), the near-linear
  `fracture` builder at or above it (`usesLinearBuilder()`; `useSimpleBuilder()` /
  `useLinearBuilder()` force either side for tests). Pure performance switch — the two
  builders are proven equivalent. `ModularDecompositionSizeGateTest` pins the flip.
- [x] **Step 5 — benchmark + docs.** Done, PR #18. `Benchmark` gained a simple-vs-linear
  builder sweep; `docs/performance.md` records the before/after numbers (n=150 dense from
  ~2 s to ~5 ms, 439×) and the gate constant. **All steps complete — CGD-16 done.**

Each step is one small PR off `develop`, oracle-guarded. To keep a session's
context small, read only **this plan + the files named under the step in §5** —
not the whole codebase. `ForcingRelation` (the Γ verdict) is never touched.

## 1. Why / scope

`ModularDecomposition` decides, for each (sub)graph, whether it is **parallel**
(disconnected), **series** (co-disconnected) or **prime**, and splits a prime
node into its maximal strong modules. Parallel/series are detected in O(n²) by
connectivity; the **prime case** uses `maximalModularPartition` → `minimalModule`,
which is O(n²)/call and ~**O(n⁴)** for a fully prime graph (uniform-random p=0.5,
n ≳ 100 → ~0.4–2 s). That is the only slow path. It feeds **both** the
orientation count and the factor-graph levels.

Goal: keep correctness exactly (oracle-guarded), make large/dense-prime graphs
fast. The comparability **verdict** lives in `ForcingRelation` (Golumbic Γ) and is
**out of scope** — do not touch it.

## 2. Decision: one algorithm, size-gated (not per-graph-type)

- Use a single near-linear MD; it is O(n+m) on **all** graph families, so it
  dominates asymptotically everywhere. Do **not** build a per-type zoo.
- It has higher constant factors, so for small n (DJIA-scale ≈ 30) the current
  closure is often faster. Gate by **size**: `n < THRESHOLD` → current recursion;
  else → near-linear. Pick `THRESHOLD` empirically with the `Benchmark` harness
  (expect somewhere in 50–150).
- The algorithm is inherently **sequential** (partition refinement has tight data
  dependencies) — do not try to parallelise it. Cross-analysis parallelism stays
  in `BatchAnalyzer`.

## 3. Which algorithm — decision (research 2026-06-16)

**Port the `fracture` algorithm from
[`jonasspinner/modular-decomposition`](https://github.com/jonasspinner/modular-decomposition)
(Rust).** A library survey found it has the cleanest API to port and that
`fracture` benchmarked best on most instances. Reimplement its MD into our `MDNode`
shape (§4) and validate it differentially against the simple recursion (§6).

- **Scale fallback:** keep
  [`mogproject/modular-decomposition`](https://github.com/mogproject/modular-decomposition)
  (C++/Python) as a reference to consult *only if* very large graphs turn out slow
  after the port.
- **This is a port, not a dependency** — translate the algorithm into Java, add no
  Rust/native runtime dep. Check the source licence before copying code directly;
  reimplementing from the algorithm's description is always fine.

Background theory (not the port target): Tedder, Corneil, Habib & Paul (2008),
*simpler* linear-time MD via recursive factorizing permutations; Habib & Paul's
survey; McConnell & Spinrad (1999). See §9.

## 4. Recommended design — build an explicit tree once

**Done in step 1.** `orientationCount()` and `levels()` used to recurse through
`rootPartition` independently (so comparability graphs decomposed twice); they now
share one cached tree. The shape that landed:

```
MDNode { Type type (PARALLEL|SERIES|PRIME|LEAF); List<MDNode> children; int[] vertices }
MDNode buildTree()                  // swappable: simple recursion OR linear
BigInteger orientationCount()       // fold over the tree: SERIES k!, PARALLEL 1, PRIME 2, LEAF 1
List<FactorGraphLevelView> levels() // derive from the tree (keep current presentation, §7)
```

Building the tree **once** and deriving both outputs removes the double work and
makes the algorithm a single swappable function `buildTree()`.

## 5. Steps (each independently mergeable, all oracle-guarded)

Each step lists the files a fresh session needs — read only those plus this plan.

1. ✅ **Refactor to a tree (behaviour-preserving).** Done (PR #11, `5c26dc8`).
   `MDNode` + `buildTree()` in `ModularDecomposition`; `orientationCount()` folds the
   tree and `levels()` uses `rootBlocks(tree())` for level 0. The tree is the single
   swappable function; no algorithm change; oracle + suite unchanged.
2. ✅ **Add `buildTreeLinear()`** — done (PR #16). Java port of the `fracture` algorithm
   from `jonasspinner/modular-decomposition` (the readable `crates/fracture/src/base.rs`),
   producing the same `MDNode` shape (`Kind` LEAF/PARALLEL/SERIES/PRIME). Wired as an
   alternative path via `useLinearBuilder()`; the simple recursion stays the default (no
   gate yet — that's step 4). No runtime dependency added.
3. ✅ **Differential test** — done (PR #16). `ModularDecompositionLinearDifferentialTest`
   asserts `buildTreeLinear` yields the same canonical decomposition tree (an
   order-invariant `treeSignature()` — node kinds + sorted child signatures) *and* the
   same orientation count as the simple recursion over ~10.8k random graphs n=6…14 plus
   the named families. The tree signature is the right invariant: it subsumes the count
   and the level-0 partition, but a node's child *display order* is not canonical, so the
   order-dependent deeper levels are not directly comparable across builders (§7).
4. ✅ **Size gate** — done (PR #17). `ModularDecomposition` dispatches on n: simple
   `< LINEAR_THRESHOLD (= 50) ≤` linear. `ModularDecompositionSizeGateTest` pins the flip
   and the auto path's correctness around/above the threshold.
5. ✅ **Benchmark + docs** — done (PR #18). `Benchmark.builderComparison()` sweeps simple
   vs linear on dense graphs; `LINEAR_THRESHOLD = 50` was chosen from the crossover;
   `docs/performance.md` records the before/after numbers and the gate constant.

## 6. Safety net (the key to trusting a tricky MD)

- `OracleCharacterizationTest` already pins **verdict + count** vs brute force for
  every labeled graph n ≤ 5 — must stay green.
- Add **differential testing**: for many random graphs (e.g. 10 000 graphs at
  n = 6…14, plus the named families), assert `buildTreeLinear()` yields the **same
  orientation count and the same levels** as the simple recursion. Random
  differential testing against the trusted simple implementation is how you catch
  prime-case bugs the small oracle can't reach.
- Optional: a canonical tree-equality check (normalise child order) for an even
  stricter comparison.

## 7. Pitfalls

- **Levels presentation must not change.** Tests assert the degenerate flattening:
  `completeGraph_IsOneCliqueModule` (K4 → one `CLIQUE` module of size 4),
  `twoDisjointEdges_…` (2K₂ → two `CLIQUE` modules then an `INDEPENDENT_SET`),
  etc. The tree→levels mapping must reproduce `ModularDecomposition.levelBlocks`'s
  current rules (series-all-singletons → one CLIQUE; parallel-all-singletons → one
  INDEPENDENT_SET; otherwise one module per child typed by `classify`). Keep this
  mapping in one place and test it.
- **Count node factors are exact:** SERIES `k!`, PARALLEL `1`, PRIME `2`, LEAF `1`.
  PRIME = 2 relies on the graph already being a comparability graph — true because
  `orientationCount()` is only called when `ForcingRelation` says comparable. Keep
  that contract.
- **Determinism / ordering.** A new algorithm may emit modules/children in a
  different order. If any test asserts a specific order, normalise the output or
  update the test consciously (the count and verdict are order-invariant; only the
  display order can shift).
- **Lean dependencies** (invariant #1) — prefer hand-rolling; add a graph library
  only if it clearly beats porting a reference, and keep the footprint small.
- Don't regress small-n latency — that's the whole point of the gate.

## 8. Acceptance criteria

- `OracleCharacterizationTest` green, unchanged.
- New differential test (simple vs linear) green over the random corpus + families.
- `Benchmark`: large dense-prime case materially faster (target: n=150 p=0.5 from
  ~2 s to well under it); small-n (≤ gate) shows no regression.
- `docs/performance.md` updated with the new numbers and the chosen `THRESHOLD`.
- Public API, `model`, exporters, and `ForcingRelation` unchanged.

## 9. References

- **Port target:** `jonasspinner/modular-decomposition` (Rust, the `fracture`
  algorithm) — <https://github.com/jonasspinner/modular-decomposition>
- **Scale fallback reference:** `mogproject/modular-decomposition` (C++/Python) —
  <https://github.com/mogproject/modular-decomposition>
- M. Tedder, D. Corneil, M. Habib, C. Paul, *"Simpler Linear-Time Modular
  Decomposition via Recursive Factorizing Permutations,"* ICALP 2008.
- M. Habib, C. Paul, *"A survey of the algorithmic aspects of modular
  decomposition,"* Computer Science Review, 2010.
- R. McConnell, J. Spinrad, *"Modular decomposition and transitive orientation,"*
  Discrete Mathematics, 1999.
- M. Golumbic, *Algorithmic Graph Theory and Perfect Graphs* — the Γ forcing
  relation (the verdict side, already implemented in `ForcingRelation`).
