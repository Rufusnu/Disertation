package Bears.Experiments;

import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Central source of randomness for the simulation. Designed so that a run is
 * reproducible from its seed EVEN WHEN agents are planned in parallel.
 *
 * Two modes:
 *
 *  - DEFAULT (no seed): {@link #forAgent(int)} and {@link #environment()} both
 *    delegate to {@link ThreadLocalRandom#current()} — same hot path as before
 *    so there is no performance penalty for non-experiment runs.
 *
 *  - SEEDED (after {@link #setSeed(long)}):
 *      * {@link #forAgent(int)} returns a dedicated {@link Random} keyed by
 *        the agent id, derived deterministically from the master seed via a
 *        SplitMix64 mixer. The same agent always sees the SAME stream of
 *        random numbers regardless of which worker thread executes
 *        {@code decideNextAction} for it.
 *      * {@link #environment()} returns a single {@link Random} that must
 *        only be used from serial code paths (map generation, commit phase,
 *        setup) — never from the parallel planning workers.
 *
 *  Per-agent {@link Random} instances are stored in a {@link ConcurrentHashMap}
 *  so concurrent threads planning different agents don't contend on insertion.
 *  {@link Random} itself is thread-safe via internal synchronisation.
 */
public final class RngSupport {

    private static volatile boolean seeded = false;
    private static volatile long masterSeed = 0L;
    private static final ConcurrentHashMap<Integer, Random> AGENT_RNGS = new ConcurrentHashMap<>();
    private static volatile Random ENVIRONMENT_RNG = null;

    private RngSupport() {}

    /**
     * Switches to seeded mode with the given master seed. Must be called
     * BEFORE constructing any agent or generating the map for the run.
     */
    public static void setSeed(long seed) {
        masterSeed = seed;
        seeded = true;
        AGENT_RNGS.clear();
        ENVIRONMENT_RNG = new Random(mix(seed, -1L));
    }

    /** Reverts to {@link ThreadLocalRandom}. */
    public static void clearSeed() {
        seeded = false;
        AGENT_RNGS.clear();
        ENVIRONMENT_RNG = null;
    }

    public static boolean isSeeded() {
        return seeded;
    }

    public static long currentSeed() {
        return masterSeed;
    }

    /**
     * Returns the RNG dedicated to the given agent id. Safe to call from any
     * thread. The stream depends only on (masterSeed, agentId), so two runs
     * with the same seed see the same per-agent decisions independent of
     * thread scheduling.
     */
    public static java.util.random.RandomGenerator forAgent(int agentId) {
        if (!seeded) {
            return ThreadLocalRandom.current();
        }
        return AGENT_RNGS.computeIfAbsent(agentId, id -> new Random(mix(masterSeed, id)));
    }

    /**
     * Returns the RNG for environment-level decisions (map generation, food
     * restoration, random tile selection). MUST ONLY be invoked from serial
     * code paths to remain deterministic; in default mode it falls back to
     * {@link ThreadLocalRandom}.
     */
    public static java.util.random.RandomGenerator environment() {
        if (!seeded) {
            return ThreadLocalRandom.current();
        }
        return ENVIRONMENT_RNG;
    }

    private static long mix(long seed, long key) {
        // SplitMix64-style mixer.
        long z = seed + (key + 1L) * 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
