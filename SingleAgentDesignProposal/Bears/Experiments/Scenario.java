package Bears.Experiments;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Definition of an experimental scenario: a parameter set plus reference
 * population data plus replicate count. A scenario is run N times by the
 * {@link BatchRunner} to obtain mean/variance of the simulation outputs and to
 * compute MAE/RMSE against the reference series.
 */
public final class Scenario {

    private final String scenarioId;
    private final int replicates;
    private final long baseSeed;
    private final long maxTicks;
    private final int initialBearCount;
    private final int mapLength;
    private final Map<String, String> parameterOverrides;
    /** year -> reference population (e.g. census estimate). May be empty. */
    private final NavigableMap<Integer, Double> referencePopulationByYear;

    public Scenario(
            String scenarioId,
            int replicates,
            long baseSeed,
            long maxTicks,
            int initialBearCount,
            int mapLength,
            Map<String, String> parameterOverrides,
            NavigableMap<Integer, Double> referencePopulationByYear
    ) {
        this.scenarioId = scenarioId;
        this.replicates = replicates;
        this.baseSeed = baseSeed;
        this.maxTicks = maxTicks;
        this.initialBearCount = initialBearCount;
        this.mapLength = mapLength;
        this.parameterOverrides = parameterOverrides != null
                ? new LinkedHashMap<>(parameterOverrides)
                : new LinkedHashMap<>();
        this.referencePopulationByYear = referencePopulationByYear != null
                ? new TreeMap<>(referencePopulationByYear)
                : new TreeMap<>();
    }

    public String scenarioId() { return scenarioId; }
    public int replicates() { return replicates; }
    public long baseSeed() { return baseSeed; }
    public long maxTicks() { return maxTicks; }
    public int initialBearCount() { return initialBearCount; }
    public int mapLength() { return mapLength; }
    public Map<String, String> parameterOverrides() { return parameterOverrides; }
    public NavigableMap<Integer, Double> referencePopulationByYear() { return referencePopulationByYear; }
}
