# Plan: near-linear modular decomposition for large graphs (CGD-16)

A start-here implementation plan for replacing the modular-decomposition
**prime-case** hotspot with a near-linear algorithm, size-gated so small graphs
keep the simpler low-constant code. Read `docs/performance.md` (the measured
hotspot) and `CLAUDE.md` invariant #2 (correctness over the brute-force oracle)
first.

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

## 3. Which algorithm

**Tedder, Corneil, Habib & Paul (2008)** — the standard *simpler* linear-time MD
(recursive partition refinement / factorizing permutation). Fallbacks if it's too
much: Habib & Paul's near-linear O(n+m·α)/O(n+m·log n) variants, or McConnell &
Spinrad (1999). Strongly prefer porting from a known-correct reference
implementation and validating it differentially (§6) rather than coding from the
paper cold.

## 4. Recommended design — build an explicit tree once

Today `orientationCount()` and `levels()` each recurse through `rootPartition`
independently (so comparability graphs decompose twice). Unify first:

```
MDNode { Type type (PARALLEL|SERIES|PRIME|LEAF); List<MDNode> children; int[] vertices }
MDNode buildTree()                  // swappable: simple recursion OR linear
BigInteger orientationCount()       // fold over the tree: SERIES k!, PARALLEL 1, PRIME 2, LEAF 1
List<FactorGraphLevelView> levels() // derive from the tree (keep current presentation, §7)
```

Building the tree **once** and deriving both outputs removes the double work and
makes the algorithm a single swappable function `buildTree()`.

## 5. Steps (each independently mergeable, all oracle-guarded)

1. **Refactor to a tree (behaviour-preserving).** Introduce `MDNode`; implement
   `buildTree()` using the *current* parallel/series/prime recursion; make
   `orientationCount()` and `levels()` consume the tree. `OracleCharacterizationTest`
   and all existing tests must pass unchanged. No new algorithm yet — this just
   decouples "compute the tree" from "use the tree."
2. **Add `buildTreeLinear()`** — the Tedder et al. builder, producing the same
   `MDNode` shape.
3. **Differential test** (§6) — gate-free, compares the two builders. Land it with
   step 2.
4. **Size gate** — `buildTree()` dispatches on n to simple vs linear.
5. **Benchmark + docs** — pick `THRESHOLD`, update `docs/performance.md` with new
   before/after numbers; note the gate constant.

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
- **Zero dependencies** (invariant #1) — hand-roll it; no graph library.
- Don't regress small-n latency — that's the whole point of the gate.

## 8. Acceptance criteria

- `OracleCharacterizationTest` green, unchanged.
- New differential test (simple vs linear) green over the random corpus + families.
- `Benchmark`: large dense-prime case materially faster (target: n=150 p=0.5 from
  ~2 s to well under it); small-n (≤ gate) shows no regression.
- `docs/performance.md` updated with the new numbers and the chosen `THRESHOLD`.
- Public API, `model`, exporters, and `ForcingRelation` unchanged.

## 9. References

- M. Tedder, D. Corneil, M. Habib, C. Paul, *"Simpler Linear-Time Modular
  Decomposition via Recursive Factorizing Permutations,"* ICALP 2008.
- M. Habib, C. Paul, *"A survey of the algorithmic aspects of modular
  decomposition,"* Computer Science Review, 2010.
- R. McConnell, J. Spinrad, *"Modular decomposition and transitive orientation,"*
  Discrete Mathematics, 1999.
- M. Golumbic, *Algorithmic Graph Theory and Perfect Graphs* — the Γ forcing
  relation (the verdict side, already implemented in `ForcingRelation`).
