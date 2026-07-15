# Performance notes (GAL-8 / D3)

Single-analysis performance of the Golumbic Γ + modular-decomposition engine.
Reproduce with the committed harness:

```bash
./mvnw test-compile
java -cp target/classes:target/test-classes \
  ch.tarvynanalytics.graphs.algos.Benchmark
```

## Where the time goes

`analyze()` is two phases: the **Γ verdict** (`ForcingRelation`, ~O(m·n)) and the
**modular decomposition** (`ModularDecomposition`, used for the count and the
factor-graph levels). A per-phase breakdown on dense random graphs (with the GAL-16
size gate active, so n ≥ 50 uses the near-linear builder):

| n   | comparable | Γ verdict | modular levels |
|-----|-----------|-----------|----------------|
| 60  | false     | ~3 ms     | ~3 ms          |
| 100 | false     | ~18 ms    | ~3 ms          |

The modular decomposition is now cheap at every size; the dominant cost on dense
graphs is the Γ verdict. Historically the modular decomposition's **prime-case**
handling was the hotspot — `maximalModularPartition` → `minimalModule` fires only
when a (sub)graph is *prime* (neither disconnected nor co-disconnected), and was
**O(n⁴)** there (~360 ms at n=100). The GAL-16 near-linear `fracture` builder,
size-gated in, removes it (see below). Series/parallel structure (all cographs,
most thresholded correlation networks) was always cheap and skips the prime case
entirely.

## Optimization applied

`minimalModule` (the module-closure used to split a prime node into its maximal
modules) previously restarted a full O(n) membership scan after every absorbed
vertex — O(n³) per call. It now keeps an incremental adjacency counter so each
membership test is O(1) → **O(n²) per call**, and `maximalModularPartition`
absorbs an entire discovered module at once instead of re-testing its members.

Before → after (best of 30):

| workload                       | before  | after   |
|--------------------------------|---------|---------|
| threshold sweep, n=60 (real)   | ~50 ms  | ~28 ms  |
| random cograph, n=150 (comp.)  | ~3 ms   | ~3 ms   |
| dense random p=0.5, n=60       | ~64 ms  | ~29 ms  |
| dense random p=0.5, n=100      | ~451 ms | ~358 ms |

The realistic paths (threshold sweeps, structured/comparability graphs) are
comfortably fast at the target sizes (DJIA ≈ 30; correlation networks up to a few
hundred). Cographs at n=150 stay ~3 ms.

## Near-linear modular decomposition, size-gated (GAL-16)

The former **O(n⁴)** prime-case limitation is **resolved**. `ModularDecomposition`
now has a second builder, `buildTreeLinear()` — a Java port of the `fracture`
algorithm from
[`jonasspinner/modular-decomposition`](https://github.com/jonasspinner/modular-decomposition)
(MIT): a factorizing permutation by partition refinement, parenthesized into the
fracture tree and read off as the canonical parallel/series/prime decomposition.
It produces the *same* tree as the simple recursion (pinned over ~10.8k random
graphs by `ModularDecompositionLinearDifferentialTest`, itself transitively
oracle-guarded), so it is a pure performance switch.

A **size gate** picks the builder per graph: the low-constant simple recursion
below `ModularDecomposition.LINEAR_THRESHOLD` (= **50** vertices), the near-linear
builder at or above it. The threshold comes from this crossover sweep on dense
(p=0.5, mostly prime) random graphs — the simple builder's worst case:

| n   | simple build | linear build | speedup |
|-----|-------------:|-------------:|--------:|
| 20  | 0.34 ms     | 0.05 ms      | 7×      |
| 40  | 4.1 ms      | 0.24 ms      | 17×     |
| 50  | 10.2 ms     | 0.38 ms      | 27×     |
| 60  | 26.3 ms     | 0.61 ms      | 43×     |
| 80  | 128 ms      | 1.16 ms      | 110×    |
| 100 | 351 ms      | 1.93 ms      | 182×    |
| 150 | 2058 ms     | 4.69 ms      | 439×    |
| 200 | 6723 ms     | 10.1 ms      | 667×    |

Below ~50 both builders are sub-millisecond, so small graphs (DJIA ≈ 30) keep the
trusted low-constant recursion with no latency regression; above it the linear
builder caps what was an O(n⁴) blow-up (n=150 from ~2 s to ~5 ms). The
factorizing-permutation algorithm is inherently sequential, so it is not
parallelised; batch-level parallelism stays in `BatchAnalyzer` (below). The
implementation plan and step history are in
[`docs/linear-md-plan.md`](linear-md-plan.md).

## Parallelism (GAL-9 / D2)

**Intra-analysis parallelism is not pursued** — the decision, with evidence:

- A single analysis is already fast at the target sizes (≈30 ms at n=60 above),
  and the engine is inherently sequential and stateful (the Γ implication-class
  BFS and the recursive modular decomposition), so splitting one analysis would
  add coordination overhead for little gain.
- The worthwhile parallelism is **batch-level**: threshold sweeps, rolling time
  windows and Monte-Carlo / bootstrap runs are independent `analyze` calls.
  `analyze` is stateless and thread-safe, so `BatchAnalyzer.*Parallel` simply
  fans them over the common `ForkJoinPool` (gated to stay sequential below
  `MIN_PARALLEL_BATCH` to avoid fork/join overhead on tiny batches).
