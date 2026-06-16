# Performance notes (CGD-8 / D3)

Single-analysis performance of the Golumbic Γ + modular-decomposition engine.
Reproduce with the committed harness:

```bash
./mvnw test-compile
java -cp target/classes:target/test-classes \
  ch.tarvynanalytics.graphs.comparability.Benchmark
```

## Where the time goes

`analyze()` is two phases: the **Γ verdict** (`ForcingRelation`, ~O(m·n)) and the
**modular decomposition** (`ModularDecomposition`, used for the count and the
factor-graph levels). A per-phase breakdown on dense random graphs:

| n   | comparable | Γ verdict | modular levels |
|-----|-----------|-----------|----------------|
| 60  | false     | ~3.5 ms   | ~29 ms         |
| 100 | false     | ~16 ms    | ~360 ms        |

The verdict is cheap; the cost is the modular decomposition's **prime-case**
handling — `maximalModularPartition` → `minimalModule` — which only fires when a
(sub)graph is *prime* (neither disconnected nor co-disconnected). Series/parallel
structure (all cographs, most thresholded correlation networks) skips it entirely.

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

## Known limitation / next lever

The maximal-modular-partition is still **O(n⁴)** on a *fully prime* graph
(e.g. uniform-random p=0.5 at n ≳ 100 → ~0.4–2 s) because it makes O(n²)
`minimalModule` calls. This is not the target workload (correlation networks
threshold down to sparse, structured graphs), so it is left as-is. The fix, if
ever needed, is a near-linear modular-decomposition algorithm (partition
refinement); guard any such change with `OracleCharacterizationTest`.

## Parallelism (CGD-9 / D2)

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
