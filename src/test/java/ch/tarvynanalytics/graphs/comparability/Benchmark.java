package ch.tarvynanalytics.graphs.comparability;

import java.util.Random;

/**
 * Standalone timing harness (not a unit test — run via {@code main}). Measures
 * {@link ComparabilityAnalyzer#analyze} on random adjacency graphs, on random
 * cographs (always comparability — they stress the modular decomposition and the
 * orientation count), and on a correlation threshold sweep (the real use case).
 *
 * <p>Run after {@code ./mvnw test-compile}:
 * {@code java -cp target/classes:target/test-classes
 * ch.tarvynanalytics.graphs.comparability.Benchmark}</p>
 */
public final class Benchmark {

    private Benchmark() {
    }

    public static void main(String[] args) {
        System.out.println("== random Erdos-Renyi graphs (p=0.5) ==");
        for (int n : new int[]{20, 40, 60, 80, 100, 150}) {
            timeMany("ER n=" + n, () -> randomAdjacency(n, 0.5));
        }
        System.out.println("== random cographs (always comparability) ==");
        for (int n : new int[]{20, 40, 60, 80, 100, 150}) {
            timeMany("cograph n=" + n, () -> randomCograph(n));
        }
        System.out.println("== correlation threshold sweep (n=60) ==");
        thresholdSweep(60);

        System.out.println("== breakdown on dense random graphs ==");
        for (int n : new int[]{60, 100}) {
            breakdown(n);
        }

        System.out.println("== modular-decomposition builders: simple vs near-linear (fracture) ==");
        builderComparison();
    }

    /**
     * Times the two modular-decomposition builders against each other on dense (p=0.5,
     * mostly prime — the simple builder's O(n^4) worst case) random graphs. This is the
     * sweep behind the size gate ({@link ModularDecomposition#LINEAR_THRESHOLD}).
     */
    private static void builderComparison() {
        System.out.printf("  (gate flips to linear at n=%d)%n", ModularDecomposition.LINEAR_THRESHOLD);
        System.out.printf("  %-6s %12s %12s %10s%n", "n", "simple(ms)", "linear(ms)", "speedup");
        for (int n : new int[]{20, 40, 50, 60, 80, 100, 150, 200}) {
            double simple = timeBuilder(n, false);
            double linear = timeBuilder(n, true);
            System.out.printf("  %-6d %12.2f %12.2f %9.1fx%n", n, simple, linear, simple / linear);
        }
    }

    /** Best-of timing of one modular-decomposition builder (forced) on dense graphs of size {@code n}. */
    private static double timeBuilder(int n, boolean linear) {
        int reps = n >= 150 ? 5 : 20;
        for (int i = 0; i < 3; i++) {
            buildTree(randomAdjacency(n, 0.5), linear);
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < reps; i++) {
            boolean[][] a = randomAdjacency(n, 0.5);
            long t0 = System.nanoTime();
            buildTree(a, linear);
            best = Math.min(best, System.nanoTime() - t0);
        }
        return best / 1e6;
    }

    private static void buildTree(boolean[][] a, boolean linear) {
        ModularDecomposition md = new ModularDecomposition(a);
        if (linear) {
            md.useLinearBuilder();
        } else {
            md.useSimpleBuilder();
        }
        md.orientationCount();
    }

    /** Splits analyze() time into the Γ verdict vs the modular-decomposition levels. */
    private static void breakdown(int n) {
        boolean[][] a = randomAdjacency(n, 0.5);
        long t0 = System.nanoTime();
        ForcingRelation.Result fr = ForcingRelation.decide(a);
        long tFr = System.nanoTime() - t0;
        long t1 = System.nanoTime();
        new ModularDecomposition(a).levels(GraphInput.fromAdjacency(a));
        long tLevels = System.nanoTime() - t1;
        System.out.printf("  n=%-4d comparable=%-5b  forcing=%7.2f ms  modularLevels=%7.2f ms%n",
                n, fr.comparable, tFr / 1e6, tLevels / 1e6);
    }

    private static void timeMany(String label, java.util.function.Supplier<boolean[][]> gen) {
        int reps = 30;
        // warm up
        for (int i = 0; i < 5; i++) {
            ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(gen.get()));
        }
        long best = Long.MAX_VALUE;
        long total = 0;
        for (int i = 0; i < reps; i++) {
            boolean[][] a = gen.get();
            long t0 = System.nanoTime();
            ComparabilityAnalyzer.analyze(GraphInput.fromAdjacency(a));
            long dt = System.nanoTime() - t0;
            best = Math.min(best, dt);
            total += dt;
        }
        System.out.printf("  %-14s  best=%6.2f ms  avg=%6.2f ms%n",
                label, best / 1e6, total / 1e6 / reps);
    }

    private static void thresholdSweep(int n) {
        Random rnd = new Random(7);
        double[][] corr = new double[n][n];
        for (int i = 0; i < n; i++) {
            corr[i][i] = 1.0;
            for (int j = i + 1; j < n; j++) {
                double v = rnd.nextDouble() * 2 - 1;
                corr[i][j] = v;
                corr[j][i] = v;
            }
        }
        for (double t = 0.2; t <= 0.8; t += 0.1) {
            long t0 = System.nanoTime();
            ComparabilityAnalyzer.analyze(GraphInput.fromCorrelation(corr, t));
            System.out.printf("  threshold=%.1f  %6.2f ms%n", t, (System.nanoTime() - t0) / 1e6);
        }
    }

    // ---- generators --------------------------------------------------------

    private static final Random RND = new Random(42);

    private static boolean[][] randomAdjacency(int n, double p) {
        boolean[][] a = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (RND.nextDouble() < p) {
                    a[i][j] = true;
                    a[j][i] = true;
                }
            }
        }
        return a;
    }

    /** A random cograph via recursive series/parallel composition of singletons. */
    private static boolean[][] randomCograph(int n) {
        boolean[][] a = new boolean[n][n];
        buildCograph(a, range(n));
        return a;
    }

    private static int[] range(int n) {
        int[] v = new int[n];
        for (int i = 0; i < n; i++) {
            v[i] = i;
        }
        return v;
    }

    private static void buildCograph(boolean[][] a, int[] verts) {
        if (verts.length <= 1) {
            return;
        }
        int cut = 1 + RND.nextInt(verts.length - 1);
        int[] left = new int[cut];
        int[] right = new int[verts.length - cut];
        System.arraycopy(verts, 0, left, 0, cut);
        System.arraycopy(verts, cut, right, 0, verts.length - cut);
        if (RND.nextBoolean()) { // series: join the two parts
            for (int u : left) {
                for (int w : right) {
                    a[u][w] = true;
                    a[w][u] = true;
                }
            }
        }
        buildCograph(a, left);
        buildCograph(a, right);
    }
}
