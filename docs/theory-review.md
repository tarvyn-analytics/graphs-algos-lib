# Theory review — is Algorithm 2.3.1 a sound & complete comparability test?

*CGD-5 (direction D5). Companion to `NEXT_SESSION.md`. The empirical claims here
are reproduced by a brute-force oracle sweep; the throwaway harness used is
described in §2 and is not committed.*

## TL;DR verdict

| Property | Verdict |
|---|---|
| The **criterion** the thesis rests on (Theorem 2.22) | **Correct** — it is Golumbic's forcing-relation theorem; cited result `[5]`, take as given. |
| **Algorithm 2.3.1** + the current implementation as a *decision* procedure | **Neither sound nor complete** beyond 5 vertices. |
| — verdict on all graphs with **n ≤ 5** | **Exact** (0 false positives, 0 false negatives over all 1 099 labeled graphs). |
| — verdict on **n = 6** | **43 false positives** (accepts non-comparability graphs) **+ 176 false negatives** (rejects comparability graphs) out of 32 768 labeled graphs. |
| The **orientation count** | **Wrong (always an under-count)** from n = 4; correct only for cliques, prime graphs and other non-substitution structures. |

The headline: the *mathematics* in the thesis is sound (it is a restatement of
Golumbic / Gilmore–Hoffman). The author's **operationalisation** of it —
Algorithm 2.3.1's bespoke chain-folding plus the local "odd-cycle" check, and
the library's multiplicative orientation count — is an **incomplete and unsound
search** that happens to be exact on the small/structured graphs the thesis and
the test-suite exercise. This explains why the library "worked" on its fixtures
yet misclassifies the 3-sun (see CGD-10).

---

## 1. Scope, sources, and the citation convention

Sources (sibling repo `graphs-transitively-orientable`):
`thesis_en/02_orientable_graphs.md` (theory), `03_application_djia.md`,
`04_conclusions_bibliography.md`, and `thesis_ro.md` (the C# appendix this engine
ports).

**Citation convention — confirmed, not assumed.** The bibliography
(`04_conclusions_bibliography.md`) is an ordered list whose **5th entry** is

> Shevrin L. N., Filipov N. D. — *"Partially Ordered Sets and Their
> Comparability Graphs"*, Sibirsky Matematichesky Zhurnal, 1970.

So a result tagged `[5]` is **cited from Shevrin–Filipov** (the comparability-graph
paper), and results printed **without a bracket are the author's own**. This
matches the working assumption in `NEXT_SESSION.md` §1.4 and is used below
without further hedging. The author-authored results to scrutinise are therefore
**Lemma 2.28, Lemmas 2.211–2.214, Definition 2.28, the *Note* after Lemma 2.210,
and Algorithm 2.3.1**; everything tagged `[5]` (including the criterion
Theorem 2.22 and Lemma 2.26) is established graph theory.

---

## 2. Method — a brute-force ground-truth oracle

Ground truth is computed directly from the definition of a comparability graph
(Definition 2.128–2.129): a graph is transitively orientable iff some orientation
of its edges is transitive. The oracle enumerates **all 2^m orientations** of the
m edges and counts those satisfying transitivity (`a→b ∧ b→c ⇒ a→c`). It is
trivially correct; it was sanity-checked (K₃ → 6, C₅ → 0, diamond → 6, 3-sun → 0).

Sweeps run:

* **Exhaustive, with exact counts**, over *every labeled graph* on n = 1..5
  (1 099 graphs).
* **Exhaustive, verdict only**, over every labeled graph on n = 6 (32 768 graphs).
* **Named families** up to n = 8 (paths, even/odd cycles, complete bipartite,
  complements of cycles, bull, net, gem, domino, 3-sun S₃, 4-sun S₄).

For each graph we compare `ComparabilityAnalyzer.analyze(...)` against the oracle
on (a) the comparability verdict and (b) the orientation count.

---

## 3. The underlying theory *is* Golumbic's forcing relation

The thesis's central objects map one-to-one onto the standard machinery:

* **Non-triangulable chain** (Def 2.24): `[x₀,…,xₙ]` with `xᵢ ≁ xᵢ₊₂` for all i
  (repeats `xᵢ = xᵢ₊₂` allowed). Two consecutive edges `(xᵢ,xᵢ₊₁)` and
  `(xᵢ₊₁,xᵢ₊₂)` share `xᵢ₊₁` with non-adjacent far endpoints — i.e. they are
  **Γ-related** in Golumbic's *forcing relation* Γ. A non-triangulable chain is a
  **Γ-forcing chain**.
* **Non-triangulable cycle** (Def 2.26): a closed Γ-forcing chain with the wrap
  condition `xₙ₋₁ ≁ x₁`. An **odd** such cycle is exactly an edge that Γ forces
  into **both** orientations.
* **Theorem 2.22 `[5]`**: *transitively orientable ⇔ every non-triangulable cycle
  is even.* This is **Golumbic's theorem** (no implication class contains an edge
  and its reverse) / Gilmore–Hoffman. It is **sound and complete**, and we take it
  as given.

A practical consequence that the empirics confirm: **the obstruction need not be
a chordless odd hole.** The 3-sun and the net are *chordal* (no induced cycle ≥ 4)
yet non-comparability — their obstruction is a *folded* odd Γ-walk that revisits
vertices. So any faithful test must search closed forcing **walks**, not just
induced cycles. The thesis is right about this; the algorithm's search for those
walks is where things break.

---

## 4. Per-result assessment

| Result | Author? | Assessment |
|---|---|---|
| **Thm 2.22** — even-cycle criterion `[5]` | cited | **Correct** (Golumbic). The whole approach is valid in principle. |
| **Lemma 2.26** — prime + all-even ⇒ exactly 2 orientations `[5]` | cited | **Correct.** Source of the "factor 2 per prime module" the count uses. |
| **Lemma 2.23** — `S(a,b)` = vertices reachable by NT chains `[5]` | cited | **Correct** (forcing/stable-closure). |
| **Lemma 2.28** — orient via a vertex-covering NT chain `F` | **author** | **Claim plausible, proof has a gap.** It argues "an edge gets two orientations ⇔ d(x₀,xᵢ) has both parities ⇔ an odd cycle exists" without rigour, and it relies on `F` *covering every edge*. But the construction it cites (Corollary of 2.26, lines 321–331) only guarantees `F` reaches every **vertex**, and its own *Remark* (line 331) admits `F` "does not necessarily pass through all edges." The vertex-cover/edge-cover gap is precisely where the implementation's edge-covering extension (`continueNonTriangChain`) goes wrong (§5.3). |
| **Lemmas 2.211–2.214** — Kₙ orientations ⇔ Hamiltonian chains, `\|Γ\| = n!` | **author** | **Correct.** The clique count `n!` is reproduced exactly by the engine for every Kₙ tested (n ≤ 6). |
| **Lemma 2.210** + **Note** — module orientation is independent of the quotient's | cited (+ author Note) | Existence direction **correct** (corollary: orientable modules + orientable quotient ⇒ orientable G). But independence of *choice* does **not** imply **multiplicativity of counts** over this decomposition — the library over-extended it (§5.4). |
| **Thm 2.21** — unique decomposition into max-complete + min-stable subgraphs `[5]` | cited | Correct as a *decomposition* theorem; it is **not** the canonical modular-decomposition tree, and using it for counting is unsafe (§5.4). |
| **Algorithm 2.3.1** — the recursive procedure | **author** | **Incomplete and (as implemented) unsound** as a decision procedure (§5.2–5.3); Step 5's chain construction can also fail to terminate (the 3-sun stack overflow fixed in CGD-10). |

---

## 5. Empirical findings — four classes

### 5.1 The verdict is exact for n ≤ 5 — why the thesis "worked"

Over **all 1 099 labeled graphs** on ≤ 5 vertices: **0 false positives, 0 false
negatives.** Every comparability graph is accepted and every non-comparability
graph (the C₅ and its 11 labeled supergraphs that stay non-comparability) is
rejected. The thesis's worked examples and the library's hand-built fixtures
(cliques, paths, even/odd cycles, cographs, K₂,₃) all live in this regime, which
is why the gaps below were never observed before.

### 5.2 Incompleteness — false positives (accepts non-comparability graphs)

At **n = 6 there are 43 false positives** out of 32 768. The minimal and cleanest
witness is the **3-sun S₃ (Hajós graph)**:

```
triangle 0-1-2;  outer 3~{0,1}, 4~{1,2}, 5~{0,2}
oracle: 0 transitive orientations  →  NOT a comparability graph
engine: comparability = true, count = 2          ← WRONG (see CGD-10)
```

S₃ is *prime* (no non-trivial module), so the algorithm tries to cover all its
edges with one non-triangulable chain and test for an odd cycle. It fails to
assemble the genuine odd forcing-walk (the cover construction does not terminate;
after the CGD-10 totality fix it bails and declares success). The search is
**incomplete**: a real odd Γ-walk exists (guaranteed by Thm 2.22) but the
greedy single-chain construction never finds it.

Notably the closely related **4-sun S₄, the net, the bull and the gem are all
classified correctly** — so the gap is specific to *which* folded walks the
heuristic happens to construct, not to "chordal non-comparability graphs" in
general.

### 5.3 Unsoundness — false negatives (rejects comparability graphs)

At **n = 6 there are 176 false negatives** out of 32 768 — graphs the engine
**rejects** although they *are* comparability graphs. Verified witness:

```
edges {0-1,0-2,0-3,0-4,1-2,1-4,1-5,2-3}
oracle: 2 transitive orientations  →  IS a comparability graph
        e.g.  0→1 0→2 0→3 0→4 2→1 2→3 4→1 5→1   (checked transitive)
engine: comparability = false
engine's "obstructing cycle": [4,0,2,0,4,1,2,3,2,1,5,1,0,3,0]
```

The reported "odd non-triangulable cycle" is a degenerate closed **walk** that
revisits 0 four times, 2 three times, etc. It is **not** a valid non-triangulable
cycle in the sense of Def 2.26. Root cause: `canCreateOddCycle` is run on the
chain **after** `continueNonTriangChain` has folded in extra edges (the
"apendix" machinery); that extension breaks the non-triangulability invariant,
and the local odd-cycle test then fires on a spurious walk. So the engine
produces **invalid certificates of non-comparability** — it is unsound. (This is
the edge-cover/vertex-cover gap flagged for Lemma 2.28 in §4.)

### 5.4 The orientation count is wrong — always an under-count

The count diverges from ground truth **from n = 4** and is **always an
under-count** (never over):

| n | comparability graphs | count mismatches | direction |
|---|---|---|---|
| ≤ 3 | 11 | 0 | — |
| 4 | 64 | 6 (~9 %) | all under |
| 5 | 1 012 | 150 (~15 %) | all under |

Minimal witness — the **diamond** `K₄ − e = K_{1,1,2}` (`{0-1,0-2,0-3,1-2,1-3}`,
2 ≁ 3):

```
oracle: 6 transitive orientations          engine: 4
```

The diamond is the join of `{0}, {1}, {2,3}`; canonically it is one *series*
node with **three** children, giving `3! = 6`. The engine instead applies
Theorem 2.21: it merges `{0,1}` into a maximal *clique* module (`2!`) joined,
through a K₂ quotient (`2!`), to the empty module `{2,3}` (factor 1) — yielding
`2·2 = 4`. The library's count
`∏ levels [ 2^(#min-stable) · ∏ factorial(clique size) ]` is **not multiplicative
over this decomposition**: coarsening a join's parts into a maximal clique trades
the series node's `k!` for a strictly smaller product, so the count can only
shrink — hence the systematic under-count. (The thesis itself only rigorously
*counts* Kₙ, via Lemma 2.214; the general product formula is the library's
extrapolation and is the part that is wrong.)

---

## 6. Soundness & completeness verdict

* **Criterion (Theorem 2.22): sound and complete.** It is Golumbic's theorem; the
  non-triangulable-cycle language is the forcing relation Γ. No defect.
* **Algorithm 2.3.1 / current implementation as a decision procedure: neither
  sound nor complete.**
  * *Incomplete* — misses real odd forcing-walks (43/32 768 false positives at
    n = 6; 3-sun is the minimal case). The single greedy chain per module does
    not explore the full Γ-implication closure.
  * *Unsound* — the post-folding `canCreateOddCycle` fires on walks that are not
    non-triangulable cycles, rejecting genuine comparability graphs
    (176/32 768 false negatives at n = 6).
  * Exact only for **n ≤ 5** and for the structured families it was designed
    around.
* **Orientation count: incorrect** (systematic under-count from n = 4); reliable
  only for cliques, prime graphs, disjoint unions and other non-substitution
  structures.

The library's README scope note ("certifies non-comparability through the odd-hole
obstruction … exact on the graph families it was built for") **overstates**
reliability: it is exact only up to n = 5 (verdict) and gives an unsafe count even
on the 4-vertex diamond. The README/CLAUDE invariant #2 ("faithful to the thesis
algorithm") is faithful to a procedure that is itself not a correct comparability
test — a fact future work (D1/D3) must not "optimise" around blindly.

---

## 7. Families for the correctness suite (hand-off to CGD-6 / D4)

Confirming (engine matches ground truth — keep as regression fixtures):

* paths Pₙ, even cycles C₂ₖ (count 2); odd cycles C₂ₖ₊₁ rejected with the hole.
* complete graphs Kₙ (count n!); complete bipartite Kₘ,ₙ (count 2).
* complements of odd cycles, the **net**, **bull**, **gem**, **4-sun S₄**,
  **domino** — all classified correctly.

Refuting (engine diverges — use as the divergence oracle / xfail set):

* **3-sun S₃** — false positive (accepted; truly non-comparability). *(CGD-10
  regression already pins termination; flip to `comparability=false` once fixed.)*
* `{0-1,0-2,0-3,0-4,1-2,1-4,1-5,2-3}` (n = 6) — false **negative** (rejected; truly
  comparability, 2 orientations).
* `{0-1,0-2,0-4,0-5,1-2,1-3,1-5,2-3,2-4}` (n = 6) — false **positive**.
* **diamond** `K_{1,1,2}` (n = 4) — verdict right, **count 4 vs 6**. Smallest count
  bug; ideal unit test for any count fix.

A practical D4 deliverable is the exhaustive sweep itself, promoted to a
parametrised characterisation test (verdict over n ≤ 6, count over n ≤ 5) against
the brute-force oracle, with the refuting set marked as known-divergent until D4
fixes them.

---

## 8. Minimal fix proposal

The theory is fine; replace the *search*, not the criterion.

1. **Verdict (sound + complete):** replace the bespoke
   `getNonTriangChain` / `continueNonTriangChain` / `canCreateOddCycle` pipeline
   with a faithful **Golumbic Γ-forcing** computation: build the implication
   classes of the edges (the standard `O(δ·m)` algorithm); the graph is a
   comparability graph **iff** no implication class contains an edge and its
   reverse. The first edge forced both ways yields a genuine odd forcing-walk for
   the `FailureCycle`. This removes both the false positives and the false
   negatives, and is the canonical realisation of Theorem 2.22.
2. **Count (correct):** compute it from the **canonical modular decomposition
   tree** with the right per-node factors — *series* (join) node with k children
   `× k!`, *parallel* (union) node `× 1`, *prime* comparability node `× 2` — times
   the product over children. (Equivalently, derive it from the Γ implication
   classes.) The current `∏ 2^(#min-stable) · ∏ clique!` over the Theorem-2.21
   decomposition is not a valid count and should be retired.
3. **Decision to record (the `FailureCycle` contract, `NEXT_SESSION.md` §1.3):**
   once the search is Γ-based, report the obstruction as a genuine **odd
   forcing-walk** (which may revisit vertices) and document that it is a closed
   walk, not necessarily a simple hole.

If a faithful re-implementation is out of scope for now, the honest interim step
is to **narrow the advertised contract**: the current engine is a reliable
comparability *verdict* only for n ≤ 5, and its orientation count is unreliable —
the README and CLAUDE invariant #2 should say so until D4 lands the fix above.
