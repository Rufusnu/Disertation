package Bears.Experiments;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable configuration of a single replicate of a scenario.
 *
 * Captures everything needed to (a) re-run the experiment and (b) trace any
 * row in the output CSV back to its provenance.
 */
public final class RunConfig {

    private final String scenarioId;
    private final String runId;
    private final int replicateIndex;
    private final long seed;
    /** Number of simulation ticks to run; if 0, wall-clock SIMULATION_LENGTH is used. */
    private final long maxTicks;
    /** How often (in ticks) to record a row in the per-tick CSV. 1 = every tick. */
    private final int metricsSampleEveryTicks;
    /** Parameter overrides (recorded in run_metadata.csv); applied by caller before run. */
    private final Map<String, String> parameterOverrides;

    public RunConfig(
            String scenarioId,
            String runId,
            int replicateIndex,
            long seed,
            long maxTicks,
            int metricsSampleEveryTicks,
            Map<String, String> parameterOverrides
    ) {
        this.scenarioId = scenarioId;
        this.runId = runId;
        this.replicateIndex = replicateIndex;
        this.seed = seed;
        this.maxTicks = maxTicks;
        this.metricsSampleEveryTicks = Math.max(1, metricsSampleEveryTicks);
        this.parameterOverrides = parameterOverrides != null
                ? new LinkedHashMap<>(parameterOverrides)
                : new LinkedHashMap<>();
    }

    public String scenarioId() { return scenarioId; }
    public String runId() { return runId; }
    public int replicateIndex() { return replicateIndex; }
    public long seed() { return seed; }
    public long maxTicks() { return maxTicks; }
    public int metricsSampleEveryTicks() { return metricsSampleEveryTicks; }
    public Map<String, String> parameterOverrides() { return parameterOverrides; }
}
