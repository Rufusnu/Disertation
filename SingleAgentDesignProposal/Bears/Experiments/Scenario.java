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
    /** Optional time-varying parameter schedule. May be empty (= stationary). */
    private final ParameterSchedule parameterSchedule;
    /**
     * Optional path to a pre-processed habitat grid (see
     * {@code Bears.BearEnvironment.MapGridLoader}). When set, BatchRunner
     * loads the grid instead of generating a random uniform map and uses the
     * grid's size as the effective {@link Settings#MAP_LENGTH}.
     */
    private final String mapSource;

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
        this(scenarioId, replicates, baseSeed, maxTicks, initialBearCount, mapLength,
                parameterOverrides, referencePopulationByYear, ParameterSchedule.empty(), "");
    }

    public Scenario(
            String scenarioId,
            int replicates,
            long baseSeed,
            long maxTicks,
            int initialBearCount,
            int mapLength,
            Map<String, String> parameterOverrides,
            NavigableMap<Integer, Double> referencePopulationByYear,
            ParameterSchedule parameterSchedule
    ) {
        this(scenarioId, replicates, baseSeed, maxTicks, initialBearCount, mapLength,
                parameterOverrides, referencePopulationByYear, parameterSchedule, "");
    }

    public Scenario(
            String scenarioId,
            int replicates,
            long baseSeed,
            long maxTicks,
            int initialBearCount,
            int mapLength,
            Map<String, String> parameterOverrides,
            NavigableMap<Integer, Double> referencePopulationByYear,
            ParameterSchedule parameterSchedule,
            String mapSource
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
        this.parameterSchedule = parameterSchedule != null
                ? parameterSchedule
                : ParameterSchedule.empty();
        this.mapSource = mapSource != null ? mapSource : "";
    }

    public String scenarioId() { return scenarioId; }
    public int replicates() { return replicates; }
    public long baseSeed() { return baseSeed; }
    public long maxTicks() { return maxTicks; }
    public int initialBearCount() { return initialBearCount; }
    public int mapLength() { return mapLength; }
    public Map<String, String> parameterOverrides() { return parameterOverrides; }
    public NavigableMap<Integer, Double> referencePopulationByYear() { return referencePopulationByYear; }
    public ParameterSchedule parameterSchedule() { return parameterSchedule; }
    public String mapSource() { return mapSource; }
}
